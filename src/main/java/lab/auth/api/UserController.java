package lab.auth.api;

import jakarta.validation.Valid;
import java.security.Principal;
import lab.auth.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Principal มาจาก SecurityContext ที่ JwtAuthFilter ตั้งไว้ — ถึงตรงนี้ได้แปลว่า token ผ่านแล้ว
    @GetMapping("/me")
    public UserResponse me(Principal principal) {
        return UserResponse.from(userService.getByUsername(principal.getName()));
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Principal principal, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(
                principal.getName(), request.currentPassword(), request.newPassword());
    }
}
