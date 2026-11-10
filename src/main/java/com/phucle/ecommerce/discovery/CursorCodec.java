package com.phucle.ecommerce.discovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class CursorCodec {
    private final byte[] key;
    CursorCodec(@Value("${marketplace.cursor.mac-secret:}") String secret) {
        key = secret.getBytes(StandardCharsets.UTF_8);
        requireKey();
    }
    String encode(String resource, String filters, UUID boundary) {
        requireKey();
        String payload = "1|" + resource + "|" + fingerprint(filters) + "|" + boundary;
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(body));
    }
    UUID decode(String cursor, String resource, String filters) {
        requireKey();
        try {
            if (cursor == null || cursor.length() > 2048) throw new IllegalArgumentException();
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(mac(parts[0]), Base64.getUrlDecoder().decode(parts[1]))
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(parts[1])).equals(parts[1]))
                throw new IllegalArgumentException();
            String[] values = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != 4 || !"1".equals(values[0]) || !resource.equals(values[1]) || !fingerprint(filters).equals(values[2]))
                throw new IllegalArgumentException();
            return UUID.fromString(values[3]);
        } catch (RuntimeException ex) { throw new InvalidCursorException(); }
    }
    private void requireKey() {
        if (key.length < 32) throw new IllegalStateException("CURSOR_MAC_SECRET must be at least 32 UTF-8 bytes");
    }
    private byte[] mac(String body) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hmac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) { throw new IllegalStateException("Cursor MAC unavailable", ex); }
    }
    private String fingerprint(String filters) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(filters.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) { throw new IllegalStateException("Filter fingerprint unavailable", ex); }
    }
}
