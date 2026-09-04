package net.aetherealtech.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryObjectStorageTest {

    private static final ObjectKey KEY = ObjectKey.of("listings/5/photo");

    @Nested
    class RoundTrip {

        @Test
        void putThenGetReturnsWhatWasStored() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();

            storage.put(KEY, new byte[] {1, 2, 3}, "image/png");

            final StoredObject stored = storage.get(KEY);
            assertThat(stored.bytes()).containsExactly(1, 2, 3);
            assertThat(stored.contentType()).isEqualTo("image/png");
        }

        @Test
        void existsIsTrueOnceStored() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();

            storage.put(KEY, new byte[] {1}, "image/png");

            assertThat(storage.exists(KEY)).isTrue();
        }

        @Test
        void existsIsFalseBeforeAnythingIsStored() {
            assertThat(new InMemoryObjectStorage().exists(KEY)).isFalse();
        }

        @Test
        void deleteRemovesTheObject() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            storage.put(KEY, new byte[] {1}, "image/png");

            storage.delete(KEY);

            assertThat(storage.exists(KEY)).isFalse();
        }

        @Test
        void getOnAMissingKeyThrowsCarryingTheKey() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();

            assertThatThrownBy(() -> storage.get(KEY))
                    .isInstanceOf(ObjectNotFoundException.class)
                    .satisfies(ex -> assertThat(((ObjectNotFoundException) ex).key()).isEqualTo(KEY));
        }

        @Test
        void deleteOfAMissingKeyIsQuiet() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();

            assertThatCode(() -> storage.delete(KEY)).doesNotThrowAnyException();
        }

        @Test
        void putReplacesWhatWasThereBefore() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            storage.put(KEY, new byte[] {1}, "image/png");

            storage.put(KEY, new byte[] {9, 9}, "image/jpeg");

            final StoredObject stored = storage.get(KEY);
            assertThat(stored.bytes()).containsExactly(9, 9);
            assertThat(stored.contentType()).isEqualTo("image/jpeg");
        }
    }

    @Nested
    class StreamOverload {

        @Test
        @DisplayName("only the declared length is stored, even when the stream carries more")
        void storesExactlyTheDeclaredLength() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            final ByteArrayInputStream stream = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});

            storage.put(KEY, stream, 3, "image/png");

            assertThat(storage.get(KEY).bytes()).containsExactly(1, 2, 3);
        }

        @Test
        void aStreamThatEndsEarlyThrows() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            final ByteArrayInputStream stream = new ByteArrayInputStream(new byte[] {1, 2});

            assertThatThrownBy(() -> storage.put(KEY, stream, 5, "image/png"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class PresignGet {

        @Test
        void theNoArgOverloadUsesTheConstructedTtl() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage(Duration.ofMinutes(15));

            assertThat(storage.presignGet(KEY).toString()).contains("X-Amz-Expires=900");
        }

        @Test
        void theTwoArgOverloadUsesTheGivenTtl() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();

            assertThat(storage.presignGet(KEY, Duration.ofSeconds(30)).toString())
                    .contains("X-Amz-Expires=30");
        }

        @Test
        void isStableForTheSameKeyAndTtl() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();

            final URI first = storage.presignGet(KEY, Duration.ofMinutes(5));
            final URI second = storage.presignGet(KEY, Duration.ofMinutes(5));

            assertThat(first).isEqualTo(second);
        }

        @Test
        void containsASignatureParameter() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();

            assertThat(storage.presignGet(KEY).toString()).contains("X-Amz-Signature");
        }

        @Test
        void startsWithThePresignHost() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();

            assertThat(storage.presignGet(KEY).toString()).startsWith(InMemoryObjectStorage.PRESIGN_HOST);
        }

        @Test
        @DisplayName("a space or a '+' inside a segment is percent-encoded, but the '/' separators survive")
        void segmentCharactersAreEncodedButSlashesSurvive() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            final ObjectKey key = ObjectKey.of("photos/my file+two.png");

            final String uri = storage.presignGet(key).toString();

            assertThat(uri).contains("/photos/my%20file%2Btwo.png");
        }

        @Test
        void nullTtlInTheConstructorIsRejected() {
            assertThatThrownBy(() -> new InMemoryObjectStorage(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("presignTtl");
        }
    }

    @Nested
    class Inspection {

        @Test
        void keysReturnsEveryKeyCurrentlyHeld() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            storage.put(KEY, new byte[] {1}, "image/png");
            storage.put(ObjectKey.of("listings/6/photo"), new byte[] {2}, "image/png");

            assertThat(storage.keys()).containsExactlyInAnyOrder(KEY, ObjectKey.of("listings/6/photo"));
        }

        @Test
        void keysIsUnmodifiable() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            storage.put(KEY, new byte[] {1}, "image/png");

            assertThatThrownBy(() -> storage.keys().add(ObjectKey.of("other")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void sizeCountsTheObjectsHeld() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            storage.put(KEY, new byte[] {1}, "image/png");
            storage.put(ObjectKey.of("listings/6/photo"), new byte[] {2}, "image/png");

            assertThat(storage.size()).isEqualTo(2);
        }

        @Test
        void clearForgetsEverything() {
            final InMemoryObjectStorage storage = new InMemoryObjectStorage();
            storage.put(KEY, new byte[] {1}, "image/png");

            storage.clear();

            assertThat(storage.size()).isZero();
            assertThat(storage.exists(KEY)).isFalse();
        }
    }
}
