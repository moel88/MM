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
 * A skill with a cutscene makes its user's body glow sky blue as it is used: Star Platinum's beatdown (G) and Kageno's H.
 * 2.64.0 (user request: "컷씬 나오는 스킬 사용할때는 시전자의 몸이 0.2초 동안 하늘색으로 변해") drew the body over in a flat
 * sky blue for 0.2 seconds; 2.65.0 (user request: "몸이 하늘색으로 변하는게 아니고 정확히는 몸 전체가 하늘색으로 빛이
 * 나야하는 쪽에 가까움 그러니까 몸 색깔을 바꾸는건 X", "빛나는 걸 0.5로 늘리자") leaves the body as it is and makes the
 * whole of it shine: for DURATION seconds a sky-blue light spreads out round the whole body from its outline, brightest
 * and palest right at the outline, in over two frames and out over the last part. It is drawn with the stands' aura
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
    public static final Identifier WHITE=dev.sangwoo.celestialfall.CelestialFall.id("textures/misc/white.png");
    /**
     * The glowing shell (without the aura layer): how far each box stands out from its limb (model pixels) and how much
     * light it adds, the nearest the brightest.
     */
    static final float[][] SHELLS={{.55f,.5f},{1.3f,.28f},{2.3f,.14f}};
    private static final Map<UUID,Long> STARTED=new HashMap<>();
    private static long ticks;
    private CutsceneFlash(){}
    public static void initialize(){
        ClientTickEvents.START_CLIENT_TICK.register(mc->{
            if(mc.isPaused())return;ticks++;
            STARTED.values().removeIf(t->ticks-t>DURATION*20+2);
        });
    }
    /** This player used a skill with a cutscene now. */
    public static void flash(UUID player){STARTED.put(player,ticks);}
    /** How strongly this one's body glows this frame (0 none, 1 fully). */
    public static float strength(Entity e,float delta){
        var at=STARTED.get(e.getUuid());if(at==null||!MapClient.enabled())return 0;
        return strength((ticks-at+Math.max(0,Math.min(1,delta)))/20f);
    }
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
