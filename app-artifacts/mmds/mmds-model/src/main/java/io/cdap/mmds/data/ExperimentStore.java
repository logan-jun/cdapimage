/*
 * Copyright © 2017-2018 Cask Data, Inc.
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

package io.cdap.mmds.data;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.mmds.api.Modeler;
import io.cdap.mmds.modeler.Modelers;
import io.cdap.mmds.proto.BadRequestException;
import io.cdap.mmds.proto.ConflictException;
import io.cdap.mmds.proto.CreateModelRequest;
import io.cdap.mmds.proto.ExperimentNotFoundException;
import io.cdap.mmds.proto.ModelNotFoundException;
import io.cdap.mmds.proto.SplitNotFoundException;
import io.cdap.mmds.proto.TrainModelRequest;
import io.cdap.mmds.spec.Parameters;
import io.cdap.mmds.stats.CategoricalHisto;
import io.cdap.mmds.stats.NumericHisto;
import io.cdap.mmds.stats.NumericStats;
import org.apache.twill.filesystem.Location;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Manages experiments, splits, and models.
 */
public class ExperimentStore {
  private static final Set<Schema.Type> CATEGORICAL_TYPES = ImmutableSet.of(Schema.Type.BOOLEAN, Schema.Type.STRING);
  private static final Set<Schema.Type> NUMERIC_TYPES =
    ImmutableSet.of(Schema.Type.INT, Schema.Type.LONG, Schema.Type.FLOAT, Schema.Type.DOUBLE);
  private final ExperimentMetaTable experiments;
  private final DataSplitTable splits;
  private final ModelTable models;

  public ExperimentStore(ExperimentMetaTable experiments, DataSplitTable splits, ModelTable models) {
    this.experiments = experiments;
    this.splits = splits;
    this.models = models;
  }

  public ExperimentsMeta listExperiments(int offset, int limit, SortInfo sortInfo) {
    return experiments.list(offset, limit);
  }

  public ExperimentsMeta listExperiments(int offset, int limit, Predicate<Experiment> predicate, SortInfo sortInfo) {
    return experiments.list(offset, limit, predicate, sortInfo);
  }

  public Experiment getExperiment(String experimentName) {
    Experiment experiment = experiments.get(experimentName);
    if (experiment == null) {
      throw new ExperimentNotFoundException(experimentName);
    }
    return experiment;
  }

  public ExperimentStats getExperimentStats(String experimentName) {
    Experiment experiment = getExperiment(experimentName);

    Map<String, ColumnStats> metricStats = new HashMap<>();
    CategoricalHisto algoHisto = new CategoricalHisto();
    CategoricalHisto statusHisto = new CategoricalHisto();

    List<ModelMeta> models = listModels(experimentName, 0, Integer.MAX_VALUE, new SortInfo(SortType.ASC)).getModels();
    if (models.isEmpty()) {
      return new ExperimentStats(experiment, metricStats, new ColumnStats(algoHisto), new ColumnStats(statusHisto));
    }

    Iterator<ModelMeta> modelIter = models.iterator();
    ModelMeta modelMeta = modelIter.next();
    algoHisto.update(modelMeta.getAlgorithm());
    statusHisto.update(modelMeta.getStatus() == null ? null : modelMeta.getStatus().toString());

    EvaluationMetrics metrics = modelMeta.getEvaluationMetrics();
    NumericStats rmse = new NumericStats(metrics.getRmse());
    NumericStats r2 = new NumericStats(metrics.getR2());
    NumericStats mae = new NumericStats(metrics.getMae());
    NumericStats evariance = new NumericStats(metrics.getEvariance());
    NumericStats precision = new NumericStats(metrics.getPrecision());
    NumericStats recall = new NumericStats(metrics.getRecall());
    NumericStats f1 = new NumericStats(metrics.getF1());

    while (modelIter.hasNext()) {
      modelMeta = modelIter.next();
      algoHisto.update(modelMeta.getAlgorithm());
      statusHisto.update(modelMeta.getStatus() == null ? null : modelMeta.getStatus().toString());

      metrics = modelMeta.getEvaluationMetrics();
      rmse.update(metrics.getRmse());
      r2.update(metrics.getR2());
      mae.update(metrics.getMae());
      evariance.update(metrics.getEvariance());
      precision.update(metrics.getPrecision());
      recall.update(metrics.getRecall());
      f1.update(metrics.getF1());
    }

    modelIter = models.iterator();
    metrics = modelIter.next().getEvaluationMetrics();
    int numBins = Math.min(10, (int) statusHisto.getTotalCount());
    NumericHisto rmseHisto = null;
    if (rmse.getMin() != null) {
      rmseHisto = new NumericHisto(rmse.getMin(), rmse.getMax(), numBins, metrics.getRmse());
    }
    NumericHisto r2Histo = null;
    if (r2.getMin() != null) {
      r2Histo = new NumericHisto(r2.getMin(), r2.getMax(), numBins, metrics.getR2());
    }
    NumericHisto maeHisto = null;
    if (mae.getMin() != null) {
      maeHisto = new NumericHisto(mae.getMin(), mae.getMax(), numBins, metrics.getMae());
    }
    NumericHisto evarianceHisto = null;
    if (evariance.getMin() != null) {
      evarianceHisto = new NumericHisto(evariance.getMin(), evariance.getMax(), numBins, metrics.getEvariance());
    }
    NumericHisto precisionHisto = null;
    if (precision.getMin() != null) {
      precisionHisto = new NumericHisto(0, 1, 10, metrics.getPrecision());
    }
    NumericHisto recallHisto = null;
    if (recall.getMin() != null) {
      recallHisto = new NumericHisto(0, 1, 10, metrics.getRecall());
    }
    NumericHisto f1Histo = null;
    if (f1.getMin() != null) {
      f1Histo = new NumericHisto(0, 1, 10, metrics.getF1());
    }

    while (modelIter.hasNext()) {
      metrics = modelIter.next().getEvaluationMetrics();
      if (rmseHisto != null) {
        rmseHisto.update(metrics.getRmse());
      }
      if (r2Histo != null) {
        r2Histo.update(metrics.getR2());
      }
      if (maeHisto != null) {
        maeHisto.update(metrics.getMae());
      }
      if (evarianceHisto != null) {
        evarianceHisto.update(metrics.getEvariance());
      }
      if (precisionHisto != null) {
        precisionHisto.update(metrics.getPrecision());
      }
      if (recallHisto != null) {
        recallHisto.update(metrics.getRecall());
      }
      if (f1Histo != null) {
        f1Histo.update(metrics.getF1());
      }
    }

    if (rmseHisto != null) {
      metricStats.put("rmse", new ColumnStats(rmseHisto));
    }
    if (r2Histo != null) {
      metricStats.put("r2", new ColumnStats(r2Histo));
    }
    if (maeHisto != null) {
      metricStats.put("mae", new ColumnStats(maeHisto));
    }
    if (evarianceHisto != null) {
      metricStats.put("evariance", new ColumnStats(evarianceHisto));
    }
    if (precisionHisto != null) {
      metricStats.put("precision", new ColumnStats(precisionHisto));
    }
    if (recallHisto != null) {
      metricStats.put("recall", new ColumnStats(recallHisto));
    }
    if (f1Histo != null) {
      metricStats.put("f1", new ColumnStats(f1Histo));
    }

    return new ExperimentStats(experiment, metricStats, new ColumnStats(algoHisto), new ColumnStats(statusHisto));
  }

  public void putExperiment(Experiment experiment) {
    experiments.put(experiment);
  }

  public void deleteExperiment(String experimentName) {
    getExperiment(experimentName);
    models.delete(experimentName);
    splits.delete(experimentName);
    experiments.delete(experimentName);
  }

  public ModelsMeta listModels(String experimentName, int offset, int limit, SortInfo sortInfo) {
    getExperiment(experimentName);
    return models.list(experimentName, offset, limit, sortInfo);
  }

  public ModelMeta getModel(ModelKey modelKey) {
    getExperiment(modelKey.getExperiment());
    ModelMeta modelMeta = models.get(modelKey);
    if (modelMeta == null) {
      throw new ModelNotFoundException(modelKey);
    }
    return modelMeta;
  }

  public ModelTrainerInfo trainModel(ModelKey key, TrainModelRequest trainRequest, long trainingTime) {
    Experiment experiment = getExperiment(key.getExperiment());
    ModelMeta meta = getModel(key);
    ModelStatus currentStatus = meta.getStatus();

    if (currentStatus != ModelStatus.DATA_READY) {
      throw new ConflictException(String.format("Cannot train a model that is in the '%s' state.", currentStatus));
    }

    Modeler modeler = Modelers.getModeler(trainRequest.getAlgorithm());
    Parameters parameters = modeler.getParams(trainRequest.getHyperparameters());
    // update parameters with the modeler defaults.
    TrainModelRequest requestWithDefaults = new TrainModelRequest(trainRequest.getAlgorithm(),
                                                                  trainRequest.getPredictionsDataset(),
                                                                  parameters.toMap());

    models.setTrainingInfo(key, requestWithDefaults, trainingTime);

    SplitKey splitKey = new SplitKey(key.getExperiment(), meta.getSplit());
    DataSplitStats splitInfo = getSplit(splitKey);

    meta = models.get(key);
    return new ModelTrainerInfo(experiment, splitInfo, key.getModel(), meta);
  }

  public void setModelSplit(ModelKey key, String splitId) {
    Experiment experiment = getExperiment(key.getExperiment());
    ModelMeta meta = getModel(key);
    ModelStatus currentStatus = meta.getStatus();

    if (currentStatus != ModelStatus.PREPARING && currentStatus != ModelStatus.SPLIT_FAILED &&
      currentStatus != ModelStatus.TRAINING_FAILED && currentStatus != ModelStatus.DATA_READY) {
      throw new ConflictException(String.format(
        "Cannot set a split for a model in the '%s' state. The model must be in the '%s', '%s', '%s', or '%s' state.",
        currentStatus, ModelStatus.PREPARING, ModelStatus.SPLIT_FAILED,
        ModelStatus.TRAINING_FAILED, ModelStatus.DATA_READY));
    }

    DataSplitStats splitInfo = getSplit(new SplitKey(key.getExperiment(), splitId));

    String currentSplit = meta.getSplit();
    if (currentSplit != null) {
      splits.unregisterModel(new SplitKey(key.getExperiment(), currentSplit), key.getModel());
    }

    models.setSplit(key, splitInfo, experiment.getOutcome());
    splits.registerModel(new SplitKey(key.getExperiment(), splitId), key.getModel());
  }

  public void unassignModelSplit(ModelKey key) {
    getExperiment(key.getExperiment());
    ModelMeta meta = getModel(key);
    ModelStatus currentStatus = meta.getStatus();

    if (currentStatus != ModelStatus.SPLIT_FAILED && currentStatus != ModelStatus.DATA_READY &&
      currentStatus != ModelStatus.TRAINING_FAILED) {
      throw new ConflictException(String.format(
        "Cannot unassign the split for a model in the '%s' state. The model must be in the '%s', '%s', or '%s' state.",
        currentStatus, ModelStatus.SPLIT_FAILED, ModelStatus.TRAINING_FAILED, ModelStatus.DATA_READY));
    }

    DataSplitStats splitInfo = getSplit(new SplitKey(key.getExperiment(), meta.getSplit()));

    models.unassignSplit(key);
    models.setStatus(key, ModelStatus.PREPARING);
    SplitKey splitKey = new SplitKey(key.getExperiment(), meta.getSplit());
    splits.unregisterModel(splitKey, key.getModel());
    if (splitInfo.getModels().size() == 1) {
      splits.delete(splitKey);
    }
  }

  public String addModel(String experimentName, CreateModelRequest createRequest) {
    Experiment experiment = getExperiment(experimentName);
    String splitId = createRequest.getSplit();
    DataSplitStats splitStats = null;
    if (splitId != null) {
      SplitKey splitKey = new SplitKey(experimentName, splitId);
      splitStats = splits.get(splitKey);
      if (splitStats == null) {
        throw new SplitNotFoundException(splitKey);
      }
    }
    String modelId = models.add(experiment, createRequest, System.currentTimeMillis());
    if (splitStats != null) {
      models.setSplit(new ModelKey(experimentName, modelId), splitStats, experiment.getOutcome());
    }
    return modelId;
  }

  public void setModelDirectives(ModelKey key, List<String> directives) {
    ModelMeta modelMeta = getModel(key);
    ModelStatus status = modelMeta.getStatus();
    if (status != ModelStatus.PREPARING) {
      throw new ConflictException(String.format(
        "Directives can only be set or modified if the model is in the %s state.", ModelStatus.PREPARING));
    }
    models.setDirectives(key, directives);
  }

  public void updateModelMetrics(ModelKey key, EvaluationMetrics evaluationMetrics,
                                 long trainedTime, Set<String> categoricalFeatures) {
    models.update(key, evaluationMetrics, trainedTime, categoricalFeatures);
  }

  public void deleteModel(ModelKey modelKey) {
    ModelMeta modelMeta = models.get(modelKey);
    if (modelMeta == null) {
      throw new ModelNotFoundException(modelKey);
    }
    models.delete(modelKey);
    if (modelMeta.getSplit() != null) {
      splits.unregisterModel(new SplitKey(modelKey.getExperiment(), modelMeta.getSplit()), modelKey.getModel());
    }
  }

  public void deployModel(ModelKey key) {
    ModelMeta modelMeta = getModel(key);
    if (modelMeta.getDeploytime() > 0) {
      // already deployed
      return;
    }
    models.setStatus(key, ModelStatus.DEPLOYED);
  }

  public void modelFailed(ModelKey key) {
    ModelMeta modelMeta = getModel(key);
    ModelStatus currentStatus = modelMeta.getStatus();
    if (currentStatus != ModelStatus.TRAINING) {
      // should never happen
      throw new IllegalStateException(String.format("Cannot transition model to '%s' from '%s'",
                                                    currentStatus, ModelStatus.TRAINING_FAILED));
    }
    models.setStatus(key, ModelStatus.TRAINING_FAILED);
  }

  public List<DataSplitStats> listSplits(String experimentName) {
    getExperiment(experimentName);
    return splits.list(experimentName);
  }

  public DataSplitInfo addSplit(String experimentName, DataSplit splitInfo, long startTimeMillis) {
    Experiment experiment = getExperiment(experimentName);
    Schema.Type experimentOutcomeType = Schema.Type.valueOf(experiment.getOutcomeType().toUpperCase());

    Schema splitSchema = splitInfo.getSchema();
    Schema.Field outcomeField = splitSchema.getField(experiment.getOutcome());
    if (outcomeField == null) {
      throw new BadRequestException(
        String.format("Invalid split schema. The split must contain the experiment outcome '%s'.",
                      experiment.getOutcome()));
    }
    Schema splitOutcomeSchema = outcomeField.getSchema();
    if (splitOutcomeSchema.isNullable()) {
      splitOutcomeSchema = splitOutcomeSchema.getNonNullable();
    }
    Schema.Type splitOutcomeType = splitOutcomeSchema.getType();

    if (CATEGORICAL_TYPES.contains(experimentOutcomeType) && !CATEGORICAL_TYPES.contains(splitOutcomeType)) {
      throw new BadRequestException(
        String.format("Invalid split schema. Outcome field '%s' is of categorical type '%s' in the experiment , " +
                        "but is of non-categorical type '%s' in the split.",
                      experiment.getOutcome(), experimentOutcomeType, splitOutcomeType));
    }
    if (NUMERIC_TYPES.contains(experimentOutcomeType) && !NUMERIC_TYPES.contains(splitOutcomeType)) {
      throw new BadRequestException(
        String.format("Invalid split schema. Outcome field '%s' is of numeric type '%s' in the experiment, " +
                        "but is of non-numeric type '%s' in the split.",
                      experiment.getOutcome(), experimentOutcomeType, splitOutcomeType));
    }

    String splitId = splits.addSplit(experimentName, splitInfo, startTimeMillis);
    Location splitLocation = splits.getLocation(new SplitKey(experimentName, splitId));
    return new DataSplitInfo(splitId, experiment, splitInfo, splitLocation);
  }

  public DataSplitStats getSplit(SplitKey key) {
    getExperiment(key.getExperiment());

    DataSplitStats stats = splits.get(key);
    if (stats == null) {
      throw new SplitNotFoundException(key);
    }
    return stats;
  }

  public void finishSplit(SplitKey splitKey, String trainingPath, String testPath, List<ColumnSplitStats> stats,
                          long endTime) {
    splits.updateStats(splitKey, trainingPath, testPath, stats, endTime);
    DataSplitStats splitStats = getSplit(splitKey);
    for (String modelId : splitStats.getModels()) {
      models.setStatus(new ModelKey(splitKey.getExperiment(), modelId), ModelStatus.DATA_READY);
    }
  }

  public void splitFailed(SplitKey key, long failTime) {
    getExperiment(key.getExperiment());
    DataSplitStats splitStats = getSplit(key);
    if (splitStats.getStatus() != SplitStatus.SPLITTING) {
      // should never happen
      throw new IllegalStateException("Cannot transition split to failed state unless it is in the splitting state.");
    }
    splits.splitFailed(key, failTime);
    for (String model : splitStats.getModels()) {
      models.setStatus(new ModelKey(key.getExperiment(), model), ModelStatus.SPLIT_FAILED);
    }
  }

  public void deleteSplit(SplitKey key) {
    DataSplitStats stats = getSplit(key);
    if (!stats.getModels().isEmpty()) {
      throw new ConflictException(String.format("Cannot delete split '%s' since it is used by model(s) '%s'.",
                                                key.getSplit(), Joiner.on(',').join(stats.getModels())));
    }
    splits.delete(key);
  }
}
