/*
 * Copyright © 2015 Cask Data, Inc.
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

package io.cdap.cdap.proto;

/**
 * Represents a dataset in an HTTP response.
 */
public class DatasetDetail {

  private final String name;
  private final String classname;

  public DatasetDetail(String name, String classname) {
    this.name = name;
    this.classname = classname;
  }

  public String getName() {
    return name;
  }

  public String getClassname() {
    return classname;
  }
}
