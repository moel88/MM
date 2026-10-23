package dev.sangwoo.celestialfall.commerce;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.progression.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.Entity;
import dev.sangwoo.celestialfall.meteor.MeteorRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
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
    public static boolean near(ServerPlayerEntity p,Entity entity){return entity instanceof MerchantEntity m&&m.isAlive()&&m.getUuid().equals(CommerceRegistry.id(m.role))&&entity.getWorld()==p.getWorld()&&p.squaredDistanceTo(entity)<=TradeRules.REACH*TradeRules.REACH&&p.canSee(entity)&&eligible(p);}
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
            var inv=p.getInventory();var before=ProgressManager.profile(p);
            if(npc.role==MerchantEntity.CRAFTER){craft(p,npc,before);return;}
            // the seller's product is the request's target (0 the Rokakaka, 1 the meteor pickaxe; 2.49.0)
            int product=npc.seller&&target==TradeRules.PICKAXE?TradeRules.PICKAXE:TradeRules.FRUIT;
            var goods=product==TradeRules.PICKAXE?new ItemStack(MeteorRegistry.PICKAXE):new ItemStack(CommerceRegistry.ROKAKAKA);
            int sellSlot=findFruit(p);int buySlot=findRoom(p,goods);
            var result=npc.seller?TradeRules.buy(before,product,buySlot>=0):TradeRules.trade(before,false,sellSlot>=0,buySlot>=0);
            if(!result.ok()){open(p,npc,result.error());return;}
            if(npc.seller){var at=inv.getStack(buySlot);if(at.isEmpty())inv.setStack(buySlot,goods);else at.increment(1);}
            else {var at=inv.getStack(sellSlot);at.decrement(1);}
            inv.markDirty();p.currentScreenHandler.sendContentUpdates();
            ProgressManager.set(p,result.profile());
            int gained=npc.seller?(product==TradeRules.PICKAXE?TradeRules.PICKAXE_XP:TradeRules.BUY_XP):TradeRules.SELL_XP;
            String level=result.profile().level()>before.level()?"  ·  레벨 UP! "+result.profile().level():"";
            String done=npc.seller?(product==TradeRules.PICKAXE?"운석 곡괭이 구매 완료! -":"구매 완료! -")+TradeRules.won(TradeRules.price(product)):"판매 완료! +"+TradeRules.won(TradeRules.SELL_PRICE);
            open(p,npc,done+(before.level()==50?"  ·  최고 레벨":"  ·  경험치 +"+gained)+level);return;
        }
        if(action==CONFIRM&&s.npc==null){
            SESSIONS.remove(id);var stack=p.getStackInHand(s.hand);var profile=ProgressManager.profile(p);
            if(!stack.isOf(CommerceRegistry.ROKAKAKA)||!ItemStack.canCombine(stack,s.stack)||(s.hand==Hand.MAIN_HAND&&p.getInventory().selectedSlot!=s.slot)||!profile.stand().equals(s.stand)||profile.stand().equals("none")){
                reply(p,3,0,-1,0,"아이템이나 스탠드가 바뀌었습니다. 다시 사용해 주세요.");return;
            }
            stack.decrement(1);p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();
            ProgressManager.set(p,profile.withStand("none"));
            reply(p,3,0,-1,0,"스탠드를 초기화했습니다. 로카카카 1개를 사용했어요.");
        }
    }
    public static int fruitCount(ServerPlayerEntity p){int n=0;for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isOf(CommerceRegistry.ROKAKAKA))n+=p.getInventory().getStack(i).getCount();if(p.getOffHandStack().isOf(CommerceRegistry.ROKAKAKA))n+=p.getOffHandStack().getCount();return n;}
    private static int findFruit(ServerPlayerEntity p){for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isOf(CommerceRegistry.ROKAKAKA))return i;return p.getOffHandStack().isOf(CommerceRegistry.ROKAKAKA)?40:-1;}
    private static int findRoom(ServerPlayerEntity p,ItemStack goods){
        for(int i=0;i<36;i++){var s=p.getInventory().getStack(i);if(!s.isEmpty()&&ItemStack.canCombine(s,goods)&&s.getCount()<s.getMaxCount())return i;}
        for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isEmpty())return i;
        return -1;
    }
    /** How many of an item the player carries (the main inventory and the off hand). */
    public static int count(ServerPlayerEntity p,Item item){
        int n=0;for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isOf(item))n+=p.getInventory().getStack(i).getCount();
        if(p.getOffHandStack().isOf(item))n+=p.getOffHandStack().getCount();return n;
    }
    /**
     * A stand arrow at the crafter (2.49.0): three meteor fragments and a stick out of the inventory, the arrow into it.
     * Planned on a copy first (the spent stacks may free the arrow's slot), so either all of it happens or none.
     */
    private static void craft(ServerPlayerEntity p,MerchantEntity npc,ProgressProfile before){
        var inv=p.getInventory();var plan=new ArrayList<ItemStack>();for(int i=0;i<inv.size();i++)plan.add(inv.getStack(i).copy());
        int fragments=count(p,MeteorRegistry.FRAGMENT),sticks=count(p,Items.STICK);
        boolean enough=dev.sangwoo.celestialfall.meteor.MeteorRules.canCraft(fragments,sticks);
        if(enough){take(plan,MeteorRegistry.FRAGMENT,dev.sangwoo.celestialfall.meteor.MeteorRules.ARROW_FRAGMENTS);take(plan,Items.STICK,dev.sangwoo.celestialfall.meteor.MeteorRules.ARROW_STICKS);}
        boolean room=enough&&put(plan,new ItemStack(MeteorRegistry.STAND_ARROW));
        var result=TradeRules.craft(before,fragments,sticks,room);
        if(!result.ok()){open(p,npc,result.error());return;}
        for(int i=0;i<plan.size();i++)inv.setStack(i,plan.get(i));
        inv.markDirty();p.currentScreenHandler.sendContentUpdates();
        ProgressManager.set(p,result.profile());
        p.getServerWorld().playSound(null,npc.getX(),npc.getY()+1,npc.getZ(),MeteorRegistry.CRAFT_SOUND,SoundCategory.NEUTRAL,1f,1f);
        String level=result.profile().level()>before.level()?"  ·  레벨 UP! "+result.profile().level():"";
        open(p,npc,"스탠드 화살 제작 완료!"+(before.level()==50?"  ·  최고 레벨":"  ·  경험치 +"+TradeRules.CRAFT_XP)+level);
    }
    private static void take(List<ItemStack> plan,Item item,int amount){
        for(int i:slotsInOrder()){var s=plan.get(i);if(amount<=0)return;if(!s.isOf(item))continue;int n=Math.min(amount,s.getCount());s.decrement(n);amount-=n;}
    }
    private static boolean put(List<ItemStack> plan,ItemStack goods){
        for(int i=0;i<36;i++){var s=plan.get(i);if(!s.isEmpty()&&ItemStack.canCombine(s,goods)&&s.getCount()<s.getMaxCount()){s.increment(1);return true;}}
        for(int i=0;i<36;i++)if(plan.get(i).isEmpty()){plan.set(i,goods);return true;}
        return false;
    }
    /** The main inventory, then the off hand. */
    private static int[] slotsInOrder(){int[] out=new int[37];for(int i=0;i<36;i++)out[i]=i;out[36]=40;return out;}
    public static void askReset(ServerPlayerEntity p,Hand hand){
        if(!eligible(p)||!p.getStackInHand(hand).isOf(CommerceRegistry.ROKAKAKA))return;
        var profile=ProgressManager.profile(p);
        if(profile.stand().equals("none")){p.sendMessage(Text.literal("초기화할 스탠드가 없습니다. 로카카카는 소모되지 않았어요."),true);return;}
        HOLDS.remove(p.getUuid());long token=token();
        SESSIONS.put(p.getUuid(),new Session(token,null,p.getId(),p.getWorld().getRegistryKey(),now(p)+300,hand,p.getInventory().selectedSlot,p.getStackInHand(hand).copy(),profile.stand()));
        reply(p,1,token,-1,0,"");
    }
    private static boolean valid(ServerPlayerEntity p,Session s){
        if(!eligible(p)||s.expiry<now(p)||s.playerId!=p.getId()||!s.dimension.equals(p.getWorld().getRegistryKey()))return false;
        return s.npc==null||near(p,p.getServerWorld().getEntity(s.npc));
    }
    private static void open(ServerPlayerEntity p,MerchantEntity npc,String notice){
        long token=token();SESSIONS.put(p.getUuid(),new Session(token,npc.getUuid(),p.getId(),p.getWorld().getRegistryKey(),now(p)+1200,null,0,ItemStack.EMPTY,""));
        ProgressManager.sync(p);reply(p,0,token,npc.getId(),npc.role,notice);
    }
    private static void close(ServerPlayerEntity p,String notice){SESSIONS.remove(p.getUuid());HOLDS.remove(p.getUuid());reply(p,3,0,-1,0,notice);}
    private static long token(){long n;do{n=RANDOM.nextLong();}while(n==0);return n;}
    /** The client's window: its kind, token, merchant, role, what the player carries for it (fruit, fragments, sticks) and a notice. */
    private static void reply(ServerPlayerEntity p,int kind,long nonce,int target,int role,String notice){
        if(!ServerPlayNetworking.canSend(p,STATE))return;
        var b=PacketByteBufs.create();b.writeByte(kind);b.writeLong(nonce);b.writeInt(target);b.writeByte(role);
        b.writeVarInt(fruitCount(p));b.writeVarInt(count(p,MeteorRegistry.FRAGMENT));b.writeVarInt(count(p,Items.STICK));
        b.writeString(notice,256);ServerPlayNetworking.send(p,STATE,b);
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
