# Finals Expansion Implementation Plan

> **For agentic workers:** Use executing-plans with isolated file ownership for parallel weapons, gadgets and maps.

**Goal:** Play a class-based Finals match with firearms/gadgets and configurable custom arenas.
**Architecture:** A session-owned combat context bounds all weapons/gadgets. A persistent loadout menu supplies class/weapon choices. A map service builds immutable snapshots into disposable arenas. The existing pure cashout state remains authoritative.
**Tech Stack:** Java 25, Paper 26.3, Gradle checks, Bukkit native structures, paired i18n catalogs.
**Spec:** `docs/superpowers/specs/2026-11-10-finals-expansion-design.md`

## Constraints and review focus
- Preserve unrelated work and server/firewall settings.
- No client dependencies; pair all new English/Russian i18n keys.
- Assert firearm ammo/reload/cadence, walls and friendly/protected targets.
- Assert class state restoration and loadout changes only at allowed lifecycle points.
- Assert temporary gadget terrain/visibility and scheduled effects clean up on death/end.
- Assert imported paths/archives/bounds and failed-write preservation; never destroy source maps.
- Assert visible theft progress corresponds to authoritative deadlines and valid channel conditions.

## Tasks
- [x] Weapons/class catalog and pure runtime checks.
- [x] Class gadgets and cooldown/effect checks.
- [x] Map import, setup commands, validation and snapshot checks.
- [x] Persistent loadout UI/commands and session integration with class restoration.
- [x] Cashout prompts/progress and interruption regression checks.
- [x] Merge localized strings, update README and English launcher.
- [ ] Full build/i18n, native server smoke, independent review and fixes; package verified JAR.
