# Server-Side Anti-Cheat Detection Patterns for Minecraft Java (Paper/Spigot 1.21)

> Research notes for **Evidex**, a Kotlin Paper plugin doing purely server-side heuristic checks on
> Bukkit events and assigning decaying Violation Levels (VL).
>
> Goal: understand how mature anti-cheats (GrimAC, NoCheatPlus, Vulcan, Matrix, Spartan, Polar)
> approach detection, where false positives come from, and which Evidex checks are most fragile.

---

## Table of Contents

1. [Detection Methodology](#1-detection-methodology)
2. [Flag vs Setback vs Mitigation](#2-flag-vs-setback-vs-mitigation)
3. [Lag Compensation Deep Dive](#3-lag-compensation-deep-dive)
4. [False-Positive Sources Per Check](#4-false-positive-sources-per-check)
5. [Where Server-Side Checks Are Fundamentally Limited](#5-where-server-side-checks-are-fundamentally-limited)
6. [Evidex Check Audit: Most FP-Prone Checks & Remediation](#6-evidex-check-audit)
7. [Recommended Architecture for Evidex](#7-recommended-architecture-for-evidex)
8. [Sources](#8-sources)

---

## 1. Detection Methodology

### 1.1 The two paradigms

There are two fundamentally different ways to detect cheating on the server:

| Paradigm | How it decides | Examples | Bypassability |
|---|---|---|---|
| **Threshold heuristics** | "Did the observed value exceed a static/statistical limit?" (e.g. speed > 0.36 b/t, CPS > 16, reach > 3.0) | NoCheatPlus, Spartan, Matrix, most Bukkit-event plugins | High — cheats tune their output to stay just under thresholds |
| **Prediction / physics simulation** | "Replay vanilla physics for this tick. Does the client's reported state fall inside the set of states physics allows?" | **GrimAC**, **Polar**, **Vulcan** (combat + movement) | Low — there is no "threshold" to slip under; movement is either physically possible or it is not |

Modern, well-regarded anti-cheats are converging on **prediction-based** detection for movement and a
**raycast + lag-compensation** model for combat. GrimAC describes itself as a "fully async, multithreaded,
**predictive**" anti-cheat that "simulates the client and does calculations using cold, hard math" with a
"1:1 replication of the player's possible movements" rather than "relying on bypassable tricks"
([GitHub: GrimAnticheat/Grim](https://github.com/GrimAnticheat/Grim),
[grim.ac](https://grim.ac/)).

**Why prediction wins.** A threshold check answers *"is this value suspicious?"* — which always has a grey
zone where legit outliers and tuned cheats overlap. A prediction check answers *"is this state reachable by
vanilla physics given the inputs the player sent?"* — a binary, math-grounded question. Grim runs a parallel
physics engine and, if "the player's actual state (sent via the position packet) deviates from the simulated
state, a violation (VL) is triggered" ([DeepWiki: GrimAC profile](https://deepwiki.com/tjshtqwq/AntiCheatWiki/4.1-grimac-()-profile-and-bypass-guide)).
NoCheatPlus, by contrast, "uses a lot of heuristics and guessing, so false positives will be encountered here
and there" ([dev.bukkit.org/projects/nocheatplus](https://dev.bukkit.org/projects/nocheatplus)).

### 1.2 Packets, not the Bukkit API

A recurring lesson from anti-cheat developers: **do not build detection on top of the Bukkit event API for
anything precision-sensitive.** A community "How to Develop an Anti-Cheat" guide states plainly: *"we don't
want to use APIs. While Bukkit, PocketMine or Nukkit provides great APIs for many use cases, we can't use that
data"* — because the API exposes processed, post-physics, possibly-mutated data on the main thread, not the
raw network packets ([gist: Snowiiii/How to Develop an Anti-Cheat](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)).

Concretely, Bukkit events have these problems for anti-cheat:

- **`PlayerMoveEvent` is debounced and lossy.** Paper fires it at most once per tick and merges multiple
  position packets; rotation-only packets and `flying` (idle) packets may not surface. You lose the per-packet
  granularity needed for Timer, Blink, and aim analysis.
- **Order is wrong.** By the time `EntityDamageByEntityEvent` fires, the server has already moved entities,
  applied knockback, and lost the client's exact perceived target position at click time.
- **MONITOR-priority post-event distance checks are unreliable** (see §3.4).

`PacketEvents` (2.7.0+) is the de-facto library for version-independent packet access, used for "transaction
aware checks, timing validation, velocity tracking, and packet order logic"
([PacketEvents API](https://www.spigotmc.org/resources/packetevents-api.80279/)). Grim runs "all movement
checks and the overwhelming majority of listeners on the netty thread" and maintains a **per-player world
replica** by listening to chunk-data, block-place, and block-change packets, so it can run collision math
off the main thread ([GitHub: GrimAnticheat/Grim](https://github.com/GrimAnticheat/Grim)).

### 1.3 VL / flag-buffer systems

Every mature AC accumulates evidence rather than punishing single events:

- **Violation Level (VL):** a per-check counter incremented on a fail, **decayed** over time or over
  consecutive passes. Punishment fires only when VL crosses a configured threshold.
- **Buffering:** "multiple flags within time windows before action" — a single anomalous tick is ignored;
  a sustained pattern is not ([gist: How to Develop an Anti-Cheat](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)).
- **Reward / trust systems:** consecutive clean ticks *reduce* VL faster (Grim's "setback reward") or loosen
  thresholds for trusted players and tighten them for suspicious ones (NoChance/Vulcan "trust scoring,
  adaptive sampling") ([NoChance](https://www.spigotmc.org/resources/nochance-anti-cheat.129357/)).
- **Decay tuning is the FP knob.** NoCheatPlus's guidance is explicit: when you get false positives, you
  "set violation thresholds conservatively" / "increase its tolerance" rather than rewrite the check
  ([dev.bukkit.org/projects/nocheatplus](https://dev.bukkit.org/projects/nocheatplus)).

```text
on check fail:
    vl += weight                      # weight scaled by severity / certainty
    if vl >= alertThreshold:  alert(staff)
    if vl >= setbackThreshold: setback(player)     # movement only
    if vl >= banThreshold:    punish(player)
on check pass:
    vl = max(0, vl - decay)           # decay; reward streaks decay faster
periodic (every N ticks):
    vl = max(0, vl - timeDecay)
```

The crucial nuance: **the VL weight should be proportional to certainty.** A movement 0.001 b/t over the
limit (possibly float error) should add far less than one 5x over the limit. Pure binary "fail = +1" buffers
are the classic source of slow-burn false bans on laggy players.

---

## 2. Flag vs Setback vs Mitigation

Three distinct response strategies, often confused:

| Response | What it does | Use for | Risk if misapplied |
|---|---|---|---|
| **Flag / Alert / Log** | Records a violation, notifies staff, increments VL. No gameplay effect. | Everything; the only safe default for ambiguous checks | None to gameplay; pure observability |
| **Setback (a.k.a. lagback)** | Teleports the player back to their last server-verified valid position; cancels the illegal movement | Movement cheats (Flight, Speed, NoFall, Step) — *only* | A false setback is **extremely** disruptive — rubber-banding legit players. Worse than a false log |
| **Mitigation** | Silently neutralizes the cheat's benefit without a hard cancel (e.g. cap horizontal speed to legal max, clamp reach, deny the block place) | Where a hard setback would be too jarring but you still want to remove the advantage | Subtle desync if the cap is wrong |

Grim's model: deviation from the simulation triggers a VL "followed by a setback (LagBack) or a ban"
([DeepWiki](https://deepwiki.com/tjshtqwq/AntiCheatWiki/4.1-grimac-()-profile-and-bypass-guide)). The key
design rule from the dev guide: **"Treating all flags equally (some require setbacks; others require inventory
closure)" is a pitfall** — each check needs a response matched to its certainty and category
([gist](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)).

**Recommendation for Evidex:** Since Evidex is event-based and heuristic (not a full simulation), its
confidence is inherently lower than Grim's. **Default to flag/log + VL**, reserve **setback** for only the
highest-certainty movement checks (e.g. clearly-impossible vertical Flight with no levitation/elytra), and use
**mitigation** (deny block place, cancel the damage event) for combat/world checks rather than teleport
setbacks. Never setback on a heuristic that can be tripped by lag.

---

## 3. Lag Compensation Deep Dive

This is the single biggest source of false positives and the area where event-based plugins struggle most.

### 3.1 The core problem

The client and server are never in the same state at the same wall-clock time. The client acts on entity
positions that are *behind* server truth by the network latency, and the server receives the client's actions
*after* they happened. From the reach research: *"The server sends packets Server → Client; there is a hard to
determine delay, which becomes even more impractical to accurately estimate with pingspoof,"* and during
legit combat "server-observed distances consistently exceed 3.5 blocks due to lag compensation"
([Hypixel: Why Reach is harder to detect than you think](https://hypixel.net/threads/tldr-why-reach-is-harder-to-detect-than-you-think-how-to-detect-it.4878543/)).

### 3.2 Transactions (the gold standard)

A **transaction** (1.8: `Window Confirmation`; 1.17+: the `Ping`/`Pong` play packets) is a packet the server
sends that the client **must echo back**, in order. Because **TCP guarantees order** (not arrival time), a
transaction acts as a precise marker in the packet stream:

```text
server →  TRANSACTION(id=A)        # "sandwich" start
server →  (any state-changing packets this tick)
server →  TRANSACTION(id=B)        # sandwich end
...
client →  ... player packets ...
client →  TRANSACTION_RESPONSE(A)  # everything BEFORE this was sent before the client saw A
client →  TRANSACTION_RESPONSE(B)
```

This is "transaction pairing" / "transaction sandwiching": *"a synchronous acknowledgement packet sent on the
main thread that tells the client to send back a packet with similar information,"* letting the AC "establish
exact packet arrival timing" and measure true round-trip latency immune to ping spoofing
([Hypixel reach thread](https://hypixel.net/threads/tldr-why-reach-is-harder-to-detect-than-you-think-how-to-detect-it.4878543/),
[PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/)). Libraries like **Pledge** wrap
this so you can attach a transaction to a tick and know exactly when the client acknowledged it
([gist](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)).

Why this matters: with transactions you can build a **per-player ring buffer of historical positions** and,
for a given attack packet, know *which* historical target position the client actually saw — so reach/aim are
validated against the truth the client perceived, not the server's "now."

> **Keepalive is NOT a substitute.** Keepalive packets are sent ~once per second on a separate cadence and are
> not ordered relative to gameplay packets, so they only give a coarse ping estimate. Use them as a sanity
> bound, never for per-tick alignment.

### 3.3 Lag-compensated combat (raycast against history)

The two viable reach strategies from the research
([Hypixel reach thread](https://hypixel.net/threads/tldr-why-reach-is-harder-to-detect-than-you-think-how-to-detect-it.4878543/)):

- **Solution A — Past-location tracking:** estimate where the attacker *perceived* the target based on
  measured ping; raycast against that. "Semi-reliably detects 3.1–3.2 block reach." Cheaper, less precise.
- **Solution B — Transaction pairing + interpolation:** pinpoint the exact client-perceived target position
  between two transaction markers, then raycast. "Enables precise raycast validation below 3.01 blocks with
  minimal false positives." This is what Grim's "3.01 reach" claim refers to
  ([GitHub: GrimAnticheat/Grim](https://github.com/GrimAnticheat/Grim)).

The combat check builds a ray from the attacker's eye through their rotation and intersects it with a
**lag-compensated, per-entity-type bounding box** (players are 0.6 × 1.8) over the *window* of positions the
target could have been in — taking the **minimum** distance across that window
([gist](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)).

### 3.4 Why MONITOR-priority post-event distance checks are unreliable

A naive reach check reads attacker and victim locations inside `EntityDamageByEntityEvent` (often at MONITOR
priority) and compares `distance`. This is broken because:

1. **Timing skew:** the event fires when the *server* processes the packet, after both entities may have moved
   on the server. The distance you measure is server-now-to-server-now, not the client's at-click distance.
2. **No latency window:** you can't reconstruct where the client saw the victim, so legit hits during normal
   ping routinely read 3.2–3.7 blocks and trip naive 3.0 checks
   ([Hypixel reach thread](https://hypixel.net/threads/tldr-why-reach-is-harder-to-detect-than-you-think-how-to-detect-it.4878543/)).
3. **Ping-buffer band-aids are exploitable:** `if (ping > 200) maxReach += 0.5` lets cheaters spoof ping to
   buy reach leniency (same source).
4. **Sweep / multi-target:** sweep attacks and some plugins generate damage events for entities the player
   never directly clicked, polluting the sample.

The fix is §3.3: align to packets via transactions and raycast against history, not a post-facto Euclidean
distance.

### 3.5 World-state lag compensation

Grim also lag-compensates the *world*, not just entities: it "queues world changes until they reach the
player, preventing false positives when blocks break beneath players," and tracks inventory to avoid "ghost
block errors at high latency" ([GitHub: GrimAnticheat/Grim](https://github.com/GrimAnticheat/Grim)). An
event-based plugin that reads `block.getType()` at "server now" will mis-evaluate NoFall/Step/Scaffold for any
laggy player because the player's client still sees the old block.

---

## 4. False-Positive Sources Per Check

Legend for FP-risk: ⬛ extreme · 🔴 high · 🟠 medium · 🟢 lower.

### 4.1 Movement checks

| Check | Primary FP sources | Notes / remediation |
|---|---|---|
| **Flight** ⬛ | Levitation, Slow Falling, Elytra (+ firework boost), Riptide trident, jump-boost, slime-block bounce, bubble columns (kelp/soul-sand/magma), `setVelocity` from plugins, vehicle dismount, teleports | Must model every vertical-motion source. `disableElytraMovementCheck` exists in vanilla precisely because elytra + fireworks trips fly/speed checks ([DisableElytraMovementCheck](https://modrinth.com/plugin/disableelytramovementcheck), [Levitation – Minecraft Wiki](https://minecraft.wiki/w/Levitation)). |
| **Speed** 🔴 | Ice/packed-ice/blue-ice friction (≈0.98 vs 0.6 default), Speed/Dolphin's Grace/Soul Speed effects, sprint-jump chaining, sprint + slime bounce, slime-block launchers, depth strider in water, knockback, custom-item speed boosts | Friction is **surface- and previous-velocity-dependent**; you cannot use a single max-speed constant. Slime launchers & elytra are confirmed common FP triggers ([Wabbanode AC guide](https://wabbanode.com/help/minecraft/how-to-configure-anti-cheat-on-your-minecraft-server)). |
| **Jesus** (walk on water) 🟠 | Frost Walker boots (real ice), lily pads, soul-sand/bubble columns pushing up, boats, drowned/entity collision, lag near shore | Confirm the block under the feet is actually liquid *on the client's view* (lag-compensated) and not a frosted/lily surface. |
| **Step** 🟠 | Auto-jump, slabs/stairs/carpets, vanilla 0.6 step height, mounts (horse step), `Attribute.GENERIC_STEP_HEIGHT` plugins, slime bounce | Vanilla allows stepping 0.6 blocks instantly. Step cheats step ≥1.0. Must read live attribute values. |
| **Spider** (climb walls) 🟢 | Ladders, vines, scaffolding, cobweb, climbing while jumping against wall | Verify no climbable block adjacent. Cobweb vertical motion is legit. |
| **Timer** 🔴 | Client lag spikes / packet bursts after a stall (TCP delivers a batch at once → looks "fast"), server TPS drops, redstone/lag, alt-tab catch-up | Use a **balance/credit** model over a long window, not per-tick. A burst after a freeze is legit; sustained >1.0x rate over seconds is not ([NoChance](https://www.spigotmc.org/resources/nochance-anti-cheat.129357/)). |
| **Blink** 🟠 | Connection stalls (client genuinely stops sending then resumes), teleport, lag | Blink = withholding then bulk-sending position packets. Distinguish from TCP-level lag via transaction timing. |
| **NoFall** 🔴 | Water/MLG water-bucket, hay bale (80% reduction), slime block (bounce, 0 damage), ladders/vines/scaffolding, cobweb, sweet berries, Slow Falling, Feather Falling boots, twisting vines, honey block, ender pearl landing, riptide | Huge exemption surface. The client sends `onGround=true` to avoid fall damage; detection compares server fall-distance to client's ground claim — but every item above legitimately negates the fall ([Tutorial: Breaking a fall – Minecraft Wiki](https://minecraft.wiki/w/Tutorial:Breaking_a_fall), [MLG techniques](https://www.kodeclik.com/what-is-mlg-minecraft/)). |

### 4.2 Combat checks

| Check | Primary FP sources | Notes / remediation |
|---|---|---|
| **Reach** ⬛ | Latency (legit hits read 3.2–3.7 b on normal ping), target moving away during lag window, hitbox interpolation, ping spoof attempts | Requires transaction-based lag comp + raycast against historical position (§3.3). Post-event distance is unreliable (§3.4). |
| **KillAura** 🔴 | Legit fast target-switching, multi-mob crowds, sweep attacks hitting several entities, high-skill flick aim | Snap-checks for instant rotation to target; but legit flicks exist. Crowd/sweep multi-target inflates "multi-aura" signals ([MX Anticheat known FPs](https://www.spigotmc.org/resources/mx-anticheat-ml-killaura-aim-detection-1-8-1-21.123341/)). |
| **AutoClick / CPS** 🟠 | Butterfly clicking & jitter clicking (legit humans hit 14–22+ CPS), drag-clicking, double-click mice (hardware), high-skill PvPers | CPS alone is weak; cheats cluster at suspiciously *consistent* CPS (e.g. 18–20 with low variance). Analyze **distribution/variance**, not raw rate ([gist](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)). |
| **AimAssist** 🔴 | Legit smooth aim, mouse acceleration, high DPI micro-movements, the GCD (mouse sensitivity quantization) being misread | A common formula `(diffPitch - (diffPitch - comparedPitch))` is acknowledged by AC authors to produce "tons of random false positives" ([MX Anticheat](https://www.spigotmc.org/resources/mx-anticheat-ml-killaura-aim-detection-1-8-1-21.123341/)). |
| **WallHit** (hit through wall) 🟠 | Partial blocks (slabs, stairs, fences, panes), block edges, lag-compensated occlusion, target's hitbox poking past a corner | Line-of-sight ray must use the *real* collision shapes, not full-cube assumptions, and the client's view. |
| **InvalidRotation** 🟢 | None for true out-of-range (pitch >90/<-90 is impossible in vanilla → reliable); but yaw wrap-around (±180) and float precision need care | One of the few low-FP checks: *"you will NEVER have pitch above 90 or below -90"* ([gist](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)). |
| **Velocity** (anti-knockback) 🔴 | Knockback resistance attribute/armor, blocking with shield, climbing, water/liquid drag, collisions, riding entities, ground friction absorbing KB | Compare expected post-KB motion to observed; must apply correct KB-resistance and environment math ("99.99% antikb" is Grim's prediction approach, [GitHub](https://github.com/GrimAnticheat/Grim)). |

### 4.3 Player / world checks

| Check | Primary FP sources | Notes / remediation |
|---|---|---|
| **XRay** 🟠 | Legit lucky strip-mining, branch mining patterns, cave/ravine exposure, players following others' tunnels | Pure event-based ore-mining can't see screen. Use **statistical + pattern analysis** of dug-to-ore ratios over time, never a single vein ([Shadow AC notes](https://www.spigotmc.org/resources/shadow-anticheat.131182/)). Best treated as a staff-alert signal, not auto-punish. |
| **FastBreak** 🟠 | Efficiency + Haste + correct tool + Aqua Affinity, instant-break blocks, server lag delivering break packets in a batch | Must compute the exact vanilla break time from tool/enchant/effect/block hardness; otherwise legit fast mining flags. |
| **Scaffold** 🔴 | Legit bridging (sneak-bridging, speed-bridging, god-bridging, ninja/breezily), placing while looking forward, fast legit placers | Cheats betray themselves via rotation GCD artifacts, impossible/air placement, and unnatural sneak-acceleration — not raw placement speed ([Shadow AC: tower/angle/speed, ImpossiblePlace, AirPlace](https://www.spigotmc.org/resources/shadow-anticheat.131182/)). |
| **FastEat** 🟠 | Lag batching of use-item packets, custom food, plugins altering eat time | Vanilla eat is ~32 ticks (1.6 s). Validate against actual food/effects. |
| **ChestStealer** 🟠 | Fast legit players, double-clicking to collect stacks, shift-click bulk moves | Detect inhuman *uniformity* of inter-slot timing, not raw speed. |
| **FastInventory** 🟠 | High-skill inventory management, drag-distribution, shift-click, ping batching | Same as above — variance and impossible-while-moving constraints, not raw rate. |

---

## 5. Where Server-Side Checks Are Fundamentally Limited

The server only sees what the client *sends* (packets) and the world state. It cannot see the client's screen
or memory. Therefore some cheats are **undetectable server-side, even in principle**
([gist: How to Develop an Anti-Cheat](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)):

- **Pure rendering / visual cheats:** ESP, X-Ray *texture packs / shaders* (vs. behavioral X-Ray), tracers,
  chams, fullbright/gamma, nametag/hitbox renderers, freecam (view-only), minimaps. The client renders these
  locally and sends nothing anomalous.
- **Reaction-assist that produces human-plausible output:** a well-tuned aim-assist or low-CPS autoclicker can
  emit packets indistinguishable from a skilled human. You can only catch *statistical* tells.
- **Information cheats:** anything that gives the player knowledge (ore locations, player positions through
  walls) without changing their movement/clicks.

> *"At the server-side, we are really limited detecting something like this [client rendering]."* Catching it
> requires a **client-side anti-cheat** (per-player install — limited adoption) or **kernel-level** anti-cheat
> like VAC/BattlEye (OS drivers in C++/Rust) ([gist](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)).

**Implication for Evidex:** invest detection effort where the server *can* win — movement physics, combat
geometry, packet timing, and behavioral statistics — and treat visual-cheat "detection" as out of scope or as
low-confidence staff alerts only.

---

## 6. Evidex Check Audit

Ranked by false-positive risk, with the concrete reason and remediation. Evidex is **event-based and
heuristic**, which makes the ⬛/🔴 checks below the most dangerous to auto-punish.

### 6.1 Highest FP risk — do NOT auto-setback/ban; flag + tune only

| Evidex check | Why it's FP-prone on event-based heuristics | Concrete remediation |
|---|---|---|
| **NoFall** ⬛ | Enormous legit-exemption surface (water/MLG, hay, slime, ladder, cobweb, slow-fall, feather-falling, honey, pearl). Reading block-at-server-now misjudges laggy clients. | Build an explicit exemption set; lag-compensate the landing block (queue world changes like Grim §3.5); compute expected fall damage and only flag when client claims `onGround` with no exempting block/effect in the lag window. Flag-only. |
| **Reach** ⬛ | If implemented as post-event `EntityDamageByEntityEvent` distance, it is structurally unreliable — legit hits read 3.2–3.7 b under normal ping (§3.4). | Move to transaction-paired raycast against historical target positions (§3.3). Until then, set the threshold high (≥3.5–4.0), buffer heavily, flag-only. |
| **Flight** ⬛ | Levitation/elytra/riptide/slime/bubble-column/`setVelocity` each produce legit upward motion. | Subscribe to potion-effect, elytra-gliding, riptide, vehicle and `PlayerVelocityEvent` states and exempt; model jump-boost. Setback only the clearly-impossible sustained-hover case. |
| **Speed** 🔴 | Friction is surface- and history-dependent (ice ≈0.98); a single max constant guarantees FPs on ice/slime/effects/KB. | Track previous-tick velocity and the block friction under the player; derive the per-tick max from vanilla friction math, not a constant. Exempt KB window, Speed/Dolphin/Soul-Speed. |
| **AimAssist** 🔴 | Rotation-delta heuristics famously over-flag legit smooth aim and misread the mouse GCD. AC authors openly report "tons of random false positives" with these formulas. | Treat as a low-weight staff signal, never auto-punish. Prefer GCD-consistency analysis over raw pitch-delta formulas. |
| **KillAura** 🔴 | Multi-target/sweep crowds and legit flicks trip snap/multi-aura logic. | Exclude sweep-attack secondary targets; require *combinations* (snap + impossible rotation + reach) before high VL; lag-compensate. |
| **Velocity** 🔴 | KB-resistance, shields, liquids, climbables, collisions all change expected knockback. | Apply correct KB-resistance attribute, shield-block state, and environment drag before comparing. Mitigate (don't setback). |
| **Timer** 🔴 | TCP batching after a stall delivers many packets at once → looks like Timer. | Use a long-window balance/credit accumulator; allow short bursts, punish only sustained >1.0× rate. |
| **Scaffold** 🔴 | Legit advanced bridging (god/breezily/ninja) places fast with forward-ish look. | Key on rotation-GCD artifacts, ImpossiblePlace/AirPlace, and sneak-acceleration anomalies — not raw place speed. |

### 6.2 Medium FP risk — flag + moderate buffering

`Jesus`, `Step`, `Blink`, `WallHit`, `XRay`, `FastBreak`, `FastEat`, `ChestStealer`, `FastInventory`.

- **Jesus / Step:** read live attributes (step height) and lag-compensated block-under-feet; exempt
  frost-walker/lily/slab/carpet/mount.
- **Blink:** distinguish withheld-then-flushed packets from genuine TCP stalls using transaction timing.
- **WallHit:** use real collision shapes for partial blocks; raycast from client's view.
- **XRay / FastBreak:** statistical over time; XRay should be staff-alert only.
- **FastEat / ChestStealer / FastInventory:** measure timing *variance/uniformity* and impossible-while-moving
  constraints, not raw rate; lag-compensate packet batches.

### 6.3 Lower FP risk — safest to weight highly

- **Spider:** simple adjacency check (no climbable block) → reliable.
- **InvalidRotation:** pitch >90 / <-90 is *impossible* in vanilla → near-zero FP; one of the strongest
  signals Evidex has ([gist](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)). Mind yaw
  wrap-around and float precision.

---

## 7. Recommended Architecture for Evidex

Given Evidex is a Kotlin Paper plugin built on Bukkit events with VL+decay, a pragmatic, incremental path:

1. **Adopt PacketEvents for the precision-critical checks.** Move Reach, Timer, Blink, AutoClick, and aim
   analysis off `PlayerMoveEvent`/`EntityDamageByEntityEvent` and onto packet listeners so you get per-packet
   granularity and correct ordering ([PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/)).

2. **Add a transaction layer (PacketEvents/Pledge).** Maintain a per-player ring buffer of historical
   positions keyed by transaction id, and measure true latency. This single change fixes Reach, Velocity,
   KillAura, and Blink simultaneously (§3).

3. **Confidence-weighted VL.** Make each fail's VL contribution proportional to how far past the limit it was
   and how certain the check is. Decay on clean ticks; reward streaks.

4. **Response matrix, not one-size-fits-all.** Flag-only for all ⬛/🔴 checks until lag-compensated; setback
   only for high-certainty movement; mitigation (deny/cancel/cap) for combat & world.

5. **Lag-compensate the world** for NoFall/Step/Scaffold — evaluate against the block state the client saw,
   not server-now (§3.5).

6. **Exemption registry.** A central, well-tested set of effect/block/state exemptions
   (levitation, slow-fall, elytra, riptide, slime, honey, ice, frost-walker, bubble columns, vehicles, recent
   teleport/pearl/portal, recent `setVelocity`) consulted by every movement check. Recent-teleport handling
   alone removes a large class of FPs (EssentialsX `/tp`, pearls, portals)
   ([Wabbanode AC guide](https://wabbanode.com/help/minecraft/how-to-configure-anti-cheat-on-your-minecraft-server)).

7. **Long-term:** for movement, the only way to truly beat threshold-tuned cheats is a prediction/simulation
   engine like Grim's. That is a large undertaking; if combat integrity is the priority, prefer running a
   mature predictive AC (Grim) for movement+combat and use Evidex for world/behavioral checks (XRay,
   inventory, FastBreak) where heuristics are acceptable and the FP cost is low.

---

## 8. Sources

- GrimAC — architecture, prediction engine, lag compensation, checks. [github.com/GrimAnticheat/Grim](https://github.com/GrimAnticheat/Grim) · [grim.ac](https://grim.ac/) · [Modrinth](https://modrinth.com/plugin/grimac)
- GrimAC profile & detection model (DeepWiki). [deepwiki.com/.../grimac-profile-and-bypass-guide](https://deepwiki.com/tjshtqwq/AntiCheatWiki/4.1-grimac-()-profile-and-bypass-guide)
- "How to Develop an Anti-Cheat" (packets vs API, prediction, transactions/Pledge, raycast, FP sources, server-side limits). [gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7](https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7)
- "Why Reach is harder to detect than you think + how to detect it" (lag comp, transaction pairing, ping-buffer pitfall). [hypixel.net/threads/...4878543](https://hypixel.net/threads/tldr-why-reach-is-harder-to-detect-than-you-think-how-to-detect-it.4878543/)
- PacketEvents API (transaction-aware checks, timing, packet order). [spigotmc.org/resources/packetevents-api.80279](https://www.spigotmc.org/resources/packetevents-api.80279/)
- NoCheatPlus (heuristic approach, FP tuning via thresholds). [dev.bukkit.org/projects/nocheatplus](https://dev.bukkit.org/projects/nocheatplus) · [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/nocheatplus)
- NoChance Anti-Cheat (4-layer validation, trust scoring, Timer/BadPackets). [spigotmc.org/resources/nochance-anti-cheat.129357](https://www.spigotmc.org/resources/nochance-anti-cheat.129357/)
- Shadow Anticheat (Scaffold tower/angle/speed, ImpossiblePlace/AirPlace, statistical XRay). [spigotmc.org/resources/shadow-anticheat.131182](https://www.spigotmc.org/resources/shadow-anticheat.131182/)
- MX Anticheat (KillAura/Aim ML detection, acknowledged aim-assist false positives). [spigotmc.org/resources/mx-anticheat...123341](https://www.spigotmc.org/resources/mx-anticheat-ml-killaura-aim-detection-1-8-1-21.123341/)
- Vulcan Anti-Cheat (PacketEvents-based packet analysis). [vulcan.ac](https://vulcan.ac/)
- Configuring anti-cheat / common FP triggers (elytra, slime launchers, teleport plugins, high ping). [wabbanode.com/.../how-to-configure-anti-cheat](https://wabbanode.com/help/minecraft/how-to-configure-anti-cheat-on-your-minecraft-server)
- DisableElytraMovementCheck (vanilla gamerule / plugin for elytra FP). [modrinth.com/plugin/disableelytramovementcheck](https://modrinth.com/plugin/disableelytramovementcheck)
- Levitation – Minecraft Wiki (vertical-motion effect). [minecraft.wiki/w/Levitation](https://minecraft.wiki/w/Levitation)
- Breaking a fall / MLG techniques (NoFall exemption surface). [minecraft.wiki/w/Tutorial:Breaking_a_fall](https://minecraft.wiki/w/Tutorial:Breaking_a_fall) · [kodeclik.com/what-is-mlg-minecraft](https://www.kodeclik.com/what-is-mlg-minecraft/)

---

*Compiled 2026-06-29. Focus: Paper/Spigot 1.21.x server-side detection patterns and FP avoidance for the Evidex plugin.*
