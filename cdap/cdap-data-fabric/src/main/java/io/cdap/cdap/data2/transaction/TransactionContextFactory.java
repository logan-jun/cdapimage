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

package io.cdap.cdap.data2.transaction;

import org.apache.tephra.TransactionContext;
import org.apache.tephra.TransactionFailureException;

/**
 * A factory for {@link TransactionContext}.
 */
public interface TransactionContextFactory {

  /**
   * Return a new transaction context for the current thread.
   *
   * @return a new transaction context
   */
  TransactionContext newTransactionContext() throws TransactionFailureException;
}
