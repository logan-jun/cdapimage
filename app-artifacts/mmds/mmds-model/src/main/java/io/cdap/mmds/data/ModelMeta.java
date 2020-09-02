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


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Holds information added by the Model manager service
 */
public class ModelMeta extends Model {
  private final String id;
  private final long createtime;
  private final long trainingtime;
  private final long trainedtime;
  private final long deploytime;
  private final ModelStatus status;
  private final String outcome;
  private final List<String> features;
  private final Set<String> categoricalFeatures;
  private final EvaluationMetrics evaluationMetrics;

  private ModelMeta(String id, String name, String description, String algorithm, String split,
                    @Nullable String predictionsDataset, ModelStatus status, List<String> directives,
                    Map<String, String> hyperparameters, List<String> features, String outcome,
                    Set<String> categoricalFeatures, long createtime, long trainingtime, long trainedtime,
                    Long deploytime, EvaluationMetrics evaluationMetrics) {
    super(name, description, algorithm, split, predictionsDataset, directives, hyperparameters);
    this.id = id;
    this.status = status;
    this.outcome = outcome;
    this.features = Collections.unmodifiableList(features);
    this.categoricalFeatures = Collections.unmodifiableSet(categoricalFeatures);
    this.createtime = createtime;
    this.trainingtime = trainingtime;
    this.trainedtime = trainedtime;
    this.deploytime = deploytime;
    this.evaluationMetrics = evaluationMetrics;
  }

  public String getId() {
    return id;
  }

  public ModelStatus getStatus() {
    return status;
  }

  public EvaluationMetrics getEvaluationMetrics() {
    return evaluationMetrics;
  }

  public long getCreatetime() {
    return createtime;
  }

  public long getTrainingtime() {
    return trainingtime;
  }

  public long getTrainedtime() {
    return trainedtime;
  }

  public long getDeploytime() {
    return deploytime;
  }

  public String getOutcome() {
    return outcome;
  }

  public List<String> getFeatures() {
    return features;
  }

  public Set<String> getCategoricalFeatures() {
    return categoricalFeatures;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    ModelMeta that = (ModelMeta) o;

    return Objects.equals(id, that.id) &&
      Objects.equals(createtime, that.createtime) &&
      Objects.equals(trainedtime, that.trainedtime) &&
      Objects.equals(deploytime, that.deploytime) &&
      Objects.equals(status, that.status) &&
      Objects.equals(outcome, that.outcome) &&
      Objects.equals(categoricalFeatures, that.categoricalFeatures) &&
      Objects.equals(evaluationMetrics, that.evaluationMetrics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, createtime, trainedtime, deploytime, status,
                        outcome, categoricalFeatures, evaluationMetrics);
  }

  public static Builder builder(String id) {
    return new Builder(id);
  }

  public static Builder builder(ModelMeta meta) {
    return new Builder(meta.id)
      .setCreateTime(meta.createtime)
      .setTrainingTime(meta.trainingtime)
      .setTrainedTime(meta.trainedtime)
      .setDeployTime(meta.deploytime)
      .setOutcome(meta.outcome)
      .setStatus(meta.status)
      .setFeatures(meta.features)
      .setCategoricalFeatures(meta.categoricalFeatures)
      .setEvaluationMetrics(meta.evaluationMetrics);
  }

  /**
   * Builds Model Metadata
   */
  public static class Builder extends Model.Builder<Builder> {
    private final String id;
    private long createtime;
    private long trainingtime;
    private long trainedtime;
    private long deploytime;
    private String outcome;
    private ModelStatus status;
    protected List<String> features;
    private Set<String> categoricalFeatures;
    private EvaluationMetrics evaluationMetrics;

    public Builder(String id) {
      this.id = id;
      this.features = new ArrayList<>();
      this.categoricalFeatures = new HashSet<>();
      this.trainingtime = -1L;
      this.trainedtime = -1L;
      this.deploytime = -1L;
      this.evaluationMetrics = new EvaluationMetrics(null, null, null, null, null, null, null);
    }

    public Builder setCreateTime(long createTime) {
      this.createtime = createTime;
      return this;
    }

    public Builder setDeployTime(long deployTime) {
      this.deploytime = deployTime;
      return this;
    }

    public Builder setTrainingTime(long trainingTime) {
      this.trainingtime = trainingTime;
      return this;
    }

    public Builder setTrainedTime(long trainedTime) {
      this.trainedtime = trainedTime;
      return this;
    }

    public Builder setOutcome(String outcome) {
      this.outcome = outcome;
      return this;
    }

    public Builder setStatus(ModelStatus status) {
      this.status = status;
      return this;
    }

    public Builder setCategoricalFeatures(Set<String> categoricalFeatures) {
      this.categoricalFeatures.clear();
      this.categoricalFeatures.addAll(categoricalFeatures);
      return this;
    }

    public Builder setEvaluationMetrics(EvaluationMetrics evaluationMetrics) {
      this.evaluationMetrics = evaluationMetrics;
      return this;
    }

    public Builder setFeatures(List<String> features) {
      this.features.clear();
      this.features.addAll(features);
      return this;
    }

    public ModelMeta build() {
      return new ModelMeta(id, name, description, algorithm, split, predictionsDataset, status, directives,
                           hyperparameters, features, outcome, categoricalFeatures, createtime,
                           trainingtime, trainedtime, deploytime, evaluationMetrics);
    }
  }
}
