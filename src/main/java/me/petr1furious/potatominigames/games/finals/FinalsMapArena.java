package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Geometry shared by the generated city and a disposable copy of a custom map. */
public interface FinalsMapArena {
    World world();

    void build();

    Location center();

    Location spawn(int team, int memberIndex);

    Location vault();

    Location station(int station);

    boolean contains(Location location);

    boolean isCover(Block block);

    int stationAt(Block block);

    boolean isVault(Block block);

    double borderSize();
}
