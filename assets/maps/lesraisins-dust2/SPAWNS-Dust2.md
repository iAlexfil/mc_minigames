# Dust II spawn metadata

Reference consulted on 2026-11-13:
[setpos.gg's Dust II CS2 spawn coordinates](https://setpos.gg/dust2/spawns).
The reference lists fifteen T positions in three groups and five CT positions
in an arc. The same page links the author's Steam Workshop practice map.

This project imports a Minecraft recreation of the older CS:GO map, not Valve's
CS2 geometry. Source-engine world coordinates cannot be used as Minecraft block
coordinates. The runtime metadata recreates the pool sizes and group layout on
the existing T Start street and covered CT Spawn underpass below Short, with spacing fitted to
the imported walkable surfaces. It is an adaptation, not a centimeter-accurate
conversion of Valve's spawn entities. In particular, the T groups are compressed
to avoid the Minecraft recreation's parked car and masonry.

- T: fifteen positions, feet Y=82, X=-1.5..1.5, Z=21.5..38.5 (shifted three blocks toward +Z on the Minecraft plan).
- CT: five positions, feet Y=75, X=93.5..97.5, Z=60.5..66.5, inside the original roofed underpass. Y=82 at these X/Z coordinates is Short, not CT Spawn.
- T faces into the map; CT faces the CT-mid/B-side exit from the spawn area.
- Pools are independently shuffled once per round, including after halftime.
  Teams use the first slots without replacement, with at most five players each.
- Buy areas are fixed around these spawn regions, independent of the shuffled
  player order. The former road/mid start locations are outside the new areas.
- No source-world blocks are moved, removed or generated for this change.

The imported-map checks read the bundled blueprint and cover headroom, original
floor support, separation and buy-zone membership for every candidate position.

## Respawn surface correction

The source map contains hollow volumes below some thin street decks. T starts
at Y=82 rather than inside the foundation at Y=78. CT is different: Y=75 is the
real covered underpass with its original floor at Y=74 and roof at Y=80. Raising
CT to Y=82 incorrectly placed the team on Short. Its five slots and buy zone now
use the lower passage; the buy zone excludes Short above it.

Deathmatch derives points from reachable surfaces, including slabs and stairs,
but only selects exposed surfaces at Y>=79 (the B platform is Y=79). Lower
foundation floors and cavities below overhead decks are excluded from automatic
sampling. The explicitly verified classic slots, including the roofed CT
underpass, remain available as curated fallback candidates. Descending graph
edges must clear every intermediate body block, so a thin street deck cannot
be skipped during a three-block descent. Both modes recheck live body clearance
and floor support and skip obstructed candidates before choosing a spawn.

Regression assertions distinguish the T street from the covered CT underpass,
check the roof and exclude Short from the CT buy zone. Native spawn selection is
exercised by `native-checks/cs/CsSpawnSmoke.java`. The CT correction is awaiting
the next PR validation run.
