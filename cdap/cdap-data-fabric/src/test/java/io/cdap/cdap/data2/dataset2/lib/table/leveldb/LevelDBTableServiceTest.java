/*
 * Copyright © 2014-2019 Cask Data, Inc.
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

package io.cdap.cdap.data2.dataset2.lib.table.leveldb;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.cdap.cdap.api.common.Bytes;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.guice.ConfigModule;
import io.cdap.cdap.common.guice.InMemoryDiscoveryModule;
import io.cdap.cdap.common.guice.NonCustomLocationUnitTestModule;
import io.cdap.cdap.data.runtime.DataFabricLevelDBModule;
import io.cdap.cdap.data.runtime.TransactionMetricsModule;
import io.cdap.cdap.data2.util.TableId;
import io.cdap.cdap.security.auth.context.AuthenticationContextModules;
import io.cdap.cdap.security.authorization.AuthorizationEnforcementModule;
import io.cdap.cdap.security.authorization.AuthorizationTestModule;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class LevelDBTableServiceTest {
  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  static LevelDBTableService service;
  static Injector injector = null;

  @BeforeClass
  public static void init() throws Exception {
    CConfiguration conf = CConfiguration.create();
    conf.set(Constants.CFG_LOCAL_DATA_DIR, tmpFolder.newFolder().getAbsolutePath());
    injector = Guice.createInjector(
      new ConfigModule(conf),
      new NonCustomLocationUnitTestModule(),
      new InMemoryDiscoveryModule(),
      new DataFabricLevelDBModule(),
      new TransactionMetricsModule(),
      new AuthorizationTestModule(),
      new AuthorizationEnforcementModule().getStandaloneModules(),
      new AuthenticationContextModules().getMasterModule());
    service = injector.getInstance(LevelDBTableService.class);
  }

  @Test
  public void testGetTableStats() throws Exception {
    String table1 = "cdap_default.table1";
    String table2 = "cdap_default.table2";
    TableId tableId1 = TableId.from("default", "table1");
    TableId tableId2 = TableId.from("default", "table2");
    Assert.assertNull(service.getTableStats().get(tableId1));
    Assert.assertNull(service.getTableStats().get(tableId2));

    service.ensureTableExists(table1);
    service.ensureTableExists(table2);
    // We sleep to allow ops flush out to disk
    TimeUnit.SECONDS.sleep(1);

    // NOTE: empty table may take non-zero disk space: it stores some meta files as well
    Assert.assertNotNull(service.getTableStats().get(tableId1));
    long table1Size = service.getTableStats().get(tableId1).getDiskSizeBytes();
    Assert.assertNotNull(service.getTableStats().get(tableId2));
    long table2Size = service.getTableStats().get(tableId2).getDiskSizeBytes();

    writeSome(table1);
    TimeUnit.SECONDS.sleep(1);

    long table1SizeUpdated = service.getTableStats().get(tableId1).getDiskSizeBytes();
    Assert.assertTrue(table1SizeUpdated > table1Size);
    table1Size = table1SizeUpdated;
    Assert.assertEquals(table2Size, service.getTableStats().get(tableId2).getDiskSizeBytes());

    writeSome(table1);
    writeSome(table2);
    TimeUnit.SECONDS.sleep(1);

    Assert.assertTrue(service.getTableStats().get(tableId1).getDiskSizeBytes() > table1Size);
    long table2SizeUpdated = service.getTableStats().get(tableId2).getDiskSizeBytes();
    Assert.assertTrue(table2SizeUpdated > table2Size);
    table2Size = table2SizeUpdated;

    service.dropTable(table1);
    TimeUnit.SECONDS.sleep(1);

    Assert.assertNull(service.getTableStats().get(tableId1));
    Assert.assertEquals(table2Size, service.getTableStats().get(tableId2).getDiskSizeBytes());
  }

  private void writeSome(String tableName) throws IOException {
    LevelDBTableCore table = new LevelDBTableCore(tableName, service);
    Random r = new Random();
    byte[] key = new byte[100];
    byte[] value = new byte[1024 * 1024];
    for (int i = 0; i < 8; i++) {
      r.nextBytes(key);
      r.nextBytes(value);
      table.put(key, Bytes.toBytes("column" + i), value, 0L);
    }
  }
}
