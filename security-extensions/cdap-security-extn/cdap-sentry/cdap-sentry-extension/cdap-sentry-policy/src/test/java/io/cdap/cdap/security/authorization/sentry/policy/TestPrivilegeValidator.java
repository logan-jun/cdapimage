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

import org.apache.sentry.policy.common.PrivilegeValidatorContext;
import org.apache.shiro.config.ConfigurationException;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test for {@link PrivilegeValidator}
 */
public class TestPrivilegeValidator {

  private final PrivilegeValidator privilegeValidator = new PrivilegeValidator();

  @Test
  public void testOnlyWithInstance() {
    testInvalidPrivileges("instance=instance1");
  }

  @Test
  public void testWithoutInstance() {
    testInvalidPrivileges("namespace=namespace1->action=read");
    testInvalidPrivileges("namespace=namespace1->application=application1");
    // test action as the first part
    testInvalidPrivileges("action=read");
  }

  @Test
  public void testValidPrivileges() {
    // instance
    testValidPrivilege("instance=instance1->action=admin");

    //namespace
    testValidPrivilege("instance=instance1->namespace=namespace1->action=read");

    // artifact
    testValidPrivilege("instance=instance1->namespace=namespace1->artifact=artifact1.0->action=write");

    // dataset
    testValidPrivilege("instance=instance1->namespace=namespace1->dataset=dataset1->action=write");

    // application
    testValidPrivilege("instance=instance1->namespace=namespace1->application=application1->action=read");

    // program
    testValidPrivilege("instance=instance1->namespace=namespace1->application=application1->program=worker.program1" +
                         "->action=execute");

  }

  @Test
  public void testInvalidAuthorizables() {
    // instance
    testInvalidPrivileges("inNstance=instance1->action=admin");

    //namespace
    testInvalidPrivileges("instance=instance1->namesSpace=namespace1->action=read");

    // artifact
    testInvalidPrivileges("instance=instance1->namespace=namespace1->artTifact=arttifact1.0->action=write");

    // dataset
    testInvalidPrivileges("instance=instance1->namespace=namespace1->dataAset=dataset1->action=write");

    // application
    testInvalidPrivileges("instance=instance1->namespace=namespace1->appliCcation=application1->action=read");

    // program
    testInvalidPrivileges("instance=instance1->namespace=namespace1->application=application1->progGram=worker." +
                            "program1->action=execute");
  }

  @Test
  public void testPrivilegeNotEndingWithAction() {
    testInvalidPrivileges("instance=instance1->namespace=namespace1->dataset=dataset1");
  }

  @Test
  public void testPrivilegeWithInvalidAction() {
    testInvalidPrivileges("instance=instance1->namespace=namespace1->dataset=dataset1->action=godmode");
  }

  @Test
  public void testWithMorePartsInPrivilege() {
    testInvalidPrivileges("instance=instance1->namespace=namespace1->dataset=dataset1->program=worker." +
                            "program1->action=read");
  }

  @Test
  public void testExcatlyOneInstance() {
    testInvalidPrivileges("instance=instance1->instance=instance2->namespace=namespace1");
  }

  private void testValidPrivilege(String privilegeString) {
    try {
      privilegeValidator.validate(new PrivilegeValidatorContext(privilegeString));
    } catch (ConfigurationException ce) {
      Assert.fail(String.format("Failed to validate a valid privilege %s", privilegeString));
    }
  }

  private void testInvalidPrivileges(String privilege) {
    try {
      privilegeValidator.validate(new PrivilegeValidatorContext(privilege));
      Assert.fail("Should throw an exception");
    } catch (Exception e) {
      // expected
    }
  }
}
