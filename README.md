# 💜 NEXORA — Quick Start

## Run Admin Panel + Backend (this machine/preview)
```bash
cd nexora/server
python3 nexora_api.py          # PORT env optional, default 8080
```
- Admin panel: open root URL (login: `admin@nexora.app` / `Nexora@Boss2026` — **change it!**)
- App config API: `/api/config` · `/api/version` · `/api/health`

## Android app (Phase 1)
1. Open `nexora/android/` in Android Studio (Koala+)
2. In `app/build.gradle.kts` set `CONFIG_BASE_URL` to your backend URL
3. Run ▶ or Build APK

## Production DB (later)
1. Create Supabase project → SQL Editor → paste `nexora-supabase-schema.sql`
2. Point backend/admin to Supabase (Phase 2 — schema already matches)

## Files
- `server/nexora_api.py` — Flask backend (auth, CRUD, publish/rollback, health checks)
- `server/admin.html` — admin console (dark-purple glass, live mobile preview)
- `android/` — Kotlin/Compose app source (Phase 1)
- `nexora-supabase-schema.sql` — production schema (21 tables + RLS)
- `NEXORA-MASTER-PLAN.md` — architecture, status, phases, dependencies
