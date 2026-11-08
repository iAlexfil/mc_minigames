package me.petr1furious.potatominigames.games.party;

import java.util.List;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

/** Numbered starting kits are bound to player UUIDs, independently of tournament shuffling. */
public record PvpDuelKits(int classA, int classB, UUID playerA, UUID playerB) {
    private static final String CLASS_A = "pvp.classA", CLASS_B = "pvp.classB",
            PLAYER_A = "pvp.playerA", PLAYER_B = "pvp.playerB";

    public PvpDuelKits {
        validate(classA); validate(classB);
        if (playerA == null || playerB == null || playerA.equals(playerB))
            throw I18n.exception("party.duels.duel.distinct_players");
    }
    private static void validate(int number) {
        if (number != -1 && (number < 1 || number > classCount()))
            throw I18n.exception("party.duels.duel.invalid_class", classCount());
    }
    public static int classCount() { return PvpClass.values().length; }
    public static Component label(int number) {
        validate(number);
        return number == -1 ? I18n.tr("party.duels.duel.random_class") : I18n.tr("party.duels.duel.numbered_class", number, PvpClass.values()[number - 1].title);
    }
    public static List<Component> classList() {
        return IntStream.rangeClosed(1, classCount()).mapToObj(PvpDuelKits::label).toList();
    }
    public Map<String, String> options() {
        return Map.of(CLASS_A, Integer.toString(classA), CLASS_B, Integer.toString(classB),
                PLAYER_A, playerA.toString(), PLAYER_B, playerB.toString());
    }
    static PvpDuelKits fromOptions(Map<String, String> options) {
        if (List.of(CLASS_A, CLASS_B, PLAYER_A, PLAYER_B).stream().noneMatch(options::containsKey)) return null;
        try {
            return new PvpDuelKits(Integer.parseInt(options.get(CLASS_A)), Integer.parseInt(options.get(CLASS_B)),
                    UUID.fromString(options.get(PLAYER_A)), UUID.fromString(options.get(PLAYER_B)));
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw I18n.exception("party.duels.duel.invalid_options");
        }
    }
    boolean matches(List<UUID> roster) {
        return roster.size() == 2 && roster.contains(playerA) && roster.contains(playerB);
    }
    private static PvpClass resolve(int number, Random random) {
        return number == -1 ? PvpClass.random(random) : PvpClass.values()[number - 1];
    }
    List<PvpClass> opening(DuelTournament.Pair pair, Random random) {
        if (!pair.contains(playerA) || !pair.contains(playerB))
            throw I18n.exception("party.duels.duel.wrong_pair");
        PvpClass a = resolve(classA, random), b = resolve(classB, random);
        return pair.first().equals(playerA) ? List.of(a, b) : List.of(b, a);
    }
}
