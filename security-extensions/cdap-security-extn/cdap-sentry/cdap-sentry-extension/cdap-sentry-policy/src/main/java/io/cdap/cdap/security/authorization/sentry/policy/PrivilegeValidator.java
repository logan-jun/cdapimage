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

package io.cdap.cdap.security.authorization.sentry.policy;

import com.google.common.collect.Lists;
import io.cdap.cdap.security.authorization.sentry.model.ActionFactory;
import io.cdap.cdap.security.authorization.sentry.model.Authorizable;
import org.apache.sentry.policy.common.PolicyConstants;
import org.apache.sentry.policy.common.PrivilegeValidatorContext;
import org.apache.shiro.config.ConfigurationException;

import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * <p> A simple PrivilegeValidator which validates the privileges to be consistent with the order in which we expect
 * them. The ordering of expected privileges as of now is: <p/>
 * Namespace  : instance->instanceName->namespace=namespaceName->action=someValidAction
 * Artifact   : instance->instanceName->namespace=namespaceName->artifact->artifactName->action=someValidAction
 * Dataset    : instance->instanceName->namespace=namespaceName->dataset->datasetName->action=someValidAction
 * Application: instance->instanceName->namespace=namespaceName->application->applicationName->action=someValidAction
 * Program    : instance->instanceName->namespace=namespaceName->application->applicationName->program=programName->
 * action=someValidAction
 */
public class PrivilegeValidator implements org.apache.sentry.policy.common.PrivilegeValidator {

  private final ActionFactory actionFactory = new ActionFactory();

  @Override
  public void validate(PrivilegeValidatorContext context) throws ConfigurationException {

    Deque<String> privileges = Lists.newLinkedList(PolicyConstants.AUTHORIZABLE_SPLITTER.split(context.getPrivilege()));

    // Check privilege splits length is at least 2 the smallest privilege possible with action. Example:
    // smallest privilege of size 2 : instance=instance1->action=read
    if (privileges.size() < 2) {
      throw new ConfigurationException("Invalid Privilege Exception: Privilege can be given to an " +
                                         "instance or " +
                                         "instance -> namespace or " +
                                         "instance -> namespace -> (artifact|applications|stream|dataset) or " +
                                         "instance -> namespace -> application -> program");
    }

    // Check the last part is a valid action
    if (!isAction(privileges.removeLast())) {
      throw new ConfigurationException("CDAP privilege must end with a valid action.\n");
    }

    // the first valid authorizable type is instance since all privilege string should start with it
    Set<Authorizable.AuthorizableType> validTypes = EnumSet.of(Authorizable.AuthorizableType.INSTANCE);
    while (!privileges.isEmpty()) {
      Authorizable authorizable = ModelAuthorizables.from(privileges.removeFirst());
      // if we were expecting no validTypes for this authorizable type that means the privilege string has more
      // authorizable when we were expecting it to end
      if (validTypes.isEmpty()) {
        throw new ConfigurationException(String.format("Was expecting end of Authorizables. Found unexpected " +
                                                         "authorizable %s of type %s",
                                                       authorizable, authorizable.getAuthzType()));
      }
      validTypes = validatePrivilege(authorizable.getAuthzType(), validTypes);
    }
  }

  /**
   * Validates that the given authorizable type exists in the validTypes and updates the validTypes depending on the
   * current authorizable type.
   *
   * @param authzType the current authorizable type
   * @param validTypes expected authorizable types
   * @return updates {@link Set} of {@link Authorizable.AuthorizableType} which are expected for the given
   * authorizable type
   */
  private Set<Authorizable.AuthorizableType> validatePrivilege(Authorizable.AuthorizableType authzType,
                                                               Set<Authorizable.AuthorizableType> validTypes) {
    if (!validTypes.contains(authzType)) {
      throw new ConfigurationException(String.format("Expecting authorizable types %s but found %s",
                                                     validTypes.toString(), authzType));
    }
    switch (authzType) {
      case INSTANCE:
        validTypes = EnumSet.of(Authorizable.AuthorizableType.NAMESPACE);
        break;
      case NAMESPACE:
        validTypes = EnumSet.of(Authorizable.AuthorizableType.APPLICATION,
                                Authorizable.AuthorizableType.ARTIFACT,
                                Authorizable.AuthorizableType.DATASET);
        break;
      case APPLICATION:
        validTypes = EnumSet.of(Authorizable.AuthorizableType.PROGRAM);
        break;
      case ARTIFACT:
      case DATASET:
      case PROGRAM:
      case PRINCIPAL:
        validTypes = new HashSet<>(); // we don't expect any other authorizable after this
    }
    return validTypes;
  }

  private boolean isAction(String privilegePart) {
    String[] action = privilegePart.toLowerCase().split(PolicyConstants.KV_SEPARATOR);
    return (action.length == 2 && action[0].equalsIgnoreCase(PolicyConstants.PRIVILEGE_NAME) &&
      actionFactory.getActionByName(action[1]) != null);
  }
}
