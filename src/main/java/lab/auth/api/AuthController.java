package lab.auth.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lab.auth.error.UnauthorizedException;
import lab.auth.security.JwtService;
import lab.auth.security.RefreshService;
import lab.auth.user.User;
import lab.auth.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshService refreshService;
    private final boolean cookieSecure;

    public AuthController(
            UserService userService,
            JwtService jwtService,
            RefreshService refreshService,
            @Value("${auth.cookie-secure}") boolean cookieSecure) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshService = refreshService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(
                userService.register(request.email(), request.username(), request.password()));
    }

    @PostMapping("/login")
    public TokenResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        User user = userService.authenticate(request.username(), request.password());
        return issueTokens(user, response);
    }

    // refresh มากับ HttpOnly cookie — JS ฝั่ง browser แตะไม่ได้เลย (กัน XSS ขโมย)
    @PostMapping("/refresh")
    public TokenResponse refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new UnauthorizedException("missing refresh token");
        }
        User user = refreshService.consume(refreshToken);   // rotation เกิดในนี้
        return issueTokens(user, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            refreshService.revoke(refreshToken);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie().toString());
    }

    private TokenResponse issueTokens(User user, HttpServletResponse response) {
        String refresh = refreshService.issue(user);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refresh).toString());
        String access = jwtService.issue(user.getUsername(), user.getRole());
        return TokenResponse.bearer(access, jwtService.ttlSeconds());
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)        // เปิดเป็น true ตอนอยู่หลัง TLS (env)
                .path("/api/auth")           // cookie วิ่งเฉพาะ endpoint ตระกูล auth
                .maxAge(refreshService.ttl())
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }
}
