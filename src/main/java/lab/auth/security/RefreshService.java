package lab.auth.security;

import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lab.auth.error.UnauthorizedException;
import lab.auth.user.RefreshToken;
import lab.auth.user.RefreshTokenRepository;
import lab.auth.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RefreshService {

    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final Duration ttl;

    public RefreshService(
            RefreshTokenRepository tokens,
            @Value("${auth.refresh-ttl-days}") long ttlDays) {
        this.tokens = tokens;
        this.ttl = Duration.ofDays(ttlDays);
    }

    // คืน token ดิบให้ client — ใน DB เก็บเฉพาะ SHA-256 (DB หลุดก็ใช้ token ไม่ได้)
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(sha256(raw), user, Instant.now().plus(ttl)));
        return raw;
    }

    // ใช้ token หนึ่งครั้ง (rotation): ของดี → revoke แล้วคืน user ให้ไปออกชุดใหม่
    // ของที่ถูก revoke ไปแล้วโผล่มาอีก = สัญญาณขโมย → เผาทุก session ของ user นั้น
    // dontRollbackOn สำคัญมาก: เราโยน 401 หลังเผา session — ถ้าปล่อย rollback ปกติ
    // การเผาจะถูกย้อนกลับหมด = ตรวจจับขโมยได้แต่ลงโทษไม่สำเร็จ (เทสต์จับบั๊กนี้ได้จริง)
    @Transactional(dontRollbackOn = UnauthorizedException.class)
    public User consume(String rawToken) {
        RefreshToken token = tokens.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new UnauthorizedException("invalid refresh token"));

        if (token.isRevoked()) {
            log.warn("refresh token reuse detected for user {} — revoking all sessions",
                    token.getUser().getUsername());
            revokeAllFor(token.getUser());
            throw new UnauthorizedException("invalid refresh token");
        }
        if (token.isExpired()) {
            throw new UnauthorizedException("refresh token expired");
        }
        token.revoke();
        tokens.save(token);
        return token.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(sha256(rawToken)).ifPresent(t -> {
            t.revoke();
            tokens.save(t);
        });
    }

    @Transactional
    public void revokeAllFor(User user) {
        tokens.findByUserAndRevokedAtIsNull(user).forEach(t -> {
            t.revoke();
            tokens.save(t);
        });
    }

    public Duration ttl() {
        return ttl;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }
}
