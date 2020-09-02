/*
 * Copyright © 2014-2017 Cask Data, Inc.
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

package io.cdap.cdap.explore.client;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * An Explore Client that uses the provided host and port to talk to a server
 * implementing {@link io.cdap.cdap.explore.service.Explore} over HTTP/HTTPS.
 */
public class FixedAddressExploreClient extends AbstractExploreClient {
  private final InetSocketAddress addr;
  private final String authToken;
  private final boolean sslEnabled;
  private final boolean verifySSLCert;

  public FixedAddressExploreClient(String host, int port, @Nullable String authToken,
                                   boolean sslEnabled, boolean verifySSLCert) {
    this.addr = InetSocketAddress.createUnresolved(host, port);
    this.authToken = authToken;
    this.sslEnabled = sslEnabled;
    this.verifySSLCert = verifySSLCert;
  }

  @Override
  protected InetSocketAddress getExploreServiceAddress() {
    return addr;
  }

  @Override
  protected String getAuthToken() {
    return authToken;
  }

  @Override
  protected boolean isSSLEnabled() {
    return sslEnabled;
  }

  @Override
  protected boolean verifySSLCert() {
    return verifySSLCert;
  }
}
