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

package io.cdap.mmds.proto;

import io.cdap.mmds.data.ModelKey;


/**
 * Indicates a model was not found.
 */
public class ModelNotFoundException extends NotFoundException {

  public ModelNotFoundException(ModelKey modelKey) {
    super(String.format("Model '%s' in experiment '%s' not found.", modelKey.getModel(), modelKey.getExperiment()));
  }
}