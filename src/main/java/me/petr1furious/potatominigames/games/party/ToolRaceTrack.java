package me.petr1furious.potatominigames.games.party;

import org.bukkit.Material;

import java.util.*;

final class ToolRaceTrack {
    private final Map<UUID, Integer> progress = new LinkedHashMap<>();
    private final Map<UUID, Long> finished = new HashMap<>();
    private final List<Material> blocks;
    private boolean closed;

    ToolRaceTrack(Collection<UUID> roster, List<Material> blocks) {
        if (blocks.size() != 50) throw new IllegalArgumentException("Fifty blocks required");
        this.blocks = List.copyOf(blocks);
        roster.forEach(id -> progress.put(id, 0));
    }

    static List<Material> generate(Random random) {
        var palette = new ArrayList<>(List.of(Material.OAK_PLANKS, Material.DIRT, Material.STONE));
        Collections.shuffle(palette, random);
        List<Material> blocks = new ArrayList<>();
        for (int i = 0; i < 50; i++) blocks.add(palette.get(i % 3));
        Collections.shuffle(blocks, random);
        return List.copyOf(blocks);
    }

    boolean mine(UUID id, int depth, long tick) {
        if (closed || !progress.containsKey(id) || depth != progress.get(id) || depth >= 50)
            return false;
        progress.put(id, depth + 1);
        if (depth == 49) finished.put(id, tick);
        return true;
    }

    int progress(UUID id) {
        return progress.getOrDefault(id, 0);
    }

    boolean finished(UUID id) {
        return finished.containsKey(id);
    }

    void close() {
        closed = true;
    }

    Material block(int depth) {
        return blocks.get(depth);
    }

    PartyRoundResult result(Set<UUID> eligible) {
        return PartyRoundResult.ordered(
                progress.keySet().stream().filter(eligible::contains).toList(),
                Comparator.<UUID, Boolean>comparing(id -> !finished(id))
                        .thenComparingLong(id -> finished.getOrDefault(id, Long.MAX_VALUE))
                        .thenComparing(Comparator.<UUID>comparingInt(this::progress).reversed()));
    }
}
