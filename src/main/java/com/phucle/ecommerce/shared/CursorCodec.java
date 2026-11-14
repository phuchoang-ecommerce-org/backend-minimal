package com.phucle.ecommerce.shared;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class CursorCodec {
    public record TimedBoundary(Instant createdAt, UUID id) {}
    private final byte[] key;
    public CursorCodec(@Value("${marketplace.cursor.mac-secret:}") String secret) {
        key = secret.getBytes(StandardCharsets.UTF_8);
        requireKey();
    }
    public String encode(String resource, String filters, UUID boundary) {
        requireKey();
        String payload = "1|" + resource + "|" + fingerprint(filters) + "|" + boundary;
        return sign(payload);
    }
    public UUID decode(String cursor, String resource, String filters) {
        requireKey();
        try {
            String[] values = fields(verifiedPayload(cursor).bytes());
            requireContext(values, 4, "1", resource, filters);
            return UUID.fromString(values[3]);
        } catch (RuntimeException ex) { throw new InvalidCursorException(); }
    }
    public String encodeTimed(String resource, String filters, Instant createdAt, UUID boundary) {
        requireKey();
        String payload = "2|" + resource + "|" + fingerprint(filters) + "|" + createdAt + "|" + boundary;
        return sign(payload);
    }
    public TimedBoundary decodeTimed(String cursor, String resource, String filters) {
        requireKey();
        try {
            Payload payload = verifiedPayload(cursor);
            if (!base64(payload.bytes()).equals(payload.body())) throw new IllegalArgumentException();
            String[] values = fields(payload.bytes());
            requireContext(values, 5, "2", resource, filters);
            return new TimedBoundary(Instant.parse(values[3]), UUID.fromString(values[4]));
        } catch (RuntimeException ex) { throw new InvalidCursorException(); }
    }
    private String sign(String payload) {
        String body = base64(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + base64(mac(body));
    }
    private Payload verifiedPayload(String cursor) {
        if (cursor == null || cursor.length() > 2048) throw new IllegalArgumentException();
        String[] parts = cursor.split("\\.", -1);
        if (parts.length != 2) throw new IllegalArgumentException();
        byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
        if (!MessageDigest.isEqual(mac(parts[0]), signature) || !base64(signature).equals(parts[1]))
            throw new IllegalArgumentException();
        return new Payload(parts[0], Base64.getUrlDecoder().decode(parts[0]));
    }
    private String[] fields(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).split("\\|", -1);
    }
    private void requireContext(String[] fields, int count, String version, String resource, String filters) {
        if (fields.length != count || !version.equals(fields[0]) || !resource.equals(fields[1])
                || !fingerprint(filters).equals(fields[2])) throw new IllegalArgumentException();
    }
    private String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private record Payload(String body, byte[] bytes) {}
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
