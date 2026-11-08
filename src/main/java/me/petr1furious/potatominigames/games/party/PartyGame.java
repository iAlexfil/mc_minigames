package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;

import java.util.List;

public final class PartyGame implements Minigame {
    private final PartyConfig config;

    public PartyGame(ConfigRegistry configs) {
        this(new PartyConfig(configs));
    }

    PartyGame(PartyConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return "party";
    }

    @Override
    public Component displayName() {
        return Component.text("Вечеринка", NamedTextColor.LIGHT_PURPLE);
    }

    @Override
    public List<String> modes() {
        return List.of(
                "pvp",
                "sumo",
                "pvp_ffa",
                "sumo_ffa",
                "arms_race",
                "tnt_run",
                "mob_count",
                "rhino",
                "miners",
                "color_floor",
                "anvils",
                "build_memory",
                "tool_race",
                "hole_in_the_wall",
                "party_games");
    }

    @Override
    public boolean showModes() {
        return true;
    }

    @Override
    public Material icon() {
        return Material.IRON_SWORD;
    }

    @Override
    public String modeTitle(String mode) {
        return switch (mode) {
            case "hole_in_the_wall" -> "Дырка в стене";
            case "party_games" -> "Party Games";
            case "miners" -> "Битва шахтёров";
            case "pvp_ffa" -> "Массовое PvP";
            case "sumo_ffa" -> "Массовое сумо";
            case "color_floor" -> "Цветной пол";
            case "anvils" -> "Наковальни";
            case "build_memory" -> "Повтори постройку";
            case "tool_race" -> "Гонка инструментов";
            default -> mode;
        };
    }

    @Override
    public Material icon(String mode) {
        return switch (mode) {
            case "miners", "tool_race" -> Material.DIAMOND_PICKAXE;
            case "color_floor" -> Material.LIME_CONCRETE;
            case "pvp_ffa" -> Material.DIAMOND_SWORD;
            case "sumo_ffa" -> Material.STICK;
            case "anvils" -> Material.ANVIL;
            case "hole_in_the_wall" -> Material.SLIME_BLOCK;
            case "party_games" -> Material.FIREWORK_ROCKET;
            case "build_memory" -> Material.BRICKS;
            default -> icon();
        };
    }

    @Override
    public int maxPlayers(String mode) {
        return mode.equals("pvp")
                        || mode.equals("sumo")
                        || mode.equals("miners")
                        || List.of(
                                        "color_floor",
                                        "anvils",
                                        "build_memory",
                                        "tool_race",
                                        "hole_in_the_wall",
                                        "party_games")
                                .contains(mode)
                ? 12
                : 16;
    }

    @Override
    public Component description(String mode) {
        return Component.text(
                switch (mode) {
                    case "hole_in_the_wall" ->
                            "Проходи сквозь стены • не дай столкнуть себя с платформы";
                    case "party_games" ->
                            "Все игры в одном матче • каждый сам за себя • призовые очки 3 / 2 / 1";
                    case "color_floor" -> "Найди нужный цвет • неверный пол исчезает";
                    case "anvils" -> "Ливни наковален • перемещающиеся укрытия • толкай соперников";
                    case "build_memory" -> "Запомни и повтори постройку • одна ошибка — выбывание";
                    case "tool_race" -> "50 блоков вниз • выбирай нужный инструмент";
                    case "miners" ->
                            "Битва шахтёров: личные шахты • случайный лут из руд • затем PvP";
                    case "sumo" -> "Сумо: столкни соперника • каждый с каждым • BO3";
                    case "pvp_ffa" -> "Массовое PvP: все против всех • случайные классы • одна жизнь";
                    case "sumo_ffa" -> "Массовое сумо: все на одной платформе • последний выживший";
                    case "arms_race" -> "Гонка вооружений: все против всех • 12 ступеней оружия";
                    case "tnt_run" -> "TNT Run: три яруса • исчезающий пол • последний выживший";
                    case "mob_count" ->
                            "Пересчёт: запомни мобов • выбери голову • прыгни на верную платформу";
                    case "rhino" ->
                            "Носорожий переполох: рывки разорителей • бросай мины • не упади";
                    default -> "PvP: каждый с каждым • BO3 • случайные классы";
                });
    }

    @Override
    public GameSession createSession(SessionServices services, String mode) {
        if (mode.equals("hole_in_the_wall"))
            return new HoleWallSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("party_games")) return new PartyGamesSession(services, config);
        if (mode.equals("color_floor"))
            return new ColorFloorSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("anvils"))
            return new AnvilSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("build_memory"))
            return new BuildMemorySession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("tool_race"))
            return new ToolRaceSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("miners")) return new MinersSession(services, config);
        if (mode.equals("arms_race")) return new ArmsRaceSession(services, config);
        if (mode.equals("tnt_run")) return new TntRunSession(services, config);
        if (mode.equals("mob_count")) return new MobCountSession(services, config);
        if (mode.equals("rhino")) return new RhinoSession(services, config);
        return new DuelSession(services, config, mode.equals("sumo") || mode.equals("sumo_ffa"),
                mode.equals("pvp_ffa") || mode.equals("sumo_ffa"));
    }
}
