/*
 * Copyright © 2016 Cask Data, Inc.
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

package io.cdap.cdap.etl.spark.batch;

import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.cdap.cdap.api.Transactionals;
import io.cdap.cdap.api.TxRunnable;
import io.cdap.cdap.api.data.DatasetContext;
import io.cdap.cdap.api.data.batch.InputFormatProvider;
import io.cdap.cdap.api.data.batch.OutputFormatProvider;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.spark.JavaSparkExecutionContext;
import io.cdap.cdap.api.spark.JavaSparkMain;
import io.cdap.cdap.api.workflow.WorkflowToken;
import io.cdap.cdap.etl.api.JoinElement;
import io.cdap.cdap.etl.api.batch.BatchSource;
import io.cdap.cdap.etl.batch.BatchPhaseSpec;
import io.cdap.cdap.etl.batch.PipelinePluginInstantiator;
import io.cdap.cdap.etl.batch.connector.SingleConnectorFactory;
import io.cdap.cdap.etl.common.Constants;
import io.cdap.cdap.etl.common.RecordInfo;
import io.cdap.cdap.etl.common.SetMultimapCodec;
import io.cdap.cdap.etl.common.StageStatisticsCollector;
import io.cdap.cdap.etl.common.plugin.PipelinePluginContext;
import io.cdap.cdap.etl.proto.v2.spec.StageSpec;
import io.cdap.cdap.etl.spark.Compat;
import io.cdap.cdap.etl.spark.SparkCollection;
import io.cdap.cdap.etl.spark.SparkPairCollection;
import io.cdap.cdap.etl.spark.SparkPipelineRunner;
import io.cdap.cdap.etl.spark.SparkStageStatisticsCollector;
import io.cdap.cdap.etl.spark.function.BatchSourceFunction;
import io.cdap.cdap.etl.spark.function.JoinMergeFunction;
import io.cdap.cdap.etl.spark.function.JoinOnFunction;
import io.cdap.cdap.etl.spark.function.PluginFunctionContext;
import io.cdap.cdap.internal.io.SchemaTypeAdapter;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Batch Spark pipeline driver.
 */
public class BatchSparkPipelineDriver extends SparkPipelineRunner implements JavaSparkMain, TxRunnable {
  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(SetMultimap.class, new SetMultimapCodec<>())
    .registerTypeAdapter(Schema.class, new SchemaTypeAdapter())
    .registerTypeAdapter(DatasetInfo.class, new DatasetInfoTypeAdapter())
    .registerTypeAdapter(InputFormatProvider.class, new InputFormatProviderTypeAdapter())
    .create();

  private transient JavaSparkContext jsc;
  private transient JavaSparkExecutionContext sec;
  private transient SparkBatchSourceFactory sourceFactory;
  private transient SparkBatchSinkFactory sinkFactory;
  private transient DatasetContext datasetContext;
  private transient Map<String, Integer> stagePartitions;

  @Override
  protected SparkCollection<RecordInfo<Object>> getSource(StageSpec stageSpec, StageStatisticsCollector collector) {
    PluginFunctionContext pluginFunctionContext = new PluginFunctionContext(stageSpec, sec, collector);
    return new RDDCollection<>(sec, jsc, new SQLContext(jsc), datasetContext, sinkFactory,
                               sourceFactory.createRDD(sec, jsc, stageSpec.getName(), Object.class, Object.class)
                                 .flatMap(Compat.convert(new BatchSourceFunction(pluginFunctionContext))));
  }

  @Override
  protected SparkPairCollection<Object, Object> addJoinKey(StageSpec stageSpec, String inputStageName,
                                                           SparkCollection<Object> inputCollection,
                                                           StageStatisticsCollector collector) throws Exception {
    PluginFunctionContext pluginFunctionContext = new PluginFunctionContext(stageSpec, sec, collector);
    return inputCollection.flatMapToPair(
      Compat.convert(new JoinOnFunction<>(pluginFunctionContext, inputStageName)));
  }

  @Override
  protected SparkCollection<Object> mergeJoinResults(
    StageSpec stageSpec,
    SparkPairCollection<Object, List<JoinElement<Object>>> joinedInputs,
    StageStatisticsCollector collector) throws Exception {
    PluginFunctionContext pluginFunctionContext = new PluginFunctionContext(stageSpec, sec, collector);
    return joinedInputs.flatMap(Compat.convert(new JoinMergeFunction<>(pluginFunctionContext)));
  }

  @Override
  public void run(JavaSparkExecutionContext sec) throws Exception {
    this.jsc = new JavaSparkContext();
    this.sec = sec;

    // Execution the whole pipeline in one long transaction. This is because the Spark execution
    // currently share the same contract and API as the MapReduce one.
    // The API need to expose DatasetContext, hence it needs to be executed inside a transaction
    Transactionals.execute(sec, this, Exception.class);
  }

  @Override
  public void run(DatasetContext context) throws Exception {
    BatchPhaseSpec phaseSpec = GSON.fromJson(sec.getSpecification().getProperty(Constants.PIPELINEID),
                                             BatchPhaseSpec.class);

    Path configFile = sec.getLocalizationContext().getLocalFile("HydratorSpark.config").toPath();
    try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
      String object = reader.readLine();
      SparkBatchSourceSinkFactoryInfo sourceSinkInfo = GSON.fromJson(object, SparkBatchSourceSinkFactoryInfo.class);
      sourceFactory = sourceSinkInfo.getSparkBatchSourceFactory();
      sinkFactory = sourceSinkInfo.getSparkBatchSinkFactory();
      stagePartitions = sourceSinkInfo.getStagePartitions();
    }
    datasetContext = context;
    PipelinePluginContext pluginContext = new PipelinePluginContext(sec.getPluginContext(), sec.getMetrics(),
                                                                    phaseSpec.isStageLoggingEnabled(),
                                                                    phaseSpec.isProcessTimingEnabled());

    Map<String, StageStatisticsCollector> collectors = new HashMap<>();
    if (phaseSpec.pipelineContainsCondition()) {
      Iterator<StageSpec> iterator = phaseSpec.getPhase().iterator();
      while (iterator.hasNext()) {
        StageSpec spec = iterator.next();
        collectors.put(spec.getName(), new SparkStageStatisticsCollector(jsc));
      }
    }
    try {
      PipelinePluginInstantiator pluginInstantiator =
        new PipelinePluginInstantiator(pluginContext, sec.getMetrics(), phaseSpec, new SingleConnectorFactory());
      boolean shouldConsolidateStages = Boolean.parseBoolean(
        sec.getRuntimeArguments().getOrDefault(Constants.CONSOLIDATE_STAGES, Boolean.FALSE.toString()));
      runPipeline(phaseSpec, BatchSource.PLUGIN_TYPE, sec, stagePartitions, pluginInstantiator, collectors,
                  sinkFactory.getUncombinableSinks(), shouldConsolidateStages);
    } finally {
      updateWorkflowToken(sec.getWorkflowToken(), collectors);
    }
  }

  private void updateWorkflowToken(WorkflowToken token, Map<String, StageStatisticsCollector> collectors) {
    for (Map.Entry<String, StageStatisticsCollector> entry : collectors.entrySet()) {
      SparkStageStatisticsCollector collector = (SparkStageStatisticsCollector) entry.getValue();
      String keyPrefix = Constants.StageStatistics.PREFIX + "." + entry.getKey() + ".";

      String inputRecordKey = keyPrefix + Constants.StageStatistics.INPUT_RECORDS;
      token.put(inputRecordKey, String.valueOf(collector.getInputRecordCount()));

      String outputRecordKey = keyPrefix + Constants.StageStatistics.OUTPUT_RECORDS;
      token.put(outputRecordKey, String.valueOf(collector.getOutputRecordCount()));

      String errorRecordKey = keyPrefix + Constants.StageStatistics.ERROR_RECORDS;
      token.put(errorRecordKey, String.valueOf(collector.getErrorRecordCount()));
    }
  }
}
