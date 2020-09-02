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

package io.cdap.plugin.format.delimited.input;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.plugin.PluginClass;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.validation.FormatContext;
import io.cdap.cdap.etl.api.validation.ValidatingInputFormat;
import io.cdap.plugin.format.input.PathTrackingConfig;
import io.cdap.plugin.format.input.PathTrackingInputFormatProvider;

import java.util.Map;

/**
 * Reads delimited text into StructuredRecords.
 */
@Plugin(type = ValidatingInputFormat.PLUGIN_TYPE)
@Name(CSVInputFormatProvider.NAME)
@Description(CSVInputFormatProvider.DESC)
public class CSVInputFormatProvider extends PathTrackingInputFormatProvider<DelimitedConfig> {
  static final String NAME = "csv";
  static final String DESC = "Plugin for reading files in csv format.";
  public static final PluginClass PLUGIN_CLASS =
    new PluginClass(ValidatingInputFormat.PLUGIN_TYPE, NAME, DESC, CSVInputFormatProvider.class.getName(),
                    "conf", DelimitedConfig.DELIMITED_FIELDS);
  private final DelimitedConfig conf;

  public CSVInputFormatProvider(DelimitedConfig conf) {
    super(conf);
    this.conf = conf;
  }

  @Override
  public String getInputFormatClassName() {
    return CombineDelimitedInputFormat.class.getName();
  }

  @Override
  protected void validate() {
    if (!conf.containsMacro(PathTrackingConfig.NAME_SCHEMA) && conf.getSchema() == null) {
      throw new IllegalArgumentException("CSV format cannot be used without specifying a schema.");
    }
  }

  @Override
  public void validate(FormatContext context) {
    Schema schema = super.getSchema(context);
    FailureCollector collector = context.getFailureCollector();
    if (!conf.containsMacro(PathTrackingConfig.NAME_SCHEMA) && schema == null) {
      collector.addFailure("CSV format cannot be used without specifying a schema.", "Schema must be specified.")
        .withConfigProperty("schema");
    }
  }

  @Override
  protected void addFormatProperties(Map<String, String> properties) {
    properties.put(PathTrackingDelimitedInputFormat.DELIMITER, ",");
    properties.put(PathTrackingDelimitedInputFormat.SKIP_HEADER, String.valueOf(conf.getSkipHeader()));
  }
}
