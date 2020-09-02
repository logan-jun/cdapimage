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

package io.cdap.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.data.schema.Schema.Field;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.cdap.etl.api.Emitter;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.InvalidEntry;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.StageConfigurer;
import io.cdap.cdap.etl.api.StageSubmitterContext;
import io.cdap.cdap.etl.api.Transform;
import io.cdap.cdap.etl.api.TransformContext;
import io.cdap.cdap.etl.api.lineage.field.FieldOperation;
import io.cdap.cdap.etl.api.lineage.field.FieldTransformOperation;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Transformation that parses a field as CSV Record into {@link StructuredRecord}.
 *
 * <p>
 * CSVParser supports transforming the input into {@link StructuredRecord}
 * Following are different CSV record types that are supported by this transform. User can also define custom delimiter
 * by selecting tht option "Custom".
 * <ul>
 *   <li>DEFAULT</li>
 *   <li>EXCEL</li>
 *   <li>RFC4180</li>
 *   <li>MYSQL</li>
 *   <li>Tab Delimited and</li>
 *   <li>Pipe Delimited</li>
 *   <li>Custom</li>
 * </ul>
 * </p>
 */
@Plugin(type = "transform")
@Name("CSVParser")
@Description("Parses a field as CSV Record into a Structured Record.")
public final class CSVParser extends Transform<StructuredRecord, StructuredRecord> {
  private final Config config;

  // Output Schema associated with transform output.
  private Schema outSchema;

  static final Set<String> FORMATS = ImmutableSet.of("DEFAULT", "EXCEL", "MYSQL", "RFC4180",
                                                  "TDF", "Pipe Delimited", "Tab Delimited",
                                                  "PDL", "Custom");

  // List of fields specified in the schema.
  private List<Field> fields;

  // Format of CSV.
  private CSVFormat csvFormat = CSVFormat.DEFAULT;

  // Format of PDL.
  public static final CSVFormat PDL;

  // Initialize Pipe Delimiter CSV Parser.
  static {
    PDL = CSVFormat.DEFAULT.withDelimiter('|').withEscape('\\').withIgnoreEmptyLines(false)
      .withAllowMissingColumnNames().withQuote(null).withRecordSeparator('\n')
      .withIgnoreSurroundingSpaces();
  }

  // This is used only for tests, otherwise this is being injected by the ingestion framework. 
  public CSVParser(Config config) {
    this.config = config;
  }

  @Override
  public void configurePipeline(PipelineConfigurer configurer) throws IllegalArgumentException {
    super.configurePipeline(configurer);
    StageConfigurer stageConfigurer = configurer.getStageConfigurer();
    FailureCollector collector = stageConfigurer.getFailureCollector();
    config.validate(collector);

    // perform schema validation
    Schema inputSchema = stageConfigurer.getInputSchema();
    validateInputSchema(inputSchema, collector);
    Schema schema = parseAndValidateOutputSchema(inputSchema, collector);
    collector.getOrThrowException();

    stageConfigurer.setOutputSchema(schema);
  }

  @Override
  public void prepareRun(StageSubmitterContext context) throws Exception {
    super.prepareRun(context);
    FailureCollector collector = context.getFailureCollector();
    config.validate(collector);
    collector.getOrThrowException();

    // Read from config.field and output to fields
    init();
    if (fields != null) {
      FieldOperation operation = new FieldTransformOperation("Parse",
                                                             "Parsed CSV data from expected field.",
                                                             Collections.singletonList(config.field),
                                                             fields.stream().map(Schema.Field::getName)
                                                               .collect(Collectors.toList()));
      context.record(Collections.singletonList(operation));
    }
  }

  @Override
  public void initialize(TransformContext context) throws Exception {
    super.initialize(context);
    init();
  }

  private void init() {
    String csvFormatString = config.format == null ? "default" : config.format.toLowerCase();
    switch (csvFormatString) {
      case "default":
        csvFormat = CSVFormat.DEFAULT;
        break;

      case "excel":
        csvFormat = CSVFormat.EXCEL;
        break;

      case "mysql":
        csvFormat = CSVFormat.MYSQL;
        break;

      case "rfc4180":
        csvFormat = CSVFormat.RFC4180;
        break;

      case "tdf":
      case "tab delimited":
        csvFormat = CSVFormat.TDF;
        break;

      case "pdl":
      case "pipe delimited":
        csvFormat = PDL;
        break;

      case "custom":
        csvFormat = CSVFormat.DEFAULT.withDelimiter(config.delimiter).withEscape('\\').withIgnoreEmptyLines(false)
          .withAllowMissingColumnNames().withQuote(null).withRecordSeparator('\n')
          .withIgnoreSurroundingSpaces();
        break;

      default:
        throw new IllegalArgumentException("Format {} specified is not one of the allowed format. Allowed formats are" +
                                             "DEFAULT, EXCEL, MYSQL, RFC4180, Pipe Delimited and Tab Delimited or " +
                                             "Custom");
    }

    try {
      outSchema = Schema.parseJson(config.schema);
      fields = outSchema.getFields();
    } catch (IOException e) {
      throw new IllegalArgumentException("Format of schema specified is invalid. Please check the format.");
    }
  }

  @Override
  public void transform(StructuredRecord in, Emitter<StructuredRecord> emitter) throws Exception {
    // Field has to string to be parsed correctly. For others throw an exception.
    String body = in.get(config.field);

    // Parse the text as CSV and emit it as structured record.
    try {
      if (body == null) {
        emitter.emit(createStructuredRecord(null, in));
      } else {
        org.apache.commons.csv.CSVParser parser = org.apache.commons.csv.CSVParser.parse(body, csvFormat);
        List<CSVRecord> records = parser.getRecords();
        for (CSVRecord record : records) {
          emitter.emit(createStructuredRecord(record, in));
        }
      }
    } catch (IOException e) {
      emitter.emitError(new InvalidEntry<>(31, e.getStackTrace()[0].toString() + " : " + e.getMessage(), in));
    }
  }

  private StructuredRecord createStructuredRecord(@Nullable CSVRecord record, StructuredRecord in) {
    StructuredRecord.Builder builder = StructuredRecord.builder(outSchema);
    int i = 0;
    for (Field field : fields) {
      String name = field.getName();
      // If the field specified in the output field is present in the input, then
      // it's directly copied into the output, else field is parsed in from the CSV parser.
      // If the input record is null, propagate all supplied input fields and null other fields
      // assumed to be CSV-parsed fields
      if (in.get(name) != null) {
        builder.set(name, in.get(name));
      } else if (record == null) {
        builder.set(name, null);
      } else {
        String val = record.get(i);
        Schema fieldSchema = field.getSchema();

        if (val.isEmpty()) {
          boolean isNullable = fieldSchema.isNullable();
          Schema.Type fieldType = isNullable ? fieldSchema.getNonNullable().getType() : fieldSchema.getType();
          // if the field is a string or a nullable string, set the value to the empty string
          if (fieldType == Schema.Type.STRING) {
            builder.set(field.getName(), "");
          } else if (!isNullable) {
            // otherwise, error out
            throw new IllegalArgumentException(String.format(
              "Field #%d (named '%s') is of non-nullable type '%s', " +
                "but was parsed as an empty string for CSV record '%s'",
              i, field.getName(), field.getSchema().getType(), record));
          }
        } else {
          builder.convertAndSet(field.getName(), val);
        }
        ++i;
      }
    }
    return builder.build();
  }

  @VisibleForTesting
  void validateInputSchema(@Nullable Schema inputSchema, FailureCollector collector) {
    if (inputSchema != null) {
      // Check the existence of field in input schema
      Schema.Field inputSchemaField = inputSchema.getField(config.field);
      if (inputSchemaField == null) {
        collector.addFailure(String.format("Field '%s' must be present in the input schema.", config.field), null)
          .withConfigProperty(Config.NAME_FIELD);
        return;
      }

      // Check that the field type is String or Nullable String
      Schema fieldSchema = inputSchemaField.getSchema();
      Schema nonNullableSchema = fieldSchema.isNullable() ? fieldSchema.getNonNullable() : fieldSchema;
      Schema.Type fieldType = nonNullableSchema.getType();

      if (!fieldType.equals(Schema.Type.STRING)) {
        collector.addFailure(String.format("Field '%s' is of invalid type '%s'.",
                                           inputSchemaField.getName(), nonNullableSchema.getDisplayName()),
                             "Ensure it is of type 'string'.")
          .withConfigProperty(Config.NAME_FIELD).withInputSchemaField(config.field);
      }
    }
  }

  @VisibleForTesting
  Schema parseAndValidateOutputSchema(@Nullable Schema inputSchema, FailureCollector collector) {
    Schema outputSchema = config.getSchema(collector);

    // When a input field is passed through to output, the type and name should be the same.
    // If the type is not the same, then we collect failure.
    if (inputSchema != null) {
      for (Field field : inputSchema.getFields()) {
        if (outputSchema.getField(field.getName()) != null) {
          Schema out = outputSchema.getField(field.getName()).getSchema();
          Schema in = field.getSchema();
          if (!in.equals(out)) {
            collector.addFailure(
              String.format("Output field '%s' must have same schema as input schema.", field.getName()),
              "Ensure input and output schema for the field is same.")
              .withInputSchemaField(field.getName()).withOutputSchemaField(field.getName());
          }
        }
      }
    }

    return outputSchema;
  }

  /**
   * Configuration for the plugin.
   */
  public static class Config extends PluginConfig {
    private static final String NAME_FORMAT = "format";
    private static final String NAME_DELIMITER = "delimiter";
    private static final String NAME_FIELD = "field";
    private static final String NAME_SCHEMA = "schema";

    @Nullable
    @Name(NAME_FORMAT)
    @Description("Specify one of the predefined formats. DEFAULT, EXCEL, MYSQL, RFC4180, Pipe Delimited, Tab " +
      "Delimited and Custom are supported formats.")
    private String format;

    @Nullable
    @Name(NAME_DELIMITER)
    @Description("Custom delimiter to be used for parsing the fields. The custom delimiter can only be specified by " +
      "selecting the option 'Custom' from the format drop-down. In case of null, defaults to ','.")
    private Character delimiter;

    @Name(NAME_FIELD)
    @Description("Specify the field that should be used for parsing into CSV. Input records with a null input field " +
      "propagate all other fields and set fields that would otherwise be parsed by the CSVParser to null.")
    private String field;

    @Name(NAME_SCHEMA)
    @Description("Specifies the schema that has to be output.")
    private String schema;

    public Config(@Nullable String format, @Nullable Character delimiter, String field, String schema) {
      this.format = format == null ? "DEFAULT" : format;
      this.delimiter = delimiter;
      this.field = field;
      this.schema = schema;
    }

    //Constructor to assign default value to format
    public Config() {
      format = "DEFAULT";
    }

    private void validate(FailureCollector collector) {
      // Check if format is one of the allowed types.
      if (!format.equalsIgnoreCase("DEFAULT") && !format.equalsIgnoreCase("EXCEL") &&
        !format.equalsIgnoreCase("MYSQL") && !format.equalsIgnoreCase("RFC4180") &&
        !format.equalsIgnoreCase("Tab Delimited") && !format.equalsIgnoreCase("Pipe Delimited") &&
        !format.equalsIgnoreCase("Custom") && !format.equalsIgnoreCase("PDL") &&
        !format.equalsIgnoreCase("TDF")) {
        collector.addFailure(String.format("Format '%s' is unsupported.", format),
                             String.format("Specify one of the following: %s.", Joiner.on(", ").join(FORMATS)))
          .withConfigProperty(NAME_FORMAT);
      }

      if (format.equalsIgnoreCase("Custom") && (delimiter == null || delimiter == 0)) {
        collector.addFailure("Delimiter must be specified for format option 'Custom'.", null)
          .withConfigProperty(NAME_DELIMITER).withConfigProperty(NAME_FORMAT);
      }

      if (!format.equalsIgnoreCase("Custom") && delimiter != null && delimiter != 0) {
        collector.addFailure("Custom delimiter can only be used for format option 'Custom'.",
                             "Remove delimiter.")
          .withConfigProperty(NAME_DELIMITER).withConfigProperty(NAME_FORMAT);
      }
    }

    @Nullable
    private Schema getSchema(FailureCollector collector) {
      try {
        return Strings.isNullOrEmpty(schema) ? null : Schema.parseJson(schema);
      } catch (IOException e) {
        collector.addFailure("Invalid schema : " + e.getMessage(), null);
      }
      throw collector.getOrThrowException();
    }
  }
}
