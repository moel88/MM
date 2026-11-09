package dev.sangwoo.celestialfall.map;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Plain Minecraft for one player on the Botand map (2.110.0, user request: "이제 명령어 하나 추가하자 명령어를 입력하면 원래
 * 크리에이티브 모드로 돌아오게 지금은 키배치나 모션이 전부 다르잖아 명령어 치면 키배치나 모션이나 전부 원래 바닐라 크리에이티브
 * 모드로 돌아오게"; answers: only whoever types it, "/botand vanilla", back to the game mode they had before).
 * <p>
 * "/botand vanilla" (operators: it gives creative) turns it on for whoever types it, and again (or "/botand vanilla off")
 * back off. On: they are put in creative, and the map is off for them alone (MapGate.enabled(PlayerEntity)): their client
 * hears the map is off (MapGate.sync), so its keys, camera, HUD, menus and motions are Minecraft's (MapControls gives the
 * keys back), and the server's map rules leave them (no stand, no skill, no bare-hand combo, Minecraft's health and death
 * screen: StandManager.has, CombatManager's attack, ProgressManager.applyHealth, AutoRespawn). Nobody's skills hold or
 * strike them (PvP with them off both ways, BossPvpMixin; Kageno's holds, KagenoManager.near and frozen; the time stop
 * passes them by) and every client draws their body as Minecraft's (VanillaHolder: a bit of the stand's tracked byte;
 * CombatClient.animate). Off: the game mode they had before, and the map's ways again. It is kept with the world
 * (MapState) and holds through a respawn, a world change and leaving and coming back, until it is turned off.
 */
public final class VanillaMode {
    private VanillaMode(){}

    /**
     * Whether {@code p} plays as plain Minecraft now: on the server, as the world keeps it (MapState); on a client, as its
     * server tells (the player's tracked flag, VanillaHolder).
     */
    public static boolean on(PlayerEntity p){
        if(p==null)return false;
        if(!p.getWorld().isClient&&p.getServer()!=null)return MapState.get(p.getServer()).vanilla(p.getUuid())!=null;
        return p instanceof VanillaHolder h&&h.botand$isVanilla();
    }

    public static void initialize(){
        // every client sees whose vanilla mode is on (kept on a new body after a respawn too)
        ServerTickEvents.START_SERVER_TICK.register(server->{for(var p:server.getPlayerManager().getPlayerList())flag(p);});
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->flag(p));
    }
    private static void flag(ServerPlayerEntity p){
        boolean on=on(p);
        if(p instanceof VanillaHolder h&&h.botand$isVanilla()!=on)h.botand$setVanilla(on);
    }

    /** "/botand vanilla" (on if off, off if on), "/botand vanilla on", "/botand vanilla off": operators only (creative). */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> command(){
        return literal("vanilla").requires(s->s.hasPermissionLevel(2))
            .executes(c->set(c.getSource(),null))
            .then(literal("on").executes(c->set(c.getSource(),true)))
            .then(literal("off").executes(c->set(c.getSource(),false)));
    }
    private static int set(ServerCommandSource source,Boolean want)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=source.getPlayerOrThrow();boolean now=on(p),to=want==null?!now:want;
        if(to==now){source.sendFeedback(()->Text.translatable(now?"message.botand.vanilla_already_on":"message.botand.vanilla_already_off"),false);return 0;}
        if(to)turnOn(p);else turnOff(p);
        GameMode mode=p.interactionManager.getGameMode();
        source.sendFeedback(()->to?Text.translatable("message.botand.vanilla_on"):Text.translatable("message.botand.vanilla_off",mode.getTranslatableName()),true);
        return 1;
    }
    /** On for {@code p}: the game mode they have now remembered, creative, and the map off for them alone. */
    public static void turnOn(ServerPlayerEntity p){
        var state=MapState.get(p.getServer());if(state.vanilla(p.getUuid())!=null)return;
        state.vanilla(p.getUuid(),p.interactionManager.getGameMode().getName());
        flag(p);
        p.changeGameMode(GameMode.CREATIVE);
        // its client turns the map's ways off; the server's rules for them end (combat left, Minecraft's health)
        MapGate.refresh(p);
    }
    /** Off for {@code p}: back to the game mode they had before, and the map's ways again. */
    public static void turnOff(ServerPlayerEntity p){
        var state=MapState.get(p.getServer());String before=state.vanilla(p.getUuid());if(before==null)return;
        state.vanilla(p.getUuid(),null);
        flag(p);
        p.changeGameMode(GameMode.byName(before,GameMode.SURVIVAL));
        MapGate.refresh(p);
    }
}
