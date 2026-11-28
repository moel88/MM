package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import dev.sangwoo.celestialfall.stand.GoldTreeEntity;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.stand.GoldHealFx.LEAF_DEEP;
import static dev.sangwoo.celestialfall.client.stand.GoldHealFx.LEAF_PALE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.AMBER;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GOLD;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GUARD_BLUE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.GUARD_LIGHT;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.PALE;
import static dev.sangwoo.celestialfall.client.stand.GoldRushFx.WHITE;
import static dev.sangwoo.celestialfall.client.stand.StandPunchFx.burst;

/**
 * The light of Gold Experience's H, the tree of life (2.152.0), drawn with its rush's on the effect layer (StandRushFx
 * calls it pass by pass): gold, the green of new leaves, and the earth it breaks out of. None of it over the screen.
 * <ul>
 * <li>Its palm going to the ground: light gathers into it, and as it touches veins of gold run over the ground from it to
 * its user's feet, where a ring of light closes.</li>
 * <li>The burst: a ring of gold runs out over the ground as far as it strikes, the ground heaves and breaks, chunks of it
 * thrown up, cracks running out from the foot of the tree (gold, then dark, as one of the pictures the user sent has
 * them), dust, a column of light up the trunk, new leaves thrown out, its user trailing gold as they are flung up; a burst
 * on each one it struck (blue off a guard that took it).</li>
 * <li>A blow on it turned back: a thread of gold from the tree to the striker, a burst on them.</li>
 * <li>Withering: its leaves fall, and the cracks close.</li>
 * </ul>
 */
public final class GoldTreeFx {
    record Shown(GoldTree.Tree tree,float t,PlayerEntity user,StandPresence.Body body,GoldTreeEntity entity,float delta){}
    private static final List<Shown> FRAME=new ArrayList<>();
    /** The twigs' ends in the model (units, its rest pose): where its leaves fall from. */
    static final List<double[]> TIPS=tips();
    /** The cracks out of its foot: how many, how far they run (blocks). */
    static final int CRACKS=9;
    static final double CRACK_MIN=1.6,CRACK_MAX=4.4;
    /** Seconds: the burst's ring runs out this long; the cracks glow gold this long. */
    static final float RING=.38f,GLOW=1.3f;
    static final int EARTH=0x5a3d22,EARTH_DARK=0x1e1208,CRACK=0x150c05;
    private GoldTreeFx(){}

    static boolean prepare(MinecraftClient mc,float tickDelta,Vec3d camera){
        FRAME.clear();
        if(mc.world==null)return false;
        for(var e:GoldTree.all().entrySet()){
            var tree=e.getValue();if(tree.spot==null||tree.spot.squaredDistanceTo(camera)>112*112)continue;
            float t=tree.age(tickDelta);if(t<0||t>GoldExperienceRules.TREE_END/20f+1.5f)continue;
            var user=mc.world.getPlayerByUuid(e.getKey());
            var entity=tree.tree>=0&&mc.world.getEntityById(tree.tree) instanceof GoldTreeEntity g?g:null;
            FRAME.add(new Shown(tree,t,user,GoldPresence.body(e.getKey()),entity,tickDelta));
        }
        return !FRAME.isEmpty();
    }
    static float clear(EffectMesh m,Vec3d p){return Ease.smooth((float)m.camera().distanceTo(p),.5f,1.3f);}
    /** When it burst up (seconds on its clock; the server's moment if it has not been heard yet). */
    static float burstAt(GoldTree.Tree t){return t.burst()?t.burst:GoldTree.BURST;}
    /** When it withers (seconds on its clock). */
    static float witherAt(Shown s){
        float at=burstAt(s.tree)+(GoldExperienceRules.TREE_WITHER-GoldExperienceRules.TREE_BURST)/20f;
        if(s.entity!=null)at=Math.min(at,burstAt(s.tree)+(s.entity.witherAt()-s.entity.born())/20f);
        if(!Float.isNaN(s.tree.withered))at=Math.min(at,s.tree.withered);
        return at;
    }
    /** Whether its tree burst up (or will: not stopped short). */
    static boolean grows(GoldTree.Tree t){return Float.isNaN(t.stopped)||t.burst();}
    /** A point of the tree's model (units) in the world: the model is drawn turned to its maker's facing, x mirrored as GeckoLib bakes it. */
    static Vec3d world(Vec3d foot,float facing,double x,double y,double z){
        double r=Math.toRadians(facing),c=Math.cos(r),s=Math.sin(r);
        return foot.add((x*c+z*s)/16,y/16,(x*s-z*c)/16);
    }

    // ───────────────────────────── the passes ─────────────────────────────

    static void alpha(EffectMesh m){
        for(var s:FRAME){
            if(!grows(s.tree))continue;
            float since=s.t-burstAt(s.tree),wither=witherAt(s);
            Vec3d foot=s.tree.spot;
            // the cracks, dark in the ground from the burst until it has withered
            float close=1-Ease.smooth(s.t,wither,wither+GoldTreeGrowth.WITHER/20f+.4f);
            if(since>=0)cracks(m,foot,s.tree.start,since,close,false);
            // dust thrown up round its foot
            for(int k=0;k<12;k++){
                float u=(since-.02f*(float)Ease.hash(k*5+(int)s.tree.start))/.9f;if(u<0||u>=1)continue;
                double az=(k+Ease.hash(k*7+(int)s.tree.start))*Math.PI*2/12,r=.6+2.2*Ease.out(u,0,.7f);
                Vec3d p=foot.add(Math.cos(az)*r,.25+.6*u,Math.sin(az)*r);
                m.softDisc(p,.35+.6*Ease.out(u,0,.6f),k%3==0?EARTH_DARK:EARTH,.55f*(1-u)*Ease.smooth(u,0,.08f)*clear(m,p));
            }
            // new leaves thrown out of it as it bursts up
            Vec3d crown=foot.add(0,2.6,0);
            GoldLifeFx.sprout(m,crown,16,(int)s.tree.start,since/1.1f,2.4,1,true);GoldLifeFx.sprout(m,crown,16,(int)s.tree.start,since/1.1f,2.4,1,false);
            // withering: its leaves fall from its twigs, turning as they go
            if(s.entity!=null||s.t>=wither)falling(m,s,wither);
        }
    }
    static void additive(EffectMesh m){
        for(var s:FRAME){
            touch(m,s);
            if(!grows(s.tree))continue;
            float since=s.t-burstAt(s.tree);
            if(since>=0)burstLight(m,s,since);
            turned(m,s);
        }
    }

    /** Its palm going to the ground: light gathering into it, the veins of gold to its user's feet, the ring closing there. */
    static void touch(EffectMesh m,Shown s){
        if(s.body==null)return;
        var t=s.tree;float a=s.t;
        float k=GoldTree.kneel(t,a);if(k<=.02f)return;
        Vec3d palm=GoldTree.palm(t.skin,s.body);
        float near=clear(m,palm);
        // light gathering into its palm as it goes down
        float g=Ease.smooth(a,.05f,GoldTree.TOUCH)*(1-Ease.smooth(a,burstAt(t),burstAt(t)+.25f));
        if(!Float.isNaN(t.stopped))g*=1-Ease.smooth(a,t.stopped,t.stopped+.15f);
        if(g>0){
            float pulse=.5f+.5f*(float)Math.sin(a*Math.PI*2*6);
            m.softDisc(palm,.25+.25*g+.05*pulse*g,AMBER,.4f*g*near);m.softDisc(palm,.1+.08*g,PALE,.55f*g*near);
            if(a<GoldTree.TOUCH)FxLib.converge(m,palm,.7,10,a,.05f,GoldTree.TOUCH,GOLD,(int)t.start+3);
        }
        // as it touches: veins of gold running over the ground from its palm to its user's feet
        if(!Float.isNaN(t.stopped)&&t.stopped<GoldTree.TOUCH)return;
        float run=Ease.out(a,GoldTree.TOUCH,burstAt(t)),fade=1-Ease.smooth(a,burstAt(t),burstAt(t)+.3f);
        if(run<=0||fade<=0)return;
        Vec3d feet=t.spot.add(0,.04,0),from=new Vec3d(palm.x,feet.y,palm.z),go=feet.subtract(from);
        if(go.lengthSquared()<1e-4)return;
        Vec3d side=new Vec3d(-go.z,0,go.x).normalize();
        for(int v=0;v<3;v++){
            double wob=(v-1)*.22,phase=Ease.hash(v*13+(int)t.start)*6.283;
            Vec3d last=from;
            for(int i=1;i<=12;i++){
                double u=i/12.0;if(u>run)break;
                Vec3d p=from.add(go.multiply(u)).add(side.multiply(wob*Math.sin(Math.PI*u)+.08*Math.sin(u*14+phase)));
                m.softTaper(last,p,.09,.09,GOLD,.8f*fade);m.softTaper(last,p,.03,.03,WHITE,.7f*fade);
                last=p;
            }
            if(run<1)m.star(last,.14,PALE,.9f*fade);
        }
        // and a ring of light closing under its user's feet as they come
        FxLib.ring(m,feet,.25+1.1*(1-run),.05+.04*run,GOLD,.75f*run*fade);
        m.softDisc(feet.add(0,.1,0),.5+.4*run,AMBER,.3f*run*fade);
    }
    /** The burst: the ring of gold over the ground, the glowing cracks, the column up the trunk, its user's trail, a burst on each one struck. */
    static void burstLight(EffectMesh m,Shown s,float since){
        var t=s.tree;Vec3d foot=t.spot.add(0,.06,0);
        float u=since/RING;
        if(u<1){
            double r=.6+(GoldExperienceRules.TREE_RADIUS-.6)*Ease.out(u,0,1);float a=(float)Math.pow(1-u,.7);
            FxLib.ring(m,foot,r,.32*(1-.5*u),GOLD,.7f*a);FxLib.ring(m,foot,r*.97,.1,WHITE,.65f*a);
            FxLib.ring(m,foot,r*.6,.12,AMBER,.4f*a);
        }
        // light at its foot and a column up its trunk
        float c=1-Ease.smooth(since,.04f,.5f);
        if(c>0){
            m.softDisc(foot.add(0,.4,0),1.6,AMBER,.45f*c*clear(m,foot));m.star(foot.add(0,.5,0),1.1*c,WHITE,.55f*c);
            Vec3d top=foot.add(0,5.2*Ease.out(since,0,.18f),0);
            m.softTaper(foot,top,.9*c,.2*c,GOLD,.5f*c);m.softTaper(foot,top,.3*c,.05*c,WHITE,.55f*c);
        }
        // the cracks glow gold a moment, then are dark
        float glow=1-Ease.smooth(since,.15f,GLOW);
        if(glow>0)cracks(m,t.spot,t.start,since,glow,true);
        // its user flung up, trailing gold
        if(s.user!=null&&since<.7f){
            float a=1-since/.7f;
            for(int k=0;k<6;k++){
                Vec3d p=s.user.getLerpedPos(s.delta).add(0,-.15-k*.35*Math.min(1,since*5),0);
                float near=clear(m,p);
                m.star(p,.08+.03*(6-k)/6.0,k%2==0?PALE:GOLD,.8f*a*(1-k/6f)*near);
            }
        }
        // a burst on each one it struck (blue rings off a guard that took it)
        var mc=MinecraftClient.getInstance();
        float b=(since-.02f)/.45f;
        if(b>=0&&b<1){
            float ab=(float)Math.pow(1-b,.7);
            for(var h:t.hits){
                if(!(mc.world.getEntityById(h.entity()) instanceof LivingEntity e))continue;
                Vec3d at=e.getLerpedPos(s.delta).add(0,e.getHeight()*.5,0);Vec3d from=at.subtract(foot);
                Vec3d toward=new Vec3d(from.x,0,from.z);toward=toward.lengthSquared()<1e-4?new Vec3d(1,0,0):toward.normalize();
                Vec3d p=at.subtract(toward.multiply(e.getWidth()*.5));
                if(h.guarded()){
                    burst(m,p,.5,.34,8,h.entity()*7,GUARD_LIGHT,GUARD_BLUE,.85f*ab,.7f*ab);
                    FxLib.ringPlane(m,p,new Vec3d(-toward.z,0,toward.x),new Vec3d(0,1,0),.2+.6*Ease.out(b,0,1),.06,GUARD_BLUE,.6f*ab);
                }else{
                    burst(m,p,.62*(.8+.3*Ease.out(b,0,.4f)),.36,10,h.entity()*7,PALE,GOLD,.9f*ab,.75f*ab);
                    m.softDisc(at,.8,AMBER,.25f*ab);
                }
            }
        }
    }
    /** Each blow it turned back: a ring off the trunk toward the striker, a thread of gold running to them, a burst on them. */
    static void turned(EffectMesh m,Shown s){
        var mc=MinecraftClient.getInstance();
        for(var r:s.tree.turned){
            float since=s.t-r.at();if(since<0||since>.55f)continue;
            if(!(mc.world.getEntityById(r.striker()) instanceof LivingEntity striker))continue;
            Vec3d to=striker.getLerpedPos(s.delta).add(0,striker.getHeight()*.6,0);
            Vec3d trunk=s.tree.spot.add(0,Math.max(.6,Math.min(3.5,to.y-s.tree.spot.y)),0);
            Vec3d go=to.subtract(trunk);if(go.lengthSquared()<1e-6)continue;
            Vec3d dir=go.normalize(),side=dir.crossProduct(new Vec3d(0,1,0));if(side.lengthSquared()<1e-4)side=new Vec3d(1,0,0);side=side.normalize();Vec3d up=side.crossProduct(dir).normalize();
            Vec3d from=trunk.add(dir.multiply(.35));
            float u=since/.3f,a=u<1?1-u:0;double grow=Ease.out(Math.min(1,u),0,1);
            if(a>0){FxLib.ringPlane(m,from,side,up,.15+.6*grow,.06,GOLD,.75f*a);FxLib.ringPlane(m,from,side,up,.12+.4*grow,.02,WHITE,.6f*a);m.softDisc(from,.4,AMBER,.35f*a);}
            float head=Math.min(1,since/.1f),tail=Math.min(1,Math.max(0,(since-.06f)/.12f));
            if(tail<1){
                Vec3d h=from.add(to.subtract(from).multiply(head)),tl=from.add(to.subtract(from).multiply(tail));
                m.softTaper(tl,h,.03,.11,GOLD,.75f);m.softTaper(tl,h,.01,.04,WHITE,.85f);m.star(h,.14,PALE,.9f);
            }
            float b=(since-.1f)/.4f;
            if(b>=0&&b<1){
                float ab=(float)Math.pow(1-b,.7);
                burst(m,to.subtract(dir.multiply(striker.getWidth()*.5)),.55*(.8+.3*Ease.out(b,0,.4f)),.36,10,r.striker()*11+(int)(r.at()*100),PALE,GOLD,.9f*ab,.8f*ab);
                m.softDisc(to,.8,AMBER,.28f*ab);
                FxLib.ringPlane(m,to.subtract(dir.multiply(.3)),side,up,.2+.8*Ease.out(b,0,1),.07,GOLD,.6f*ab);
            }
        }
    }

    /**
     * The cracks out of its foot: jagged lines over the ground, running out from it as it bursts up ({@code since} seconds
     * after), dark in the ground ({@code glow}: gold, drawn light, as they glow a moment after the burst).
     */
    static void cracks(EffectMesh m,Vec3d foot,long seed,float since,float shown,boolean glow){
        if(shown<=0)return;
        int s=(int)seed;
        for(int k=0;k<CRACKS;k++){
            double az=(k+.6*Ease.hash(s+k*3))*Math.PI*2/CRACKS,len=CRACK_MIN+(CRACK_MAX-CRACK_MIN)*Ease.hash(s+k*7);
            double run=len*Ease.out(since,0,.16f+.06f*(float)Ease.hash(s+k*5));
            Vec3d last=foot.add(Math.cos(az)*.45,.025,Math.sin(az)*.45);double heading=az;
            int n=6;
            for(int i=1;i<=n;i++){
                double d=len*i/n;if(d-len/n>run)break;
                heading+=(Ease.hash(s+k*31+i*17)-.5)*.9;
                double step=Math.min(len/n,run-(d-len/n));
                Vec3d p=last.add(Math.cos(heading)*step,0,Math.sin(heading)*step);
                Vec3d across=new Vec3d(-Math.sin(heading),0,Math.cos(heading));
                double w=(.16-.12*i/n)*(glow?.55:1);
                if(glow){m.needle(last,p,across,w,.5,GOLD,.85f*shown);m.needle(last,p,across,w*.4,.5,WHITE,.7f*shown);}
                else m.needle(last,p,across,w,.5,CRACK,.85f*shown);
                last=p;
                // a short branch off it now and then
                if(i==2||i==4&&Ease.hash(s+k*13+i)>.5){
                    double h2=heading+(Ease.hash(s+k*19+i)>.5?.9:-.9);Vec3d q=p.add(Math.cos(h2)*len*.18,0,Math.sin(h2)*len*.18);
                    Vec3d a2=new Vec3d(-Math.sin(h2),0,Math.cos(h2));
                    if(glow)m.needle(p,q,a2,w*.6,.4,GOLD,.7f*shown);else m.needle(p,q,a2,w*.6,.4,CRACK,.8f*shown);
                }
            }
        }
    }
    /** Withering: leaves falling from its twigs' ends, turning as they go, gone on the ground. */
    static void falling(EffectMesh m,Shown s,float wither){
        Vec3d foot=s.tree.spot;float facing=s.entity!=null?s.entity.facing():s.tree.yaw;
        for(int i=0;i<TIPS.size();i++){
            double[] tip=TIPS.get(i);
            // a few fall now and then as it stands (all its 20 s), the rest as it withers
            float stand=(GoldExperienceRules.TREE_WITHER-GoldExperienceRules.TREE_BURST)/20f;
            float start=Ease.hash(i*7+3)<.35?burstAt(s.tree)+1.5f+Math.max(0,stand-3f)*(float)Ease.hash(i*11):wither+.5f*(float)Ease.hash(i*13);
            float u=(s.t-start)/1.4f;if(u<0||u>=1)continue;
            Vec3d from=world(foot,facing,tip[0],tip[1],tip[2]);
            double drop=(from.y-foot.y)*(u*u*.6+u*.4),sway=.35*Math.sin(u*9+i);
            Vec3d p=from.add(sway,-drop,.25*Math.cos(u*7+i));
            float a=Ease.smooth(u,0,.08f)*(1-Ease.smooth(u,.8f,1))*clear(m,p);
            GoldLifeFx.leaf(m,p,i*1.3+u*6,.16,a,true);GoldLifeFx.leaf(m,p,i*1.3+u*6,.16,a,false);
        }
    }
    /** The ground heaving and broken as it bursts up: a ring running out under it, chunks thrown up. */
    public static void solids(FxSolids solids){
        for(var s:FRAME){
            if(!grows(s.tree))continue;
            float since=s.t-burstAt(s.tree);if(since<0||since>1.1f)continue;
            FxLib.heave(solids,s.tree.spot,since,GoldExperienceRules.TREE_RADIUS-.5,.45,13,1.1,.9);
            FxLib.chunks(solids,s.tree.spot,since,14,(int)s.tree.start*7+5,2.6,6.5,.22,.55f);
        }
    }
    /** The effect layer: its palm lights the ground, the burst its foot, a blow turned back its striker. */
    static void frame(FxFrame f,Vec3d camera){
        for(var s:FRAME){
            var t=s.tree;
            if(s.body!=null){float k=GoldTree.kneel(t,s.t)*(1-Ease.smooth(s.t,burstAt(t),burstAt(t)+.3f));if(k>0)f.light(GoldTree.palm(t.skin,s.body),0xffd890,4,1.2f*k);}
            if(!grows(t))continue;
            float since=s.t-burstAt(t);
            if(since>=0&&since<.9f)f.light(t.spot.add(0,1,0),0xffe0a0,11,3.6f*FxLib.kick(since,0,.25f));
            // once its user is down, its leaves fall leaving no streak behind them (the layer's afterglow drew a falling
            // leaf as a pale green beam in the first filming): they are leaves, not lights
            if(since>=1.2f)f.afterglow(.12f);
            for(var r:t.turned){float d=s.t-r.at();if(d>=0&&d<.45f&&MinecraftClient.getInstance().world.getEntityById(r.striker()) instanceof LivingEntity e)f.light(e.getPos().add(0,e.getHeight()*.6,0),0xffd27a,6,1.8f*(1-d/.45f));}
        }
    }

    private static List<double[]> tips(){
        var out=new ArrayList<double[]>();
        try(var in=GoldTreeFx.class.getResourceAsStream("/assets/celestialfall/stands/gold_tree_shape.json")){
            var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,"gold_tree_shape"),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var p:root.getAsJsonArray("tips")){var a=p.getAsJsonArray();out.add(new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()});}
        }catch(Exception ex){
            CelestialFall.LOGGER.error("Gold Experience's tree's twigs could not be read",ex);
        }
        return List.copyOf(out);
    }
}
