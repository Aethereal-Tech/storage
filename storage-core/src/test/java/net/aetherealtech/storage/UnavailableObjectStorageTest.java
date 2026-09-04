package net.aetherealtech.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnavailableObjectStorageTest {

    private static final ObjectKey KEY = ObjectKey.of("listings/5/photo");

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    @DisplayName("every operation refuses, including exists and delete — that is the whole point")
    void everyOperationRefuses(final String name, final Consumer<ObjectStorage> operation) {
        final UnavailableObjectStorage storage = new UnavailableObjectStorage();

        assertThatThrownBy(() -> operation.accept(storage)).isInstanceOf(StorageUnavailableException.class);
    }

    private static Stream<Arguments> operations() {
        return Stream.of(
                Arguments.of("put(bytes)", (Consumer<ObjectStorage>) s -> s.put(KEY, new byte[] {1}, "image/png")),
                Arguments.of(
                        "put(stream)",
                        (Consumer<ObjectStorage>)
                                s -> s.put(KEY, new ByteArrayInputStream(new byte[] {1}), 1, "image/png")),
                Arguments.of("get", (Consumer<ObjectStorage>) s -> s.get(KEY)),
                Arguments.of("exists", (Consumer<ObjectStorage>) s -> s.exists(KEY)),
                Arguments.of("delete", (Consumer<ObjectStorage>) s -> s.delete(KEY)),
                Arguments.of("presignGet(key, ttl)", (Consumer<ObjectStorage>) s -> s.presignGet(KEY, Duration.ofMinutes(5))),
                Arguments.of("presignGet(key)", (Consumer<ObjectStorage>) s -> s.presignGet(KEY)));
    }

    @Nested
    class DefaultMessage {

        @Test
        @DisplayName("names both storage.s3.access-key and storage.s3.secret-key")
        void namesBothProperties() {
            assertThatThrownBy(() -> new UnavailableObjectStorage().exists(KEY))
                    .hasMessageContaining("storage.s3.access-key")
                    .hasMessageContaining("storage.s3.secret-key");
        }
    }

    @Nested
    class CustomMessage {

        @Test
        void isUsedVerbatim() {
            final UnavailableObjectStorage storage = new UnavailableObjectStorage("no bucket for you");

            assertThatThrownBy(() -> storage.exists(KEY)).hasMessage("no bucket for you");
        }

        @Test
        void nullMessageIsRejected() {
            assertThatThrownBy(() -> new UnavailableObjectStorage(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("message");
        }
    }
}
