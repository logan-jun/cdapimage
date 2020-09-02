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

package io.cdap.plugin.transform;

import io.cdap.cdap.api.common.Bytes;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.Transform;
import io.cdap.cdap.etl.api.TransformContext;
import io.cdap.cdap.etl.api.validation.CauseAttributes;
import io.cdap.cdap.etl.api.validation.ValidationException;
import io.cdap.cdap.etl.api.validation.ValidationFailure.Cause;
import io.cdap.cdap.etl.mock.common.MockEmitter;
import io.cdap.cdap.etl.mock.common.MockPipelineConfigurer;
import io.cdap.cdap.etl.mock.transform.MockTransformContext;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 */
public class ProjectionTransformTest {
  private static final Schema SIMPLE_TYPES_SCHEMA =
    Schema.recordOf("record",
                    Schema.Field.of("booleanField", Schema.of(Schema.Type.BOOLEAN)),
                    Schema.Field.of("intField", Schema.of(Schema.Type.INT)),
                    Schema.Field.of("longField", Schema.of(Schema.Type.LONG)),
                    Schema.Field.of("floatField", Schema.of(Schema.Type.FLOAT)),
                    Schema.Field.of("doubleField", Schema.of(Schema.Type.DOUBLE)),
                    Schema.Field.of("bytesField", Schema.of(Schema.Type.BYTES)),
                    Schema.Field.of("stringField", Schema.of(Schema.Type.STRING)));
  private static final StructuredRecord SIMPLE_TYPES_RECORD = StructuredRecord.builder(SIMPLE_TYPES_SCHEMA)
    .set("booleanField", true)
    .set("intField", 28)
    .set("longField", 99L)
    .set("floatField", 2.71f)
    .set("doubleField", 3.14)
    .set("bytesField", Bytes.toBytes("foo"))
    .set("stringField", "bar")
    .build();

  private static final String STAGE = "stage";
  private static final String MOCK_STAGE = "mockstage";

  @Test
  public void testConfigurePipelineSchemaValidation() {
    Schema schema = Schema.recordOf("three",
                                    Schema.Field.of("x", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
                                    Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));

    MockPipelineConfigurer mockConfigurer = new MockPipelineConfigurer(schema, Collections.emptyMap());

    // test drop
    ProjectionTransform.ProjectionTransformConfig config =
      new ProjectionTransform.ProjectionTransformConfig("y, z", null, null, null);

    new ProjectionTransform(config).configurePipeline(mockConfigurer);
    Schema expectedSchema = Schema.recordOf("three.projected", Schema.Field.of("x", Schema.of(Schema.Type.INT)));
    Assert.assertEquals(expectedSchema, mockConfigurer.getOutputSchema());

    //test keep
    config = new ProjectionTransform.ProjectionTransformConfig(null, null, null, "y,z");

    new ProjectionTransform(config).configurePipeline(mockConfigurer);
    expectedSchema = Schema.recordOf("three.projected", Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
                                     Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));
    Assert.assertEquals(expectedSchema, mockConfigurer.getOutputSchema());

    // test rename
    config = new ProjectionTransform.ProjectionTransformConfig(null, "x:a, y:b", null, null);

    new ProjectionTransform(config).configurePipeline(mockConfigurer);
    expectedSchema = Schema.recordOf("three.projected", Schema.Field.of("a", Schema.of(Schema.Type.INT)),
                                     Schema.Field.of("b", Schema.of(Schema.Type.DOUBLE)),
                                     Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));
    Assert.assertEquals(expectedSchema, mockConfigurer.getOutputSchema());

    // test convert
    config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "x:string,y:string", null);

    new ProjectionTransform(config).configurePipeline(mockConfigurer);
    expectedSchema = Schema.recordOf("three.projected", Schema.Field.of("x", Schema.of(Schema.Type.STRING)),
                                     Schema.Field.of("y", Schema.of(Schema.Type.STRING)),
                                     Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));
    Assert.assertEquals(expectedSchema, mockConfigurer.getOutputSchema());

    // null input schema
    mockConfigurer = new MockPipelineConfigurer(null, Collections.emptyMap());
    new ProjectionTransform(config).configurePipeline(mockConfigurer);
    Assert.assertNull(mockConfigurer.getOutputSchema());
  }

  @Test
  public void testSameFieldMultipleConverts() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "x:int,x:long", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    try {
      transform.initialize(transformContext);
      Assert.fail();
    } catch (ValidationException e) {
      Assert.assertEquals(1, e.getFailures().size());
      Assert.assertEquals(1, e.getFailures().get(0).getCauses().size());
      Cause expectedCause = new Cause();
      expectedCause.addAttribute(CauseAttributes.STAGE_CONFIG, ProjectionTransform.ProjectionTransformConfig.CONVERT);
      expectedCause.addAttribute(STAGE, MOCK_STAGE);
      Assert.assertEquals(expectedCause, e.getFailures().get(0).getCauses().get(0));
    }
  }

  @Test
  public void testSameFieldMultipleRenames() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, "x:z,x:y", null, null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    try {
      transform.initialize(transformContext);
      Assert.fail();
    } catch (ValidationException e) {
      Assert.assertEquals(1, e.getFailures().size());
      Assert.assertEquals(2, e.getFailures().get(0).getCauses().size());
    }
  }

  @Test
  public void testMultipleRenamesToSameField() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, "x:z,y:z", null, null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    try {
      transform.initialize(transformContext);
      Assert.fail();
    } catch (ValidationException e) {
      Assert.assertEquals(1, e.getFailures().size());
      Assert.assertEquals(1, e.getFailures().get(0).getCauses().size());
      Cause expectedCause = new Cause();
      expectedCause.addAttribute(CauseAttributes.STAGE_CONFIG, ProjectionTransform.ProjectionTransformConfig.RENAME);
      expectedCause.addAttribute(STAGE, MOCK_STAGE);
      Assert.assertEquals(expectedCause, e.getFailures().get(0).getCauses().get(0));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidSyntax() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, "x,y", null, null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);
  }

  @Test
  public void testInvalidConversion() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "x:int", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    try {
      transform.initialize(transformContext);

      Schema schema = Schema.recordOf("record", Schema.Field.of("x", Schema.of(Schema.Type.LONG)));
      StructuredRecord input = StructuredRecord.builder(schema).set("x", 5L).build();
      MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
      transform.transform(input, emitter);
      Assert.fail();
    } catch (ValidationException e) {
      Assert.assertEquals(1, e.getFailures().size());
      Assert.assertEquals(1, e.getFailures().get(0).getCauses().size());
      Cause expectedCause = new Cause();
      expectedCause.addAttribute(CauseAttributes.STAGE_CONFIG, ProjectionTransform.ProjectionTransformConfig.CONVERT);
      expectedCause.addAttribute(CauseAttributes.CONFIG_ELEMENT, "x:int");
      expectedCause.addAttribute(STAGE, MOCK_STAGE);
      Assert.assertEquals(expectedCause, e.getFailures().get(0).getCauses().get(0));
    }
  }

  @Test
  public void testDropFields() throws Exception {
    Schema schema = Schema.recordOf("three",
                                    Schema.Field.of("x", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
                                    Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));
    StructuredRecord input = StructuredRecord.builder(schema)
      .set("x", 1)
      .set("y", 3.14)
      .set("z", new int[] { 1, 2, 3 })
      .build();
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig("y, z", null, null, null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(input, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("three.projected", Schema.Field.of("x", Schema.of(Schema.Type.INT)));
    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertEquals(1, output.<Integer>get("x").intValue());
  }

  @Test
  public void testKeepFields() throws Exception {
    Schema schema = Schema.recordOf("three",
                                    Schema.Field.of("x", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
                                    Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));
    StructuredRecord input = StructuredRecord.builder(schema)
      .set("x", 1)
      .set("y", 3.14)
      .set("z", new int[] { 1, 2, 3 })
      .build();
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, null, "x");
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(input, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("three.projected", Schema.Field.of("x", Schema.of(Schema.Type.INT)));
    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertEquals(1, output.<Integer>get("x").intValue());
  }

  @Test
  public void testKeepDropBothNonNull() {
    Schema schema = Schema.recordOf("three",
                                    Schema.Field.of("x", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
                                    Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));

    MockPipelineConfigurer mockConfigurer = new MockPipelineConfigurer(schema, Collections.emptyMap());
    try {
      ProjectionTransform.ProjectionTransformConfig config =
          new ProjectionTransform.ProjectionTransformConfig("y, z", null, null, "x,y");

      new ProjectionTransform(config).configurePipeline(mockConfigurer);
      Assert.fail();
    } catch (ValidationException e) {
      Assert.assertEquals(1, e.getFailures().size());
      Assert.assertEquals(2, e.getFailures().get(0).getCauses().size());
    }
  }

  @Test
  public void testRenameFields() throws Exception {
    Schema schema = Schema.recordOf("three",
                                    Schema.Field.of("x", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
                                    Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));
    StructuredRecord input = StructuredRecord.builder(schema)
      .set("x", 1)
      .set("y", 3.14)
      .set("z", new int[] { 1, 2, 3 })
      .build();
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, "x:y,y:z,z:x", null, null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(input, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("three.projected",
                                            Schema.Field.of("y", Schema.of(Schema.Type.INT)),
                                            Schema.Field.of("z", Schema.of(Schema.Type.DOUBLE)),
                                            Schema.Field.of("x", Schema.arrayOf(Schema.of(Schema.Type.INT))));
    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertEquals(1, output.<Integer>get("y").intValue());
    Assert.assertTrue(Math.abs(3.14 - (Double) output.get("z")) < 0.000001);
    Assert.assertArrayEquals(new int[]{1, 2, 3}, (int[]) output.get("x"));
  }

  @Test
  public void testDropRenameConvert() throws Exception {
    Schema schema = Schema.recordOf("record",
                                    Schema.Field.of("x", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("y", Schema.nullableOf(Schema.of(Schema.Type.INT))));
    StructuredRecord input = StructuredRecord.builder(schema)
      .set("x", 5)
      .set("y", 10)
      .build();

    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig("x", "y:x", "y:string", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(input, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("record.projected",
                                            Schema.Field.of("x", Schema.nullableOf(Schema.of(Schema.Type.STRING))));
    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertEquals("10", output.get("x"));
  }

  @Test
  public void testConvertToString() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "booleanField:string,intField:string,longField:string,floatField:string," +
      "doubleField:string,bytesField:string,stringField:string", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(SIMPLE_TYPES_RECORD, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("record.projected",
                                            Schema.Field.of("booleanField", Schema.of(Schema.Type.STRING)),
                                            Schema.Field.of("intField", Schema.of(Schema.Type.STRING)),
                                            Schema.Field.of("longField", Schema.of(Schema.Type.STRING)),
                                            Schema.Field.of("floatField", Schema.of(Schema.Type.STRING)),
                                            Schema.Field.of("doubleField", Schema.of(Schema.Type.STRING)),
                                            Schema.Field.of("bytesField", Schema.of(Schema.Type.STRING)),
                                            Schema.Field.of("stringField", Schema.of(Schema.Type.STRING)));
    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertEquals("true", output.get("booleanField"));
    Assert.assertEquals("28", output.get("intField"));
    Assert.assertEquals("99", output.get("longField"));
    Assert.assertEquals("2.71", output.get("floatField"));
    Assert.assertEquals("3.14", output.get("doubleField"));
    Assert.assertEquals("foo", output.get("bytesField"));
    Assert.assertEquals("bar", output.get("stringField"));
  }

  @Test
  public void testConvertFromString() throws Exception {
    Schema schema = Schema.recordOf("record",
                                    Schema.Field.of("booleanField", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("intField", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("longField", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("floatField", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("doubleField", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("bytesField", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("stringField", Schema.of(Schema.Type.STRING)));
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "booleanField:boolean,intField:int,longField:long,floatField:float," +
      "doubleField:double,bytesField:bytes,stringField:string", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    StructuredRecord input = StructuredRecord.builder(schema)
      .set("booleanField", "true")
      .set("intField", "28")
      .set("longField", "99")
      .set("floatField", "2.71")
      .set("doubleField", "3.14")
      .set("bytesField", "foo")
      .set("stringField", "bar")
      .build();

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(input, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf(SIMPLE_TYPES_SCHEMA.getRecordName() + ".projected",
                                            SIMPLE_TYPES_SCHEMA.getFields());
    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertTrue((Boolean) output.get("booleanField"));
    Assert.assertEquals(28, output.<Integer>get("intField").intValue());
    Assert.assertEquals(99L, output.<Long>get("longField").longValue());
    Assert.assertTrue(Math.abs(2.71f - (Float) output.get("floatField")) < 0.000001);
    Assert.assertTrue(Math.abs(3.14 - (Double) output.get("doubleField")) < 0.000001);
    Assert.assertArrayEquals(Bytes.toBytes("foo"), (byte[]) output.get("bytesField"));
    Assert.assertEquals("bar", output.get("stringField"));
  }

  @Test
  public void testConvertToBytes() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "booleanField:bytes,intField:bytes,longField:bytes,floatField:bytes," +
      "doubleField:bytes,bytesField:bytes,stringField:bytes", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(SIMPLE_TYPES_RECORD, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("record.projected",
                                            Schema.Field.of("booleanField", Schema.of(Schema.Type.BYTES)),
                                            Schema.Field.of("intField", Schema.of(Schema.Type.BYTES)),
                                            Schema.Field.of("longField", Schema.of(Schema.Type.BYTES)),
                                            Schema.Field.of("floatField", Schema.of(Schema.Type.BYTES)),
                                            Schema.Field.of("doubleField", Schema.of(Schema.Type.BYTES)),
                                            Schema.Field.of("bytesField", Schema.of(Schema.Type.BYTES)),
                                            Schema.Field.of("stringField", Schema.of(Schema.Type.BYTES)));
    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertArrayEquals(Bytes.toBytes(true), (byte[]) output.get("booleanField"));
    Assert.assertArrayEquals(Bytes.toBytes(28), (byte[]) output.get("intField"));
    Assert.assertArrayEquals(Bytes.toBytes(99L), (byte[]) output.get("longField"));
    Assert.assertArrayEquals(Bytes.toBytes(2.71f), (byte[]) output.get("floatField"));
    Assert.assertArrayEquals(Bytes.toBytes(3.14), (byte[]) output.get("doubleField"));
    Assert.assertArrayEquals(Bytes.toBytes("foo"), (byte[]) output.get("bytesField"));
    Assert.assertArrayEquals(Bytes.toBytes("bar"), (byte[]) output.get("stringField"));
  }

  @Test
  public void testConvertFromBytes() throws Exception {
    Schema schema = Schema.recordOf("record",
                                    Schema.Field.of("booleanField", Schema.of(Schema.Type.BYTES)),
                                    Schema.Field.of("intField", Schema.of(Schema.Type.BYTES)),
                                    Schema.Field.of("longField", Schema.of(Schema.Type.BYTES)),
                                    Schema.Field.of("floatField", Schema.of(Schema.Type.BYTES)),
                                    Schema.Field.of("doubleField", Schema.of(Schema.Type.BYTES)),
                                    Schema.Field.of("bytesField", Schema.of(Schema.Type.BYTES)),
                                    Schema.Field.of("stringField", Schema.of(Schema.Type.BYTES)));
    StructuredRecord input = StructuredRecord.builder(schema)
      .set("booleanField", Bytes.toBytes(true))
      .set("intField", Bytes.toBytes(28))
      .set("longField", Bytes.toBytes(99L))
      .set("floatField", Bytes.toBytes(2.71f))
      .set("doubleField", Bytes.toBytes(3.14))
      .set("bytesField", Bytes.toBytes("foo"))
      .set("stringField", Bytes.toBytes("bar"))
      .build();

    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "booleanField:boolean,intField:int,longField:long,floatField:float," +
      "doubleField:double,bytesField:bytes,stringField:string", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(input, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf(SIMPLE_TYPES_SCHEMA.getRecordName() + ".projected",
                                            SIMPLE_TYPES_SCHEMA.getFields());
    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertTrue((Boolean) output.get("booleanField"));
    Assert.assertEquals(28, output.<Integer>get("intField").intValue());
    Assert.assertEquals(99L, output.<Long>get("longField").longValue());
    Assert.assertTrue(Math.abs(2.71f - (Float) output.get("floatField")) < 0.000001);
    Assert.assertTrue(Math.abs(3.14 - (Double) output.get("doubleField")) < 0.000001);
    Assert.assertArrayEquals(Bytes.toBytes("foo"), (byte[]) output.get("bytesField"));
    Assert.assertEquals("bar", output.get("stringField"));
  }

  @Test
  public void testConvertToLong() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "intField:long", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(SIMPLE_TYPES_RECORD, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("record.projected",
                                            Schema.Field.of("booleanField", Schema.of(Schema.Type.BOOLEAN)),
                                            Schema.Field.of("intField", Schema.of(Schema.Type.LONG)),
                                            Schema.Field.of("longField", Schema.of(Schema.Type.LONG)),
                                            Schema.Field.of("floatField", Schema.of(Schema.Type.FLOAT)),
                                            Schema.Field.of("doubleField", Schema.of(Schema.Type.DOUBLE)),
                                            Schema.Field.of("bytesField", Schema.of(Schema.Type.BYTES)),
                                            Schema.Field.of("stringField", Schema.of(Schema.Type.STRING)));

    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertTrue((Boolean) output.get("booleanField"));
    Assert.assertEquals(28L, output.<Long>get("intField").longValue());
    Assert.assertEquals(99L, output.<Long>get("longField").longValue());
    Assert.assertTrue(Math.abs(2.71f - (Float) output.get("floatField")) < 0.000001);
    Assert.assertTrue(Math.abs(3.14 - (Double) output.get("doubleField")) < 0.000001);
    Assert.assertArrayEquals(Bytes.toBytes("foo"), (byte[]) output.get("bytesField"));
    Assert.assertEquals("bar", output.get("stringField"));
  }

  @Test
  public void testConvertToFloat() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "intField:float,longField:float", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(SIMPLE_TYPES_RECORD, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("record.projected",
                                            Schema.Field.of("booleanField", Schema.of(Schema.Type.BOOLEAN)),
                                            Schema.Field.of("intField", Schema.of(Schema.Type.FLOAT)),
                                            Schema.Field.of("longField", Schema.of(Schema.Type.FLOAT)),
                                            Schema.Field.of("floatField", Schema.of(Schema.Type.FLOAT)),
                                            Schema.Field.of("doubleField", Schema.of(Schema.Type.DOUBLE)),
                                            Schema.Field.of("bytesField", Schema.of(Schema.Type.BYTES)),
                                            Schema.Field.of("stringField", Schema.of(Schema.Type.STRING)));

    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertTrue((Boolean) output.get("booleanField"));
    Assert.assertEquals(28f, output.get("intField"), 0.0001f);
    Assert.assertEquals(99f, output.get("longField"), 0.0001f);
    Assert.assertTrue(Math.abs(2.71f - (Float) output.get("floatField")) < 0.000001);
    Assert.assertTrue(Math.abs(3.14 - (Double) output.get("doubleField")) < 0.000001);
    Assert.assertArrayEquals(Bytes.toBytes("foo"), (byte[]) output.get("bytesField"));
    Assert.assertEquals("bar", output.get("stringField"));
  }

  @Test
  public void testConvertToDouble() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "intField:double,longField:double,floatField:double", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(SIMPLE_TYPES_RECORD, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("record.projected",
                                            Schema.Field.of("booleanField", Schema.of(Schema.Type.BOOLEAN)),
                                            Schema.Field.of("intField", Schema.of(Schema.Type.DOUBLE)),
                                            Schema.Field.of("longField", Schema.of(Schema.Type.DOUBLE)),
                                            Schema.Field.of("floatField", Schema.of(Schema.Type.DOUBLE)),
                                            Schema.Field.of("doubleField", Schema.of(Schema.Type.DOUBLE)),
                                            Schema.Field.of("bytesField", Schema.of(Schema.Type.BYTES)),
                                            Schema.Field.of("stringField", Schema.of(Schema.Type.STRING)));

    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertTrue((Boolean) output.get("booleanField"));
    Assert.assertEquals(28d, output.get("intField"), 0.0001d);
    Assert.assertEquals(99d, output.get("longField"), 0.0001d);
    Assert.assertTrue(Math.abs(2.71 - (Double) output.get("floatField")) < 0.000001);
    Assert.assertTrue(Math.abs(3.14 - (Double) output.get("doubleField")) < 0.000001);
    Assert.assertArrayEquals(Bytes.toBytes("foo"), (byte[]) output.get("bytesField"));
    Assert.assertEquals("bar", output.get("stringField"));
  }

  @Test
  public void testConvertNullField() throws Exception {
    ProjectionTransform.ProjectionTransformConfig config = new ProjectionTransform
      .ProjectionTransformConfig(null, null, "x:long", null);
    Transform<StructuredRecord, StructuredRecord> transform = new ProjectionTransform(config);
    TransformContext transformContext = new MockTransformContext();
    transform.initialize(transformContext);

    Schema inputSchema = Schema.recordOf("record",
                                         Schema.Field.of("x", Schema.nullableOf(Schema.of(Schema.Type.INT))));
    StructuredRecord input = StructuredRecord.builder(inputSchema).build();

    MockEmitter<StructuredRecord> emitter = new MockEmitter<>();
    transform.transform(input, emitter);
    StructuredRecord output = emitter.getEmitted().get(0);

    Schema expectedSchema = Schema.recordOf("record.projected",
                                            Schema.Field.of("x", Schema.nullableOf(Schema.of(Schema.Type.LONG))));

    Assert.assertEquals(expectedSchema, output.getSchema());
    Assert.assertNull(output.get("x"));
  }

  @Test
  public void testDropFieldsValidations() {
    Schema schema = Schema.recordOf("three",
            Schema.Field.of("x", Schema.of(Schema.Type.INT)),
            Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
            Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));

    MockPipelineConfigurer mockConfigurer = new MockPipelineConfigurer(schema, Collections.emptyMap());
    ProjectionTransform.ProjectionTransformConfig config =
            new ProjectionTransform.ProjectionTransformConfig("x,y,z", null, null, null);
    try {
      new ProjectionTransform(config).configurePipeline(mockConfigurer);
      Assert.fail();
    } catch (ValidationException e) {
      Assert.assertEquals(1, e.getFailures().size());
      Assert.assertEquals(1, e.getFailures().get(0).getCauses().size());
      Cause expectedCause = new Cause();
      expectedCause.addAttribute(CauseAttributes.STAGE_CONFIG, ProjectionTransform.ProjectionTransformConfig.DROP);
      expectedCause.addAttribute(STAGE, MOCK_STAGE);
      Assert.assertEquals(expectedCause, e.getFailures().get(0).getCauses().get(0));
    }
  }

  @Test
  public void testKeepFieldsValidations() {
    Schema schema = Schema.recordOf("three",
            Schema.Field.of("x", Schema.of(Schema.Type.INT)),
            Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
            Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));

    MockPipelineConfigurer mockConfigurer = new MockPipelineConfigurer(schema, Collections.emptyMap());
    ProjectionTransform.ProjectionTransformConfig config =
            new ProjectionTransform.ProjectionTransformConfig(null, null, null, "n");
    try {
      new ProjectionTransform(config).configurePipeline(mockConfigurer);
      Assert.fail();
    } catch (ValidationException e) {
      Assert.assertEquals(1, e.getFailures().size());
      Assert.assertEquals(1, e.getFailures().get(0).getCauses().size());
      Cause expectedCause = new Cause();
      expectedCause.addAttribute(CauseAttributes.STAGE_CONFIG, ProjectionTransform.ProjectionTransformConfig.KEEP);
      expectedCause.addAttribute(CauseAttributes.CONFIG_ELEMENT, "n");
      expectedCause.addAttribute(STAGE, MOCK_STAGE);
      Assert.assertEquals(expectedCause, e.getFailures().get(0).getCauses().get(0));
    }
  }

  @Test
  public void testConvertFieldsValidations() {
    Schema schema = Schema.recordOf("three",
            Schema.Field.of("x", Schema.of(Schema.Type.INT)),
            Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
            Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));

    MockPipelineConfigurer mockConfigurer = new MockPipelineConfigurer(schema, Collections.emptyMap());
    ProjectionTransform.ProjectionTransformConfig config =
            new ProjectionTransform.ProjectionTransformConfig(null, null, "n:boolean", "x");
    try {
      new ProjectionTransform(config).configurePipeline(mockConfigurer);
      Assert.fail();
    } catch (ValidationException e) {
      Assert.assertEquals(1, e.getFailures().size());
      Assert.assertEquals(1, e.getFailures().get(0).getCauses().size());
      Cause expectedCause = new Cause();
      expectedCause.addAttribute(CauseAttributes.STAGE_CONFIG, ProjectionTransform.ProjectionTransformConfig.CONVERT);
      expectedCause.addAttribute(CauseAttributes.CONFIG_ELEMENT, "n:boolean");
      expectedCause.addAttribute(STAGE, MOCK_STAGE);
      Assert.assertEquals(expectedCause, e.getFailures().get(0).getCauses().get(0));
    }
  }

  @Test
  public void testRenameFieldsValidations() {
    Schema schema = Schema.recordOf("three",
            Schema.Field.of("x", Schema.of(Schema.Type.INT)),
            Schema.Field.of("y", Schema.of(Schema.Type.DOUBLE)),
            Schema.Field.of("z", Schema.arrayOf(Schema.of(Schema.Type.INT))));

    MockPipelineConfigurer mockConfigurer = new MockPipelineConfigurer(schema, Collections.emptyMap());
    ProjectionTransform.ProjectionTransformConfig config =
            new ProjectionTransform.ProjectionTransformConfig(null, "n:m", null, "x");
    try {
      new ProjectionTransform(config).configurePipeline(mockConfigurer);
    } catch (ValidationException e) {
      Assert.assertEquals(1, e.getFailures().size());
      Assert.assertEquals(1, e.getFailures().get(0).getCauses().size());
      Cause expectedCause = new Cause();
      expectedCause.addAttribute(CauseAttributes.STAGE_CONFIG, ProjectionTransform.ProjectionTransformConfig.RENAME);
      expectedCause.addAttribute(CauseAttributes.CONFIG_ELEMENT, "n:m");
      expectedCause.addAttribute(STAGE, MOCK_STAGE);
      Assert.assertEquals(expectedCause, e.getFailures().get(0).getCauses().get(0));
    }
  }
}
