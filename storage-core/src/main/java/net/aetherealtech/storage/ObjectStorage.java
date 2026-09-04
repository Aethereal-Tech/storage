package net.aetherealtech.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Somewhere to keep bytes that are not in the database.
 *
 * <p>The whole vocabulary is a {@link ObjectKey key}, some bytes and a content type. Nothing here
 * knows what a listing, an organization or an order is, and nothing here ever will — that is the
 * mistake this library exists to undo.
 *
 * <p><b>Reads are presigned, writes are not.</b> {@link #presignGet} hands the browser a URL it
 * fetches the object from directly, which is what makes a public page render images with no session
 * and no proxying through the API. Writes go through the server, so the server can enforce what the
 * bytes are before they land — see {@link net.aetherealtech.storage.upload.UploadInspector}.
 *
 * <p><b>Delete the ROW first, the object after the commit.</b> A failed delete then leaks an
 * unreferenced object, which costs storage; the other order leaves a live row pointing at nothing,
 * which is a broken image on a page. {@link net.aetherealtech.storage.tx.AfterCommit} does the
 * registering.
 *
 * <p>Two implementations here need nothing: {@link InMemoryObjectStorage} for a consumer's own
 * suite, and {@link UnavailableObjectStorage} for {@code optional} mode. The S3 one is a separate
 * artifact.
 */
public interface ObjectStorage {

    /**
     * Stores the bytes under {@code key}, replacing anything already there.
     *
     * @param contentType what a browser will be told this is when it fetches it back; take it from
     *                    {@link net.aetherealtech.storage.upload.InspectedUpload#contentType()}
     *                    rather than from whatever the uploader declared
     */
    void put(ObjectKey key, byte[] bytes, String contentType);

    /**
     * The same, from a stream whose length is already known.
     *
     * <p>The length is not optional and cannot be discovered: an S3 {@code PUT} needs a
     * {@code Content-Length} up front, so an implementation given a stream of unknown length would
     * have to buffer the whole thing — which is the case this overload exists to avoid. A caller
     * that does not know the length has bytes, and should use the other method.
     *
     * <p>The stream is read once and NOT closed; closing what you opened stays the caller's, since
     * only the caller knows whether it wraps something reusable.
     */
    void put(ObjectKey key, InputStream stream, long length, String contentType);

    /**
     * The bytes stored under {@code key}, for the SERVER's own use — embedding an asset in a
     * document it generates, or serving a stable URL a crawler will cache.
     *
     * <p>Screens should not call this. Everywhere a browser needs the object, {@link #presignGet}
     * is the answer: it keeps the bytes out of the API's responses entirely.
     *
     * @throws ObjectNotFoundException when nothing is stored under that key
     */
    StoredObject get(ObjectKey key);

    /**
     * Whether anything is stored under {@code key}. A {@code HEAD}, not a {@code GET} — the answer
     * is one bit and the object can be megabytes.
     *
     * <p>Worth its round trip only for a DERIVED key, where nothing in the database records whether
     * an object was ever written. A key RECORDED on a row is there by construction, and asking is
     * one request per image to render a gallery.
     */
    boolean exists(ObjectKey key);

    /** Removes the object. Succeeds quietly if it is already gone, so a retry is safe. */
    void delete(ObjectKey key);

    /**
     * A URL a browser can fetch the object from directly, valid for {@code ttl}.
     *
     * <p><b>The URL is a bearer token.</b> It carries its own signature, so for its lifetime anyone
     * holding it reads that object with no session and no ownership check. Mint it only after those
     * checks have passed, and keep the ttl short; that is the whole of the protection.
     */
    URI presignGet(ObjectKey key, Duration ttl);

    /**
     * The same, for the implementation's own configured lifetime.
     *
     * <p>Here because almost every call site wants the deployment's answer rather than a number of
     * its own, and threading one through from configuration to each call is how two call sites end
     * up disagreeing.
     */
    URI presignGet(ObjectKey key);
}
