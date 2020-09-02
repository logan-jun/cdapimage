/*
 * Copyright © 2018 Cask Data, Inc.
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

package io.cdap.cdap.metadata;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import io.cdap.cdap.api.metadata.MetadataReader;
import io.cdap.cdap.api.metadata.MetadataWriter;
import io.cdap.cdap.common.metadata.AbstractMetadataClient;
import io.cdap.cdap.common.runtime.RuntimeModule;
import io.cdap.cdap.data2.metadata.writer.DefaultMetadataServiceClient;
import io.cdap.cdap.data2.metadata.writer.MessagingMetadataPublisher;
import io.cdap.cdap.data2.metadata.writer.MetadataPublisher;
import io.cdap.cdap.data2.metadata.writer.MetadataServiceClient;

/**
 * Guice binding for {@link MetadataReader} and {@link MetadataWriter} for service binding see
 * {@link MetadataServiceModule}.
 */
public class MetadataReaderWriterModules extends RuntimeModule {

  @Override
  public Module getInMemoryModules() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(MetadataReader.class).to(DefaultMetadataReader.class);
        bind(MetadataPublisher.class).to(MessagingMetadataPublisher.class);
        bind(MetadataServiceClient.class).to(DefaultMetadataServiceClient.class);
      }
    };
  }

  @Override
  public Module getStandaloneModules() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(MetadataReader.class).to(DefaultMetadataReader.class);
        bind(MetadataPublisher.class).to(MessagingMetadataPublisher.class);
        bind(MetadataServiceClient.class).to(DefaultMetadataServiceClient.class);
      }
    };
  }

  @Override
  public Module getDistributedModules() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        // CDAP-13610 We should be binding MetadataAdmin which the AbstractMetadataAdmin implements.
        // Currently AbstractMetadataAdmin is just a gigantic class which has it's own apis which is exposed as
        // metadata client apis.
        bind(AbstractMetadataClient.class).to(RemoteMetadataClient.class);
        // TODO: Bind to cloud implementation in cloud mode. How to check cdap is in cloud mode?
        bind(MetadataReader.class).to(RemoteMetadataReader.class);
        bind(MetadataPublisher.class).to(MessagingMetadataPublisher.class);
        bind(MetadataServiceClient.class).to(DefaultMetadataServiceClient.class);
      }
    };
  }
}
