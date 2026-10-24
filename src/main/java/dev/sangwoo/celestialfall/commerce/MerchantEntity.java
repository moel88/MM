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
    /** The three: the item seller, the item buyer and (2.49.0) the crafter who makes stand arrows. */
    public static final int SELLER=0,BUYER=1,CRAFTER=2;
    private static final String[] NAMES={"아이템 판매 · 로카","아이템 매입 · 카카","제작소 · 장인"};
    public final int role;
    public final boolean seller;
    public MerchantEntity(EntityType<? extends VillagerEntity> type,World world,int role){
        super(type,world);this.role=role;this.seller=role==SELLER;
        setAiDisabled(true);setPersistent();setInvulnerable(true);setNoGravity(true);
        setCustomName(Text.literal(NAMES[role]));setCustomNameVisible(true);
        var profession=role==SELLER?VillagerProfession.CLERIC:role==BUYER?VillagerProfession.LIBRARIAN:VillagerProfession.TOOLSMITH;
        setVillagerData(getVillagerData().withType(VillagerType.PLAINS).withProfession(profession).withLevel(5));
    }
    /**
     * Where a merchant stands: on a described map where its definition says (a definition without a crafter puts it on
     * the seller's far side from the buyer, as far from the seller as the buyer stands), else its place in the delivered
     * city (the crafter in the shop, right of the seller across the counter).
     */
    public static Vec3d homeOf(net.minecraft.server.MinecraftServer server,int role){
        var defined=server==null?java.util.Optional.<dev.sangwoo.celestialfall.map.MapDefinition>empty():dev.sangwoo.celestialfall.map.MapGate.definition(server);
        if(defined.isPresent()){
            var d=defined.get();
            if(role==SELLER)return d.seller();
            if(role==BUYER)return d.buyer();
            if(d.crafter()!=null)return d.crafter();
            return new Vec3d(2*d.seller().x-d.buyer().x,d.seller().y,2*d.seller().z-d.buyer().z);
        }
        return role==SELLER?new Vec3d(-6.5,81.0625,-118.5):role==BUYER?new Vec3d(55.5,81,-124.5):new Vec3d(-3.5,81.0625,-118.5);
    }
    public static float yawOf(net.minecraft.server.MinecraftServer server,int role){
        return server==null?0:dev.sangwoo.celestialfall.map.MapGate.definition(server).map(d->role==SELLER?d.sellerYaw():role==BUYER?d.buyerYaw():d.crafter()!=null?d.crafterYaw():d.sellerYaw()).orElse(0f);
    }
    public Vec3d home(){return homeOf(getWorld().getServer(),role);}
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
