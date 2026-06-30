# Documentación de Evidex

## Investigación (`research/`)

Dossiers técnicos con patrones de la industria y fuentes citadas inline, que respaldan las
decisiones de diseño de Evidex.

| Doc | Tema | Contenido |
|---|---|---|
| [01-anticheat-detection.md](research/01-anticheat-detection.md) | Detección anti-cheat | Heurística vs predicción (GrimAC/NCP/Vulcan/…), VL/flag-buffer, flag vs setback vs mitigación, lag compensation por transacciones, y auditoría de FP por check de Evidex. |
| [02-pov-replay-systems.md](research/02-pov-replay-systems.md) | Replay POV server-side | Captura por tick, render por paquetes a un solo viewer (npc-lib/PacketEvents/EntityLib), control de cámara, reconstrucción de bloques sobre snapshot, almacenamiento/retención, límites sin mod cliente. |
| [03-architecture-security-ops.md](research/03-architecture-security-ops.md) | Arquitectura/seguridad/ops | Seguridad del panel web, threading en Paper (50 ms/tick, async, Folia), modelo de datos y retención (SQLite vs Postgres, HikariCP), packaging (shadow/relocation/Paper loader), config/UX de anti-cheat. |

## Cómo se generó

Los tres dossiers se produjeron con investigación web (búsqueda + lectura de fuentes primarias:
PaperMC docs, OWASP, Minecraft protocol wiki, repos de PacketEvents/npc-lib/ReplayMod/AdvancedReplay,
y artículos de configuración de anti-cheats). Cada afirmación lleva URL de fuente. Tratá el contenido
como guía de diseño, no como documentación de la API exacta — verificá índices de metadata/paquetes
contra tu build de Paper.

Volver al [README principal](../README.md).
