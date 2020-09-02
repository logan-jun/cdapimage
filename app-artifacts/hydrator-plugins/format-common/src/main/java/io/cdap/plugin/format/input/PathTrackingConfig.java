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

package io.cdap.plugin.format.input;

import com.google.common.base.Strings;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.cdap.api.plugin.PluginPropertyField;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Plugin config for input format plugins that can track the path of the file that each record was read from.
 */
public class PathTrackingConfig extends PluginConfig {
  public static final Map<String, PluginPropertyField> FIELDS;
  public static final String NAME_SCHEMA = "schema";
  private static final String SCHEMA_DESC = "Schema of the data to read.";
  private static final String PATH_FIELD_DESC =
    "Output field to place the path of the file that the record was read from. "
      + "If not specified, the file path will not be included in output records. "
      + "If specified, the field must exist in the schema and be of type string.";
  private static final String FILENAME_ONLY_DESC =
    "Whether to only use the filename instead of the URI of the file path when a path field is given. "
      + "The default value is false.";

  static {
    Map<String, PluginPropertyField> fields = new HashMap<>();
    fields.put("schema", new PluginPropertyField("schema", SCHEMA_DESC, "string", false, true));
    fields.put("pathField",
               new PluginPropertyField("pathField", PATH_FIELD_DESC, "string", false, true));
    fields.put("filenameOnly",
               new PluginPropertyField("filenameOnly", FILENAME_ONLY_DESC, "boolean", false, true));
    FIELDS = Collections.unmodifiableMap(fields);
  }

  @Macro
  @Nullable
  @Description(SCHEMA_DESC)
  protected String schema;

  @Macro
  @Nullable
  @Description(PATH_FIELD_DESC)
  protected String pathField;

  @Macro
  @Nullable
  @Description(FILENAME_ONLY_DESC)
  protected Boolean filenameOnly;

  @Nullable
  public String getPathField() {
    return pathField;
  }

  public boolean useFilenameOnly() {
    return filenameOnly == null ? false : filenameOnly;
  }

  @Nullable
  public Schema getSchema() {
    try {
      return Strings.isNullOrEmpty(schema) ? null : Schema.parseJson(schema);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid schema: " + e.getMessage(), e);
    }
  }
}
