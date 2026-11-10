package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.UUID;

/** Session-owned permissions and lifecycle for weapons and gadgets. */
interface FinalsCombatContext {
    JavaPlugin plugin();

    World world();

    long tick();

    Collection<Player> players();

    boolean active(Player player);

    boolean canAct(Player player);

    boolean friendly(Player first, Player second);

    boolean inside(Location location);

    default boolean canPlace(Block block) {
        return block != null && inside(block.getLocation());
    }

    boolean destructible(Block block);

    void breakBlock(Block block);

    void track(Entity entity);

    void damage(Player target, Player source, double amount);

    void reveal(Player player);

    boolean awaitingRespawn(Player player);

    Location deathLocation(UUID player);

    boolean revive(Player teammate, Player reviver);

    boolean carrier(Player player);

    FinalsClass playerClass(Player player);
}
