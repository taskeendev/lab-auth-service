package lab.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity   // เปิดใช้ @PreAuthorize ราย endpoint
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
            throws Exception {
        return http
                // API ล้วน ไม่ใช้ cookie auth ในภาคนี้ → ปิด CSRF (ต้องทบทวนใหม่ตอน refresh cookie ขั้น 7)
                .csrf(csrf -> csrf.disable())
                // stateless: ไม่มี session ฝั่ง server — ตัวตนมาจาก token ล้วน ๆ
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                // นิรนามชน path หวงห้าม → 401 (default ของ Spring คือ 403 ซึ่งความหมายผิด)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType("application/problem+json");
                            res.getWriter().write(
                                    "{\"title\":\"Unauthorized\",\"status\":401,"
                                            + "\"detail\":\"missing or invalid token\"}");
                        })
                        // มีตัวตนแต่สิทธิ์ไม่ถึง → 403 (คนละความหมายกับ 401)
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType("application/problem+json");
                            res.getWriter().write(
                                    "{\"title\":\"Forbidden\",\"status\":403,"
                                            + "\"detail\":\"insufficient role\"}");
                        }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
