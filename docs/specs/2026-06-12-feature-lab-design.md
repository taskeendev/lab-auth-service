# Feature Lab — Design Spec (จากการ brainstorm 2026-06-12)

## ปรัชญา

> ไม่ตะโกนว่านี่คืองานของฉัน — **ให้งานตะโกนแทนว่านี่คืองานที่ดี**

เว็บนี้คือ "แล็บเก็บฟีเจอร์": ทุกฟีเจอร์สร้างจริงระดับ production พร้อมคำอธิบายการออกแบบ
ผู้ใช้จริงมีคนเดียว (เจ้าของ) แต่ระบบรองรับผู้ใช้จริงได้เต็มรูปแบบ — ใช้เป็นผลงานสมัครงาน
สาย full-stack/DevOps ตลาดไทยไปพร้อมกับเป็นสนามเรียนรู้

## การตัดสินใจที่ล็อกแล้ว

| เรื่อง | ข้อสรุป |
|--------|---------|
| หน้าบ้าน | React + Vite + TypeScript, Tailwind + shadcn/ui, dark mode (toggle+จำค่า+ตาม OS), i18n EN/TH, react-router — host Vercel |
| หลังบ้าน | Java Spring Boot **microservices** (repo ละ service) + PostgreSQL |
| ระบบ user | register/login จริง, สิทธิ์ USER/ADMIN, เปลี่ยนรหัส, reset ผ่านอีเมล, 2FA (TOTP) |
| Presence | realtime ผ่าน WebSocket — admin dashboard เห็น online/offline + เวลา |
| Feed | user และ admin โพสต์/comment/like/share ได้ |
| Contact | เก็บ DB + แสดงใน admin + แจ้งอีเมล |
| Config | ห้าม hardcode — ทุกค่าผ่าน env, มี .env.example ทุก repo |
| Gateway | เขียนเองคั่น FE ↔ services (บทเรียนจาก traffic-stack) |
| Infra | CI/CD ทุก repo, custom domain + TLS, monitoring เบา ๆ |

## สถาปัตยกรรมปลายทาง

```
[Web React+TS (Vercel)] ──▶ [Gateway] ──┬──▶ [auth-service]    ─┐  + WebSocket presence
[Admin dashboard (ใน Web)] ─┘            ├──▶ [feed-service]    ─┼─▶ [PostgreSQL]
                                         └──▶ [contact-service] ─┘  + email ขาออก
```

Repos: `lab-auth-service` · `lab-web` · `lab-feed-service` · `lab-contact-service` · `lab-gateway`

## Roadmap

1. **auth-service ภาค 1** — register/login/JWT/roles/เปลี่ยนรหัส + CI ← เริ่มที่นี่
2. auth-service ภาค 2 — reset รหัสผ่านอีเมล + 2FA TOTP
3. lab-web shell — TS+Tailwind+shadcn + i18n + dark mode + login/register → Vercel
4. Presence realtime + admin dashboard
5. feed-service + UI
6. contact-service + UI
7. Gateway + custom domain + TLS + monitoring + CD

## auth-service ภาค 1 — API

```
POST /api/auth/register     {email, username, password} → 201
POST /api/auth/login        {username, password} → {accessToken}
GET  /api/users/me          (Bearer) → ข้อมูลตัวเอง
POST /api/users/me/password {currentPassword, newPassword} → 204
GET  /api/admin/users       (ADMIN) → รายชื่อ user
GET  /health                → 200
```

- รหัสผ่าน hash ด้วย BCrypt, JWT stateless (sub/role/exp), secret จาก env
- คนที่ register ด้วยอีเมลตรง `ADMIN_EMAIL` (env) ได้ role ADMIN (กลไก bootstrap)
- Flyway จัดการ schema, Testcontainers สำหรับ integration test, springdoc-openapi เอกสาร API

## วิธีทำงาน

- Claude สร้าง+ทดสอบเองทุกขั้น แล้วรายงานสรุป + URL ทุกครั้งที่จบขั้น/แก้บั๊กเสร็จ
- ผู้ใช้ตรวจรอบใหญ่ตอนจบเฟส
- สอนแบบ traffic-stack: อธิบายเป็นไทยทีละขั้น, commit ต่อขั้น, PROGRESS.md อัปเดตตลอด
