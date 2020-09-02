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

package io.cdap.plugin.batch.action;

import com.google.common.annotations.VisibleForTesting;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.StageConfigurer;
import io.cdap.cdap.etl.api.action.Action;
import io.cdap.cdap.etl.api.action.ActionContext;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Action that deletes file(s).
 */
@Plugin(type = Action.PLUGIN_TYPE)
@Name("FileDelete")
@Description("Deletes files.")
public class FileDeleteAction extends Action {
  private static final Logger LOG = LoggerFactory.getLogger(FileDeleteAction.class);

  private Conf config;
  private PathFilter filter;

  public FileDeleteAction(Conf config) {
    this.config = config;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    StageConfigurer stageConfigurer = pipelineConfigurer.getStageConfigurer();
    FailureCollector collector = stageConfigurer.getFailureCollector();
    config.validate(collector);
  }

  @Override
  public void run(ActionContext context) throws Exception {
    Path path = new Path(config.path);

    FileSystem fileSystem = path.getFileSystem(new Configuration());

    FileStatus[] listFiles;
    if (config.fileRegex != null) {
      PathFilter filter = new PathFilter() {
        private final Pattern pattern = Pattern.compile(config.fileRegex);

        @Override
        public boolean accept(Path path) {
          return pattern.matcher(path.getName()).matches();
        }
      };
      listFiles = fileSystem.listStatus(path, filter);
    } else {
      listFiles = fileSystem.listStatus(path);
    }

    for (FileStatus file : listFiles) {
      Path currPath = file.getPath();
      removePath(fileSystem, currPath);
    }


    if (fileSystem.isDirectory(path) && config.fileRegex == null) {
      removePath(fileSystem, path);
    }

  }

  public void removePath(FileSystem fileSystem, Path currPath) throws Exception {
    try {
      if (!fileSystem.delete(currPath, true)) {
        if (!config.continueOnError) {
          throw new IOException(String.format("Removal of %s was unsuccessful.", currPath.toString()));
        }
        LOG.warn("Removal of {} was unsuccessful.", currPath.toString());
      }
    } catch (IOException e) {
      if (!config.continueOnError) {
        throw e;
      }
      LOG.warn("Removal of {} was unsuccessful.", currPath.toString());
    }
  }

  /**
   * Config class that contains all properties necessary to execute a file delete command.
   */
  public class Conf extends PluginConfig {

    // Constants for property names
    private static final String FILE_REGEX = "fileRegex";

    @Description("The full path of the file or files that need to be deleted. If path points to a file, " +
      "the file will be removed. If path points to a directory with no regex specified, the directory and all of " +
      "its contents will be removed. If a regex is specified, only the files and directories matching that regex " +
      "will be removed")
    @Macro
    private String path;

    @Description("Regular expression to filter the files in the source directory that will be deleted")
    @Nullable
    @Macro
    private String fileRegex;

    @Description("Indicates if the pipeline should continue if the delete fails")
    private boolean continueOnError;

    public void validate(FailureCollector collector) {
      if (!containsMacro(FILE_REGEX) && fileRegex != null) {
        try {
          Pattern.compile(fileRegex);
        } catch (Exception e) {
          collector.addFailure(String.format("File regex '%s' is invalid: %s.", fileRegex, e.getMessage()),
                               "Provide valid regex.").withConfigProperty(FILE_REGEX);
        }
      }
    }

    @VisibleForTesting
    Conf(String path, String fileRegex, boolean continueOnError) {
      this.path = path;
      this.fileRegex = fileRegex;
      this.continueOnError = continueOnError;
    }
  }
}
