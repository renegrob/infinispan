/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.infinispan.query.impl.massindex;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.hibernate.search.engine.reporting.EntityIndexingFailureContext;
import org.hibernate.search.engine.reporting.FailureHandler;
import org.hibernate.search.util.common.SearchException;

import org.infinispan.commons.time.TimeService;
import org.infinispan.query.logging.Log;
import org.infinispan.search.mapper.common.EntityReference;
import org.infinispan.search.mapper.mapping.SearchMappingHolder;
import org.infinispan.util.logging.LogFactory;

class MassIndexerProgressNotifier {

   private static final Log log = LogFactory.getLog(MassIndexerProgressNotifier.class, Log.class);

   private final MassIndexerProgressMonitor monitor;
   private final SearchMappingHolder searchMappingHolder;

   private final AtomicReference<RecordedEntityIndexingFailure> entityIndexingFirstFailure = new AtomicReference<>(null);
   private final LongAdder entityIndexingFailureCount = new LongAdder();

   private FailureHandler failureHandler;

   MassIndexerProgressNotifier(SearchMappingHolder searchMappingHolder, TimeService timeService) {
      this.monitor = new MassIndexerProgressMonitor(timeService);
      this.searchMappingHolder = searchMappingHolder;
   }

   void notifyDocumentsAdded(int size) {
      monitor.documentsAdded(size);
   }

   void notifyIndexingCompletedSuccessfully() {
      monitor.indexingCompleted();

      SearchException entityIndexingException = createEntityIndexingExceptionOrNull();
      if (entityIndexingException != null) {
         throw entityIndexingException;
      }
   }

   <T> void notifyEntityIndexingFailure(Class<T> type, Object id, Throwable throwable) {
      RecordedEntityIndexingFailure recordedFailure = new RecordedEntityIndexingFailure(throwable);
      entityIndexingFirstFailure.compareAndSet(null, recordedFailure);
      entityIndexingFailureCount.increment();

      EntityIndexingFailureContext.Builder contextBuilder = EntityIndexingFailureContext.builder();
      contextBuilder.throwable(throwable);
      // Add minimal information here, but information we're sure we can get
      contextBuilder.failingOperation(log.massIndexerIndexingInstance(type.getSimpleName()));
      // Add more information here, but information that may not be available if the session completely broke down
      // (we're being extra careful here because we don't want to throw an exception while handling and exception)
      EntityReference entityReference = EntityReference.withDefaultName(type, id);
      if (entityReference != null) {
         contextBuilder.entityReference(entityReference);
         recordedFailure.entityReference = entityReference;
      }

      if (failureHandler == null) {
         failureHandler = searchMappingHolder.getSearchMapping().getFailureHandler();
      }
      failureHandler.handle(contextBuilder.build());
   }

   private SearchException createEntityIndexingExceptionOrNull() {
      RecordedEntityIndexingFailure firstFailure = entityIndexingFirstFailure.get();
      if (firstFailure == null) {
         return null;
      }
      return log.massIndexingEntityFailures(entityIndexingFailureCount.longValue(), firstFailure.entityReference, firstFailure.throwable.getMessage(), firstFailure.throwable);
   }

   private static class RecordedEntityIndexingFailure {
      private Throwable throwable;
      private EntityReference entityReference;

      RecordedEntityIndexingFailure(Throwable throwable) {
         this.throwable = throwable;
      }
   }
}