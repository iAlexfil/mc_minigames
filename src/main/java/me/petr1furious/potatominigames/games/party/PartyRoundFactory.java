package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;

final class PartyRoundFactory {
    static PartyRoundSession create(
            String mode, SessionServices services, PartyConfig config, PartyRoundContext context) {
        return switch (mode) {
            case "hole_in_the_wall" -> new HoleWallSession(services, config, context);
            case "pvp", "sumo" -> new DuelSession(services, config, mode.equals("sumo"), context);
            case "arms_race" -> new ArmsRaceSession(services, config, context);
            case "tnt_run" -> new TntRunSession(services, config, context);
            case "mob_count" -> new MobCountSession(services, config, context);
            case "rhino" -> new RhinoSession(services, config, context);
            case "miners" -> new MinersSession(services, config, context);
            case "color_floor" -> new ColorFloorSession(services, config, context);
            case "anvils" -> new AnvilSession(services, config, context);
            case "build_memory" -> new BuildMemorySession(services, config, context);
            case "tool_race" -> new ToolRaceSession(services, config, context);
            default -> throw new IllegalArgumentException("Unknown party round: " + mode);
        };
    }

    static String title(String mode) {
        return switch (mode) {
            case "hole_in_the_wall" -> "Дырка в стене";
            case "pvp" -> "PvP";
            case "sumo" -> "Сумо";
            case "arms_race" -> "Гонка вооружений";
            case "tnt_run" -> "TNT Run";
            case "mob_count" -> "Пересчёт";
            case "rhino" -> "Носорожий переполох";
            case "miners" -> "Битва шахтёров";
            case "color_floor" -> "Цветной пол";
            case "anvils" -> "Наковальни";
            case "build_memory" -> "Повтори постройку";
            case "tool_race" -> "Гонка инструментов";
            default -> mode;
        };
    }
}
