package lab.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lab.common.security.JwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// ดัก Authorization: Bearer <token> — token ดี = ใส่ตัวตนลง SecurityContext, ไม่ดี = ปล่อยผ่านแบบนิรนาม
// (คนนิรนามที่เข้า path หวงห้ามจะโดน entry point ตอบ 401 เอง — filter ไม่ต้องตัดสินโทษ)
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtVerifier jwtVerifier;

    public JwtAuthFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtVerifier.verify(header.substring(7));
                var authority = new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class));
                var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException ignored) {
                // token เสีย = เหมือนไม่มี token — ไม่ตั้งตัวตน
            }
        }
        chain.doFilter(request, response);
    }
}
