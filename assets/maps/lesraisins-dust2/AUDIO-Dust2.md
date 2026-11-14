# Dust II C4 audio calibration

## Reference

Valve's original CS2 event curves, as tracked by SteamDatabase at
`82e2b9d62cc97f1358c68a220a4a392c9e41c509`:

- [Weapon/C4 sound events](https://github.com/SteamDatabase/GameTracking-CS2/blob/82e2b9d62cc97f1358c68a220a4a392c9e41c509/game/csgo/pak01_dir/soundevents/game_sounds_weapons.vsndevts)
- [Dust II retake landmark and plant coordinates](https://github.com/SteamDatabase/GameTracking-CS2/blob/82e2b9d62cc97f1358c68a220a4a392c9e41c509/game/csgo/pak01_dir/maps/de_dust2_retake.txt)

The distance curves reach zero near 1,102 CS units for `c4.initiate` and
`c4.keypressquiet`, 2,000 for `c4.disarmstart`/`c4.disarmfinish`, 4,100 for the
longer `c4.plant` finish sound, and 1,300 for `C4.PlantSound` (ticking).
The long plant-finish tail is distinct from the shorter keypad sequence.

## Compare landmarks, not unit names

Coordinates in Valve's retake file:

- Default A plant: `(1090.739014, 2498.391846, 96.018295)`.
- Default B plant: `(-1380.638428, 2547.678223, 4.108919)`.
- Labelled CT spawn: `(283.011230, 2364.396729, -56.154083)`.

Representative locations in the imported Minecraft blueprint:

- A: `(104, 82, 89)`.
- B: `(114, 79, 7)`.
- Covered CT spawn: `(95.5, 75, 63.5)`.

| Pair | CS2 straight-line distance | Minecraft distance | Ratio |
| --- | ---: | ---: | ---: |
| A to B | 2,473.58 units | 82.66 blocks | 29.92 |
| A to CT spawn | 832.79 units | 27.78 blocks | 29.98 |

Both pairs independently suggest about 30 CS units per block on this recreation.
Rounded maximum ranges are 36 blocks for plant initiation/keypad, 66 for defuse,
136 for plant completion, and 44 for ticking. This preserves local relationships:
A's start cues reach the CT underpass/nearby Short; defuse taps and ticking do not
reach the other bombsite. From CT spawn, a B defuse is near the range's quiet edge,
while B keypad clicks are out of range. The plant-completion cue travels farther.

These are map-specific approximations, not a global meters/Source-units formula.
The Minecraft recreation predates CS2 and its corridors are not identical.

## Runtime behavior

All sounds use vanilla Minecraft samples; no resource pack or copied CS2 audio is
required. Plant initiation and keypad clicks differ from the mechanical defuse
start and completion cue. Each sound originates at the actual planter/C4 position,
uses the shared `CsAudio` delivery helper and player sound category, and only
reaches match viewers in the same world
within its 3D radius. Native attenuation fades the sound toward the edge. The B
site's ticking uses a slightly lower pitch.

A defuse start is emitted once per attempt, allowing fake defuses; refreshing the
held interaction does not replay it. Plant keypad clicks occur every 0.4 seconds
only while the channel remains valid. Cancellation, death, round reset and game
end remove the channel, with no scheduled audio left behind. No continuous defuse
loop is invented. Ticking cannot run after successful defuse or round completion.

This does not reproduce CS2's exact volume curves, material occlusion, reverb or
HRTF. The radius and landmark regression cases in `CsCheck` passed in full builds
for both API classifiers on 2026-11-14. Connected-client volume and recognizability
should be checked from CT spawn, Short, the B approaches and the opposite site.
