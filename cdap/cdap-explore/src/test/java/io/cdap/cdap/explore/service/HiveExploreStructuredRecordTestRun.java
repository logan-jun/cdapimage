/*
 * Copyright © 2015-2017 Cask Data, Inc.
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

package io.cdap.cdap.explore.service;

import com.google.common.collect.Lists;
import io.cdap.cdap.api.Transactional;
import io.cdap.cdap.api.TxRunnable;
import io.cdap.cdap.api.data.DatasetContext;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.dataset.DatasetProperties;
import io.cdap.cdap.api.dataset.DatasetSpecification;
import io.cdap.cdap.api.dataset.ExploreProperties;
import io.cdap.cdap.api.dataset.lib.ObjectMappedTable;
import io.cdap.cdap.api.dataset.lib.ObjectMappedTableProperties;
import io.cdap.cdap.api.dataset.table.Table;
import io.cdap.cdap.api.dataset.table.TableProperties;
import io.cdap.cdap.data.dataset.SystemDatasetInstantiator;
import io.cdap.cdap.data2.dataset2.MultiThreadDatasetCache;
import io.cdap.cdap.data2.transaction.Transactions;
import io.cdap.cdap.explore.client.ExploreExecutionResult;
import io.cdap.cdap.explore.service.datasets.EmailTableDefinition;
import io.cdap.cdap.explore.service.datasets.TableWrapperDefinition;
import io.cdap.cdap.proto.ColumnDesc;
import io.cdap.cdap.proto.QueryResult;
import io.cdap.cdap.proto.QueryStatus;
import io.cdap.cdap.proto.id.DatasetId;
import io.cdap.cdap.proto.id.DatasetModuleId;
import io.cdap.cdap.test.SlowTests;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Tests exploration of record scannables that are scannables of StructuredRecord.
 */
@Category(SlowTests.class)
public class HiveExploreStructuredRecordTestRun extends BaseHiveExploreServiceTest {
  @ClassRule
  public static TemporaryFolder tmpFolder = new TemporaryFolder();

  private static Transactional transactional;

  @BeforeClass
  public static void start() throws Exception {
    initialize(tmpFolder);

    DatasetModuleId moduleId = NAMESPACE_ID.datasetModule("email");
    datasetFramework.addModule(moduleId, new EmailTableDefinition.EmailTableModule());
    datasetFramework.addInstance("email", MY_TABLE, DatasetProperties.EMPTY);

    transactional = Transactions.createTransactional(
      new MultiThreadDatasetCache(new SystemDatasetInstantiator(datasetFramework),
                                  transactionSystemClient, NAMESPACE_ID,
                                  Collections.<String, String>emptyMap(), null, null));

    transactional.execute(new TxRunnable() {
      @Override
      public void run(DatasetContext context) throws Exception {
        // Accessing dataset instance to perform data operations
        EmailTableDefinition.EmailTable table = context.getDataset(MY_TABLE.getDataset());
        Assert.assertNotNull(table);
        table.writeEmail("email1", "this is the subject", "this is the body", "sljackson@boss.com");
      }
    });

    datasetFramework.addModule(NAMESPACE_ID.datasetModule("TableWrapper"),
                               new TableWrapperDefinition.Module());
  }

  @AfterClass
  public static void stop() throws Exception {
    datasetFramework.deleteInstance(MY_TABLE);
    datasetFramework.deleteModule(NAMESPACE_ID.datasetModule("TableWrapper"));
  }

  @Test
  public void testCreateDropCustomDBAndTable() throws Exception {
    testCreateDropCustomDBAndTable("databasex", null);
    testCreateDropCustomDBAndTable(null, "tablex");
    testCreateDropCustomDBAndTable("databasey", "tabley");
  }

  private void testCreateDropCustomDBAndTable(@Nullable String database, @Nullable String tableName) throws Exception {
    String datasetName = "cdccat";
    DatasetId datasetId = NAMESPACE_ID.dataset(datasetName);
    ExploreProperties.Builder props = ExploreProperties.builder();
    if (tableName != null) {
      props.setExploreTableName(tableName);
    } else {
      tableName = getDatasetHiveName(datasetId);
    }
    if (database != null) {
      runCommand(NAMESPACE_ID, "create database if not exists " + database, false, null, null);
      props.setExploreDatabaseName(database);
    }
    try {
      datasetFramework.addInstance("email", datasetId, props.build());
      if (database == null) {
        runCommand(NAMESPACE_ID, "show tables", true, null,
                   Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(MY_TABLE_NAME)),
                                      new QueryResult(Lists.<Object>newArrayList(tableName))));
      } else {
        runCommand(NAMESPACE_ID, "show tables in " + database, true, null,
                   Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(tableName))));
      }
      datasetFramework.deleteInstance(datasetId);
      if (database == null) {
        runCommand(NAMESPACE_ID, "show tables", true, null,
                   Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(MY_TABLE_NAME))));
      } else {
        runCommand(NAMESPACE_ID, "show tables in " + database, false, null,
                   Collections.<QueryResult>emptyList());
      }
    } finally {
      if (database != null) {
        runCommand(NAMESPACE_ID, "drop database if exists " + database + "cascade", false, null, null);
      }
    }
  }


  @Test(expected = IllegalArgumentException.class)
  public void testMissingSchemaFails() throws Exception {
    DatasetId instanceId = NAMESPACE_ID.dataset("badtable");
    datasetFramework.addInstance("TableWrapper", instanceId, DatasetProperties.EMPTY);

    DatasetSpecification spec = datasetFramework.getDatasetSpec(instanceId);
    try {
      exploreTableManager.enableDataset(instanceId, spec, false);
    } finally {
      datasetFramework.deleteInstance(instanceId);
    }
  }

  @Test
  public void testRecordScannableAndWritableIsOK() throws Exception {
    DatasetId instanceId = NAMESPACE_ID.dataset("tabul");
    datasetFramework.addInstance("TableWrapper", instanceId, DatasetProperties.builder()
      .add(DatasetProperties.SCHEMA,
           Schema.recordOf("intRecord", Schema.Field.of("x", Schema.of(Schema.Type.STRING))).toString())
      .build());

    DatasetSpecification spec = datasetFramework.getDatasetSpec(instanceId);
    try {
      exploreTableManager.enableDataset(instanceId, spec, false);
      runCommand(NAMESPACE_ID, "describe dataset_tabul",
        true,
        Lists.newArrayList(
          new ColumnDesc("col_name", "STRING", 1, "from deserializer"),
          new ColumnDesc("data_type", "STRING", 2, "from deserializer"),
          new ColumnDesc("comment", "STRING", 3, "from deserializer")
        ),
        Lists.newArrayList(
          new QueryResult(Lists.<Object>newArrayList("x", "string", "from deserializer"))
        )
      );
    } finally {
      datasetFramework.deleteInstance(instanceId);
    }
  }

  @Test
  public void testSchema() throws Exception {
    runCommand(NAMESPACE_ID, "describe " + MY_TABLE_NAME,
               true,
               Lists.newArrayList(
                 new ColumnDesc("col_name", "STRING", 1, "from deserializer"),
                 new ColumnDesc("data_type", "STRING", 2, "from deserializer"),
                 new ColumnDesc("comment", "STRING", 3, "from deserializer")
               ),
               Lists.newArrayList(
                 new QueryResult(Lists.<Object>newArrayList("id", "string", "from deserializer")),
                 new QueryResult(Lists.<Object>newArrayList("subject", "string", "from deserializer")),
                 new QueryResult(Lists.<Object>newArrayList("body", "string", "from deserializer")),
                 new QueryResult(Lists.<Object>newArrayList("sender", "string", "from deserializer"))
               )
    );
  }

  @Test
  public void testSelectStar() throws Exception {
    List<ColumnDesc> expectedSchema = Lists.newArrayList(
      new ColumnDesc(MY_TABLE_NAME + ".id", "STRING", 1, null),
      new ColumnDesc(MY_TABLE_NAME + ".subject", "STRING", 2, null),
      new ColumnDesc(MY_TABLE_NAME + ".body", "STRING", 3, null),
      new ColumnDesc(MY_TABLE_NAME + ".sender", "STRING", 4, null)
    );
    ExploreExecutionResult results = exploreClient.submit(NAMESPACE_ID, "select * from " + MY_TABLE_NAME).get();
    // check schema
    Assert.assertEquals(expectedSchema, results.getResultSchema());
    List<Object> columns = results.next().getColumns();
    // check results
    Assert.assertEquals("email1", columns.get(0));
    Assert.assertEquals("this is the subject", columns.get(1));
    Assert.assertEquals("this is the body", columns.get(2));
    Assert.assertEquals("sljackson@boss.com", columns.get(3));
    // should not be any more
    Assert.assertFalse(results.hasNext());
  }

  @Test
  public void testSelect() throws Exception {
    String command = String.format("select sender from %s where body='this is the body'", MY_TABLE_NAME);
    runCommand(NAMESPACE_ID, command,
               true,
               Lists.newArrayList(new ColumnDesc("sender", "STRING", 1, null)),
               Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList("sljackson@boss.com")))
    );
  }

  @Test
  public void testInsert() throws Exception {
    DatasetId copyTable = NAMESPACE_ID.dataset("emailCopy");
    datasetFramework.addInstance(Table.class.getName(), copyTable, TableProperties.builder()
      .setSchema(EmailTableDefinition.SCHEMA)
      .setRowFieldName("id")
      .build());
    try {
      String command = String.format("insert into %s select * from %s",
        getDatasetHiveName(copyTable), MY_TABLE_NAME);
      ExploreExecutionResult result = exploreClient.submit(NAMESPACE_ID, command).get();
      Assert.assertEquals(QueryStatus.OpStatus.FINISHED, result.getStatus().getStatus());

      command = String.format("select id, subject, body, sender from %s", getDatasetHiveName(copyTable));
      runCommand(NAMESPACE_ID, command,
        true,
        Lists.newArrayList(new ColumnDesc("id", "STRING", 1, null),
                           new ColumnDesc("subject", "STRING", 2, null),
                           new ColumnDesc("body", "STRING", 3, null),
                           new ColumnDesc("sender", "STRING", 4, null)),
        Lists.newArrayList(new QueryResult(Lists.<Object>newArrayList(
          "email1", "this is the subject", "this is the body", "sljackson@boss.com")))
      );
    } finally {
      datasetFramework.deleteInstance(copyTable);
    }
  }

  @Test
  public void testObjectMappedTable() throws Exception {
    // Add a ObjectMappedTable instance
    final DatasetId datasetId = NAMESPACE_ID.dataset("person");
    datasetFramework.addInstance(ObjectMappedTable.class.getName(), datasetId,
                                 ObjectMappedTableProperties.builder()
                                   .setType(Person.class)
                                   .setRowKeyExploreName("id")
                                   .setRowKeyExploreType(Schema.Type.STRING)
                                   .build());

    // Insert data using sql
    String command = String.format("INSERT into %s (id, firstname, lastname, age) VALUES (\"%s\", \"%s\", \"%s\", %d)",
                                   getDatasetHiveName(datasetId), "bobby", "Bobby", "Bob", 15);
    ExploreExecutionResult result = exploreClient.submit(NAMESPACE_ID, command).get();
    Assert.assertEquals(QueryStatus.OpStatus.FINISHED, result.getStatus().getStatus());

    transactional.execute(new TxRunnable() {
      @Override
      public void run(DatasetContext context) throws Exception {
        // Read the data back via dataset directly
        ObjectMappedTable<Person> objTable = context.getDataset(datasetId.getDataset());

        Person person = objTable.read("bobby");
        Assert.assertNotNull(person);
        Assert.assertEquals("Bobby", person.getFirstName());
        Assert.assertEquals("Bob", person.getLastName());
        Assert.assertEquals(15, person.getAge());
      }
    });

    // Delete the dataset, hence also drop the table.
    datasetFramework.deleteInstance(datasetId);
  }
}
