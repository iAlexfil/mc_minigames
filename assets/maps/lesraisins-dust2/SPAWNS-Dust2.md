# Dust II spawn metadata

Reference consulted on 2026-11-13:
[setpos.gg's Dust II CS2 spawn coordinates](https://setpos.gg/dust2/spawns).
The reference lists fifteen T positions in three groups and five CT positions
in an arc. The same page links the author's Steam Workshop practice map.

This project imports a Minecraft recreation of the older CS:GO map, not Valve's
CS2 geometry. Source-engine world coordinates cannot be used as Minecraft block
coordinates. The runtime metadata recreates the pool sizes and group layout on
the existing T Start street and CT Spawn surface near A, with spacing fitted to
the imported walkable surfaces. It is an adaptation, not a centimeter-accurate
conversion of Valve's spawn entities. In particular, the T groups are compressed
to avoid the Minecraft recreation's parked car and masonry.

- T: fifteen positions, feet Y=82, X=-1.5..1.5, Z=21.5..38.5 (shifted three blocks toward +Z on the Minecraft plan).
- CT: five positions, feet Y=82, X=93.5..97.5, Z=63.5..66.5.
- T faces into the map; CT faces the CT-mid/B-side exit from the spawn area.
- Pools are independently shuffled once per round, including after halftime.
  Teams use the first slots without replacement, with at most five players each.
- Buy areas are fixed around these spawn regions, independent of the shuffled
  player order. The former road/mid start locations are outside the new areas.
- No source-world blocks are moved, removed or generated for this change.

The imported-map checks read the bundled blueprint and cover headroom, original
floor support, separation and buy-zone membership for every candidate position.

## Respawn surface correction

The source map contains hollow volumes below its thin street decks. The former
T Y=78 and CT Y=75 positions had floor support and headroom but were inside those
volumes. Both classic pools now start above the Y=81 street deck; two CT slots
move slightly within the same area to avoid structures at the corrected height.
Buy-zone heights follow the street surface, including its bottom slabs.

Deathmatch derives points from reachable surfaces, including slabs and stairs,
but only selects exposed surfaces at Y>=79 (the B platform is Y=79). Lower
foundation floors and cavities below overhead decks are excluded. Classic
positions remain available as verified fallback candidates. Descending graph
edges must clear every intermediate body block, so a thin street deck cannot
be skipped during a three-block descent. Both modes recheck live body clearance
and floor support and skip obstructed candidates before choosing a spawn.

Regression assertions for the surface heights and thin-deck traversal pass in
`CsCheck` on both supported versions. Native spawn selection is exercised by
`native-checks/cs/CsSpawnSmoke.java`.
