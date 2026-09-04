package net.aetherealtech.storage.s3;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.headRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import net.aetherealtech.storage.ObjectKey;
import net.aetherealtech.storage.ObjectNotFoundException;
import net.aetherealtech.storage.StorageAccessException;
import net.aetherealtech.storage.StorageUnavailableException;
import net.aetherealtech.storage.StoredObject;

/**
 * Asserts THE WIRE — the exact request the AWS SDK puts on the socket, and the exact way each of the
 * store's answers becomes either a value or one of this library's four exceptions — never a round
 * trip through a store that merely echoes what was written. See this module's pom and CLAUDE.md for
 * why: a test that wrote with {@link S3ObjectStorage} and read back with the same instance would
 * prove the two agree with each other, and nothing about what Hetzner will actually accept.
 */
class S3ObjectStorageTest {

    private static final String BUCKET = "kapar-photos";

    // Deliberately has a slash, so every assertion on a request line also proves a multi-segment key
    // survives addressing untouched.
    private static final String KEY = "listings/42/photo.jpg";

    private static final String REGION = "eu-central-1";

    @RegisterExtension
    private final WireMockExtension store =
            WireMockExtension.newInstance()
                    .options(WireMockConfiguration.options().dynamicPort())
                    .build();

    // ---- addressing ----

    @Test
    @DisplayName("virtual-hosted addressing puts the bucket in the Host header, never the path")
    void virtualHostedAddressingSendsTheBucketAsAHostPrefix() {
        store.stubFor(put(urlEqualTo("/" + KEY)).willReturn(aResponse().withStatus(200)));

        try (S3ObjectStorage storage = storage(false)) {
            storage.put(ObjectKey.of(KEY), body(), "text/plain");
        }

        // *.localhost resolving to loopback (RFC 6761) is what lets the SDK actually reach WireMock
        // at this rewritten host; see the report on this test for the fallback if it ever does not.
        final LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo("/" + KEY);
        assertThat(request.getHeader("Host")).startsWith(BUCKET + ".");
    }

    @Test
    @DisplayName("path-style addressing puts the bucket in the path; the Host carries no bucket")
    void pathStyleAddressingSendsTheBucketInThePath() {
        store.stubFor(
                put(urlEqualTo("/" + BUCKET + "/" + KEY)).willReturn(aResponse().withStatus(200)));

        try (S3ObjectStorage storage = storage(true)) {
            storage.put(ObjectKey.of(KEY), body(), "text/plain");
        }

        final LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo("/" + BUCKET + "/" + KEY);
        assertThat(request.getHeader("Host")).doesNotContain(BUCKET);
    }

    // ---- put ----

    @Test
    void putSendsTheBodyByteForByteAndTheExactContentType() {
        store.stubFor(put(anyUrl()).willReturn(aResponse().withStatus(200)));
        // Cyrillic bytes prove the body reaches the wire unmangled, not just ASCII bytes that would
        // survive almost any encoding bug.
        final byte[] bytes = "Здраво, Капар!".getBytes(StandardCharsets.UTF_8);

        try (S3ObjectStorage storage = storage(false)) {
            // No charset parameter: the JDK's own HttpURLConnection normalises a charset token's
            // case when it parses and re-serialises a Content-Type header, which would make this
            // assertion about the ADAPTER fail on a rewrite performed two layers below it instead.
            storage.put(ObjectKey.of(KEY), bytes, "image/jpeg");
        }

        final LoggedRequest request = onlyRequest();
        assertThat(request.getBody()).isEqualTo(bytes);
        assertThat(request.getHeader("Content-Type")).isEqualTo("image/jpeg");
    }

    @Test
    void putFromAStreamSendsTheSameBodyWithTheCorrectContentLength() {
        store.stubFor(put(anyUrl()).willReturn(aResponse().withStatus(200)));
        final byte[] bytes = "streamed onto the wire".getBytes(StandardCharsets.UTF_8);

        try (S3ObjectStorage storage = storage(false)) {
            storage.put(
                    ObjectKey.of(KEY),
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    "application/octet-stream");
        }

        final LoggedRequest request = onlyRequest();
        assertThat(request.getBody()).isEqualTo(bytes);
        assertThat(request.getHeader("Content-Length")).isEqualTo(String.valueOf(bytes.length));
    }

    // ---- get ----

    @Test
    void getReturnsTheStoredBytesAndTheStoredContentType() {
        final byte[] bytes = "the stored bytes".getBytes(StandardCharsets.UTF_8);
        store.stubFor(
                get(urlEqualTo("/" + KEY))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "image/jpeg")
                                        .withBody(bytes)));

        try (S3ObjectStorage storage = storage(false)) {
            final StoredObject stored = storage.get(ObjectKey.of(KEY));
            assertThat(stored.bytes()).isEqualTo(bytes);
            assertThat(stored.contentType()).isEqualTo("image/jpeg");
        }
    }

    // ---- exists ----

    @Test
    void existsIssuesAHeadAndAnswersTrueOnTwoHundred() {
        store.stubFor(head(urlEqualTo("/" + KEY)).willReturn(aResponse().withStatus(200)));

        try (S3ObjectStorage storage = storage(false)) {
            assertThat(storage.exists(ObjectKey.of(KEY))).isTrue();
        }

        store.verify(headRequestedFor(urlEqualTo("/" + KEY)));
    }

    // ---- delete ----

    @Test
    void deleteIssuesADeleteAndSucceedsOnNoContent() {
        store.stubFor(delete(urlEqualTo("/" + KEY)).willReturn(aResponse().withStatus(204)));

        try (S3ObjectStorage storage = storage(false)) {
            storage.delete(ObjectKey.of(KEY));
        }

        store.verify(deleteRequestedFor(urlEqualTo("/" + KEY)));
    }

    @Test
    @DisplayName("deleting a key that is not there succeeds quietly, so a retry is safe")
    void deletingAMissingKeySucceedsQuietly() {
        store.stubFor(
                delete(urlEqualTo("/" + KEY))
                        .willReturn(aResponse().withStatus(404).withBody(noSuchKeyXml())));

        try (S3ObjectStorage storage = storage(false)) {
            assertThatCode(() -> storage.delete(ObjectKey.of(KEY))).doesNotThrowAnyException();
        }
    }

    // ---- error mapping ----

    @Test
    @DisplayName("NoSuchKey's XML error on get becomes ObjectNotFoundException, carrying the key")
    void getOnAMissingKeyThrowsObjectNotFoundException() {
        store.stubFor(
                get(urlEqualTo("/" + KEY))
                        .willReturn(
                                aResponse()
                                        .withStatus(404)
                                        .withHeader("Content-Type", "application/xml")
                                        .withBody(noSuchKeyXml())));

        try (S3ObjectStorage storage = storage(false)) {
            final ObjectKey key = ObjectKey.of(KEY);
            final ObjectNotFoundException exception =
                    catchThrowableOfType(ObjectNotFoundException.class, () -> storage.get(key));
            assertThat(exception.key()).isEqualTo(key);
        }
    }

    @Test
    @DisplayName("a HEAD's bare 404 also makes exists() answer false, not throw")
    void existsOnAMissingKeyAnswersFalseRatherThanThrowing() {
        // A real HTTP HEAD response carries no body (RFC 9110 §9.3.2), so this is what a store's HEAD
        // answer to a missing key actually looks like. Measured, not assumed: the SDK's generated
        // unmarshaller for HeadObject synthesizes a typed NoSuchKeyException for ANY 404 regardless
        // of body content, because that operation is documented to have no other error — so this
        // exercises the SAME catch(NoSuchKeyException) branch as the get() test above, just reached a
        // different way. See getOnABareFourOhFourAlsoThrowsObjectNotFoundException for the operation
        // that actually reaches the generic S3Exception branch on a 404.
        store.stubFor(head(urlEqualTo("/" + KEY)).willReturn(aResponse().withStatus(404)));

        try (S3ObjectStorage storage = storage(false)) {
            assertThat(storage.exists(ObjectKey.of(KEY))).isFalse();
        }
    }

    @Test
    @DisplayName("a bare 404 GET with no parseable body still becomes ObjectNotFoundException")
    void getOnABareFourOhFourAlsoThrowsObjectNotFoundException() {
        // Unlike HeadObject, GetObject's modeled errors are not limited to NoSuchKey, so with no
        // body to read a code from, the SDK cannot default to the typed exception the way it does
        // for exists() above — this is what actually reaches the plain-S3Exception-with-404 branch
        // of the mapping, distinct from the NoSuchKeyException branch every other 404 test exercises.
        store.stubFor(get(urlEqualTo("/" + KEY)).willReturn(aResponse().withStatus(404)));

        try (S3ObjectStorage storage = storage(false)) {
            final ObjectKey key = ObjectKey.of(KEY);
            final ObjectNotFoundException exception =
                    catchThrowableOfType(ObjectNotFoundException.class, () -> storage.get(key));
            assertThat(exception.key()).isEqualTo(key);
        }
    }

    @Test
    @DisplayName("403 AccessDenied becomes StorageAccessException carrying the status and error code")
    void accessDeniedBecomesStorageAccessException() {
        store.stubFor(
                put(urlEqualTo("/" + KEY))
                        .willReturn(
                                aResponse()
                                        .withStatus(403)
                                        .withHeader("Content-Type", "application/xml")
                                        .withBody(errorXml("AccessDenied", "Access Denied"))));

        try (S3ObjectStorage storage = storage(false)) {
            final ObjectKey key = ObjectKey.of(KEY);
            final byte[] bytes = body();
            final StorageAccessException exception =
                    catchThrowableOfType(
                            StorageAccessException.class,
                            () -> storage.put(key, bytes, "text/plain"));
            assertThat(exception.statusCode()).isEqualTo(403);
            assertThat(exception.errorCode()).isEqualTo("AccessDenied");
        }
    }

    @Test
    @DisplayName("500 becomes StorageAccessException carrying status 500")
    void aServerErrorBecomesStorageAccessException() {
        store.stubFor(
                put(urlEqualTo("/" + KEY))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", "application/xml")
                                        .withBody(errorXml("InternalError", "We encountered an error"))));

        try (S3ObjectStorage storage = storage(false)) {
            final ObjectKey key = ObjectKey.of(KEY);
            final byte[] bytes = body();
            final StorageAccessException exception =
                    catchThrowableOfType(
                            StorageAccessException.class,
                            () -> storage.put(key, bytes, "text/plain"));
            assertThat(exception.statusCode()).isEqualTo(500);
        }
    }

    @Test
    @DisplayName("a status with no parseable error body still maps, with a null error code")
    void aResponseWithNoBodyHasNoErrorCode() {
        store.stubFor(put(urlEqualTo("/" + KEY)).willReturn(aResponse().withStatus(503)));

        try (S3ObjectStorage storage = storage(false)) {
            final ObjectKey key = ObjectKey.of(KEY);
            final byte[] bytes = body();
            final StorageAccessException exception =
                    catchThrowableOfType(
                            StorageAccessException.class,
                            () -> storage.put(key, bytes, "text/plain"));
            assertThat(exception.statusCode()).isEqualTo(503);
            assertThat(exception.errorCode()).isNull();
        }
    }

    @Test
    @DisplayName("a connection refused becomes StorageUnavailableException naming the endpoint")
    void aConnectionFailureBecomesStorageUnavailableException() throws IOException {
        final int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        final URI endpoint = URI.create("http://localhost:" + deadPort);

        try (S3ObjectStorage storage =
                new S3ObjectStorage(S3Config.of(endpoint, REGION, BUCKET, "AKIAEXAMPLE", "secret"))) {
            final ObjectKey key = ObjectKey.of(KEY);
            assertThatThrownBy(() -> storage.exists(key))
                    .isInstanceOf(StorageUnavailableException.class)
                    .hasMessageContaining(endpoint.toString());
        }
    }

    // ---- retries ----

    @Test
    @DisplayName("a retryable status is attempted exactly once — retries are off")
    void aRetryableStatusIsAttemptedExactlyOnce() {
        // 503 is precisely what the SDK's default strategy WOULD retry, several times and over
        // several seconds, inside a call a request thread is waiting on. Asserted on the wire
        // because that is the only place the difference shows: the exception a caller sees is the
        // same either way.
        store.stubFor(put(urlEqualTo("/" + KEY)).willReturn(aResponse().withStatus(503)));

        try (S3ObjectStorage storage = storage(false)) {
            final ObjectKey key = ObjectKey.of(KEY);
            final byte[] bytes = body();
            assertThatThrownBy(() -> storage.put(key, bytes, "text/plain"))
                    .isInstanceOf(StorageAccessException.class);
        }

        onlyRequest();
    }

    // ---- presigning ----

    @Test
    @DisplayName("a presigned GET carries the addressing-appropriate host and every SigV4 parameter")
    void presignedUrlCarriesSigV4Parameters() {
        try (S3ObjectStorage storage = storage(false)) {
            final URI url = storage.presignGet(ObjectKey.of(KEY), Duration.ofMinutes(5));

            assertThat(url.getHost()).startsWith(BUCKET + ".");
            assertThat(url.getPath()).isEqualTo("/" + KEY);

            final Map<String, String> query = queryParams(url);
            assertThat(query).containsEntry("X-Amz-Expires", "300");
            assertThat(query).containsEntry("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
            assertThat(query).containsKeys(
                    "X-Amz-Credential", "X-Amz-Date", "X-Amz-SignedHeaders", "X-Amz-Signature");
        }

        // Presigning is pure local computation; it must never touch the network.
        assertThat(store.findAll(anyRequestedFor(ANY))).isEmpty();
    }

    @Test
    @DisplayName("a path-style presigned GET carries the bucket in the path, not the host")
    void pathStylePresignedUrl() {
        try (S3ObjectStorage storage = storage(true)) {
            final URI url = storage.presignGet(ObjectKey.of(KEY), Duration.ofMinutes(5));

            assertThat(url.getPath()).isEqualTo("/" + BUCKET + "/" + KEY);
            assertThat(url.getHost()).doesNotContain(BUCKET);
        }
    }

    @Test
    @DisplayName("presignGet(key) with no explicit ttl uses the config's own presignTtl")
    void presignGetWithNoTtlUsesTheConfiguredTtl() {
        final S3Config config =
                S3Config.of(
                                URI.create(store.baseUrl()),
                                REGION,
                                BUCKET,
                                "AKIAEXAMPLE",
                                "secret")
                        .withPresignTtl(Duration.ofHours(2));

        try (S3ObjectStorage storage = new S3ObjectStorage(config)) {
            final URI url = storage.presignGet(ObjectKey.of(KEY));
            assertThat(queryParams(url))
                    .containsEntry("X-Amz-Expires", String.valueOf(Duration.ofHours(2).toSeconds()));
        }
    }

    // ---- lifecycle ----

    @Test
    void closeIsIdempotentAndDoesNotThrow() {
        final S3ObjectStorage storage = storage(false);
        assertThatCode(
                        () -> {
                            storage.close();
                            storage.close();
                        })
                .doesNotThrowAnyException();
    }

    private S3ObjectStorage storage(final boolean pathStyle) {
        return new S3ObjectStorage(
                S3Config.of(URI.create(store.baseUrl()), REGION, BUCKET, "AKIAEXAMPLE", "secret")
                        .withPathStyle(pathStyle));
    }

    private static byte[] body() {
        return "hello".getBytes(StandardCharsets.UTF_8);
    }

    private LoggedRequest onlyRequest() {
        final List<LoggedRequest> requests = store.findAll(anyRequestedFor(ANY));
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    private static String noSuchKeyXml() {
        return errorXml("NoSuchKey", "The specified key does not exist.");
    }

    private static String errorXml(final String code, final String message) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Error><Code>"
                + code
                + "</Code><Message>"
                + message
                + "</Message><Key>"
                + KEY
                + "</Key></Error>";
    }

    private static Map<String, String> queryParams(final URI url) {
        return Arrays.stream(url.getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(
                        Collectors.toMap(
                                kv -> URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                                kv -> URLDecoder.decode(kv[1], StandardCharsets.UTF_8)));
    }
}
