package dev.sangwoo.celestialfall.commerce;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.*;
import net.minecraft.world.World;

/** Stationary, persistent townspeople. No vanilla villager trading, wandering or breeding AI. */
public final class MerchantEntity extends VillagerEntity {
    public final boolean seller;
    public MerchantEntity(EntityType<? extends VillagerEntity> type,World world,boolean seller){
        super(type,world);this.seller=seller;
        setAiDisabled(true);setPersistent();setInvulnerable(true);setNoGravity(true);
        setCustomName(Text.literal(seller?"아이템 판매 · 로카":"아이템 매입 · 카카"));setCustomNameVisible(true);
        setVillagerData(getVillagerData().withType(VillagerType.PLAINS).withProfession(seller?VillagerProfession.CLERIC:VillagerProfession.LIBRARIAN).withLevel(5));
    }
    /** Where a merchant stands: on a described map where its definition says, else its place in the delivered city. */
    public static Vec3d homeOf(net.minecraft.server.MinecraftServer server,boolean seller){
        var defined=server==null?java.util.Optional.<dev.sangwoo.celestialfall.map.MapDefinition>empty():dev.sangwoo.celestialfall.map.MapGate.definition(server);
        if(defined.isPresent())return seller?defined.get().seller():defined.get().buyer();
        return seller?new Vec3d(-6.5,81.0625,-118.5):new Vec3d(55.5,81,-124.5);
    }
    public static float yawOf(net.minecraft.server.MinecraftServer server,boolean seller){
        return server==null?0:dev.sangwoo.celestialfall.map.MapGate.definition(server).map(d->seller?d.sellerYaw():d.buyerYaw()).orElse(0f);
    }
    public Vec3d home(){return homeOf(getWorld().getServer(),seller);}
    @Override public void tick(){
        super.tick();if(!getWorld().isClient){setVelocity(Vec3d.ZERO);setPosition(home());
            var p=getWorld().getClosestPlayer(this,5);if(p!=null)getLookControl().lookAt(p,25,25);
        }
    }
    @Override public ActionResult interactMob(PlayerEntity p,Hand hand){if(!getWorld().isClient)p.sendMessage(Text.literal("가까이서 E키를 1초간 누르면 대화할 수 있어요."),true);return ActionResult.SUCCESS;}
    @Override public boolean damage(DamageSource source,float amount){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public void pushAwayFrom(Entity entity){}
    @Override public void takeKnockback(double strength,double x,double z){}
    @Override public boolean canUsePortals(){return false;}
}
