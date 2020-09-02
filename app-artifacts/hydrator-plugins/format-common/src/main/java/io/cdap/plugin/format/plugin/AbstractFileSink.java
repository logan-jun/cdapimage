/*
 * Copyright © 2018-2019 Cask Data, Inc.
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

package io.cdap.plugin.format.plugin;

import io.cdap.cdap.api.data.batch.Output;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.dataset.lib.KeyValue;
import io.cdap.cdap.api.plugin.InvalidPluginConfigException;
import io.cdap.cdap.api.plugin.InvalidPluginProperty;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.cdap.etl.api.Emitter;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.batch.BatchSink;
import io.cdap.cdap.etl.api.batch.BatchSinkContext;
import io.cdap.cdap.etl.api.validation.FormatContext;
import io.cdap.cdap.etl.api.validation.ValidatingOutputFormat;
import io.cdap.plugin.common.LineageRecorder;
import io.cdap.plugin.common.batch.sink.SinkOutputFormatProvider;
import io.cdap.plugin.format.FileFormat;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Writes data to files on Google Cloud Storage.
 *
 * @param <T> the type of plugin config
 */
public abstract class AbstractFileSink<T extends PluginConfig & FileSinkProperties>
  extends BatchSink<StructuredRecord, NullWritable, StructuredRecord> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractFileSink.class);
  private static final String NAME_FORMAT = "format";
  private final T config;

  protected AbstractFileSink(T config) {
    this.config = config;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    FailureCollector collector = pipelineConfigurer.getStageConfigurer().getFailureCollector();
    config.validate(collector);
    // throw exception if there were any errors while validating the config. This could happen if format or schema is
    // invalid
    collector.getOrThrowException();

    if (config.containsMacro(NAME_FORMAT)) {
      // Deploy all format plugins. This ensures that the required plugin is available when
      // the format macro is evaluated in prepareRun.
      for (FileFormat f: FileFormat.values()) {
        try {
          pipelineConfigurer.usePlugin(ValidatingOutputFormat.PLUGIN_TYPE, f.name().toLowerCase(),
                                       f.name().toLowerCase(), config.getRawProperties());
        } catch (InvalidPluginConfigException e) {
          LOG.warn("Failed to register format '{}', which means it cannot be used when the pipeline is run. " +
                     "Missing properties: {}, invalid properties: {}", f.name(), e.getMissingProperties(),
                   e.getInvalidProperties().stream().map(InvalidPluginProperty::getName).collect(Collectors.toList()));
        }
      }
      return;
    }

    FileFormat format = config.getFormat();
    ValidatingOutputFormat validatingOutputFormat = getValidatingOutputFormat(pipelineConfigurer);
    FormatContext context = new FormatContext(collector, pipelineConfigurer.getStageConfigurer().getInputSchema());
    validateOutputFormatProvider(context, format, validatingOutputFormat);
  }

  protected ValidatingOutputFormat getValidatingOutputFormat(PipelineConfigurer pipelineConfigurer) {
    FileFormat format = config.getFormat();
    return pipelineConfigurer.usePlugin(ValidatingOutputFormat.PLUGIN_TYPE, format.name().toLowerCase(),
                                        format.name().toLowerCase(), config.getRawProperties());
  }

  @Override
  public void prepareRun(BatchSinkContext context) throws Exception {
    FailureCollector collector = context.getFailureCollector();
    config.validate(collector);
    FileFormat fileFormat = config.getFormat();
    ValidatingOutputFormat validatingOutputFormat = getOutputFormatForRun(context);
    FormatContext formatContext = new FormatContext(collector, context.getInputSchema());
    validateOutputFormatProvider(formatContext, fileFormat, validatingOutputFormat);
    collector.getOrThrowException();


    // record field level lineage information
    // needs to happen before context.addOutput(), otherwise an external dataset without schema will be created.
    Schema schema = config.getSchema();
    if (schema == null) {
      schema = context.getInputSchema();
    }
    LineageRecorder lineageRecorder = new LineageRecorder(context, config.getReferenceName());
    lineageRecorder.createExternalDataset(schema);
    if (schema != null && schema.getFields() != null && !schema.getFields().isEmpty()) {
      recordLineage(lineageRecorder,
                    schema.getFields().stream().map(Schema.Field::getName).collect(Collectors.toList()));
    }

    Map<String, String> outputProperties = new HashMap<>(validatingOutputFormat.getOutputFormatConfiguration());
    outputProperties.putAll(getFileSystemProperties(context));
    outputProperties.put(FileOutputFormat.OUTDIR, getOutputDir(context.getLogicalStartTime()));
    context.addOutput(Output.of(config.getReferenceName(),
                                new SinkOutputFormatProvider(validatingOutputFormat.getOutputFormatClassName(),
                                                             outputProperties)));
  }

  protected ValidatingOutputFormat getOutputFormatForRun(BatchSinkContext context) throws InstantiationException {
    FileFormat fileFormat = config.getFormat();
    try {
      return context.newPluginInstance(fileFormat.name().toLowerCase());
    } catch (InvalidPluginConfigException e) {
      Set<String> properties = new HashSet<>(e.getMissingProperties());
      for (InvalidPluginProperty invalidProperty : e.getInvalidProperties()) {
        properties.add(invalidProperty.getName());
      }
      String errorMessage = String.format("Format '%s' cannot be used because properties %s were not provided or " +
                                            "were invalid when the pipeline was deployed. Set the format to a " +
                                            "different value, or re-create the pipeline with all required properties.",
                                          fileFormat.name(), properties);
      throw new IllegalArgumentException(errorMessage, e);
    }
  }

  @Override
  public void transform(StructuredRecord input, Emitter<KeyValue<NullWritable, StructuredRecord>> emitter) {
    emitter.emit(new KeyValue<>(NullWritable.get(), input));
  }

  /**
   * Override this to provide any additional Configuration properties that are required by the FileSystem.
   * For example, if the FileSystem requires setting properties for credentials, those should be returned by
   * this method.
   */
  protected Map<String, String> getFileSystemProperties(BatchSinkContext context) {
    return Collections.emptyMap();
  }

  /**
   * Override this to specify a custom field level operation name and description.
   */
  protected void recordLineage(LineageRecorder lineageRecorder, List<String> outputFields) {
    lineageRecorder.recordWrite("Write", String.format("Wrote to %s files.", config.getFormat().name().toLowerCase()),
                                outputFields);
  }

  protected String getOutputDir(long logicalStartTime) {
    String suffix = config.getSuffix();
    String timeSuffix = suffix == null || suffix.isEmpty() ? "" : new SimpleDateFormat(suffix).format(logicalStartTime);
    String configPath = config.getPath();
    //Avoid the extra '/' since '/' is appended before timeSuffix in the next line
    String finalPath = configPath.endsWith("/") ? configPath.substring(0, configPath.length() - 1) : configPath;
    return String.format("%s/%s", finalPath, timeSuffix);
  }

  private void validateOutputFormatProvider(FormatContext context, FileFormat format,
                                            @Nullable ValidatingOutputFormat validatingOutputFormat) {
    FailureCollector collector = context.getFailureCollector();
    if (validatingOutputFormat == null) {
      collector.addFailure(
        String.format("Could not find the '%s' output format plugin.", format.name().toLowerCase()), null)
        .withPluginNotFound(format.name().toLowerCase(),
                            format.name().toLowerCase(),
                            ValidatingOutputFormat.PLUGIN_TYPE);
    } else {
      validatingOutputFormat.validate(context);
    }
  }
}
