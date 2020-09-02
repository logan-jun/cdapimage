/*
 * Copyright © 2016-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.plugin.db.batch.action;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.batch.BatchActionContext;
import io.cdap.cdap.etl.api.batch.PostAction;
import io.cdap.plugin.DBManager;
import io.cdap.plugin.common.batch.action.Condition;
import io.cdap.plugin.common.batch.action.ConditionConfig;

import java.sql.Driver;
import javax.annotation.Nullable;

/**
 * Runs a query after a pipeline run.
 */
@SuppressWarnings("ConstantConditions")
@Plugin(type = PostAction.PLUGIN_TYPE)
@Name("DatabaseQuery")
@Description("Runs a query after a pipeline run.")
public class QueryAction extends PostAction {
  private static final String JDBC_PLUGIN_ID = "driver";
  private final QueryActionConfig config;

  public QueryAction(QueryActionConfig config) {
    this.config = config;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    FailureCollector collector = pipelineConfigurer.getStageConfigurer().getFailureCollector();
    config.validate(collector);
    DBManager dbManager = new DBManager(config);
    dbManager.validateJDBCPluginPipeline(pipelineConfigurer, JDBC_PLUGIN_ID, collector);
  }

  @Override
  public void run(BatchActionContext batchContext) throws Exception {
    FailureCollector collector = batchContext.getFailureCollector();
    config.validate(collector);
    collector.getOrThrowException();

    if (!config.shouldRun(batchContext)) {
      return;
    }

    Class<? extends Driver> driverClass = batchContext.loadPluginClass(JDBC_PLUGIN_ID);
    DBRun executeQuery = new DBRun(config, driverClass);
    executeQuery.run();
  }

  /**
   * config for {@link QueryAction}
   */
  public class QueryActionConfig extends QueryConfig {
    private static final String NAME_RUN_CONDITION = "runCondition";

    @Nullable
    @Description("When to run the action. Must be 'completion', 'success', or 'failure'. Defaults to 'success'. " +
      "If set to 'completion', the action will be executed regardless of whether the pipeline run succeeded or " +
      "failed. If set to 'success', the action will only be executed if the pipeline run succeeded. " +
      "If set to 'failure', the action will only be executed if the pipeline run failed.")
    @Macro
    public String runCondition;

    public QueryActionConfig() {
      super();
      runCondition = Condition.SUCCESS.name();
    }

    public void validate(FailureCollector collector) {
      // have to delegate instead of inherit, since we can't extend both ConditionConfig and ConnectionConfig.
      if (!containsMacro("runCondition")) {
        new ConditionConfig(runCondition).validate(collector);
      }
    }

    public boolean shouldRun(BatchActionContext actionContext) {
      return new ConditionConfig(runCondition).shouldRun(actionContext);
    }
  }
}
