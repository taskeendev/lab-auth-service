package lab.auth.api;

import lab.auth.user.User;

// สิ่งที่โลกภายนอกเห็น — ไม่มีวันมี passwordHash
public record UserResponse(Long id, String email, String username, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getUsername(), user.getRole().name());
    }
}
