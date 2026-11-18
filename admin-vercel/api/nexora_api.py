"""
NEXORA Backend + Admin API (Phase 0-1 working build)
- Local mode: SQLite (no external creds needed) - production path: Supabase (schema included)
- Admin: email+password (PBKDF2-SHA256), session cookie, failed-login lockout, audit logs
- App API: versioned config with ETag/304 (never returns admin secrets)
- Server Health: real HEAD/GET checks, rate-limited, logged in server_health_history
"""
import json, os, sqlite3, time, hashlib, secrets, threading
from flask import Flask, request, jsonify, g, make_response

DB_PATH = os.environ.get("NEXORA_DB") or ("/tmp/nexora.db" if os.environ.get("VERCEL") else os.path.join(os.path.dirname(__file__), "nexora.db"))
TOKEN_TTL = 7 * 86400
LOCK_LIMIT, LOCK_WINDOW = 5, 300
_sessions = {}          # token -> {admin_id, exp}
_failed = {}            # ip -> [timestamps]
_rl = {}                # path-key -> [timestamps]
_RL_LIMIT = 120
_lock = threading.Lock()

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 2 * 1024 * 1024

# ---------- DB helpers ----------
def con():
    c = sqlite3.connect(DB_PATH, timeout=15)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA journal_mode=WAL")
    return c

SCHEMA = """
CREATE TABLE IF NOT EXISTS admin_users(id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT UNIQUE, pw_hash TEXT, role TEXT DEFAULT 'super_admin', created REAL);
CREATE TABLE IF NOT EXISTS categories(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, sort INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS app_cards(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT DEFAULT '', logo_url TEXT DEFAULT '',
  category_id INTEGER, featured INTEGER DEFAULT 0, label TEXT DEFAULT '', enabled INTEGER DEFAULT 1, sort INTEGER DEFAULT 0,
  browser_mode TEXT DEFAULT 'internal', created REAL, updated REAL);
CREATE TABLE IF NOT EXISTS app_servers(id INTEGER PRIMARY KEY AUTOINCREMENT, card_id INTEGER NOT NULL, url TEXT NOT NULL,
  priority INTEGER DEFAULT 10, is_default INTEGER DEFAULT 0, enabled INTEGER DEFAULT 1, maintenance INTEGER DEFAULT 0, note TEXT DEFAULT '');
CREATE TABLE IF NOT EXISTS announcements(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, level TEXT DEFAULT 'info', enabled INTEGER DEFAULT 1, created REAL);
CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE IF NOT EXISTS config_versions(version INTEGER PRIMARY KEY, published_at REAL, snapshot TEXT);
CREATE TABLE IF NOT EXISTS audit_logs(id INTEGER PRIMARY KEY AUTOINCREMENT, ts REAL, actor TEXT, action TEXT, detail TEXT);
CREATE TABLE IF NOT EXISTS server_health_history(id INTEGER PRIMARY KEY AUTOINCREMENT, server_id INTEGER, ts REAL, ok INTEGER, http_code INTEGER, ms INTEGER);
"""

def now(): return time.time()

def audit(actor, action, detail=""):
    c = con(); c.execute("INSERT INTO audit_logs(ts,actor,action,detail) VALUES(?,?,?,?)", (now(), actor, action, json.dumps(detail)[:500])); c.commit(); c.close()

# ---------- seed ----------
def seed():
    c = con(); c.executescript(SCHEMA)
    if not c.execute("SELECT 1 FROM admin_users").fetchone():
        salt = secrets.token_hex(16)
        h = hashlib.pbkdf2_hmac("sha256", b"Nexora@Boss2026", salt.encode(), 120_000).hex()
        c.execute("INSERT INTO admin_users(email,pw_hash,role) VALUES(?,?,?)", ("admin@nexora.app", salt + "$" + h, "super_admin"))
    if not c.execute("SELECT 1 FROM categories").fetchone():
        cats = ["Study Apps", "Portals", "Tools"]
        for i, n in enumerate(cats): c.execute("INSERT INTO categories(name,sort) VALUES(?,?)", (n, i))
        cat_id = {n: i + 1 for i, n in enumerate(cats)}
        cards = [
            ("BAT UNIVERSE", "Hub - all study apps in one place", "https://bat.universe.example/logo.png", "Portals", 1, "HOT", [("https://bat.unuverse.workers.dev/", 1, 1), ("https://batuniverse.lovable.app/", 2, 0)]),
            ("PHYSICS WALLAH", "PW batches - full study library", "", "Study Apps", 1, "NEW", [("https://bat.pw1.workers.dev/", 1, 1), ("https://bat.pww2.workers.dev/", 2, 0), ("https://bat.pww3.workers.dev/", 3, 0), ("https://bat.pw4.workers.dev/", 4, 0)]),
            ("VIDYAGRAM", "Vidyagram batches portal", "", "Study Apps", 1, "TRENDING", [("https://bat.vidya1.workers.dev/", 1, 1), ("https://bat.vg2.workers.dev/", 2, 0), ("https://bat.vg3.workers.dev/", 3, 0), ("https://bat.vg4.workers.dev/", 4, 0)]),
            ("NEXT TOPPER", "Next Topper batches", "", "Study Apps", 0, "", [("https://bat.nextt1.workers.dev/", 1, 1), ("https://bat.nextt2.workers.dev/", 2, 0), ("https://bat.nextt3.workers.dev/", 3, 0), ("https://bat.nextt4.workers.dev/", 4, 0)]),
            ("KHAN G S", "Khan Global Studies batches", "", "Study Apps", 0, "", [("https://bat.kgs1.workers.dev/", 1, 1), ("https://bat.kgs2.workers.dev/", 2, 0), ("https://bat.kgs3.workers.dev/", 3, 0), ("https://bat.kgs4.workers.dev/", 4, 0)]),
            ("CAREER WILL", "CareerWill batches", "", "Study Apps", 0, "", [("https://bat.cwill1.workers.dev/", 1, 1), ("https://bat.cwill2.workers.dev/", 2, 0), ("https://bat.cwill3.workers.dev/", 3, 0), ("https://bat.cwill4.workers.dev/", 4, 0)]),
            ("DISHA O C", "Disha online coaching batches", "", "Study Apps", 0, "", [("https://bat.docc1.workers.dev/", 1, 1), ("https://bat.doc22.workers.dev/", 2, 0), ("https://bat.doc3.workers.dev/", 3, 0), ("https://bat.doc44.workers.dev/", 4, 0)]),
            ("PARMAR SSC", "Parmar SSC free batches + Academy", "", "Study Apps", 1, "VIP", [("https://bat.parmar1.workers.dev/", 1, 1), ("https://bat.parmar2.workers.dev/", 2, 0), ("https://bat.parmar3.workers.dev/", 3, 0), ("https://bat.parmar4.workers.dev/", 4, 0)]),
            ("SCIENCE MAGNET", "Multi-app learning portal (GK/Parmar/VG/Disha)", "", "Portals", 1, "NEW", [("https://science-magnet.ai.studio/", 1, 1)]),
            ("RWA PORTAL", "Rojgar With Ankit official learning portal", "", "Portals", 0, "", [("https://ais-pre-puxho4o57upkknekv3mgg7-49225910278.asia-east1.run.app/", 1, 1)]),
        ]
        for name, desc, logo, cat, feat, label, servers in cards:
            cur = c.execute("INSERT INTO app_cards(name,description,logo_url,category_id,featured,label,enabled,sort,created,updated) VALUES(?,?,?,?,1,?,1,?,?,?)",
                            (name, desc, logo, cat_id[cat], label, len(name), now(), now()))
            cid = cur.lastrowid
            for url, pri, dflt in servers:
                c.execute("INSERT INTO app_servers(card_id,url,priority,is_default,enabled,maintenance) VALUES(?,?,?,?,1,0)",
                          (cid, url, pri, 1 if dflt else 0))
        c.execute("INSERT INTO announcements(title,body,level,created) VALUES(?,?,?,?)",
                  ("Welcome to NEXORA", "All study apps are now live. More portals coming soon.", "info", now()))
    for k, v in {"welcome_title": "Welcome to NEXORA", "welcome_sub": "ONE PLATFORM. MULTIPLE WEBSITES. UNLIMITED CONFIGURABILITY.",
                 "sections_featured": "1", "sections_popular": "1", "sections_recent": "1", "sections_trending": "1",
                 "flag_ai_assistant": "0", "flag_downloads": "1", "flag_notifications": "1", "flag_browser_internal": "1"}.items():
        c.execute("INSERT OR IGNORE INTO settings(key,value) VALUES(?,?)", (k, v))
    c.commit(); c.close()

seed()

# ---------- auth ----------
def pw_verify(email, password):
    c = con(); r = c.execute("SELECT * FROM admin_users WHERE email=?", (email,)).fetchone(); c.close()
    if not r: return None
    salt, h = r["pw_hash"].split("$", 1)
    if hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 120_000).hex() == h: return r
    return None

def current_admin():
    tok = request.cookies.get("nexora_admin") or (request.headers.get("X-Admin-Token") or "")
    s = _sessions.get(tok)
    if s and s["exp"] > now(): return s["admin_id"]
    _sessions.pop(tok, None); return None

def rate_ok(key, limit=_RL_LIMIT, win=60):
    with _lock:
        arr = [t for t in _rl.get(key, []) if t > now() - win]
        if len(arr) >= limit: return False
        arr.append(now()); _rl[key] = arr; return True

@app.after_request
def cors_open(resp):
    if request.path.startswith("/api/") and request.method == "GET":
        resp.headers["Access-Control-Allow-Origin"] = "*"
    return resp

@app.before_request
def guard():
    p = request.path
    if p.startswith("/api/admin"):
        if not rate_ok("admin:" + (request.remote_addr or "x"), 240):
            return jsonify(error="rate_limited"), 429
        if p != "/api/admin/login" and not current_admin():
            return jsonify(error="unauthorized"), 401
    elif p.startswith("/api/"):
        if not rate_ok("api:" + (request.remote_addr or "x")):
            return jsonify(error="rate_limited"), 429

# ---------- public app API ----------
def build_config():
    c = con()
    cats = [{"id": r["id"], "name": r["name"], "sort": r["sort"]} for r in c.execute("SELECT * FROM categories ORDER BY sort")]
    cards = []
    for r in c.execute("SELECT * FROM app_cards ORDER BY sort, id"):
        sv = [{"id": s["id"], "url": s["url"], "priority": s["priority"], "is_default": bool(s["is_default"]),
               "enabled": bool(s["enabled"]), "maintenance": bool(s["maintenance"])} for s in
              c.execute("SELECT * FROM app_servers WHERE card_id=? AND enabled=1 ORDER BY priority", (r["id"],))]
        if not sv: continue
        cards.append({"id": r["id"], "name": r["name"], "description": r["description"], "logo": r["logo_url"],
                      "category": r["category_id"], "featured": bool(r["featured"]), "label": r["label"],
                      "browser": r["browser_mode"], "servers": sv})
    ann = [{"title": a["title"], "body": a["body"], "level": a["level"]} for a in c.execute("SELECT * FROM announcements WHERE enabled=1 ORDER BY created DESC LIMIT 10")]
    settings = {k: v for k, v in c.execute("SELECT key,value FROM settings")}
    c.close()
    return {"cards": cards, "categories": cats, "announcements": ann, "settings": settings}

@app.get("/")
def admin_panel():
    html = open(os.path.join(os.path.dirname(__file__), "admin.html"), encoding="utf-8").read()
    return make_response(html)

@app.get("/app")
def user_app():
    html = open(os.path.join(os.path.dirname(__file__), "user.html"), encoding="utf-8").read()
    return make_response(html)

@app.get("/manifest.webmanifest")
def manifest():
    r = make_response(open(os.path.join(os.path.dirname(__file__), "manifest.webmanifest"), encoding="utf-8").read())
    r.headers["Content-Type"] = "application/manifest+json"
    return r

@app.get("/sw.js")
def serviceworker():
    r = make_response(open(os.path.join(os.path.dirname(__file__), "sw.js"), encoding="utf-8").read())
    r.headers["Content-Type"] = "application/javascript"
    return r

@app.get("/api/version")
def api_version():
    c = con(); v = c.execute("SELECT MAX(version) v FROM config_versions").fetchone()["v"] or 0; c.close()
    return jsonify(service="NEXORA", config_version=v, ok=True)

@app.get("/api/config")
def api_config():
    cfg = build_config()
    body = json.dumps(cfg, sort_keys=True)
    tag = hashlib.sha256(body.encode()).hexdigest()[:16]
    if request.headers.get("If-None-Match") == tag:
        return "", 304
    vrow = con().execute("SELECT MAX(version) v, published_at p FROM config_versions").fetchone()
    resp = make_response(jsonify(ok=True, config_version=vrow["v"] or 0, published_at=vrow["p"], data=cfg))
    resp.headers["ETag"] = tag
    return resp

@app.get("/api/health")
def api_health():
    _mode = "supabase" if os.environ.get("SUPABASE_URL") else "local-ephemeral"
    return jsonify(ok=True, service="NEXORA-API", time=int(now()), mode=_mode)

# ---------- admin auth ----------
@app.post("/api/admin/login")
def admin_login():
    ip = request.remote_addr or "x"
    with _lock:
        _failed[ip] = [t for t in _failed.get(ip, []) if t > now() - LOCK_WINDOW]
        if len(_failed[ip]) >= LOCK_LIMIT:
            return jsonify(error="locked", msg="Too many failed attempts. Try later."), 429
    d = request.get_json(silent=True) or {}
    r = pw_verify((d.get("email") or "").strip().lower(), d.get("password") or "")
    if not r:
        with _lock: _failed.setdefault(ip, []).append(now())
        audit("anon", "login_failed", {"email": d.get("email")})
        return jsonify(error="invalid"), 401
    tok = secrets.token_urlsafe(32)
    _sessions[tok] = {"admin_id": r["id"], "exp": now() + TOKEN_TTL}
    with _lock: _failed.pop(ip, None)
    audit(r["email"], "login_ok")
    resp = make_response(jsonify(ok=True, email=r["email"], role=r["role"]))
    resp.set_cookie("nexora_admin", tok, httponly=True, samesite="Lax", max_age=TOKEN_TTL)
    return resp

@app.post("/api/admin/logout")
def admin_logout():
    tok = request.cookies.get("nexora_admin")
    if tok: _sessions.pop(tok, None)
    return jsonify(ok=True)

@app.post("/api/admin/password")
def admin_password():
    d = request.get_json(silent=True) or {}
    c = con(); r = c.execute("SELECT * FROM admin_users WHERE id=?", (current_admin(),)).fetchone()
    if not r or not pw_verify(r["email"], d.get("old") or ""):
        c.close(); return jsonify(error="invalid"), 401
    np = d.get("new") or ""
    if len(np) < 10: c.close(); return jsonify(error="weak", msg="Min 10 chars"), 400
    salt = secrets.token_hex(16)
    h = hashlib.pbkdf2_hmac("sha256", np.encode(), salt.encode(), 120_000).hex()
    c.execute("UPDATE admin_users SET pw_hash=? WHERE id=?", (salt + "$" + h, r["id"])); c.commit(); c.close()
    audit(r["email"], "password_changed"); return jsonify(ok=True)

# ---------- admin CRUD ----------
@app.get("/api/admin/overview")
def overview():
    c = con()
    d = {"cards": c.execute("SELECT COUNT(*) FROM app_cards").fetchone()[0],
         "servers": c.execute("SELECT COUNT(*) FROM app_servers").fetchone()[0],
         "categories": c.execute("SELECT COUNT(*) FROM categories").fetchone()[0],
         "announcements": c.execute("SELECT COUNT(*) FROM announcements").fetchone()[0],
         "version": c.execute("SELECT MAX(version) FROM config_versions").fetchone()[0] or 0,
         "health_checks": c.execute("SELECT COUNT(*) FROM server_health_history").fetchone()[0]}
    c.close(); return jsonify(ok=True, data=d)

@app.get("/api/admin/cards")
def cards_list():
    c = con(); out = []
    for r in c.execute("SELECT * FROM app_cards ORDER BY sort, id"):
        sv = [dict(s) for s in c.execute("SELECT * FROM app_servers WHERE card_id=? ORDER BY priority", (r["id"],))]
        out.append({**dict(r), "servers": sv})
    c.close(); return jsonify(ok=True, data=out)

@app.post("/api/admin/cards")
def cards_create():
    d = request.get_json(silent=True) or {}
    if not (d.get("name") or "").strip(): return jsonify(error="name_required"), 400
    c = con(); cur = c.execute(
        "INSERT INTO app_cards(name,description,logo_url,category_id,featured,label,enabled,sort,browser_mode,created,updated) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        (d["name"].strip(), d.get("description", ""), d.get("logo", ""), d.get("category_id"),
         1 if d.get("featured") else 0, d.get("label", ""), 1 if d.get("enabled", True) else 0,
         int(d.get("sort") or 999), d.get("browser", "internal"), now(), now()))
    cid = cur.lastrowid
    for i, s in enumerate(d.get("servers") or []):
        if (s.get("url") or "").strip():
            c.execute("INSERT INTO app_servers(card_id,url,priority,is_default,enabled,maintenance,note) VALUES(?,?,?,?,1,0,?)",
                      (cid, s["url"].strip(), int(s.get("priority") or i + 1), 1 if (d.get("default_server") == i or s.get("is_default")) else 0, s.get("note", "")))
    c.commit(); c.close(); audit("admin", "card_create", {"id": cid, "name": d["name"]})
    return jsonify(ok=True, id=cid)

@app.put("/api/admin/cards/<int:cid>")
def cards_update(cid):
    d = request.get_json(silent=True) or {}
    c = con()
    fields, vals = [], []
    for k, col in (("name", "name"), ("description", "description"), ("logo", "logo_url"), ("category_id", "category_id"),
                   ("label", "label"), ("browser", "browser_mode")):
        if k in d: fields.append(col + "=?"); vals.append(d[k])
    for k in ("featured", "enabled"):
        if k in d: fields.append(k + "=?"); vals.append(1 if d[k] else 0)
    if "sort" in d: fields.append("sort=?"); vals.append(int(d["sort"]))
    if fields:
        vals.append(now()); vals.append(cid)
        c.execute("UPDATE app_cards SET " + ", ".join(fields) + ", updated=? WHERE id=?", vals)
    c.commit(); c.close(); audit("admin", "card_update", {"id": cid, "fields": list(d.keys())})
    return jsonify(ok=True)

@app.delete("/api/admin/cards/<int:cid>")
def cards_delete(cid):
    c = con(); c.execute("DELETE FROM app_servers WHERE card_id=?", (cid,)); c.execute("DELETE FROM app_cards WHERE id=?", (cid,))
    c.commit(); c.close(); audit("admin", "card_delete", {"id": cid}); return jsonify(ok=True)

@app.post("/api/admin/cards/<int:cid>/duplicate")
def cards_dup(cid):
    c = con(); r = c.execute("SELECT * FROM app_cards WHERE id=?", (cid,)).fetchone()
    if not r: return jsonify(error="not_found"), 404
    cur = c.execute("INSERT INTO app_cards(name,description,logo_url,category_id,featured,label,enabled,sort,browser_mode,created,updated) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    (r["name"] + " (Copy)", r["description"], r["logo_url"], r["category_id"], r["featured"], r["label"], 0, r["sort"] + 1, r["browser_mode"], now(), now()))
    nid = cur.lastrowid
    for s in c.execute("SELECT * FROM app_servers WHERE card_id=?", (cid,)):
        c.execute("INSERT INTO app_servers(card_id,url,priority,is_default,enabled,maintenance,note) VALUES(?,?,?,?,1,0,?)", (nid, s["url"], s["priority"], 0, s["note"]))
    c.commit(); c.close(); audit("admin", "card_duplicate", {"from": cid, "to": nid}); return jsonify(ok=True, id=nid)

@app.post("/api/admin/cards/<int:cid>/servers")
def server_add(cid):
    d = request.get_json(silent=True) or {}
    url = (d.get("url") or "").strip()
    if not url.startswith(("http://", "https://")): return jsonify(error="bad_url"), 400
    c = con(); cur = c.execute("INSERT INTO app_servers(card_id,url,priority,is_default,enabled,maintenance,note) VALUES(?,?,?,?,1,0,?)",
                               (cid, url, int(d.get("priority") or 99), 1 if d.get("is_default") else 0, d.get("note", "")))
    c.commit(); sid = cur.lastrowid; c.close(); audit("admin", "server_add", {"card": cid, "sid": sid}); return jsonify(ok=True, id=sid)

@app.put("/api/admin/servers/<int:sid>")
def server_update(sid):
    d = request.get_json(silent=True) or {}
    c = con(); fields, vals = [], []
    for k, col in (("url", "url"), ("note", "note")):
        if k in d: fields.append(col + "=?"); vals.append(d[k])
    if "priority" in d: fields.append("priority=?"); vals.append(int(d["priority"]))
    if d.get("is_default"):
        r = c.execute("SELECT card_id FROM app_servers WHERE id=?", (sid,)).fetchone()
        c.execute("UPDATE app_servers SET is_default=0 WHERE card_id=?", (r["card_id"],))
        fields.append("is_default=1"); vals.append(1)
    for k in ("enabled", "maintenance"):
        if k in d: fields.append(k + "=?"); vals.append(1 if d[k] else 0)
    if fields:
        vals.append(sid); c.execute("UPDATE app_servers SET " + ", ".join(fields) + " WHERE id=?", vals)
    c.commit(); c.close(); audit("admin", "server_update", {"sid": sid, "fields": list(d.keys())}); return jsonify(ok=True)

@app.delete("/api/admin/servers/<int:sid>")
def server_delete(sid):
    c = con(); c.execute("DELETE FROM app_servers WHERE id=?", (sid,)); c.commit(); c.close()
    audit("admin", "server_delete", {"sid": sid}); return jsonify(ok=True)

@app.post("/api/admin/servers/<int:sid>/health")
def server_health(sid):
    import requests as rq
    c = con(); r = c.execute("SELECT * FROM app_servers WHERE id=?", (sid,)).fetchone()
    if not r: return jsonify(error="not_found"), 404
    ok, code, ms = 0, 0, 0
    t0 = None
    try:
        t0 = time.time()
        resp = rq.head(r["url"], timeout=8, allow_redirects=True, headers={"User-Agent": "NEXORA-HealthCheck/1.0"})
        code = resp.status_code; ms = int((time.time() - t0) * 1000); ok = 1 if code < 400 else 0
        if code in (405, 501):
            t0 = time.time(); resp = rq.get(r["url"], timeout=8, stream=True, headers={"User-Agent": "NEXORA-HealthCheck/1.0"})
            code = resp.status_code; ms = int((time.time() - t0) * 1000); ok = 1 if code < 400 else 0
            resp.close()
    except Exception:
        ms = int((time.time() - t0) * 1000) if t0 else 0
    c.execute("INSERT INTO server_health_history(server_id,ts,ok,http_code,ms) VALUES(?,?,?,?,?)", (sid, now(), ok, code, ms))
    c.commit(); c.close(); audit("admin", "server_health", {"sid": sid, "ok": ok, "code": code})
    return jsonify(ok=True, result={"up": bool(ok), "http": code, "ms": ms})

# ---------- categories / announcements / settings ----------
@app.get("/api/admin/categories")
def cats_list():
    c = con(); out = [dict(r) for r in c.execute("SELECT * FROM categories ORDER BY sort")]; c.close()
    return jsonify(ok=True, data=out)

@app.post("/api/admin/categories")
def cats_create():
    d = request.get_json(silent=True) or {}
    c = con()
    try: cur = c.execute("INSERT INTO categories(name,sort) VALUES(?,?)", (d["name"].strip(), int(d.get("sort") or 99))); c.commit()
    except Exception: return jsonify(error="duplicate"), 400
    cid = cur.lastrowid; c.close(); audit("admin", "category_add", {"id": cid}); return jsonify(ok=True, id=cid)

@app.put("/api/admin/categories/<int:gid>")
def cats_update(gid):
    d = request.get_json(silent=True) or {}
    c = con()
    if "name" in d: c.execute("UPDATE categories SET name=? WHERE id=?", (d["name"], gid))
    if "sort" in d: c.execute("UPDATE categories SET sort=? WHERE id=?", (int(d["sort"]), gid))
    c.commit(); c.close(); return jsonify(ok=True)

@app.delete("/api/admin/categories/<int:gid>")
def cats_delete(gid):
    c = con()
    used = c.execute("SELECT COUNT(*) FROM app_cards WHERE category_id=?", (gid,)).fetchone()[0]
    if used: c.close(); return jsonify(error="in_use", msg="Reassign cards first"), 400
    c.execute("DELETE FROM categories WHERE id=?", (gid,)); c.commit(); c.close(); return jsonify(ok=True)

@app.get("/api/admin/announcements")
def ann_list():
    c = con(); out = [dict(r) for r in c.execute("SELECT * FROM announcements ORDER BY created DESC")]; c.close()
    return jsonify(ok=True, data=out)

@app.post("/api/admin/announcements")
def ann_create():
    d = request.get_json(silent=True) or {}
    c = con(); cur = c.execute("INSERT INTO announcements(title,body,level,enabled,created) VALUES(?,?,?,?,?)",
                               (d.get("title", ""), d.get("body", ""), d.get("level", "info"), 1 if d.get("enabled", True) else 0, now()))
    c.commit(); c.close(); return jsonify(ok=True, id=cur.lastrowid)

@app.delete("/api/admin/announcements/<int:aid>")
def ann_delete(aid):
    c = con(); c.execute("DELETE FROM announcements WHERE id=?", (aid,)); c.commit(); c.close(); return jsonify(ok=True)

@app.get("/api/admin/settings")
def settings_get():
    c = con(); d = {k: v for k, v in c.execute("SELECT key,value FROM settings")}; c.close(); return jsonify(ok=True, data=d)

@app.put("/api/admin/settings")
def settings_put():
    d = request.get_json(silent=True) or {}
    c = con()
    for k, v in d.items(): c.execute("INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (k, str(v)))
    c.commit(); c.close(); audit("admin", "settings_update", list(d.keys())); return jsonify(ok=True)

# ---------- config publish / rollback / audit ----------
@app.post("/api/admin/config/publish")
def config_publish():
    c = con()
    v = (c.execute("SELECT MAX(version) v FROM config_versions").fetchone()["v"] or 0) + 1
    snap = json.dumps(build_config(), sort_keys=True)
    c.execute("INSERT INTO config_versions(version,published_at,snapshot) VALUES(?,?,?)", (v, now(), snap))
    c.commit(); c.close(); audit("admin", "config_publish", {"version": v})
    return jsonify(ok=True, version=v)

@app.get("/api/admin/config/versions")
def config_versions():
    c = con(); out = [{"version": r["version"], "published_at": r["published_at"],
                       "size": len(r["snapshot"])} for r in c.execute("SELECT * FROM config_versions ORDER BY version DESC LIMIT 50")]
    c.close(); return jsonify(ok=True, data=out)

@app.post("/api/admin/config/rollback")
def config_rollback():
    d = request.get_json(silent=True) or {}
    v = d.get("version")
    c = con(); r = c.execute("SELECT * FROM config_versions WHERE version=?", (v,)).fetchone()
    if not r: return jsonify(error="not_found"), 404
    snap = json.loads(r["snapshot"])
    # restore base tables from snapshot (cards+servers rebuilt from config)
    c.execute("DELETE FROM app_servers"); c.execute("DELETE FROM app_cards")
    for card in snap["cards"]:
        cur = c.execute("INSERT INTO app_cards(id,name,description,logo_url,category_id,featured,label,enabled,sort,browser_mode,created,updated) VALUES(?,?,?,?,?,?,?,1,?,?,?,?)",
                        (card["id"], card["name"], card["description"], card["logo"], card["category"], 1 if card["featured"] else 0, card["label"], card["id"], card["browser"], now(), now()))
        cid = cur.lastrowid
        for i, s in enumerate(card["servers"]):
            c.execute("INSERT INTO app_servers(card_id,url,priority,is_default,enabled,maintenance) VALUES(?,?,?,?,1,0)", (cid, s["url"], s["priority"], 1 if s["is_default"] else 0))
    c.commit(); c.close(); audit("admin", "config_rollback", {"to": v})
    return jsonify(ok=True, restored_from=v)

@app.get("/api/admin/audit")
def audit_list():
    c = con(); out = [dict(r) for r in c.execute("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 200")]; c.close()
    return jsonify(ok=True, data=out)

@app.get("/api/admin/health-history")
def health_history():
    c = con(); out = [dict(r) for r in c.execute("SELECT * FROM server_health_history ORDER BY id DESC LIMIT 100")]; c.close()
    return jsonify(ok=True, data=out)

if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    app.run(host="0.0.0.0", port=port, threaded=True)
