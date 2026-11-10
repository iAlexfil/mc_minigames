# Finals equipment and custom maps

User request: add original-game firearms/melee, S/M/L classes, gadgets, an explicit working cashout-steal interaction, and a way to import/configure better custom maps. Respond and document in English; retain the project's standard English/Russian i18n (explicit user clarification). The user approved the fifteen-weapon roster below.

- S / Light: M11, XP-54, SR-84, Sword, Dagger; 15 health, fast movement.
- M / Medium: AKM, FCAR, Model 1887, Riot Shield, Dual Blades; 25 health, normal movement.
- L / Heavy: M60, Lewis Gun, SA1216, Sledgehammer, Spear; 35 health, slower movement.

Each contestant selects one primary weapon. Firearms use block-obstructed hitscan, distinct fire cadence, magazines and timed reloads. Melee weapons have explicit reach, cooldowns and distinguishing abilities. These are gameplay adaptations with vanilla item visuals; no client mod or resource pack is required.

Each class has one specialization and three gadgets: Light gets dash, frag, sonar, vanishing bomb; Medium gets healing beam, frag, jump pad, defibrillator; Heavy gets charge and slam, RPG-7, dome shield, barricade. Effects must obey session bounds, team rules, protection, death and teardown. Gadgets and guns cannot damage other sessions. Temporary terrain must restore only its own unchanged blocks.

Class/weapon selection is a localized inventory and commands, saved per player. Countdown changes can apply immediately; combat changes apply on the next respawn, preventing healing/ammunition exploits. Restore original health attributes and movement on leave, quit and end. Prevent a second vanilla melee event from duplicating custom damage.

Cashout theft remains five seconds of uninterrupted crouching beside the active opposing station. Add visible action prompts and progress, interrupted by actual damage, leaving reach, releasing crouch, death or disconnect. Payout still takes priority over a theft completing on exactly the same tick.

Custom-map admin workflow: import a vanilla world folder/ZIP from a dedicated imports directory, or register a world already loaded. Configure bounds, red/blue spawns, vault and A/B cashouts; validate and save a bounded native structure snapshot. Select a saved map for future matches. Every game copies the snapshot into its disposable world; source worlds and templates are preserved. Failed imports/saves keep the last working map. Reject path traversal, symlinks, unsafe archives, oversized selections, missing markers and unsafe spawns.

Keep existing generated arena as a fallback until a custom map is supplied. No external map download or invented claim that a custom map was imported. No changes to unrelated dirty Party files, server authentication or firewall. Build the artifact and verify before any local plugin replacement/restart; avoid interrupting an active game.
