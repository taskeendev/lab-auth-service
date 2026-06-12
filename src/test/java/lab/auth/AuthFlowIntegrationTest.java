package lab.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// เทสต์กับ Postgres "จริง" ที่ Testcontainers ปั้นขึ้นใน docker แล้วเก็บทิ้งเอง
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jwt.secret=integration-test-secret-must-be-long-42",
            "app.admin-email=admin@test.local"
        })
@Testcontainers
class AuthFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String login(String username, String password) {
        ResponseEntity<Map> res = rest.postForEntity(
                "/api/auth/login", Map.of("username", username, "password", password), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) res.getBody().get("accessToken");
    }

    @Test
    void fullAuthFlow() {
        // register สำเร็จ + ไม่มี hash หลุดใน response
        ResponseEntity<Map> created = rest.postForEntity("/api/auth/register",
                Map.of("email", "bob@test.local", "username", "bob", "password", "longenough1"),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("role", "USER").doesNotContainKey("passwordHash");

        // email ซ้ำ → 409
        ResponseEntity<Map> dup = rest.postForEntity("/api/auth/register",
                Map.of("email", "bob@test.local", "username", "bob2", "password", "longenough1"),
                Map.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // validation พัง → 400 + field errors
        ResponseEntity<Map> bad = rest.postForEntity("/api/auth/register",
                Map.of("email", "nope", "username", "x", "password", "s"), Map.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) bad.getBody().get("errors");
        assertThat(errors).containsKeys("email", "username", "password");

        // รหัสผิด → 401
        ResponseEntity<Map> wrong = rest.postForEntity("/api/auth/login",
                Map.of("username", "bob", "password", "wrong-password"), Map.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // login ถูก → token ใช้เรียก /me ได้
        String bobToken = login("bob", "longenough1");
        ResponseEntity<Map> me = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(bearer(bobToken)), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("username", "bob");

        // ไม่มี token → 401
        assertThat(rest.getForEntity("/api/users/me", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // USER เรียกเขต admin → 403
        ResponseEntity<Map> forbidden = rest.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(bearer(bobToken)), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // อีเมลตรง ADMIN_EMAIL → role ADMIN + เข้าเขต admin ได้
        ResponseEntity<Map> admin = rest.postForEntity("/api/auth/register",
                Map.of("email", "admin@test.local", "username", "root", "password", "longenough1"),
                Map.class);
        assertThat(admin.getBody()).containsEntry("role", "ADMIN");
        String adminToken = login("root", "longenough1");
        ResponseEntity<Map[]> list = rest.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)), Map[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().length).isGreaterThanOrEqualTo(2);

        // เปลี่ยนรหัส: รหัสเดิมผิด → 401, ถูก → 204 แล้วรหัสเก่าใช้ไม่ได้/ใหม่ใช้ได้
        ResponseEntity<Map> badChange = rest.exchange("/api/users/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "nope", "newPassword", "evenlonger22"),
                        bearer(bobToken)), Map.class);
        assertThat(badChange.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Void> changed = rest.exchange("/api/users/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "longenough1", "newPassword", "evenlonger22"),
                        bearer(bobToken)), Void.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(rest.postForEntity("/api/auth/login",
                Map.of("username", "bob", "password", "longenough1"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        login("bob", "evenlonger22");
    }

    private static String refreshCookieOf(ResponseEntity<?> response) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token="))
                .map(c -> c.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private ResponseEntity<Map> refreshWith(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(headers), Map.class);
    }

    @Test
    void refreshRotationAndLogout() {
        rest.postForEntity("/api/auth/register",
                Map.of("email", "carol@test.local", "username", "carol", "password", "longenough1"),
                Map.class);
        ResponseEntity<Map> loginRes = rest.postForEntity("/api/auth/login",
                Map.of("username", "carol", "password", "longenough1"), Map.class);

        // login ต้องแนบ refresh cookie แบบ HttpOnly
        String cookie1 = refreshCookieOf(loginRes);
        assertThat(loginRes.getHeaders().get(HttpHeaders.SET_COOKIE).get(0)).contains("HttpOnly");

        // refresh สำเร็จ → access ใหม่ + cookie ใหม่ (rotation)
        ResponseEntity<Map> refreshed = refreshWith(cookie1);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody()).containsKey("accessToken");
        String cookie2 = refreshCookieOf(refreshed);
        assertThat(cookie2).isNotEqualTo(cookie1);

        // เอา cookie เก่า (ถูกหมุนทิ้งแล้ว) มาใช้ซ้ำ = ฉากขโมย → 401
        assertThat(refreshWith(cookie1).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // ตรวจจับ reuse แล้วต้องเผาทุก session: cookie ใหม่ที่เพิ่งออกก็ใช้ไม่ได้แล้ว
        assertThat(refreshWith(cookie2).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // login รอบใหม่ → logout → refresh ด้วย cookie นั้นไม่ได้อีก
        ResponseEntity<Map> again = rest.postForEntity("/api/auth/login",
                Map.of("username", "carol", "password", "longenough1"), Map.class);
        String cookie3 = refreshCookieOf(again);
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.add(HttpHeaders.COOKIE, cookie3);
        ResponseEntity<Void> logout = rest.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(logoutHeaders), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(refreshWith(cookie3).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
