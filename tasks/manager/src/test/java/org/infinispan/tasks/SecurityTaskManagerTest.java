package org.infinispan.tasks;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.security.auth.Subject;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.security.AuthorizationPermission;
import org.infinispan.security.Security;
import org.infinispan.security.mappers.IdentityRoleMapper;
import org.infinispan.tasks.impl.TaskManagerImpl;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests verifying the tasks execution with cache security settings.
 *
 * @author amanukya
 */
@Test(groups = "functional", testName = "tasks.SecurityTaskManagerTest")
public class SecurityTaskManagerTest extends SingleCacheManagerTest {

   static final Subject ADMIN = TestingUtil.makeSubject("admin", "admin");
   static final Subject HACKER = TestingUtil.makeSubject("hacker", "hacker");

   private TaskManagerImpl taskManager;
   private DummyTaskEngine taskEngine;

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();
      global
            .security()
            .authorization().enable()
            .principalRoleMapper(new IdentityRoleMapper())
            .role("admin").permission(AuthorizationPermission.ALL);
      ConfigurationBuilder config = new ConfigurationBuilder();
      config.security().authorization().enable()
            .role("admin");
      return TestCacheManagerFactory.createCacheManager(global, config);
   }

   @Override
   protected void setup() throws Exception {
      Security.doAs(ADMIN, () -> {
         try {
            SecurityTaskManagerTest.super.setup();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
         taskManager = (TaskManagerImpl) cacheManager.getGlobalComponentRegistry().getComponent(TaskManager.class);
         taskEngine = new DummyTaskEngine();
         taskManager.registerTaskEngine(taskEngine);
      });
   }

   @Override
   protected void teardown() {
      Security.doAs(ADMIN, () -> SecurityTaskManagerTest.super.teardown());
   }

   @Override
   protected void clearContent() {
      Security.doAs(ADMIN, () -> cacheManager.getCache().clear());
   }

   @Test(dataProvider = "principalProvider")
   public void testTaskExecutionWithAuthorization(String principal, Subject subject) {
      Security.doAs(subject, () -> {
         CompletableFuture<Object> slowTask = taskManager.runTask(DummyTaskEngine.DummyTaskTypes.SLOW_TASK.name(), new TaskContext())
               .toCompletableFuture();
         Collection<TaskExecution> currentTasks = taskManager.getCurrentTasks();
         assertEquals(1, currentTasks.size());

         TaskExecution currentTask = currentTasks.iterator().next();
         assertEquals(DummyTaskEngine.DummyTaskTypes.SLOW_TASK.name(), currentTask.getName());
         assertEquals(principal, currentTask.getWho().get());
         taskEngine.getSlowTask().complete("slow");

         assertEquals(0, taskManager.getCurrentTasks().size());
         try {
            assertEquals("slow", slowTask.get());
         } catch (InterruptedException | ExecutionException e) {
            fail("Exception thrown while getting the slowTask. ");
         }

         taskEngine.setSlowTask(new CompletableFuture<>());
      });
   }

   @DataProvider(name = "principalProvider")
   private static Object[][] providePrincipals() {
      return new Object[][]{{"admin", ADMIN}, {"hacker", HACKER}};
   }
}
