package dev.sangwoo.celestialfall.client.engine;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.xchara.FlatBody;
import dev.sangwoo.celestialfall.engine.Defense;
import dev.sangwoo.celestialfall.engine.EngineNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * What the combat engine's two defences look like (2.157.0, Defense): every client draws them as the server tells
 * (EngineNet.EVENT), its own player's evasive from the frame it is pressed (EngineClient).
 * <ul>
 * <li>A perfect guard: a sky-blue hexagon of light flashes up before the guard, facing the way its holder faces, white at
 * first and gone in a third of a second; a white star at its heart, a ring opening across it, sparks flung out along it,
 * the light of it on the world; a bright clang (the server's), and to its holder a high ding of their own. One it turns
 * back recoils (HitStun.PARRIED, MotionClips.parried).</li>
 * <li>An evasive: sky-blue silhouettes of the body left along its way (where it left, a third and two thirds of the way
 * to where it is), thinning out in half a second; a ring opening on the ground where it left, streaks along its way and
 * a burst of light there.</li>
 * </ul>
 * No screen effect. Everything is a function of its clock (this client's ticks since it heard).
 */
public final class DefenseFx {
    private DefenseFx(){}
    /** The sky blue of the two, its bright core and its deep edge. */
    static final int SKY=0x7fd8ff,CORE=0xe9fbff,DEEP=0x2a8fe0;
    /** How long a perfect guard's flash and an evasive's trail are drawn (seconds). */
    static final float PERFECT_FOR=.45f,EVADE_FOR=.6f;
    /** How long after its own predicted evasive this client takes no word of it from the server (ticks). */
    static final int OWN=20;

    private record Perfect(Vec3d at,int guard,float yaw,boolean parried,int heard,boolean mine){
        float sec(float delta){return (StandClient.ticks()-heard+delta)/20f;}
    }
    private record Evade(int id,UUID who,Vec3d from,float yaw,float forward,float side,int heard){
        float sec(float delta){return (StandClient.ticks()-heard+delta)/20f;}
        Vec3d way(){double r=Math.toRadians(yaw);return new Vec3d(-Math.sin(r)*forward+Math.cos(r)*side,0,Math.cos(r)*forward+Math.sin(r)*side);}
    }
    private static final List<Perfect> PERFECTS=new ArrayList<>();
    private static final Map<UUID,Evade> EVADES=new HashMap<>();
    private static Object world;
    /** When this client's player last took a blow perfectly (its ticks), for the guard bar's word. */
    private static int lastMine=-1000;

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(EngineNet.EVENT,(mc,handler,b,response)->{
            int kind=b.readUnsignedByte();Identifier dim=b.readIdentifier();
            if(kind==EngineNet.PERFECT){
                int guard=b.readVarInt();UUID who=b.readUuid();int striker=b.readVarInt();boolean parried=b.readBoolean();
                Vec3d at=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());long tick=b.readLong();
                if(!finite(at))return;
                mc.execute(()->{
                    if(!here(mc,dim))return;
                    Entity e=mc.world.getEntityById(guard);float yaw=e!=null?e.getYaw():mc.gameRenderer.getCamera().getYaw();
                    boolean mine=mc.player!=null&&mc.player.getUuid().equals(who);
                    PERFECTS.add(new Perfect(at,guard,yaw,parried,StandClient.ticks(),mine));
                    while(PERFECTS.size()>24)PERFECTS.remove(0);
                    // a high ding of their own for whoever guarded (the clang everyone hears is the server's)
                    if(mine){lastMine=StandClient.ticks();mc.world.playSound(mc.player,mc.player.getX(),mc.player.getY(),mc.player.getZ(),SoundEvents.ENTITY_ARROW_HIT_PLAYER,SoundCategory.PLAYERS,.55f,1.25f);}
                });
            }else if(kind==EngineNet.EVADED){
                int id=b.readVarInt();UUID who=b.readUuid();long start=b.readLong();
                Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat(),forward=b.readFloat(),side=b.readFloat();
                if(!finite(from)||!Float.isFinite(yaw)||!Float.isFinite(forward)||!Float.isFinite(side))return;
                mc.execute(()->{
                    if(!here(mc,dim))return;
                    // (this client's own player's it drew from the press)
                    var mine=EVADES.get(who);
                    if(mc.player!=null&&mc.player.getUuid().equals(who)&&mine!=null&&StandClient.ticks()-mine.heard<OWN)return;
                    EVADES.put(who,new Evade(id,who,from,yaw,forward,side,StandClient.ticks()));
                });
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(DefenseFx::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(DefenseFx::clear));
        WorldRenderEvents.AFTER_ENTITIES.register(DefenseFx::render);
    }
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static boolean here(MinecraftClient mc,Identifier dim){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return false;
        if(world!=mc.world){clear();world=mc.world;}
        return true;
    }
    static void clear(){PERFECTS.clear();EVADES.clear();world=null;lastMine=-1000;}
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        PERFECTS.removeIf(p->p.sec(0)>PERFECT_FOR+.1f);
        EVADES.values().removeIf(e->e.sec(0)>EVADE_FOR+.1f);
    }
    /** This client's own player's evasive, from the frame it is pressed (EngineClient). */
    static void evadedHere(LivingEntity player){
        var shadow=EngineClient.evadeWay();
        EVADES.put(player.getUuid(),new Evade(player.getId(),player.getUuid(),player.getPos(),shadow[0],shadow[1],shadow[2],StandClient.ticks()));
    }
    /** How long ago this client's player took a blow perfectly (seconds; large if never), for the word over the guard bar. */
    public static float perfectAgo(float delta){return (StandClient.ticks()-lastMine+delta)/20f;}

    // ───────────────────────────── how they look ─────────────────────────────

    /** How bright a perfect guard's flash is {@code sec} after it (in at once, gone by PERFECT_FOR). */
    static float flash(float sec){return sec<0?0:Ease.smooth(sec,0,.02f)*(1-Ease.smooth(sec,.08f,PERFECT_FOR));}
    /** How white it still is (white at first, sky blue within a tenth of a second). */
    static float white(float sec){return sec<0?0:1-Ease.smooth(sec,0,.1f);}
    /** How much of an evasive's trail still shows {@code sec} after it. */
    static float trail(float sec){return sec<0?0:1-Ease.smooth(sec,.1f,EVADE_FOR);}
    /** The two axes across a guard facing {@code yaw}: its side and up. */
    private static Vec3d[] across(float yaw){double r=Math.toRadians(yaw);return new Vec3d[]{new Vec3d(Math.cos(r),0,Math.sin(r)),new Vec3d(0,1,0)};}
    private static float near(Vec3d p,Vec3d camera){return Ease.smooth((float)camera.distanceTo(p),.6f,1.6f);}

    private static List<Perfect> perfects=List.of();
    private static List<Evade> evades=List.of();
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        if(PERFECTS.isEmpty()&&EVADES.isEmpty()){perfects=List.of();evades=List.of();return false;}
        perfects=PERFECTS.stream().filter(p->p.at.squaredDistanceTo(camera)<96*96&&p.sec(delta)<PERFECT_FOR).toList();
        evades=EVADES.values().stream().filter(e->e.from.squaredDistanceTo(camera)<96*96&&e.sec(delta)<EVADE_FOR).toList();
        return !perfects.isEmpty()||!evades.isEmpty();
    }
    /** The blended pass: the hexagon of the perfect guard. */
    public static void alpha(EffectMesh m){
        float delta=MinecraftClient.getInstance().getTickDelta();
        for(var p:perfects){
            float f=flash(p.sec(delta)),near=near(p.at,m.camera());if(f<=0||near<=0)continue;
            var ax=across(p.yaw);double r=.78+.12*Ease.smooth(p.sec(delta),0,.12f);
            int fill=lerp(SKY,CORE,white(p.sec(delta)));
            for(int i=0;i<6;i++){
                double a=i*Math.PI/3+Math.PI/6,b=(i+1)*Math.PI/3+Math.PI/6;
                Vec3d pa=p.at.add(ax[0].multiply(Math.cos(a)*r)).add(ax[1].multiply(Math.sin(a)*r)),pb=p.at.add(ax[0].multiply(Math.cos(b)*r)).add(ax[1].multiply(Math.sin(b)*r));
                m.triangle(p.at,pa,pb,fill,.38f*f*near);m.triangle(p.at,pb,pa,fill,.38f*f*near);
                m.line(pa,pb,.06,DEEP,.8f*f*near);
            }
        }
    }
    /** The light, added: the star, the ring, the sparks; the evasive's ring, streaks and burst. */
    public static void additive(EffectMesh m){
        float delta=MinecraftClient.getInstance().getTickDelta();
        for(var p:perfects){
            float sec=p.sec(delta),f=flash(sec),near=near(p.at,m.camera());if(f<=0||near<=0)continue;
            var ax=across(p.yaw);
            m.star(p.at,1.05-.5*Ease.smooth(sec,0,.25f),CORE,.95f*f*near);
            m.softDisc(p.at,1.25,SKY,.45f*f*near);
            double ring=.45+1.5*Ease.out(sec,0,.3f);
            m.ring(p.at,ax[0],ax[1],ring,40,1,.07,SKY,.8f*f*near,0);
            m.ring(p.at,ax[0],ax[1],ring*.72,40,1,.04,CORE,.6f*f*near,.2);
            for(int i=0;i<12;i++){
                double a=i*Math.PI/6+.13*(i%3);
                Vec3d d=ax[0].multiply(Math.cos(a)).add(ax[1].multiply(Math.sin(a)));
                double from=.35+1.1*Ease.smooth(sec,0,.22f),to=from+.32+.25*(i%2);
                m.softLine(p.at.add(d.multiply(from)),p.at.add(d.multiply(to)),.07,i%2==0?CORE:SKY,.8f*f*near);
            }
        }
        for(var e:evades){
            float sec=e.sec(delta),t=trail(sec),near=near(e.from,m.camera());if(t<=0||near<=0)continue;
            Vec3d way=e.way();Vec3d ground=e.from.add(0,.06,0);
            double ring=.35+1.5*Ease.out(sec,0,.35f);
            m.ring(ground,new Vec3d(1,0,0),new Vec3d(0,0,1),ring,36,1,.08,SKY,.7f*t*near,0);
            m.softDisc(e.from.add(0,.9,0),1.1,SKY,.35f*t*(1-Ease.smooth(sec,0,.2f))*near);
            for(int i=0;i<7;i++){
                double h=.25+.22*i,off=(i%2==0?1:-1)*.18*(i%3);
                Vec3d side=new Vec3d(-way.z,0,way.x).multiply(off);
                Vec3d a=e.from.add(0,h,0).add(side),b=a.add(way.multiply(1.6+.5*(i%3)));
                m.softLine(a,b,.05,i%2==0?CORE:SKY,.55f*t*near);
            }
        }
    }
    /** The light of them on the world. */
    public static void frame(FxFrame f,Vec3d camera){
        float delta=MinecraftClient.getInstance().getTickDelta();
        for(var p:perfects){float l=flash(p.sec(delta));if(l>0)f.light(p.at,SKY,4.5,.9f*l);}
        for(var e:evades){float l=trail(e.sec(delta))*(1-Ease.smooth(e.sec(delta),0,.25f));if(l>0)f.light(e.from.add(0,.9,0),SKY,3.5,.6f*l);}
    }
    private static int lerp(int a,int b,float t){
        int ar=a>>16&255,ag=a>>8&255,ab=a&255,br=b>>16&255,bg=b>>8&255,bb=b&255;
        return (int)(ar+(br-ar)*t)<<16|(int)(ag+(bg-ag)*t)<<8|(int)(ab+(bb-ab)*t);
    }

    /**
     * The evasive's silhouettes: the body drawn again in flat sky blue where it left and a third and two thirds of the
     * way to where it is, thinning out (not where a shader pack draws the world, nor this client's own body in the first
     * person).
     */
    private static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();
        if(EVADES.isEmpty()||mc.world==null||!(ctx.consumers() instanceof VertexConsumerProvider.Immediate immediate)||!FxLayer.available())return;
        float delta=ctx.tickDelta();Vec3d cam=ctx.camera().getPos();boolean drew=false;
        for(var e:EVADES.values()){
            float t=trail(e.sec(delta));if(t<=0)continue;
            if(!(mc.world.getEntityById(e.id) instanceof LivingEntity body)||!body.getUuid().equals(e.who)||!body.isAlive()||body.isInvisible())continue;
            if(body==mc.getCameraEntity()&&!ctx.camera().isThirdPerson())continue;
            Vec3d now=body.getLerpedPos(delta);if(now.squaredDistanceTo(cam)>96*96)continue;
            for(int k=0;k<3;k++){
                Vec3d at=e.from.lerp(now,k/3.0);
                if(at.squaredDistanceTo(now)<.25*.25)continue;
                FlatBody.draw(mc,ctx.matrixStack(),immediate,cam,body,at,delta,k==0?DEEP:SKY,1.0f,(.42f-.1f*k)*t);
                drew=true;
            }
        }
        if(drew)immediate.draw(RenderLayer.getDebugQuads());
    }
    /** For the capture tools and the checks: the perfect guards and evasives this client draws. */
    public static String describe(){return "perfect="+PERFECTS.size()+" evade="+EVADES.size()+" mine="+(StandClient.ticks()-lastMine);}
}
