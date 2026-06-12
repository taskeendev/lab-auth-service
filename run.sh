#!/usr/bin/env bash
# โหลด env จาก .env แล้วรัน service — ทางเดียวที่ใช้รัน dev เพื่อให้วินัย env คงเส้นคงวา
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "ไม่พบ .env — คัดลอกจาก .env.example ก่อน: cp .env.example .env" >&2
  exit 1
fi

set -a
source .env
set +a

exec ./gradlew bootRun
