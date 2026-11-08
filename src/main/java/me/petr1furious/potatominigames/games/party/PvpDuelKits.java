package me.petr1furious.potatominigames.games.party;

import java.util.List;
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
            throw new IllegalArgumentException("Для BO3 нужны два разных игрока.");
    }
    private static void validate(int number) {
        if (number != -1 && (number < 1 || number > classCount()))
            throw new IllegalArgumentException("Номер класса: от 1 до " + classCount() + ", либо -1 для случайного. /party classes");
    }
    public static int classCount() { return PvpClass.values().length; }
    public static String label(int number) {
        validate(number);
        return number == -1 ? "случайный класс" : number + ". " + PvpClass.values()[number - 1].title;
    }
    public static List<String> classList() {
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
            throw new IllegalArgumentException("Некорректные параметры начальных классов BO3. Используй /party duel <класс_A> <класс_B>.", failure);
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
            throw new IllegalArgumentException("Начальные классы заданы для другой пары игроков.");
        PvpClass a = resolve(classA, random), b = resolve(classB, random);
        return pair.first().equals(playerA) ? List.of(a, b) : List.of(b, a);
    }
}
