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

package io.cdap.plugin.common;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.plugin.batch.sink.TableSink;

import javax.annotation.Nullable;

/**
 * {@link PluginConfig} for {@link TableSink}
 */
public class TableSinkConfig extends BatchReadableWritableConfig {
  @Macro
  @Name(Properties.Table.PROPERTY_SCHEMA)
  @Description("schema of the table as a JSON Object. If the table does not already exist, one will be " +
    "created with this schema, which will allow the table to be explored through Hive. If no schema is given, the " +
    "table created will not be explorable.")
  @Nullable
  private String schemaStr;

  @Macro
  @Name(Properties.Table.PROPERTY_SCHEMA_ROW_FIELD)
  @Description("The name of the record field that should be used as the row key when writing to the table.")
  private String rowField;

  public TableSinkConfig(String name, String rowField, @Nullable String schemaStr) {
    super(name);
    this.rowField = rowField;
    this.schemaStr = schemaStr;
  }

  @Nullable
  public String getSchemaStr() {
    return schemaStr;
  }

  public String getRowField() {
    return rowField;
  }
}
