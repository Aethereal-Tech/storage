package net.aetherealtech.storage.tx;

import net.aetherealtech.storage.InMemoryObjectStorage;
import net.aetherealtech.storage.ObjectKey;
import net.aetherealtech.storage.StorageUnavailableException;
import net.aetherealtech.storage.UnavailableObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AfterCommitTest {

    private static final ObjectKey KEY = ObjectKey.of("listings/5/photo");

    @AfterEach
    void clearSynchronization() {
        // A failed assertion inside a test that registered a synchronization would otherwise leak
        // the bound state — Spring's manager is a ThreadLocal — into whichever test runs next on
        // the same thread.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Nested
    class NoTransaction {

        @Test
        void deletesImmediately() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            storage.put(KEY, new byte[] {1}, "image/png");

            AfterCommit.delete(storage, KEY);

            assertThat(storage.exists(KEY)).isFalse();
        }
    }

    @Nested
    class Commit {

        @Test
        @DisplayName("the object survives until afterCommit actually runs, then is gone")
        void deletesOnlyAfterAfterCommitRuns() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            storage.put(KEY, new byte[] {1}, "image/png");
            TransactionSynchronizationManager.initSynchronization();

            AfterCommit.delete(storage, KEY);

            assertThat(storage.exists(KEY)).isTrue();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(storage.exists(KEY)).isFalse();
        }
    }

    @Nested
    class Rollback {

        @Test
        @DisplayName("clearing the synchronization without running afterCommit never deletes")
        void neverDeletes() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            storage.put(KEY, new byte[] {1}, "image/png");
            TransactionSynchronizationManager.initSynchronization();

            AfterCommit.delete(storage, KEY);
            TransactionSynchronizationManager.clearSynchronization();

            assertThat(storage.exists(KEY)).isTrue();
        }
    }

    @Nested
    class FailureAfterCommit {

        @Test
        @DisplayName("a delete that fails after commit is logged, not thrown — the caller's work already succeeded")
        void isSwallowed() {
            TransactionSynchronizationManager.initSynchronization();

            AfterCommit.delete(new UnavailableObjectStorage(), KEY);

            assertThatCode(
                            () ->
                                    TransactionSynchronizationManager.getSynchronizations()
                                            .forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the immediate path (no transaction) DOES throw — there is no committed work to contradict")
        void theImmediatePathThrows() {
            assertThatThrownBy(() -> AfterCommit.delete(new UnavailableObjectStorage(), KEY))
                    .isInstanceOf(StorageUnavailableException.class);
        }
    }

    @Nested
    class Nulls {

        @Test
        void nullStorageThrows() {
            assertThatThrownBy(() -> AfterCommit.delete(null, KEY))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("storage");
        }

        @Test
        void nullKeyThrows() {
            assertThatThrownBy(() -> AfterCommit.delete(new InMemoryObjectStorage(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("key");
        }
    }
}
