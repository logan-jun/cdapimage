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

package io.cdap.cdap.security.authorization.sentry.binding;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.cdap.cdap.api.NamespaceSummary;
import io.cdap.cdap.api.TxRunnable;
import io.cdap.cdap.api.data.DatasetInstantiationException;
import io.cdap.cdap.api.dataset.Dataset;
import io.cdap.cdap.api.dataset.DatasetManagementException;
import io.cdap.cdap.api.dataset.DatasetProperties;
import io.cdap.cdap.api.dataset.InstanceNotFoundException;
import io.cdap.cdap.api.security.store.SecureStoreData;
import io.cdap.cdap.api.security.store.SecureStoreMetadata;
import io.cdap.cdap.proto.ProgramType;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.ArtifactId;
import io.cdap.cdap.proto.id.DatasetId;
import io.cdap.cdap.proto.id.DatasetModuleId;
import io.cdap.cdap.proto.id.DatasetTypeId;
import io.cdap.cdap.proto.id.EntityId;
import io.cdap.cdap.proto.id.KerberosPrincipalId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.proto.id.ProgramId;
import io.cdap.cdap.proto.id.SecureKeyId;
import io.cdap.cdap.proto.id.WorkerId;
import io.cdap.cdap.proto.security.Action;
import io.cdap.cdap.proto.security.Authorizable;
import io.cdap.cdap.proto.security.Principal;
import io.cdap.cdap.proto.security.Privilege;
import io.cdap.cdap.proto.security.Role;
import io.cdap.cdap.security.authorization.sentry.binding.conf.AuthConf;
import io.cdap.cdap.security.spi.authorization.AuthorizationContext;
import io.cdap.cdap.security.spi.authorization.UnauthorizedException;
import org.apache.hadoop.conf.Configuration;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Test for {@link SentryAuthorizer}
 */
public class SentryAuthorizerTest {

  @ClassRule
  public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

  private static final String SUPERUSER_HULK = "hulk";
  private static final String SUPERUSER_SPIDERMAN = "spiderman";
  private static final int CACHE_TTL_SECS = 3;

  private static SentryAuthorizer authorizer;
  private static TestSentryService sentryService;

  @BeforeClass
  public static void setupTests() throws Exception {
    URL policyFileResource = SentryAuthorizer.class.getClassLoader().getResource("test-authz-provider.ini");
    Assert.assertNotNull("Cannot find policy file: test-authz-provider.ini", policyFileResource);
    sentryService = new TestSentryService(TEMPORARY_FOLDER.newFolder(), new File(policyFileResource.getPath()));
    sentryService.start();

    Configuration clientConfig = sentryService.getClientConfig();
    File sentrySite = TEMPORARY_FOLDER.newFile("sentry-site.xml");
    clientConfig.writeXml(new FileOutputStream(sentrySite));

    final Properties properties = new Properties();
    properties.put(AuthConf.SENTRY_SITE_URL, sentrySite.getAbsolutePath());
    properties.put(AuthConf.INSTANCE_NAME, "cdap");
    properties.put(AuthConf.SUPERUSERS, Joiner.on(",").join(SUPERUSER_HULK, SUPERUSER_SPIDERMAN));
    properties.put(AuthConf.SENTRY_ADMIN_GROUP, "cdap");
    properties.put(AuthConf.CACHE_MAX_ENTRIES, "100");
    properties.put(AuthConf.CACHE_TTL_SECS, String.valueOf(CACHE_TTL_SECS));
    authorizer = new SentryAuthorizer();
    authorizer.initialize(new AuthorizationContext() {
      @Override
      public boolean namespaceExists(String s) {
        return false;
      }

      @Nullable
      @Override
      public NamespaceSummary getNamespaceSummary(String s) {
        return null;
      }

      @Override
      public List<SecureStoreMetadata> list(String namespace) {
        return Collections.emptyList();
      }

      @Override
      public SecureStoreData get(String namespace, String name) {
        throw new NoSuchElementException(namespace + ":" + name);
      }

      @Override
      public void put(String namespace, String key, String data, String description,
                      Map<String, String> properties) {
        // no-op
      }

      @Override
      public void delete(String namespace, String key) {
        // no-op
      }

      @Override
      public Principal getPrincipal() {
        return new Principal(TestSentryService.ADMIN_USER, Principal.PrincipalType.USER);
      }

      @Override
      public Properties getExtensionProperties() {
        return properties;
      }

      @Override
      public void createTopic(String topic) {
        // no-op
      }

      @Override
      public void createTopic(String topic,
                              Map<String, String> properties) {
        // no-op
      }

      @Override
      public Map<String, String> getTopicProperties(String topic) {
        return Collections.emptyMap();
      }

      @Override
      public void updateTopic(String topic, Map<String, String> properties) {
        // no-op
      }

      @Override
      public void deleteTopic(String topic) {
        // no-op
      }

      @Override
      public boolean datasetExists(String name) {
        return false;
      }

      @Override
      public String getDatasetType(String name) throws DatasetManagementException {
        throw new InstanceNotFoundException(name);
      }

      @Override
      public DatasetProperties getDatasetProperties(String name) throws DatasetManagementException {
        throw new InstanceNotFoundException(name);
      }

      @Override
      public void createDataset(String name, String type, DatasetProperties datasetProperties) {
        // no-op
      }

      @Override
      public void updateDataset(String name, DatasetProperties datasetProperties) {
        // no-op
      }

      @Override
      public void dropDataset(String name) {
        // no-op
      }

      @Override
      public void truncateDataset(String name) {
        // no-op
      }

      @Override
      public <T extends Dataset> T getDataset(String name) throws DatasetInstantiationException {
        throw new DatasetInstantiationException("Cannot get dataset through no-op AuthorizationContext");
      }

      @Override
      public <T extends Dataset> T getDataset(String namespace, String dataset) throws DatasetInstantiationException {
        throw new DatasetInstantiationException("Cannot get dataset through no-op AuthorizationContext");
      }

      @Override
      public <T extends Dataset> T getDataset(String name, Map<String, String> map)
        throws DatasetInstantiationException {
        throw new DatasetInstantiationException("Cannot get dataset through no-op AuthorizationContext");
      }

      @Override
      public <T extends Dataset> T getDataset(String namespace, String dataset,
                                              Map<String, String> properties) throws DatasetInstantiationException {
        throw new DatasetInstantiationException("Cannot get dataset through no-op AuthorizationContext");
      }

      @Override
      public void releaseDataset(Dataset dataset) {
        // no-op
      }

      @Override
      public void discardDataset(Dataset dataset) {
        // no-op
      }

      @Override
      public void execute(TxRunnable txRunnable) {
        // no-op
      }

      @Override
      public void execute(int timeout, TxRunnable txRunnable) {
        // no-op
      }
    });
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (sentryService != null) {
      sentryService.stop();
    }
  }

  @Test
  public void testAuthorized() throws Exception {
    testAuthorized(new NamespaceId("ns1"));
    testAuthorized(new DatasetId("ns1", "ds1"));
    testAuthorized(new ArtifactId("ns1", "art", "1"));
    testAuthorized(new ApplicationId("ns1", "app1"));
    testAuthorized(new ProgramId("ns1", "app1", ProgramType.WORKER, "prog1"));

    // admin2 is admin of ns2
    assertAuthorized(new NamespaceId("ns2"), getUser("admin2"), Action.ADMIN);
    // user2 can read dataset1 in ns2
    assertAuthorized(new DatasetId("ns2", "dataset1"), getUser("readers2"), Action.READ);

    // executors1 can execute prog1
    assertAuthorized(new ProgramId("ns1", "app1", ProgramType.WORKER, "prog1"), getUser("executors1"),
                     Action.EXECUTE);

    // all_admin is admin of all namespaces
    assertAuthorized(new NamespaceId("ns1"), getUser("all_admin"), Action.ADMIN);
    assertAuthorized(new NamespaceId("ns2"), getUser("all_admin"), Action.ADMIN);
    assertAuthorized(new NamespaceId("ns3"), getUser("all_admin"), Action.ADMIN);

    // check principal authorization
    assertAuthorized(new KerberosPrincipalId("alice"), getUser("admin1"), Action.ADMIN);
    assertAuthorized(new KerberosPrincipalId("bobby"), getUser("admin2"), Action.ADMIN);
    assertAuthorized(new KerberosPrincipalId("eve/host1.com@domain.net"), getUser("all_admin"), Action.ADMIN);

    // check multi-actions
    assertAuthorized(new DatasetId("ns1", "ds1"), getUser("all1"),
                     ImmutableSet.of(Action.READ, Action.WRITE, Action.EXECUTE, Action.ADMIN));
    assertAuthorized(new DatasetId("ns1", "ds1"), getUser("all1"),
                     ImmutableSet.of(Action.READ, Action.EXECUTE, Action.ADMIN));

    assertAuthorized(new DatasetId("ns1", "ds1"), getUser("rw_ds1"),
                     ImmutableSet.of(Action.READ, Action.WRITE));
    assertAuthorized(new DatasetId("ns2", "ds1"), getUser("rw_ds1"),
                     ImmutableSet.of(Action.READ, Action.WRITE));
  }

  @Test
  public void testListPrivileges() throws Exception {
    Set<Privilege> privileges = authorizer.listPrivileges(getUser("ns3_user1"));
    ImmutableSet<Privilege> expectedPrivileges = ImmutableSet.of(
      new Privilege(Authorizable.fromString("securekey:ns3.*_key-??"), Action.ADMIN),
      new Privilege(Authorizable.fromString("artifact:ns3.*"), Action.READ),
      (new Privilege(Authorizable.fromString("dataset_type:ns3.io.cdap.table??"), Action.READ))
    );

    Assert.assertTrue(privileges.containsAll(expectedPrivileges));
    privileges = authorizer.listPrivileges(getUser("admin1"));
    expectedPrivileges = ImmutableSet.of(
      new Privilege(Authorizable.fromString("application:ns1.app1"), Action.ADMIN),
      new Privilege(Authorizable.fromString("instance:cdap"), Action.ADMIN));
    Assert.assertTrue(privileges.containsAll(expectedPrivileges));
  }

  @Test
  public void testUnauthorized() throws Exception {
    // do some invalid operations
    // admin1 is not admin of ns2
    assertUnauthorized(new NamespaceId("ns2"), getUser("admin1"), Action.ADMIN);

    // user2 cannot read dataset1 in ns1
    assertUnauthorized(new DatasetId("ns1", "dataset1"), getUser("readers2"), Action.READ);

    // readers1 cannot write dataset1 in ns1
    assertUnauthorized(new DatasetId("ns1", "dataset1"), getUser("readers1"), Action.WRITE);
    // writers1 cannot write dataset1 in ns1
    assertUnauthorized(new DatasetId("ns1", "dataset1"), getUser("writers1"), Action.READ);
    // admin1 cannot read/write/all/execute on
    assertUnauthorized(new DatasetId("ns1", "dataset1"), getUser("admin1"), Action.READ);
    assertUnauthorized(new DatasetId("ns1", "dataset1"), getUser("admin1"), Action.WRITE);
    assertUnauthorized(new DatasetId("ns1", "dataset1"), getUser("admin1"), Action.EXECUTE);

    // all_admin cannot read/write/execute/admin any entities in any namespace
    assertUnauthorized(new DatasetId("ns2", "dataset1"), getUser("all_admin"), Action.READ);
    assertUnauthorized(new DatasetId("ns1", "ds1"), getUser("all_admin"), Action.WRITE);
    assertUnauthorized(new ProgramId("ns1", "app1", ProgramType.WORKER, "prog1"),
                       getUser("all_admin"), Action.EXECUTE);
    assertUnauthorized(new ApplicationId("ns1", "app1"), getUser("all_admin"), Action.ADMIN);

    // check principal authorization
    assertUnauthorized(new KerberosPrincipalId("alice"), getUser("admin2"), Action.ADMIN);
    assertUnauthorized(new KerberosPrincipalId("alice"), getUser("admin1"), Action.EXECUTE);
    assertUnauthorized(new KerberosPrincipalId("bobby"), getUser("admin1"), Action.ADMIN);

    // check multi-actions
    assertUnauthorized(new DatasetId("ns1", "ds1"), getUser("readers1"),
                     ImmutableSet.of(Action.READ, Action.WRITE, Action.EXECUTE, Action.ADMIN));
    assertUnauthorized(new DatasetId("ns1", "ds1"), getUser("readers1"),
                     ImmutableSet.of(Action.READ, Action.EXECUTE, Action.ADMIN));

    assertUnauthorized(new DatasetId("ns1", "ds1"), getUser("rw_ds1"),
                     ImmutableSet.of(Action.READ, Action.WRITE, Action.EXECUTE));
    assertUnauthorized(new DatasetId("ns1", "ds1"), getUser("rw_ds1"),
                       ImmutableSet.of(Action.WRITE, Action.EXECUTE, Action.ADMIN));
    assertUnauthorized(new DatasetId("ns1", "ds2"), getUser("rw_ds1"),
                       ImmutableSet.of(Action.READ, Action.WRITE));
    assertUnauthorized(new DatasetId("ns1", "dataset1"), getUser("rw_ds1"),
                       ImmutableSet.of(Action.READ, Action.WRITE));
  }

  @Test
  public void testVisibility() throws Exception {
    // Test visibility of executors1
    Set<? extends EntityId> visible = ImmutableSet.of(new NamespaceId("ns1"),
                                            new ApplicationId("ns1", "app1"),
                                            new WorkerId("ns1", "app1", "prog1"));
    Set<? extends EntityId> notVisible = ImmutableSet.of(new NamespaceId("ns2"),
                                                         new DatasetId("ns1", "ds1"),
                                                         new ArtifactId("ns1", "art", "1"));
    Set<? extends EntityId> actual = authorizer.isVisible(Sets.union(visible, notVisible), getUser("executors1"));
    Assert.assertEquals(visible, actual);

    // Test visibility of readers1
    visible = ImmutableSet.of(new NamespaceId("ns1"),
                              new ApplicationId("ns1", "app1"),
                              new WorkerId("ns1", "app1", "prog1"),
                              new DatasetId("ns1", "ds1"),
                              new ArtifactId("ns1", "art", "1"),
                              new DatasetId("ns1", "dataset1")
                              );
    notVisible = ImmutableSet.of(new NamespaceId("ns2"));
    actual = authorizer.isVisible(Sets.union(visible, notVisible), getUser("readers1"));
    Assert.assertEquals(visible, actual);

    // Test visibility of writers1
    visible = ImmutableSet.of(new NamespaceId("ns1"),
                              new ApplicationId("ns1", "app1"),
                              new WorkerId("ns1", "app1", "prog1")
                              );
    notVisible = ImmutableSet.of(new WorkerId("ns2", "app1", "prog1"),
                                 new WorkerId("ns1", "app1", "pr")
                                 );
    actual = authorizer.isVisible(Sets.union(visible, notVisible), getUser("writers1"));
    Assert.assertEquals(visible, actual);


    // Test visibility of all_admin
    visible = ImmutableSet.of(new NamespaceId("ns1"),
                              new NamespaceId("ns5"),
                              new NamespaceId("ns8")
                              );
    notVisible = ImmutableSet.of(new ApplicationId("ns1", "app1"),
                                 new DatasetId("ns1", "ds1"),
                                 new WorkerId("ns1", "app1", "prog1"),
                                 new ArtifactId("ns1", "art", "1"),
                                 new DatasetId("ns2", "dataset1")
                                 );
    actual = authorizer.isVisible(Sets.union(visible, notVisible), getUser("all_admin"));
    Assert.assertEquals(visible, actual);

    // Test visibility of rw_ds1
    visible = ImmutableSet.of(new NamespaceId("ns1"),
                              new NamespaceId("ns5"),
                              new NamespaceId("ns8"),
                              new DatasetId("ns1", "ds1"),
                              new DatasetId("ns2", "ds1")
                              );
    notVisible = ImmutableSet.of(new ApplicationId("ns1", "app1"),
                                 new WorkerId("ns2", "app1", "prog1"),
                                 new ArtifactId("ns1", "art", "1"),
                                 new DatasetId("ns2", "dataset1")
                                 );
    actual = authorizer.isVisible(Sets.union(visible, notVisible), getUser("rw_ds1"));
    Assert.assertEquals(visible, actual);
  }

  @Test
  public void testUser1Ns3() throws Exception {
    // ns3_user1 can read all artifacts in ns3, write to all datasets in ns3, execute all programs in ns3,
    // read all dataset types that is of the form table?? in ns3, and admin all secure keys of form *_key-?? in ns3
    Principal user = getUser("ns3_user1");

    // artifacts
    assertAuthorized(new ArtifactId("ns3", "art", "1"), user, Action.READ);
    assertAuthorized(new ArtifactId("ns3", "art", "2"), user, Action.READ);
    assertAuthorized(new ArtifactId("ns3", "artifact", "2"), user, Action.READ);

    assertUnauthorized(new ArtifactId("ns5", "artifact", "1"), user, Action.READ);
    assertUnauthorized(new ArtifactId("ns3", "artifact", "2"), user, Action.WRITE);

    // programs
    assertAuthorized(new ProgramId("ns3", "app1", ProgramType.WORKER, "prog1"), user, Action.EXECUTE);
    assertAuthorized(new ProgramId("ns3", "app5", ProgramType.SPARK, "p10"), user, Action.EXECUTE);
    assertAuthorized(new ProgramId("ns3", "a1", ProgramType.WORKER, "p1"), user, Action.EXECUTE);

    assertUnauthorized(new ProgramId("ns2", "app5", ProgramType.SPARK, "p10"), user, Action.EXECUTE);
    assertUnauthorized(new ProgramId("ns3", "app5", ProgramType.WORKER, "p10"), user, Action.ADMIN);

    // datasets
    assertUnauthorized(new DatasetId("ns3", "ds1"), user, Action.READ);

    // dataset types
    assertAuthorized(new DatasetTypeId("ns3", "io.cdap.table10"), user, Action.READ);
    assertAuthorized(new DatasetTypeId("ns3", "io.cdap.table-1"), user, Action.READ);
    assertAuthorized(new DatasetTypeId("ns3", "io.cdap.table1b"), user, Action.READ);

    assertUnauthorized(new DatasetTypeId("ns2", "io.cdap.table1b"), user, Action.READ);
    assertUnauthorized(new DatasetTypeId("ns3", "io.cdap.table1bc"), user, Action.READ);
    assertUnauthorized(new DatasetTypeId("ns3", "io.cdap.table1b"), user, Action.WRITE);
    assertUnauthorized(new DatasetTypeId("ns3", "1io.cdap.table-1"), user, Action.READ);

    // secure keys
    assertAuthorized(new SecureKeyId("ns3", "secret_key-01"), user, Action.ADMIN);
    assertAuthorized(new SecureKeyId("ns3", "encrypt_key-ab"), user, Action.ADMIN);

    assertUnauthorized(new SecureKeyId("ns2", "encrypt_key-ab"), user, Action.ADMIN);
    assertUnauthorized(new SecureKeyId("ns3", "encrypt_key-ab"), user, Action.READ);
    assertUnauthorized(new SecureKeyId("ns3", "encrypt_keys-ab"), user, Action.ADMIN);
    assertUnauthorized(new SecureKeyId("ns3", "encrypt-key_ab"), user, Action.ADMIN);
    assertUnauthorized(new SecureKeyId("ns3", "encrypt_key-abc"), user, Action.ADMIN);
  }

  @Test
  public void testUser2Ns3() throws Exception {
    // ns3_user2 can read any version of artifact art in ns3, write to all datasets in ns3, admin all apps in ns3,
    // execute all workers in ns3 and all access to dataset modules with name tab_*_mod in ns3
    Principal user = getUser("ns3_user2");

    assertAuthorized(new ArtifactId("ns3", "art", "1"), user, Action.READ);
    assertAuthorized(new ArtifactId("ns3", "art", "2"), user, Action.READ);
    assertAuthorized(new ArtifactId("ns3", "artifact", "2"), user, Action.READ);

    assertUnauthorized(new ArtifactId("ns5", "art", "1"), user, Action.READ);
    assertUnauthorized(new ArtifactId("ns3", "art", "1"), user, Action.WRITE);

    // datasets
    assertAuthorized(new DatasetId("ns3", "ds1"), user, Action.WRITE);
    assertAuthorized(new DatasetId("ns3", "ds15"), user, Action.WRITE);

    assertUnauthorized(new DatasetId("ns3", "ds1"), user, Action.READ);
    assertUnauthorized(new DatasetId("ns4", "ds1"), user, Action.WRITE);

    // applications
    assertAuthorized(new ApplicationId("ns3", "app1"), user, Action.ADMIN);
    assertAuthorized(new ApplicationId("ns3", "app4"), user, Action.ADMIN);

    assertUnauthorized(new ApplicationId("ns3", "app1"), user, Action.READ);
    assertUnauthorized(new ApplicationId("ns2", "app1"), user, Action.ADMIN);

    // workers
    assertAuthorized(new ProgramId("ns3", "app1", ProgramType.WORKER, "prog1"), user, Action.EXECUTE);
    assertAuthorized(new ProgramId("ns3", "app1", ProgramType.WORKER, "prog2"), user, Action.EXECUTE);

    assertUnauthorized(new ProgramId("ns3", "app1", ProgramType.WORKER, "prog1"), user, Action.READ);
    assertUnauthorized(new ProgramId("ns3", "a1", ProgramType.SPARK, "s1"), user, Action.EXECUTE);

    // dataset modules
    assertAuthorized(new DatasetModuleId("ns3", "io.cdap.tab_1_mod"), user, Action.READ);
    assertAuthorized(new DatasetModuleId("ns3", "io.cdap.tab_data_mod"), user, Action.WRITE);
    assertAuthorized(new DatasetModuleId("ns3", "io.cdap.tab_da_4_ta_mod"), user, Action.ADMIN);

    assertUnauthorized(new DatasetModuleId("ns2", "io.cdap.tab_1_mod"), user, Action.ADMIN);
    assertUnauthorized(new DatasetModuleId("ns3", "io.cdap.table_1_mod"), user, Action.ADMIN);
  }

  @Test
  public void testRoleGrant() throws Exception {
    String allPrograms = "program:gold.new_pipeline.*";
    Principal execRolePrincipal = new Principal("exec-role", Principal.PrincipalType.ROLE);
    Role execRole = new Role(execRolePrincipal.getName());
    authorizer.createRole(execRole);
    authorizer.grant(Authorizable.fromString(allPrograms),
                     execRolePrincipal,
                     ImmutableSet.of(Action.EXECUTE));
    authorizer.addRoleToPrincipal(execRole,
                                  new Principal("spare_group", Principal.PrincipalType.GROUP));
    assertAuthorized(new ProgramId("gold", "new_pipeline", ProgramType.WORKER, "worker1"),
                     getUser("spare_user"),
                     ImmutableSet.of(Action.EXECUTE));
    authorizer.dropRole(execRole);
  }

  private void testAuthorized(EntityId entityId) throws Exception {
    // admin1 is admin of entity
    assertAuthorized(entityId, getUser("admin1"), Action.ADMIN);
    // readers1 can read entity
    assertAuthorized(entityId, getUser("readers1"), Action.READ);
    // writer1 can write entity
    assertAuthorized(entityId, getUser("writers1"), Action.WRITE);
    // all1 can read/write/admin to entity
    assertAuthorized(entityId, getUser("all1"), Action.WRITE);
    assertAuthorized(entityId, getUser("all1"), Action.READ);
    assertAuthorized(entityId, getUser("all1"), Action.ADMIN);
  }

  private void assertAuthorized(EntityId entityId, Principal principal, Action action) throws Exception {
    authorizer.enforce(entityId, principal, action);
  }

  private void assertAuthorized(EntityId entityId, Principal principal, Set<Action> actions) throws Exception {
    authorizer.enforce(entityId, principal, actions);
  }

  private void assertUnauthorized(EntityId entityId, Principal principal, Action action) throws Exception {
    try {
      authorizer.enforce(entityId, principal, action);
      Assert.fail("The authorization check should have failed.");
    } catch (UnauthorizedException expected) {
      // expected
    }
  }

  private void assertUnauthorized(EntityId entityId, Principal principal, Set<Action> actions) throws Exception {
    try {
      authorizer.enforce(entityId, principal, actions);
      Assert.fail("The authorization check should have failed.");
    } catch (UnauthorizedException expected) {
      // expected
    }
  }

  private Principal getUser(String name) {
    return new Principal(name, Principal.PrincipalType.USER);
  }
}
