package me.petr1furious.potatominigames.games.party;

import java.util.*;
import java.util.function.BiConsumer;
import org.bukkit.World;
import me.petr1furious.potatominigames.core.game.*;
import me.petr1furious.potatominigames.core.player.*;
import me.petr1furious.potatominigames.core.hud.BossBarService;

/** One round owns its roster, bars, world names and a single completion notification. */
final class PartyRoundContext {
    private final SessionServices services;
    private final List<Participant> roster;
    private final PartyRoundOptions options;
    private final BiConsumer<EndReason,PartyRoundResult> completion;
    private final String prefix = "party_" + UUID.randomUUID().toString().replace("-", "") + "_";
    private final Set<String> worlds = new LinkedHashSet<>();
    private final Set<UUID> withdrawn = new HashSet<>();
    private boolean ended;
    private PartyRoundResult result = PartyRoundResult.empty();

    PartyRoundContext(SessionServices services,List<Participant> roster,PartyRoundOptions options,BiConsumer<EndReason,PartyRoundResult> completion) {
        this.roster=List.copyOf(roster); this.options=options; this.completion=completion;
        if (services != null && options.embedded()) {
            ParticipantRegistry participants = new ParticipantRegistry();
            for (Participant p : roster) if(p.player()!=null) participants.add(p.player());
            this.services = new SessionServices(services.plugin(),services.session(),participants,services.playerState(),services.teams(),
                    services.sidebar(),new BossBarService(services.session()::audiencePlayers),services.arena(),services.worlds(),
                    services.spawnFinder(),services.messages(),services.points());
        } else this.services=services;
    }
    static PartyRoundContext standalone(SessionServices services) {
        return new PartyRoundContext(services,List.of(),PartyRoundOptions.standalone(),
                (reason,result)->services.session().endGame(reason));
    }
    SessionServices services() { return services; }
    List<Participant> roster() { return !embedded() && services != null ? List.copyOf(services.participants().all()) : roster; }
    PartyRoundOptions options() { return options; }
    boolean embedded() { return options.embedded(); }
    boolean ended() { return ended; }
    PartyRoundResult result() { return result; }
    String worldId(String mode) { return prefix+mode; }
    World createVoid(String mode) {
        String id=worldId(mode); worlds.add(id); return services.worlds().createVoid(id);
    }
    void withdraw(UUID id) {
        withdrawn.add(id);
        if (embedded() && services != null) services.participants().remove(id);
    }
    Set<UUID> eligible() {
        Set<UUID> ids=new LinkedHashSet<>();
        for (Participant p:roster()) if(!withdrawn.contains(p.id()) && p.player()!=null) ids.add(p.id());
        return ids;
    }
    void finish(EndReason reason,PartyRoundResult result) {
        if(ended)return;
        this.result=embedded() ? result.eligible(eligible()) : result;
        ended=true; completion.accept(reason,this.result);
    }
    void disposeWorlds() {
        for(String id:List.copyOf(worlds)) { services.worlds().destroyVoid(id); worlds.remove(id); }
    }
    void hideBars() { if(services!=null) services.bossBars().hideAll(); }
}
