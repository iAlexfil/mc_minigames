package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/** Geometry shared by the generated city and a disposable copy of a custom map. */
public interface FinalsMapArena {
    World world();

    void build();

    Location center();

    Location spawn(int team, int memberIndex);

    Location vault();

    /** All configured candidates, including the currently active vault. */
    List<Location> vaultLocations();

    /** Start the next objective at a random candidate, avoiding an immediate repeat. */
    void nextVault();

    Location station(int station);

    /** All configured cashout candidates, including both active stations. */
    List<Location> cashoutLocations();

    /** Replace only the completed station, using an inactive candidate when one exists. */
    void nextCashout(int station);

    boolean contains(Location location);

    boolean isCover(Block block);

    int stationAt(Block block);

    boolean isVault(Block block);

    double borderSize();
}
