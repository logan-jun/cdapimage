/*
 * Copyright © 2015-2019 Cask Data, Inc.
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

package io.cdap.plugin.batch;

import com.google.common.collect.ImmutableMap;
import io.cdap.cdap.api.common.Bytes;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.dataset.lib.KeyValueTable;
import io.cdap.cdap.api.dataset.lib.TimePartitionedFileSet;
import io.cdap.cdap.api.dataset.table.Put;
import io.cdap.cdap.api.dataset.table.Row;
import io.cdap.cdap.api.dataset.table.Table;
import io.cdap.cdap.etl.api.Transform;
import io.cdap.cdap.etl.api.batch.BatchSink;
import io.cdap.cdap.etl.api.batch.BatchSource;
import io.cdap.cdap.etl.proto.v2.ETLBatchConfig;
import io.cdap.cdap.etl.proto.v2.ETLPlugin;
import io.cdap.cdap.etl.proto.v2.ETLStage;
import io.cdap.cdap.proto.artifact.AppRequest;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.test.ApplicationManager;
import io.cdap.cdap.test.DataSetManager;
import io.cdap.plugin.common.Constants;
import io.cdap.plugin.common.Properties;
import io.cdap.plugin.format.text.input.TextInputFormatProvider;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Tests for ETLBatch.
 */
public class ETLMapReduceTestRun extends ETLBatchTestBase {
  private static final Schema TEXT_SCHEMA = TextInputFormatProvider.getDefaultSchema(null);

  @Test
  public void testInvalidTransformConfigFailsToDeploy() {
    ETLPlugin sourceConfig =
      new ETLPlugin("KVTable", BatchSource.PLUGIN_TYPE,
                    ImmutableMap.of(Properties.BatchReadableWritable.NAME, "table1"), null);
    ETLPlugin sink =
      new ETLPlugin("KVTable", BatchSink.PLUGIN_TYPE,
                    ImmutableMap.of(Properties.BatchReadableWritable.NAME, "table2"), null);

    ETLPlugin transform = new ETLPlugin("Script", Transform.PLUGIN_TYPE,
                                        ImmutableMap.of("script", "return x;"), null);
    ETLBatchConfig etlConfig = ETLBatchConfig.builder("* * * * *")
      .addStage(new ETLStage("source", sourceConfig))
      .addStage(new ETLStage("sink", sink))
      .addStage(new ETLStage("transform", transform))
      .addConnection("source", "transform")
      .addConnection("transform", "sink")
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app("KVToKV");
    try {
      deployApplication(appId, appRequest);
      Assert.fail();
    } catch (Exception e) {
      // expected
    }
  }

  @Test
  public void testKVToKV() throws Exception {
    // kv table to kv table pipeline
    ETLStage source = new ETLStage(
      "source", new ETLPlugin("KVTable", BatchSource.PLUGIN_TYPE,
                              ImmutableMap.of(Properties.BatchReadableWritable.NAME, "kvTable1"), null));
    ETLStage sink = new ETLStage(
      "sink", new ETLPlugin("KVTable", BatchSink.PLUGIN_TYPE,
                            ImmutableMap.of(Properties.BatchReadableWritable.NAME, "kvTable2"), null));
    ETLStage transform = new ETLStage("transform", new ETLPlugin("Projection", Transform.PLUGIN_TYPE,
                                                                 ImmutableMap.of(), null));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder("* * * * *")
      .addStage(source)
      .addStage(sink)
      .addStage(transform)
      .addConnection(source.getName(), transform.getName())
      .addConnection(transform.getName(), sink.getName())
      .build();

    ApplicationManager appManager = deployETL(etlConfig, "KVToKV");

    // add some data to the input table
    DataSetManager<KeyValueTable> table1 = getDataset("kvTable1");
    KeyValueTable inputTable = table1.get();
    for (int i = 0; i < 10000; i++) {
      inputTable.write("hello" + i, "world" + i);
    }
    table1.flush();

    runETLOnce(appManager);

    DataSetManager<KeyValueTable> table2 = getDataset("kvTable2");
    try (KeyValueTable outputTable = table2.get()) {
      for (int i = 0; i < 10000; i++) {
        Assert.assertEquals("world" + i, Bytes.toString(outputTable.read("hello" + i)));
      }
    }
  }

  @Test
  public void testDAG() throws Exception {
    Schema schema = Schema.recordOf(
      "userNames",
      Schema.Field.of("rowkey", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("userid", Schema.of(Schema.Type.STRING))
    );
    ETLStage source = new ETLStage(
      "source", new ETLPlugin("Table",
                              BatchSource.PLUGIN_TYPE,
                              ImmutableMap.of(
                                Properties.BatchReadableWritable.NAME, "dagInputTable",
                                Properties.Table.PROPERTY_SCHEMA_ROW_FIELD, "rowkey",
                                Properties.Table.PROPERTY_SCHEMA, schema.toString()),
                              null));
    ETLStage sink1 = new ETLStage(
      "sink1", new ETLPlugin("Table",
                             BatchSink.PLUGIN_TYPE,
                             ImmutableMap.of(
                               Properties.BatchReadableWritable.NAME, "dagOutputTable1",
                               Properties.Table.PROPERTY_SCHEMA_ROW_FIELD, "rowkey",
                               Properties.Table.PROPERTY_SCHEMA, schema.toString()),
                             null));
    ETLStage sink2 = new ETLStage(
      "sink2", new ETLPlugin("Table",
                             BatchSink.PLUGIN_TYPE,
                             ImmutableMap.of(
                               Properties.BatchReadableWritable.NAME, "dagOutputTable2",
                               Properties.Table.PROPERTY_SCHEMA_ROW_FIELD, "rowkey",
                               Properties.Table.PROPERTY_SCHEMA, schema.toString()),
                             null));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder("* * * * *")
      .addStage(source)
      .addStage(sink1)
      .addStage(sink2)
      .addConnection(source.getName(), sink2.getName())
      .addConnection(source.getName(), sink1.getName())
      .build();

    ApplicationManager appManager = deployETL(etlConfig, "DagApp");

    // add some data to the input table
    DataSetManager<Table> inputManager = getDataset("dagInputTable");
    Table inputTable = inputManager.get();

    for (int i = 0; i < 10; i++) {
      Put put = new Put(Bytes.toBytes("row" + i));
      // valid record, user name "sam[0-9]" is 4 chars long for validator transform
      put.add("userid", "sam" + i);
      inputTable.put(put);
      inputManager.flush();
    }

    runETLOnce(appManager);

    // all records are passed to this table (validation not performed)
    DataSetManager<Table> outputManager1 = getDataset("dagOutputTable1");
    Table outputTable1 = outputManager1.get();
    for (int i = 0; i < 10; i++) {
      Row row = outputTable1.get(Bytes.toBytes("row" + i));
      Assert.assertEquals("sam" + i, row.getString("userid"));
    }

    // only 10 records are passed to this table (validation performed)
    DataSetManager<Table> outputManager2 = getDataset("dagOutputTable2");
    Table outputTable2 = outputManager2.get();
    for (int i = 0; i < 10; i++) {
      Row row = outputTable2.get(Bytes.toBytes("row" + i));
      Assert.assertEquals("sam" + i, row.getString("userid"));
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testTableToTableWithValidations() throws Exception {

    Schema schema = Schema.recordOf(
      "purchase",
      Schema.Field.of("rowkey", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("user", Schema.of(Schema.Type.STRING)),
      Schema.Field.of("count", Schema.of(Schema.Type.INT)),
      Schema.Field.of("price", Schema.of(Schema.Type.DOUBLE)),
      Schema.Field.of("item", Schema.of(Schema.Type.STRING))
    );

    ETLStage source = new ETLStage(
      "source", new ETLPlugin("Table",
                              BatchSource.PLUGIN_TYPE,
                              ImmutableMap.of(
                                Properties.BatchReadableWritable.NAME, "inputTable",
                                Properties.Table.PROPERTY_SCHEMA_ROW_FIELD, "rowkey",
                                Properties.Table.PROPERTY_SCHEMA, schema.toString()),
                              null));

    ETLStage sink1 = new ETLStage(
      "sink1", new ETLPlugin("Table",
                             BatchSink.PLUGIN_TYPE,
                             ImmutableMap.of(
                               Properties.BatchReadableWritable.NAME, "outputTable",
                               Properties.Table.PROPERTY_SCHEMA_ROW_FIELD, "rowkey",
                               Properties.Table.PROPERTY_SCHEMA, schema.toString())));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder("* * * * *")
      .addStage(source)
      .addStage(sink1)
      .addConnection(source.getName(), sink1.getName())
      .build();

    ApplicationManager appManager = deployETL(etlConfig, "TableToTable");

    // add some data to the input table
    DataSetManager<Table> inputManager = getDataset("inputTable");
    Table inputTable = inputManager.get();

    // valid record, user name "samuel" is 6 chars long
    Put put = new Put(Bytes.toBytes("row1"));
    put.add("user", "samuel");
    put.add("count", 5);
    put.add("price", 123.45);
    put.add("item", "scotch");
    inputTable.put(put);
    inputManager.flush();

    // valid record, user name "jackson" is > 6 characters
    put = new Put(Bytes.toBytes("row2"));
    put.add("user", "jackson");
    put.add("count", 10);
    put.add("price", 123456789d);
    put.add("item", "island");
    inputTable.put(put);
    inputManager.flush();

    runETLOnce(appManager);

    DataSetManager<Table> outputManager = getDataset("outputTable");
    Table outputTable = outputManager.get();

    Row row = outputTable.get(Bytes.toBytes("row1"));
    Assert.assertEquals("samuel", row.getString("user"));
    Assert.assertEquals(5, (int) row.getInt("count"));
    Assert.assertTrue(Math.abs(123.45 - row.getDouble("price")) < 0.000001);
    Assert.assertEquals("scotch", row.getString("item"));
  }

  @Test
  public void testFiletoMultipleTPFS() throws Exception {
    String filePath = "file:///tmp/test/text.txt";
    String testData = "String for testing purposes.";

    Path textFile = new Path(filePath);
    Configuration conf = new Configuration();
    FileSystem fs = FileSystem.get(conf);
    FSDataOutputStream writeData = fs.create(textFile);
    writeData.write(testData.getBytes());
    writeData.flush();
    writeData.close();

    ETLStage source = new ETLStage(
      "source", new ETLPlugin("File", BatchSource.PLUGIN_TYPE,
                              ImmutableMap.<String, String>builder()
                                .put(Constants.Reference.REFERENCE_NAME, "TestFile")
                                .put(Properties.File.FILESYSTEM, "Text")
                                .put(Properties.File.PATH, filePath)
                                .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
                                .put(Properties.File.FORMAT, "text")
                                .build(),
                              null));

    ETLStage sink1 = new ETLStage(
      "sink1", new ETLPlugin("TPFSAvro", BatchSink.PLUGIN_TYPE,
                             ImmutableMap.of(Properties.TimePartitionedFileSetDataset.SCHEMA, TEXT_SCHEMA.toString(),
                                             Properties.TimePartitionedFileSetDataset.TPFS_NAME, "fileSink1"),
                             null));
    ETLStage sink2 = new ETLStage(
      "sink2", new ETLPlugin("TPFSParquet", BatchSink.PLUGIN_TYPE,
                             ImmutableMap.of(Properties.TimePartitionedFileSetDataset.SCHEMA, TEXT_SCHEMA.toString(),
                                             Properties.TimePartitionedFileSetDataset.TPFS_NAME, "fileSink2"),
                             null));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink1)
      .addStage(sink2)
      .addConnection(source.getName(), sink1.getName())
      .addConnection(source.getName(), sink2.getName())
      .build();

    ApplicationManager appManager = deployETL(etlConfig, "FileToTPFS");
    runETLOnce(appManager);

    for (String sinkName : new String[] { "fileSink1", "fileSink2" }) {
      DataSetManager<TimePartitionedFileSet> fileSetManager = getDataset(sinkName);
      try (TimePartitionedFileSet fileSet = fileSetManager.get()) {
        List<GenericRecord> records = readOutput(fileSet, TEXT_SCHEMA);
        Assert.assertEquals(1, records.size());
        Assert.assertEquals(testData, records.get(0).get("body").toString());
      }
    }
  }

  @Test
  public void testDuplicateStageNameInPipeline() throws Exception {
    String filePath = "file:///tmp/test/text.txt";

    ETLStage source = new ETLStage("source", new ETLPlugin("File", BatchSource.PLUGIN_TYPE,
                                                           ImmutableMap.<String, String>builder()
                                                             .put(Properties.File.FILESYSTEM, "Text")
                                                             .put(Properties.File.PATH, filePath)
                                                             .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
                                                             .build(),
                                                           null));

    ETLStage sink1 = new ETLStage(
      "sink", new ETLPlugin("TPFSAvro", BatchSink.PLUGIN_TYPE,
                            ImmutableMap.of(Properties.TimePartitionedFileSetDataset.SCHEMA, TEXT_SCHEMA.toString(),
                                            Properties.TimePartitionedFileSetDataset.TPFS_NAME, "fileSink1"),
                            null));
    // duplicate name for 2nd sink, should throw exception
    ETLStage sink2 = new ETLStage(
      "sink", new ETLPlugin("TPFSAvro", BatchSink.PLUGIN_TYPE,
                            ImmutableMap.of(Properties.TimePartitionedFileSetDataset.SCHEMA, TEXT_SCHEMA.toString(),
                                            Properties.TimePartitionedFileSetDataset.TPFS_NAME, "fileSink2"),
                            null));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink1)
      .addStage(sink2)
      .addConnection(source.getName(), sink1.getName())
      .addConnection(source.getName(), sink2.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app("FileToTPFS");

    // deploying would thrown an excpetion
    try {
      deployApplication(appId, appRequest);
      Assert.fail();
    } catch (Exception e) {
      // expected
    }
  }
}
