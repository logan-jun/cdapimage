/*
 * Copyright 2016-2019 Cask Data, Inc.
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

package io.cdap.cdap.security.authorization.sentry.model;

/**
 * Action supported on {@link Authorizable}
 */
public class ActionConstant {
  public static final String READ = "read";
  public static final String WRITE = "write";
  public static final String EXECUTE = "execute";
  public static final String ADMIN = "admin";
  public static final String ALL = "all";         // read + write + execute + admin
  public static final String ACTION_NAME = "action";
}
