package net.aetherealtech.storage.s3;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

/**
 * Pins the pom's exclusions on this module's own classpath rather than trusting a comment.
 *
 * <p>The AWS SDK ships more than one sync HTTP client and fails at startup the moment two land on
 * the same classpath, so exactly one of them may be reachable here. A future SDK bump that
 * reintroduces {@code apache-client} or {@code apache5-client} — the exclusion in
 * {@code storage-s3/pom.xml} names an artifact ID, and an SDK release that renames or splits one
 * would sail straight past it — fails THIS build rather than a consumer's.
 */
class S3HttpClientClasspathTest {

    @Test
    void apacheClientIsExcluded() {
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName("software.amazon.awssdk.http.apache.ApacheHttpClient"));
    }

    @Test
    void apache5ClientIsExcluded() {
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName("software.amazon.awssdk.http.apache5.Apache5HttpClient"));
    }

    @Test
    void urlConnectionClientIsThePresentSyncClient() {
        assertThatCode(() -> Class.forName("software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient"))
                .doesNotThrowAnyException();
    }
}
