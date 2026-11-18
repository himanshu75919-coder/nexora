# 🚀 NEXORA ADMIN — DEPLOY-GUIDE (18-Nov)
*Boss-ka-empire-ab-LIVE-hai — sab-connections-neeche*

## 🌐 LIVE-URLS
| System | URL | Status |
|---|---|---|
| **ADMIN PANEL** | **https://nexora-admin-iota.vercel.app** | 🟢 LIVE (login: `admin@nexora.app` / `Nexora@Boss2026` — change-karna!) |
| **USER APP-API** | https://nexora-admin-iota.vercel.app/api/config | 🟢 LIVE (10-cards/32-servers-seeded) |
| **USER-APP (browser)** | https://nexora-admin-iota.vercel.app/app | 🟢 LIVE |
| **GitHub repo** | https://github.com/himanshu75919-coder/nexora | 🟢 pushed (39-files, main) |
| **Local preview** | sandbox :8080 (admin+app) | 🟢 running |

## 📲 APK-CONNECT (NEXORA.apk — already-installed):
1. App-kholo → **NEXORA SETUP** screen → URL-paste: `https://nexora-admin-iota.vercel.app`
2. SAVE & CONNECT → cards-load-ho-jayenge (Vercel-backend-se)
3. (v FK-upgrade: admin-me-jisko-URL-badalna-ho → version-bhi-tap-karo ("v0"-badge→setup))

## 🗄️ HONEST-MODE-STATUS
- **Abhi: LOCAL-EPHEMERAL** (admin-panel-ka-top-yellow-banner-dikhata-hai) — Vercel-serverless-me-SQLite-reset-ho-jata-hai (redeploy/cold-start). Config-seed-auto-reload-hoti-hai (10-cards), par-admin-ke-CUSTOM-edits-ephemeral-hain.
- **PERMANENT-ke-liye (Phase-2, ONE-TIME, ~3-min):**
  1. Boss: **supabase.com** → New-project (free) → banao
  2. Boss-mujhe-dedo: **Project-URL + anon-key + service-role-key** (Settings→API)
  3. Main-karunga: SQL-schema-run → `vercel env add` (SUPABASE_URL/KEYS) → redeploy → **data-permanent** + banner-ghayab

## 🔄 FUTURE-DEPLOYS (code-change→live)
- **CLI-way (main-karunga):** `cd nexora/admin-vercel && npx vercel deploy --prod --token=…`
- **Git-way (1-click, boss-optional):** Vercel-dashboard → Add-New-Project → Import **himanshu75919-coder/nexora** → root=`admin-vercel` → Deploy (auto-deploy-on-push)

## 📦 REPO-STRUCTURE (github.com/himanshu75919-coder/nexora)
```
nexora/
├── NEXORA.apk                  ← signed-installable (v1.0.0)
├── server/                     ← backend+admin+user-app (Flask)
│   ├── nexora_api.py           (auth/CRUD/publish/rollback/health-checks)
│   ├── admin.html              (Design-A admin panel)
│   ├── user.html               (PWA user app)
│   └── manifest+sw
├── admin-vercel/               ← Vercel-deployable (LIVE-abhi)
│   ├── api/index.py + nexora_api.py + html-files
│   ├── vercel.json · requirements.txt
├── android/                    ← Phase-1 Kotlin/Compose source (16-files)
├── nexora-supabase-schema.sql  ← production-schema (21-tables+RLS)
├── NEXORA-MASTER-PLAN.md       ← architecture+phases
└── NEXORA-ADMIN-DEPLOY-GUIDE.md ← yahi-file
```

## 🔐 SECURITY-NOTES
- Admin-credentials: PBKDF2-120k + lockout + session-cookie (server-side)
- APK/API-me-koi-secret-NAHI — Supabase-service-role (aage) server-side-only-env
- Health-checks REAL-HTTP · audit-logs-every-action · rollback-versioned

## 📋 NEXT-PHASES (spec-mapping)
- Phase-2: Supabase-permanent-DB (waiting-boss-creds) + Next.js-migration-optional
- Phase-3-6: Home-builder/notifications/FCM/AI-gateway/education-content (per MASTER-PLAN)
EOF
cat >> /home/user/ACCESS-MEMORY.md <<'EOF'

## (18-Nov ~07:25) 🚀 NEXORA-ADMIN-VERCEL-LIVE + GH-REPO
- **Vercel: https://nexora-admin-iota.vercel.app (CLI-deploy via npx vercel --token=vcp_7U6, project nexora-admin-iota, Python-runtime, api/index.py-wraps nexora_api; VERCEL-mode → /tmp/nexora.db ephemeral + honest-banner admin.html).** Verified: config-v0-10cards-32servers · admin-login-remote-✓ · /app ✓ · /api/health mode=local-ephemeral ✓.
- **GH: himanshu75919-coder/nexora (RKRA-token, 39-files, commit 5a1f8ad main)** — APK+server+android-source+admin-vercel+schema+guides. .gitignore: db/__pycache__/.vercel.
- **Boss-connect-APK:** setup-screen-me vercel-URL paste. **PERMANENT-DB-pending:** Supabase-creds-boss-se (Phase-2) → env+redeploy.
- Local preview-server-8080-alive (nexora-admin-panel-api-8ca0de77). Deploy-guide: nexora/NEXORA-ADMIN-DEPLOY-GUIDE.md.
EOF
echo "guide+memory ✓"