# lab-auth-service — Progress

ส่วนหนึ่งของ **Feature Lab** (ดู `docs/specs/2026-06-12-feature-lab-design.md`)
เฟสนี้: auth-service ภาค 1 — register/login/JWT/roles/เปลี่ยนรหัส + CI

สถานะ: ⬜ ยังไม่เริ่ม · 🔨 กำลังทำ · ✅ เสร็จ

## บันได 6 ขั้น

- [x] 1. โครงโปรเจกต์: Spring Boot + Gradle + docker compose (Postgres) + /health + วินัย env — 2026-06-12
- [x] 2. User entity + Flyway migration + JPA repository — 2026-06-12
- [x] 3. POST /api/auth/register (validation + BCrypt) + GlobalExceptionHandler (RFC 7807) — 2026-06-12
- [x] 4. POST /api/auth/login → JWT + Security filter chain (stateless) — 2026-06-12
- [x] 5. Roles USER/ADMIN + /api/admin/users + bootstrap admin จาก env — 2026-06-12
- [x] 6. เปลี่ยนรหัสผ่าน + structured logging/request id + integration tests (Testcontainers) + GitHub Actions CI — 2026-06-12
- [x] 7. refresh token (เก็บ DB, HttpOnly cookie, rotation) + logout/revoke — 2026-06-12

เกณฑ์ผ่านเฟส: CI เขียวบน GitHub

## Log การทำงาน

- 2026-06-12 — ขั้น 7 เสร็จ = **auth-service ภาค 1 จบ**: refresh_tokens (เก็บ SHA-256 ไม่เก็บ token ดิบ), RefreshService: issue/consume (rotation)/revoke/revokeAllFor + reuse detection (token ที่หมุนทิ้งแล้วโผล่ซ้ำ = เผาทุก session); HttpOnly cookie path=/api/auth SameSite=Lax secure จาก env; /refresh /logout; **บั๊กที่เทสต์จับได้: @Transactional rollback ตอนโยน 401 ทำให้การเผา session ถูกย้อนกลับ → dontRollbackOn=UnauthorizedException**; integration tests 2 ตัวครอบ rotation/reuse/logout ครบ

- 2026-06-12 — ขั้น 6 เสร็จ: POST /users/me/password (ต้องรู้รหัสเดิม — token ถูกขโมยยึดบัญชีถาวรไม่ได้); RequestIdFilter (รับต่อ X-Request-Id หรือสร้างใหม่, MDC ใส่ทุกบรรทัด log, access log ท้าย request, ล้าง MDC กัน thread reuse); graceful shutdown; AuthFlowIntegrationTest (Testcontainers postgres:16) ครอบ 14 จุด: register/dup/validation/login ผิด-ถูก/me/401/403/admin bootstrap/เปลี่ยนรหัสครบวงจร; แก้ generics capture ใน assertion 2 รอบ; CI = gradlew build บน ubuntu (docker มีในตัว)

- 2026-06-12 — ขั้น 5 เสร็จ: @EnableMethodSecurity + @PreAuthorize("hasRole('ADMIN')") ที่ /api/admin/users; bootstrap admin จาก ADMIN_EMAIL env (เว้นว่าง = ปิด); **บั๊กที่เจอ+แก้**: @PreAuthorize โยน AccessDeniedException ทะลุ MVC เข้า catch-all ของ GlobalExceptionHandler → กลายเป็น 500 (accessDeniedHandler ของ filter chain จับไม่ถึงชั้น method) → เพิ่ม @ExceptionHandler(AccessDeniedException) → 403; เทสต์: ADMIN 200 / USER 403 / นิรนาม 401

- 2026-06-12 — ขั้น 4 เสร็จ: JwtService (jjwt 0.12, HS512, secret จาก env แบบไม่มี default — ลืมตั้ง = พังตอน boot), JwtAuthFilter (token ดีใส่ SecurityContext, เสีย = นิรนาม), SecurityConfig stateless + permitAll เฉพาะ /health,/api/auth/** + entry point ตอบ 401 ProblemDetail (default Spring เป็น 403 ซึ่งผิดความหมาย); login คืน {accessToken,Bearer,expiresIn}; authenticate ใช้ข้อความเดียว "invalid credentials" ทั้งไม่มี user/รหัสผิด (กัน enumeration); /api/users/me อ่านจาก Principal; เทสต์ครบ: token ดี/ไม่มี/ขยะ/รหัสผิด/health เปิด

- 2026-06-12 — ขั้น 3 เสร็จ: /api/auth/register + Bean Validation (รหัส 8-72 — 72 คือเพดาน BCrypt) + UserService (เช็คซ้ำ → ConflictException) + GlobalExceptionHandler ตอบ RFC 7807 ทุกกรณี (409 ชัดเหตุ, 400 มี field errors, 500 ไม่รั่ว stack, DataIntegrityViolation เป็นตาข่าย race); UserResponse ไม่มีวันมี hash; เทสต์: 201/409/400 ตรงสัญญา, DB เก็บ $2a$ hash

- 2026-06-12 — ขั้น 2 เสร็จ: V1__create_users.sql (BIGSERIAL, email/username UNIQUE, role default USER, created_at default now), entity User + Role enum + UserRepository; ddl-auto=validate (Flyway เป็นเจ้าของ schema, Hibernate เป็นผู้ตรวจ); verify: ตาราง+index ครบใน psql, flyway_schema_history v1 success, restart ไม่ migrate ซ้ำ

- 2026-06-12 — brainstorm + spec + แผนอนุมัติ; ขั้น 1 เสร็จ: Spring Boot 3.4.5/Java 21/Gradle 8.11.1 ทำมือ, /health, Postgres ใน compose, วินัย env ครบ (.env.example + run.sh ที่ source .env — พิสูจน์: เปลี่ยน SERVER_PORT ใน .env แล้ว service ย้าย port โดยไม่แตะโค้ด)
