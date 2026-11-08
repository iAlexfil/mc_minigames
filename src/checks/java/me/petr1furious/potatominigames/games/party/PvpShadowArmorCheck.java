package me.petr1furious.potatominigames.games.party;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Real cloak transitions with fake clients; any attempt to mutate inventory fails. */
final class PvpShadowArmorCheck {
    static void run() {
        World world = world(), otherWorld = world();
        FakePlayer shadow = new FakePlayer(world), opponent = new FakePlayer(world);
        FakePlayer spectator = new FakePlayer(world), outsider = new FakePlayer(otherWorld);
        shadow.tracking.addAll(List.of(shadow.player, opponent.player, outsider.player));
        PvpShadowArmor cloak = new PvpShadowArmor(p -> true);
        cloak.watch(shadow.player);
        cloak.update(shadow.player, false);
        check(opponent.packets.isEmpty(), "Visible Shadow uses native armour rendering");
        cloak.update(shadow.player, true);
        checkHidden(opponent.last());
        check(shadow.packets.isEmpty() && outsider.packets.isEmpty(), "Self inventory and other worlds receive no fake equipment");
        check(shadow.armor.values().stream().allMatch(item -> item != null), "Hiding never removes real protective armour");
        cloak.update(shadow.player, true);
        check(opponent.packets.size() == 1, "Unchanged invisibility does not spam equipment packets");
        shadow.tracking.add(spectator.player);
        cloak.update(shadow.player, true);
        checkHidden(spectator.last());
        check(opponent.packets.size() == 1, "A new viewer does not resend to existing viewers");
        ItemStack replacement = new FakeStack();
        shadow.armor.put(EquipmentSlot.CHEST, replacement);
        shadow.armor.put(EquipmentSlot.FEET, null);
        cloak.changed(shadow.player);
        cloak.update(shadow.player, true);
        check(opponent.packets.size() == 2 && spectator.packets.size() == 2, "Equipment broadcasts are masked again after armour changes");
        checkHidden(opponent.last());
        cloak.update(shadow.player, false);
        checkActual(shadow, opponent.last());
        checkActual(shadow, spectator.last());
        check(opponent.last().get(EquipmentSlot.CHEST) == replacement && opponent.last().get(EquipmentSlot.FEET) == null,
                "Expiry restores current gear, including changed or removed armour, rather than old snapshots");
        cloak.update(shadow.player, true);
        checkHidden(opponent.last());
        shadow.tracking.remove(spectator.player);
        int oldPackets = spectator.packets.size();
        cloak.remove(shadow.id);
        checkActual(shadow, opponent.last());
        check(spectator.packets.size() == oldPackets, "Cleanup never sends stale entity equipment to an untracking viewer");
        int restored = opponent.packets.size();
        cloak.remove(shadow.id); cloak.update(shadow.player, true);
        check(opponent.packets.size() == restored, "Removed cloaks cannot hide armour again");
        cloak.watch(shadow.player); cloak.update(shadow.player, true);
        opponent.world = otherWorld;
        cloak.clear();
        checkHidden(opponent.last());
        check(opponent.packets.size() == restored + 1, "World changes prevent restoration packets to a reused entity id");
        opponent.world = world;
        cloak.watch(shadow.player); cloak.update(shadow.player, true);
        cloak.close(); cloak.close();
        checkActual(shadow, opponent.last());
        // Both players can draw Shadow in a deciding round; one cleanup must not reveal the other.
        opponent.tracking.add(shadow.player);
        cloak.watch(shadow.player); cloak.watch(opponent.player);
        cloak.update(shadow.player, true); cloak.update(opponent.player, true);
        checkHidden(opponent.last()); checkHidden(shadow.last());
        int ownPackets = shadow.packets.size();
        cloak.remove(shadow.id);
        checkActual(shadow, opponent.last());
        check(shadow.packets.size() == ownPackets, "Each Shadow has independent cloak state");
        cloak.clear();
        checkActual(opponent, shadow.last());
    }
    private static void checkHidden(Map<EquipmentSlot, ItemStack> packet) {
        check(packet.size() == 4 && packet.keySet().containsAll(PvpShadowArmor.ARMOR)
                && packet.values().stream().allMatch(item -> item == null), "All four armour slots are hidden; hands stay native");
    }
    private static void checkActual(FakePlayer player, Map<EquipmentSlot, ItemStack> packet) {
        check(packet.size() == 4, "Restoration only changes the four armour slots");
        for (EquipmentSlot slot : PvpShadowArmor.ARMOR)
            check(packet.get(slot) == player.armor.get(slot), "Restored equipment matches the real inventory");
    }
    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (p, m, a) -> {
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            throw new AssertionError("Unexpected world call: " + m.getName());
        });
    }
    private static final class FakeStack extends ItemStack { }
    private static final class FakePlayer {
        final UUID id = UUID.randomUUID();
        World world;
        final Set<Player> tracking = new HashSet<>();
        final Map<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);
        final List<Map<EquipmentSlot, ItemStack>> packets = new ArrayList<>();
        final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[] {PlayerInventory.class}, (p, m, a) -> {
                    if (m.getName().equals("getItem") && a[0] instanceof EquipmentSlot slot) return armor.get(slot);
                    throw new AssertionError("Cloak must not modify inventory: " + m.getName());
                });
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (p, m, a) -> {
            return switch (m.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "isOnline" -> true;
                case "getTrackedBy" -> tracking;
                case "getInventory" -> inventory;
                case "hashCode" -> id.hashCode();
                case "equals" -> p == a[0];
                case "sendEquipmentChange" -> {
                    @SuppressWarnings("unchecked") var packet = (Map<EquipmentSlot, ItemStack>) a[1];
                    packets.add(new EnumMap<>(packet)); yield null;
                }
                default -> throw new AssertionError("Unexpected client call: " + m.getName());
            };
        });
        FakePlayer(World world) {
            this.world = world;
            for (EquipmentSlot slot : PvpShadowArmor.ARMOR) armor.put(slot, new FakeStack());
        }
        Map<EquipmentSlot, ItemStack> last() { return packets.getLast(); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
