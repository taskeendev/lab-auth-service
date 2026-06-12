package lab.auth.error;

// ข้อมูลชนกับของที่มีอยู่ (เช่น email/username ซ้ำ) → 409
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
