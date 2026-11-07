package me.petr1furious.potatominigames.games.party;
import java.util.Collection;
import java.util.List;
import org.bukkit.entity.Player;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import me.petr1furious.potatominigames.core.team.TeamColor;
import org.bukkit.event.Cancellable;
import me.petr1furious.potatominigames.core.game.GameSession;

/** Event boundary: no gameplay reaches a completed or not-yet-started round. */
abstract class PartyRoundForwarder implements GameSession {
    protected abstract GameSession delegate();
    private void protect(Object event) {
        if (event instanceof Cancellable cancellable) cancellable.setCancelled(true);
        if (event instanceof PlayerDeathEvent death) {
            death.getDrops().clear(); death.setDroppedExp(0); death.setKeepInventory(true); death.deathMessage(null);
        }
    }
    @Override public void onPlayerRespawn(PlayerRespawnEvent event) { GameSession active=delegate(); if(active!=null)active.onPlayerRespawn(event); else protect(event); }
    @Override public void onPlayerPostRespawn(PlayerPostRespawnEvent event) { GameSession active=delegate(); if(active!=null)active.onPlayerPostRespawn(event); else protect(event); }
    @Override public void onPlayerDeath(PlayerDeathEvent event) { GameSession active=delegate(); if(active!=null)active.onPlayerDeath(event); else protect(event); }
    @Override public void onBlockBreak(BlockBreakEvent event) { GameSession active=delegate(); if(active!=null)active.onBlockBreak(event); else protect(event); }
    @Override public void onBlockPlace(BlockPlaceEvent event) { GameSession active=delegate(); if(active!=null)active.onBlockPlace(event); else protect(event); }
    @Override public void onEntityExplode(EntityExplodeEvent event) { GameSession active=delegate(); if(active!=null)active.onEntityExplode(event); else protect(event); }
    @Override public void onBlockExplode(BlockExplodeEvent event) { GameSession active=delegate(); if(active!=null)active.onBlockExplode(event); else protect(event); }
    @Override public void onEntityDamageByEntity(EntityDamageByEntityEvent event) { GameSession active=delegate(); if(active!=null)active.onEntityDamageByEntity(event); else protect(event); }
    @Override public void onEntityDamage(EntityDamageEvent event) { GameSession active=delegate(); if(active!=null)active.onEntityDamage(event); else protect(event); }
    @Override public void onEntityDeath(EntityDeathEvent event) { GameSession active=delegate(); if(active!=null)active.onEntityDeath(event); else protect(event); }
    @Override public void onEntityMount(EntityMountEvent event) { GameSession active=delegate(); if(active!=null)active.onEntityMount(event); else protect(event); }
    @Override public void onPlayerInteractEntity(PlayerInteractEntityEvent event) { GameSession active=delegate(); if(active!=null)active.onPlayerInteractEntity(event); else protect(event); }
    @Override public void onInventoryOpen(InventoryOpenEvent event) { GameSession active=delegate(); if(active!=null)active.onInventoryOpen(event); else protect(event); }
    @Override public void onEntityShootBow(EntityShootBowEvent event) { GameSession active=delegate(); if(active!=null)active.onEntityShootBow(event); else protect(event); }
    @Override public void onProjectileLaunch(ProjectileLaunchEvent event) { GameSession active=delegate(); if(active!=null)active.onProjectileLaunch(event); else protect(event); }
    @Override public void onProjectileHit(ProjectileHitEvent event) { GameSession active=delegate(); if(active!=null)active.onProjectileHit(event); else protect(event); }
    @Override public void onPotionSplash(PotionSplashEvent event) { GameSession active=delegate(); if(active!=null)active.onPotionSplash(event); else protect(event); }
    @Override public void onFoodLevelChange(FoodLevelChangeEvent event) { GameSession active=delegate(); if(active!=null)active.onFoodLevelChange(event); else protect(event); }
    @Override public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) { GameSession active=delegate(); if(active!=null)active.onPrePlayerAttackEntity(event); else protect(event); }
    @Override public void onPlayerInteract(PlayerInteractEvent event) { GameSession active=delegate(); if(active!=null)active.onPlayerInteract(event); else protect(event); }
    @Override public void onPlayerMove(PlayerMoveEvent event) { GameSession active=delegate(); if(active!=null)active.onPlayerMove(event); else protect(event); }
    @Override public void onPlayerJump(PlayerJumpEvent event) { GameSession active=delegate(); if(active!=null)active.onPlayerJump(event); else protect(event); }
    @Override public void onPlayerTeleport(PlayerTeleportEvent event) { GameSession active=delegate(); if(active!=null)active.onPlayerTeleport(event); else protect(event); }
    @Override public void onInventoryClick(InventoryClickEvent event) { GameSession active=delegate(); if(active!=null)active.onInventoryClick(event); else protect(event); }
    @Override public void onInventoryDrag(InventoryDragEvent event) { GameSession active=delegate(); if(active!=null)active.onInventoryDrag(event); else protect(event); }
    @Override public void onItemHeld(PlayerItemHeldEvent event) { GameSession active=delegate(); if(active!=null)active.onItemHeld(event); else protect(event); }
    @Override public void onSwapHandItems(PlayerSwapHandItemsEvent event) { GameSession active=delegate(); if(active!=null)active.onSwapHandItems(event); else protect(event); }
    @Override public void onDropItem(PlayerDropItemEvent event) { GameSession active=delegate(); if(active!=null)active.onDropItem(event); else protect(event); }
    @Override public void onPickupItem(EntityPickupItemEvent event) { GameSession active=delegate(); if(active!=null)active.onPickupItem(event); else protect(event); }
    @Override public void onCraftItem(CraftItemEvent event) { GameSession active=delegate(); if(active!=null)active.onCraftItem(event); else protect(event); }
    @Override public void onFurnaceExtract(FurnaceExtractEvent event) { GameSession active=delegate(); if(active!=null)active.onFurnaceExtract(event); else protect(event); }
}
