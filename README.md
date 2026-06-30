# Evidex

**Anti-cheat + replay de evidencia, 100 % server-side, para Paper/Spigot 1.21.**

Evidex detecta tramposos con checks heurísticos y, cuando un jugador acumula violaciones,
**graba lo que hace**. Luego un admin puede **revivir la grabación desde la vista en primera
persona del sospechoso**, dentro del juego, sin que el jugador ni el admin instalen ningún mod
de cliente. La reconstrucción se hace con entidades/NPCs falsos enviados por paquetes solo al
admin que mira.

Incluye además un **dashboard web** (panel de admin) para ver violaciones en vivo, controlar
grabaciones y reproducir evidencia.

> **Decisión de arquitectura:** Evidex es un **plugin de servidor puro**. No hay mod de cliente.
> Todo lo que el admin ve (replay POV, marcadores, entidades) se renderiza vía paquetes. Esto
> tiene límites duros — ver [Limitaciones](#limitaciones).

---

## Características

### Anti-cheat (detección)
Checks heurísticos sobre eventos Bukkit, con sistema de **Violation Level (VL)** que acumula y
**decae** con el tiempo. Por categoría:

- **Movimiento:** Flight, Speed, Jesus, Step, Spider, Timer, Blink, NoFall
- **Combate:** Reach, KillAura, AutoClick, AimAssist, WallHit, InvalidRotation, Velocity
- **Jugador:** XRay, FastBreak, Scaffold, FastEat, ChestStealer, FastInventory

Cada check es configurable (enable / VL por flag / VL máximo / umbral de flag / sensibilidad),
con exenciones por permiso, gamemode y mundo, y cooldown de alertas.

### Replay de evidencia (POV)
- Graba posición, rotación, entidades cercanas, equipamiento, cambios de bloques y snapshot del mundo.
- Reproduce al admin en **primera persona del sospechoso** usando NPCs (npc-lib) + PacketEvents.
- Auto-grabación al superar cierto VL; limpieza/retención configurable.

### Dashboard web
- Login con sesión, ver violaciones en vivo, iniciar/parar grabaciones, controlar el replay,
  borrar grabaciones.
- Backend embebido (NanoHTTPD), datos en SQLite/MySQL/PostgreSQL/MariaDB vía HikariCP.

---

## Cómo funciona (alto nivel)

```
                    ┌──────────────────────── Paper server (JVM) ───────────────────────┐
  Jugador  ──eventos──►  DetectionListener ──► Checks (VL) ──► AlertService ──► staff/dashboard
                    │          │                                   │
                    │          └──► RecordingManager ──► frames + DB (al superar VL)
                    │                                           │
   Admin  ──/evidex replay──►  ReplayManager ──► NPCs/PacketEvents (solo al admin) ──► POV
                    │
   Admin  ──navegador──►  DashboardServer (NanoHTTPD, 127.0.0.1) ──► ApiHandler ──► DB
                    └────────────────────────────────────────────────────────────────────┘
```

Toda la detección corre en el **hilo principal** sobre eventos Bukkit. El render del replay y los
marcadores se mandan como **paquetes solo al admin** que mira (los demás no los ven).

---

## Build

Requisitos: JDK (toolchain configurada en `build.gradle.kts`), Gradle wrapper incluido.

```bash
./gradlew build        # compila + shadowJar
# salida: build/libs/evidex-plugin.jar
```

Solo Kotlin (no hay fuentes Java). El jar (~7.4 MB) shadea+reloca solo lo acoplado a
NMS/protocolo (**PacketEvents, npc-lib, nanohttpd, Kotlin stdlib**). **Gson, HikariCP y los 4
drivers JDBC** se declaran en `libraries:` de `plugin.yml` y Paper los baja de Maven Central en
runtime (sin shadear), manteniendo el jar liviano. Si cambiás versiones, sincronizá
`plugin.yml` con los `compileOnly` de `build.gradle.kts`.

---

## Configuración

`config.yml` cubre detección (por check), grabación, retención y dashboard. Claves clave:

```yaml
dashboard:
  enabled: true
  port: 9090
  bind-address: 127.0.0.1   # SOLO localhost por defecto (ver Seguridad)
```

Comandos y permisos (`/evidex <record|replay|stop|list|alerts|violations|check> [jugador]`):

| Permiso | Default | Para |
|---|---|---|
| `evidex.admin` | op | usar comandos |
| `evidex.bypass` | op | exento de detección |
| `evidex.alerts` | op | recibir alertas in-game |
| `evidex.detection.admin` | op | ver detalle de violaciones/VL |

---

## Seguridad

El dashboard es un **panel de control privilegiado** (puede borrar evidencia, grabar, etc.).
Medidas ya aplicadas:

- **Bind `127.0.0.1` por defecto** (no `0.0.0.0`). Para acceso remoto usá túnel SSH
  (`ssh -L 9090:127.0.0.1:9090 user@host`) o reverse proxy con TLS.
- **Sin credenciales por defecto fijas:** el usuario `admin` se crea con **contraseña temporal
  aleatoria impresa en consola** (obliga a cambiarla al primer login).
- **Lockout de login:** 5 intentos fallidos → bloqueo de 5 min por usuario.
- Hash de contraseñas **PBKDF2-HMAC-SHA256**, salt por contraseña, **600k iteraciones** (piso OWASP 2024).
- **Sin CORS `*`** (el dashboard es same-origin).
- JSON del dashboard vía **Gson** (sin interpolación de strings ni parseo regex → sin inyección).

Pendiente (ver [docs/research/03](docs/research/03-architecture-security-ops.md) §1): tokens CSRF
en endpoints destructivos, cookie `Secure` tras TLS, cabeceras de seguridad (CSP, X-Frame-Options),
y evaluar migración a Argon2id.

---

## Limitaciones (server-side)

Al no haber mod de cliente, hay cosas que **no se pueden** reconstruir/detectar fielmente:

- **POV exacto sub-tick** (mouse entre ticks): se interpola, no es captura real de pantalla.
- **GUI/HUD del cliente** (inventario abierto, scroll): se reconstruye de eventos, no es la UI real.
- **Cheats puramente visuales/de información** (ciertos ESP, fullbright, xray-texture): el servidor
  no los ve.

Detalle completo en [docs/research/02](docs/research/02-pov-replay-systems.md) §6 y
[docs/research/01](docs/research/01-anticheat-detection.md).

---

## Documentación de investigación

Investigación técnica que respalda el diseño (patrones de la industria, fuentes citadas):

- **[docs/research/01 — Detección anti-cheat](docs/research/01-anticheat-detection.md)**
  Metodología VL, flag vs setback vs mitigación, lag compensation, y auditoría de qué checks de
  Evidex son más propensos a falsos positivos.
- **[docs/research/02 — Replay POV](docs/research/02-pov-replay-systems.md)**
  Captura por tick, render por paquetes a un solo viewer, control de cámara, reconstrucción de
  bloques, almacenamiento/retención y límites del enfoque sin mod de cliente.
- **[docs/research/03 — Arquitectura, seguridad y ops](docs/research/03-architecture-security-ops.md)**
  Seguridad del panel web, modelo de threading en Paper, modelo de datos/retención (SQLite vs
  Postgres), packaging (shadow/relocation/loader) y patrones de config/UX.

Índice: [docs/README.md](docs/README.md).

---

## Estado / roadmap

Mejoras recientes:
- Limpieza del repo (artefactos de build fuera de VCS, config Fabric muerta eliminada).
- Seguridad dashboard: bind localhost, sin backdoor admin/admin, lockout, sin CORS `*`,
  PBKDF2 600k, **JSON vía Gson** (sin interpolación de strings ni parseo regex).
- Fixes de falsos positivos en detección: Spider, Flight, NoFall, AimAssist, KillAura
  (sweep + noLOS por pared real), Velocity (escudo/agua/etc), Reach (hitbox).
- **Registro central de exenciones** (`ExemptionService`): ventana de gracia tras
  teleport/respawn/join/cambio-de-mundo; el listener salta detecciones durante la gracia.
- **Compensación de lag** (`LagCompensator`): latencia real por transacciones de PacketEvents
  (`TransactionLatencyTracker`, con fallback al ping de Bukkit) + relajación de checks por ping
  alto o TPS bajos.
- **Packaging**: drivers JDBC + Gson + HikariCP vía `libraries:` de Paper → jar de ~31 MB a ~7.4 MB.
- **Persistencia async** (`ViolationWriter`): el hilo principal ya no bloquea en la DB; las
  violaciones se encolan (O(1)) y se escriben en lotes cada 2 s en un task async. SQLite
  serializa su conexión única para ser thread-safe con el writer + dashboard.
- **Headers de seguridad** del dashboard (CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`)
  y cookie de sesión `Secure` tras TLS.

Próximos pasos sugeridos (de la investigación):
- VL ponderado por confianza por check.
- CSP estricta (mover `onclick` inline a `addEventListener`) + tokens CSRF en endpoints destructivos.
- Detección basada en predicción/simulación de física (estilo GrimAC) para los checks de movimiento.
