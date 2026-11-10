# Native Finals checks

These source-only fixtures exercise the plugin against AdvancedSlimePaper 26.3 and Java 25. They build and modify worlds, import map copies, and shut the server down automatically. Use a fresh, disposable server directory bound to loopback. Keep its caches, worlds, logs and compiled JARs outside Git.

## Prepare and run

1. Build the main plugin with Java 25 using `gradlew build`. Create an isolated directory such as `.tools/finals-v2-smoke/server`; copy the cached ASP 26.3 bootstrap JAR and its `libraries`, `cache` and `versions` directories there. Use the test runtime's accepted EULA.
2. Configure that server with `server-ip=127.0.0.1`, an unused `server-port`, `online-mode=false`, `white-list=true`, `enforce-whitelist=true`, `enable-query=false`, `enable-rcon=false`, `enable-status=false`, `management-server-enabled=false`, `level-type=minecraft:flat`, `generate-structures=false`, `view-distance=3`, `simulation-distance=3`, and `pause-when-empty-seconds=-1`. In `plugins/PotatoMinigames/config.yml`, set `asp.enable: false` and `lobby.enabled: false` using nested YAML sections.
3. Compile both Java files with Java 25. The classpath needs the built PotatoMinigames JAR and all runtime dependency JARs recursively beneath `libraries`. A `javac @compile.args` argument file avoids Windows command-line length limits; use quoted paths with forward slashes in that file.
4. Copy `plugin.yml` into the compiled classes directory and package it with `jar --create --file FinalsMapsSmoke.jar -C <classes-directory> .`. Put that JAR and the unchanged main plugin JAR in the isolated server's `plugins` directory.
5. Run from the isolated server directory:

```text
java -Xms256M -Xmx1536M -Dfinals.smoke.includeLoadouts=true -jar asp-server.jar --nogui
```

The default run checks maps only. Add `-Dfinals.smoke.includeLoadouts=true` for both suites, or use `-Dfinals.smoke.loadoutOnly=true` for a targeted lifecycle rerun. Start a fresh map fixture for another map run: its setup names are intentionally fixed.

## Evidence

Successful runs log `FINALS_MAPS_SMOKE_PASS` and `FINALS_LOADOUT_SMOKE_PASS`. Results are also written beneath `plugins/FinalsMapsSmoke/` as `result.txt` and `loadout-result.txt`. Retain the console log and the tested main JAR's SHA-256 with those results.

The verified run produced **72 map assertions and 354 loadout assertions** against main JAR SHA-256 `4b67fb337a6c06c9c15354f2b24968c333b36f8bbe99cdfad8ef119db17ef6a5`.

Map checks use real worlds and Bukkit structures: offset geometry, chest/item/sign/stair metadata, entity exclusion, immutable snapshots, failed-save preservation, destruction/reset isolation, native 26.3 save-root folder and wrapped ZIP import, migrated editor reload, source hashes, traversal rejection, and generated fallback.

Loadout checks use real registries, attributes, inventories, GUI events and weapon events with fake player transport. Real `onStart` creates the roster and arena; the production private spawn path is then explicitly invoked for each fixture player. Only player-lookup operations temporarily use a server proxy. Assertions cover all three classes, countdown and deferred choices, respawn application, leave/end stat restoration, all 15 weapon choices, gun/reload/melee inputs, and English/Russian metadata. This does not replace a multiplayer client playtest.
