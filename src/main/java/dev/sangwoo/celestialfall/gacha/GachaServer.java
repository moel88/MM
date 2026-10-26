package dev.sangwoo.celestialfall.gacha;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.LongCooldowns;
import dev.sangwoo.celestialfall.commerce.CommerceRegistry;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.progression.ProgressManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.security.SecureRandom;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/**
 * The stand gacha on the server (user request, 2.39.0). A client asks for its count (0 pulls) or for 1 or 10 pulls; the
 * server checks the player may pull (on the dedicated map, out of combat and cutscenes, no stand equipped, room for the
 * Rokakaka that could come), rolls, keeps the new count, equips Kageno on an SSR (its long cooldown still running begins
 * again in full, 2.74.1: LongCooldowns), gives a Rokakaka for every R (2.41.0) and answers with what was rolled. Pulls
 * are free for now.
 */
public final class GachaServer {
    public static final Identifier REQUEST=CelestialFall.id("gacha_request_v1"),RESULT=CelestialFall.id("gacha_result_v1");
    private static final Map<UUID,long[]> LAST=new HashMap<>();
    /** Rolls nobody can predict from the world's own randomness. */
    private static final SecureRandom RANDOM=new SecureRandom();
    private GachaServer(){}
    public record Outcome(List<GachaRules.Grade> grades,GachaRules.Count count,boolean granted,String message){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(REQUEST,(s,p,h,b,r)->{
            if(b.readableBytes()!=5)return;int serial=b.readInt(),n=b.readUnsignedByte();
            if(n!=0&&n!=GachaRules.SINGLE&&n!=GachaRules.TEN)return;
            s.execute(()->request(p,serial,n));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->LAST.remove(h.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->LAST.clear());
    }

    /** Makes {@code n} pulls for {@code p} (0: only reads the count); the same checks serve packets and live-server tests. */
    public static Outcome perform(ServerPlayerEntity p,int n){
        if(!MapGate.enabled(p.getWorld()))return new Outcome(List.of(),GachaRules.Count.NEW,false,"전용 맵에서만 뽑을 수 있어요.");
        var state=GachaState.get(p.getServer());var count=state.get(p.getUuid());
        if(n==0)return new Outcome(List.of(),count,false,"");
        if(!ProgressManager.canChange(p))return new Outcome(List.of(),count,false,"전투나 컷씬이 끝난 뒤 뽑을 수 있어요.");
        var profile=ProgressManager.profile(p);
        if(!GachaRules.canPull(profile.stand()))return new Outcome(List.of(),count,false,"장착한 스탠드가 없어야 뽑을 수 있어요.");
        // every R is a Rokakaka: room for as many as could come, checked before rolling (rewards never fall to the ground)
        if(fruitRoom(p)<n)return new Outcome(List.of(),count,false,"로카카카를 받을 인벤토리 자리가 없어요.");
        var result=GachaRules.pulls(count,n,()->RANDOM.nextInt(GachaRules.ODDS));
        state.set(p.getUuid(),result.after());
        boolean granted=result.any(GachaRules.Grade.SSR);
        if(granted){
            ProgressManager.set(p,profile.withStand(GachaRules.SSR_STAND));
            // (2.74.1, user request: "뽑기로 얻어도 처음부터 다시 흐르게") its long cooldown still running begins again in
            // full, as the stand arrow's does (only the new stand's: another stand's runs on)
            LongCooldowns.begin(p);
        }
        int fruits=GachaRules.fruits(result.grades());
        if(fruits>0)p.getInventory().offerOrDrop(new ItemStack(CommerceRegistry.ROKAKAKA,fruits));
        CelestialFall.LOGGER.info("Gacha: {} ({}) pulled {}: {}; SSR certain in {}, SR in {}, {} pulls in all{}{}",p.getName().getString(),p.getUuid(),n,
            result.grades(),result.after().toSsr(),result.after().toSr(),result.after().pulls(),granted?"; Kageno equipped":"",fruits>0?"; "+fruits+" Rokakaka":"");
        String message=granted?"SSR! 카게노를 얻었어요.":"";
        if(fruits>0)message+=(message.isEmpty()?"":" ")+"로카카카 "+fruits+"개를 받았어요.";
        return new Outcome(result.grades(),result.after(),granted,message);
    }
    /** How many more Rokakaka the player's main inventory holds. */
    private static int fruitRoom(ServerPlayerEntity p){
        var inv=p.getInventory();var fruit=new ItemStack(CommerceRegistry.ROKAKAKA);int[] held=new int[36];
        for(int i=0;i<36;i++){var s=inv.getStack(i);held[i]=s.isEmpty()?0:ItemStack.canCombine(s,fruit)?s.getCount():-1;}
        return GachaRules.room(held,fruit.getMaxCount());
    }

    private static void request(ServerPlayerEntity p,int serial,int n){
        long now=p.getServer().getTicks();var last=LAST.computeIfAbsent(p.getUuid(),k->GachaRules.never());
        Outcome out;
        if(GachaRules.tooSoon(now,last,n))out=new Outcome(List.of(),GachaState.get(p.getServer()).get(p.getUuid()),false,"잠시 후 다시 눌러 주세요.");
        else{out=perform(p,n);if(n>0)last[1]=now;}
        last[0]=now;
        if(!ServerPlayNetworking.canSend(p,RESULT))return;
        var b=PacketByteBufs.create();b.writeInt(serial);
        b.writeVarInt(out.count().sinceSsr());b.writeVarInt(out.count().sinceSr());b.writeVarInt(out.count().pulls());
        b.writeByte(out.grades().size());for(var g:out.grades())b.writeByte(g.ordinal());
        b.writeBoolean(out.granted());b.writeString(out.message(),160);
        ServerPlayNetworking.send(p,RESULT,b);
    }

    /** {@code /botand gacha}: an operator reads or sets a player's count (to check the guarantees, or to film them). */
    public static void add(LiteralArgumentBuilder<ServerCommandSource> root){
        var gacha=literal("gacha").requires(s->s.hasPermissionLevel(2));
        gacha.then(literal("info").then(argument("target",EntityArgumentType.player()).executes(c->{
            var p=EntityArgumentType.getPlayer(c,"target");var count=GachaState.get(p.getServer()).get(p.getUuid());
            c.getSource().sendFeedback(()->Text.translatable("message.botand.gacha_info",p.getName(),count.toSsr(),count.toSr(),count.pulls()),false);return 1;
        })));
        gacha.then(literal("set").then(argument("targets",EntityArgumentType.players())
            .then(argument("sinceSsr",IntegerArgumentType.integer(0,GachaRules.SSR_PITY-1)).then(argument("sinceSr",IntegerArgumentType.integer(0,GachaRules.SR_PITY-1)).executes(c->{
                var players=EntityArgumentType.getPlayers(c,"targets");int ssr=IntegerArgumentType.getInteger(c,"sinceSsr"),sr=IntegerArgumentType.getInteger(c,"sinceSr");
                for(var p:players){var s=GachaState.get(p.getServer());s.set(p.getUuid(),new GachaRules.Count(ssr,sr,s.get(p.getUuid()).pulls()));}
                c.getSource().sendFeedback(()->Text.translatable("message.botand.gacha_set",players.size(),GachaRules.SSR_PITY-ssr,GachaRules.SR_PITY-sr),true);return players.size();
            })))));
        root.then(gacha);
    }
}
