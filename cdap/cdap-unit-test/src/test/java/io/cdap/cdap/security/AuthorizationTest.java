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

package io.cdap.cdap.security;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.cdap.cdap.AllProgramsApp;
import io.cdap.cdap.ConfigTestApp;
import io.cdap.cdap.api.Config;
import io.cdap.cdap.api.artifact.ArtifactSummary;
import io.cdap.cdap.api.common.Bytes;
import io.cdap.cdap.api.dataset.lib.CloseableIterator;
import io.cdap.cdap.api.dataset.lib.KeyValue;
import io.cdap.cdap.api.dataset.lib.KeyValueTable;
import io.cdap.cdap.api.dataset.lib.ObjectMappedTable;
import io.cdap.cdap.api.dataset.lib.ObjectStore;
import io.cdap.cdap.api.dataset.lib.PartitionedFileSet;
import io.cdap.cdap.api.schedule.SchedulableProgramType;
import io.cdap.cdap.api.workflow.ScheduleProgramInfo;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.id.Id;
import io.cdap.cdap.common.namespace.NamespaceAdmin;
import io.cdap.cdap.common.test.AppJarHelper;
import io.cdap.cdap.common.utils.Tasks;
import io.cdap.cdap.internal.app.runtime.schedule.ProgramScheduleStatus;
import io.cdap.cdap.internal.app.runtime.schedule.trigger.TimeTrigger;
import io.cdap.cdap.internal.schedule.constraint.Constraint;
import io.cdap.cdap.proto.ApplicationDetail;
import io.cdap.cdap.proto.DatasetDetail;
import io.cdap.cdap.proto.NamespaceMeta;
import io.cdap.cdap.proto.ProgramRecord;
import io.cdap.cdap.proto.ProgramRunStatus;
import io.cdap.cdap.proto.ProgramType;
import io.cdap.cdap.proto.RunRecord;
import io.cdap.cdap.proto.ScheduleDetail;
import io.cdap.cdap.proto.artifact.AppRequest;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.ArtifactId;
import io.cdap.cdap.proto.id.DatasetId;
import io.cdap.cdap.proto.id.DatasetModuleId;
import io.cdap.cdap.proto.id.DatasetTypeId;
import io.cdap.cdap.proto.id.EntityId;
import io.cdap.cdap.proto.id.KerberosPrincipalId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.proto.id.ProgramId;
import io.cdap.cdap.proto.id.ScheduleId;
import io.cdap.cdap.proto.security.Action;
import io.cdap.cdap.proto.security.Authorizable;
import io.cdap.cdap.proto.security.Principal;
import io.cdap.cdap.proto.security.Privilege;
import io.cdap.cdap.security.authorization.InMemoryAuthorizer;
import io.cdap.cdap.security.spi.authentication.SecurityRequestContext;
import io.cdap.cdap.security.spi.authorization.Authorizer;
import io.cdap.cdap.security.spi.authorization.UnauthorizedException;
import io.cdap.cdap.spark.stream.TestSparkCrossNSDatasetApp;
import io.cdap.cdap.test.ApplicationManager;
import io.cdap.cdap.test.ArtifactManager;
import io.cdap.cdap.test.DataSetManager;
import io.cdap.cdap.test.MapReduceManager;
import io.cdap.cdap.test.ProgramManager;
import io.cdap.cdap.test.ScheduleManager;
import io.cdap.cdap.test.ServiceManager;
import io.cdap.cdap.test.SlowTests;
import io.cdap.cdap.test.SparkManager;
import io.cdap.cdap.test.TestBase;
import io.cdap.cdap.test.TestConfiguration;
import io.cdap.cdap.test.WorkflowManager;
import io.cdap.cdap.test.app.AppWithSchedule;
import io.cdap.cdap.test.app.CrossNsDatasetAccessApp;
import io.cdap.cdap.test.app.DatasetCrossNSAccessWithMAPApp;
import io.cdap.cdap.test.app.DummyApp;
import io.cdap.cdap.test.artifacts.plugins.ToStringPlugin;
import io.cdap.common.http.HttpRequest;
import io.cdap.common.http.HttpRequests;
import io.cdap.common.http.HttpResponse;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.twill.filesystem.LocalLocationFactory;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/**
 * Unit tests with authorization enabled.
 */
public class AuthorizationTest extends TestBase {

  @ClassRule
  public static final TestConfiguration CONFIG = new TestConfiguration(
    Constants.Explore.EXPLORE_ENABLED, false,
    Constants.Security.Authorization.CACHE_MAX_ENTRIES, 0
  );
  private static final EnumSet<Action> ALL_ACTIONS = EnumSet.allOf(Action.class);

  private static final Principal ALICE = new Principal("alice", Principal.PrincipalType.USER);
  private static final Principal BOB = new Principal("bob", Principal.PrincipalType.USER);
  private static final NamespaceId AUTH_NAMESPACE = new NamespaceId("authorization");
  private static final NamespaceMeta AUTH_NAMESPACE_META =
    new NamespaceMeta.Builder().setName(AUTH_NAMESPACE.getNamespace()).build();

  private static String oldUser;
  private static Set<EntityId> cleanUpEntities;

  /**
   * An {@link ExternalResource} that wraps a {@link TemporaryFolder} and {@link TestConfiguration} to execute them in
   * a chain.
   */
  private static final class AuthTestConf extends ExternalResource {
    private final TemporaryFolder tmpFolder = new TemporaryFolder();
    private TestConfiguration testConf;

    @Override
    public Statement apply(final Statement base, final Description description) {
      // Apply the TemporaryFolder on a Statement that creates a TestConfiguration and applies on base
      return tmpFolder.apply(new Statement() {
        @Override
        public void evaluate() throws Throwable {
          testConf = new TestConfiguration(getAuthConfigs(tmpFolder.newFolder()));
          testConf.apply(base, description).evaluate();
        }
      }, description);
    }

    private static String[] getAuthConfigs(File tmpDir) throws IOException {
      LocationFactory locationFactory = new LocalLocationFactory(tmpDir);
      Location authExtensionJar = AppJarHelper.createDeploymentJar(locationFactory, InMemoryAuthorizer.class);
      return new String[]{
        Constants.Security.ENABLED, "true",
        Constants.Security.Authorization.ENABLED, "true",
        Constants.Security.Authorization.EXTENSION_JAR_PATH, authExtensionJar.toURI().getPath(),
        // we only want to test authorization, but we don't specify principal/keytab, so disable kerberos
        Constants.Security.KERBEROS_ENABLED, "false",
      };
    }
  }

  @ClassRule
  public static final AuthTestConf AUTH_TEST_CONF = new AuthTestConf();

  @BeforeClass
  public static void setup() {
    oldUser = SecurityRequestContext.getUserId();
  }

  @Before
  public void setupTest() throws Exception {
    Assert.assertEquals(ImmutableSet.<Privilege>of(), getAuthorizer().listPrivileges(ALICE));
    SecurityRequestContext.setUserId(ALICE.getName());
    cleanUpEntities = new HashSet<>();
  }

  @Test
  public void testNamespaces() throws Exception {
    NamespaceAdmin namespaceAdmin = getNamespaceAdmin();
    Authorizer authorizer = getAuthorizer();
    try {
      namespaceAdmin.create(AUTH_NAMESPACE_META);
      Assert.fail("Namespace create should have failed because alice is not authorized on " + AUTH_NAMESPACE);
    } catch (UnauthorizedException expected) {
      // expected
    }
    createAuthNamespace();
    Assert.assertTrue(namespaceAdmin.list().contains(AUTH_NAMESPACE_META));
    namespaceAdmin.get(AUTH_NAMESPACE);
    // revoke privileges
    revokeAndAssertSuccess(AUTH_NAMESPACE);
    try {
      Assert.assertTrue(namespaceAdmin.list().isEmpty());
      namespaceAdmin.exists(AUTH_NAMESPACE);
      Assert.fail("Namespace existence check should fail since the privilege of alice has been revoked");
    } catch (UnauthorizedException expected) {
      // expected
    }
    // grant privileges again
    grantAndAssertSuccess(AUTH_NAMESPACE, ALICE, ImmutableSet.of(Action.ADMIN));
    namespaceAdmin.exists(AUTH_NAMESPACE);
    Assert.assertEquals(ImmutableSet.of(new Privilege(AUTH_NAMESPACE, Action.ADMIN)), authorizer.listPrivileges(ALICE));
    NamespaceMeta updated = new NamespaceMeta.Builder(AUTH_NAMESPACE_META).setDescription("new desc").build();
    namespaceAdmin.updateProperties(AUTH_NAMESPACE, updated);
    Assert.assertEquals(updated, namespaceAdmin.get(AUTH_NAMESPACE));
  }

  @Test
  @Category(SlowTests.class)
  public void testApps() throws Exception {
    try {
      deployApplication(NamespaceId.DEFAULT, DummyApp.class);
      Assert.fail("App deployment should fail because alice does not have ADMIN privilege on the application");
    } catch (UnauthorizedException e) {
      // Expected
    }
    createAuthNamespace();
    Authorizer authorizer = getAuthorizer();
    ApplicationId dummyAppId = AUTH_NAMESPACE.app(DummyApp.class.getSimpleName());
    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(dummyAppId, EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.artifact(DummyApp.class.getSimpleName(), "1.0-SNAPSHOT"), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset("whom"), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset("customDataset"), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.datasetType(KeyValueTable.class.getName()), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    // alice will not be able to deploy the app since she does not have privilege on the implicit dataset module
    try {
      deployApplication(AUTH_NAMESPACE, DummyApp.class);
      Assert.fail();
    } catch (UnauthorizedException e) {
      // expected
    }

    // grant alice the required implicit type and module
    grantAndAssertSuccess(AUTH_NAMESPACE.datasetType(DummyApp.CustomDummyDataset.class.getName()), ALICE,
                          EnumSet.of(Action.ADMIN));
    cleanUpEntities.add(AUTH_NAMESPACE.datasetType(DummyApp.CustomDummyDataset.class.getName()));
    grantAndAssertSuccess(AUTH_NAMESPACE.datasetModule(DummyApp.CustomDummyDataset.class.getName()), ALICE,
                          EnumSet.of(Action.ADMIN));
    cleanUpEntities.add(AUTH_NAMESPACE.datasetModule(DummyApp.CustomDummyDataset.class.getName()));

    // this time it should be successful
    ApplicationManager appManager = deployApplication(AUTH_NAMESPACE, DummyApp.class);
    // Bob should not have any privileges on Alice's app
    Assert.assertTrue("Bob should not have any privileges on alice's app", authorizer.listPrivileges(BOB).isEmpty());
    // update should succeed because alice has admin privileges on the app
    appManager.update(new AppRequest(new ArtifactSummary(DummyApp.class.getSimpleName(), "1.0-SNAPSHOT")));
    // Update should fail for Bob
    SecurityRequestContext.setUserId(BOB.getName());
    try {
      appManager.update(new AppRequest(new ArtifactSummary(DummyApp.class.getSimpleName(), "1.0-SNAPSHOT")));
      Assert.fail("App update should have failed because Bob does not have admin privileges on the app.");
    } catch (UnauthorizedException expected) {
      // expected
    }
    // grant READ and WRITE to Bob
    grantAndAssertSuccess(dummyAppId, BOB, ImmutableSet.of(Action.READ, Action.WRITE));
    // delete should fail
    try {
      appManager.delete();
    } catch (UnauthorizedException expected) {
      // expected
    }
    // grant ADMIN to Bob. Now delete should succeed
    grantAndAssertSuccess(dummyAppId, BOB, ImmutableSet.of(Action.ADMIN));
    // deletion should succeed since BOB has privileges on the app
    appManager.delete();

    // Should still have the privilege for the app since we no longer revoke privileges after deletion of an entity
    Assert.assertTrue(!getAuthorizer().isVisible(Collections.singleton(dummyAppId), BOB).isEmpty());

    // bob should still have privileges granted to him
    Assert.assertEquals(3, authorizer.listPrivileges(BOB).size());
    // switch back to Alice
    SecurityRequestContext.setUserId(ALICE.getName());
    // Deploy a couple of apps in the namespace
    // Deploy dummy app should be successful since we already pre-grant the required privileges
    deployApplication(AUTH_NAMESPACE, DummyApp.class);

    final ApplicationId appId = AUTH_NAMESPACE.app(AllProgramsApp.NAME);
    Map<EntityId, Set<Action>> anotherAppNeededPrivilege = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(appId, EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.artifact(AllProgramsApp.class.getSimpleName(), "1.0-SNAPSHOT"), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset(AllProgramsApp.DATASET_NAME), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset(AllProgramsApp.DATASET_NAME2), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset(AllProgramsApp.DATASET_NAME3), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset(AllProgramsApp.DS_WITH_SCHEMA_NAME), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.datasetType(ObjectMappedTable.class.getName()), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, anotherAppNeededPrivilege);

    Map<EntityId, Set<Action>> bobDatasetPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(AUTH_NAMESPACE.dataset(AllProgramsApp.DATASET_NAME), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset(AllProgramsApp.DATASET_NAME2), EnumSet.of(Action.ADMIN))
      .build();
    Map<EntityId, Set<Action>> bobProgramPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(appId.program(ProgramType.SERVICE, AllProgramsApp.NoOpService.NAME), EnumSet.of(Action.EXECUTE))
      .put(appId.program(ProgramType.WORKER, AllProgramsApp.NoOpWorker.NAME), EnumSet.of(Action.EXECUTE))
      .build();
    setUpPrivilegeAndRegisterForDeletion(BOB, bobDatasetPrivileges);
    setUpPrivilegeAndRegisterForDeletion(BOB, bobProgramPrivileges);

    deployApplication(AUTH_NAMESPACE, AllProgramsApp.class);

    // Switch to BOB since he does not have any privilege
    SecurityRequestContext.setUserId(BOB.getName());
    // deleting all apps should fail because bob does not have admin privileges on the apps and the namespace
    try {
      deleteAllApplications(AUTH_NAMESPACE);
      Assert.fail("Deleting all applications in the namespace should have failed because bob does not have ADMIN " +
                    "privilege on the workflow app.");
    } catch (UnauthorizedException expected) {
      // expected
    }

    ApplicationDetail applicationDetail = getAppDetail(appId);

    Assert.assertEquals(
      bobDatasetPrivileges.keySet(),
      Sets.<EntityId>newHashSet(Iterables.transform(applicationDetail.getDatasets(),
                                                    new Function<DatasetDetail, DatasetId>() {
                                                      @Override
                                                      public DatasetId apply(DatasetDetail input) {
                                                        return appId.getNamespaceId().dataset(input.getName());
                                                      }})));
    Assert.assertEquals(
      bobProgramPrivileges.keySet(),
      Sets.<EntityId>newHashSet(Iterables.transform(applicationDetail.getPrograms(),
                                                    new Function<ProgramRecord, ProgramId>() {
                                                      @Override
                                                      public ProgramId apply(ProgramRecord input) {
                                                        return appId.program(input.getType(), input.getName());
                                                      }})));

    // Switch to ALICE, deletion should be successful since ALICE has ADMIN privileges
    SecurityRequestContext.setUserId(ALICE.getName());
    deleteAllApplications(AUTH_NAMESPACE);
  }

  @Test
  public void testArtifacts() throws Exception {
    String appArtifactName = "app-artifact";
    String appArtifactVersion = "1.1.1";
    try {
      ArtifactId defaultNsArtifact = NamespaceId.DEFAULT.artifact(appArtifactName, appArtifactVersion);
      addAppArtifact(defaultNsArtifact, ConfigTestApp.class);
      Assert.fail("Should not be able to add an app artifact to the default namespace because alice does not have " +
                    "admin privileges on the artifact.");
    } catch (UnauthorizedException expected) {
      // expected
    }
    String pluginArtifactName = "plugin-artifact";
    String pluginArtifactVersion = "1.2.3";
    try {
      ArtifactId defaultNsArtifact = NamespaceId.DEFAULT.artifact(pluginArtifactName, pluginArtifactVersion);
      addAppArtifact(defaultNsArtifact, ToStringPlugin.class);
      Assert.fail("Should not be able to add a plugin artifact to the default namespace because alice does not have " +
                    "admin privileges on the artifact.");
    } catch (UnauthorizedException expected) {
      // expected
    }
    // create a new namespace
    createAuthNamespace();
    ArtifactId appArtifactId = AUTH_NAMESPACE.artifact(appArtifactName, appArtifactVersion);
    grantAndAssertSuccess(appArtifactId, ALICE, EnumSet.of(Action.ADMIN));
    cleanUpEntities.add(appArtifactId);
    ArtifactManager appArtifactManager = addAppArtifact(appArtifactId, ConfigTestApp.class);
    ArtifactId pluginArtifactId = AUTH_NAMESPACE.artifact(pluginArtifactName, pluginArtifactVersion);
    grantAndAssertSuccess(pluginArtifactId, ALICE, EnumSet.of(Action.ADMIN));
    cleanUpEntities.add(pluginArtifactId);
    ArtifactManager pluginArtifactManager = addPluginArtifact(pluginArtifactId, appArtifactId, ToStringPlugin.class);
    // Bob should not be able to delete or write properties to artifacts since he does not have ADMIN permission on
    // the artifacts
    SecurityRequestContext.setUserId(BOB.getName());
    try {
      appArtifactManager.writeProperties(ImmutableMap.of("authorized", "no"));
      Assert.fail("Writing properties to artifact should have failed because Bob does not have admin privileges on " +
                    "the artifact");
    } catch (UnauthorizedException expected) {
      // expected
    }

    try {
      appArtifactManager.delete();
      Assert.fail("Deleting artifact should have failed because Bob does not have admin privileges on the artifact");
    } catch (UnauthorizedException expected) {
      // expected
    }

    try {
      pluginArtifactManager.writeProperties(ImmutableMap.of("authorized", "no"));
      Assert.fail("Writing properties to artifact should have failed because Bob does not have admin privileges on " +
                    "the artifact");
    } catch (UnauthorizedException expected) {
      // expected
    }

    try {
      pluginArtifactManager.removeProperties();
      Assert.fail("Removing properties to artifact should have failed because Bob does not have admin privileges on " +
                    "the artifact");
    } catch (UnauthorizedException expected) {
      // expected
    }

    try {
      pluginArtifactManager.delete();
      Assert.fail("Deleting artifact should have failed because Bob does not have admin privileges on the artifact");
    } catch (UnauthorizedException expected) {
      // expected
    }
    // alice should be permitted to update properties/delete artifact
    SecurityRequestContext.setUserId(ALICE.getName());
    appArtifactManager.writeProperties(ImmutableMap.of("authorized", "yes"));
    appArtifactManager.removeProperties();
    appArtifactManager.delete();
    pluginArtifactManager.delete();
  }

  @Test
  public void testPrograms() throws Exception {
    createAuthNamespace();
    grantAndAssertSuccess(AUTH_NAMESPACE.app(DummyApp.class.getSimpleName()), ALICE, EnumSet.of(Action.ADMIN));
    ApplicationId dummyAppId = AUTH_NAMESPACE.app(DummyApp.class.getSimpleName());
    final ProgramId serviceId = dummyAppId.service(DummyApp.Greeting.SERVICE_NAME);
    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(dummyAppId, EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.artifact(DummyApp.class.getSimpleName(), "1.0-SNAPSHOT"), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset("whom"), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.datasetType(KeyValueTable.class.getName()), EnumSet.of(Action.ADMIN))
      .put(serviceId, EnumSet.of(Action.EXECUTE, Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset("customDataset"), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.datasetType(DummyApp.CustomDummyDataset.class.getName()), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.datasetModule(DummyApp.CustomDummyDataset.class.getName()), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    final ApplicationManager dummyAppManager = deployApplication(AUTH_NAMESPACE, DummyApp.class);

    // alice should be able to start and stop programs in the app she deployed since she has execute privilege
    dummyAppManager.startProgram(Id.Service.fromEntityId(serviceId));
    ServiceManager greetingService = dummyAppManager.getServiceManager(serviceId.getProgram());
    greetingService.waitForRun(ProgramRunStatus.RUNNING, 10, TimeUnit.SECONDS);
    // alice should be able to set instances for the program
    greetingService.setInstances(2);
    Assert.assertEquals(2, greetingService.getProvisionedInstances());
    // alice should also be able to save runtime arguments for all future runs of the program
    Map<String, String> args = ImmutableMap.of("key", "value");
    greetingService.setRuntimeArgs(args);
    // Alice should be able to get runtime arguments as she has ADMIN on it
    Assert.assertEquals(args, greetingService.getRuntimeArgs());
    dummyAppManager.stopProgram(Id.Service.fromEntityId(serviceId));
    greetingService.waitForRun(ProgramRunStatus.KILLED, 10, TimeUnit.SECONDS);
    // Bob should not be able to start programs in dummy app because he does not have privileges on it
    SecurityRequestContext.setUserId(BOB.getName());
    try {
      dummyAppManager.startProgram(Id.Service.fromEntityId(serviceId));
      Assert.fail("Bob should not be able to start the service because he does not have execute privileges on it.");
    } catch (RuntimeException expected) {
      //noinspection ThrowableResultOfMethodCallIgnored
      Assert.assertTrue(Throwables.getRootCause(expected) instanceof UnauthorizedException);
    }
    try {
      dummyAppManager.getInfo();
      Assert.fail("Bob should not be able to read the app info with out privileges");
    } catch (Exception expected) {
      // expected
    }

    // TODO: CDAP-5452 can't verify running programs in this case, because DefaultApplicationManager maintains an
    // in-memory map of running processes that does not use ApplicationLifecycleService to get the runtime status.
    // So no matter if the start/stop call succeeds or fails, it updates its running state in the in-memory map.
    // Also have to switch back to being alice, start the program, and then stop it as Bob because otherwise AppManager
    // doesn't send the request to the app fabric service, but just makes decisions based on an in-memory
    // ConcurrentHashMap.
    // Also add a test for stopping with unauthorized user after the above bug is fixed

    // setting instances should fail because Bob does not have admin privileges on the program
    try {
      greetingService.setInstances(3);
      Assert.fail("Setting instances should have failed because bob does not have admin privileges on the service.");
    } catch (RuntimeException expected) {
      //noinspection ThrowableResultOfMethodCallIgnored
      Assert.assertTrue(Throwables.getRootCause(expected) instanceof UnauthorizedException);
    }
    try {
      greetingService.setRuntimeArgs(args);
      Assert.fail("Setting runtime arguments should have failed because bob does not have admin privileges on the " +
                    "service");
    } catch (UnauthorizedException expected) {
      // expected
    }

    try {
      greetingService.getRuntimeArgs();
      Assert.fail("Getting runtime arguments should have failed because bob does not have one of READ, WRITE, ADMIN " +
                    "privileges on the service");
    } catch (UnauthorizedException expected) {
      // expected
    }

    SecurityRequestContext.setUserId(ALICE.getName());
    dummyAppManager.delete();
  }

  @Test
  public void testCrossNSService() throws Exception {
    createAuthNamespace();
    ApplicationId appId = AUTH_NAMESPACE.app(CrossNsDatasetAccessApp.APP_NAME);
    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(appId, EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.artifact(CrossNsDatasetAccessApp.class.getSimpleName(), "1.0-SNAPSHOT"),
           EnumSet.of(Action.ADMIN))
      .build();

    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    ProgramId programId = appId.service(CrossNsDatasetAccessApp.SERVICE_NAME);
    cleanUpEntities.add(programId);
    // grant bob execute on program and READ/WRITE on stream
    grantAndAssertSuccess(programId, BOB, EnumSet.of(Action.EXECUTE));

    ApplicationManager appManager = deployApplication(AUTH_NAMESPACE, CrossNsDatasetAccessApp.class);

    // switch to to ALICE
    SecurityRequestContext.setUserId(ALICE.getName());

    ServiceManager serviceManager = appManager.getServiceManager(CrossNsDatasetAccessApp.SERVICE_NAME);

    testSystemDatasetAccessFromService(serviceManager);
    testCrossNSDatasetAccessFromService(serviceManager);
  }

  private void testSystemDatasetAccessFromService(ServiceManager serviceManager) throws Exception {
    addDatasetInstance(NamespaceId.SYSTEM.dataset("store"), "keyValueTable");

    // give bob write permission on the dataset
    grantAndAssertSuccess(NamespaceId.SYSTEM.dataset("store"), BOB, EnumSet.of(Action.WRITE));

    // switch to BOB
    SecurityRequestContext.setUserId(BOB.getName());

    Map<String, String> args = ImmutableMap.of(
      CrossNsDatasetAccessApp.OUTPUT_DATASET_NS, NamespaceId.SYSTEM.getNamespace(),
      CrossNsDatasetAccessApp.OUTPUT_DATASET_NAME, "store"
    );

    // Start the Service as BOB
    serviceManager.start(args);

    // Try to write data, it should fail as BOB don't have the permission to get system dataset
    URL url = new URL(serviceManager.getServiceURL(5, TimeUnit.SECONDS), "write/data");
    HttpResponse response = executeHttp(HttpRequest.put(url).build());
    Assert.assertEquals(500, response.getResponseCode());
    Assert.assertTrue(response.getResponseBodyAsString().contains("Cannot access dataset store in system namespace"));

    serviceManager.stop();
    serviceManager.waitForStopped(10, TimeUnit.SECONDS);

    // switch to back to ALICE
    SecurityRequestContext.setUserId(ALICE.getName());

    // cleanup
    deleteDatasetInstance(NamespaceId.SYSTEM.dataset("store"));
  }

  private void testCrossNSDatasetAccessFromService(ServiceManager serviceManager) throws Exception {
    NamespaceMeta outputDatasetNS = new NamespaceMeta.Builder().setName("outputNS").build();
    NamespaceId outputDatasetNSId = outputDatasetNS.getNamespaceId();
    DatasetId datasetId = outputDatasetNSId.dataset("store");
    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(outputDatasetNSId, EnumSet.of(Action.ADMIN))
      .put(datasetId, EnumSet.of(Action.ADMIN, Action.READ))
      .put(outputDatasetNSId.datasetType("keyValueTable"), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges
    );
    getNamespaceAdmin().create(outputDatasetNS);
    addDatasetInstance(datasetId, "keyValueTable");

    // switch to BOB
    SecurityRequestContext.setUserId(BOB.getName());

    Map<String, String> args = ImmutableMap.of(
      CrossNsDatasetAccessApp.OUTPUT_DATASET_NS, outputDatasetNS.getNamespaceId().getNamespace(),
      CrossNsDatasetAccessApp.OUTPUT_DATASET_NAME, "store"
    );

    // Start the service as BOB
    serviceManager.start(args);

    // Call to the service would result in failure due to BOB doesn't have permission on the namespace as set in args
    URL url = new URL(serviceManager.getServiceURL(5, TimeUnit.SECONDS), "write/data");
    HttpResponse response = executeHttp(HttpRequest.put(url).build());
    Assert.assertEquals(500, response.getResponseCode());
    Assert.assertTrue(response.getResponseBodyAsString().contains("'" + BOB + "' is not authorized"));

    serviceManager.stop();
    serviceManager.waitForStopped(10, TimeUnit.SECONDS);
    SecurityRequestContext.setUserId(ALICE.getName());

    assertDatasetIsEmpty(outputDatasetNS.getNamespaceId(), "store");

    // Give BOB permission to write to the dataset in another namespace
    grantAndAssertSuccess(datasetId, BOB, EnumSet.of(Action.WRITE));

    // switch back to BOB to run service again
    SecurityRequestContext.setUserId(BOB.getName());

    // Write data in another namespace should be successful now
    serviceManager.start(args);
    for (int i = 0; i < 10; i++) {
      url = new URL(serviceManager.getServiceURL(5, TimeUnit.SECONDS), "write/" + i);
      response = executeHttp(HttpRequest.put(url).build());
      Assert.assertEquals(200, response.getResponseCode());
    }

    serviceManager.stop();
    serviceManager.waitForStopped(10, TimeUnit.SECONDS);

    // switch back to alice and verify the data its fine now to verify.
    SecurityRequestContext.setUserId(ALICE.getName());

    DataSetManager<KeyValueTable> dataSetManager = getDataset(outputDatasetNS.getNamespaceId().dataset("store"));
    KeyValueTable results = dataSetManager.get();

    for (int i = 0; i < 10; i++) {
      byte[] key = String.valueOf(i).getBytes(Charsets.UTF_8);
      Assert.assertArrayEquals(key, results.read(key));
    }

    getNamespaceAdmin().delete(outputDatasetNS.getNamespaceId());
  }

  @Test
  public void testCrossNSMapReduce() throws Exception {
    createAuthNamespace();
    ApplicationId appId = AUTH_NAMESPACE.app(DatasetCrossNSAccessWithMAPApp.class.getSimpleName());

    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(appId, EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.artifact(DatasetCrossNSAccessWithMAPApp.class.getSimpleName(), "1.0-SNAPSHOT"),
           EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    ProgramId programId = appId.program(ProgramType.MAPREDUCE, DatasetCrossNSAccessWithMAPApp.MAPREDUCE_PROGRAM);
    // bob will be executing the program
    grantAndAssertSuccess(programId, BOB, EnumSet.of(Action.EXECUTE));
    cleanUpEntities.add(programId);

    ApplicationManager appManager = deployApplication(AUTH_NAMESPACE, DatasetCrossNSAccessWithMAPApp.class);

    MapReduceManager mrManager = appManager.getMapReduceManager(DatasetCrossNSAccessWithMAPApp.MAPREDUCE_PROGRAM);

    testCrossNSSystemDatasetAccessWithAuthMapReduce(mrManager);
    testCrossNSDatasetAccessWithAuthMapReduce(mrManager);
  }

  private void testCrossNSSystemDatasetAccessWithAuthMapReduce(MapReduceManager mrManager) throws Exception {
    addDatasetInstance(NamespaceId.SYSTEM.dataset("table1"), "keyValueTable").create();
    addDatasetInstance(NamespaceId.SYSTEM.dataset("table2"), "keyValueTable").create();
    NamespaceMeta otherNS = new NamespaceMeta.Builder().setName("otherNS").build();
    NamespaceId otherNsId = otherNS.getNamespaceId();
    DatasetId datasetId = otherNsId.dataset("otherTable");
    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(otherNsId, EnumSet.of(Action.ADMIN))
      .put(datasetId, EnumSet.of(Action.ADMIN))
      .put(otherNsId.datasetType("keyValueTable"), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    getNamespaceAdmin().create(otherNS);
    addDatasetInstance(datasetId, "keyValueTable").create();
    addDummyData(NamespaceId.SYSTEM, "table1");

    // first test that reading system namespace fails with valid table as output
    Map<String, String> argsForMR = ImmutableMap.of(
      DatasetCrossNSAccessWithMAPApp.INPUT_DATASET_NS, NamespaceId.SYSTEM.getNamespace(),
      DatasetCrossNSAccessWithMAPApp.INPUT_DATASET_NAME, "table1",
      DatasetCrossNSAccessWithMAPApp.OUTPUT_DATASET_NS, otherNS.getNamespaceId().getNamespace(),
      DatasetCrossNSAccessWithMAPApp.OUTPUT_DATASET_NAME, "otherTable");

    // give privilege to BOB on all the datasets
    grantAndAssertSuccess(NamespaceId.SYSTEM.dataset("table1"), BOB, EnumSet.of(Action.READ));
    grantAndAssertSuccess(NamespaceId.SYSTEM.dataset("table2"), BOB, EnumSet.of(Action.WRITE));
    grantAndAssertSuccess(otherNS.getNamespaceId().dataset("otherTable"), BOB, ALL_ACTIONS);

    // Switch to BOB and run the  mapreduce job. The job will fail at the runtime since BOB is trying to read from
    // system namespace
    SecurityRequestContext.setUserId(BOB.getName());
    assertProgramFailure(argsForMR, mrManager);
    assertDatasetIsEmpty(otherNS.getNamespaceId(), "otherTable");

    // now try reading a table from valid namespace and writing to system namespace
    argsForMR = ImmutableMap.of(
      DatasetCrossNSAccessWithMAPApp.INPUT_DATASET_NS, otherNS.getName(),
      DatasetCrossNSAccessWithMAPApp.INPUT_DATASET_NAME, "otherTable",
      DatasetCrossNSAccessWithMAPApp.OUTPUT_DATASET_NS, NamespaceId.SYSTEM.getNamespace(),
      DatasetCrossNSAccessWithMAPApp.OUTPUT_DATASET_NAME, "table2");

    addDummyData(otherNS.getNamespaceId(), "otherTable");

    // verify that the program fails
    assertProgramFailure(argsForMR, mrManager);
    assertDatasetIsEmpty(NamespaceId.SYSTEM, "table2");

    // switch to back to ALICE
    SecurityRequestContext.setUserId(ALICE.getName());

    // cleanup
    deleteDatasetInstance(NamespaceId.SYSTEM.dataset("table1"));
    deleteDatasetInstance(NamespaceId.SYSTEM.dataset("table2"));
    getNamespaceAdmin().delete(otherNS.getNamespaceId());
  }

  private void testCrossNSDatasetAccessWithAuthMapReduce(MapReduceManager mrManager) throws Exception {
    NamespaceMeta inputDatasetNS = new NamespaceMeta.Builder().setName("inputNS").build();
    NamespaceId inputDatasetNSId = inputDatasetNS.getNamespaceId();
    NamespaceMeta outputDatasetNS = new NamespaceMeta.Builder().setName("outputNS").build();
    NamespaceId outputDatasetNSId = outputDatasetNS.getNamespaceId();
    DatasetId table1Id = inputDatasetNSId.dataset("table1");
    DatasetId table2Id = outputDatasetNSId.dataset("table2");

    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(inputDatasetNSId, EnumSet.of(Action.ADMIN))
      .put(outputDatasetNSId, EnumSet.of(Action.ADMIN))
      // We need to write some data into table1
      .put(table1Id, EnumSet.of(Action.ADMIN, Action.WRITE))
      // Need to read data from table2
      .put(table2Id, EnumSet.of(Action.ADMIN, Action.READ))
      .put(inputDatasetNSId.datasetType("keyValueTable"), EnumSet.of(Action.ADMIN))
      .put(outputDatasetNSId.datasetType("keyValueTable"), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    getNamespaceAdmin().create(inputDatasetNS);
    getNamespaceAdmin().create(outputDatasetNS);
    addDatasetInstance(table1Id, "keyValueTable").create();
    addDatasetInstance(table2Id, "keyValueTable").create();

    addDummyData(inputDatasetNSId, "table1");

    Map<String, String> argsForMR = ImmutableMap.of(
      DatasetCrossNSAccessWithMAPApp.INPUT_DATASET_NS, inputDatasetNS.getNamespaceId().getNamespace(),
      DatasetCrossNSAccessWithMAPApp.INPUT_DATASET_NAME, "table1",
      DatasetCrossNSAccessWithMAPApp.OUTPUT_DATASET_NS, outputDatasetNS.getNamespaceId().getNamespace(),
      DatasetCrossNSAccessWithMAPApp.OUTPUT_DATASET_NAME, "table2");

    // Switch to BOB and run the  mapreduce job. The job will fail at the runtime since BOB does not have permission
    // on the input and output datasets in another namespaces.
    SecurityRequestContext.setUserId(BOB.getName());
    assertProgramFailure(argsForMR, mrManager);

    // Switch back to Alice
    SecurityRequestContext.setUserId(ALICE.getName());
    // Verify nothing write to the output dataset
    assertDatasetIsEmpty(outputDatasetNS.getNamespaceId(), "table2");

    // give privilege to BOB on the input dataset
    grantAndAssertSuccess(inputDatasetNS.getNamespaceId().dataset("table1"), BOB, EnumSet.of(Action.READ));

    // switch back to bob and try running again. this will still fail since bob does not have access on the output
    // dataset
    SecurityRequestContext.setUserId(BOB.getName());
    assertProgramFailure(argsForMR, mrManager);

    // Switch back to Alice
    SecurityRequestContext.setUserId(ALICE.getName());
    // Verify nothing write to the output dataset
    assertDatasetIsEmpty(outputDatasetNS.getNamespaceId(), "table2");

    // give privilege to BOB on the output dataset
    grantAndAssertSuccess(outputDatasetNS.getNamespaceId().dataset("table2"), BOB, EnumSet.of(Action.WRITE));

    // switch back to BOB and run MR again. this should work
    SecurityRequestContext.setUserId(BOB.getName());
    mrManager.start(argsForMR);
    mrManager.waitForRun(ProgramRunStatus.COMPLETED, 60, TimeUnit.SECONDS);

    // Verify results as alice
    SecurityRequestContext.setUserId(ALICE.getName());
    verifyDummyData(outputDatasetNS.getNamespaceId(), "table2");
    getNamespaceAdmin().delete(inputDatasetNS.getNamespaceId());
    getNamespaceAdmin().delete(outputDatasetNS.getNamespaceId());
  }

  @Test
  public void testCrossNSSpark() throws Exception {
    createAuthNamespace();
    ApplicationId appId = AUTH_NAMESPACE.app(TestSparkCrossNSDatasetApp.APP_NAME);

    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(appId, EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.artifact(TestSparkCrossNSDatasetApp.class.getSimpleName(), "1.0-SNAPSHOT"),
           EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset(TestSparkCrossNSDatasetApp.DEFAULT_OUTPUT_DATASET), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.datasetType(KeyValueTable.class.getName()), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    ProgramId programId = appId.spark(TestSparkCrossNSDatasetApp.SPARK_PROGRAM_NAME);
    // bob will be executing the program
    grantAndAssertSuccess(programId, BOB, EnumSet.of(Action.EXECUTE));
    cleanUpEntities.add(programId);

    ApplicationManager appManager = deployApplication(AUTH_NAMESPACE, TestSparkCrossNSDatasetApp.class);
    SparkManager sparkManager = appManager.getSparkManager(TestSparkCrossNSDatasetApp.SparkCrossNSDatasetProgram
                                                             .class.getSimpleName());

    testCrossNSSystemDatasetAccessWithAuthSpark(sparkManager);
    testCrossNSDatasetAccessWithAuthSpark(sparkManager);
  }

  @Test
  public void testScheduleAuth() throws Exception {
    createAuthNamespace();
    ApplicationId appId = AUTH_NAMESPACE.app(AppWithSchedule.class.getSimpleName());
    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(appId, EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.artifact(AppWithSchedule.class.getSimpleName(), "1.0-SNAPSHOT"), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset(AppWithSchedule.INPUT_NAME), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.dataset(AppWithSchedule.OUTPUT_NAME), EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.datasetType(ObjectStore.class.getName()), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    ApplicationManager appManager = deployApplication(AUTH_NAMESPACE, AppWithSchedule.class);
    String workflowName = AppWithSchedule.SampleWorkflow.class.getSimpleName();
    ProgramId workflowID = new ProgramId(AUTH_NAMESPACE.getNamespace(), AppWithSchedule.class.getSimpleName(),
                                         ProgramType.WORKFLOW, workflowName);
    cleanUpEntities.add(workflowID);

    final WorkflowManager workflowManager =
      appManager.getWorkflowManager(workflowName);
    ScheduleManager scheduleManager = workflowManager.getSchedule(AppWithSchedule.EVERY_HOUR_SCHEDULE);

    // switch to BOB
    SecurityRequestContext.setUserId(BOB.getName());
    // try to resume schedule as BOB. It should fail since BOB does not have execute privileges on the programs
    try {
      scheduleManager.resume();
      Assert.fail("Resuming schedule should have failed since BOB does not have EXECUTE on the program");
    } catch (UnauthorizedException e) {
      // Expected
    }

    // bob should also not be able see the status of the schedule
    try {
      scheduleManager.status(HttpURLConnection.HTTP_FORBIDDEN);
      Assert.fail("Getting schedule status should have failed since BOB does not have any privilege on the program");
    } catch (UnauthorizedException e) {
      // Expected
    }

    // give BOB READ permission in the workflow
    grantAndAssertSuccess(workflowID, BOB, EnumSet.of(Action.READ));

    // switch to BOB
    SecurityRequestContext.setUserId(BOB.getName());
    // try to resume schedule as BOB. It should fail since BOB has READ but not EXECUTE on the workflow
    try {
      scheduleManager.resume();
      Assert.fail("Resuming schedule should have failed since BOB does not have EXECUTE on the program");
    } catch (UnauthorizedException e) {
      // Expected
    }

    // but BOB should be able to get schedule status now
    Assert.assertEquals(ProgramScheduleStatus.SUSPENDED.name(), scheduleManager.status(HttpURLConnection.HTTP_OK));

    // give BOB EXECUTE permission in the workflow
    grantAndAssertSuccess(workflowID, BOB, EnumSet.of(Action.EXECUTE));

    // switch to BOB
    SecurityRequestContext.setUserId(BOB.getName());
    // try to resume the schedule. This should pass and workflow should run
    scheduleManager.resume();
    Assert.assertEquals(ProgramScheduleStatus.SCHEDULED.name(), scheduleManager.status(HttpURLConnection.HTTP_OK));
    
    // suspend the schedule so that it does not start running again
    scheduleManager.suspend();
    Assert.assertEquals(ProgramScheduleStatus.SUSPENDED.name(), scheduleManager.status(HttpURLConnection.HTTP_OK));

    ScheduleId scheduleId = new ScheduleId(appId.getNamespace(), appId.getApplication(), appId.getVersion(),
                                           "testSchedule");
    ScheduleDetail scheduleDetail =
      new ScheduleDetail(AUTH_NAMESPACE.getNamespace(), AppWithSchedule.class.getSimpleName(), "1.0-SNAPSHOT",
                         "testSchedule", "Something 2",
                         new ScheduleProgramInfo(SchedulableProgramType.WORKFLOW, workflowName),
                         Collections.<String, String>emptyMap(), new TimeTrigger("*/1 * * * *"),
                         Collections.<Constraint>emptyList(), TimeUnit.HOURS.toMillis(6), null, null);

    try {
      addSchedule(scheduleId, scheduleDetail);
      Assert.fail("Adding schedule should fail since BOB does not have AMDIN on the app");
    } catch (UnauthorizedException e) {
      // expected
    }

    // grant BOB ADMIN on the app
    grantAndAssertSuccess(appId, BOB, EnumSet.of(Action.ADMIN));

    // add schedule should succeed
    addSchedule(scheduleId, scheduleDetail);
    Assert.assertEquals(ProgramScheduleStatus.SUSPENDED.name(),
                        workflowManager.getSchedule(scheduleId.getSchedule()).status(HttpURLConnection.HTTP_OK));

    // update schedule should succeed
    updateSchedule(scheduleId, scheduleDetail);
    Assert.assertEquals(ProgramScheduleStatus.SUSPENDED.name(),
                        workflowManager.getSchedule(scheduleId.getSchedule()).status(HttpURLConnection.HTTP_OK));

    // revoke ADMIN from BOB
    getAuthorizer().revoke(Authorizable.fromEntityId(appId), BOB, EnumSet.of(Action.ADMIN));

    try {
      // delete schedule should fail since we revoke the ADMIN privilege from BOB
      deleteSchedule(scheduleId);
      Assert.fail("Deleting schedule should fail since BOB does not have AMDIN on the app");
    } catch (UnauthorizedException e) {
      // expected
    }

    try {
      updateSchedule(scheduleId, scheduleDetail);
      Assert.fail("Updating schedule should fail since BOB does not have AMDIN on the app");
    } catch (UnauthorizedException e) {
      // expected
    }

    // grant BOB ADMIN on the app again
    grantAndAssertSuccess(appId, BOB, EnumSet.of(Action.ADMIN));
    deleteSchedule(scheduleId);
    workflowManager.getSchedule(scheduleId.getSchedule()).status(HttpURLConnection.HTTP_NOT_FOUND);

    // switch to Alice
    SecurityRequestContext.setUserId(ALICE.getName());
  }

  private void testCrossNSSystemDatasetAccessWithAuthSpark(SparkManager sparkManager) throws Exception {
    addDatasetInstance(NamespaceId.SYSTEM.dataset("table1"), "keyValueTable").create();
    addDatasetInstance(NamespaceId.SYSTEM.dataset("table2"), "keyValueTable").create();
    NamespaceMeta otherNS = new NamespaceMeta.Builder().setName("otherNS").build();
    NamespaceId otherNSId = otherNS.getNamespaceId();
    DatasetId otherTableId = otherNSId.dataset("otherTable");

    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(otherNSId, EnumSet.of(Action.ADMIN))
      .put(otherTableId, EnumSet.of(Action.ADMIN))
      .put(otherNSId.datasetType("keyValueTable"), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    getNamespaceAdmin().create(otherNS);
    addDatasetInstance(otherTableId, "keyValueTable").create();
    addDummyData(NamespaceId.SYSTEM, "table1");

    // give privilege to BOB on all the datasets
    grantAndAssertSuccess(NamespaceId.SYSTEM.dataset("table1"), BOB, EnumSet.of(Action.READ));
    grantAndAssertSuccess(NamespaceId.SYSTEM.dataset("table2"), BOB, EnumSet.of(Action.WRITE));
    grantAndAssertSuccess(otherNS.getNamespaceId().dataset("otherTable"), BOB, ALL_ACTIONS);

    // Switch to Bob and run the spark program. this will fail because bob is trying to read from a system dataset
    SecurityRequestContext.setUserId(BOB.getName());
    Map<String, String> args = ImmutableMap.of(
      TestSparkCrossNSDatasetApp.INPUT_DATASET_NAMESPACE,
      NamespaceId.SYSTEM.getNamespace(),
      TestSparkCrossNSDatasetApp.INPUT_DATASET_NAME, "table1",
      TestSparkCrossNSDatasetApp.OUTPUT_DATASET_NAMESPACE,
      otherNS.getNamespaceId().getNamespace(),
      TestSparkCrossNSDatasetApp.OUTPUT_DATASET_NAME, "otherTable"
    );

    assertProgramFailure(args, sparkManager);
    assertDatasetIsEmpty(otherNS.getNamespaceId(), "otherTable");

    // try running spark job with valid input namespace but writing to system namespace this should fail too
    args = ImmutableMap.of(
      TestSparkCrossNSDatasetApp.INPUT_DATASET_NAMESPACE,
      otherNS.getNamespaceId().getNamespace(),
      TestSparkCrossNSDatasetApp.INPUT_DATASET_NAME, "otherTable",
      TestSparkCrossNSDatasetApp.OUTPUT_DATASET_NAMESPACE,
      NamespaceId.SYSTEM.getNamespace(),
      TestSparkCrossNSDatasetApp.OUTPUT_DATASET_NAME, "table2"
    );

    addDummyData(otherNS.getNamespaceId(), "otherTable");

    assertProgramFailure(args, sparkManager);
    assertDatasetIsEmpty(NamespaceId.SYSTEM, "table2");

    // switch to back to ALICE
    SecurityRequestContext.setUserId(ALICE.getName());

    // cleanup
    deleteDatasetInstance(NamespaceId.SYSTEM.dataset("table1"));
    deleteDatasetInstance(NamespaceId.SYSTEM.dataset("table2"));
    getNamespaceAdmin().delete(otherNS.getNamespaceId());
  }

  private void testCrossNSDatasetAccessWithAuthSpark(SparkManager sparkManager) throws Exception {
    NamespaceMeta inputDatasetNSMeta = new NamespaceMeta.Builder().setName("inputDatasetNS").build();
    NamespaceMeta outputDatasetNSMeta = new NamespaceMeta.Builder().setName("outputDatasetNS").build();
    NamespaceId inputDatasetNSMetaId = inputDatasetNSMeta.getNamespaceId();
    DatasetId inputTableId = inputDatasetNSMetaId.dataset("input");
    NamespaceId outputDatasetNSMetaId = outputDatasetNSMeta.getNamespaceId();
    DatasetId outputTableId = outputDatasetNSMetaId.dataset("output");

    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(inputDatasetNSMetaId, EnumSet.of(Action.ADMIN))
      .put(outputDatasetNSMetaId, EnumSet.of(Action.ADMIN))
      .put(inputTableId, EnumSet.of(Action.ADMIN, Action.WRITE))
      .put(inputDatasetNSMetaId.datasetType("keyValueTable"), EnumSet.of(Action.ADMIN))
      .put(outputTableId, EnumSet.of(Action.ADMIN, Action.READ))
      .put(outputDatasetNSMetaId.datasetType("keyValueTable"), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    getNamespaceAdmin().create(inputDatasetNSMeta);
    getNamespaceAdmin().create(outputDatasetNSMeta);
    addDatasetInstance(inputTableId, "keyValueTable").create();
    addDatasetInstance(outputTableId, "keyValueTable").create();
    // write sample stuff in input dataset
    addDummyData(inputDatasetNSMeta.getNamespaceId(), "input");

    // Switch to Bob and run the spark program. this will fail because bob does not have access to either input or
    // output dataset
    SecurityRequestContext.setUserId(BOB.getName());
    Map<String, String> args = ImmutableMap.of(
      TestSparkCrossNSDatasetApp.INPUT_DATASET_NAMESPACE,
      inputDatasetNSMeta.getNamespaceId().getNamespace(),
      TestSparkCrossNSDatasetApp.INPUT_DATASET_NAME, "input",
      TestSparkCrossNSDatasetApp.OUTPUT_DATASET_NAMESPACE,
      outputDatasetNSMeta.getNamespaceId().getNamespace(),
      TestSparkCrossNSDatasetApp.OUTPUT_DATASET_NAME, "output"
    );

    assertProgramFailure(args, sparkManager);

    SecurityRequestContext.setUserId(ALICE.getName());
    // Verify nothing write to the output dataset
    assertDatasetIsEmpty(outputDatasetNSMeta.getNamespaceId(), "output");

    // give privilege to BOB on the input dataset
    grantAndAssertSuccess(inputDatasetNSMeta.getNamespaceId().dataset("input"), BOB, EnumSet.of(Action.READ));

    // switch back to bob and try running again. this will still fail since bob does not have access on the output
    // dataset
    SecurityRequestContext.setUserId(BOB.getName());
    assertProgramFailure(args, sparkManager);

    // Switch back to Alice
    SecurityRequestContext.setUserId(ALICE.getName());
    // Verify nothing write to the output dataset
    assertDatasetIsEmpty(outputDatasetNSMeta.getNamespaceId(), "output");

    // give privilege to BOB on the output dataset
    grantAndAssertSuccess(outputDatasetNSMeta.getNamespaceId().dataset("output"), BOB, EnumSet.of(Action.WRITE));

    // switch back to BOB and run spark again. this should work
    SecurityRequestContext.setUserId(BOB.getName());
    sparkManager.start(args);
    sparkManager.waitForRun(ProgramRunStatus.COMPLETED, 120, TimeUnit.SECONDS);

    waitForStoppedPrograms(sparkManager);

    // Verify the results as alice
    SecurityRequestContext.setUserId(ALICE.getName());
    verifyDummyData(outputDatasetNSMeta.getNamespaceId(), "output");
    getNamespaceAdmin().delete(inputDatasetNSMeta.getNamespaceId());
    getNamespaceAdmin().delete(outputDatasetNSMeta.getNamespaceId());
  }

  @Test
  public void testAddDropPartitions() throws Exception {
    createAuthNamespace();
    ApplicationId appId = AUTH_NAMESPACE.app(PartitionTestApp.class.getSimpleName());
    DatasetId datasetId = AUTH_NAMESPACE.dataset(PartitionTestApp.PFS_NAME);

    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(appId, EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.artifact(PartitionTestApp.class.getSimpleName(), "1.0-SNAPSHOT"), EnumSet.of(Action.ADMIN))
      .put(datasetId, EnumSet.of(Action.ADMIN))
      .put(AUTH_NAMESPACE.datasetType(PartitionedFileSet.class.getName()), EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    ProgramId programId = appId.program(ProgramType.SERVICE, PartitionTestApp.PFS_SERVICE_NAME);
    grantAndAssertSuccess(programId, BOB, EnumSet.of(Action.EXECUTE));
    cleanUpEntities.add(programId);
    grantAndAssertSuccess(datasetId, BOB, EnumSet.of(Action.READ));
    cleanUpEntities.add(datasetId);

    ApplicationManager appMgr = deployApplication(AUTH_NAMESPACE, PartitionTestApp.class);
    SecurityRequestContext.setUserId(BOB.getName());
    String partition = "p1";
    String subPartition = "1";
    String text = "some random text for pfs";
    ServiceManager pfsService = appMgr.getServiceManager(PartitionTestApp.PFS_SERVICE_NAME);

    pfsService.start();
    pfsService.waitForRun(ProgramRunStatus.RUNNING, 1, TimeUnit.MINUTES);
    URL pfsURL = pfsService.getServiceURL();
    String apiPath = String.format("partitions/%s/subpartitions/%s", partition, subPartition);
    URL url = new URL(pfsURL, apiPath);
    HttpRequest request;
    HttpResponse response;
    try {
      request = HttpRequest.post(url).withBody(text).build();
      response = executeHttp(request);
      // should fail because bob does not have write privileges on the dataset
      Assert.assertEquals(500, response.getResponseCode());
    } finally {
      pfsService.stop();
      pfsService.waitForRun(ProgramRunStatus.KILLED, 1, TimeUnit.MINUTES);
    }
    // grant read and write on dataset and restart
    grantAndAssertSuccess(datasetId, BOB, EnumSet.of(Action.WRITE, Action.READ));
    pfsService.start();
    pfsService.waitForRun(ProgramRunStatus.RUNNING, 1, TimeUnit.MINUTES);
    pfsURL = pfsService.getServiceURL();
    url = new URL(pfsURL, apiPath);
    try  {
      request = HttpRequest.post(url).withBody(text).build();
      response = executeHttp(request);
      // should succeed now because bob was granted write privileges on the dataset
      Assert.assertEquals(200, response.getResponseCode());
      // make sure that the partition was added
      request = HttpRequest.get(url).build();
      response = executeHttp(request);
      Assert.assertEquals(200, response.getResponseCode());
      Assert.assertEquals(text, response.getResponseBodyAsString());
      // drop the partition
      request = HttpRequest.delete(url).build();
      response = executeHttp(request);
      Assert.assertEquals(200, response.getResponseCode());
    } finally {
      pfsService.stop();
      pfsService.waitForRuns(ProgramRunStatus.KILLED, 2, 1, TimeUnit.MINUTES);
      SecurityRequestContext.setUserId(ALICE.getName());
    }
  }

  /**
   * This test is to make sure we do not bypass the authorization check for datasets in system namespace
   */
  @Test
  public void testDeleteSystemDatasets() throws Exception {
    // create a random user and try to delete a system dataset
    UserGroupInformation remoteUser = UserGroupInformation.createRemoteUser("random");
    remoteUser.doAs(new PrivilegedAction<Void>() {
      @Override
      public Void run() {
        try {
          deleteDatasetInstance(NamespaceId.SYSTEM.dataset("app.meta"));
          Assert.fail();
        } catch (UnauthorizedException e) {
          // Expected
        } catch (Exception e) {
          Assert.fail("Getting incorrect exception");
        }
        return null;
      }
    });
  }

  /**
   * Note that the impersonation is not actually happening since we do not have keytab files for unit test,
   * all impersonation doAs will be no-op, but we can still simulate the namespace deploy and app creation in the test
   */
  @Test
  public void testCreationWithOwner() throws Exception {
    // this test will test deploy app without app owner specified. Like namespace impersonation
    testDeployAppWithoutOwner();
    // this test will test deploy app with app owner specified. Like app impersonation
    testDeployAppWithOwner();
  }

  private void testDeployAppWithoutOwner() throws Exception {
    NamespaceId namespaceId = new NamespaceId("namespaceImpersonation");
    // We will create a namespace as owner bob, the keytab url is provided to pass the check for DefaultNamespaceAdmin
    // in unit test, it is useless, since impersonation will never happen
    NamespaceMeta ownerNSMeta = new NamespaceMeta.Builder().setName(namespaceId.getNamespace())
      .setPrincipal(BOB.getName()).setKeytabURI("/tmp/").build();
    KerberosPrincipalId bobPrincipalId = new KerberosPrincipalId(BOB.getName());

    // grant alice admin to the namespace, but creation should still fail since alice needs to have privilege on
    // principal bob
    grantAndAssertSuccess(namespaceId, ALICE, EnumSet.of(Action.ADMIN));
    cleanUpEntities.add(namespaceId);
    try {
      getNamespaceAdmin().create(ownerNSMeta);
      Assert.fail("Namespace creation should fail since alice does not have privilege on principal bob");
    } catch (UnauthorizedException e) {
      // expected
    }

    // grant alice admin on principal bob, now creation of namespace should work
    grantAndAssertSuccess(bobPrincipalId, ALICE, EnumSet.of(Action.ADMIN));
    cleanUpEntities.add(bobPrincipalId);
    getNamespaceAdmin().create(ownerNSMeta);

    // deploy dummy app with ns impersonation
    deployDummyAppWithImpersonation(ownerNSMeta, null);
  }

  private void testDeployAppWithOwner() throws Exception {
    NamespaceId namespaceId = new NamespaceId("appImpersonation");
    NamespaceMeta nsMeta = new NamespaceMeta.Builder().setName(namespaceId.getNamespace()).build();
    // grant ALICE admin on namespace and create namespace
    grantAndAssertSuccess(namespaceId, ALICE, EnumSet.of(Action.ADMIN));
    cleanUpEntities.add(namespaceId);
    getNamespaceAdmin().create(nsMeta);

    // deploy dummy app with app impersonation
    deployDummyAppWithImpersonation(nsMeta, BOB.getName());
  }

  private void deployDummyAppWithImpersonation(NamespaceMeta nsMeta, @Nullable String appOwner) throws Exception {
    NamespaceId namespaceId = nsMeta.getNamespaceId();
    ApplicationId dummyAppId = namespaceId.app(DummyApp.class.getSimpleName());
    ArtifactId artifactId = namespaceId.artifact(DummyApp.class.getSimpleName(), "1.0-SNAPSHOT");
    DatasetId datasetId = namespaceId.dataset("whom");
    DatasetTypeId datasetTypeId = namespaceId.datasetType(KeyValueTable.class.getName());
    String owner = appOwner != null ? appOwner : nsMeta.getConfig().getPrincipal();
    KerberosPrincipalId principalId = new KerberosPrincipalId(owner);
    Principal principal = new Principal(owner, Principal.PrincipalType.USER);
    DatasetId dummyDatasetId = namespaceId.dataset("customDataset");
    DatasetTypeId dummyTypeId = namespaceId.datasetType(DummyApp.CustomDummyDataset.class.getName());
    DatasetModuleId dummyModuleId = namespaceId.datasetModule((DummyApp.CustomDummyDataset.class.getName()));
    // these are the privileges that are needed to deploy the app if no impersonation is involved,
    // can check testApps() for more info
    Map<EntityId, Set<Action>> neededPrivileges = ImmutableMap.<EntityId, Set<Action>>builder()
      .put(dummyAppId, EnumSet.of(Action.ADMIN))
      .put(artifactId, EnumSet.of(Action.ADMIN))
      .put(datasetId, EnumSet.of(Action.ADMIN))
      .put(datasetTypeId, EnumSet.of(Action.ADMIN))
      .put(principalId, EnumSet.of(Action.ADMIN))
      .put(dummyDatasetId, EnumSet.of(Action.ADMIN))
      .put(dummyTypeId, EnumSet.of(Action.ADMIN))
      .put(dummyModuleId, EnumSet.of(Action.ADMIN))
      .build();
    setUpPrivilegeAndRegisterForDeletion(ALICE, neededPrivileges);

    // add the artifact
    addAppArtifact(artifactId, DummyApp.class);
    AppRequest<? extends Config> appRequest =
      new AppRequest<>(new ArtifactSummary(artifactId.getArtifact(), artifactId.getVersion()), null, appOwner);

    try {
      deployApplication(dummyAppId, appRequest);
      Assert.fail();
    } catch (Exception e) {
      // expected
    }

    // revoke privileges on datasets from alice, she does not need these privileges to deploy the app
    // the owner will need these privileges to deploy
    revokeAndAssertSuccess(datasetId);
    revokeAndAssertSuccess(datasetTypeId);
    revokeAndAssertSuccess(dummyDatasetId);
    revokeAndAssertSuccess(dummyTypeId);
    revokeAndAssertSuccess(dummyModuleId);

    // grant ADMIN privileges to owner
    grantAndAssertSuccess(datasetId, principal, EnumSet.of(Action.ADMIN));
    grantAndAssertSuccess(datasetTypeId, principal, EnumSet.of(Action.ADMIN));
    grantAndAssertSuccess(dummyDatasetId, principal, EnumSet.of(Action.ADMIN));
    grantAndAssertSuccess(dummyTypeId, principal, EnumSet.of(Action.ADMIN));
    grantAndAssertSuccess(dummyModuleId, principal, EnumSet.of(Action.ADMIN));

    // this time it should be successful
    deployApplication(dummyAppId, appRequest);
    // clean up the privilege on the owner principal id
    revokeAndAssertSuccess(principalId);
  }

  @After
  @Override
  public void afterTest() throws Exception {
    Authorizer authorizer = getAuthorizer();

    SecurityRequestContext.setUserId(ALICE.getName());
    grantAndAssertSuccess(AUTH_NAMESPACE, SecurityRequestContext.toPrincipal(), EnumSet.of(Action.ADMIN));
    // clean up. remove the namespace if it exists
    if (getNamespaceAdmin().exists(AUTH_NAMESPACE)) {
      getNamespaceAdmin().delete(AUTH_NAMESPACE);
      Assert.assertFalse(getNamespaceAdmin().exists(AUTH_NAMESPACE));
    }
    revokeAndAssertSuccess(AUTH_NAMESPACE);
    for (EntityId entityId : cleanUpEntities) {
      revokeAndAssertSuccess(entityId);
    }
    Assert.assertEquals(Collections.emptySet(), authorizer.listPrivileges(ALICE));
  }

  @AfterClass
  public static void cleanup() throws Exception {
    // we want to execute TestBase's @AfterClass after unsetting userid, because the old userid has been granted ADMIN
    // on default namespace in TestBase so it can clean the namespace.
    SecurityRequestContext.setUserId(oldUser);
    finish();
  }

  private void createAuthNamespace() throws Exception {
    Authorizer authorizer = getAuthorizer();
    grantAndAssertSuccess(AUTH_NAMESPACE, ALICE, ImmutableSet.of(Action.ADMIN));
    getNamespaceAdmin().create(AUTH_NAMESPACE_META);
    Assert.assertEquals(ImmutableSet.of(new Privilege(AUTH_NAMESPACE, Action.ADMIN)), authorizer.listPrivileges(ALICE));
  }

  private void grantAndAssertSuccess(EntityId entityId, Principal principal, Set<Action> actions) throws Exception {
    Authorizer authorizer = getAuthorizer();
    Set<Privilege> existingPrivileges = authorizer.listPrivileges(principal);
    authorizer.grant(Authorizable.fromEntityId(entityId), principal, actions);
    ImmutableSet.Builder<Privilege> expectedPrivilegesAfterGrant = ImmutableSet.builder();
    for (Action action : actions) {
      expectedPrivilegesAfterGrant.add(new Privilege(entityId, action));
    }
    Assert.assertEquals(Sets.union(existingPrivileges, expectedPrivilegesAfterGrant.build()),
                        authorizer.listPrivileges(principal));
  }

  private void revokeAndAssertSuccess(final EntityId entityId) throws Exception {
    Authorizer authorizer = getAuthorizer();
    authorizer.revoke(Authorizable.fromEntityId(entityId));
    assertNoAccess(entityId);
  }

  private void assertNoAccess(Principal principal, final EntityId entityId) throws Exception {
    Authorizer authorizer = getAuthorizer();
    Predicate<Privilege> entityFilter = new Predicate<Privilege>() {
      @Override
      public boolean apply(Privilege input) {
        return Authorizable.fromEntityId(entityId).equals(input.getAuthorizable());
      }
    };
    Assert.assertTrue(Sets.filter(authorizer.listPrivileges(principal), entityFilter).isEmpty());
  }
  private void assertNoAccess(final EntityId entityId) throws Exception {
    assertNoAccess(ALICE, entityId);
    assertNoAccess(BOB, entityId);
  }

  private void assertDatasetIsEmpty(NamespaceId namespaceId, String datasetName) throws Exception {
    DataSetManager<KeyValueTable> outTableManager = getDataset(namespaceId.dataset(datasetName));
    KeyValueTable outputTable = outTableManager.get();
    try (CloseableIterator<KeyValue<byte[], byte[]>> scanner = outputTable.scan(null, null)) {
      Assert.assertFalse(scanner.hasNext());
    }
  }

  private <T extends ProgramManager> void assertProgramFailure(
    Map<String, String> programArgs, final ProgramManager<T> programManager)
    throws TimeoutException, InterruptedException, ExecutionException {
    final int prevNumFailures = programManager.getHistory(ProgramRunStatus.FAILED).size();

    programManager.start(programArgs);

    // need to check that every run has failed as well as the number of failures
    // otherwise there is a race where start() returns before any run record is written
    // and this check passes because there are existing failed runs, but the new run has not failed.
    Tasks.waitFor(true, () -> {
      // verify program history just have failures, and there is one more failure than before program start
      List<RunRecord> history = programManager.getHistory();
      for (final RunRecord runRecord : history) {
        if (runRecord.getStatus() != ProgramRunStatus.FAILED) {
          return false;
        }
      }
      return history.size() == prevNumFailures + 1;
    }, 5, TimeUnit.MINUTES, "Not all program runs have failed status. Expected all run status to be failed");

    programManager.waitForStopped(10, TimeUnit.SECONDS);
  }


  private void assertAllAccess(Principal principal, EntityId... entityIds) throws Exception {
    for (EntityId entityId : entityIds) {
      getAuthorizer().enforce(entityId, principal, EnumSet.allOf(Action.class));
    }
  }

  private void addDummyData(NamespaceId namespaceId, String datasetName) throws Exception {
    DataSetManager<KeyValueTable> tableManager = getDataset(namespaceId.dataset(datasetName));
    KeyValueTable inputTable = tableManager.get();
    inputTable.write("hello", "world");
    tableManager.flush();
  }

  private void verifyDummyData(NamespaceId namespaceId, String datasetName) throws Exception {
    DataSetManager<KeyValueTable> outTableManager = getDataset(namespaceId.dataset(datasetName));
    KeyValueTable outputTable = outTableManager.get();
    Assert.assertEquals("world", Bytes.toString(outputTable.read("hello")));
  }

  private void setUpPrivilegeAndRegisterForDeletion(Principal principal,
                                                    Map<EntityId, Set<Action>> neededPrivileges) throws Exception {
    for (Map.Entry<EntityId, Set<Action>> privilege : neededPrivileges.entrySet()) {
      grantAndAssertSuccess(privilege.getKey(), principal, privilege.getValue());
      cleanUpEntities.add(privilege.getKey());
    }
  }

  private void waitForStoppedPrograms(final ProgramManager programManager) throws Exception {
    Tasks.waitFor(true, new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        List<RunRecord> runs = programManager.getHistory();
        for (RunRecord meta : runs) {
          if (meta.getStatus() == ProgramRunStatus.STARTING ||
            meta.getStatus() == ProgramRunStatus.RUNNING ||
            meta.getStatus() == ProgramRunStatus.RESUMING) {
            return false;
          }
        }
        return true;
      }
    }, 10, TimeUnit.SECONDS);
  }
}
