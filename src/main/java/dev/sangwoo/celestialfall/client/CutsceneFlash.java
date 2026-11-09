package dev.sangwoo.celestialfall.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A skill with a cutscene makes its user's body glow sky blue: Kageno's H, Star Platinum's beatdown (G) and Lee So-sang's
 * G. Since 2.109.0 (user request: "빛나는 메커니즘을 바꾸자 몸이 하늘색으로 빛나고 빛이 끝나고 스킬이 시전되게 바꾸자 카게노 H
 * 스타플래티나 비트다운 이소상 G") the glow comes first and the skill is cast as it ends: the server tells of it
 * (CutsceneGlow, "cutscene_glow_v1") as its key is pressed, and of its being cut short (a blow landing on its user as they
 * glow), when it fades out at once ({@link #CUT_FADE}). While their own glow lasts the local player may only walk (at a
 * fifth of their speed, the server's: CombatClient.actionLocked, their body facing the way the camera looks, so the skill
 * goes the way they look as it is cast). Before (2.64.0 to 2.108.0) it came as the skill was cast, and 2.108.1 (never
 * released, user request: "이소상 G스킬도 컷씬 스킬이여서 푸른색으로 몸이 잠시 빛나야함 스타플래티나 비트다운 처럼") added Lee
 * So-sang's G to the two.
 * 2.64.0 (user request: "컷씬 나오는 스킬 사용할때는 시전자의 몸이 0.2초 동안 하늘색으로 변해") drew the body over in a flat
 * sky blue for 0.2 seconds; 2.65.0 (user request: "몸이 하늘색으로 변하는게 아니고 정확히는 몸 전체가 하늘색으로 빛이
 * 나야하는 쪽에 가까움 그러니까 몸 색깔을 바꾸는건 X", "빛나는 걸 0.5로 늘리자") leaves the body as it is and makes the
 * whole of it shine: for DURATION seconds a sky-blue light spreads out round the whole body from its outline, brightest
 * and palest right at the outline, with a little light along its edges just inside it (as a light behind it would
 * cast), in over two frames and out over the last part. It is drawn with the stands' aura
 * (StandAuraLayer: the body into a mask of its own, blurred out round it, only outside it); without that layer (a shader
 * pack, or post-processing off) the body gets a glowing shell instead ({@link #hull}: boxes a little bigger than each of
 * its limbs whose sides facing the camera are left out, so they show only round the body's outline, adding sky-blue light).
 */
public final class CutsceneFlash {
    /** How long it lasts (seconds), how quickly it comes in and from when it goes out. */
    public static final float DURATION=.5f,IN=.04f,OUT=.3f;
    /** The sky blue (the menus' and the gacha's). */
    public static final float RED=.5f,GREEN=.86f,BLUE=1f;
    /** A plain white texture: what the shell is drawn with. */
    public static final Identifier WHITE=new Identifier(dev.sangwoo.celestialfall.CelestialFall.ID,"textures/misc/white.png");
    /**
     * The glowing shell (without the aura layer): how far each box stands out from its limb (model pixels) and how much
     * light it adds, the nearest the brightest.
     */
    static final float[][] SHELLS={{.55f,.5f},{1.3f,.28f},{2.3f,.14f}};
    /** How quickly a glow cut short (its user struck as they glowed) fades out (seconds). */
    public static final float CUT_FADE=.1f;
    /** When each glow began, and when one was cut short (this client's ticks and the part of one, so it comes in and goes smoothly). */
    private static final Map<UUID,Double> STARTED=new HashMap<>(),CUTS=new HashMap<>();
    private static long ticks;
    /** Until when (this client's ticks) the local player glows, their skill not yet cast (-1: not). */
    private static long localUntil=-1;
    private CutsceneFlash(){}
    public static void initialize(){
        ClientTickEvents.START_CLIENT_TICK.register(mc->{
            if(mc.isPaused())return;ticks++;
            STARTED.values().removeIf(t->ticks-t>DURATION*20+2);CUTS.keySet().retainAll(STARTED.keySet());
        });
        // (2.109.0) the server's news of a glow: it begins as the key is pressed, it is cut short, or its skill is cast
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.sangwoo.celestialfall.combat.CutsceneGlow.EVENT,(mc,h,b,r)->{
            var id=b.readUuid();int event=b.readByte(),skill=b.readByte();
            mc.execute(()->heard(mc,id,event,skill));
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->mc.execute(CutsceneFlash::clear));
    }
    public static void clear(){STARTED.clear();CUTS.clear();localUntil=-1;}
    /** The server's news of {@code id}'s glow (CutsceneGlow): {@code event} BEGIN, CUT or CAST, for {@code skill}. */
    static void heard(MinecraftClient mc,UUID id,int event,int skill){
        boolean local=mc.player!=null&&mc.player.getUuid().equals(id);
        float delta=mc.getTickDelta();
        if(event==dev.sangwoo.celestialfall.combat.CutsceneGlow.BEGIN){
            STARTED.put(id,ticks+(double)clamp(delta));CUTS.remove(id);
            if(local)localUntil=ticks+dev.sangwoo.celestialfall.combat.CutsceneGlow.TICKS+4;
            return;
        }
        if(event==dev.sangwoo.celestialfall.combat.CutsceneGlow.CUT&&STARTED.containsKey(id))CUTS.putIfAbsent(id,ticks+(double)clamp(delta));
        if(local)localUntil=-1;
    }
    /** This player's glow begins now (as the server tells of it). */
    public static void flash(UUID player){STARTED.put(player,(double)ticks);CUTS.remove(player);}
    /** Whether the local player glows now, their skill not yet cast: they may only walk (CombatClient.actionLocked). */
    public static boolean localGlowing(){var mc=MinecraftClient.getInstance();return mc.player!=null&&MapClient.enabled()&&ticks<localUntil;}
    /** How strongly this one's body glows this frame (0 none, 1 fully). */
    public static float strength(Entity e,float delta){
        var at=STARTED.get(e.getUuid());if(at==null||!MapClient.enabled())return 0;
        double now=ticks+clamp(delta);
        var cut=CUTS.get(e.getUuid());
        return strength((float)((now-at)/20),cut==null?Float.NaN:(float)((cut-at)/20));
    }
    /**
     * How strongly it shows {@code t} seconds after it began, cut short {@code cut} seconds after it began (NaN: not): from
     * the cut it fades out over {@link #CUT_FADE}, from however strong it was.
     */
    public static float strength(float t,float cut){
        float a=strength(t);
        if(Float.isNaN(cut)||t<cut)return a;
        return a*(1-smooth((t-cut)/CUT_FADE));
    }
    private static float clamp(float delta){return Math.max(0,Math.min(1,delta));}
    /** How strongly it shows {@code t} seconds after the skill was used. */
    public static float strength(float t){
        if(t<0||t>=DURATION)return 0;
        float in=smooth(t/IN),out=1-smooth((t-OUT)/(DURATION-OUT));
        return in*out;
    }
    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** For the capture tools: how strongly the local player glows now. */
    public static float local(float delta){var p=MinecraftClient.getInstance().player;return p==null?0:strength(p,delta);}

    /** The players glowing this frame, and how strongly. */
    public record Glowing(PlayerEntity player,float strength){}
    public static List<Glowing> glowing(MinecraftClient mc,float delta){
        var out=new ArrayList<Glowing>();if(mc.world==null||STARTED.isEmpty())return out;
        for(var id:STARTED.keySet()){
            var p=mc.world.getPlayerByUuid(id);if(p==null||p.isInvisible())continue;
            float a=strength(p,delta);if(a>.001f)out.add(new Glowing(p,a));
        }
        return out;
    }

    /**
     * The glowing shell round a body, for when the aura layer cannot draw the glow: for each of its limbs, boxes a
     * little bigger than it, wound to face inwards, so that the sides facing the camera are culled and only the far
     * sides show, round the outline (the body hides them everywhere else), adding sky-blue light ({@code a} how strongly).
     */
    public static void hull(MatrixStack m,VertexConsumerProvider vertices,BipedEntityModel<?> model,float a){
        VertexConsumer vc=vertices.getBuffer(RenderLayer.getEyes(WHITE));
        ModelPart[] parts={model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg};
        for(float[] shell:SHELLS){
            float d=shell[0],k=shell[1]*a;
            for(var part:parts){
                if(!part.visible||part.hidden)continue;
                part.forEachCuboid(m,(entry,path,index,cuboid)->box(entry.getPositionMatrix(),entry.getNormalMatrix(),vc,cuboid,d,RED*k,GREEN*k,BLUE*k));
            }
        }
    }
    /** A box {@code d} model pixels bigger than {@code c} each way, every face wound to face inwards. */
    static void box(Matrix4f p,Matrix3f n,VertexConsumer vc,ModelPart.Cuboid c,float d,float r,float g,float b){
        float x0=(c.minX-d)/16,y0=(c.minY-d)/16,z0=(c.minZ-d)/16,x1=(c.maxX+d)/16,y1=(c.maxY+d)/16,z1=(c.maxZ+d)/16;
        face(p,n,vc,r,g,b,new Vector3f(x1,y0,z0),new Vector3f(0,y1-y0,0),new Vector3f(0,0,z1-z0),new Vector3f(1,0,0));
        face(p,n,vc,r,g,b,new Vector3f(x0,y0,z0),new Vector3f(0,y1-y0,0),new Vector3f(0,0,z1-z0),new Vector3f(-1,0,0));
        face(p,n,vc,r,g,b,new Vector3f(x0,y1,z0),new Vector3f(x1-x0,0,0),new Vector3f(0,0,z1-z0),new Vector3f(0,1,0));
        face(p,n,vc,r,g,b,new Vector3f(x0,y0,z0),new Vector3f(x1-x0,0,0),new Vector3f(0,0,z1-z0),new Vector3f(0,-1,0));
        face(p,n,vc,r,g,b,new Vector3f(x0,y0,z1),new Vector3f(x1-x0,0,0),new Vector3f(0,y1-y0,0),new Vector3f(0,0,1));
        face(p,n,vc,r,g,b,new Vector3f(x0,y0,z0),new Vector3f(x1-x0,0,0),new Vector3f(0,y1-y0,0),new Vector3f(0,0,-1));
    }
    /**
     * One face (corners {@code o}, o+u, o+u+v, o+v; {@code out} the way out of the box), wound so that it faces inwards:
     * seen from outside it is clockwise, and culled.
     */
    static void face(Matrix4f p,Matrix3f n,VertexConsumer vc,float r,float g,float b,Vector3f o,Vector3f u,Vector3f v,Vector3f out){
        boolean outward=new Vector3f(u).cross(v).dot(out)>0;
        Vector3f a=o,c=new Vector3f(o).add(u).add(v),s1=new Vector3f(o).add(outward?v:u),s2=new Vector3f(o).add(outward?u:v);
        for(var q:new Vector3f[]{a,s1,c,s2})
            vc.vertex(p,q.x,q.y,q.z).color(r,g,b,1).texture(.5f,.5f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(n,-out.x,-out.y,-out.z).next();
    }
}
