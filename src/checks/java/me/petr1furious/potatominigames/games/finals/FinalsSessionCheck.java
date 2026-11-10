package me.petr1furious.potatominigames.games.finals;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.GamePhase;
import me.petr1furious.potatominigames.core.text.Messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Real Paper handler checks; visual kits, inventory conversion and respawning need a live server.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class FinalsSessionCheck {
    private static final UUID RED = new UUID(1, 1);
    private static final UUID BLUE = new UUID(1, 2);
    private static final UUID BLUE_MATE = new UUID(1, 3);
    private static final UUID UNKNOWN = new UUID(1, 4);
    private static final Map<UUID, Integer> ROSTER = Map.of(RED, 0, BLUE, 1, BLUE_MATE, 1);
    private static int checks;

    public static void main(String[] args) throws Exception {
        friendlyAndAttributedDamage();
        protectionAndObjectiveGates();
        damageInterruptions();
        deathAndRejoin();
        legacyItemsAndNativeMutation();
        customDamageAndActionGates();
        menuAndEquipmentSlotGates();
        protectedPlacementAreas();
        fixedCountdownAnchor();
        System.out.println("FinalsSessionCheck: " + checks + " checks passed");
    }

    private static void friendlyAndAttributedDamage() throws Exception {
        Fixture f = new Fixture();
        EntityDamageByEntityEvent friendly = hit(f.blueMate.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(friendly);
        check(friendly.isCancelled(), "teammates cannot deal melee damage");
        EntityDamageByEntityEvent enemy = hit(f.red.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(enemy);
        check(!enemy.isCancelled(), "enemy melee remains enabled");
        Shot friendlyArrow = new Shot(f.blueMate.player);
        EntityDamageByEntityEvent arrow = hit(friendlyArrow.projectile, f.blue.player, 3);
        f.session.onEntityDamageByEntity(arrow);
        check(arrow.isCancelled(), "arrow damage uses the shooter's team");
        Shot enemyArrow = new Shot(f.red.player);
        EntityDamageByEntityEvent enemyShot = hit(enemyArrow.projectile, f.blue.player, 3);
        f.session.onEntityDamageByEntity(enemyShot);
        check(!enemyShot.isCancelled(), "enemy arrow damage remains enabled");
        Bomb friendlyBomb = new Bomb(f.blueMate.player);
        EntityDamageByEntityEvent tnt = hit(friendlyBomb.entity, f.blue.player, 3);
        f.session.onEntityDamageByEntity(tnt);
        check(tnt.isCancelled(), "TNT damage uses the thrower's team");
        Bomb enemyBomb = new Bomb(f.red.player);
        EntityDamageByEntityEvent enemyBlast = hit(enemyBomb.entity, f.blue.player, 3);
        f.session.onEntityDamageByEntity(enemyBlast);
        check(!enemyBlast.isCancelled(), "enemy TNT damage remains enabled");
        Bomb ownerless = new Bomb(null);
        EntityDamageByEntityEvent ownerlessBlast = hit(ownerless.entity, f.blue.player, 3);
        f.session.onEntityDamageByEntity(ownerlessBlast);
        check(ownerlessBlast.isCancelled(), "unattributed explosions cannot damage contestants");
        f.connected().remove(RED);
        EntityDamageByEntityEvent departedShot = hit(enemyArrow.projectile, f.blue.player, 3);
        f.session.onEntityDamageByEntity(departedShot);
        check(
                departedShot.isCancelled(),
                "projectile from departed shooter cannot damage contestants");
    }

    private static void protectionAndObjectiveGates() throws Exception {
        Fixture f = new Fixture();
        f.protection().put(RED, 101L);
        EntityDamageByEntityEvent protectedSource = hit(f.red.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(protectedSource);
        check(protectedSource.isCancelled(), "protected source cannot attack");
        EntityDamageByEntityEvent protectedTarget = hit(f.blue.player, f.red.player, 3);
        f.session.onEntityDamageByEntity(protectedTarget);
        check(protectedTarget.isCancelled(), "protected target cannot be damaged");
        Shot shot = new Shot(f.red.player);
        ProjectileLaunchEvent launch = new ProjectileLaunchEvent(shot.projectile);
        f.session.onProjectileLaunch(launch);
        check(
                launch.isCancelled() && f.ordnance().isEmpty(),
                "protected player cannot launch a projectile");
        f.red.location = f.arena.vault().add(0, 0, 2);
        f.session.onPlayerInteract(
                interact(f.red.player, f.world.block(0, 65, 0), EquipmentSlot.HAND));
        check(
                f.state.phase() == FinalsState.Phase.VAULT_CLOSED,
                "spawn protection blocks vault activation");
        f.protection().clear();
        f.session.onPlayerInteract(
                interact(f.red.player, f.world.block(0, 65, 0), EquipmentSlot.OFF_HAND));
        check(
                f.state.phase() == FinalsState.Phase.VAULT_CLOSED,
                "offhand duplicate cannot activate vault");
        f.red.location = new Location(f.world.world, 20, 65, 20);
        f.session.onPlayerInteract(
                interact(f.red.player, f.world.block(0, 65, 0), EquipmentSlot.HAND));
        check(
                f.state.phase() == FinalsState.Phase.VAULT_CLOSED,
                "distant objective interaction is rejected");
        f.red.location = f.arena.vault().add(0, 0, 2);
        f.session.onPlayerInteract(
                interact(f.red.player, f.world.block(0, 65, 0), EquipmentSlot.HAND));
        check(
                f.state.phase() == FinalsState.Phase.VAULT_OPENING,
                "nearby unprotected player can activate vault");
        f.session.onProjectileLaunch(new ProjectileLaunchEvent(shot.projectile));
        check(
                f.ordnance().containsKey(shot.id),
                "eligible launched projectile is tracked for cleanup");
        set(f.session, "state", null);
        EntityDamageByEntityEvent countdownHit = hit(f.red.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(countdownHit);
        check(countdownHit.isCancelled(), "countdown rejects combat");
    }

    private static void damageInterruptions() throws Exception {
        Fixture f = new Fixture();
        f.depositRed();
        f.state.beginSteal(BLUE, 100);
        EntityDamageByEntityEvent friendly = hit(f.blueMate.player, f.blue.player, 3);
        f.session.onEntityDamage(friendly);
        f.session.onEntityDamageByEntity(friendly);
        check(
                friendly.isCancelled() && f.state.stealDeadlines().containsKey(BLUE),
                "generic damage hook cannot interrupt steal before friendly-fire validation");
        EntityDamageByEntityEvent enemy = hit(f.red.player, f.blue.player, 3);
        f.session.onEntityDamage(enemy);
        f.session.onEntityDamageByEntity(enemy);
        check(
                !enemy.isCancelled() && !f.state.stealDeadlines().containsKey(BLUE),
                "positive valid enemy damage interrupts steal");
        f.state.beginSteal(BLUE, 100);
        EntityDamageEvent cancelledFall = environmental(f.blue.player, 3);
        cancelledFall.setCancelled(true);
        f.session.onEntityDamage(cancelledFall);
        check(
                f.state.stealDeadlines().containsKey(BLUE),
                "cancelled environmental damage preserves steal");
        f.session.onEntityDamage(environmental(f.blue.player, 0));
        check(
                f.state.stealDeadlines().containsKey(BLUE),
                "zero environmental damage preserves steal");
        f.session.onEntityDamage(environmental(f.blue.player, 3));
        check(
                !f.state.stealDeadlines().containsKey(BLUE),
                "positive environmental damage interrupts steal");
        f.state.beginSteal(BLUE, 100);
        f.protection().put(BLUE, 101L);
        EntityDamageEvent protectedFall = environmental(f.blue.player, 3);
        f.session.onEntityDamage(protectedFall);
        check(
                protectedFall.isCancelled() && f.state.stealDeadlines().containsKey(BLUE),
                "spawn protection blocks environmental damage and preserves channel");
    }

    private static void deathAndRejoin() throws Exception {
        Fixture f = new Fixture();
        f.depositRed();
        f.state.beginSteal(BLUE, 100);
        Shot shot = new Shot(f.blue.player);
        Shot survivorShot = new Shot(f.red.player);
        f.ordnance().put(shot.id, shot.projectile);
        f.ordnance().put(survivorShot.id, survivorShot.projectile);
        PlayerDeathEvent death =
                new PlayerDeathEvent(
                        f.blue.player, null, new ArrayList<>(), 7, Component.text("death"), true);
        f.session.onPlayerDeath(death);
        check(f.respawns().get(BLUE) == 160L, "death schedules the configured respawn delay");
        check(
                !f.state.stealDeadlines().containsKey(BLUE),
                "session death interrupts current steal");
        check(
                death.getKeepInventory() && death.getKeepLevel() && death.getDroppedExp() == 0,
                "death retains kit and level without dropping experience");
        check(
                shot.removed == 1
                        && survivorShot.removed == 0
                        && !f.ordnance().containsKey(shot.id),
                "death removes only that player's tracked ordnance");
        set(f.session, "tick", 110L);
        f.session.onPlayerDeath(death);
        check(f.respawns().get(BLUE) == 160L, "duplicate death cannot extend respawn timer");

        Fixture rejoin = new Fixture();
        rejoin.connected().remove(BLUE);
        rejoin.state.disconnect(BLUE, 100);
        rejoin.blue.dead =
                true; // A dead client's normal respawn event will finish spectator recovery.
        rejoin.session.onParticipantRejoin(rejoin.blue.player);
        check(
                rejoin.connected().contains(BLUE)
                        && rejoin.state.isActive(BLUE)
                        && rejoin.state.teamOf(BLUE) == 1
                        && rejoin.respawns().get(BLUE) == 160L,
                "rejoin keeps team and schedules recovery instead of immediate combat");
        EntityDamageByEntityEvent recovering = hit(rejoin.blue.player, rejoin.red.player, 3);
        rejoin.session.onEntityDamageByEntity(recovering);
        check(recovering.isCancelled(), "rejoining player cannot attack during respawn delay");
        rejoin.connected().remove(BLUE_MATE);
        rejoin.withdrawn().add(BLUE_MATE);
        rejoin.state.withdraw(BLUE_MATE, 100);
        rejoin.session.onParticipantRejoin(rejoin.blueMate.player);
        check(
                !rejoin.connected().contains(BLUE_MATE)
                        && !rejoin.respawns().containsKey(BLUE_MATE),
                "withdrawn player cannot rejoin or acquire a respawn callback");
        FakePlayer unknown = new FakePlayer(UNKNOWN, rejoin.world.world);
        rejoin.session.onParticipantRejoin(unknown.player);
        check(!rejoin.connected().contains(UNKNOWN), "unknown player cannot join fixed roster");
    }

    private static void legacyItemsAndNativeMutation() throws Exception {
        Fixture f = new Fixture();
        f.red.hand.type = Material.TNT;
        f.red.hand.amount = 2;
        f.red.location = new Location(f.world.world, 0, 65, 23.8f);
        f.session.onPlayerInteract(interact(f.red.player, null, EquipmentSlot.HAND));
        check(
                f.world.spawned == 0 && f.red.hand.amount == 2,
                "legacy TNT cannot spawn beyond arena boundary or consume an item");
        f.red.location = new Location(f.world.world, 0, 65, 0);
        f.protection().put(RED, 101L);
        f.session.onPlayerInteract(interact(f.red.player, null, EquipmentSlot.HAND));
        check(f.world.spawned == 0 && f.red.hand.amount == 2, "protected player cannot throw TNT");
        f.protection().clear();
        f.session.onPlayerInteract(interact(f.red.player, null, EquipmentSlot.HAND));
        check(
                f.world.spawned == 0 && f.red.hand.amount == 2 && f.ordnance().isEmpty(),
                "legacy raw TNT no longer launches or consumes items during combat");
        Block cover = f.world.block(6, 65, 3);
        Block floor = f.world.block(0, 64, 0);
        Block vault = f.world.block(0, 65, 0);
        Block wall = f.world.block(24, 65, 0);
        Bomb legacy = new Bomb(f.red.player);
        EntityExplodeEvent blast = explosion(legacy.entity, List.of(cover, floor, vault, wall));
        f.session.onEntityExplode(blast);
        check(
                blast.blockList().isEmpty() && blast.getYield() == 0,
                "untracked native TNT cannot mutate cover, floor, objectives or walls");
        EntityExplodeEvent outside = explosion(new Bomb(null).entity, List.of(cover));
        f.session.onEntityExplode(outside);
        check(outside.blockList().isEmpty(), "ownerless native blasts cannot alter arena blocks");
        BlockPlaceEvent placed =
                new BlockPlaceEvent(
                        cover, null, floor, f.red.hand, f.red.player, true, EquipmentSlot.HAND);
        f.session.onBlockPlace(placed);
        check(placed.isCancelled(), "native TNT and block placement are denied");
        f.red.slot = 0;
        BlockBreakEvent wrongSlot = new BlockBreakEvent(cover, f.red.player);
        f.session.onBlockBreak(wrongSlot);
        check(
                wrongSlot.isCancelled() && !wrongSlot.isDropItems(),
                "primary weapons cannot use native block breaking");
        f.red.slot = 7;
        for (Block protectedBlock : List.of(floor, vault, wall)) {
            BlockBreakEvent broken = new BlockBreakEvent(protectedBlock, f.red.player);
            f.session.onBlockBreak(broken);
            check(
                    broken.isCancelled() && !broken.isDropItems(),
                    "demolition slot preserves permanent terrain and objectives");
        }
        f.protection().put(RED, 101L);
        BlockBreakEvent protectedBreak = new BlockBreakEvent(cover, f.red.player);
        f.session.onBlockBreak(protectedBreak);
        check(protectedBreak.isCancelled(), "spawn protection also blocks terrain demolition");
        f.connected().remove(RED);
        EntityDamageByEntityEvent oldBlast = hit(legacy.entity, f.blue.player, 3);
        f.session.onEntityDamageByEntity(oldBlast);
        check(oldBlast.isCancelled(), "TNT from departed source cannot damage players");
    }

    private static void customDamageAndActionGates() throws Exception {
        Fixture f = new Fixture();
        EntityDamageByEntityEvent ordinarySelf = hit(f.blue.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(ordinarySelf);
        check(ordinarySelf.isCancelled(), "native self damage cannot bypass the team gate");
        set(f.session, "applyingCombatDamage", true);
        f.depositRed();
        f.state.beginSteal(BLUE, 100);
        EntityDamageByEntityEvent customSelf = hit(f.blue.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(customSelf);
        check(
                !customSelf.isCancelled() && !f.state.stealDeadlines().containsKey(BLUE),
                "custom self explosion passes the team gate and interrupts stealing");
        EntityDamageByEntityEvent customAlly = hit(f.blueMate.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(customAlly);
        check(customAlly.isCancelled(), "custom damage still cannot hurt teammates");
        f.protection().put(BLUE, 101L);
        EntityDamageByEntityEvent protectedSelf = hit(f.blue.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(protectedSelf);
        check(protectedSelf.isCancelled(), "custom self explosion respects spawn protection");
        f.protection().clear();
        f.respawns().put(BLUE, 160L);
        EntityDamageByEntityEvent respawningSelf = hit(f.blue.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(respawningSelf);
        check(
                respawningSelf.isCancelled(),
                "custom self explosion cannot hurt a respawning player");
        f.respawns().clear();
        set(f.session, "applyingCombatDamage", false);
        FinalsCombatContext context = context(f.session);
        check(context.canAct(f.blue.player), "active unprotected player can use combat equipment");
        f.state.beginSteal(BLUE, 100);
        check(!context.canAct(f.blue.player), "stealing blocks weapon and gadget activation");
        f.state.cancelSteal(BLUE);
        f.protection().put(BLUE, 101L);
        check(!context.canAct(f.blue.player), "spawn protection blocks gadget activation");
        f.protection().clear();
        f.respawns().put(BLUE, 160L);
        check(!context.canAct(f.blue.player), "respawn waiting blocks gadget activation");
        f.respawns().clear();
        set(f.session, "state", null);
        check(!context.canAct(f.blue.player), "countdown blocks gadget activation");
    }

    private static FinalsCombatContext context(FinalsSession session) throws Exception {
        var method = FinalsSession.class.getDeclaredMethod("combatContext");
        method.setAccessible(true);
        return (FinalsCombatContext) method.invoke(session);
    }

    private static void menuAndEquipmentSlotGates() throws Exception {
        Fixture f = new Fixture();
        set(f.session, "loadouts", bare(FinalsLoadouts.class));
        FinalsCombatContext context = context(f.session);
        check(context.canAct(f.blue.player), "ordinary inventory view permits combat");
        InventoryOpenEvent ordinary = new InventoryOpenEvent(f.blue.view);
        f.session.onInventoryOpen(ordinary);
        check(ordinary.isCancelled(), "ordinary container inventories stay locked");
        var menuType = Class.forName(FinalsLoadouts.class.getName() + "$Menu");
        var menuConstructor = menuType.getDeclaredConstructor(UUID.class);
        menuConstructor.setAccessible(true);
        f.blue.openHolder = (InventoryHolder) menuConstructor.newInstance(BLUE);
        check(!context.canAct(f.blue.player), "open loadout menu blocks all combat equipment");
        InventoryOpenEvent loadout = new InventoryOpenEvent(f.blue.view);
        f.session.onInventoryOpen(loadout);
        check(!loadout.isCancelled(), "loadout menu is the sole allowed inventory");
        f.blue.openHolder = null;
        check(context.canAct(f.blue.player), "closing loadout menu restores combat eligibility");

        FinalsWeapons weapons = new FinalsWeapons(context);
        var equippedType = Class.forName(FinalsWeapons.class.getName() + "$Equipped");
        var equippedConstructor = equippedType.getDeclaredConstructor(FinalsWeapon.class);
        equippedConstructor.setAccessible(true);
        map(weapons, "equipped")
                .put(BLUE, equippedConstructor.newInstance(FinalsWeapon.SLEDGEHAMMER));
        map(f.session, "classes").put(BLUE, FinalsClass.HEAVY);
        set(f.session, "weapons", weapons);
        for (int slot : new int[] {1, 4, 7, 8}) {
            f.blue.slot = slot;
            EntityDamageByEntityEvent handHit = hit(f.blue.player, f.red.player, 4);
            f.session.onEntityDamageByEntity(handHit);
            check(
                    handHit.isCancelled(),
                    "gadget, specialization, demolition and menu slots cannot deal vanilla melee"
                        + " damage");
        }
    }

    private static void protectedPlacementAreas() throws Exception {
        Fixture f = new Fixture();
        FinalsCombatContext context = context(f.session);
        for (Location objective :
                List.of(f.arena.vault(), f.arena.station(0), f.arena.station(1))) {
            check(
                    !context.canPlace(objective.getBlock()),
                    "objective block rejects temporary cover");
            check(
                    !context.canPlace(objective.clone().add(0, 1, 0).getBlock()),
                    "temporary cover cannot seal the air immediately above an objective");
            check(
                    !context.canPlace(objective.clone().add(3, 0, 0).getBlock()),
                    "objective placement protection includes its three-block boundary");
        }
        var spawnIndex = FinalsSession.class.getDeclaredMethod("spawnIndex", UUID.class);
        spawnIndex.setAccessible(true);
        for (var member : ROSTER.entrySet()) {
            Location spawn =
                    f.arena.spawn(
                            member.getValue(), (int) spawnIndex.invoke(f.session, member.getKey()));
            check(
                    !context.canPlace(spawn.getBlock()),
                    "each roster spawn stays clear at foot level");
            check(
                    !context.canPlace(spawn.clone().add(0, 1, 0).getBlock()),
                    "each roster spawn keeps standing headroom clear");
            check(
                    !context.canPlace(spawn.clone().add(2, 2, 0).getBlock()),
                    "two-block spawn protection also preserves upper barricade headroom");
        }
        Location spawn = f.arena.spawn(0, (int) spawnIndex.invoke(f.session, RED));
        f.connected().clear();
        check(
                !context.canPlace(spawn.getBlock()),
                "disconnected roster members retain safe future spawn locations");
        check(
                context.canPlace(f.world.block(8, 65, 0)),
                "remote in-bounds terrain remains available for useful cover");
        check(
                context.canPlace(f.world.block(8, 67, 0)),
                "remote upper cover is permitted outside reserved areas");
        check(
                !context.canPlace(f.world.block(30, 65, 0)),
                "placement outside arena bounds is denied");
        check(
                !context.canPlace(new FakeWorld().block(8, 65, 0)),
                "placement cannot leak into a different world");
        check(!context.canPlace(null), "missing placement targets are rejected safely");
        FinalsGadgets gadgets = new FinalsGadgets(context);
        var placeable = FinalsGadgets.class.getDeclaredMethod("placeable", Block.class);
        placeable.setAccessible(true);
        check(
                !(boolean) placeable.invoke(gadgets, spawn.clone().add(0, 1, 0).getBlock()),
                "gadget placement enforces the session's spawn restriction before touching"
                    + " terrain");
        check(
                !(boolean)
                        placeable.invoke(gadgets, f.arena.vault().clone().add(0, 1, 0).getBlock()),
                "gadget placement enforces the session's objective restriction");
    }

    private static void fixedCountdownAnchor() throws Exception {
        Fixture f = new Fixture();
        f.red.walkSpeed = .3f;
        f.red.flySpeed = .2f;
        f.red.velocity = new Vector(.2, .42, -.1);
        callPlayerMethod(f.session, "freeze", f.red.player);
        check(
                f.red.walkSpeed == 0 && f.red.flySpeed == 0 && f.red.velocity.lengthSquared() == 0,
                "freeze disables walking and flight and clears preexisting velocity");
        f.red.location.add(.15, .4, .15);
        callPlayerMethod(f.session, "freeze", f.red.player);
        Location attempted = new Location(f.world.world, 3, 67, 3, 70, 25);
        PlayerMoveEvent move = new PlayerMoveEvent(f.red.player, f.red.location.clone(), attempted);
        f.session.onPlayerMove(move);
        check(
                move.getTo().getX() == 2 && move.getTo().getY() == 65 && move.getTo().getZ() == 2,
                "repeated countdown movement returns to original anchor instead of drifting"
                        + " event.from");
        check(
                move.getTo().getYaw() == 70 && move.getTo().getPitch() == 25,
                "countdown permits looking without changing position");
        PlayerJumpEvent jump =
                new PlayerJumpEvent(f.red.player, f.red.location.clone(), attempted.clone());
        f.session.onPlayerJump(jump);
        check(
                jump.isCancelled() && f.red.velocity.lengthSquared() == 0,
                "countdown cancels jumping before velocity can accumulate");
        callPlayerMethod(f.session, "release", f.red.player);
        callPlayerMethod(f.session, "release", f.red.player);
        check(
                f.red.walkSpeed == .3f && f.red.flySpeed == .2f,
                "release restores original custom speeds exactly once after repeated freeze");
        PlayerMoveEvent released =
                new PlayerMoveEvent(f.red.player, f.red.location.clone(), attempted.clone());
        f.session.onPlayerMove(released);
        check(
                released.getTo().getX() == 3 && released.getTo().getY() == 67,
                "released fighter moves freely within arena");
        PlayerJumpEvent releasedJump =
                new PlayerJumpEvent(f.red.player, f.red.location.clone(), attempted.clone());
        f.session.onPlayerJump(releasedJump);
        check(!releasedJump.isCancelled(), "release restores jumping");
    }

    private static void callPlayerMethod(FinalsSession session, String name, Player player)
            throws Exception {
        var method = FinalsSession.class.getDeclaredMethod(name, Player.class);
        method.setAccessible(true);
        method.invoke(session, player);
    }

    private static EntityDamageByEntityEvent hit(Entity source, Player target, double damage) {
        return new EntityDamageByEntityEvent(
                source, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, null, damage);
    }

    private static EntityDamageEvent environmental(Player target, double damage) {
        return new EntityDamageEvent(target, EntityDamageEvent.DamageCause.FALL, null, damage);
    }

    private static PlayerInteractEvent interact(Player player, Block block, EquipmentSlot hand) {
        return new PlayerInteractEvent(
                player,
                block == null ? Action.RIGHT_CLICK_AIR : Action.RIGHT_CLICK_BLOCK,
                null,
                block,
                BlockFace.UP,
                hand);
    }

    private static EntityExplodeEvent explosion(Entity entity, List<Block> blocks) {
        return new EntityExplodeEvent(
                entity,
                new Location(null, 0, 65, 0),
                new ArrayList<>(blocks),
                1,
                ExplosionResult.DESTROY);
    }

    private static final class Fixture {
        final FakeWorld world = new FakeWorld();
        final FinalsArena arena = new FinalsArena(world.world);
        final FinalsState state = new FinalsState(ROSTER, 0);
        final FakePlayer red = new FakePlayer(RED, world.world);
        final FakePlayer blue = new FakePlayer(BLUE, world.world);
        final FakePlayer blueMate = new FakePlayer(BLUE_MATE, world.world);
        final FinalsSession session;

        Fixture() throws Exception {
            ActiveSession active = bare(ActiveSession.class);
            set(active, "phase", GamePhase.RUNNING);
            Messages messages = new Messages(Audience::empty, null);
            session =
                    new FinalsSession(
                            new SessionServices(
                                    null, active, null, null, null, null, null, null, null, null,
                                    messages, null),
                            null);
            arena.build();
            set(session, "world", world.world);
            set(session, "arena", arena);
            set(session, "state", state);
            set(session, "rules", FinalsState.Rules.defaults());
            set(session, "tick", 100L);
            set(session, "respawnTicks", 60);
            map(session, "roster").putAll(ROSTER);
            connected().addAll(ROSTER.keySet());
        }

        void depositRed() {
            state.activateVault(RED, 0);
            state.pickUp(RED, 100);
            state.deposit(RED, 0, 100);
        }

        Set<UUID> connected() throws Exception {
            return setField(session, "connected");
        }

        Set<UUID> withdrawn() throws Exception {
            return setField(session, "withdrawn");
        }

        Map<UUID, Long> protection() throws Exception {
            return map(session, "protectedUntil");
        }

        Map<UUID, Long> respawns() throws Exception {
            return map(session, "respawnAt");
        }

        Map<UUID, Entity> ordnance() throws Exception {
            return map(session, "ordnance");
        }
    }

    private static final class FakeStack extends ItemStack {
        Material type = Material.AIR;
        int amount = 1;

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    private static final class FakePlayer {
        final UUID id;
        final FakeStack hand = new FakeStack();
        final Player player;
        Location location;
        boolean dead;
        int slot;
        InventoryHolder openHolder;
        final InventoryView view;
        float walkSpeed = .2f, flySpeed = .1f;
        Vector velocity = new Vector();

        FakePlayer(UUID id, World world) {
            this.id = id;
            location = new Location(world, 2, 65, 2);
            Inventory top =
                    proxy(
                            Inventory.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getHolder" -> openHolder;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected top inventory call: "
                                                                + method.getName());
                                    });
            view =
                    proxy(
                            InventoryView.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getTopInventory" -> top;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected inventory view call: "
                                                                + method.getName());
                                    });
            PlayerInventory inventory =
                    proxy(
                            PlayerInventory.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getItemInMainHand" -> hand;
                                        case "getHeldItemSlot" -> slot;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected inventory call: "
                                                                + method.getName());
                                    });
            player =
                    proxy(
                            Player.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getWorld" -> location.getWorld();
                                        case "getGameMode" -> GameMode.SURVIVAL;
                                        case "getLocation" -> location.clone();
                                        case "getEyeLocation" -> location.clone().add(0, 1.6, 0);
                                        case "getInventory" -> inventory;
                                        case "getOpenInventory" -> view;
                                        case "getWalkSpeed" -> walkSpeed;
                                        case "getFlySpeed" -> flySpeed;
                                        case "setWalkSpeed" -> {
                                            walkSpeed = (float) args[0];
                                            yield null;
                                        }
                                        case "setFlySpeed" -> {
                                            flySpeed = (float) args[0];
                                            yield null;
                                        }
                                        case "setVelocity" -> {
                                            velocity = ((Vector) args[0]).clone();
                                            yield null;
                                        }
                                        case "isDead" -> dead;
                                        case "isSneaking" -> true;
                                        case "sendMessage" -> null;
                                        case "displayName" -> Component.text(id.toString());
                                        case "equals" -> p == args[0];
                                        case "hashCode" -> id.hashCode();
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected player call: "
                                                                + method.getName());
                                    });
        }
    }

    private static final class Shot {
        final UUID id = UUID.randomUUID();
        final Projectile projectile;
        int removed;

        Shot(Player source) {
            projectile =
                    proxy(
                            Projectile.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getShooter" -> source;
                                        case "isValid" -> removed == 0;
                                        case "remove" -> {
                                            removed++;
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected projectile call: "
                                                                + method.getName());
                                    });
        }
    }

    private static final class Bomb {
        final UUID id = UUID.randomUUID();
        final TNTPrimed entity;
        Player source;
        int fuse;

        Bomb(Player source) {
            this.source = source;
            entity =
                    proxy(
                            TNTPrimed.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getSource" -> this.source;
                                        case "setSource" -> {
                                            this.source = (Player) args[0];
                                            yield null;
                                        }
                                        case "setFuseTicks" -> {
                                            fuse = (int) args[0];
                                            yield null;
                                        }
                                        case "setYield", "setVelocity" -> null;
                                        case "isValid" -> true;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected TNT call: " + method.getName());
                                    });
        }
    }

    private record Position(int x, int y, int z) {}

    private static final class FakeWorld {
        final Map<Position, Material> blocks = new HashMap<>();
        final World world;
        int spawned;
        Bomb lastBomb;

        FakeWorld() {
            world =
                    proxy(
                            World.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getBlockAt" ->
                                                args[0] instanceof Location location
                                                        ? block(
                                                                location.getBlockX(),
                                                                location.getBlockY(),
                                                                location.getBlockZ())
                                                        : block(
                                                                (int) args[0],
                                                                (int) args[1],
                                                                (int) args[2]);
                                        case "setSpawnLocation" -> true;
                                        case "equals" -> p == args[0];
                                        case "hashCode" -> System.identityHashCode(p);
                                        case "spawn" -> {
                                            spawned++;
                                            lastBomb = new Bomb(null);
                                            yield lastBomb.entity;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected world call: "
                                                                + method.getName());
                                    });
        }

        Block block(int x, int y, int z) {
            Position at = new Position(x, y, z);
            return proxy(
                    Block.class,
                    (p, method, args) ->
                            switch (method.getName()) {
                                case "getWorld" -> world;
                                case "getX" -> x;
                                case "getY" -> y;
                                case "getZ" -> z;
                                case "getType" -> blocks.getOrDefault(at, Material.AIR);
                                case "setType" -> {
                                    blocks.put(at, (Material) args[0]);
                                    yield null;
                                }
                                case "getLocation" -> new Location(world, x, y, z);
                                case "equals" -> p == args[0];
                                case "hashCode" -> at.hashCode();
                                default ->
                                        throw new AssertionError(
                                                "Unexpected block call: " + method.getName());
                            });
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static <T> T bare(Class<T> type) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type));
    }

    private static Field field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target, name).set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Object target, String name) throws Exception {
        return (Map<K, V>) field(target, name).get(target);
    }

    @SuppressWarnings("unchecked")
    private static <T> Set<T> setField(Object target, String name) throws Exception {
        return (Set<T>) field(target, name).get(target);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
