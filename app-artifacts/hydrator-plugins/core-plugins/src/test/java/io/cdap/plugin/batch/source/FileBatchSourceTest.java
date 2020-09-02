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

package io.cdap.plugin.batch.source;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.cdap.cdap.api.common.Bytes;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.dataset.DatasetProperties;
import io.cdap.cdap.api.dataset.lib.TimePartitionedFileSet;
import io.cdap.cdap.api.dataset.table.Table;
import io.cdap.cdap.api.metadata.MetadataEntity;
import io.cdap.cdap.api.metadata.MetadataScope;
import io.cdap.cdap.datapipeline.SmartWorkflow;
import io.cdap.cdap.etl.api.batch.BatchSource;
import io.cdap.cdap.etl.mock.batch.MockSink;
import io.cdap.cdap.etl.proto.v2.ETLBatchConfig;
import io.cdap.cdap.etl.proto.v2.ETLPlugin;
import io.cdap.cdap.etl.proto.v2.ETLStage;
import io.cdap.cdap.format.StructuredRecordStringConverter;
import io.cdap.cdap.proto.ProgramRunStatus;
import io.cdap.cdap.proto.artifact.AppRequest;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.test.ApplicationManager;
import io.cdap.cdap.test.DataSetManager;
import io.cdap.cdap.test.TestConfiguration;
import io.cdap.cdap.test.WorkflowManager;
import io.cdap.plugin.batch.ETLBatchTestBase;
import io.cdap.plugin.common.Constants;
import io.cdap.plugin.common.Properties;
import io.cdap.plugin.format.FileFormat;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.io.DatumWriter;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Tests to verify configuration of {@link FileBatchSource}
 */
public class FileBatchSourceTest extends ETLBatchTestBase {

  @ClassRule
  public static final TestConfiguration CONFIG = new TestConfiguration("explore.enabled", false);
  private static final Schema RECORD_SCHEMA = Schema.recordOf("record",
                                                              Schema.Field.of("i", Schema.of(Schema.Type.INT)),
                                                              Schema.Field.of("l", Schema.of(Schema.Type.LONG)),
                                                              Schema.Field.of("file",
                                                                              Schema.of(Schema.Type.STRING)));
  @ClassRule
  public static TemporaryFolder temporaryFolder = new TemporaryFolder();
  private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
  private static String fileName = dateFormat.format(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)));

  @Test
  public void testIgnoreNonExistingFolder() throws Exception {
    Map<String, String> sourceProperties = new ImmutableMap.Builder<String, String>()
      .put(Constants.Reference.REFERENCE_NAME, "TestCase")
      .put(Properties.File.PATH, "/src/test/resources/path_one/")
      .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "true")
      .put(Properties.File.FORMAT, "text")
      .build();

    ETLStage source = new ETLStage("FileInput", new ETLPlugin("File", BatchSource.PLUGIN_TYPE, sourceProperties, null));

    String outputDatasetName = "ignore-non-existing-files";
    ETLStage sink = new ETLStage("sink", MockSink.getPlugin(outputDatasetName));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app("FileTest-ignore-non-existing-files");

    ApplicationManager appManager = deployApplication(appId, appRequest);

    WorkflowManager workflowManager = appManager.getWorkflowManager(SmartWorkflow.NAME);
    workflowManager.start();
    workflowManager.waitForRuns(ProgramRunStatus.COMPLETED, 1, 5, TimeUnit.MINUTES);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);

    Assert.assertEquals("Expected records", 0, output.size());
  }

  @Test
  public void testNotPresentFolder() throws Exception {
    Map<String, String> sourceProperties = new ImmutableMap.Builder<String, String>()
      .put(Constants.Reference.REFERENCE_NAME, "TestCase")
      .put(Properties.File.PATH, "/src/test/resources/path_one/")
      .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
      .put(Properties.File.FORMAT, "text")
      .build();

    ETLStage source = new ETLStage("FileInput", new ETLPlugin("File", BatchSource.PLUGIN_TYPE, sourceProperties, null));

    String outputDatasetName = "output-batchsourcetest";
    ETLStage sink = new ETLStage("sink", MockSink.getPlugin(outputDatasetName));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app("FileTest-not-present-folder");

    ApplicationManager appManager = deployApplication(appId, appRequest);

    WorkflowManager workflowManager = appManager.getWorkflowManager(SmartWorkflow.NAME);
    workflowManager.start();
    workflowManager.waitForRuns(ProgramRunStatus.FAILED, 1, 5, TimeUnit.MINUTES);
  }

  @Test
  public void testRecursiveFolders() throws Exception {
    Schema schema = Schema.recordOf("file.record",
                                    Schema.Field.of("offset", Schema.of(Schema.Type.LONG)),
                                    Schema.Field.of("body", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                                    Schema.Field.of("file", Schema.nullableOf(Schema.of(Schema.Type.STRING))));
    Map<String, String> sourceProperties = new ImmutableMap.Builder<String, String>()
      .put(Constants.Reference.REFERENCE_NAME, "TestCase")
      .put(Properties.File.PATH, "src/test/resources/")
      .put(Properties.File.FILE_REGEX, "[a-zA-Z0-9\\-:/_]*/x/[a-z0-9]*.txt$")
      .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
      .put(Properties.File.RECURSIVE, "true")
      .put("pathField", "file")
      .put("filenameOnly", "true")
      .put(Properties.File.SCHEMA, schema.toString())
      .put(Properties.File.FORMAT, "text")
      .build();

    ETLStage source = new ETLStage("FileInput", new ETLPlugin("File", BatchSource.PLUGIN_TYPE, sourceProperties, null));

    String outputDatasetName = "recursive-folders";
    ETLStage sink = new ETLStage("sink", MockSink.getPlugin(outputDatasetName));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app("FileTest-recursive-folders");

    ApplicationManager appManager = deployApplication(appId, appRequest);

    WorkflowManager workflowManager = appManager.getWorkflowManager(SmartWorkflow.NAME);
    workflowManager.start();
    workflowManager.waitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);

    Set<StructuredRecord> expected = ImmutableSet.of(
      StructuredRecord.builder(schema).set("offset", 0L).set("body", "Hello,World").set("file", "test1.txt").build(),
      StructuredRecord.builder(schema).set("offset", 0L).set("body", "CDAP,Platform").set("file", "test3.txt").build());
    Set<StructuredRecord> actual = new HashSet<>(MockSink.readOutput(outputManager));
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testNonRecursiveRegex() throws Exception {
    Map<String, String> sourceProperties = new ImmutableMap.Builder<String, String>()
      .put(Constants.Reference.REFERENCE_NAME, "TestCase")
      .put(Properties.File.PATH, "src/test/resources/")
      .put(Properties.File.FILE_REGEX, ".+fileBatchSource.*")
      .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
      .put(Properties.File.RECURSIVE, "false")
      .put(Properties.File.FORMAT, "text")
      .build();

    ETLStage source = new ETLStage("FileInput", new ETLPlugin("File", BatchSource.PLUGIN_TYPE, sourceProperties, null));

    String outputDatasetName = "non-recursive-regex";
    ETLStage sink = new ETLStage("sink", MockSink.getPlugin(outputDatasetName));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app("FileTest-non-recursive-regex");

    ApplicationManager appManager = deployApplication(appId, appRequest);

    WorkflowManager workflowManager = appManager.getWorkflowManager(SmartWorkflow.NAME);
    workflowManager.start();
    workflowManager.waitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);

    Assert.assertEquals("Expected records", 1, output.size());
    Set<String> outputValue = new HashSet<>();
    for (StructuredRecord record : output) {
      outputValue.add(record.get("body"));
    }
    Assert.assertTrue(outputValue.contains("CDAP,Platform"));
  }

  @Test
  public void testFileRegex() throws Exception {
    Map<String, String> sourceProperties = new ImmutableMap.Builder<String, String>()
      .put(Constants.Reference.REFERENCE_NAME, "TestCase")
      .put(Properties.File.PATH, "src/test/resources/test1/x/")
      .put(Properties.File.FILE_REGEX, ".+test.*")
      .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
      .put(Properties.File.RECURSIVE, "false")
      .put(Properties.File.FORMAT, "text")
      .build();

    ETLStage source = new ETLStage("FileInput", new ETLPlugin("File", BatchSource.PLUGIN_TYPE, sourceProperties, null));

    String outputDatasetName = "file-regex";
    ETLStage sink = new ETLStage("sink", MockSink.getPlugin(outputDatasetName));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app("FileTest-file-Regex");

    ApplicationManager appManager = deployApplication(appId, appRequest);

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);

    Assert.assertEquals("Expected records", 1, output.size());
    Set<String> outputValue = new HashSet<>();
    for (StructuredRecord record : output) {
      outputValue.add(record.get("body"));
    }
    Assert.assertTrue(outputValue.contains("CDAP,Platform"));
  }

  @Test
  public void testRecursiveRegex() throws Exception {
    Map<String, String> sourceProperties = new ImmutableMap.Builder<String, String>()
      .put(Constants.Reference.REFERENCE_NAME, "TestCase")
      .put(Properties.File.PATH, "src/test/resources/")
      .put(Properties.File.FILE_REGEX, ".+fileBatchSource.*")
      .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
      .put(Properties.File.RECURSIVE, "true")
      .put(Properties.File.FORMAT, "text")
      .build();

    ETLStage source = new ETLStage("FileInput", new ETLPlugin("File", BatchSource.PLUGIN_TYPE, sourceProperties, null));

    String outputDatasetName = "recursive-regex";
    ETLStage sink = new ETLStage("sink", MockSink.getPlugin(outputDatasetName));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app("FileTest-recursive-regex");

    ApplicationManager appManager = deployApplication(appId, appRequest);
    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);

    Assert.assertEquals("Expected records", 2, output.size());
    Set<String> outputValue = new HashSet<>();
    for (StructuredRecord record : output) {
      outputValue.add(record.get("body"));
    }
    Assert.assertTrue(outputValue.contains("Hello,World"));
    Assert.assertTrue(outputValue.contains("CDAP,Platform"));
  }

  @Test
  public void testPathGlobbing() throws Exception {
    Map<String, String> sourceProperties = new ImmutableMap.Builder<String, String>()
      .put(Constants.Reference.REFERENCE_NAME, "TestCase")
      .put(Properties.File.PATH, "src/test/resources/*/x/")
      .put(Properties.File.FILE_REGEX, ".+.txt")
      .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
      .put(Properties.File.RECURSIVE, "false")
      .put(Properties.File.FORMAT, "text")
      .build();

    ETLStage source = new ETLStage("FileInput", new ETLPlugin("File", BatchSource.PLUGIN_TYPE, sourceProperties, null));

    String outputDatasetName = "path-globbing";
    ETLStage sink = new ETLStage("sink", MockSink.getPlugin(outputDatasetName));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app("FileTest-path-globbing");

    ApplicationManager appManager = deployApplication(appId, appRequest);
    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);

    Assert.assertEquals("Expected records", 2, output.size());
    Set<String> outputValue = new HashSet<>();
    for (StructuredRecord record : output) {
      outputValue.add(record.get("body"));
    }
    Assert.assertTrue(outputValue.contains("Hello,World"));
    Assert.assertTrue(outputValue.contains("CDAP,Platform"));
  }

  @Test
  public void testSkipHeader() throws Exception {
    Schema schema = Schema.recordOf("user",
                                    Schema.Field.of("body", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("offset", Schema.of(Schema.Type.LONG)));
    Set<StructuredRecord> expected = ImmutableSet.of(
      StructuredRecord.builder(schema).set("body", "123").set("offset", 4L).build(),
      StructuredRecord.builder(schema).set("body", "456").set("offset", 8L).build());
    Assert.assertEquals(expected, testSkipHeader("text", "", schema));;

    schema = Schema.recordOf("user",
                             Schema.Field.of("val1", Schema.of(Schema.Type.INT)),
                             Schema.Field.of("val2", Schema.of(Schema.Type.INT)),
                             Schema.Field.of("val3", Schema.of(Schema.Type.INT)));
    expected = ImmutableSet.of(
      StructuredRecord.builder(schema).set("val1", 1).set("val2", 2).set("val3", 3).build(),
      StructuredRecord.builder(schema).set("val1", 4).set("val2", 5).set("val3", 6).build());
    Assert.assertEquals(expected, testSkipHeader("csv", ",", schema));
    Assert.assertEquals(expected, testSkipHeader("tsv", "\t", schema));
    Assert.assertEquals(expected, testSkipHeader("delimited", " ", schema));
  }

  private Set<StructuredRecord> testSkipHeader(String format, String delimeter, Schema schema) throws Exception {
    File inputFile = temporaryFolder.newFile();

    try (Writer writer = new FileWriter(inputFile)) {
      writer.write(Joiner.on(delimeter).join(new String[] {"a", "b", "c"}) + "\n");
      writer.write(Joiner.on(delimeter).join(new String[] {"1", "2", "3"}) + "\n");
      writer.write(Joiner.on(delimeter).join(new String[] {"4", "5", "6"}) + "\n");
    }

    String outputDatasetName = UUID.randomUUID().toString();
    String appName = UUID.randomUUID().toString();

    String uri = inputFile.toURI().toString();
    ApplicationManager appManager = createSourceAndDeployApp(appName, inputFile, format, outputDatasetName, schema,
                                                             delimeter, true, false);

    appManager.getWorkflowManager(SmartWorkflow.NAME).startAndWaitForRun(ProgramRunStatus.COMPLETED,
                                                                         5, TimeUnit.MINUTES);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    return new HashSet<>(MockSink.readOutput(outputManager));
  }

  @Test
  public void testCopyHeader() throws Exception {
    File inputFile = temporaryFolder.newFile();

    try (Writer writer = new FileWriter(inputFile)) {
      writer.write("header123\n");
      writer.write("123456789\n");
      writer.write("987654321\n");
    }

    // each line is 10 bytes. So a max split size of 10 should break the file into 3 splits.
    Map<String, String> sourceProperties = new ImmutableMap.Builder<String, String>()
      .put(Constants.Reference.REFERENCE_NAME, "CopyHeader")
      .put("copyHeader", "true")
      .put("maxSplitSize", "10")
      .put(Properties.File.FORMAT, "text")
      .put(Properties.File.PATH, inputFile.getAbsolutePath())
      .build();

    ETLStage source = new ETLStage("FileInput", new ETLPlugin("File", BatchSource.PLUGIN_TYPE, sourceProperties, null));

    String outputDatasetName = "copyHeaderOutput";
    ETLStage sink = new ETLStage("sink", MockSink.getPlugin(outputDatasetName));

    ETLBatchConfig config = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, config);
    ApplicationId appId = NamespaceId.DEFAULT.app("CopyHeaderTest");

    ApplicationManager appManager = deployApplication(appId, appRequest);
    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);

    Map<String, Integer> expected = new HashMap<>();
    expected.put("header123", 3);
    expected.put("123456789", 1);
    expected.put("987654321", 1);
    Map<String, Integer> actual = new HashMap<>();
    for (StructuredRecord record : MockSink.readOutput(outputManager)) {
      String body = record.get("body");
      if (actual.containsKey(body)) {
        actual.put(body, actual.get(body) + 1);
      } else {
        actual.put(body, 1);
      }
    }
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testReadBlob() throws Exception {
    File testFolder = temporaryFolder.newFolder();
    File file1 = new File(testFolder, "test1");
    File file2 = new File(testFolder, "test2");
    File file3 = new File(testFolder, "empty");
    String outputDatasetName = UUID.randomUUID().toString();

    Schema schema = Schema.recordOf("blob",
                                    Schema.Field.of("body", Schema.of(Schema.Type.BYTES)),
                                    Schema.Field.of("file", Schema.nullableOf(Schema.of(Schema.Type.STRING))));

    String appName = UUID.randomUUID().toString();
    ApplicationManager appManager = createSourceAndDeployApp(appName, testFolder, FileFormat.BLOB.name(),
                                                             outputDatasetName, schema);

    String content1 = "abc\ndef\nghi\njkl";
    FileUtils.writeStringToFile(file1, content1);
    String content2 = "123\n456\n789";
    FileUtils.writeStringToFile(file2, content2);
    file3.createNewFile();

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    byte[] byteContent1 = content1.getBytes(StandardCharsets.US_ASCII);
    byte[] byteContent2 = content2.getBytes(StandardCharsets.US_ASCII);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);
    Assert.assertEquals(2, output.size());

    Map<String, byte[]> contents = new HashMap<>();
    for (StructuredRecord outputRecord : output) {
      contents.put(outputRecord.get("file"), Bytes.toBytes((ByteBuffer) outputRecord.get("body")));
    }
    Assert.assertArrayEquals(byteContent1, contents.get(file1.toURI().toString()));
    Assert.assertArrayEquals(byteContent2, contents.get(file2.toURI().toString()));
  }

  @Test
  public void testReadBlobWithSplits() throws Exception {
    // Should generate only one record per split
    // There should only be one split per file
    File testFolder = temporaryFolder.newFolder();
    File file1 = new File(testFolder, "test1");
    File file2 = new File(testFolder, "test2");
    String outputDatasetName = UUID.randomUUID().toString();

    Schema schema = Schema.recordOf("blob",
                                    Schema.Field.of("body", Schema.of(Schema.Type.BYTES)),
                                    Schema.Field.of("file", Schema.nullableOf(Schema.of(Schema.Type.STRING))));

    String appName = UUID.randomUUID().toString();
    ImmutableMap.Builder<String, String> sourceProperties = ImmutableMap.<String, String>builder()
      .put(Constants.Reference.REFERENCE_NAME, appName + "TestFile")
      .put(Properties.File.PATH, testFolder.getAbsolutePath())
      .put(Properties.File.FORMAT, FileFormat.BLOB.name())
      .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
      .put("skipHeader", "false")
      .put("pathField", "file")
      .put(Properties.File.SCHEMA, schema.toString())
      .put("maxSplitSize", "1");

    ApplicationManager appManager = createSourceAndDeployApp(appName, outputDatasetName, sourceProperties.build());

    String content1 = "abc\ndef\nghi\njkl";
    FileUtils.writeStringToFile(file1, content1);
    String content2 = "123\n456\n789";
    FileUtils.writeStringToFile(file2, content2);

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    byte[] byteContent1 = content1.getBytes(StandardCharsets.US_ASCII);
    byte[] byteContent2 = content2.getBytes(StandardCharsets.US_ASCII);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);
    Assert.assertEquals(2, output.size());

    Map<String, byte[]> contents = new HashMap<>();
    for (StructuredRecord outputRecord : output) {
      contents.put(outputRecord.get("file"), Bytes.toBytes((ByteBuffer) outputRecord.get("body")));
    }
    Assert.assertArrayEquals(byteContent1, contents.get(file1.toURI().toString()));
    Assert.assertArrayEquals(byteContent2, contents.get(file2.toURI().toString()));
  }

  @Test
  public void testReadJson() throws Exception {
    File fileText = new File(temporaryFolder.newFolder(), "test.json");
    String outputDatasetName = UUID.randomUUID().toString();

    Schema schema = Schema.recordOf("user",
                                    Schema.Field.of("id", Schema.of(Schema.Type.LONG)),
                                    Schema.Field.of("name", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                                    Schema.Field.of("file", Schema.nullableOf(Schema.of(Schema.Type.STRING))));

    String appName = UUID.randomUUID().toString();
    ApplicationManager appManager = createSourceAndDeployApp(appName, fileText, FileFormat.JSON.name(),
                                                             outputDatasetName, schema);

    StructuredRecord record1 = StructuredRecord.builder(schema).set("id", 0L).set("name", "Sam").build();
    StructuredRecord record2 = StructuredRecord.builder(schema).set("id", 1L).build();
    String fileContent = StructuredRecordStringConverter.toJsonString(record1) + "\n"
      + StructuredRecordStringConverter.toJsonString(record2);
    FileUtils.writeStringToFile(fileText, fileContent);

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    StructuredRecord expected1 = StructuredRecord.builder(schema)
      .set("id", record1.get("id"))
      .set("name", record1.get("name"))
      .set("file", fileText.toURI().toString()).build();
    StructuredRecord expected2 = StructuredRecord.builder(schema)
      .set("id", record2.get("id"))
      .set("name", record2.get("name"))
      .set("file", fileText.toURI().toString()).build();
    Set<StructuredRecord> expected = ImmutableSet.of(expected1, expected2);

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    Set<StructuredRecord> output = new HashSet<>(MockSink.readOutput(outputManager));

    Assert.assertEquals(expected, output);
  }

  @Test
  public void testReadCSV() throws Exception {
    testReadDelimitedText(FileFormat.CSV.name(), ",");
  }

  @Test
  public void testReadTSV() throws Exception {
    testReadDelimitedText(FileFormat.TSV.name(), "\t");
  }

  @Test
  public void testReadDelimited() throws Exception {
    testReadDelimitedText(FileFormat.DELIMITED.name(), "\u0001");
  }

  private void testReadDelimitedText(String format, String delimiter) throws Exception {
    File fileText = new File(temporaryFolder.newFolder(), "test.txt");
    String outputDatasetName = UUID.randomUUID().toString();

    Schema schema = Schema.recordOf("user",
                                    Schema.Field.of("id", Schema.of(Schema.Type.LONG)),
                                    Schema.Field.of("name", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                                    Schema.Field.of("file", Schema.nullableOf(Schema.of(Schema.Type.STRING))));

    String appName = UUID.randomUUID().toString();
    ApplicationManager appManager = createSourceAndDeployApp(appName, fileText, format, outputDatasetName, schema,
                                                             delimiter, false, true);

    String inputStr = new StringBuilder()
      .append("0").append("\n")
      .append("1").append(delimiter).append("\n")
      .append("2").append(delimiter).append("sam\n").toString();
    FileUtils.writeStringToFile(fileText, inputStr);

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    Set<StructuredRecord> expected = ImmutableSet.of(
      StructuredRecord.builder(schema).set("id", 0L).set("file", fileText.toURI().toString()).build(),
      StructuredRecord.builder(schema).set("id", 1L).set("file", fileText.toURI().toString()).build(),
      StructuredRecord.builder(schema).set("id", 2L).set("name", "sam").set("file", fileText.toURI().toString()).build()
    );

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    Set<StructuredRecord> output = new HashSet<>(MockSink.readOutput(outputManager));

    Assert.assertEquals(expected, output);
  }

  @Test
  public void testTextFormatWithoutOffset() throws Exception {
    File fileText = new File(temporaryFolder.newFolder(), "test.txt");
    String outputDatasetName = UUID.randomUUID().toString();

    Schema textSchema = Schema.recordOf("file.record",
                                        Schema.Field.of("body", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                                        Schema.Field.of("file", Schema.nullableOf(Schema.of(Schema.Type.STRING))));

    String appName = UUID.randomUUID().toString();
    ApplicationManager appManager = createSourceAndDeployApp(appName, fileText, "text", outputDatasetName, textSchema);

    FileUtils.writeStringToFile(fileText, "Hello,World!");

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    List<StructuredRecord> expected = ImmutableList.of(
      StructuredRecord.builder(textSchema)
        .set("body", "Hello,World!")
        .set("file", fileText.toURI().toString())
        .build()
    );

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);

    Assert.assertEquals(expected, output);
  }


  @Test
  public void testFileBatchInputFormatText() throws Exception {
    File outputFolder = temporaryFolder.newFolder();
    File fileText = new File(outputFolder, "test.txt");
    String outputDatasetName = "test-filesource-text";

    Schema textSchema = Schema.recordOf("file.record",
                                        Schema.Field.of("offset", Schema.of(Schema.Type.LONG)),
                                        Schema.Field.of("body", Schema.nullableOf(Schema.of(Schema.Type.STRING))),
                                        Schema.Field.of("file", Schema.nullableOf(Schema.of(Schema.Type.STRING))));

    String appName = "FileSourceText";
    ApplicationManager appManager = createSourceAndDeployApp(appName, outputFolder, "text",
                                                             outputDatasetName, textSchema);

    FileUtils.writeStringToFile(fileText, "Hello,World!");
    File emptyFile = new File(outputFolder, "empty");
    emptyFile.createNewFile();

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    List<StructuredRecord> expected = ImmutableList.of(
      StructuredRecord.builder(textSchema)
        .set("offset", (long) 0)
        .set("body", "Hello,World!")
        .set("file", fileText.toURI().toString())
        .build()
    );

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);

    Assert.assertEquals(expected, output);

    // verify that the external dataset has the given schema
    verifyDatasetSchema(appName + "TestFile", textSchema);
  }

  @Test
  public void testFileBatchInputFormatAvro() throws Exception {
    File fileAvro = new File(temporaryFolder.newFolder(), "test.avro");
    String outputDatasetName = "test-filesource-avro";

    String appName = "FileSourceAvro";
    ApplicationManager appManager = createSourceAndDeployApp(appName, fileAvro, "avro", outputDatasetName,
                                                             RECORD_SCHEMA);

    org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(RECORD_SCHEMA.toString());
    GenericRecord record = new GenericRecordBuilder(avroSchema)
      .set("i", Integer.MAX_VALUE)
      .set("l", Long.MAX_VALUE)
      .set("file", fileAvro.getAbsolutePath())
      .build();

    DataSetManager<TimePartitionedFileSet> inputManager = getDataset("TestFile");

    DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(avroSchema);
    DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
    dataFileWriter.create(avroSchema, fileAvro);
    dataFileWriter.append(record);
    dataFileWriter.close();
    inputManager.flush();

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    List<StructuredRecord> expected = ImmutableList.of(
      StructuredRecord.builder(RECORD_SCHEMA)
        .set("i", Integer.MAX_VALUE)
        .set("l", Long.MAX_VALUE)
        .set("file", fileAvro.toURI().toString())
        .build()
    );

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);
    Assert.assertEquals(expected, output);
  }

  @Test
  public void testFileBatchInputFormatAvroNullSchema() throws Exception {
    File fileAvro = new File(temporaryFolder.newFolder(), "test.avro");
    String outputDatasetName = "test-filesource-avro-null-schema";

    String appName = "FileSourceAvroNullSchema";
    ApplicationManager appManager = createSourceAndDeployApp(appName, fileAvro, "avro", outputDatasetName,
                                                             null);

    Schema recordSchemaWithoutPathField = Schema.recordOf("record",
                                                          Schema.Field.of("i", Schema.of(Schema.Type.INT)),
                                                          Schema.Field.of("l", Schema.of(Schema.Type.LONG)));

    org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(recordSchemaWithoutPathField.
      toString());
    GenericRecord record = new GenericRecordBuilder(avroSchema)
      .set("i", Integer.MAX_VALUE)
      .set("l", Long.MAX_VALUE)
      .build();

    DataSetManager<TimePartitionedFileSet> inputManager = getDataset("TestFile");

    DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(avroSchema);
    DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
    dataFileWriter.create(avroSchema, fileAvro);
    dataFileWriter.append(record);
    dataFileWriter.close();
    inputManager.flush();

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    List<StructuredRecord> expected = ImmutableList.of(
      StructuredRecord.builder(RECORD_SCHEMA)
        .set("i", Integer.MAX_VALUE)
        .set("l", Long.MAX_VALUE)
        .set("file", fileAvro.toURI().toString())
        .build()
    );

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);
    Assert.assertEquals(expected, output);
  }

  @Test
  public void testFileBatchInputFormatAvroMissingField() throws Exception {
    File fileAvro = new File(temporaryFolder.newFolder(), "test.avro");
    String outputDatasetName = "test-filesource-avro-missing-field";

    Schema recordSchemaWithMissingField = Schema.recordOf("record",
                                                          Schema.Field.of("i", Schema.of(Schema.Type.INT)),
                                                          Schema.Field.of("file",
                                                                          Schema.of(Schema.Type.STRING)));

    String appName = "FileSourceAvroMissingField";
    ApplicationManager appManager = createSourceAndDeployApp(appName, fileAvro, "avro", outputDatasetName,
                                                             recordSchemaWithMissingField);

    org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(RECORD_SCHEMA.toString());
    GenericRecord record = new GenericRecordBuilder(avroSchema)
      .set("i", Integer.MAX_VALUE)
      .set("l", Long.MAX_VALUE)
      .set("file", fileAvro.getAbsolutePath())
      .build();

    DataSetManager<TimePartitionedFileSet> inputManager = getDataset("TestFile");

    DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(avroSchema);
    DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
    dataFileWriter.create(avroSchema, fileAvro);
    dataFileWriter.append(record);
    dataFileWriter.close();
    inputManager.flush();

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    List<StructuredRecord> expected = ImmutableList.of(
      StructuredRecord.builder(recordSchemaWithMissingField)
        .set("i", Integer.MAX_VALUE)
        .set("file", fileAvro.toURI().toString())
        .build()
    );

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);
    Assert.assertEquals(expected, output);
  }

  @Test
  public void testFileBatchInputFormatParquet() throws Exception {
    File fileParquet = new File(temporaryFolder.newFolder(), "test.parquet");
    String outputDatasetName = "test-filesource-parquet";

    String appName = "FileSourceParquet";
    ApplicationManager appManager = createSourceAndDeployApp(appName, fileParquet, "parquet", outputDatasetName,
                                                             RECORD_SCHEMA);

    org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(RECORD_SCHEMA.toString());
    GenericRecord record = new GenericRecordBuilder(avroSchema)
      .set("i", Integer.MAX_VALUE)
      .set("l", Long.MAX_VALUE)
      .set("file", fileParquet.getAbsolutePath())
      .build();

    DataSetManager<TimePartitionedFileSet> inputManager = getDataset("TestFile");
    ParquetWriter<GenericRecord> parquetWriter = new AvroParquetWriter<>(new Path(fileParquet.getAbsolutePath()),
                                                                         avroSchema);
    parquetWriter.write(record);
    parquetWriter.close();
    inputManager.flush();

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    List<StructuredRecord> expected = ImmutableList.of(
      StructuredRecord.builder(RECORD_SCHEMA)
        .set("i", Integer.MAX_VALUE)
        .set("l", Long.MAX_VALUE)
        .set("file", fileParquet.toURI().toString())
        .build()
    );

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);
    Assert.assertEquals(expected, output);

    // verify that the external dataset has the given schema
    verifyDatasetSchema(appName + "TestFile", RECORD_SCHEMA);
  }

  @Test
  public void testFileBatchInputFormatParquetNullSchema() throws Exception {
    File fileParquet = new File(temporaryFolder.newFolder(), "test.parquet");
    String outputDatasetName = "test-filesource-parquet-null-schema";

    String appName = "FileSourceParquetNullSchema";
    ApplicationManager appManager = createSourceAndDeployApp(appName, fileParquet, "parquet", outputDatasetName,
                                                             null);

    Schema recordSchemaWithMissingField = Schema.recordOf("record",
                                                          Schema.Field.of("i", Schema.of(Schema.Type.INT)),
                                                          Schema.Field.of("l", Schema.of(Schema.Type.LONG)));

    org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(recordSchemaWithMissingField.
      toString());
    GenericRecord record = new GenericRecordBuilder(avroSchema)
      .set("i", Integer.MAX_VALUE)
      .set("l", Long.MAX_VALUE)
      .build();

    DataSetManager<TimePartitionedFileSet> inputManager = getDataset("TestFile");
    ParquetWriter<GenericRecord> parquetWriter = new AvroParquetWriter<>(new Path(fileParquet.getAbsolutePath()),
                                                                         avroSchema);
    parquetWriter.write(record);
    parquetWriter.close();
    inputManager.flush();

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    List<StructuredRecord> expected = ImmutableList.of(
      StructuredRecord.builder(RECORD_SCHEMA)
        .set("i", Integer.MAX_VALUE)
        .set("l", Long.MAX_VALUE)
        .set("file", fileParquet.toURI().toString())
        .build()
    );

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);
    Assert.assertEquals(expected, output);
  }

  @Test
  public void testFileBatchInputFormatParquetMissingField() throws Exception {
    File fileParquet = new File(temporaryFolder.newFolder(), "test.parquet");
    String outputDatasetName = "test-filesource-parquet-missing-field";

    Schema recordSchemaWithMissingField = Schema.recordOf("record",
                                                          Schema.Field.of("i", Schema.of(Schema.Type.INT)),
                                                          Schema.Field.of("file",
                                                                          Schema.of(Schema.Type.STRING)));

    String appName = "FileSourceParquetMissingField";
    ApplicationManager appManager = createSourceAndDeployApp(appName, fileParquet, "parquet", outputDatasetName,
                                                             recordSchemaWithMissingField);

    org.apache.avro.Schema avroSchema = new org.apache.avro.Schema.Parser().parse(RECORD_SCHEMA.toString());
    GenericRecord record = new GenericRecordBuilder(avroSchema)
      .set("i", Integer.MAX_VALUE)
      .set("l", Long.MAX_VALUE)
      .set("file", fileParquet.getAbsolutePath())
      .build();

    DataSetManager<TimePartitionedFileSet> inputManager = getDataset("TestFile");
    ParquetWriter<GenericRecord> parquetWriter = new AvroParquetWriter<>(new Path(fileParquet.getAbsolutePath()),
                                                                         avroSchema);
    parquetWriter.write(record);
    parquetWriter.close();
    inputManager.flush();

    appManager.getWorkflowManager(SmartWorkflow.NAME)
      .startAndWaitForRun(ProgramRunStatus.COMPLETED, 5, TimeUnit.MINUTES);

    List<StructuredRecord> expected = ImmutableList.of(
      StructuredRecord.builder(recordSchemaWithMissingField)
        .set("i", Integer.MAX_VALUE)
        .set("file", fileParquet.toURI().toString())
        .build()
    );

    DataSetManager<Table> outputManager = getDataset(outputDatasetName);
    List<StructuredRecord> output = MockSink.readOutput(outputManager);
    Assert.assertEquals(expected, output);
  }

  private ApplicationManager createSourceAndDeployApp(String appName, File file, String format,
                                                      String outputDatasetName, Schema schema) throws Exception {
    return createSourceAndDeployApp(appName, file, format, outputDatasetName, schema, null, false, true);
  }

  private ApplicationManager createSourceAndDeployApp(String appName, File file, String format,
                                                      String outputDatasetName, Schema schema,
                                                      @Nullable String delimiter, boolean skipHeader,
                                                      boolean includePath) throws Exception {

    ImmutableMap.Builder<String, String> sourceProperties = ImmutableMap.<String, String>builder()
      .put(Constants.Reference.REFERENCE_NAME, appName + "TestFile")
      .put(Properties.File.PATH, file.getAbsolutePath())
      .put(Properties.File.FORMAT, format)
      .put(Properties.File.IGNORE_NON_EXISTING_FOLDERS, "false")
      .put("skipHeader", String.valueOf(skipHeader));
    if (includePath) {
      sourceProperties.put("pathField", "file");
    }
    if (delimiter != null) {
      sourceProperties.put("delimiter", delimiter);
    }

    if (schema != null) {
      String schemaString = schema.toString();
      sourceProperties.put(Properties.File.SCHEMA, schemaString);
    }
    return createSourceAndDeployApp(appName, outputDatasetName, sourceProperties.build());
  }

  private ApplicationManager createSourceAndDeployApp(String appName, String outputDatasetName,
                                                      Map<String, String> properties) throws Exception {
    ETLStage source = new ETLStage(
      "source", new ETLPlugin("File", BatchSource.PLUGIN_TYPE, properties, null));

    ETLStage sink = new ETLStage("sink", MockSink.getPlugin(outputDatasetName));

    ETLBatchConfig etlConfig = ETLBatchConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .build();

    AppRequest<ETLBatchConfig> appRequest = new AppRequest<>(DATAPIPELINE_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app(appName);
    return deployApplication(appId, appRequest);
  }

  private void verifyDatasetSchema(String dsName, Schema expectedSchema) throws IOException {
    Map<String, String> metadataProperties = getMetadataAdmin()
      .getProperties(MetadataScope.SYSTEM, MetadataEntity.ofDataset(NamespaceId.DEFAULT.getNamespace(), dsName));
    Assert.assertEquals(expectedSchema.toString(), metadataProperties.get(DatasetProperties.SCHEMA));
  }
}
