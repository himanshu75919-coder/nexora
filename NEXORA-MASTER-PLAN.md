# 💜 NEXORA — MASTER PLAN & ARCHITECTURE
*Vision: ONE PLATFORM. MULTIPLE WEBSITES. UNLIMITED CONFIGURABILITY.*
*Version: Phase 0-1 delivered (17-18 Nov). Status labels: ✅ working now · 🟡 pending dependency · ⬜ later phase.*

## 1. SYSTEM MAP
```
┌────────────────────┐     HTTPS      ┌──────────────────────────────┐
│  NEXORA Android     │ ──/api/config──▶  NEXORA Backend API           │
│  (Kotlin/Compose)   │ ◀──ETag 304───  (this repo: Flask+SQLite now, │
│  dashboard · browser│                │   Supabase-ready later)      │
└────────────────────┘                └──────────┬───────────────────┘
                                                 │ admin CRUD
                                      ┌──────────▼───────────────────┐
                                      │  Admin Panel (web, separate)  │
                                      │  admin.html — login/session   │
                                      └──────────────────────────────┘
```

## 2. WHAT IS WORKING RIGHT NOW (✅ Phase 0-1)
| Component | Status | Proof |
|---|---|---|
| Backend API (config, ETag/304, version) | ✅ | test-suite pass: 10 cards, ETag d7fa…, 304 on If-None-Match |
| Admin auth (PBKDF2, lockout 5/5min, session 7d) | ✅ | 401 bad-login, 401 no-token, login ok |
| App Manager (cards CRUD + duplicate + labels) | ✅ | created/duplicated/deleted in tests |
| Server Manager (per-card independent servers, default/maintenance/priority) | ✅ | server CRUD pass |
| **Server Health (real checks, logged history)** | ✅ | example.com → HTTP 200, 69ms |
| Categories / Announcements / Home settings / Feature flags | ✅ | CRUD pass |
| **Publish + versioning + one-click rollback** | ✅ | publish v1 → rollback → audit trail |
| Audit log | ✅ | 9 entries recorded in tests |
| Admin Panel UI (dark-purple glass, mobile preview) | ✅ | admin.html |
| Android Phase-1 app (Compose: dashboard, search, favorites/recents DataStore, server-sheet, secure WebView, last-known-good cache) | ✅ source | builds in Android Studio (needs Android SDK) |
| Seeded REAL data | ✅ | 10 cards from boss's live ecosystem (BAT×8, Science Magnet, RWA) — 33 servers |

## 3. PENDING DEPENDENCIES (🟡 honest list)
| Item | Needs | Where it plugs in |
|---|---|---|
| Supabase production DB | boss creates Supabase project → run `nexora-supabase-schema.sql` | swap base URL in admin/API config; schema already matches |
| APK build | Android Studio (SDK 34) | open `android/`, set CONFIG_BASE_URL, Build APK |
| FCM notifications | Firebase project + server key | notifications table exists; push worker = Phase 8 |
| AI Study Assistant | AI provider key on SERVER (never in APK) | AI gateway endpoint = Phase 7; flag `flag_ai_assistant` already wired |
| Education content | authorized content sources | institutes/content_items tables ready |

## 4. SECURITY MODEL (implemented + planned)
- ✅ PBKDF2-SHA256 (120k iters, random salt), httponly session cookie, failed-login lockout, rate limits (120/min public, 240/min admin)
- ✅ admin endpoints reject unauthenticated (401); public API exposes ZERO admin data
- ✅ Android: no secrets in APK; WebView hardened (no file/content access, safe browsing, mixed-content blocked, unknown schemes fail-closed)
- ✅ ETag versioning; last-known-good cache on device; rollback on server
- 🟡 MFA + RBAC roles (schema ready; wire on Supabase) · audit viewer UI exists
- 🟡 RLS policies included in SQL (public read-only, admin-write via service role)

## 5. PHASES (prompt §22 mapping)
- **Phase 1 (foundation+cards+servers+browser): ✅ delivered this build**
- Phase 2 Supabase cutover: 🟡 needs boss's Supabase project (30 min)
- Phase 3 advanced builders (drag-drop, scheduled publish): ⬜
- Phase 4 more admin modules (nav builder, home builder): ⬜
- Phase 5 bookmarks UI + download manager native: ⬜
- Phase 6 education platform content: ⬜ (needs authorized sources)
- Phase 7 AI gateway: ⬜ (needs provider key, server-side)
- Phase 8 FCM + analytics: ⬜
- Phase 9 hardening/perf/load tests: ⬜

## 6. RUNBOOK
- Start backend+admin: `cd nexora/server && python3 nexora_api.py` → panel on `/` (admin.html), API on `/api/*`
- First login: `admin@nexora.app` / `Nexora@Boss2026` → **change password immediately** (Sign-out dialog → password route; UI: coming in Phase-2 settings page; API: POST /api/admin/password)
- App config for users/apps: GET `/api/config` (ETag), GET `/api/version`
- Deploy: any VPS (uvicorn/gunicorn) or Render/Railway; set env `PORT`, `NEXORA_DB` path
- Android: open `android/` in Android Studio → set `CONFIG_BASE_URL` → Run/Build APK

## 7. NOTES (honesty rules respected)
- No fake statuses: health checks are REAL HTTP checks; empty states are real empty states
- Servers are independent websites (no mirror auto-fallback) — exactly per prompt §5
- No APK-update needed for content changes: publish → apps auto-sync on next open
