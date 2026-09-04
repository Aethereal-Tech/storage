package net.aetherealtech.storage.s3;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

import net.aetherealtech.storage.ObjectKey;
import net.aetherealtech.storage.ObjectNotFoundException;
import net.aetherealtech.storage.ObjectStorage;
import net.aetherealtech.storage.StorageAccessException;
import net.aetherealtech.storage.StorageUnavailableException;
import net.aetherealtech.storage.StoredObject;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * {@link ObjectStorage} against an S3-compatible store, over the AWS SDK.
 *
 * <p>The SDK is the SigV4 protocol client, not a commitment to AWS — see {@link S3Config#endpoint()}.
 * What THIS class owns is the mapping from the SDK's exception hierarchy to this library's four, done
 * in exactly one place so it cannot differ per method; see {@link #execute}.
 *
 * <p><strong>No retries, and a bounded call timeout.</strong> A caller sits in a request thread
 * waiting on this, so a refused connection or a slow store must fail fast with
 * {@link StorageUnavailableException} rather than being retried silently, for tens of seconds, inside
 * a library the caller does not control. Whether to retry is the caller's decision, not this one's.
 *
 * <p>Thread-safe: the underlying {@link S3Client} and {@link S3Presigner} are, and this class holds
 * no other state.
 */
public final class S3ObjectStorage implements ObjectStorage, AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(S3ObjectStorage.class.getName());

    // Bounds a single attempt with no retries behind it: long enough for a real store under normal
    // load, short enough that a caller waiting on this in a request thread gets an answer.
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(10);

    private final S3Config config;
    private final S3Client client;
    private final S3Presigner presigner;

    /**
     * Builds BOTH the {@link S3Client} and the {@link S3Presigner} from this one config, configured
     * identically — same endpoint, region, credentials and addressing style.
     *
     * <p>That is deliberate and is why there is no constructor taking two pre-built clients instead.
     * A SigV4 signature covers the host, so a presigner that disagreed with the client about
     * addressing style would mint URLs that fail to verify — and the error a browser shows for that
     * says nothing about addressing; it just fails to load the image. Building both from one config
     * is what makes the two disagreeing impossible.
     */
    public S3ObjectStorage(final S3Config config) {
        this.config = Objects.requireNonNull(config, "config must not be null");

        final AwsCredentialsProvider credentials =
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), config.secretKey()));
        final S3Configuration addressing =
                S3Configuration.builder()
                        .pathStyleAccessEnabled(config.pathStyle())
                        // PutObject requires a checksum per its own model, unconditionally — nothing
                        // below opts out of that. Left at its default, the SDK satisfies it by
                        // streaming the body as aws-chunked with a TRAILING CRC32, a wire feature an
                        // arbitrary S3-compatible store is not guaranteed to speak. Every put() here
                        // hands the SDK a length-known body already fully in hand — see
                        // ObjectStorage#put(ObjectKey, byte[], String) and the stream overload's
                        // Content-Length requirement — so with chunking off, the SDK computes the
                        // checksum up front instead and sends it as an ordinary header: the same
                        // request shape every store this library targets already has to support.
                        .chunkedEncodingEnabled(false)
                        .build();

        this.client =
                S3Client.builder()
                        .endpointOverride(config.endpoint())
                        .region(Region.of(config.region()))
                        .credentialsProvider(credentials)
                        .serviceConfiguration(addressing)
                        // Named explicitly rather than left to the classpath scan the SDK runs when
                        // no HTTP client is set. The pom excludes apache-client and netty-nio-client,
                        // so that scan would find exactly one candidate anyway — but naming this one
                        // removes the scan entirely, so a consumer who adds a different SDK HTTP
                        // client for their own reasons (a shared Netty event loop, say) cannot make
                        // it ambiguous which one THIS adapter uses.
                        .httpClient(UrlConnectionHttpClient.create())
                        // WHEN_REQUIRED, not the SDK's own default of WHEN_SUPPORTED: this governs
                        // checksums for operations that merely SUPPORT one (a GET's response
                        // validation, say), which this library never asked for and which is a second,
                        // independent way an opportunistic checksum could reach the wire beyond the
                        // PutObject case chunkedEncodingEnabled(false) above already handles.
                        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                        .overrideConfiguration(
                                ClientOverrideConfiguration.builder()
                                        // One attempt, no retries. `retryStrategy` rather than the
                                        // deprecated `retryPolicy(RetryPolicy.none())`: both say
                                        // zero retries, and only this one survives the SDK's move
                                        // off RetryPolicy.
                                        .retryStrategy(AwsRetryStrategy.doNotRetry())
                                        .apiCallTimeout(API_CALL_TIMEOUT)
                                        .apiCallAttemptTimeout(API_CALL_TIMEOUT)
                                        .build())
                        .build();
        this.presigner =
                S3Presigner.builder()
                        .endpointOverride(config.endpoint())
                        .region(Region.of(config.region()))
                        .credentialsProvider(credentials)
                        .serviceConfiguration(addressing)
                        .build();
    }

    @Override
    public void put(final ObjectKey key, final byte[] bytes, final String contentType) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        LOGGER.log(System.Logger.Level.DEBUG, "Storing {0} bytes at {1}", bytes.length, key);
        execute(
                key,
                () -> client.putObject(putRequest(key, contentType), RequestBody.fromBytes(bytes)));
    }

    @Override
    public void put(
            final ObjectKey key,
            final InputStream stream,
            final long length,
            final String contentType) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(stream, "stream must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        LOGGER.log(System.Logger.Level.DEBUG, "Storing {0} bytes (stream) at {1}", length, key);
        execute(
                key,
                () ->
                        client.putObject(
                                putRequest(key, contentType),
                                RequestBody.fromInputStream(stream, length)));
    }

    @Override
    public StoredObject get(final ObjectKey key) {
        Objects.requireNonNull(key, "key must not be null");
        LOGGER.log(System.Logger.Level.DEBUG, "Reading {0}", key);
        final ResponseBytes<GetObjectResponse> response =
                execute(
                        key,
                        () ->
                                client.getObjectAsBytes(
                                        GetObjectRequest.builder()
                                                .bucket(config.bucket())
                                                .key(key.value())
                                                .build()));
        return new StoredObject(response.asByteArray(), response.response().contentType());
    }

    @Override
    public boolean exists(final ObjectKey key) {
        Objects.requireNonNull(key, "key must not be null");
        LOGGER.log(System.Logger.Level.DEBUG, "Checking existence of {0}", key);
        try {
            execute(
                    key,
                    () ->
                            client.headObject(
                                    HeadObjectRequest.builder()
                                            .bucket(config.bucket())
                                            .key(key.value())
                                            .build()));
            return true;
        } catch (final ObjectNotFoundException e) {
            // The port's contract for exists(): answer false rather than propagate, so a caller
            // deciding whether to render a gallery image does not need a try/catch of its own.
            return false;
        }
    }

    @Override
    public void delete(final ObjectKey key) {
        Objects.requireNonNull(key, "key must not be null");
        LOGGER.log(System.Logger.Level.DEBUG, "Deleting {0}", key);
        try {
            execute(
                    key,
                    () ->
                            client.deleteObject(
                                    DeleteObjectRequest.builder()
                                            .bucket(config.bucket())
                                            .key(key.value())
                                            .build()));
        } catch (final ObjectNotFoundException e) {
            // Succeeds quietly per ObjectStorage#delete, so a retry after a partial failure is safe.
            // Real S3 already answers 204 for a missing key; some S3-compatible stores answer 404
            // instead, and both mean the same thing here: after this call, nothing is there.
        }
    }

    @Override
    public URI presignGet(final ObjectKey key, final Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        // Presigning is a local SigV4 computation over the request, the credentials and the clock —
        // it issues no request of its own, so nothing here can throw StorageAccessException or
        // StorageUnavailableException.
        final PresignedGetObjectRequest presigned =
                presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(ttl)
                                .getObjectRequest(
                                        GetObjectRequest.builder()
                                                .bucket(config.bucket())
                                                .key(key.value())
                                                .build())
                                .build());
        return URI.create(presigned.url().toString());
    }

    @Override
    public URI presignGet(final ObjectKey key) {
        return presignGet(key, config.presignTtl());
    }

    @Override
    public void close() {
        client.close();
        presigner.close();
    }

    private PutObjectRequest putRequest(final ObjectKey key, final String contentType) {
        return PutObjectRequest.builder()
                .bucket(config.bucket())
                .key(key.value())
                .contentType(contentType)
                .build();
    }

    /**
     * Routes every SDK call through one mapping, so it cannot differ per method:
     *
     * <ul>
     *   <li>{@link NoSuchKeyException} → {@link ObjectNotFoundException}.
     *   <li>a plain {@link S3Exception} with status 404 → {@link ObjectNotFoundException} too. Most
     *       404s the SDK can unmarshal come back as the typed exception above, but not every one
     *       does — a store's response the SDK cannot parse an error code from (an empty body, a
     *       proxy's own 404 page) falls back to the generic exception instead. Both mean the same
     *       thing to a caller: nothing is at that key.
     *   <li>any other {@link S3Exception} → {@link StorageAccessException}, carrying the status and
     *       error code, which is the only thing that tells a wrong credential apart from a missing
     *       bucket from a forbidding policy — see that class's javadoc on why a 403 is ambiguous by
     *       design.
     *   <li>{@link SdkClientException} — a connection refused, a DNS failure, a timeout — anything
     *       that never got an HTTP answer at all → {@link StorageUnavailableException}.
     * </ul>
     */
    private <T> T execute(final ObjectKey key, final Supplier<T> call) {
        try {
            return call.get();
        } catch (final NoSuchKeyException e) {
            throw new ObjectNotFoundException(key, e);
        } catch (final S3Exception e) {
            if (e.statusCode() == 404) {
                throw new ObjectNotFoundException(key, e);
            }
            throw storageAccessException(e);
        } catch (final SdkClientException e) {
            throw new StorageUnavailableException(
                    "Could not reach the object store at " + config.endpoint() + ": "
                            + e.getMessage(),
                    e);
        }
    }

    private static StorageAccessException storageAccessException(final S3Exception e) {
        final AwsErrorDetails details = e.awsErrorDetails();
        // awsErrorDetails() is null rather than empty when the response carried nothing the SDK could
        // parse as an S3 error body — a plain 500 from a proxy in front of the store, say.
        final String errorCode = details != null ? details.errorCode() : null;
        return new StorageAccessException(
                "S3 request failed with status "
                        + e.statusCode()
                        + (errorCode != null ? " (" + errorCode + ")" : ""),
                e.statusCode(),
                errorCode,
                e);
    }
}
