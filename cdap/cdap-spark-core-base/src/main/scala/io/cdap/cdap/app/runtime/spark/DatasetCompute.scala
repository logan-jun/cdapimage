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

package io.cdap.cdap.app.runtime.spark

import io.cdap.cdap.api.dataset.Dataset

import scala.reflect.ClassTag

/**
  * A trait for [[io.cdap.cdap.app.runtime.spark.data.DatasetRDD]]
  * to acquire [[io.cdap.cdap.api.dataset.Dataset]] instance and performs computation on it.
  */
trait DatasetCompute {

  /**
    * Performs computation on a [[io.cdap.cdap.api.dataset.Dataset]] instance.
    *
    * @param namespace namespace in which the dataset exists
    * @param datasetName name of the dataset
    * @param arguments arguments for the dataset
    * @param computeFunc function to operate on the dataset instance
    * @tparam T type of the result
    * @return result of the computation by the function
    */
  def apply[T: ClassTag](namespace: String, datasetName: String, arguments: Map[String, String],
                         computeFunc: Dataset => T): T
}
