# Parkour Sprint native regression check

`ParkourSmoke` builds two actual lanes with the production sliced arena builder.
It checks real chest block entities, glass obstruction rules, isolated one-bucket
inventories and repeated attempt refills. Native inventory events exercise the
open/click permissions for falling owners, rivals, grounded players, stale views,
hotbar transfers, shift-click and clock protection. It also verifies dry tunnel
water rollback and the production rocket's Flight Duration 2 metadata.

Run only on a disposable Paper server with Java 25. Bind to localhost, disable
RCON/query/status/management, use offline mode with an enforced whitelist, set
`pause-when-empty-seconds=-1`, and disable lobby and ASP map storage in the plugin
configuration. Compile `ParkourSmoke.java` against the supported Paper API and
its dependency JARs; package the resulting classes with this `plugin.yml` and put
the fixture beside the production PotatoMinigames JAR. Avoid other automatic
shutdown fixtures in that runtime. Repeat for 26.2 and 26.3.

The fixture modifies the disposable world's terrain. It logs
`PARKOUR_SMOKE_PASS` and writes `plugins/ParkourSmoke/result.txt` on success, or
logs `PARKOUR_SMOKE_FAIL` with the exception. It shuts down the server after
either result. Inspect the marker; a normal server exit alone does not prove
success. Keep runtime worlds, console logs and JARs outside Git.

Player transport and inventory views are substituted; terrain, native container
inventories, obstruction checks, inventory-event classes and item metadata are
real. This does not test mouse/keyboard timing or a complete connected-client
match. Before merging, play the chest catch, water placement/retrieval in the dry
tunnel, the eight ice turns and the one-rocket flight on an actual client. The
rocket's final difficulty margin and boat handling still require that playtest.

On plain Paper, the plugin currently requires cached ASP API classes at load
time even with ASP storage disabled. They may be added only to a disposable test
copy of the JAR, as described in `../cs/README.md`; never bundle that workaround
into the production artifact.

## Validation evidence

On 2026-11-13, full Gradle builds passed for 26.2 and 26.3 after merging upstream
`697331b` with the Parkour/One Shot feature commit `c84bb2c`. Both builds include
Parkour Sprint, One Shot, i18n and 5,872 CS checks. Native Paper 26.2 #135 and
26.3 #249 each passed **21 assertions**, including actual chest opening under
glass, refills, inventory permissions, water rollback and the rocket metadata.
Both disposable servers shut down cleanly. Connected-client timing, flight
difficulty and boat steering remain unverified by this fixture.

Production JAR SHA-256 (both classifiers):

```text
d5b63d1c31435c7f6a29d5c62c1c7548d5d6d0fa9872c3db8ae64aa1fbe3fc6f
```

Disposable Paper copy SHA-256 (both versions, with the ASP API workaround):

```text
5e715b50881848ef03f756e7c544c4e0246b7756914756d99ad421e793270082
```
