# Dust II spawn metadata

Reference consulted on 2026-11-13:
[setpos.gg's Dust II CS2 spawn coordinates](https://setpos.gg/dust2/spawns).
The reference lists fifteen T positions in three groups and five CT positions
in an arc. The same page links the author's Steam Workshop practice map.

This project imports a Minecraft recreation of the older CS:GO map, not Valve's
CS2 geometry. Source-engine world coordinates cannot be used as Minecraft block
coordinates. The runtime metadata recreates the pool sizes and group layout on
the existing T Start street and CT Spawn passage under A, with spacing fitted to
the imported walkable surfaces. It is an adaptation, not a centimeter-accurate
conversion of Valve's spawn entities. In particular, the T groups are compressed
to avoid the Minecraft recreation's parked car and masonry.

- T: fifteen positions, feet Y=78, X=-1.5..1.5, Z=21.5..38.5 (shifted three blocks toward +Z on the Minecraft plan).
- CT: five positions, feet Y=75, X=93.5..97.5, Z=60.5..66.5.
- T faces into the map; CT faces the CT-mid/B-side exit from the spawn passage.
- Pools are independently shuffled once per round, including after halftime.
  Teams use the first slots without replacement, with at most five players each.
- Buy areas are fixed around these spawn regions, independent of the shuffled
  player order. The former road/mid start locations are outside the new areas.
- No source-world blocks are moved, removed or generated for this change.

The imported-map checks read the bundled blueprint and cover headroom, original
floor support, separation and buy-zone membership for every candidate position.
