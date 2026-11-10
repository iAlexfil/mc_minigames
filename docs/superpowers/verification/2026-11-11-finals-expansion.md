# Finals expansion verification

Verified 2026-11-11 on Java 25 / AdvancedSlimePaper 26.3.

## Branch isolation

- Dedicated branch: codex/finals.
- Base: origin/main at 2651efe86f2daa86b81a635f27ecd5fd15271e5b.
- Implementation commit: 1abe038.
- Transferred Finals files were hash-checked before removing their edits from the original Party checkout.
- Unrelated Party commits were excluded. The original checkout was clean after transfer.
- No firewall settings were changed. Native checks ran only in an isolated loopback server.
- The new build remains in this branch's build output; the existing shared local server JAR was not replaced.

## Full build

gradlew.bat build --offline --no-daemon completed successfully: 38 tasks, 37 executed and one up-to-date.

- Finals state: 97 checks.
- Weapons: 94 checks.
- Gadgets: 54 checks.
- Session integration: 96 checks.
- Generated arena and custom-map filesystem/geometry checks passed.
- All other repository checks passed.
- Shared English/Russian i18n validation passed.
- The English Windows launcher's duplicate-server guard passed without starting the real server.

## Native server checks

Final artifact SHA-256:

4B67FB337A6C06C9C15354F2B24968C333B36F8BBE99CDFAD8EF119DB17EF6A5

Artifact: build/libs/potato-minigames-0.2.0-mc26.3.jar.

**72 map assertions passed**, exercising real Bukkit worlds and native Structure snapshots:
block states, chest contents, sign data, offset coordinates, entity exclusion,
failed-save preservation, immutable source/template hashes, independent disposable
match restoration, legacy/native 26.3 overworld folder and wrapped ZIP imports,
Paper world-folder migration, editor unload/service reload, unsafe ZIP rollback
and generated fallback.

**354 loadout/lifecycle assertions passed** with native registries, inventories,
worlds and production methods, using fake player transport:
class health 15/25/35, movement .24/.20/.17, countdown changes, deferred combat
selection, next-spawn application, leave/end restoration, all 15 weapon menus
and equips, nine firearm fire/reload paths, six melee event routes, and
English/Russian item and menu localization.

The fixture runs native world construction under the actual CraftServer and
scopes fake-player lookup only to methods requiring it. Earlier proxy-related
fixture failures were corrected without changing production. Both successful
native suites used the same artifact hash above; the isolated server exited
cleanly. Final source fixtures and procedure are in native-checks/finals.

## Review fixes verified

- Defer inventory close until the next tick.
- Use the same three-block radius to start and continue cashout stealing.
- Allow the demolition pickaxe to remove temporary cover safely.
- Preserve sonar glow across carrying/dropping and remove it at expiry.
- Render gadget inventory metadata using the recipient's locale.
- Reserve objective and roster-spawn/headroom locations against temporary cover.
- Keep open-menu players vulnerable and restore original class attributes on exit.

## Verification limits

No two-human-client playtest or Paper 26.2 build was performed for this expansion.
Windows filesystem symlink creation was unavailable in the local check environment;
ZIP symlink rejection and path traversal checks passed.
