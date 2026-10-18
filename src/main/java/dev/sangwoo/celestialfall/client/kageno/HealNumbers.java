package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.StandClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Passive 1 "초재생": the only indication in the review is a green number over the torso showing
 * the total healed so far. Each regeneration tick replaces it in place at a new random spot and
 * tilt; ≈1.27 s after the last tick it accelerates away in a random direction while shrinking and
 * darkening (0.45 s), and the total starts over. 1 stud = 0.36 blocks.
 */
public final class HealNumbers {
    private HealNumbers(){}
    private static final class Entry{float health=-1;double total;long updated=-1000;int serial;}
    private static final Map<UUID,Entry> ENTRIES=new HashMap<>();
    private static final float HOLD=1.27f,EXIT=.45f;
    public static void render(MinecraftClient mc,MatrixStack matrices,float delta,Vec3d camera){
        if(mc.world==null)return;long now=mc.world.getTime();var vertices=mc.getBufferBuilders().getEntityVertexConsumers();boolean drew=false;
        ENTRIES.keySet().removeIf(id->mc.world.getPlayerByUuid(id)==null);
        for(var p:mc.world.getPlayers()){
            if(!StandClient.visible(p)||!p.isAlive())continue;
            var e=ENTRIES.computeIfAbsent(p.getUuid(),k->new Entry());float h=p.getHealth();
            if(e.health>=0&&h>e.health+.2f&&h-e.health<=1.01f){
                if((now-e.updated)/20f>HOLD+EXIT)e.total=0;
                e.total+=h-e.health;e.updated=now;e.serial++;
            }
            e.health=h;
            float age=(now-e.updated+delta)/20f;if(age>=HOLD+EXIT||e.total<=0||p.getPos().squaredDistanceTo(camera)>32*32)continue;
            int seed=p.getUuid().hashCode()*31+e.serial;
            Vec3d look=camera.subtract(p.getLerpedPos(delta)).multiply(1,0,1);if(look.lengthSquared()<1e-4)look=new Vec3d(0,0,1);look=look.normalize();
            Vec3d right=new Vec3d(look.z,0,-look.x);
            double dx=(Ease.hash(seed)*2-1)*.75*.36,dy=(-1.05+1.6*Ease.hash(seed+1))*.36,tilt=(Ease.hash(seed+2)*2-1)*17;
            Vec3d at=p.getLerpedPos(delta).add(0,1.1+dy,0).add(right.multiply(dx)).add(look.multiply(.55));
            float scale=1,dark=0;
            if(age>HOLD){
                float u=(age-HOLD)/EXIT,k=u*u*u;double ang=Ease.hash(seed+3)*Math.PI*2;
                at=at.add(right.multiply(Math.cos(ang)*1.6*k)).add(0,Math.abs(Math.sin(ang))*.8*k+.3*k,0);
                scale=1-.9f*u;dark=u;
            }
            int fill=mix(0x3ce49c,0x0e1a5a,dark);
            matrices.push();matrices.translate(at.x,at.y,at.z);matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)tilt));
            float s=.0215f*scale;matrices.scale(-s,-s,s);
            var text=Text.literal(format(e.total)).asOrderedText();float w=mc.textRenderer.getWidth(text);
            mc.textRenderer.drawWithOutline(text,-w/2,-4,0xff000000|fill,0xff0e1a5a,matrices.peek().getPositionMatrix(),vertices,LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();drew=true;
        }
        if(drew)vertices.draw();
    }
    /** Up to two decimals, trailing zeros dropped ("7", "7.5", "12.25"). */
    static String format(double v){
        String s=String.format(java.util.Locale.ROOT,"%.2f",v);
        if(s.contains(".")){s=s.replaceAll("0+$","");if(s.endsWith("."))s=s.substring(0,s.length()-1);}
        return s;
    }
    private static int mix(int a,int b,float t){
        int r=(int)(((a>>16)&255)+(((b>>16)&255)-((a>>16)&255))*t),g=(int)(((a>>8)&255)+(((b>>8)&255)-((a>>8)&255))*t),bl=(int)((a&255)+((b&255)-(a&255))*t);
        return (r<<16)|(g<<8)|bl;
    }
}
