/*
 * Copyright © 2015, 2016-2019 Cask Data, Inc.
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

package io.cdap.plugin.batch.sink;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.annotation.Requirements;
import io.cdap.cdap.api.dataset.lib.FileSetProperties;
import io.cdap.cdap.api.dataset.lib.TimePartitionedFileSet;
import io.cdap.cdap.etl.api.batch.BatchSink;
import io.cdap.plugin.common.FileSetUtil;
import io.cdap.plugin.format.FileFormat;

import javax.annotation.Nullable;

/**
 * A {@link BatchSink} to write Avro record to {@link TimePartitionedFileSet}
 */
@Plugin(type = BatchSink.PLUGIN_TYPE)
@Name("TPFSAvro")
@Description("Sink for a TimePartitionedFileSet that writes data in Avro format.")
@Requirements(datasetTypes = TimePartitionedFileSet.TYPE)
public class TimePartitionedFileSetDatasetAvroSink extends
  TimePartitionedFileSetSink<TimePartitionedFileSetDatasetAvroSink.TPFSAvroSinkConfig> {
  private final TPFSAvroSinkConfig config;

  public TimePartitionedFileSetDatasetAvroSink(TPFSAvroSinkConfig config) {
    super(config);
    this.config = config;
  }

  @Override
  protected String getOutputFormatName() {
    return FileFormat.AVRO.name().toLowerCase();
  }

  @Override
  protected void addFileSetProperties(FileSetProperties.Builder properties) {
    FileSetUtil.configureAvroFileSet(config.schema, properties);
  }

  /**
   * Config for TimePartitionedFileSetAvroSink
   */
  public static class TPFSAvroSinkConfig extends TPFSSinkConfig {

    @Nullable
    @Description("Used to specify the compression codec to be used for the final dataset.")
    private String compressionCodec;

    public TPFSAvroSinkConfig(String name, @Nullable String basePath, @Nullable String pathFormat,
                              @Nullable String timeZone, @Nullable String compressionCodec) {
      super(name, basePath, pathFormat, timeZone);
      this.compressionCodec = compressionCodec;
    }
  }
}
