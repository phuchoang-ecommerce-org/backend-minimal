package com.phucle.ecommerce.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorCodecTest {
    private final CursorCodec codec = new CursorCodec("test-only-cursor-mac-secret-at-least-32-bytes");

    @Test
    void simpleCursorPreservesResourceFilterAndBoundary() {
        UUID boundary = UUID.randomUUID();
        String cursor = codec.encode("products", "category=books", boundary);

        assertThat(codec.decode(cursor, "products", "category=books")).isEqualTo(boundary);
        assertThatThrownBy(() -> codec.decode(cursor, "products", "category=other"))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec.decode(cursor, "categories", "category=books"))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void timedCursorRejectsTamperingAndPreservesBoundary() {
        Instant createdAt = Instant.parse("2026-11-14T10:15:30Z");
        UUID boundary = UUID.randomUUID();
        String cursor = codec.encodeTimed("orders", "member", createdAt, boundary);

        assertThat(codec.decodeTimed(cursor, "orders", "member"))
                .isEqualTo(new CursorCodec.TimedBoundary(createdAt, boundary));
        assertThatThrownBy(() -> codec.decodeTimed(cursor + "x", "orders", "member"))
                .isInstanceOf(InvalidCursorException.class);
    }
}
