# Counter-Strike native regression check

`CsGrenadeSmoke` invokes the production flash detonation against Paper's actual
particle factory. The previous untyped `FLASH` call fails here because that
particle requires `Color` on both supported server versions. Bukkit sounds also
require native registries, so this fixture deliberately runs outside the
server-free `CsCheck`.

Use a fresh disposable Paper/AdvancedSlimePaper server directory and Java 25; never run
this fixture on a shared game server. Bind to `127.0.0.1` and an unused port,
disable RCON/query/status/management, use offline mode with an enforced whitelist,
set `pause-when-empty-seconds=-1`, and disable the plugin's lobby and ASP map
storage in that disposable configuration. Compile this source against the
server's Paper API and dependency JARs, including `CsGrenadeSmoke.java`,
`CsScopeSmoke.java` and `CsVisualSmoke.java`; package all classes with `plugin.yml`, and
put it beside the built PotatoMinigames JAR in `plugins/`.

Start the server normally. The fixture writes `plugins/CsGrenadeSmoke/result.txt`
and logs `CS_GRENADE_SMOKE_PASS` after a successful detonation, or logs
`CS_GRENADE_SMOKE_FAIL` with the exception. It shuts down the disposable server
automatically. `CsScopeSmoke` additionally checks the production scope helper
against real native movement attributes: FOV ratios, compensated movement,
non-stacking modifiers and stop/clear restoration. It uses simulated player
transport and cannot replace an actual client visual test. Success also logs
`CS_SCOPE_SMOKE_PASS`. To check the shared native metadata adapter too, compile
`../finals/FinalsTeammateGlowSmoke.java` into the same fixture JAR and launch with
`-Dcs.smoke.includeFinalsGlow=true`; success logs `CS_SHARED_GLOW_SMOKE_PASS`.
`CsVisualSmoke` checks real cosmetic armor components and
captures fire block packets to verify real terrain stays unchanged, shared fire
cells survive overlap, late spectators see fire, and cleanup restores the current
background. Success also logs `CS_VISUAL_SMOKE_PASS`. Repeat on 26.2 and 26.3 when preparing the PR; keep console logs and
the tested plugin JAR's SHA-256 outside Git.

`checkCounterStrike` additionally covers 150-block rifle/sniper hits, cover and
miss trace endpoints, the CS2 purchase prices, armor refill, the loss-bonus ladder,
the halftime price swap, roster-scaled payouts for all 1–5 player team sizes,
category coverage and price ordering, snowball-like grenade air motion and
localized roster/chat text. The existing native
Finals teammate-glow fixture exercises the shared private metadata adapter.

Client playtest before merging:

- Throw Molotovs onto floors, stairs and near walls. Animated ground fire must
  match supported surfaces in the existing damage zone, without spreading or
  changing the real map. Test overlapping fire, smoke extinguishing one/both
  zones, expiry, spectator arrival, chunk reload, leaving and round/end cleanup.
- Check red/blue full leather uniforms, purchased iron chestplates/helmets,
  depleted vests and halftime. Inspect native attributes: none of the visible
  armor may add protection, toughness or knockback resistance. Existing logical
  damage and armor consumption must stay identical.

- With AWP, SSG 08, AUG and SG 553, left-click in air, at blocks and at entities.
  One click must enable zoom, a later click must disable it. Hold right-click to
  fire while scoped; no native spyglass overlay/use must start. Check movement,
  hit tagging, reload, switching/dropping, death, menus, disconnect, halftime and
  match-end cleanup. Enable client FOV Effects; zero strength intentionally
  disables vanilla dynamic FOV. Sniper FOV is stronger than AUG/SG zoom.

- Check the one-screen buy menu on both sides: all legal weapons visible at once,
  distinct colored category columns/pairs, ascending prices and bottom-row gear.
  Weapon/gear clicks must buy only once; category headers must not debit money.
- Throw each grenade level, upward and downward, from standing/moving/jumping
  positions and next to thin cover. Compare the arc with a vanilla snowball;
  utility must still bounce, detonate once and clean up at round end.
- Check 2v2 (40% earned payouts), 2v3 (90% vs 60%, with $1,200 vs $800 pistol
  cash), and 5v5 (baseline). Deaths or disconnects during a round must not boost
  that round's payouts; the following round must update the roster snapshot.

- With two allies and an enemy, check that only allies have outlines, including
  through walls, and that smoke/flash suppress them. Check death, reconnect and
  round/end cleanup; other modes must retain their original glow behavior.
- Walk into/out of smoke and through overlapping smoke and flashes. The cloud
  should obscure the environment; bullets must still cross it, and visual
  intensity should be checked on an actual client.
- Open `/cs score` alive and dead. Check status changes, K/D/A, MVP and own-team
  cash only. The sidebar must show correct T/CT alive counts after halftime.
- Send `!private`, `!<red>literal</red>`, an empty `!`, and ordinary global chat.
  Test with opponents, a spectator, another active game, a disconnected/rejoined
  teammate and after halftime. Private messages must reach only current allies;
  ordinary messages keep the server's existing chat behavior.

## Validation evidence

On 2026-11-12, full Gradle builds passed on both 26.2 and 26.3; the final CS
suite passed **2,003 checks** on each API, and both i18n gates passed. Native
Paper 26.2 #135 and 26.3 #249 runs passed flash detonation, scope attributes and
restoration, cosmetic armor, fire overlays/overlap/cleanup, plus **43 shared
teammate-glow packet assertions** per version. Both isolated servers shut down
cleanly. Player transport is simulated; an actual client visual playtest remains
outstanding, especially FOV Effects, smoke density and floor-fire presentation.

Production JAR SHA-256 (identical class/resource payload for both classifiers):

```text
4f6f4c0b4ed0cf02f67139b8a517d9703ec6096f7b509c749e77612855442b58
```

For the plain Paper runs only, the cached ASP API 4.2.0-SNAPSHOT classes were
added to disposable test copies because the plugin already requires those
classes at load time even with ASP storage disabled. No ASP classes are bundled
in the production artifacts or source change. On AdvancedSlimePaper, use the
unmodified production JAR. Tested copy SHA-256 values:

```text
26.2 03a7db4b71d57d46a9139f157989a845c7ee9edf8943a1b7972f03febda313e3
26.3 347635a16313bac1867df1a70966d20e6a704410f2cc2064625b6d87f29d33d6
```

Build logs, native console logs, runtime worlds and generated binaries are kept
outside Git in the task's `work/` directories.
