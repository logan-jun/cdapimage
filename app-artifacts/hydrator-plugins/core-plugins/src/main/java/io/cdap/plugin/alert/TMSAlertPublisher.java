/*
 * Copyright © 2017-2019 Cask Data, Inc.
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

package io.cdap.plugin.alert;

import com.google.gson.Gson;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.messaging.MessagePublisher;
import io.cdap.cdap.api.messaging.TopicAlreadyExistsException;
import io.cdap.cdap.api.messaging.TopicNotFoundException;
import io.cdap.cdap.api.plugin.PluginConfig;
import io.cdap.cdap.etl.api.Alert;
import io.cdap.cdap.etl.api.AlertPublisher;
import io.cdap.cdap.etl.api.AlertPublisherContext;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.StageConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Publishes alerts to TMS.
 */
@Plugin(type = AlertPublisher.PLUGIN_TYPE)
@Name("TMS")
@Description("Publishes alerts to the CDAP Transaction Messaging System. Alerts will be formatted as json objects.")
public class TMSAlertPublisher extends AlertPublisher {
  public static final Gson GSON = new Gson();
  private static final Logger LOG = LoggerFactory.getLogger(TMSAlertPublisher.class);
  private final Conf conf;
  private MessagePublisher messagePublisher;
  private String publishNamespace;

  public TMSAlertPublisher(Conf conf) {
    this.conf = conf;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) throws IllegalArgumentException {
    StageConfigurer stageConfigurer = pipelineConfigurer.getStageConfigurer();
    FailureCollector collector = stageConfigurer.getFailureCollector();

    conf.validate(collector);
  }

  @Override
  public void initialize(AlertPublisherContext context) throws Exception {
    super.initialize(context);
    conf.validate(context.getFailureCollector());
    context.getFailureCollector().getOrThrowException();

    try {
      context.getTopicProperties(conf.topic);
    } catch (TopicNotFoundException e) {
      if (conf.autoCreateTopic) {
        // this is checked at configure time unless namespace is a macro
        if (conf.namespace != null) {
          throw new IllegalArgumentException(
            String.format("Topic '%s' does not exist and cannot be auto-created since namespace is set." +
                            "Topics can only be auto-created if no namespace is given.", conf.topic));
        }

        try {
          context.createTopic(conf.topic);
        } catch (TopicAlreadyExistsException e1) {
          // somebody happened to create it at the same time, ignore
        }
      } else {
        throw e;
      }
    }
    messagePublisher = context.getDirectMessagePublisher();
    // TODO: use pipeline namespace instead of 'default' once namespace is available through context
    publishNamespace = conf.namespace == null ? context.getNamespace() : conf.namespace;
  }

  @Override
  public void publish(Iterator<Alert> iterator) throws Exception {
    long tickTime = System.currentTimeMillis();
    int publishedSinceLastTick = 0;
    while (iterator.hasNext()) {
      messagePublisher.publish(publishNamespace, conf.topic, GSON.toJson(iterator.next()));
      publishedSinceLastTick++;
      long currentTime = System.currentTimeMillis();
      if (currentTime - tickTime > 1000) {
        tickTime = currentTime;
        publishedSinceLastTick = 0;
      } else if (publishedSinceLastTick >= conf.maxAlertsPerSecond) {
        long sleepTime = tickTime + 1000 - currentTime;
        LOG.info("Hit maximum of {} published alerts in the past second, sleeping for {} millis.",
                 publishedSinceLastTick, sleepTime);
        TimeUnit.MILLISECONDS.sleep(tickTime + 1000 - currentTime);
      }
    }
  }

  /**
   * Plugin configuration
   */
  public static class Conf extends PluginConfig {
    @Macro
    @Description("The TMS topic to publish messages to.")
    private String topic;

    @Macro
    @Nullable
    @Description("The namespace of the topic to publish messages to. If none is specified, " +
      "the pipeline namespace will be used.")
    private String namespace;

    @Nullable
    @Description("Whether to create the topic in the pipeline namespace if the topic does not already exist. " +
      "Cannot be set to true if namespace is set. Defaults to false.")
    private Boolean autoCreateTopic;

    @Nullable
    @Description("The maximum number of alerts to publish per second. Defaults to 100.")
    private Integer maxAlertsPerSecond;

    private Conf() {
      topic = null;
      namespace = null;
      autoCreateTopic = false;
      maxAlertsPerSecond = 100;
    }

    private void validate(FailureCollector collector) {
      if (autoCreateTopic && namespace != null) {
        collector.addFailure("Cannot auto-create topic when namespace is set.",
                             "Namespace must not be set to auto-create topic.").withConfigProperty("namespace")
          .withConfigProperty("autoCreateTopic");
      }
      if (maxAlertsPerSecond < 1) {
        collector.addFailure(String.format("Invalid maxAlertsPerSecond %d. Must be at least 1.", maxAlertsPerSecond),
                             "").withConfigProperty("maxAlertsPerSecond");
      }
    }
  }
}
