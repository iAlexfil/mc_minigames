package me.petr1furious.potatominigames.games.cs;

import java.util.*;

/** CS2-style MR12 bomb-defusal rules. Stable team IDs survive the halftime faction swap. */
final class CsMatch {
    enum Phase {
        FREEZE,
        LIVE,
        ROUND_END,
        HALFTIME,
        FINISHED
    }

    enum Reason {
        ELIMINATION,
        TIMEOUT,
        EXPLOSION,
        DEFUSE,
        FORFEIT
    }

    enum Utility {
        HE,
        FLASH,
        SMOKE,
        FIRE
    }

    enum Buy {
        OK,
        MONEY,
        SIDE,
        LIMIT,
        CLOSED
    }

    static final class Actor {
        final UUID id;
        final int team;
        int money = 800, kills, deaths, assists, mvps, life, roundKills;
        UUID killedBy;
        KillInfo killInfo;
        double health = 100, armour;
        boolean helmet, kit, alive, online = true;
        CsGun primary, secondary;
        final EnumMap<Utility, Integer> utility = new EnumMap<>(Utility.class);

        Actor(UUID id, int team) {
            this.id = id;
            this.team = team;
        }

        CsGun gun(int slot) {
            return slot == 0 ? primary : slot == 1 ? secondary : null;
        }

        int grenades() {
            return utility.values().stream().mapToInt(Integer::intValue).sum();
        }

        void loseEquipment() {
            primary = null;
            secondary = null;
            armour = 0;
            helmet = false;
            kit = false;
            utility.clear();
        }
    }

    record Round(int number, int winner, Reason reason) {}

    record Damage(double health, boolean killed, boolean head) {}

    enum KillCause {
        BULLET,
        KNIFE,
        HE,
        FIRE,
        C4,
        OTHER;

        boolean grenade() {
            return this == HE || this == FIRE || this == C4;
        }
    }

    record KillInfo(CsWeapon weapon, boolean headshot, KillCause cause) {}

    private final Map<UUID, Actor> actors = new LinkedHashMap<>();
    private final int[] economySizes = new int[2];
    final int halfRounds, freezeTicks, roundTicks, bombTicks, buyTicks;
    final boolean practice, deathmatch;
    final int[] score = {0, 0}, losses = {1, 1}, timeouts = {4, 4};
    private final boolean[] pendingPause = {false, false};
    private final Map<UUID, Map<UUID, Double>> contributions = new HashMap<>();
    Phase phase = Phase.FREEZE;
    int number, version;
    long until, liveAt, buyUntil, detonateAt = -1;
    UUID carrier, planter, defuser, lastKiller;
    int bombSite = -1;
    boolean dropped;
    private boolean ended;
    Round lastRound;

    CsMatch(
            Map<UUID, Integer> teams,
            int halfRounds,
            int freezeTicks,
            int roundTicks,
            int bombTicks,
            int buyTicks,
            boolean practice) {
        this(teams, halfRounds, freezeTicks, roundTicks, bombTicks, buyTicks, practice, false);
    }

    CsMatch(
            Map<UUID, Integer> teams,
            int halfRounds,
            int freezeTicks,
            int roundTicks,
            int bombTicks,
            int buyTicks,
            boolean practice,
            boolean deathmatch) {
        if (teams.isEmpty()
                || teams.size() > (deathmatch ? 16 : 10)
                || teams.values().stream().anyMatch(t -> t < 0 || t > 1)
                || halfRounds < 1) throw new IllegalArgumentException("Invalid CS roster or rules");
        for (int team = 0; team < 2; team++) {
            int selected = team;
            if (!deathmatch && teams.values().stream().filter(t -> t == selected).count() > 5)
                throw new IllegalArgumentException("Five players per team maximum");
        }
        teams.forEach((id, team) -> actors.put(id, new Actor(id, team)));
        this.halfRounds = halfRounds;
        this.freezeTicks = freezeTicks;
        this.roundTicks = roundTicks;
        this.bombTicks = bombTicks;
        this.buyTicks = buyTicks;
        this.practice = practice;
        this.deathmatch = deathmatch;
    }

    Collection<Actor> actors() {
        return Collections.unmodifiableCollection(actors.values());
    }

    Actor actor(UUID id) {
        return actors.get(id);
    }

    int side(int team) {
        return team ^ (number > halfRounds ? 1 : 0);
    }

    int side(Actor p) {
        return side(p.team);
    }

    int teamForSide(int side) {
        return side ^ (number > halfRounds ? 1 : 0);
    }

    boolean live(UUID id) {
        Actor p = actors.get(id);
        return p != null && p.online && p.alive;
    }

    boolean planted() {
        return detonateAt >= 0;
    }

    void start(long now, Random random) {
        if (number != 0) throw new IllegalStateException("Already started");
        if (deathmatch) {
            number = 1;
            phase = Phase.FREEZE;
            until = now + freezeTicks;
            for (Actor p : actors.values()) respawnDeathmatch(p.id);
            version++;
        } else beginRound(now, random);
    }

    boolean respawnDeathmatch(UUID id) {
        Actor p = actors.get(id);
        if (!deathmatch || ended || p == null || !p.online) return false;
        p.loseEquipment();
        p.health = 100;
        p.armour = 100;
        p.helmet = true;
        p.alive = true;
        p.life++;
        p.killedBy = null;
        p.killInfo = null;
        p.money = 0;
        contributions.remove(id);
        return true;
    }

    private void beginRound(long now, Random random) {
        number++;
        // Freeze the participating roster for all payouts in this round. Deaths, spectating
        // and disconnects during combat must never increase rewards for the remaining players.
        for (int team = 0; team < 2; team++) {
            int selected = team;
            economySizes[team] =
                    (int)
                            actors.values().stream()
                                    .filter(p -> p.online && p.team == selected)
                                    .count();
        }
        phase = Phase.FREEZE;
        until = now + freezeTicks;
        carrier = null;
        planter = null;
        defuser = null;
        lastKiller = null;
        detonateAt = -1;
        bombSite = -1;
        dropped = false;
        contributions.clear();
        if (number == halfRounds + 1) {
            Arrays.fill(losses, 1);
            for (Actor p : actors.values()) {
                p.loseEquipment();
            }
        }
        if (number == 1 || number == halfRounds + 1)
            for (Actor p : actors.values()) p.money = startingCash(p.team);
        for (int team = 0; team < 2; team++)
            if (pendingPause[team]) {
                until += 600;
                pendingPause[team] = false;
            }
        List<Actor> terrorists = new ArrayList<>();
        for (Actor p : actors.values()) {
            if (!p.alive) p.loseEquipment();
            p.health = 100;
            p.alive = p.online;
            p.life++;
            p.roundKills = 0;
            p.killedBy = null;
            p.killInfo = null;
            if (p.secondary == null)
                p.secondary = new CsGun(side(p) == 0 ? CsWeapon.GLOCK : CsWeapon.USP);
            if (p.primary != null) p.primary.restock();
            p.secondary.restock();
            if (practice) p.money = 16000;
            if (p.alive && side(p) == 0) terrorists.add(p);
        }
        if (!terrorists.isEmpty()) carrier = terrorists.get(random.nextInt(terrorists.size())).id;
        version++;
    }

    void tick(long now, Random random) {
        if (ended) return;
        if (phase == Phase.FREEZE && now >= until) {
            phase = Phase.LIVE;
            liveAt = now;
            until = now + roundTicks;
            buyUntil = now + buyTicks;
            version++;
        } else if (phase == Phase.ROUND_END && now >= until) {
            if (score[0] >= halfRounds + 1
                    || score[1] >= halfRounds + 1
                    || number >= halfRounds * 2) {
                phase = Phase.FINISHED;
                ended = true;
                version++;
            } else if (number == halfRounds) {
                phase = Phase.HALFTIME;
                until = now + 300;
                version++;
            } else beginRound(now, random);
        } else if (phase == Phase.HALFTIME && now >= until) beginRound(now, random);
        if (phase != Phase.LIVE) return;
        if (deathmatch) {
            if (now >= until) {
                phase = Phase.FINISHED;
                ended = true;
                version++;
            }
            return;
        }
        if (planted() && now >= detonateAt) {
            endRound(teamForSide(0), Reason.EXPLOSION, now);
            return;
        }
        if (!planted() && now >= until) {
            endRound(teamForSide(1), Reason.TIMEOUT, now);
            return;
        }
        if (practice) return;
        long t = actors.values().stream().filter(p -> live(p.id) && side(p) == 0).count();
        long ct = actors.values().stream().filter(p -> live(p.id) && side(p) == 1).count();
        if (ct == 0) endRound(teamForSide(0), Reason.ELIMINATION, now);
        else if (t == 0 && !planted()) endRound(teamForSide(1), Reason.ELIMINATION, now);
    }

    boolean canBuy(Actor p, long now) {
        return p != null
                && live(p.id)
                && (phase == Phase.FREEZE || phase == Phase.LIVE && now <= buyUntil);
    }

    Buy weapon(Actor p, CsWeapon w, long now) {
        if (!canBuy(p, now)) return Buy.CLOSED;
        if (!w.allowed(side(p))) return Buy.SIDE;
        if (p.money < w.price) return Buy.MONEY;
        p.money -= w.price;
        if (w.primary) p.primary = new CsGun(w);
        else p.secondary = new CsGun(w);
        return Buy.OK;
    }

    /** One price source for the shop and the actual debit, including side-specific utility. */
    int gearPrice(Actor p, String id) {
        return switch (id) {
            case "vest" -> 650;
            case "helmet" -> p.helmet ? 650 : p.armour >= 100 ? 350 : 1000;
            case "kit" -> 400;
            case "flash" -> 200;
            case "he", "smoke" -> 300;
            case "fire" -> side(p) == 0 ? 400 : 500;
            default -> 0;
        };
    }

    Buy gear(Actor p, String id, long now) {
        if (!canBuy(p, now)) return Buy.CLOSED;
        int cost = gearPrice(p, id);
        switch (id) {
            case "vest" -> {
                if (p.armour >= 100) return Buy.LIMIT;
            }
            case "helmet" -> {
                if (p.helmet && p.armour >= 100) return Buy.LIMIT;
            }
            case "kit" -> {
                if (side(p) != 1) return Buy.SIDE;
                if (p.kit) return Buy.LIMIT;
            }
            default -> {
                Utility u;
                try {
                    u = Utility.valueOf(id.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return Buy.LIMIT;
                }
                int limit = u == Utility.FLASH ? 2 : 1;
                if (p.grenades() >= 4 || p.utility.getOrDefault(u, 0) >= limit) return Buy.LIMIT;
                if (p.money < cost) return Buy.MONEY;
                p.money -= cost;
                p.utility.merge(u, 1, Integer::sum);
                return Buy.OK;
            }
        }
        if (p.money < cost) return Buy.MONEY;
        p.money -= cost;
        switch (id) {
            case "vest" -> p.armour = 100;
            case "helmet" -> {
                p.armour = 100;
                p.helmet = true;
            }
            case "kit" -> p.kit = true;
            default -> throw new IllegalStateException();
        }
        return Buy.OK;
    }

    boolean consume(Actor p, Utility utility) {
        if (deathmatch) return false;
        int n = p.utility.getOrDefault(utility, 0);
        if (!live(p.id) || phase != Phase.LIVE || n == 0) return false;
        if (n == 1) p.utility.remove(utility);
        else p.utility.put(utility, n - 1);
        return true;
    }

    boolean plant(UUID player, int site, long now) {
        Actor p = actors.get(player);
        if (deathmatch
                || site < 0
                || site > 1
                || phase != Phase.LIVE
                || !live(player)
                || side(p) != 0
                || !Objects.equals(carrier, player)
                || planted()
                || now >= until) return false;
        carrier = null;
        planter = player;
        bombSite = site;
        detonateAt = now + bombTicks;
        dropped = false;
        reward(p, 300);
        return true;
    }

    boolean defuse(UUID player, long now) {
        Actor p = actors.get(player);
        if (phase != Phase.LIVE || !planted() || now >= detonateAt || !live(player) || side(p) != 1)
            return false;
        defuser = player;
        reward(p, 300);
        p.mvps++;
        endRound(p.team, Reason.DEFUSE, now);
        return true;
    }

    boolean dropBomb(UUID id) {
        if (!Objects.equals(id, carrier) || planted()) return false;
        carrier = null;
        dropped = true;
        return true;
    }

    boolean takeBomb(UUID id) {
        Actor p = actors.get(id);
        if (!dropped || planted() || carrier != null || !live(id) || side(p) != 0) return false;
        carrier = id;
        dropped = false;
        return true;
    }

    Damage hurt(
            UUID victim,
            UUID attacker,
            double raw,
            boolean head,
            boolean leg,
            CsWeapon weapon,
            boolean grenade) {
        return hurt(
                victim,
                attacker,
                raw,
                head,
                leg,
                weapon,
                grenade
                        ? KillCause.HE
                        : weapon != null
                                ? KillCause.BULLET
                                : attacker != null ? KillCause.KNIFE : KillCause.OTHER);
    }

    Damage hurt(
            UUID victim,
            UUID attacker,
            double raw,
            boolean head,
            boolean leg,
            CsWeapon weapon,
            KillCause cause) {
        boolean grenade = cause.grenade();
        Actor p = actors.get(victim), source = actors.get(attacker);
        if (phase != Phase.LIVE || !live(victim) || raw <= 0) return new Damage(0, false, head);
        boolean friendly =
                !deathmatch && source != null && source.team == p.team && !source.id.equals(p.id);
        if (friendly) raw *= grenade ? .85 : weapon == null ? .4 : .33;
        if (!leg && p.armour > 0 && (!head || p.helmet)) {
            double penetration = weapon == null ? (grenade ? .5 : .85) : weapon.penetration;
            double absorbed = raw * (1 - penetration);
            if (absorbed * .5 > p.armour) absorbed = p.armour * 2;
            p.armour = Math.max(0, p.armour - absorbed * .5);
            raw -= absorbed;
        }
        double damage = Math.min(p.health, raw);
        p.health = Math.max(0, p.health - damage);
        if (source != null && !source.id.equals(p.id) && (deathmatch || source.team != p.team))
            contributions
                    .computeIfAbsent(victim, k -> new HashMap<>())
                    .merge(attacker, damage, Double::sum);
        if (p.health <= 0)
            die(p, source, new KillInfo(weapon, weapon != null && !grenade && head, cause));
        return new Damage(damage, p.health <= 0, head);
    }

    private void die(Actor p, Actor source, KillInfo info) {
        if (!p.alive) return;
        p.alive = false;
        p.deaths++;
        lastKiller = source == null ? null : source.id;
        p.killedBy = lastKiller;
        p.killInfo = info;
        if (source != null && !source.id.equals(p.id)) {
            if (!deathmatch && source.team == p.team) {
                source.kills--;
                credit(source, -300);
            } else {
                source.kills++;
                source.roundKills++;
                if (!deathmatch)
                    reward(
                            source,
                            info.cause().grenade()
                                    ? 300
                                    : info.weapon() == null ? 1500 : info.weapon().killReward());
                contributions
                        .getOrDefault(p.id, Map.of())
                        .forEach((id, damage) -> {
                            Actor other = actors.get(id);
                            if (!id.equals(source.id) && damage >= 40) other.assists++;
                        });
            }
        } else if (source != null) credit(source, -300);
        contributions.remove(p.id);
    }

    void suicide(UUID id) {
        Actor p = actors.get(id);
        if (live(id)) {
            p.health = 0;
            die(p, p, new KillInfo(null, false, KillCause.OTHER));
        }
    }

    void disconnect(UUID id) {
        Actor p = actors.get(id);
        if (p == null) return;
        p.online = false;
        if (p.alive) {
            p.health = 0;
            die(p, null, new KillInfo(null, false, KillCause.OTHER));
        }
    }

    void reconnect(UUID id) {
        Actor p = actors.get(id);
        if (p != null) p.online = true;
    }

    private void endRound(int winner, Reason reason, long now) {
        if (phase != Phase.LIVE) return;
        score[winner]++;
        phase = Phase.ROUND_END;
        until = now + 100;
        lastRound = new Round(number, winner, reason);
        version++;
        for (Actor p : actors.values()) {
            if (!p.online) continue;
            if (p.team == winner)
                reward(p, reason == Reason.EXPLOSION || reason == Reason.DEFUSE ? 3500 : 3250);
            else if (!(reason == Reason.TIMEOUT && side(p) == 0 && p.alive))
                reward(p, 1400 + 500 * Math.min(4, losses[p.team]));
            if (p.team != winner && planted() && side(p) == 0) reward(p, 600);
        }
        losses[winner] = Math.max(0, losses[winner] - 1);
        losses[1 - winner] = Math.min(4, losses[1 - winner] + 1);
        if (reason == Reason.EXPLOSION && planter != null) actors.get(planter).mvps++;
        else if (reason != Reason.DEFUSE)
            actors.values().stream()
                    .filter(p -> p.team == winner)
                    .max(Comparator.comparingInt(p -> p.roundKills))
                    .filter(p -> p.roundKills > 0)
                    .ifPresent(p -> p.mvps++);
    }

    void forfeit(int winner) {
        if (phase == Phase.FINISHED) return;
        score[winner] = Math.max(halfRounds + 1, score[winner]);
        phase = Phase.FINISHED;
        ended = true;
        version++;
    }

    static void credit(Actor p, int amount) {
        p.money = Math.clamp((long) p.money + amount, 0, 16000);
    }

    double incomeScale(int team) {
        int largest = Math.max(economySizes[0], economySizes[1]);
        if (practice || largest == 0 || economySizes[team] == 0) return 1;
        // Keep the full 5v5 payout per person; compensate only a numeric disadvantage.
        return (double) largest / economySizes[team];
    }

    private int startingCash(int team) {
        int largest = Math.max(economySizes[0], economySizes[1]);
        return economySizes[team] == 0
                ? 800
                : (int) Math.min(16000, Math.round(800.0 * largest / economySizes[team]));
    }

    private void reward(Actor p, int base) {
        credit(p, (int) Math.round(base * incomeScale(p.team)));
    }

    boolean pause(int team) {
        if (deathmatch) return false;
        if (phase == Phase.FINISHED || timeouts[team] == 0 || pendingPause[team]) return false;
        timeouts[team]--;
        if (phase == Phase.FREEZE) until += 600;
        else pendingPause[team] = true;
        return true;
    }
}
