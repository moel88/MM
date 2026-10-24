package dev.sangwoo.celestialfall.loot;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.commerce.*;
import dev.sangwoo.celestialfall.meteor.MeteorRegistry;
import dev.sangwoo.celestialfall.progression.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import java.util.*;

/** Server-timed holds and one atomic claim per shared field slot. No client-provided rewards. */
public final class LootServer {
    public static final Identifier REQUEST=CelestialFall.id("loot_request_v1"),RESULT=CelestialFall.id("loot_result_v1");
    public static final int START=0,PULSE=1,CANCEL=2;
    private record Hold(UUID entity,int playerId,long cycle,RegistryKey<World> dimension,long start,long pulse){}
    private static final Map<UUID,Hold> HOLDS=new HashMap<>();
    private static final Map<UUID,Long> LAST=new HashMap<>();
    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(REQUEST,(s,p,h,b,r)->{
            if(b.readableBytes()!=5)return;int action=b.readUnsignedByte(),id=b.readInt();if(action>2)return;
            s.execute(()->handle(p,action,id));
        });
        ServerTickEvents.END_SERVER_TICK.register(LootServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{HOLDS.remove(h.player.getUuid());LAST.remove(h.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{HOLDS.clear();LAST.clear();});
    }
    public static boolean near(ServerPlayerEntity p,Entity e){
        if(!(e instanceof FieldLootEntity loot)||!loot.isAlive()||loot.opened()||!LootPositions.valid(loot.slot())||e.getWorld()!=p.getWorld()||!LootWorld.enabled(p.getServerWorld())||!CommerceServer.eligible(p))return false;
        if(!e.getUuid().equals(LootPositions.id(loot.slot()))||p.squaredDistanceTo(e)>LootRules.REACH*LootRules.REACH||!p.canSee(e))return false;
        var slot=LootState.get(p.getServerWorld()).slot(loot.slot());return slot!=null&&slot.available()&&slot.cycle()==loot.cycle();
    }
    public static void handle(ServerPlayerEntity p,int action,int target){
        var id=p.getUuid();long tick=p.getServer().getTicks();
        if(action==CANCEL){HOLDS.remove(id);return;}
        var e=p.getWorld().getEntityById(target);
        if(action==START){
            if(tick-LAST.getOrDefault(id,-100L)<4)return;LAST.put(id,tick);
            if(!near(p,e))return;
            var loot=(FieldLootEntity)e;
            if(loot.treasure()&&keySlot(p)<0){reply(p,false,"상자 키가 1개 필요해요.");return;}
            HOLDS.put(id,new Hold(e.getUuid(),p.getId(),loot.cycle(),p.getWorld().getRegistryKey(),tick,tick));
        }else if(action==PULSE){
            var hold=HOLDS.get(id);
            if(hold!=null&&e!=null&&e.getUuid().equals(hold.entity)&&valid(p,hold))HOLDS.put(id,new Hold(hold.entity,hold.playerId,hold.cycle,hold.dimension,hold.start,tick));
            else HOLDS.remove(id);
        }
    }
    private static boolean valid(ServerPlayerEntity p,Hold h){
        var e=p.getServerWorld().getEntity(h.entity);
        return h.playerId==p.getId()&&h.dimension.equals(p.getWorld().getRegistryKey())&&e instanceof FieldLootEntity loot&&loot.cycle()==h.cycle&&near(p,e);
    }
    private static void tick(MinecraftServer s){
        for(var p:s.getPlayerManager().getPlayerList()){
            var h=HOLDS.get(p.getUuid());if(h==null)continue;
            long now=s.getTicks();
            if(now-h.pulse>8||!valid(p,h)){HOLDS.remove(p.getUuid());continue;}
            if(now-h.start>=LootRules.HOLD_TICKS){HOLDS.remove(p.getUuid());claim(p,(FieldLootEntity)p.getServerWorld().getEntity(h.entity));}
        }
    }
    public static int keySlot(ServerPlayerEntity p){
        for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isOf(LootRegistry.CHEST_KEY))return i;
        return p.getOffHandStack().isOf(LootRegistry.CHEST_KEY)?40:-1;
    }
    /** Dry-run inventory, including the consumed key freeing its slot; never drop rewards on the ground. */
    public static List<ItemStack> inventoryPlan(ServerPlayerEntity p,LootState.Slot slot){
        var inv=p.getInventory();var plan=new ArrayList<ItemStack>();for(int i=0;i<inv.size();i++)plan.add(inv.getStack(i).copy());
        if(slot.treasure()){int key=keySlot(p);if(key<0)return null;plan.get(key).decrement(1);}
        for(int flag:new int[]{LootRules.FRUIT,LootRules.KEY,LootRules.ARROW,LootRules.STICK})if((slot.drops()&flag)!=0){
            var reward=new ItemStack(reward(flag));boolean placed=false;
            for(int i=0;i<36;i++){var at=plan.get(i);if(!at.isEmpty()&&ItemStack.canCombine(at,reward)&&at.getCount()<at.getMaxCount()){at.increment(1);placed=true;break;}}
            if(!placed)for(int i=0;i<36;i++)if(plan.get(i).isEmpty()){plan.set(i,reward);placed=true;break;}
            if(!placed)return null;
        }return plan;
    }
    private static Item reward(int flag){
        return flag==LootRules.FRUIT?CommerceRegistry.ROKAKAKA:flag==LootRules.KEY?LootRegistry.CHEST_KEY:flag==LootRules.ARROW?MeteorRegistry.STAND_ARROW:Items.STICK;
    }
    /** Called only after a server-timed hold. Public for integrated-server acceptance verification. */
    public static boolean claim(ServerPlayerEntity p,FieldLootEntity loot){
        if(!near(p,loot))return false;
        var w=p.getServerWorld();var state=LootState.get(w);var slot=state.slot(loot.slot());
        if(slot.treasure()&&keySlot(p)<0){reply(p,false,"상자 키가 1개 필요해요.");return false;}
        var plan=inventoryPlan(p,slot);if(plan==null){reply(p,false,"인벤토리 공간이 부족해요. 비운 뒤 다시 열어 주세요.");return false;}
        var before=ProgressManager.profile(p);int money=LootRules.money(slot.treasure()),xp=LootRules.xp(slot.treasure());
        if(before.money()>ProgressProfile.MAX_MONEY-money){reply(p,false,"보유 금액 한도에 도달했어요.");return false;}
        int delay=LootRules.respawnTicks(w.random.nextInt(100),w.random.nextInt(60));
        if(!state.claim(loot.slot(),slot.cycle(),w.getTime(),delay))return false;
        for(int i=0;i<plan.size();i++)p.getInventory().setStack(i,plan.get(i));
        p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();
        var after=before.withMoney(before.money()+money).grantXp(xp);ProgressManager.set(p,after);
        loot.configure(loot.slot(),state.slot(loot.slot()));
        w.playSound(null,loot.getBlockPos(),slot.treasure()?SoundEvents.BLOCK_CHEST_OPEN:SoundEvents.BLOCK_BARREL_OPEN,SoundCategory.BLOCKS,.85f,slot.treasure()?.8f:1.2f);
        w.playSound(null,loot.getBlockPos(),SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,SoundCategory.PLAYERS,.65f,1.25f);
        if(slot.treasure())w.spawnParticles(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(1f,.76f,.3f),.55f),loot.getX(),loot.getY()+.75,loot.getZ(),10,.35,.2,.3,.01);
        else w.spawnParticles(ParticleTypes.HAPPY_VILLAGER,loot.getX(),loot.getY()+.8,loot.getZ(),8,.45,.35,.35,.025);
        String items=((slot.drops()&LootRules.FRUIT)!=0?" · 로카카카 ×1":"")+((slot.drops()&LootRules.KEY)!=0?" · 상자 키 ×1":"")
            +((slot.drops()&LootRules.ARROW)!=0?" · 스탠드 화살 ×1":"")+((slot.drops()&LootRules.STICK)!=0?" · 막대기 ×1":"");
        reply(p,true,(slot.treasure()?"보물상자":"박스")+" 획득! +"+money+"원"+(before.level()==50?" · 최고 레벨":" · 경험치 +"+xp)+items+(after.level()>before.level()?" · 레벨 UP! "+after.level():""));
        return true;
    }
    private static void reply(ServerPlayerEntity p,boolean success,String text){
        if(!ServerPlayNetworking.canSend(p,RESULT))return;var b=PacketByteBufs.create();b.writeBoolean(success);b.writeString(text,256);ServerPlayNetworking.send(p,RESULT,b);
    }
    private LootServer(){}
}
