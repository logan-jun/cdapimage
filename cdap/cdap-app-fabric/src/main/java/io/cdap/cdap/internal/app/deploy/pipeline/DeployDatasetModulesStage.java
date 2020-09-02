/*
 * Copyright © 2014 Cask Data, Inc.
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

package io.cdap.cdap.internal.app.deploy.pipeline;

import com.google.common.reflect.TypeToken;
import io.cdap.cdap.api.dataset.module.DatasetModule;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.data2.dataset2.DatasetFramework;
import io.cdap.cdap.internal.app.deploy.LocalApplicationManager;
import io.cdap.cdap.pipeline.AbstractStage;
import io.cdap.cdap.proto.id.KerberosPrincipalId;
import io.cdap.cdap.security.authorization.AuthorizationUtil;
import io.cdap.cdap.security.impersonation.OwnerAdmin;
import io.cdap.cdap.security.spi.authentication.AuthenticationContext;

/**
 * This {@link io.cdap.cdap.pipeline.Stage} is responsible for automatic
 * deploy of the {@link DatasetModule}s specified by application.
 */
public class DeployDatasetModulesStage extends AbstractStage<ApplicationDeployable> {
  private final DatasetModulesDeployer datasetModulesDeployer;
  private final OwnerAdmin ownerAdmin;
  private final AuthenticationContext authenticationContext;

  public DeployDatasetModulesStage(CConfiguration configuration,
                                   DatasetFramework datasetFramework, DatasetFramework inMemoryDatasetFramework,
                                   OwnerAdmin ownerAdmin, AuthenticationContext authenticationContext) {
    super(TypeToken.of(ApplicationDeployable.class));
    this.datasetModulesDeployer = new DatasetModulesDeployer(datasetFramework, inMemoryDatasetFramework, configuration);
    this.ownerAdmin = ownerAdmin;
    this.authenticationContext = authenticationContext;
  }

  /**
   * Deploys dataset modules specified in the given application spec.
   *
   * @param input An instance of {@link ApplicationDeployable}
   */
  @Override
  public void process(ApplicationDeployable input) throws Exception {
    KerberosPrincipalId ownerPrincipal = input.getOwnerPrincipal();
    // get the authorizing user
    String authorizingUser =
      AuthorizationUtil.getAppAuthorizingUser(ownerAdmin, authenticationContext, input.getApplicationId(),
                                              ownerPrincipal);
    ClassLoader classLoader = getContext().getProperty(LocalApplicationManager.ARTIFACT_CLASSLOADER_KEY);
    datasetModulesDeployer.deployModules(input.getApplicationId().getParent(),
                                         input.getSpecification().getDatasetModules(),
                                         input.getArtifactLocation(),
                                         classLoader, authorizingUser);

    // Emit the input to next stage.
    emit(input);
  }
}
