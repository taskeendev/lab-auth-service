package lab.auth.user;

import lab.auth.error.ConflictException;
import lab.auth.error.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public User register(String email, String username, String rawPassword) {
        if (users.existsByEmail(email)) {
            throw new ConflictException("email already registered");
        }
        if (users.existsByUsername(username)) {
            throw new ConflictException("username already taken");
        }
        return users.save(new User(email, username, encoder.encode(rawPassword)));
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
