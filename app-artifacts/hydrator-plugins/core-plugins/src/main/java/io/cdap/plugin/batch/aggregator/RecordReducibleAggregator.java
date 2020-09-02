/*
 * Copyright © 2020 Cask Data, Inc.
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

package io.cdap.plugin.batch.aggregator;

import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.etl.api.batch.BatchAggregatorContext;
import io.cdap.cdap.etl.api.batch.BatchReducibleAggregator;

import javax.annotation.Nullable;

/**
 * Base class for StructuredRecord based aggregators.
 *
 * @param <AGG_VALUE> Type of aggregation value
 */
public abstract class RecordReducibleAggregator<AGG_VALUE>
  extends BatchReducibleAggregator<StructuredRecord, StructuredRecord, AGG_VALUE, StructuredRecord> {

  @Nullable
  private final Integer numPartitions;

  protected RecordReducibleAggregator(@Nullable Integer numPartitions) {
    this.numPartitions = numPartitions;
  }

  @Override
  public void prepareRun(BatchAggregatorContext context) throws Exception {
    if (numPartitions != null) {
      context.setNumPartitions(numPartitions);
    }
  }
}
