/*
 * Copyright © 2016-2019 Cask Data, Inc.
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

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.batch.BatchActionContext;
import io.cdap.cdap.etl.api.batch.PostAction;
import io.cdap.plugin.common.batch.action.Condition;
import io.cdap.plugin.common.batch.action.ConditionConfig;
import io.cdap.plugin.common.http.HTTPConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.HttpMethod;

/**
 * Makes an HTTP call at the end of a pipeline run.
 */
@Plugin(type = PostAction.PLUGIN_TYPE)
@Name("HTTPCallback")
@Description("Makes an HTTP call at the end of a pipeline run.")
public class HTTPCallbackAction extends PostAction {
  private static final Logger LOG = LoggerFactory.getLogger(HTTPCallbackAction.class);
  private final HttpRequestConf conf;

  public HTTPCallbackAction(HttpRequestConf conf) {
    this.conf = conf;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    conf.validate(pipelineConfigurer.getStageConfigurer().getFailureCollector());
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void run(BatchActionContext batchActionContext) throws Exception {
    FailureCollector collector = batchActionContext.getFailureCollector();
    conf.validate(collector);
    collector.getOrThrowException();

    if (!conf.shouldRun(batchActionContext)) {
      return;
    }

    int retries = 0;
    Exception exception = null;
    do {
      HttpURLConnection conn = null;
      Map<String, String> headers = conf.getRequestHeadersMap();
      try {
        URL url = new URL(conf.getUrl());
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(conf.method.toUpperCase());
        conn.setConnectTimeout(conf.getConnectTimeout());
        for (Map.Entry<String, String> propertyEntry : headers.entrySet()) {
          conn.addRequestProperty(propertyEntry.getKey(), propertyEntry.getValue());
        }
        if (conf.body != null) {
          conn.setDoOutput(true);
          try (OutputStream outputStream = conn.getOutputStream()) {
            outputStream.write(conf.body.getBytes(Charsets.UTF_8));
          }
        }
        LOG.info("Request to {} resulted in response code {}.", conf.getUrl(), conn.getResponseCode());
        break;
      } catch (MalformedURLException | ProtocolException e) {
        // these should never happen because the url and request method are checked at configure time
        throw new IllegalStateException("Error opening url connection. Reason: " + e.getMessage(), e);
      } catch (Exception e) {
        LOG.warn("Error making {} request to url {} with headers {}.", conf.method, conf.getUrl(), headers);
        exception = e;
      } finally {
        if (conn != null) {
          conn.disconnect();
        }
      }
      retries++;
    } while (retries < conf.numRetries);

    if (exception != null) {
      throw exception;
    }
  }

  /**
   * Config for the http callback action.
   */
  public static final class HttpRequestConf extends HTTPConfig {
    private static final Set<String> METHODS = ImmutableSet.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS,
                                                               HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE);
    static final String NAME_METHOD = "method";
    static final String NAME_NUM_RETRIES = "numRetries";
    static final String NAME_RUN_CONDITION = "runCondition";

    @Name(NAME_RUN_CONDITION)
    @Nullable
    @Description("When to run the action. Must be 'completion', 'success', or 'failure'. Defaults to 'completion'. " +
      "If set to 'completion', the action will be executed regardless of whether " +
      "the pipeline run succeeded or failed. " +
      "If set to 'success', the action will only be executed if the pipeline run succeeded. " +
      "If set to 'failure', the action will only be executed if the pipeline run failed.")
    @Macro
    public String runCondition;

    @Name(NAME_METHOD)
    @Description("The http request method.")
    @Macro
    private String method;

    @Nullable
    @Description("The http request body.")
    @Macro
    private String body;

    @Nullable
    @Description("The number of times the request should be retried if the request fails. Defaults to 0.")
    @Macro
    private Integer numRetries;

    public HttpRequestConf() {
      super();
      numRetries = 0;
      runCondition = Condition.COMPLETION.name();
    }

    public void validate(FailureCollector collector) {
      super.validate(collector);
      if (!containsMacro(NAME_METHOD) && !METHODS.contains(method.toUpperCase())) {
        collector.addFailure(String.format("Invalid request method '%s'.", method),
                             String.format("Supported methods are : %s", Joiner.on(',').join(METHODS)))
          .withConfigProperty(NAME_METHOD);
      }
      if (!containsMacro(NAME_NUM_RETRIES) && numRetries != null && numRetries < 0) {
        collector.addFailure(String.format("Invalid numRetries '%d'.", numRetries),
                             "Retries must be a positive number or zero.")
          .withConfigProperty(NAME_NUM_RETRIES);
      }
    }

    public boolean shouldRun(BatchActionContext context) {
      if (!containsMacro(NAME_RUN_CONDITION)) {
        return new ConditionConfig(runCondition).shouldRun(context);
      } else {
        return false;
      }
    }
  }
}
