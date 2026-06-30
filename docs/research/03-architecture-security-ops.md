# Evidex Architecture, Security & Operations Patterns

> Research dossier for the **Evidex** Paper 1.21 anti-cheat plugin (Kotlin) with an embedded NanoHTTPD web admin dashboard backed by SQLite/MySQL/Postgres/MariaDB via HikariCP, using PacketEvents + npc-lib, shaded with Gradle Shadow relocation.
>
> This document collects *patterns and pitfalls*, not product recommendations. Sources are cited inline.

---

## Table of Contents

1. [Embedding an Admin Web Panel Securely](#1-embedding-an-admin-web-panel-securely)
2. [Main-Thread Performance & Threading Model](#2-main-thread-performance--threading-model)
3. [Data Model, Concurrency & Retention](#3-data-model-concurrency--retention)
4. [Plugin Packaging: Shadow, Relocation & the Paper Loader](#4-plugin-packaging-shadow-relocation--the-paper-loader)
5. [Configuration & UX Patterns for Anti-Cheat Tooling](#5-configuration--ux-patterns-for-anti-cheat-tooling)

---

## 1. Embedding an Admin Web Panel Securely

An embedded HTTP admin panel inside a game server is one of the highest-value targets on the box: it can start/stop recordings, control replay (which spawns/moves NPCs and can desync clients), and delete forensic evidence. Treat it as a privileged control plane, not a convenience feature.

### 1.1 Network exposure: localhost-only is the default posture

The strongest and simplest mitigation is to **never bind the panel to a public interface**. Bind to `127.0.0.1` and require operators to reach it over an SSH tunnel, a VPN (e.g. WireGuard/Tailscale), or a reverse proxy that terminates TLS. This mirrors the long-standing guidance for RCON and other server control surfaces, where binding to localhost protects them from anything other than the trusted proxy ([PaperMC — Securing your servers](https://docs.papermc.io/velocity/security/); [Minecraft Server Security Checklist 2026](https://mineguard.pro/en/blog/minecraft-server-security-checklist-2026)).

The two production-grade access patterns:

| Pattern | How | Pros | Cons |
|---|---|---|---|
| **Localhost + SSH tunnel** | `ssh -L 8765:127.0.0.1:8765 user@host`, panel binds `127.0.0.1` | Zero public attack surface; reuses existing SSH auth/keys; no extra TLS to manage | Per-operator setup; awkward for non-technical staff |
| **Reverse proxy + TLS** | Nginx/Caddy on `:443` proxies to `127.0.0.1:8765`; Let's Encrypt cert auto-renewed | Browser-friendly; central auth/rate-limit/WAF layer; HTTPS for free with Caddy | Panel reachable from internet if proxy/firewall misconfigured; more moving parts |

Both Host Havoc and the MineGuard checklist stress that admin credentials must **never** be transmitted over plaintext, and that web control panels should be served over HTTPS with valid (auto-renewed Let's Encrypt) certificates ([Host Havoc — Securing Your Minecraft Server](https://hosthavoc.com/blog/secure-your-minecraft-server); [MineGuard](https://mineguard.pro/en/blog/minecraft-server-security-checklist-2026)). Community guidance is explicit that some plugins "open hidden ports" and that you must not expose something like `0.0.0.0:4242` directly to the internet without firewall/IP-whitelist/VPN rules ([advanced-mc-server-security-guide](https://github.com/sammwyy/advanced-mc-server-security-guide)).

**Recommended Evidex defaults:**

```yaml
web:
  enabled: true
  bind: 127.0.0.1      # NEVER default to 0.0.0.0
  port: 8765
  public-base-url: ""  # set only when behind a reverse proxy, drives Secure cookie + redirect URLs
  trusted-proxy: false # when true, honor X-Forwarded-For/Proto from the proxy only
```

### 1.2 What goes wrong when these panels hit the internet

- **Default/hardcoded credentials.** Shipping `admin/admin` or a baked-in token is the classic compromise. Force a first-run credential setup or generate a random password printed once to console; refuse to start the web server with default creds still in place.
- **Plaintext login over HTTP.** Credentials and session cookies sniffable on the wire; cookie replay = full takeover.
- **No rate limiting → credential stuffing / brute force** against the single admin account.
- **Directory/endpoint exposure** (recording files, SQLite DB downloads) without authz checks → forensic data and PII leak.
- **CSRF** triggering destructive actions (delete recordings, stop recording mid-incident) from a logged-in admin's browser.

### 1.3 Session & cookie hardening — and the NanoHTTPD caveat

The "golden standard" for an authenticating session cookie is `HttpOnly; Secure; SameSite=Lax` (or `Strict` where the UX allows), which together defend against XSS token theft, MITM, and CSRF ([Leapcell — Fortifying Sessions](https://leapcell.io/blog/fortifying-sessions-understanding-httponly-secure-and-samesite-for-robust-cookie-management); [Barrion — Cookie Security Guide](https://barrion.io/blog/cookie-security-best-practices)):

- **`HttpOnly`** — blocks `document.cookie` access, so an XSS payload cannot exfiltrate the session token ([howhttpworks](https://howhttpworks.com/guides/cookie-security)).
- **`Secure`** — cookie only sent over HTTPS. Set this whenever the panel is reached over TLS (i.e. via the reverse proxy).
- **`SameSite=Lax/Strict`** — primary CSRF mitigation for cross-site cookie sends; treated as defense-in-depth, *not* the sole CSRF control ([wanago.io — SameSite cookies](https://wanago.io/2020/12/21/csrf-attacks-same-site-cookies/)).

> **Critical NanoHTTPD limitation:** NanoHTTPD's built-in `CookieHandler` **does not support `path`, `Secure`, or `HttpOnly`** ([NanoHTTPD CookieHandler.java](https://github.com/NanoHttpd/nanohttpd/blob/master/core/src/main/java/org/nanohttpd/protocols/http/content/CookieHandler.java)). Do **not** rely on `CookieHandler` for the session cookie. Instead set the `Set-Cookie` header manually so you control all attributes:

```kotlin
// Build the cookie string yourself; do not use NanoHTTPD CookieHandler for the session.
val attrs = buildString {
    append("EVIDEX_SID="); append(sessionId)
    append("; HttpOnly; SameSite=Lax; Path=/")
    append("; Max-Age=").append(sessionTtlSeconds)
    if (servedOverHttps) append("; Secure")   // gate on public-base-url scheme / X-Forwarded-Proto
}
response.addHeader("Set-Cookie", attrs)
```

Session token rules:
- Generate the session ID from a CSPRNG (`java.security.SecureRandom`), ≥128 bits, and compare with a **constant-time** equality check.
- Store sessions server-side (in-memory map or DB) keyed by the token; never trust client-supplied identity claims.
- Idle + absolute timeouts; rotate the session ID on login (prevents session fixation); invalidate on logout.

### 1.4 CSRF protection

`SameSite` alone is defense-in-depth. For destructive endpoints (start/stop recording, delete recording, replay control) use the **Synchronizer Token Pattern**, which is the most comprehensive CSRF defense ([Spring Security reference](https://docs.spring.io/spring-security/site/docs/5.2.x/reference/html/features.html)):

- Issue a per-session (or per-request) CSRF token, embed it in forms / send via a custom header for fetch/XHR.
- Validate on every state-changing request (POST/PUT/DELETE); reject on mismatch.
- Combine with `SameSite=Lax` and, optionally, an `Origin`/`Referer` allow-check against `public-base-url`.
- Make all mutating actions non-GET (no destructive GET endpoints).

### 1.5 Login rate-limiting and lockout

There is one (or few) admin account(s), so brute force is the dominant threat. Implement:

- **Fixed/sliding-window throttle** per source IP **and** per username (so attackers can't rotate one to dodge the other).
- **Progressive backoff / temporary lockout** after N failures (e.g. exponential: 1s, 2s, 4s … then a cooling-off lock).
- **Constant-time** credential verification and identical error messages/timing for "bad user" vs "bad password" (no user enumeration).
- Log failed attempts and surface them in the panel + optional staff alert.
- When behind a reverse proxy, derive the client IP from `X-Forwarded-For` **only** when `trusted-proxy: true`, otherwise the limiter is trivially bypassed by spoofing the header.

### 1.6 Password hashing & credential storage

Per the [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), use a slow, memory-hard algorithm with a unique per-password salt:

| Algorithm | OWASP parameters | When to use |
|---|---|---|
| **Argon2id** (preferred) | ≥19 MiB memory, iterations=2, parallelism=1 | New apps; OWASP 2024+ default |
| **scrypt** | N=2^17, r=8, p=1 | If Argon2id unavailable |
| **bcrypt** | work factor ≥10, ≤72-byte password limit | Legacy/interop |
| **PBKDF2-HMAC-SHA256** | ≥600,000 iterations | FIPS-140 compliance required |

A unique salt per password defeats rainbow tables, and slow/memory-hard hashing makes offline brute force expensive ([OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)). In the JVM, a library like **Password4j** provides Argon2/bcrypt/scrypt/PBKDF2 ([password4j.com](https://password4j.com/)) — though note this is exactly the kind of dependency that interacts with shading/relocation (see §4). Store only the encoded hash (algorithm + params + salt + digest), never the plaintext or a reversible form.

> **Evidex status:** the current dashboard already uses PBKDF2-HMAC-SHA256 with a per-password salt and 65,536 iterations. OWASP's current floor for PBKDF2-SHA256 is **600,000** iterations — consider raising it, or migrating to Argon2id.

**Security checklist (panel):**

- [ ] Bind `127.0.0.1` by default; refuse `0.0.0.0` without an explicit opt-in flag + warning
- [ ] No default credentials; force first-run setup or one-time generated password
- [ ] Argon2id password hashing with per-user salt (or PBKDF2 ≥600k iterations)
- [ ] Manual `Set-Cookie` with `HttpOnly; SameSite=Lax; Secure(when TLS)` (not NanoHTTPD CookieHandler)
- [ ] CSRF tokens on all state-changing endpoints; no destructive GETs
- [ ] Per-IP + per-user login throttle with lockout/backoff; constant-time compare
- [ ] Session: CSPRNG token, server-side store, rotate on login, idle+absolute timeout
- [ ] Security headers: `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`
- [ ] Authz check on every endpoint including static/file downloads; path-traversal guard on recording file serving
- [ ] Audit log of admin actions (login, start/stop record, delete, replay)

---

## 2. Main-Thread Performance & Threading Model

### 2.1 The budget: 50 ms per tick, shared by everything

Paper runs the world at 20 TPS — **one tick = 50 ms**, and *every* plugin listener, entity update, and chunk operation shares that budget. `PlayerMoveEvent` is one of the hottest events on the server: it fires on **every** positional change of **every** player, so any expensive work in its listener multiplies across the player count and directly burns tick time, causing lag ([PaperMC Docs — Scheduling](https://docs.papermc.io/paper/dev/scheduler/)).

Mitigation patterns for hot listeners:

- **Early-out fast:** cheaply check whether the event matters (e.g. ignore pure head-rotation moves — `from.getX()==to.getX() && from.getY()==to.getY() && from.getZ()==to.getZ()`) and `return` before any heuristic runs.
- **Keep listeners lightweight:** the listener should *gather* data and *enqueue*, never compute heavy heuristics or touch I/O inline.
- **Set `ignoreCancelled`/priority** appropriately so already-cancelled moves don't re-run checks.

### 2.2 Never block the main thread

Any blocking call on the main thread (JDBC writes, HTTP, file I/O, `Thread.sleep`) freezes the *entire* server for its duration — all players, all worlds. The rule: **DB writes and network I/O must be off-thread.**

But there is a hard counter-rule: **async tasks must never touch the Bukkit API** (entities, worlds, players, blocks). Thread-safety is the developer's responsibility ([PaperMC forums — Async Methods](https://forums.papermc.io/threads/async-methods.1141/); [Bukkit Scheduler Programming](https://bukkit.fandom.com/wiki/Scheduler_Programming)). The canonical shape:

```
main thread (listener)         async thread                 main thread
─────────────────────         ─────────────                ───────────
read player state    ──enqueue──►  batch + write to DB
(snapshot primitives)              (HikariCP, JDBC)
                                   compute heavy heuristic
                          ──result──►                 apply Bukkit-side
                                                       effect (alert, flag)
```

- Heuristics that need world state must read **snapshots of primitives** (doubles, booleans) captured on the main thread, then run math async.
- Anything that calls back into Bukkit (kick, message, NPC spawn for replay) must hop **back** to the main thread (`runTask`).

### 2.3 Schedulers (Bukkit/Paper)

```kotlin
val s = Bukkit.getScheduler()
s.runTask(plugin) { /* main-thread Bukkit API */ }
s.runTaskAsynchronously(plugin) { /* DB write, no Bukkit API */ }
s.runTaskTimerAsynchronously(plugin, /*delay*/0L, /*period*/100L) { flushBatch() }
```

> **Thread-pool footgun:** Bukkit's async scheduler pool has **no maximum thread limit**. Firing `runTaskAsynchronously` hundreds/thousands of times (e.g. one task per move event per player) can spawn unbounded threads and trigger `OutOfMemoryError` ([Paper Discussion #10550](https://github.com/PaperMC/Paper/discussions/10550)). **Do not** spawn an async task per violation/frame. Instead use a **bounded executor you own** (or a single repeating async flush task) and a queue.

### 2.4 Batching DB writes

For an anti-cheat continuously recording violations and replay frames, per-row inserts are the death of throughput. Pattern:

- **In-memory ring buffer / concurrent queue** (e.g. `ConcurrentLinkedQueue` or a bounded `ArrayBlockingQueue` with back-pressure) populated from the main thread.
- A **single repeating async flush task** drains the queue and writes with JDBC **batch inserts** (`addBatch()`/`executeBatch()`) inside one transaction every N ms (e.g. 1–5 s) or every N rows, whichever comes first.
- Bound the queue and define an overflow policy (drop-oldest frames, or pause recording) so a stalled DB never OOMs the server.

### 2.5 Folia considerations

If Evidex should support Folia (regionised multithreading), the threading model changes fundamentally. Folia splits the world into independent regions each on its own thread and **replaces the BukkitScheduler** with four schedulers ([Folia Plugin Development — DeepWiki](https://deepwiki.com/PaperMC/Folia/3-plugin-development); [PaperMC — Supporting Paper and Folia](https://docs.papermc.io/paper/dev/folia-support/)):

| Scheduler | Use for |
|---|---|
| **`GlobalRegionScheduler`** | Server-wide tasks not tied to a location/entity |
| **`RegionScheduler`** | Tasks for a specific world location |
| **`EntityScheduler`** (`Entity#getScheduler`) | Tasks on an entity — **follows the entity across regions** |
| **`AsyncScheduler`** | Location-independent async work (DB/network) |

Key migration facts:
- Declare support with `folia-supported: true` in `plugin.yml`.
- Code in one region **cannot touch another region's** entities/blocks; events and commands may run on **different threads**, so all shared plugin state (violation maps, session store, the write queue) must be **thread-safe**.
- `Bukkit#isOwnedByCurrentRegion(...)` tests whether the current ticking region owns a position/entity before acting on it.
- For Evidex's **NPC-based replay**, NPC spawning/movement must run on the scheduler that owns the relevant region/entity.

A common compatibility approach is a thin scheduler-abstraction layer that delegates to Bukkit schedulers on Paper and to the region/entity schedulers on Folia.

---

## 3. Data Model, Concurrency & Retention

### 3.1 Schema shape: violations vs. time-series replay frames

Two very different write profiles:

- **Violations** — relatively low-frequency, append-mostly, queried/displayed live. Indexed by player UUID + timestamp + check type.
- **Replay frames** — high-frequency time-series (potentially every tick per recorded suspect). Write-heavy, read in bulk per recording.

Suggested logical model:

```sql
recordings(
  id, player_uuid, server, world, started_at, ended_at,
  reason, started_by, frame_count
);
violations(
  id, player_uuid, check_name, vl REAL, server, world,
  created_at, recording_id NULL REFERENCES recordings(id),
  detail TEXT /* JSON */
);
replay_frames(
  recording_id REFERENCES recordings(id),
  tick INTEGER, ts BIGINT,
  x REAL, y REAL, z REAL, yaw REAL, pitch REAL,
  flags INTEGER
  /* PK (recording_id, tick); index on recording_id */
);
```

Store frames as compact primitives (or a packed binary/columnar blob per N ticks) rather than wide JSON rows — replay frame volume dominates storage.

### 3.2 SQLite vs Postgres: the concurrency cliff

This is the central engine decision and it hinges on **concurrent writes**:

- **SQLite requires an exclusive lock over the entire database for writes — only a single writer at a time** ([tenthousandmeters — SQLite concurrent writes](https://tenthousandmeters.com/blog/sqlite-concurrent-writes-and-database-is-locked-errors/); [oldmoe — The Write Stuff](https://oldmoe.blog/2024/07/08/the-write-stuff-concurrent-write-transactions-in-sqlite/)). Even in **WAL mode**, readers proceed during a write, but there is **still only one writer**.
- At **~100+ concurrent writers**, SQLite shows a significant performance drop or throws **`database is locked`** errors ([Hacker News](https://news.ycombinator.com/item?id=45508462)).
- **Postgres uses MVCC + row-level locking**, so many transactions write different rows simultaneously without blocking ([Coddy — SQLite vs Postgres](https://coddy.tech/docs/sqlite/sqlite-vs-postgres)).

Implications for Evidex:

| Engine | Verdict for Evidex |
|---|---|
| **SQLite** | Fine for small/single servers **only if** writes are funneled through **one serialized writer thread** + WAL mode + the batching in §2.4. Avoid HikariCP pools > 1 effective writer. Plan for `database is locked` handling (busy_timeout, retry/backoff). |
| **Postgres/MariaDB/MySQL** | Required once you have high frame-rate recording, multiple servers writing to one DB, or concurrent admin reads + heavy writes. |

WAL pragmas for SQLite (`PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA busy_timeout=...`) plus the single-writer discipline are the standard way to push SQLite as far as it goes ([SkyPilot — Abusing SQLite for Concurrency](https://blog.skypilot.co/abusing-sqlite-to-handle-concurrency/)).

### 3.3 HikariCP pool sizing

The widely-cited HikariCP formula:

```
pool_size = (core_count * 2) + effective_spindle_count
```

So a 4-core server with SSD → `(4*2)+1 = 9`; an 8-core SSD server → `(8*2)+1 = 17`. Postgres throughput **peaks** near this number, and **adding more connections beyond it decreases total TPS** ([Gold Lapel — HikariCP Pool Sizing for Postgres](https://goldlapel.com/grounds/connection-pooling/hikaricp-pool-sizing-postgres)).

Practical Evidex guidance:
- **SQLite:** effectively `maximumPoolSize = 1` for writes, since the engine serializes writes anyway.
- **Postgres/MariaDB on the same small VPS:** a *small* pool (e.g. 5–10), not a large one.
- Set `connectionTimeout`, `maxLifetime`, `idleTimeout`, and validation; never leak connections (`use {}`).

### 3.4 Retention & cleanup

Replay frames grow without bound; you need an enforced retention policy.

- **Avoid mass `DELETE` on huge tables.** A `DELETE` scans indexes, visits every matching row, marks dead tuples, and on large tables can take minutes-to-hours and generate gigabytes of WAL ([Data Egret](https://dataegret.com/2025/05/data-archiving-and-retention-in-postgresql-best-practices-for-large-datasets/)).
- **Prefer partition-drop on Postgres.** Time-based partitioning lets you drop an entire old partition as a near-instant metadata operation ([OneUptime](https://oneuptime.com/blog/post/2026-01-26-time-based-partitioning-postgresql/view); [Tiger Data](https://www.tigerdata.com/blog/moving-from-row-deletes-to-instant-data-retention)). `pg_partman` + `pg_cron` automate it.
- **SQLite / simpler deployments:** run **scheduled, batched** deletes (chunked by id/time ranges) during low-traffic windows from the async scheduler, and `VACUUM`/`PRAGMA incremental_vacuum` periodically. Keep batches small to avoid long write-locks ([Stormatics](https://stormatics.tech/blogs/best-practices-for-timescaledb-massive-delete-operations)).
- Tiering: keep **violations** longer (cheap, evidentiary) than **raw frames** (expensive). E.g. frames 14–30 days, violation summaries 90+ days, configurable.

### 3.5 GDPR / privacy of behavioral recording

Recording a player's movement/behavior is processing of **personal data** (especially when tied to a UUID/username and when IPs are logged). Key obligations:

- **Lawful basis.** Anti-cheat enforcement is commonly justified under **legitimate interest**, but you must perform a balancing test against the players' privacy rights ([GDPR EU — gaming](https://www.gdpreu.org/gaming-harassment-and-your-digital-rights-a-gdpr-perspective/); [heyData — Gaming GDPR 2025](https://heydata.eu/en/magazine/gaming-gdpr-risks-are-rising-and-these-2025-cases-prove-it)).
- **Data minimization.** Collect only what the anti-cheat function needs; don't record more behavior, for longer, than necessary.
- **IP logging.** IPs are personal data; logging them needs a legitimate security need and a defined retention period ([Optimize Smart](https://optimizesmart.com/blog/gdpr-ip-address-logging-retention-and-monitoring/); [TermsFeed](https://www.termsfeed.com/blog/gdpr-log-data/)).
- **Practical controls to build into Evidex:** configurable retention (§3.4), the ability to **purge a specific player's data** (right to erasure) and **export it** (access request), a documented retention default, and clear server-owner-facing documentation that *they* are the data controller and must inform players. Pseudonymise where possible (store UUID, avoid unnecessary IP capture in frames).

---

## 4. Plugin Packaging: Shadow, Relocation & the Paper Loader

### 4.1 Always relocate PacketEvents

PacketEvents explicitly instructs you to shade **and relocate** it, because multiple plugins shading the same library will clash if they share package names. Relocate **both** packages ([PacketEvents — Shading](https://github.com/retrooper/packetevents/wiki/Shading-PacketEvents); [PacketEvents — Bundling guide](https://docs.packetevents.com/setup-when-bundling/a-beginners-guide-to-bundling/)):

```kotlin
tasks.shadowJar {
    relocate("com.github.retrooper.packetevents", "com.yourname.evidex.libs.packetevents")
    relocate("io.github.retrooper.packetevents", "com.yourname.evidex.libs.packetevents.impl")
    minimize() // drop unused classes -> much smaller jar (PacketEvents supports this)
}
```

The `spigot` PacketEvents module works on Paper, Purpur, and **Folia** ([mvnrepository — packetevents-spigot](https://mvnrepository.com/artifact/com.github.retrooper/packetevents-spigot/2.7.0)). `minimize()` can dramatically shrink the jar.

### 4.2 The Kotlin stdlib relocation trap

**Do not relocate the Kotlin standard library** if you use Kotlin **metadata** or **reflection** — they are tightly coupled to the Kotlin compiler/runtime and break under relocation ([Shadow — Kotlin Plugins](https://gradleup.com/shadow/kotlin-plugins/)). This is directly relevant to Evidex (Kotlin plugin).

> **Evidex status:** the current `build.gradle.kts` does `relocate("kotlin", "com.evidex.lib.kotlin")`. If Evidex uses any Kotlin reflection or `@Metadata`-dependent feature this can break at runtime — verify, and prefer **bundling stdlib un-relocated**.

### 4.3 JDBC drivers: shade vs. Paper `libraries` loader — fighting jar bloat

Bundling four JDBC drivers (SQLite, MySQL, MariaDB, Postgres) + HikariCP bloats the jar enormously, and most users only need one driver. Two strategies:

**(a) `plugin.yml` `libraries` (Bukkit-style runtime download).** Declare Maven coordinates that Paper downloads from Maven Central at runtime and adds to the classpath — **no shading/relocation needed** ([PaperMC — plugin.yml](https://docs.papermc.io/paper/dev/plugin-yml/)). Downloads **all** declared libraries.

**(b) Paper plugins + `PluginLoader` + `MavenLibraryResolver` (most flexible).** Paper plugins declare a loader class in `paper-plugin.yml`; the loader builds the classpath and supplies external libraries at runtime ([PaperMC — Paper plugins](https://docs.papermc.io/paper/dev/getting-started/paper-plugins/)). This lets you **resolve only the driver the operator actually configured**, avoiding both jar bloat and downloading all four drivers.

| Approach | Jar size | Relocation needed | Downloads | Notes |
|---|---|---|---|---|
| Shade all drivers | Huge | Yes | None | Worst bloat; offline-friendly |
| `plugin.yml libraries` | Lean | No | All declared libs at startup | Simple; can't be conditional |
| Paper `PluginLoader` + resolver | Lean | No | Only configured driver | Most flexible; Paper-plugin only |

For Evidex, **(b)** is the strongest fit: shade+relocate **PacketEvents and npc-lib**, but resolve **JDBC driver + HikariCP** via the loader so only the configured engine downloads.

### 4.4 api-version / Paper API pinning

- `api-version` declares which Paper/Bukkit API version the plugin targets. Since **1.20.5** Paper supports a **minor** version too (e.g. `1.21.1`) ([PaperMC — plugin.yml](https://docs.papermc.io/paper/dev/plugin-yml/)). Pin it to your tested target.
- Use `paper-api` (`io.papermc.paper:paper-api:1.21.x-R0.1-SNAPSHOT`) as `compileOnly`; never shade it.
- Tools like Minecrell's **plugin-yml** Gradle plugin generate `plugin.yml`/`paper-plugin.yml` from the build ([Minecrell/plugin-yml](https://github.com/Minecrell/plugin-yml)).

> **Evidex status:** `build.gradle.kts` pins `paperApiVersion = "26.2.build.31-alpha"` and `gradle.properties` (now removed) referenced `minecraft_version=26.2`. There is no Minecraft "26.2"; the real coordinate is `1.21.x-R0.1-SNAPSHOT`. Pin to the actual tested version.

**Packaging checklist:**

- [ ] Relocate `com.github.retrooper.packetevents` **and** `io.github.retrooper.packetevents`
- [ ] `minimize()` the shadow jar
- [ ] Do **not** relocate Kotlin stdlib; bundle it un-relocated
- [ ] Resolve JDBC driver(s) + HikariCP via Paper `PluginLoader`/`MavenLibraryResolver` (conditional on config) instead of shading all four
- [ ] `paper-api` as `compileOnly`, never shaded; pin to a **real** version
- [ ] Pin `api-version` to tested target (`1.21`)
- [ ] `folia-supported: true` only if the scheduler abstraction is actually implemented

---

## 5. Configuration & UX Patterns for Anti-Cheat Admin Tooling

Patterns drawn from how established Minecraft anti-cheats (Vulcan, Grim, Guardian, Atra) are configured ([SpigotMC — Atra](https://www.spigotmc.org/resources/atra-anti-cheat-advanced-anti-cheat-system-1-8-1-21-4.130303/); [Vulcan config](https://builtbybit.com/resources/vulcan-anti-cheat-configuration.96433/); [GameTeam](https://gameteam.io/blog/minecraft-server-anti-cheat-protection-systems/); [Wabbanode](https://wabbanode.com/help/minecraft/how-to-configure-anti-cheat-on-your-minecraft-server)).

### 5.1 Per-check enable + Violation Level (VL) + thresholds

The dominant model is per-check configuration with a **Violation Level** that accumulates on flags and **decays** over time during normal behavior; staff are alerted and punishments triggered when VL crosses configurable thresholds:

```yaml
checks:
  speed:
    enabled: true
    sensitivity: 1.0        # multiplier: higher = stricter/more sensitive
    vl-decay: 0.5           # VL removed per decay interval of clean play
    thresholds:
      alert: 5              # notify staff at VL >= 5
      setback: 10           # cancel/teleport-back
      punish: 20            # run punishment command
    actions:
      punish: ["kick %player% Suspected speed"]
  reach:
    enabled: false
```

### 5.2 Sensitivity multipliers & latency/TPS compensation

A single **sensitivity** multiplier per check lets operators tune strictness without editing internal math. Crucially, anti-cheats minimize false positives by **compensating for ping and TPS** — automatically loosening checks under high latency or low server TPS so lag isn't mistaken for cheating ([GameTeam](https://gameteam.io/blog/minecraft-server-anti-cheat-protection-systems/)):

```yaml
compensation:
  ping-compensation: true
  max-ping-ms: 300          # above this, soften/skip movement checks
  tps-compensation: true
  min-tps: 18.0             # below this, soften/skip
```

### 5.3 Exemptions: permissions, worlds, gamemodes

Staff using `/fly`, `/teleport`, `/vanish`, or creative mode will trip movement checks, so **bypass permissions** (e.g. `vulcan.bypass`) are added to staff ranks ([Wabbanode](https://wabbanode.com/help/minecraft/how-to-configure-anti-cheat-on-your-minecraft-server)). Provide multi-axis exemptions:

```yaml
exempt:
  permission: "evidex.bypass"
  gamemodes: [CREATIVE, SPECTATOR]
  worlds: ["creative_plots", "build"]
  on-teleport-ticks: 20              # grace window after TP/respawn/velocity
  log-exemptions: true
```

Also exempt transient states (recent teleport, respawn, vehicle exit, elytra, knockback) for a few ticks to kill the most common false positives.

### 5.4 Alert cooldowns & anti-spam

A flagging cheater can flood staff chat. The established control is to **suppress alerts above a max VL** ([Wabbanode](https://wabbanode.com/help/minecraft/how-to-configure-anti-cheat-on-your-minecraft-server)):

```yaml
alerts:
  cooldown-ms: 1000                  # min gap between alerts for same player+check
  ignore-alerts-over-vl: 50          # stop spamming once past this VL
  aggregate-window-ms: 5000          # "x12 Speed in 5s" rather than 12 lines
```

### 5.5 Staff alert routing

Route alerts to multiple sinks, each toggleable: **in-game** to players with `evidex.alerts`; **the web dashboard** (live feed); **external** (Discord webhook, console log).

```yaml
routing:
  ingame: { permission: "evidex.alerts", default-on: true }
  discord: { enabled: false, webhook-url: "", min-vl: 10 }
  console: true
```

### 5.6 General UX principles

- **Safe defaults that don't false-positive** out of the box; document each check's purpose and the cost of over-tightening.
- **Hot-reload** config without restart where possible.
- **Per-check verbose/debug** mode for tuning, gated so it doesn't spam production.
- **Recording linkage:** auto-start a replay recording when a player crosses an `investigate` VL threshold so the suspicious window is captured for later POV replay.
- **Audit everything destructive** (recording deletes, manual bans from the panel).

---

## Consolidated References

**Web panel security**
- PaperMC — Securing your servers: https://docs.papermc.io/velocity/security/
- MineGuard — Minecraft Server Security Checklist 2026: https://mineguard.pro/en/blog/minecraft-server-security-checklist-2026
- Host Havoc — Securing Your Minecraft Server: https://hosthavoc.com/blog/secure-your-minecraft-server
- advanced-mc-server-security-guide: https://github.com/sammwyy/advanced-mc-server-security-guide
- NanoHTTPD CookieHandler (no Secure/HttpOnly): https://github.com/NanoHttpd/nanohttpd/blob/master/core/src/main/java/org/nanohttpd/protocols/http/content/CookieHandler.java
- OWASP Password Storage Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- Password4j: https://password4j.com/
- Spring Security (Synchronizer Token): https://docs.spring.io/spring-security/site/docs/5.2.x/reference/html/features.html

**Threading & performance**
- PaperMC Docs — Scheduling: https://docs.papermc.io/paper/dev/scheduler/
- PaperMC forums — Async Methods: https://forums.papermc.io/threads/async-methods.1141/
- Paper Discussion #10550 (unbounded async pool / OOM): https://github.com/PaperMC/Paper/discussions/10550
- Folia Plugin Development (DeepWiki): https://deepwiki.com/PaperMC/Folia/3-plugin-development
- PaperMC — Supporting Paper and Folia: https://docs.papermc.io/paper/dev/folia-support/

**Data model, concurrency & retention**
- SQLite concurrent writes & "database is locked": https://tenthousandmeters.com/blog/sqlite-concurrent-writes-and-database-is-locked-errors/
- oldmoe — Concurrent write transactions in SQLite: https://oldmoe.blog/2024/07/08/the-write-stuff-concurrent-write-transactions-in-sqlite/
- Coddy — SQLite vs Postgres: https://coddy.tech/docs/sqlite/sqlite-vs-postgres
- SkyPilot — Abusing SQLite to Handle Concurrency: https://blog.skypilot.co/abusing-sqlite-to-handle-concurrency/
- Gold Lapel — HikariCP Pool Sizing for Postgres: https://goldlapel.com/grounds/connection-pooling/hikaricp-pool-sizing-postgres
- OneUptime — Time-based partitioning in Postgres: https://oneuptime.com/blog/post/2026-01-26-time-based-partitioning-postgresql/view
- Tiger Data — Instant data retention: https://www.tigerdata.com/blog/moving-from-row-deletes-to-instant-data-retention
- GDPR EU — Gaming & digital rights: https://www.gdpreu.org/gaming-harassment-and-your-digital-rights-a-gdpr-perspective/

**Packaging**
- PacketEvents — Shading: https://github.com/retrooper/packetevents/wiki/Shading-PacketEvents
- Shadow — Kotlin Plugins (don't relocate stdlib): https://gradleup.com/shadow/kotlin-plugins/
- Shadow — Relocation: https://gradleup.com/shadow/configuration/relocation/
- PaperMC — Paper plugins (PluginLoader / MavenLibraryResolver): https://docs.papermc.io/paper/dev/getting-started/paper-plugins/
- PaperMC — plugin.yml (libraries, api-version): https://docs.papermc.io/paper/dev/plugin-yml/
- Minecrell/plugin-yml: https://github.com/Minecrell/plugin-yml

**Anti-cheat configuration & UX**
- SpigotMC — Atra Anti-Cheat: https://www.spigotmc.org/resources/atra-anti-cheat-advanced-anti-cheat-system-1-8-1-21-4.130303/
- BuiltByBit — Vulcan Anti-Cheat Configuration: https://builtbybit.com/resources/vulcan-anti-cheat-configuration.96433/
- GameTeam — Minecraft Server Anti-Cheat Protection Systems: https://gameteam.io/blog/minecraft-server-anti-cheat-protection-systems/
- Wabbanode — How to Configure Anti-Cheat: https://wabbanode.com/help/minecraft/how-to-configure-anti-cheat-on-your-minecraft-server
