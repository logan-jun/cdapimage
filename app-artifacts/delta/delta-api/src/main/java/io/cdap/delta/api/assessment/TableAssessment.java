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

package io.cdap.delta.api.assessment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A detailed assessment of issues related to a table.
 */
public class TableAssessment {
  private final List<ColumnAssessment> columns;
  private final List<Problem> features;

  public TableAssessment(List<ColumnAssessment> columns, List<Problem> featureProblems) {
    this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
    this.features = Collections.unmodifiableList(new ArrayList<>(featureProblems));
  }

  public List<ColumnAssessment> getColumns() {
    return columns;
  }

  public List<Problem> getFeatureProblems() {
    return features;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TableAssessment that = (TableAssessment) o;
    return Objects.equals(columns, that.columns) &&
      Objects.equals(features, that.features);
  }

  @Override
  public int hashCode() {
    return Objects.hash(columns, features);
  }
}
