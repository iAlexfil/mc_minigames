# CS2 weapon movement reference

Source: Valve's [weapons.vdata](https://github.com/SteamDatabase/GameTracking-CS2/blob/82e2b9d62cc97f1358c68a220a4a392c9e41c509/game/csgo/pak01_dir/scripts/weapons.vdata),
tracked by SteamDatabase at `82e2b9d62cc97f1358c68a220a4a392c9e41c509`.
Read `m_flMaxSpeed` (normal/alternate) and `m_flAttackMovespeedFactor`.

| Held item | CS2 speed | Fraction of knife |
| --- | ---: | ---: |
| Knife, C4 | 250 | 100% |
| Grenades | 245 | 98% |
| Glock, USP-S, P250, Dual Berettas, Five-SeveN, Tec-9, CZ75, MAC-10, MP9 | 240 | 96% |
| Deagle, UMP-45, P90, SSG 08 | 230 | 92% |
| M4A1-S, M4A4, MAG-7 | 225 | 90% |
| MP7, FAMAS, AUG, Nova | 220 | 88% |
| AK-47, Galil AR, XM1014 | 215 | 86% |
| SG 553, Sawed-Off | 210 | 84% |
| AWP | 200 | 80% |
| Negev | 150 | 60% |

Negev's attack movement factor is 0.5; it moves at the equivalent of 75 while
firing. Other implemented guns have factor 1. Scoped values are AWP 100,
Scout 230, AUG 150 and SG 150. Several guns intentionally share a speed in CS2;
this implementation does not invent different values merely to make them unique.

The Minecraft arena retains its base walk speed of 0.26 for knife/C4, multiplied
by these ratios. These are relative speeds, not CS units converted to blocks.
Slow crouched movement, hit tagging and airborne behavior remain separate.
Weapon speed also applies while survivors collect equipment after a round.

The FOV attribute trick must compensate real movement without exceeding Bukkit's
maximum walk speed of 1. AWP retains its existing 0.555 FOV ratio. To permit the
faster real scoped movement, Scout uses approximately 0.6196 and AUG/SG 0.578.
Scout's zoom is therefore slightly weaker than before; its scope no longer gives
it the universal 55% movement penalty. Scope exit restores normal weapon speed.
No native spyglass or client mod is introduced.

Validated on 2026-11-14: full builds for both API classifiers passed, including
the 6,669-case CS suite. Native Paper 26.2 #135 and 26.3 #249 scope checks verify
the real movement attributes, weapon-specific FOV ratios, non-stacking modifiers
and cleanup. The native fixture disables random zombie spawn/baby modifiers so
its stand-in body has player-like base movement. A connected-client playtest is
still needed for visual zoom and movement feel.
