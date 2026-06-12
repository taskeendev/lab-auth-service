package lab.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 3, max = 50) String username,
        // เพดาน 72 เพราะ BCrypt ใช้แค่ 72 ไบต์แรก — เกินกว่านั้นเงียบ ๆ ถูกตัดทิ้ง
        @NotBlank @Size(min = 8, max = 72) String password) {
}
