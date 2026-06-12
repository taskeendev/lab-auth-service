package lab.auth.user;

import lab.auth.error.ConflictException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserRepository users) {
        this.users = users;
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
}
