# Server-Side POV Replay & Evidence System for Minecraft Java (Paper 1.21) — Research & Design Patterns

> Scope: Patterns for building a **no-client-mod** "replay / POV evidence" system in a Kotlin Paper plugin (codename **Evidex**). The plugin records a suspect's state each tick into a database + frame files, then plays it back to a single online admin by driving the admin's camera and spawning per-viewer fake entities/NPCs via **npc-lib** (juliarn) and **PacketEvents** (retrooper).
>
> This is a self-contained design/reference document. Code is pseudocode unless noted. Protocol values target the 1.21.x family; always confirm exact metadata indices/packet IDs against the protocol wiki for your precise Paper build, since they shift between minor versions.

---

## 1. How packet-based replay works

### 1.1 The two philosophies: packet-stream vs. state-stream

There are two fundamentally different ways to record Minecraft, and Evidex must consciously pick one.

| Approach | What is stored | Examples | Fit for Evidex |
|---|---|---|---|
| **Packet-stream replay** | The raw clientbound packet stream, with timestamps, exactly as a client received it. Playback = a fake "client" that re-feeds packets. | ReplayMod (`.mcpr`) | Faithful, but client-side; ill-suited to server-side single-viewer playback |
| **State-stream replay** | A semantic, per-tick snapshot of *what each tracked subject was doing* (pos, rot, pose, item, animation, block edits). Playback = reconstruct entities/blocks server-side and push them to one viewer. | AdvancedReplay (server plugin) | **This is the Evidex model** |

ReplayMod is a **client mod** that injects a Netty channel handler at the raw and decoded packet stages, saving every server→client packet with a timestamp into the `.mcpr` archive, plus *synthetic* packets for client-only events (local player movement/rotation, sounds, initial spawn) that the server never actually transmits ([ReplayMod Recording System, DeepWiki](https://deepwiki.com/ReplayMod/ReplayMod/2.1-recording-system)). A `.mcpr` is essentially a ZIP containing `recording.tmcpr` (the timestamped packet blob), `metaData.json` (MC/protocol version, server, markers), resource packs by hash, and thumbnail ([replaymod.com/docs](https://www.replaymod.com/docs/)).

Evidex **cannot** use the ReplayMod model directly, because:
- It has no client mod to capture the suspect's *local* first-person packets (the very POV data we want).
- Playback would require feeding a packet stream into a real client, which a vanilla client won't accept selectively.

Therefore Evidex follows the **AdvancedReplay** pattern: record players, living entities, items and projectiles as semantic state, save to file or DB, and replay by reconstructing the scene server-side for a viewer ([AdvancedReplay, GitHub](https://github.com/Jumper251/AdvancedReplay) / [SpigotMC](https://www.spigotmc.org/resources/advancedreplay-1-8-1-21.52849/)). AdvancedReplay records "almost every action a player does," supports MySQL or S3 storage, configurable max length (default 3600 s), and exposes an API to add custom recorded data.

### 1.2 What must be captured per tick to reconstruct a POV

A faithful first-person reconstruction needs the subject's *kinematic*, *postural*, *equipment*, and *event* state. The minimum viable per-tick (or per-keyframe) field set:

| Category | Fields | Source on server | Notes |
|---|---|---|---|
| Position | x, y, z (double) | `player.getLocation()` | The camera anchor |
| Body rotation | yaw, pitch (float) | `location.getYaw/Pitch()` | Drives the *camera* direction in POV |
| Head rotation | head yaw (float) | same as look yaw for players | For 3rd-person NPC head; for POV equals look |
| Pose / posture | sneaking, sprinting, swimming, gliding, crawling pose | `isSneaking()`, `isSprinting()`, `getPose()` | Metadata flags (§ "shared flags") |
| Held item / hotbar | main hand item, selected slot, offhand, full hotbar | `getInventory()` | Drives held-item render + HUD bar |
| Equipment | helmet/chest/legs/boots | `getEquipment()` | For 3rd-person NPC; minor in POV |
| Animations | arm swing (attack/use), hurt, item-use start/stop | `PlayerAnimationEvent`, damage events | Discrete events, not continuous |
| Combat / interaction | attacks (target entity id), block break/place, damage taken/dealt | Bukkit events | The "evidence" payload |
| World context | nearby entities (id, type, pos, rot, equip), block changes | entity tracker + `BlockPlace/BreakEvent` | Needed to see *what the suspect saw* |

**Capture rate vs. interpolation tradeoff.** Minecraft's network model itself only sends absolute player positions ~ every other tick and otherwise sends delta/relative moves; the client *interpolates* between updates ([ReplayMod docs / Recording System](https://deepwiki.com/ReplayMod/ReplayMod/2.1-recording-system)). This gives Evidex three rate strategies:

- **Full 20 Hz capture** — one frame every tick. Highest fidelity, biggest storage. Recommended for the suspect being investigated (the "primary" subject).
- **Reduced keyframe capture (5–10 Hz) + interpolation on playback** — store every 2nd–4th tick; the playback engine LERPs position and SLERPs/short-angle-interpolates rotation between keyframes. This matches vanilla behavior (the real client also interpolates), so it looks natural. Recommended for *nearby* entities/context.
- **Event-driven delta** — store a frame only when something changed beyond a threshold (moved > ε, rotated > ε, pose changed, item changed, discrete event fired). Massive storage savings for idle players.

> **Design rule for Evidex:** capture the *primary suspect* at full tick rate with deltas, and capture *context* entities at a reduced keyframe rate with interpolation. Always store discrete events (hits, block edits, animations) at the exact tick they happened regardless of the position keyframe schedule.

**Interpolation detail (rotation).** Yaw must be interpolated with shortest-arc wrapping (`-180..180`), never naïve linear, or the camera spins the wrong way crossing the ±180 boundary:

```kotlin
fun lerpAngle(a: Float, b: Float, t: Float): Float {
    var d = (b - a) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return a + d * t
}
```

---

## 2. Server-side rendering of recorded entities to a single viewer

### 2.1 The per-viewer fake-entity model

To replay without a client mod, Evidex spawns **fake entities** (NPCs and non-player entities) that *exist only as packets sent to the watching admin*. They are never registered in the server world, so:

- Other online players never see them (packets are sent only to the viewer).
- They cannot be hit, collide, or affect the world.
- They are cheap and fully controllable frame-by-frame.

Both libraries support this per-player model:

- **npc-lib** (juliarn) builds NPCs via `NPCPool` / builder, supports per-player visibility (`npc.isShownFor(player)`, `npc.show(player, plugin, tabListRemoveTicks)`), and works on Bukkit/Folia via either **ProtocolLib or PacketEvents**. It exposes `npc.platform().packetFactory()` to craft and schedule custom packets to specific players ([npc-lib, GitHub](https://github.com/juliarn/NPC-Lib), [README v3](https://github.com/juliarn/NPC-Lib/blob/v3/README.md)).
- **EntityLib** (Tofaa2, PacketEvents-based) models *non-player* fake entities with an explicit viewer set: `entity.addViewer(user)`, `entity.removeViewer(user)`, plus `entity.teleport(loc)`, `entity.rotateHead(yaw,pitch)`, `entity.remove()`, and metadata via `WrapperPlayServerEntityMetadata` ([EntityLib, GitHub](https://github.com/Tofaa2/EntityLib), [Hangar](https://hangar.papermc.io/Tofaa2/EntityLib)). Note its docs flag that automatic viewer packet-sync is still maturing ("WrapperEntities must seamlessly send packet updates to viewers, currently they are not") — so Evidex should drive updates explicitly each frame rather than relying on auto-sync.

> **Recommendation:** Use **npc-lib for human-shaped subjects** (the suspect and nearby players — it handles skins, tab-list, and player-info correctly) and **EntityLib (or raw PacketEvents wrappers) for non-player context entities** (mobs, items, projectiles). Both ride on the same PacketEvents instance, avoiding two packet stacks.

### 2.2 The packet vocabulary

A single fake player NPC requires this packet sequence, all sent **only to the viewer**:

| Action | PacketEvents wrapper | Notes |
|---|---|---|
| Register profile (player NPC) | `WrapperPlayServerPlayerInfoUpdate` (ADD_PLAYER, with GameProfile + skin property) | Needed before spawn so skin renders ([packetevents #1400](https://github.com/retrooper/packetevents/issues/1400)) |
| Spawn | `WrapperPlayServerSpawnEntity` (and for players, the spawn is via player info + spawn entity in modern protocol) | Allocate a unique entity id + UUID |
| Position update | `WrapperPlayServerEntityTeleport` (absolute) or `WrapperPlayServerEntityRelativeMove` / `...RotationHeadLook` (delta) | Use relative for small moves, absolute to resync |
| Body+head look | `WrapperPlayServerEntityHeadLook` + rotation in move packet | Head yaw is separate from body yaw |
| Metadata (pose/flags) | `WrapperPlayServerEntityMetadata` | Sneak, sprint, swim, pose, etc. |
| Equipment | `WrapperPlayServerEntityEquipment` | Held item + armor |
| Animation | `WrapperPlayServerEntityAnimation` | Swing arm = 0/`SWING_MAIN_ARM`; hurt etc. |
| Despawn | `WrapperPlayServerDestroyEntities` | At replay end / out of range |
| Tab-list cleanup | `WrapperPlayServerPlayerInfoRemove` | Remove ghost from tab list after a short delay |

**Known pitfall — head rotation on fake player NPCs.** There is a documented PacketEvents issue where `WrapperPlayServerEntityHeadLook` sent to fake player NPCs produces no visible rotation on 1.21.10, even though no error is thrown ([packetevents #1400](https://github.com/retrooper/packetevents/issues/1400)). Mitigations: send head yaw both in the rotation-bearing move packet *and* the dedicated head-look packet, and verify against your exact server build. This is a real risk for an evidence tool where head direction is the whole point — budget test time for it.

### 2.3 Entity ID allocation (avoiding collisions)

Fake entities must use IDs that will never collide with real server entities, or the viewer's client will mix them up (your fake NPC teleporting a real cow, etc.).

Strategies, best to worst:

1. **Negative or very-high reserved range.** The vanilla server assigns entity ids from a counter starting low and counting up. Allocating from a high reserved band (e.g. starting near `Int.MAX_VALUE` and decrementing) makes practical collision extremely unlikely for normal server lifetimes. EntityLib centralizes this via an injectable `EntityIdProvider` (`EntityLib.getPlatform().getEntityIdProvider().provide()`), so all fake ids come from one source ([EntityLib](https://github.com/Tofaa2/EntityLib)).
2. **Mirror the real counter via reflection** — read Bukkit/NMS `Entity.ENTITY_COUNTER` and atomically claim ids above the current value. More correct but version-fragile.
3. **Single shared allocator in Evidex** — one `AtomicInteger`, all NPC libs configured to draw from it, so npc-lib and EntityLib never overlap with each other.

> **Rule:** route *every* fake-entity id (npc-lib NPCs, EntityLib entities, fake item frames, etc.) through one Evidex allocator backed by a high reserved range. Also generate fresh random UUIDs per fake player to avoid tab-list/skin cache collisions.

### 2.4 Isolation (only the admin sees the replay)

Isolation is automatic *if and only if* every packet is addressed to the viewer's `User`/`Player` and the entity is never added to the world. Concretely:

- Never call `world.spawnEntity(...)` or any Bukkit spawn — only send packets.
- npc-lib: use `build()` (raw, you control who it shows to) rather than world-tracked spawns, and only `show(viewer, ...)`.
- EntityLib: only `addViewer(viewerUser)`; never add other players.
- Block changes (§4): send block packets only to the viewer.
- Sounds/particles during replay: send via per-player `player.playSound`/`spawnParticle` overloads (which target only that player), never world-wide variants.

---

## 3. Camera control of the viewing admin

### 3.1 Spectator-camera vs. forced-position — pick the hybrid

Two mechanisms exist; each has a sharp edge.

**Option A — Spectate a fake entity (Set Camera Entity packet).** The clientbound *Camera* packet ("Set Camera Entity") sets the entity the player renders from; the camera then *moves and looks with that entity*, and the player cannot move it (their movement packets are treated as the camera entity's). Reset by sending the packet with the player's own entity id ([Minecraft Wiki — Packets](https://minecraft.wiki/w/Java_Edition_protocol/Packets); [SpigotMC discussion](https://www.spigotmc.org/threads/how-to-effieciently-set-the-camera-spectate-an-entity-but-control-player.574719/)).

- Pros: smooth client-side interpolation "for free" — you only move the spectated fake entity, and the client tweens the camera. Yaw/pitch follow the entity, giving a true hands-off POV.
- Cons: *"If the given entity is not loaded by the player, this packet is ignored"* — you must guarantee the fake camera entity is spawned and in a loaded chunk for the viewer first. The vanilla server resets the camera when the spectated entity dies or the player sneaks ([Minecraft Wiki — Packets](https://minecraft.wiki/w/Java_Edition_protocol/Packets)), so you must intercept/re-assert on those events.

**Option B — Force position via teleport packets each frame.** Send `WrapperPlayServerPlayerPositionAndLook` (a.k.a. Synchronize/Player Position) each frame with the recorded pos+yaw+pitch.

- Pros: total control of exact pose; no dependency on a camera entity.
- Cons: the teleport handshake — *"the server ignores all movement packets from the client until a Confirm Teleportation packet matching the sent id is received"* ([Minecraft Wiki — Packets](https://minecraft.wiki/w/Java_Edition_protocol/Packets)). Spamming 20 teleports/s causes jitter, fights client prediction, and risks "moved too fast"/desync. Paper also has a documented spectator-teleport desync where a server-side location override leaves the camera in unloaded chunks ([PaperMC #13473](https://github.com/PaperMC/Paper/issues/13473)).

> **Recommended hybrid:** Put the admin in **spectator gamemode**, spawn a dedicated invisible **fake "camera entity"** (an armorstand or a marker entity, viewer-only), and use the **Set Camera Entity** packet to attach the admin's view to it (Option A). Then each frame you only move/rotate the camera entity via teleport/head-look packets and let the client interpolate. This gives smooth POV without fighting player-movement prediction. Provide a "free-cam" toggle that resets camera to the admin's own entity (Option A reset) for third-person inspection where they fly around the reconstructed scene.

### 3.2 Smoothing & interpolation between frames

- Drive the camera entity at the *recorded* rate but render at client framerate: because the spectated-entity camera interpolates client-side, sending keyframes at 10–20 Hz looks smooth.
- For position use linear interpolation; for rotation use shortest-arc angle interpolation (§1.2).
- Support playback speed (0.25×–4×) by scaling the tick advance; at slow speeds interpolation between stored keyframes is what keeps motion fluid.
- For pause: stop advancing the frame index but keep re-asserting the camera attachment (so a sneak/death reset doesn't drop the camera).

### 3.3 Chunk loading at the recorded location

The camera will travel to wherever the suspect was, which may be far from the admin's spawn and in unloaded terrain. The Set Camera packet is *silently ignored if the target isn't loaded* ([Minecraft Wiki — Packets](https://minecraft.wiki/w/Java_Edition_protocol/Packets)), and Paper's spectator override can strand the camera in unloaded chunks ([PaperMC #13473](https://github.com/PaperMC/Paper/issues/13473)). Mitigations:

- **Pre-teleport** the admin's real player entity to the recording's starting region (server-side `teleportAsync`) so the server loads/tracks chunks for them before attaching the camera.
- Add **plugin chunk tickets** (`world.addPluginChunkTicket` / `chunk.addPluginChunkTicket`) around the camera's current position to keep them loaded along the replay path; remove tickets behind the camera.
- If replaying against a **static snapshot world** (recommended for evidence, see §4.3), load that world and keep a moving ring of forced chunks around the camera.
- Watch the camera's projected position a few frames ahead and ensure those chunks are loaded before the camera arrives.

---

## 4. Block / world reconstruction

### 4.1 Recording block edits

Capture block changes as discrete, timestamped events: `{tick, x, y, z, oldBlockData, newBlockData}` from `BlockPlaceEvent`, `BlockBreakEvent`, `BlockPhysicsEvent` (filtered), explosion events, and bucket/fluid events. Store both old and new states so playback can go forward (apply new) and the replay can be reversed/scrubbed (restore old).

### 4.2 Replaying block changes to the viewer

Send block updates only to the viewer using packets, not real world edits:

- **Single block**: `WrapperPlayServerBlockChange`.
- **Multiple in one chunk-section in one tick**: `WrapperPlayServerMultiBlockChange` — fired whenever 2+ blocks change in the same chunk section on the same tick, far more efficient than N single packets ([Minecraft Wiki — Packets](https://minecraft.wiki/w/Java_Edition_protocol/Packets); [SpigotMC](https://www.spigotmc.org/threads/sending-multi-block-change-packet.111328/)).

Block encoding inside the multi-block-change packet packs the block-state id with the local section coordinate: `blockStateId << 12 | (localX << 8 | localZ << 4 | localY)` ([MrPowerGamerBR gist](https://gist.github.com/MrPowerGamerBR/51bf5beb6466d557da2191ed8a3fe0df)). PacketEvents wrappers abstract this, but the formula matters when debugging.

```kotlin
// Pseudocode: apply this frame's block edits to the viewer only
fun applyBlockEdits(viewer: User, edits: List<BlockEdit>) {
    edits.groupBy { sectionOf(it.x, it.y, it.z) }.forEach { (section, group) ->
        if (group.size == 1) sendSingleBlockChange(viewer, group[0])
        else sendMultiBlockChange(viewer, section, group)   // packs stateId<<12 | localPos
    }
}
```

### 4.3 Base snapshot + diff (the critical reliability point)

AdvancedReplay's well-known weakness: it *replays block changes onto the live ongoing world*, so newly placed/broken blocks during a replay actually appear in the real world for everyone — fine for a static map, broken for survival/UHC worlds ([AdvancedReplay reviews/issues](https://github.com/Jumper251/AdvancedReplay)). Evidex must avoid this for an evidence tool.

> **Recommended pattern:** Replay against a **base world snapshot** captured at (or reconstructed for) the recording's start time, and apply the recorded block diffs **as viewer-only packets** on top of it. Options for the base:
> - Capture a lightweight **region/chunk snapshot** of the suspect's vicinity at record start (store block states for the bounding box), then replay diffs over it. Self-contained, world-independent, ideal for evidence retention.
> - Or load a separate copy/void world for playback and reconstruct the snapshot there, so nothing touches the live world.
>
> Never push replay block changes to the live world. All world reconstruction is per-viewer packets over a snapshot.

For fake blocks that don't fit normal block changes (highlight outlines, region markers), the same per-player block-change technique is used by preview/selection plugins to show blocks only to one player without committing them ([ProtocolLib article](https://www.protocollib.com/2025/04/25/what-is-can-protocollib-really-modify-minecraft-packets/)).

---

## 5. Storage & performance

### 5.1 How much data per minute

Reference points:
- ReplayMod (full packet stream, *everything* around the player) compresses to ~**10 MB per hour** ≈ 167 KB/min ([replaymod.com/docs](https://www.replaymod.com/docs/)). That's the whole-world packet stream.
- A state-stream recording of a *single subject* is far smaller. Rough budget for one subject at full 20 Hz with deltas:

| Field group | Bytes/frame (delta-coded) | Frames/s | Bytes/s |
|---|---|---|---|
| Position (3× var-scaled delta) | ~6 | 20 | 120 |
| Rotation (yaw+pitch+head, 1 byte ea) | ~3 | 20 | 60 |
| Pose/flags (changed only) | ~1 (amortized) | ~1 | ~1 |
| Item/equip (changed only) | ~4 (amortized) | sparse | ~4 |
| Events (hits/blocks/anim) | variable | sparse | ~10 |
| **Total (one subject)** | | | **~200 B/s ≈ 12 KB/min ≈ 0.7 MB/hr** |

Adding ~10 context entities at 10 Hz keyframes roughly multiplies the positional cost by ~5 (lower rate, more subjects): on the order of **1–3 MB per hour total**. Block-heavy sessions add more. So a single-suspect Evidex recording is an order of magnitude smaller than ReplayMod's whole-world capture.

### 5.2 Compression & encoding techniques

- **Delta encoding** of position/rotation between frames (store only changes); this is what makes the per-subject footprint tiny.
- **Quantization**: yaw/pitch fit in 1 byte each (256 steps = ~1.4°, the protocol's own resolution); positions as fixed-point deltas.
- **Keyframe + interpolation** for context entities (§1.2).
- **Frame-skip / change-threshold**: drop frames where nothing changed beyond ε.
- **Block-stream compression**: container compression (gzip/zstd) over the serialized frame blob; AdvancedReplay stores compact serialized action data and supports MySQL or S3 ([AdvancedReplay](https://github.com/Jumper251/AdvancedReplay)).
- **Separate hot path from cold storage**: write frames to an append-only in-memory ring buffer during recording, flush to file/DB asynchronously off the main thread (ReplayMod explicitly does metadata saving async to avoid blocking the network thread — [DeepWiki](https://deepwiki.com/ReplayMod/ReplayMod/2.1-recording-system)). On Paper, never do blocking DB/disk I/O on the main server thread.

### 5.3 Storage layout: files vs. DB

The user's design (DB for index/metadata + frame files for bulk) is the right call. Pattern:

- **DB (SQLite/MySQL/Postgres)**: recording index, metadata, retention, searchable evidence events.
- **Frame files** (per recording, compressed binary): the bulk time-series. Avoids bloating the DB with millions of frame rows and gives fast sequential scrubbing. (AdvancedReplay supports file *or* DB/S3 — Evidex's hybrid is a reasonable best-of-both.)

### 5.4 DB schema patterns for time-series frame data

Two viable schemas:

**A. Blob-per-recording (recommended for the frame bulk):**

```sql
CREATE TABLE replay (
  id            BIGINT PRIMARY KEY,
  subject_uuid  UUID NOT NULL,
  subject_name  VARCHAR(16),
  world         VARCHAR(64),
  start_epoch_ms BIGINT NOT NULL,
  end_epoch_ms   BIGINT,
  tick_count    INT,
  capture_hz    SMALLINT,
  frame_uri     TEXT,         -- path/S3 key to compressed frame file
  schema_ver    SMALLINT,
  size_bytes    BIGINT,
  expires_at    TIMESTAMP,     -- retention
  reason        TEXT,          -- why recorded (report id, alert)
  INDEX (subject_uuid), INDEX (start_epoch_ms), INDEX (expires_at)
);

-- searchable discrete evidence events (sparse, indexable)
CREATE TABLE replay_event (
  replay_id  BIGINT,
  tick       INT,
  type       VARCHAR(24),    -- ATTACK, BLOCK_BREAK, ITEM_SWITCH, DAMAGE...
  target_uuid UUID NULL,
  x INT, y INT, z INT,
  data       JSONB,          -- type-specific payload
  PRIMARY KEY (replay_id, tick, type),
  INDEX (replay_id, type), INDEX (type, target_uuid)
);
```

**B. Row-per-frame (only if you need SQL queries over kinematics):**

```sql
CREATE TABLE replay_frame (
  replay_id BIGINT, tick INT,
  x DOUBLE, y DOUBLE, z DOUBLE,
  yaw REAL, pitch REAL, head_yaw REAL,
  flags SMALLINT, pose TINYINT,
  held_slot TINYINT,
  PRIMARY KEY (replay_id, tick)
);
```

Schema B explodes row counts (20/s × subjects × seconds) and is slower to scrub; reserve it for analytics. For Postgres, partition `replay_frame`/`replay_event` by time (monthly) and use BRIN indexes on `(replay_id, tick)` for cheap range scans. For Postgres specifically, TimescaleDB-style hypertables are an option but usually overkill — the blob approach (A) is simpler and faster for sequential playback.

### 5.5 Retention & cleanup

- Per-recording `expires_at`; a scheduled async task deletes expired rows and their frame files/S3 objects.
- Cap max recording length (AdvancedReplay defaults to 3600 s — [AdvancedReplay](https://github.com/Jumper251/AdvancedReplay)); ring-buffer "rolling" recordings (keep last N minutes) for always-on suspicion mode, and "pin" a buffer to permanent storage when an alert/report fires.
- Tier storage: hot (recent, local disk/DB) → cold (S3) → delete.

---

## 6. Limitations of the no-client-mod approach

### 6.1 What is impossible (or hard) to reconstruct server-side

The server simply never receives some client-local state. These cannot be faithfully reproduced without a client mod:

| POV detail | Why it's lost server-side | Workaround |
|---|---|---|
| **Exact GUI/HUD state** — open inventory, chest contents being viewed, container scroll, crafting grid in-flight | Container UI is client-rendered; server only sees committed clicks | Reconstruct from inventory click events; show a synthetic overlay, not the real client GUI |
| **Mouse movement between ticks / sub-tick aim** | Server gets look only at packet cadence (~ every other tick); fine-grained mouse path is interpolated/lost | Capture at full tick rate; accept interpolation. True sub-tick aim (key for "is this aimbot?") is **not** recoverable server-side |
| **Client framerate, input timing, key presses** | Never sent to server | Infer from movement/animation events only |
| **F5 third-person, FOV, zoom, perspective** | Pure client render setting | N/A |
| **Client-side particles, screen shake, damage tilt** | Client-rendered | Re-emit server-known particles per-viewer |
| **Exact camera interpolation the suspect actually saw** | Their client interpolated locally; you only stored keyframes | Re-interpolate; visually close but not bit-identical |
| **Chat/UI overlays, toasts, advancement popups** | Client UI | Reconstruct from server chat/advancement events as overlay |
| **Render distance / what was actually loaded for them** | Client setting | Use server view distance as proxy |

The single most important limitation for an *anti-cheat evidence* use case: **you cannot prove sub-tick aim or client-side-only cheats from a server-side replay.** A server-side POV shows what the server knew the player did (positions, hits, reach, rotation at tick granularity) — which is plenty for reach, fly, speed, killaura *patterns*, and "what did they do" review, but it is reconstructed/interpolated, not a literal screen capture of their client.

### 6.2 Head rotation fidelity caveat

As noted in §2.2, fake-player-NPC head rotation via packets has a live, documented failure mode in recent PacketEvents/protocol versions ([packetevents #1400](https://github.com/retrooper/packetevents/issues/1400)). Because head direction *is* the POV, validate this thoroughly on the target Paper build; if third-person NPC head-look proves unreliable, the spectator-camera approach (§3.1 Option A) sidesteps it for the primary POV since the camera follows the camera-entity's own rotation rather than a rendered head.

### 6.3 How existing products work around it

- **ReplayMod** sidesteps the whole problem by *being a client mod* — it records the actual client packet stream plus synthetic local-camera packets, so it has true first-person data the server never sees ([DeepWiki](https://deepwiki.com/ReplayMod/ReplayMod/2.1-recording-system)). The cost is requiring every recorder to install the mod.
- **AdvancedReplay** accepts the server-side limitations: it reconstructs players/entities/items/projectiles as NPCs and replays them to a viewer, explicitly *not* trying to reproduce client GUI; and it warns about its world-block-change leakage ([AdvancedReplay](https://github.com/Jumper251/AdvancedReplay)). Evidex improves on it by recording against a snapshot and sending viewer-only block packets (§4.3).
- **Hypixel's replay system** (a bespoke server-side implementation) similarly reconstructs from server-known state and is the closest spiritual model to Evidex ([Hypixel Dev Blog #10](https://hypixel.net/threads/dev-blog-10-replay-system-technical-rundown.3234748/)).

---

## 7. Putting it together — Evidex reference architecture

### 7.1 Recording pipeline (per tick / event)

```
Bukkit events + per-tick scheduler
        │  (main thread: read state only, cheap)
        ▼
  FrameCollector (per tracked subject)
   ├─ delta-encode pos/rot, pose flags, item/equip
   ├─ append discrete events (attack, block edit, animation)
   └─ push to lock-free ring buffer
        │  (async worker thread)
        ▼
  FrameWriter → compressed frame file (+ block snapshot at start)
  EventWriter → DB rows (replay, replay_event)
```

Key correctness rules: never touch Bukkit API off-thread; only *read* state on the main thread and serialize on a worker; flush on rollover and on stop.

### 7.2 Playback pipeline (per viewer)

```
Admin runs /evidex play <id>
        ▼
  Load replay metadata + block snapshot + frame file
  Pre-teleport admin near start; force chunks; set spectator
  Spawn viewer-only fakes:
     ├─ suspect → npc-lib NPC (skin, equip)
     ├─ context entities → EntityLib / PacketEvents wrappers
     └─ camera entity → invisible marker; SetCamera → attach admin view
        ▼
  PlaybackClock (scaled tick advance, pause/seek)
   each frame:
     ├─ interpolate pos/rot for entities between keyframes → teleport/head-look (viewer-only)
     ├─ apply metadata deltas (pose/flags), equipment, animation
     ├─ apply block edits (single/multi-block-change, viewer-only, over snapshot)
     ├─ move camera entity to suspect's recorded pos/rot (client interpolates)
     └─ re-assert SetCamera if sneak/death reset detected
        ▼
  On stop: destroy fakes, remove tab-list ghosts, reset camera to admin entity,
           restore admin gamemode, drop chunk tickets
```

### 7.3 Library division of labor

| Concern | Library |
|---|---|
| Packet I/O, wrappers, per-user send | **PacketEvents** (single shared instance) |
| Human subjects (skins, tab list, equipment) | **npc-lib** (on PacketEvents adapter) |
| Non-player context entities (mobs, items, projectiles) | **EntityLib** or raw PacketEvents wrappers |
| Camera | Raw clientbound *Set Camera Entity* + teleport/head-look packets |
| Blocks | Raw `BlockChange` / `MultiBlockChange` wrappers, viewer-only |
| Entity id allocation | One Evidex allocator (high reserved range) feeding all of the above |

---

## Sources

- ReplayMod recording system internals — [DeepWiki: ReplayMod Recording System](https://deepwiki.com/ReplayMod/ReplayMod/2.1-recording-system)
- ReplayMod docs (format, ~10 MB/hr) — [replaymod.com/docs](https://www.replaymod.com/docs/)
- ReplayMod recording file format issue — [GitHub ReplayMod #49](https://github.com/ReplayMod/ReplayMod/issues/49)
- AdvancedReplay (server-side replay plugin) — [GitHub Jumper251/AdvancedReplay](https://github.com/Jumper251/AdvancedReplay), [README](https://github.com/Jumper251/AdvancedReplay/blob/master/README.md), [SpigotMC page](https://www.spigotmc.org/resources/advancedreplay-1-8-1-21.52849/), [Modrinth](https://modrinth.com/plugin/advancedreplay)
- Hypixel replay system technical rundown — [Hypixel Dev Blog #10](https://hypixel.net/threads/dev-blog-10-replay-system-technical-rundown.3234748/)
- npc-lib (juliarn) — [GitHub](https://github.com/juliarn/NPC-Lib), [README v3](https://github.com/juliarn/NPC-Lib/blob/v3/README.md), [SpigotMC](https://www.spigotmc.org/resources/npclib.55884/)
- EntityLib (Tofaa2, PacketEvents) — [GitHub](https://github.com/Tofaa2/EntityLib), [Hangar](https://hangar.papermc.io/Tofaa2/EntityLib)
- PacketEvents fake-NPC head rotation issue — [packetevents #1400](https://github.com/retrooper/packetevents/issues/1400)
- Spawning fake entities with packets — [Paper discussion #10584](https://github.com/PaperMC/Paper/discussions/10584)
- Minecraft Java protocol — packets (Camera/Set Camera Entity, Teleport handshake, Multi Block Change) — [Minecraft Wiki: Packets](https://minecraft.wiki/w/Java_Edition_protocol/Packets)
- Entity metadata (shared flags, pose enum, player fields) — [Minecraft Wiki: Entity metadata](https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata)
- Chunk/block encoding & multi-block-change examples — [MrPowerGamerBR gist](https://gist.github.com/MrPowerGamerBR/51bf5beb6466d557da2191ed8a3fe0df), [SpigotMC multi-block-change](https://www.spigotmc.org/threads/sending-multi-block-change-packet.111328/)
- Per-player fake blocks / packet modification — [ProtocolLib article](https://www.protocollib.com/2025/04/25/what-is-can-protocollib-really-modify-minecraft-packets/)
- Spectator camera control & teleport desync — [SpigotMC spectate-but-control discussion](https://www.spigotmc.org/threads/how-to-effieciently-set-the-camera-spectate-an-entity-but-control-player.574719/), [PaperMC #13473](https://github.com/PaperMC/Paper/issues/13473)
