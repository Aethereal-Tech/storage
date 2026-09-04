package net.aetherealtech.storage.tx;

import java.util.Objects;
import net.aetherealtech.storage.ObjectKey;
import net.aetherealtech.storage.ObjectStorage;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Deletes an object once the transaction that dropped its row has actually committed.
 *
 * <p><b>Delete the ROW first, the object after the commit.</b> Get the order wrong and a rolled-back
 * transaction leaves a live row pointing at bytes that are gone — a broken image on a page, with
 * nothing left to repair it from. This way round, a failed delete leaks an unreferenced object,
 * which costs storage and nothing else.
 *
 * <pre>{@code
 * @Transactional
 * public void removePhoto(final Long photoId) {
 *     ListingPhoto photo = photos.findById(photoId).orElseThrow();
 *     photos.delete(photo);
 *     AfterCommit.delete(storage, ObjectKey.of(photo.getObjectKey()));
 * }
 * }</pre>
 *
 * <p>The one class here that needs Spring — {@code spring-tx}, {@code optional} in this artifact, so
 * a consumer without it never loads this class and pays nothing for its existence.
 */
public final class AfterCommit {

    private static final System.Logger LOGGER = System.getLogger(AfterCommit.class.getName());

    /**
     * Registers the delete against the current transaction, or performs it now if there is none.
     *
     * <p>"No transaction" is not an error and is not a warning: a maintenance job, a test, or a
     * caller outside any {@code @Transactional} boundary has nothing to wait for, and the delete has
     * the same meaning immediately. What would be an error is silently doing nothing, which is what
     * a version of this that only ever registered would do.
     *
     * <p><b>A failure after the commit is logged, not thrown.</b> The row is already gone and the
     * caller's work already succeeded; propagating would fail a request that did everything right,
     * to report a leaked object the caller cannot do anything about. The immediate path does throw,
     * because there is no committed work to contradict.
     */
    public static void delete(final ObjectStorage storage, final ObjectKey key) {
        Objects.requireNonNull(storage, "storage must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.delete(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            storage.delete(key);
                        } catch (final RuntimeException e) {
                            LOGGER.log(
                                    System.Logger.Level.WARNING,
                                    "Could not delete " + key + " after commit; the object is now"
                                            + " unreferenced and must be cleaned up out of band",
                                    e);
                        }
                    }
                });
    }

    private AfterCommit() {}
}
