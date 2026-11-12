# Native Finals checks

These source-only fixtures exercise the plugin against AdvancedSlimePaper 26.3 and Java 25. They build and modify worlds, import map copies, and shut the server down automatically. Use a fresh, disposable server directory bound to loopback. Keep its caches, worlds, logs and compiled JARs outside Git.

## Prepare and run

1. Build the main plugin with Java 25 using `gradlew build`. Create an isolated directory such as `.tools/finals-v2-smoke/server`; copy the cached ASP 26.3 bootstrap JAR and its `libraries`, `cache` and `versions` directories there. Use the test runtime's accepted EULA.
2. Configure that server with `server-ip=127.0.0.1`, an unused `server-port`, `online-mode=false`, `white-list=true`, `enforce-whitelist=true`, `enable-query=false`, `enable-rcon=false`, `enable-status=false`, `management-server-enabled=false`, `level-type=minecraft:flat`, `generate-structures=false`, `view-distance=3`, `simulation-distance=3`, and `pause-when-empty-seconds=-1`. In `plugins/PotatoMinigames/config.yml`, set `asp.enable: false` and `lobby.enabled: false` using nested YAML sections.
3. Compile `FinalsMapsSmoke.java`, `FinalsLoadoutSmoke.java` and `FinalsPreviewSmoke.java` with Java 25. The classpath needs the built PotatoMinigames JAR, JetBrains annotations, and all runtime dependency JARs recursively beneath `libraries`. A `javac @compile.args` argument file avoids Windows command-line length limits; use quoted paths with forward slashes in that file and `-encoding UTF-8`.
4. Copy `plugin.yml` into the compiled classes directory and package it with `jar --create --file FinalsMapsSmoke.jar -C <classes-directory> .`. Put that JAR and the unchanged main plugin JAR in the isolated server's `plugins` directory.
5. Run from the isolated server directory:

```text
java -Xms256M -Xmx1536M -Dfinals.smoke.includePreviews=true -jar asp-server.jar --nogui
```

The default run checks maps only. Add `-Dfinals.smoke.includePreviews=true` for editor previews and/or `-Dfinals.smoke.includeLoadouts=true` for loadouts. Use `-Dfinals.smoke.previewOnly=true` or `-Dfinals.smoke.loadoutOnly=true` for a targeted rerun. Start a fresh map fixture for another map run: its setup names are intentionally fixed.

## Evidence

Successful runs log `FINALS_MAPS_SMOKE_PASS`, `FINALS_PREVIEW_SMOKE_PASS` and/or `FINALS_LOADOUT_SMOKE_PASS`. Results are also written beneath `plugins/FinalsMapsSmoke/` as `result.txt`, `preview-result.txt` and `loadout-result.txt`. Retain the console log and the tested main JAR's SHA-256 with those results.

The cashout-pool and editor-preview run on 2026-11-12 produced **605 map assertions and 37 preview assertions** against main JAR SHA-256 `75c1ad9fd9f291af7ae612e4faea85eb10203d83f7003367f12115ff76fb7a16`. The private loopback server shut down after the checks. The earlier **354 loadout assertions** passed against JAR SHA-256 `4b67fb337a6c06c9c15354f2b24968c333b36f8bbe99cdfad8ef119db17ef6a5`; that suite was not rerun for these map-editor changes.

Map checks use real worlds and Bukkit structures: offset geometry, chest/item/sign/stair metadata, entity exclusion, immutable snapshots, failed-save preservation, destruction/reset isolation, native 26.3 save-root folder and wrapped ZIP import, migrated editor reload, source hashes, traversal rejection, and generated fallback. Random-vault coverage includes candidate persistence, append/clear/reset setup, headroom and floor validation at every spot, single-location compatibility, no consecutive repeats, one active marker, protection of inactive candidates, and restoration of original sign rotation and text when a vault moves.

Cashout coverage checks persistence of additional candidates, exactly two distinct active stations, replacement of only the completed station, restoration of native sign metadata, protection of inactive locations, headroom validation, setup clear/add operations, and compatibility with two-location maps.

Preview checks use real native display entities with simulated player transport: private visibility, numbered English/Russian labels, particles, live draft changes, entity reuse, snapshot exclusion, separate editor views, range limits, and cleanup on permission loss, world changes, disconnect and plugin disable. They verify entity metadata and lifecycle; visual appearance still benefits from an actual client playtest.

Loadout checks use real registries, attributes, inventories, GUI events and weapon events with fake player transport. Real `onStart` creates the roster and arena; the production private spawn path is then explicitly invoked for each fixture player. Only player-lookup operations temporarily use a server proxy. Assertions cover all three classes, countdown and deferred choices, respawn application, leave/end stat restoration, all 15 weapon choices, gun/reload/melee inputs, and English/Russian metadata. This does not replace a multiplayer client playtest.

The loadout fixture also covers the 18-second selection window, saved specialization
and gadget choices, and two complete three-player team wipes. It checks early
respawn/revive rejection, identical shared spawn positions, equipment restoration,
and a disconnected member returning after the shared deadline.

`FinalsSpecializationsSmoke.run(smokePlugin, mainPlugin)` is an additional synchronous
fixture to invoke on the server thread in an isolated runner. Compile it alongside
the other native fixtures. It exercises real deployable entities, turret line of
sight and destruction, mine arming, remote breach detonation, APS interception,
cloud effects, smoke/cloak visibility ownership, temporary terrain restoration
with goo and fluids, full-body pull collisions, directional Mesh protection, and
cleanup. Player transport is simulated; collision shapes, blocks and display
entities use the actual server implementation.

The combined run on 2026-11-12 passed against main JAR SHA-256
`9620f6596571e9a238f4a62704fb2f5628e9cae75fdac57500c9f8c682654c37`:
115 highlight, 7 name-tag, 43 teammate-glow, 39 Tab, 17 point-removal,
88 cashout-physics, 36 thrown-deposit, 444 loadout/team-wipe,
58 specialization/gadget, 12 drop-input and 32 asynchronous-map assertions.
