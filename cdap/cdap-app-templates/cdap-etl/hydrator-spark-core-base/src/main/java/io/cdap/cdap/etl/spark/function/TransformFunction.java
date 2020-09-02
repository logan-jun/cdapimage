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

package io.cdap.cdap.etl.spark.function;

import io.cdap.cdap.etl.api.Transform;
import io.cdap.cdap.etl.common.RecordInfo;
import io.cdap.cdap.etl.common.TrackedTransform;
import io.cdap.cdap.etl.spark.CombinedEmitter;

/**
 * Function that uses a Transform to perform a flatmap.
 * Non-serializable fields are lazily created since this is used in a Spark closure.
 *
 * @param <T> type of input object
 */
public class TransformFunction<T> implements FlatMapFunc<T, RecordInfo<Object>> {
  private final PluginFunctionContext pluginFunctionContext;
  private transient TrackedTransform<T, Object> transform;
  private transient CombinedEmitter<Object> emitter;

  public TransformFunction(PluginFunctionContext pluginFunctionContext) {
    this.pluginFunctionContext = pluginFunctionContext;
  }

  @Override
  public Iterable<RecordInfo<Object>> call(T input) throws Exception {
    if (transform == null) {
      Transform<T, Object> plugin = pluginFunctionContext.createPlugin();
      plugin.initialize(pluginFunctionContext.createBatchRuntimeContext());
      transform = new TrackedTransform<>(plugin, pluginFunctionContext.createStageMetrics(),
                                         pluginFunctionContext.getDataTracer(),
                                         pluginFunctionContext.getStageStatisticsCollector());
      emitter = new CombinedEmitter<>(pluginFunctionContext.getStageName());
    }
    emitter.reset();
    transform.transform(input, emitter);
    return emitter.getEmitted();
  }
}
