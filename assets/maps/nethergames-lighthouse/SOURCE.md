# Lighthouse — external map asset

- Map: SW-Lighthouse; credited builder: JangoMango.
- Publisher: NetherGames Network, https://github.com/NetherGamesMC/assets
- Source commit: `5cfda13ef2873c3a048d4a665fe100d1d24542b1`.
- World: https://github.com/NetherGamesMC/assets/tree/5cfda13ef2873c3a048d4a665fe100d1d24542b1/Skywars/arenas/SW-Lighthouse
- Original spawn/credit configuration: `Skywars/arenas.yml` in that commit.
- License: Creative Commons Attribution 4.0 International, https://creativecommons.org/licenses/by/4.0/
- The publisher explicitly licenses all map/world assets under CC BY 4.0. See `LICENSE.txt`.

`source-bedrock.zip` contains the pinned source world database, level.dat and
original arena configuration. No server plugins or executable code are imported.
The Java resource `maps/magic/lighthouse.bin.gz` is a coordinate/palette conversion
of the source's 40,571 non-air blocks and twelve spawn markers, not a newly
procedurally generated arena. Legacy block names, numeric colours/orientations,
slabs and connected fences/panes/walls are translated into Java block states.
Block entities, inventories, entities and world scripts are intentionally omitted;
chests become renewable mana caches managed by the game. Start markers retain
original X/Z, with players placed on the existing ground below their raised cages.

Reproduce from the repository root with Python 3.12:

    python3.12 -m venv /tmp/magic-map-converter
    /tmp/magic-map-converter/bin/pip install amulet-leveldb==1.0.7 PyYAML==6.0.3
    unzip assets/maps/nethergames-lighthouse/source-bedrock.zip -d /tmp/lighthouse-source
    /tmp/magic-map-converter/bin/python tools/import_magic_lighthouse.py /tmp/lighthouse-source src/main/resources/maps/magic/lighthouse.bin.gz

The conversion script is original project code. The independently licensed map
remains CC BY 4.0. Attribution is also included in the plugin resources and the
round's startup chat. Keep that attribution and this source notice when sharing.
