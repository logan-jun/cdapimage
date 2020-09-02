/*
 * Copyright © 2019 Cask Data, Inc.
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


package io.cdap.plugin.batch.aggregator.function;

import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Collect List of a specific column
 * @param <T> type of aggregate value
 */
public class CollectList<T> implements AggregateFunction<List<T>, CollectList<T>> {
  private final String fieldName;
  private final Schema fieldSchema;
  private List<T> result;

  public CollectList(String fieldName, Schema fieldSchema) {
    this.fieldName = fieldName;
    this.fieldSchema = fieldSchema;
  }

  @Override
  public void initialize() {
    this.result = new ArrayList<>();
  }

  @Override
  public void mergeValue(StructuredRecord record) {
    result.add(record.get(fieldName));
  }

  @Override
  public void mergeAggregates(CollectList<T> otherAgg) {
    result.addAll(otherAgg.result);
  }

  @Override
  public List<T> getAggregate() {
    return result;
  }

  @Override
  public Schema getOutputSchema() {
    return Schema.arrayOf(fieldSchema);
  }
}
