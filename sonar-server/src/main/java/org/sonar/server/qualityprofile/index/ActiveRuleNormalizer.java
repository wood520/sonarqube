/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.qualityprofile.index;

import com.google.common.base.Preconditions;
import org.elasticsearch.action.update.UpdateRequest;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.qualityprofile.db.ActiveRuleDto;
import org.sonar.core.qualityprofile.db.ActiveRuleKey;
import org.sonar.core.qualityprofile.db.ActiveRuleParamDto;
import org.sonar.server.db.DbClient;
import org.sonar.server.search.BaseNormalizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ActiveRuleNormalizer extends BaseNormalizer<ActiveRuleDto, ActiveRuleKey> {

  public static enum ActiveRuleField {
    KEY("key"),
    INHERITANCE("inheritance"),
    PROFILE_ID("profile"),
    SEVERITY("severity"),
    PARENT_KEY("parentKey"),
    PARAMS("params"),
    RULE_KEY("ruleKey");

    private final String key;

    private ActiveRuleField(final String key) {
      this.key = key;
    }

    public String key() {
      return key;
    }

    @Override
    public String toString() {
      return key;
    }
  }

  public static enum ActiveRuleParamField {
    NAME("name"),
    VALUE("value");

    private final String key;

    private ActiveRuleParamField(final String key) {
      this.key = key;
    }

    public String key() {
      return key;
    }

    @Override
    public String toString() {
      return key;
    }
  }

  public ActiveRuleNormalizer(DbClient db) {
    super(db);
  }

  @Override
  public UpdateRequest normalize(ActiveRuleKey key) {
    DbSession dbSession = db().openSession(false);
    try {
      return normalize(db().activeRuleDao().getByKey(key, dbSession));
    } finally {
      dbSession.close();
    }
  }

  public UpdateRequest normalize(ActiveRuleParamDto param, ActiveRuleKey key) {
    Preconditions.checkArgument(key != null, "Cannot normalize ActiveRuleParamDto for null key of ActiveRule");

    Map<String, Object> newParam = new HashMap<String, Object>();
    newParam.put("_id", param.getKey());
    newParam.put(ActiveRuleParamField.NAME.key(), param.getKey());
    newParam.put(ActiveRuleParamField.VALUE.key(), param.getValue());

    return this.nestedUpsert(ActiveRuleField.PARAMS.key(), param.getKey(), newParam)
      .routing(key.ruleKey().toString());
  }

  @Override
  public UpdateRequest normalize(ActiveRuleDto activeRuleDto) {
    ActiveRuleKey key = activeRuleDto.getKey();
    Preconditions.checkArgument(key != null, "Cannot normalize ActiveRuleDto with null key");

    Map<String, Object> newRule = new HashMap<String, Object>();
    newRule.put("_parent", key.ruleKey().toString());
    newRule.put("ruleKey", key.ruleKey().toString());
    newRule.put(ActiveRuleField.KEY.key(), key.toString());
    newRule.put(ActiveRuleField.INHERITANCE.key(), activeRuleDto.getInheritance());
    newRule.put(ActiveRuleField.PROFILE_ID.key(), activeRuleDto.getProfileId());
    newRule.put(ActiveRuleField.SEVERITY.key(), activeRuleDto.getSeverityString());

    //TODO this should be generated by RegisterRule and modified in DTO.
    String parentKey = null;
    if (activeRuleDto.getParentId() != null) {
      DbSession session = db.openSession(false);
      try {
        ActiveRuleDto parentDto = db.activeRuleDao().getById(activeRuleDto.getParentId(), session);
        parentKey = parentDto.getKey().toString();
      } finally {
        session.close();
      }
    }
    newRule.put(ActiveRuleField.PARENT_KEY.key(), parentKey);

    Map<String, Object> upsert = new HashMap<String, Object>(newRule);
    upsert.put(ActiveRuleField.PARAMS.key(), new ArrayList());

    /* Creating updateRequest */
    return new UpdateRequest()
      .routing(key.ruleKey().toString())
      .id(activeRuleDto.getKey().toString())
      .parent(activeRuleDto.getKey().ruleKey().toString())
      .doc(newRule)
      .upsert(upsert);
  }
}
