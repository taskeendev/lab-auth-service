package lab.auth.user;

import java.util.List;
import lab.auth.error.ConflictException;
import lab.auth.error.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminEmail;

    public UserService(
            UserRepository users,
            PasswordEncoder encoder,
            @Value("${app.admin-email}") String adminEmail) {
        this.users = users;
        this.encoder = encoder;
        this.adminEmail = adminEmail;
    }

    public User register(String email, String username, String rawPassword) {
        if (users.existsByEmail(email)) {
            throw new ConflictException("email already registered");
        }
        if (users.existsByUsername(username)) {
            throw new ConflictException("username already taken");
        }
        User user = new User(email, username, encoder.encode(rawPassword));
        // กลไก bootstrap: อีเมลที่ประกาศไว้ใน env ได้เป็น ADMIN — ไม่ต้องไป UPDATE ใน DB เอง
        if (!adminEmail.isBlank() && email.equalsIgnoreCase(adminEmail)) {
            user.setRole(Role.ADMIN);
        }
        return users.save(user);
    }

    public List<User> listAll() {
        return users.findAll();
    }

    // ข้อความ error เดียวกันทั้ง "ไม่มี user" และ "รหัสผิด" — ไม่บอกใบ้คนเดาบัญชี
    public User authenticate(String username, String rawPassword) {
        return users.findByUsername(username)
                .filter(u -> encoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("invalid credentials"));
    }

    public User getByUsername(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("unknown user"));
    }
}
