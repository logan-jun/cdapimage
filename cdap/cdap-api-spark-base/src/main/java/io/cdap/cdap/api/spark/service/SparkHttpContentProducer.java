/*
 * Copyright © 2017 Cask Data, Inc.
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

package io.cdap.cdap.api.spark.service;

import io.cdap.cdap.api.annotation.TransactionControl;
import io.cdap.cdap.api.annotation.TransactionPolicy;
import io.cdap.cdap.api.service.http.HttpContentProducer;

/**
 * A {@link HttpContentProducer} for {@link SparkHttpServiceHandler} to stream large response body.
 * It is more suitable for Spark handler use cases that it uses {@link TransactionControl#EXPLICIT explicit transaction}
 * for the {@link #onFinish()} and {@link #onError(Throwable)} methods.
 */
public abstract class SparkHttpContentProducer extends HttpContentProducer {

  @TransactionPolicy(TransactionControl.EXPLICIT)
  @Override
  public abstract void onFinish() throws Exception;

  @TransactionPolicy(TransactionControl.EXPLICIT)
  @Override
  public abstract void onError(Throwable failureCause);
}
