# lab-auth-service — Progress

ส่วนหนึ่งของ **Feature Lab** (ดู `docs/specs/2026-06-12-feature-lab-design.md`)
เฟสนี้: auth-service ภาค 1 — register/login/JWT/roles/เปลี่ยนรหัส + CI

สถานะ: ⬜ ยังไม่เริ่ม · 🔨 กำลังทำ · ✅ เสร็จ

## บันได 6 ขั้น

- [x] 1. โครงโปรเจกต์: Spring Boot + Gradle + docker compose (Postgres) + /health + วินัย env — 2026-06-12
- [ ] 2. User entity + Flyway migration + JPA repository
- [ ] 3. POST /api/auth/register (validation + BCrypt)
- [ ] 4. POST /api/auth/login → JWT + Security filter chain (stateless)
- [ ] 5. Roles USER/ADMIN + /api/admin/users + bootstrap admin จาก env
- [ ] 6. เปลี่ยนรหัสผ่าน + integration tests (Testcontainers) + GitHub Actions CI
- [ ] 7. refresh token (เก็บ DB, HttpOnly cookie, rotation) + logout/revoke

เกณฑ์ผ่านเฟส: CI เขียวบน GitHub

## Log การทำงาน

- 2026-06-12 — brainstorm + spec + แผนอนุมัติ; ขั้น 1 เสร็จ: Spring Boot 3.4.5/Java 21/Gradle 8.11.1 ทำมือ, /health, Postgres ใน compose, วินัย env ครบ (.env.example + run.sh ที่ source .env — พิสูจน์: เปลี่ยน SERVER_PORT ใน .env แล้ว service ย้าย port โดยไม่แตะโค้ด)
