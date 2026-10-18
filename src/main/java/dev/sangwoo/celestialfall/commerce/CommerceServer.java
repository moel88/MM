package dev.sangwoo.celestialfall.commerce;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.progression.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.text.Text;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import java.util.*;
import java.security.SecureRandom;

/** All requests run on the server thread. Nonces are single-use for every mutation. */
public final class CommerceServer {
    public static final Identifier REQUEST=CelestialFall.id("commerce_request_v1"),STATE=CelestialFall.id("commerce_state_v1");
    public static final int START=0,PULSE=1,CANCEL=2,TRADE=3,ASK=4,CONFIRM=5,CLOSE=6,PING=7;
    private record Hold(UUID npc,int playerId,RegistryKey<World> dimension,long start,long pulse){}
    private record Session(long token,UUID npc,int playerId,RegistryKey<World> dimension,long expiry,Hand hand,int slot,ItemStack stack,String stand){}
    private static final Map<UUID,Hold> HOLDS=new HashMap<>();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Long> LAST=new HashMap<>();
    private static final SecureRandom RANDOM=new SecureRandom();
    private static long now(ServerPlayerEntity p){return p.getServer().getTicks();}
    public static boolean eligible(ServerPlayerEntity p){return ProgressManager.canChange(p)&&!p.hasVehicle()&&!p.isSleeping()&&p.currentScreenHandler==p.playerScreenHandler;}
    public static boolean near(ServerPlayerEntity p,Entity entity){return entity instanceof MerchantEntity m&&m.isAlive()&&m.getUuid().equals(CommerceRegistry.id(m.seller))&&entity.getWorld()==p.getWorld()&&p.squaredDistanceTo(entity)<=TradeRules.REACH*TradeRules.REACH&&p.canSee(entity)&&eligible(p);}
    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(REQUEST,(server,p,handler,b,response)->{
            if(b.readableBytes()!=13)return;
            int action=b.readUnsignedByte(),target=b.readInt();long nonce=b.readLong();
            if(action>7)return;
            server.execute(()->handle(p,action,target,nonce));
        });
        ServerTickEvents.END_SERVER_TICK.register(CommerceServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{HOLDS.remove(h.player.getUuid());SESSIONS.remove(h.player.getUuid());LAST.remove(h.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{HOLDS.clear();SESSIONS.clear();LAST.clear();});
    }
    public static void handle(ServerPlayerEntity p,int action,int target,long nonce){
        UUID id=p.getUuid();long tick=now(p);
        if(action==CANCEL){HOLDS.remove(id);return;}
        if(action==CLOSE){var s=SESSIONS.get(id);if(s!=null&&s.token==nonce)SESSIONS.remove(id);HOLDS.remove(id);return;}
        if(action==START||action==ASK){
            if(tick-LAST.getOrDefault(id,-100L)<2)return;LAST.put(id,tick);
        }
        if(action==START){
            if(SESSIONS.containsKey(id))return;
            var e=p.getWorld().getEntityById(target);if(!near(p,e))return;
            HOLDS.put(id,new Hold(e.getUuid(),p.getId(),p.getWorld().getRegistryKey(),tick,tick));return;
        }
        if(action==PULSE){
            var h=HOLDS.get(id);var e=p.getWorld().getEntityById(target);
            if(h!=null&&e!=null&&e.getUuid().equals(h.npc)&&near(p,e)&&h.playerId==p.getId()&&h.dimension.equals(p.getWorld().getRegistryKey()))HOLDS.put(id,new Hold(h.npc,h.playerId,h.dimension,h.start,tick));
            else HOLDS.remove(id);return;
        }
        if(action==ASK){askReset(p,target==1?Hand.OFF_HAND:Hand.MAIN_HAND);return;}
        var s=SESSIONS.get(id);if(s==null||s.token!=nonce)return;
        if(!valid(p,s)){close(p,"대화가 종료되었습니다. 가까이서 다시 말을 걸어 주세요.");return;}
        if(action==PING){SESSIONS.put(id,new Session(s.token,s.npc,s.playerId,s.dimension,tick+1200,s.hand,s.slot,s.stack,s.stand));return;}
        if(action==TRADE&&s.npc!=null){
            // Consume the token before changing any inventory or profile data.
            SESSIONS.remove(id);var npc=(MerchantEntity)p.getServerWorld().getEntity(s.npc);
            var inv=p.getInventory();int sellSlot=findFruit(p);int buySlot=findRoom(p);
            var before=ProgressManager.profile(p);
            var result=TradeRules.trade(before,npc.seller,sellSlot>=0,buySlot>=0);
            if(!result.ok()){open(p,npc,result.error());return;}
            if(npc.seller){var at=inv.getStack(buySlot);if(at.isEmpty())inv.setStack(buySlot,new ItemStack(CommerceRegistry.ROKAKAKA));else at.increment(1);}
            else {var at=inv.getStack(sellSlot);at.decrement(1);}
            inv.markDirty();p.currentScreenHandler.sendContentUpdates();
            ProgressManager.set(p,result.profile());
            int gained=npc.seller?TradeRules.BUY_XP:TradeRules.SELL_XP;
            String level=result.profile().level()>before.level()?"  ·  레벨 UP! "+result.profile().level():"";
            open(p,npc,(npc.seller?"구매 완료! -"+TradeRules.BUY_PRICE+"원":"판매 완료! +"+TradeRules.SELL_PRICE+"원")+(before.level()==50?"  ·  최고 레벨":"  ·  경험치 +"+gained)+level);return;
        }
        if(action==CONFIRM&&s.npc==null){
            SESSIONS.remove(id);var stack=p.getStackInHand(s.hand);var profile=ProgressManager.profile(p);
            if(!stack.isOf(CommerceRegistry.ROKAKAKA)||!ItemStack.canCombine(stack,s.stack)||(s.hand==Hand.MAIN_HAND&&p.getInventory().selectedSlot!=s.slot)||!profile.stand().equals(s.stand)||profile.stand().equals("none")){
                reply(p,3,0,-1,false,"아이템이나 스탠드가 바뀌었습니다. 다시 사용해 주세요.");return;
            }
            stack.decrement(1);p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();
            ProgressManager.set(p,profile.withStand("none"));
            reply(p,3,0,-1,false,"스탠드를 초기화했습니다. 로카카카 1개를 사용했어요.");
        }
    }
    public static int fruitCount(ServerPlayerEntity p){int n=0;for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isOf(CommerceRegistry.ROKAKAKA))n+=p.getInventory().getStack(i).getCount();if(p.getOffHandStack().isOf(CommerceRegistry.ROKAKAKA))n+=p.getOffHandStack().getCount();return n;}
    private static int findFruit(ServerPlayerEntity p){for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isOf(CommerceRegistry.ROKAKAKA))return i;return p.getOffHandStack().isOf(CommerceRegistry.ROKAKAKA)?40:-1;}
    private static int findRoom(ServerPlayerEntity p){
        var plain=new ItemStack(CommerceRegistry.ROKAKAKA);
        for(int i=0;i<36;i++){var s=p.getInventory().getStack(i);if(s.isEmpty()||(ItemStack.canCombine(s,plain)&&s.getCount()<s.getMaxCount()))return i;}return -1;
    }
    public static void askReset(ServerPlayerEntity p,Hand hand){
        if(!eligible(p)||!p.getStackInHand(hand).isOf(CommerceRegistry.ROKAKAKA))return;
        var profile=ProgressManager.profile(p);
        if(profile.stand().equals("none")){p.sendMessage(Text.literal("초기화할 스탠드가 없습니다. 로카카카는 소모되지 않았어요."),true);return;}
        HOLDS.remove(p.getUuid());long token=token();
        SESSIONS.put(p.getUuid(),new Session(token,null,p.getId(),p.getWorld().getRegistryKey(),now(p)+300,hand,p.getInventory().selectedSlot,p.getStackInHand(hand).copy(),profile.stand()));
        reply(p,1,token,-1,false,"");
    }
    private static boolean valid(ServerPlayerEntity p,Session s){
        if(!eligible(p)||s.expiry<now(p)||s.playerId!=p.getId()||!s.dimension.equals(p.getWorld().getRegistryKey()))return false;
        return s.npc==null||near(p,p.getServerWorld().getEntity(s.npc));
    }
    private static void open(ServerPlayerEntity p,MerchantEntity npc,String notice){
        long token=token();SESSIONS.put(p.getUuid(),new Session(token,npc.getUuid(),p.getId(),p.getWorld().getRegistryKey(),now(p)+1200,null,0,ItemStack.EMPTY,""));
        ProgressManager.sync(p);reply(p,0,token,npc.getId(),npc.seller,notice);
    }
    private static void close(ServerPlayerEntity p,String notice){SESSIONS.remove(p.getUuid());HOLDS.remove(p.getUuid());reply(p,3,0,-1,false,notice);}
    private static long token(){long n;do{n=RANDOM.nextLong();}while(n==0);return n;}
    private static void reply(ServerPlayerEntity p,int kind,long nonce,int target,boolean seller,String notice){
        if(!ServerPlayNetworking.canSend(p,STATE))return;
        var b=PacketByteBufs.create();b.writeByte(kind);b.writeLong(nonce);b.writeInt(target);b.writeBoolean(seller);b.writeVarInt(fruitCount(p));b.writeString(notice,256);ServerPlayNetworking.send(p,STATE,b);
    }
    private static void tick(MinecraftServer server){
        for(var p:server.getPlayerManager().getPlayerList()){
            var h=HOLDS.get(p.getUuid());if(h!=null){
                var npc=p.getServerWorld().getEntity(h.npc);
                if(h.playerId!=p.getId()||!h.dimension.equals(p.getWorld().getRegistryKey())||now(p)-h.pulse>8||!near(p,npc))HOLDS.remove(p.getUuid());
                else if(now(p)-h.start>=TradeRules.HOLD_TICKS){HOLDS.remove(p.getUuid());open(p,(MerchantEntity)npc,"");}
            }
            var s=SESSIONS.get(p.getUuid());if(s!=null&&!valid(p,s))close(p,"대화가 종료되었습니다.");
        }
    }
    private CommerceServer(){}
}
