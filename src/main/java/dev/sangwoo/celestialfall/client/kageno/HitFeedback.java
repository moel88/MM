package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The game's generic hit feedback seen in every dummy demo of the review, shown to the attacker:
 * a red cumulative damage number with a dark outline on the target's chest (reset per combo) and
 * a dark-red blood streak with splatter along the attack direction.
 */
public final class HitFeedback {
    private HitFeedback(){}
    private static final class Number{double total;long last;int pop;}
    private record Blood(int entity,Vec3d at,Vec3d dir,long born,int seed){}
    private static final Map<Integer,Number> NUMBERS=new HashMap<>();
    private static final List<Blood> BLOOD=new ArrayList<>();
    /** Seconds without a hit before the number fades and the next hit starts a new total. */
    private static final float COMBO=1.6f;
    public static void hit(MinecraftClient mc,int entity,float amount,float yaw){
        if(mc.world==null)return;long now=mc.world.getTime();var e=mc.world.getEntityById(entity);if(e==null)return;
        var n=NUMBERS.computeIfAbsent(entity,k->new Number());if((now-n.last)/20f>COMBO)n.total=0;
        n.total+=amount;n.last=now;n.pop++;
        if(BLOOD.size()>64)BLOOD.remove(0);
        BLOOD.add(new Blood(entity,e.getPos().add(0,e.getHeight()*.62,0),Vec3d.fromPolar(0,yaw),now,(int)(now*31+entity)));
    }
    public static void clear(){NUMBERS.clear();BLOOD.clear();}
    /** Alpha pass: blood streaks and droplets. */
    public static void blood(MinecraftClient mc,EffectMesh m,float delta){
        if(mc.world==null)return;long now=mc.world.getTime();
        BLOOD.removeIf(b->(now-b.born())/20f>.7f);
        for(var b:BLOOD){
            float t=(now-b.born()+delta)/20f,a=1-Ease.smooth(t,.35f,.7f);
            Vec3d side=new Vec3d(-b.dir().z,0,b.dir().x);
            // A long thin streak through the target along the hit, growing out in 0.08 s.
            double len=1.8*(.6+.4*Ease.hash(b.seed()))*Ease.out(t,0,.08f);
            Vec3d from=b.at().subtract(b.dir().multiply(.15)),to=b.at().add(b.dir().multiply(len)).add(side.multiply((Ease.hash(b.seed()+1)-.5)*.4)).add(0,(Ease.hash(b.seed()+2)-.5)*.3,0);
            m.needle(from,to,.12,.25,0x650206,a*.9f);m.needle(from,to,.05,.25,0xc43b39,a*.8f);
            for(int k=0;k<9;k++){
                double sp=1.5+2*Ease.hash(b.seed()+k*7);Vec3d v=b.dir().multiply(sp).add(side.multiply((Ease.hash(b.seed()+k*11)-.5)*2)).add(0,1+1.5*Ease.hash(b.seed()+k*13),0);
                Vec3d p=b.at().add(v.multiply(t)).add(0,-4.9*t*t,0);double s=.03+.04*Ease.hash(b.seed()+k*17);
                int col=k%3==0?0x87070a:k%3==1?0x701c26:0xc43b39;
                m.needle(p.add(0,s,0),p.subtract(0,s,0),s*1.6,.5,col,a);
            }
        }
    }
    /** Text pass: the red cumulative numbers. */
    public static void numbers(MinecraftClient mc,MatrixStack matrices,float delta){
        if(mc.world==null)return;long now=mc.world.getTime();var vertices=mc.getBufferBuilders().getEntityVertexConsumers();boolean drew=false;
        NUMBERS.entrySet().removeIf(en->(now-en.getValue().last)/20f>COMBO+.3f||mc.world.getEntityById(en.getKey())==null);
        for(var en:NUMBERS.entrySet()){
            Entity e=mc.world.getEntityById(en.getKey());var n=en.getValue();
            float since=(now-n.last+delta)/20f,a=1-Ease.smooth(since,COMBO,COMBO+.3f);if(a<=0)continue;
            float pop=1+.35f*(1-Ease.smooth(since,0,.12f));
            // On the chest, but in front of the body as seen from the camera so the model never hides it.
            Vec3d chest=e.getLerpedPos(delta).add(0,e.getHeight()*.62,0),toCam=mc.gameRenderer.getCamera().getPos().subtract(chest);
            Vec3d at=toCam.lengthSquared()<1e-4?chest:chest.add(toCam.normalize().multiply(e.getWidth()*.5+.35));
            matrices.push();matrices.translate(at.x,at.y,at.z);matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            float s=.026f*pop;matrices.scale(-s,-s,s);
            var text=Text.literal(HealNumbers.format(n.total)).asOrderedText();float w=mc.textRenderer.getWidth(text);
            int alpha=Math.max(8,(int)(a*255))<<24;
            mc.textRenderer.drawWithOutline(text,-w/2,-4,alpha|0xe0242a,alpha|0x2a0606,matrices.peek().getPositionMatrix(),vertices,LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();drew=true;
        }
        if(drew)vertices.draw();
    }
}
