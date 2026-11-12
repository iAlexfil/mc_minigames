# One Shot component checks

Run from the repository root with Java 25:

```sh
sh ./gradlew -Pmc=26.2 -I mock-checks/one-shot/one-shot.init.gradle checkOneShotMock --no-configuration-cache
```

The fixture pins MockBukkit 26.2 and resolves it only for this optional task; the
plugin has no new runtime dependency. Compiled fixtures stay under `build/`.
The regular `checkOneShot` task runs models and registry-independent handlers on
both API versions. This additional task initializes Paper registries and also
runs its `--with-server` shield-feedback cases.

The component fixture uses production kits, event callbacks and the per-tick
controller. It covers cursor/armour/offhand cleanup, bow consumption policy,
lethal weak shots, the exact ten-second refill, death attribution/deduplication,
three-second respawns, both bonus types, one-hit shields, the three-pickup cap and
end cleanup. It uses real Bukkit inventories and events with MockBukkit players.

Fixture accommodations: nullable armour arrays are normalized for this
MockBukkit version; full-cube passability is supplied for the sandstone map;
particle rendering is a no-op. Native bow-item consumption is explicitly
simulated after the shoot callback. It does not test native projectile flight,
network packets, client death screens or multiplayer latency.
