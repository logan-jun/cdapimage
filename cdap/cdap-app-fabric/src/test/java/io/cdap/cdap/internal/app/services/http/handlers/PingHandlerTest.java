/*
 * Copyright © 2014 Cask Data, Inc.
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

package io.cdap.cdap.internal.app.services.http.handlers;

import io.cdap.cdap.internal.app.services.http.AppFabricTestBase;
import io.cdap.common.http.HttpResponse;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test ping handler.
 */
public class PingHandlerTest extends AppFabricTestBase {
  @Test
  public void testPing() throws Exception {
    HttpResponse response = doGet("/ping");
    Assert.assertEquals(200, response.getResponseCode());
  }

  @Test
  public void testStatus() throws Exception {
    HttpResponse response = doGet("/v3/system/services/appfabric/status");
    Assert.assertEquals(200, response.getResponseCode());
  }
}
