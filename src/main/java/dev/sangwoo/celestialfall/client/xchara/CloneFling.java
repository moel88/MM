package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import static dev.sangwoo.celestialfall.client.xchara.GlitchFx.*;
import static dev.sangwoo.celestialfall.xchara.CloneRules.*;

/**
 * X Chara's R with the hack on, the double, drawn (2.125.0, through the effect layer, from its cast's clock: CloneClient,
 * the user's video for its looks). For everyone near: the "!!!" over the one marked (G's own, SwordDance.bangs), gone as
 * the double throws or as a guard blocks it (a flash of white light on them then); on the double beside its user, the X
 * on its face (the jab's X, CorruptionJab) and bars of purple torn across its body, pixels as it comes and as it breaks
 * up; a streak off its throw to the one marked and a ring round them as they are seized, pixels rising under them as
 * they hang, a streak behind them as they are flung; the wall ({@link #solids}: a fan of pointed spikes of the block
 * under it, "벽은 아래 있는 블록에 따라 바뀜", thrust up out of the ground just before they reach it, shaken as they go
 * in, pieces of the ground thrown, sinking back as it lets them go), rings on the ground, dust and a burst as they go
 * into it. No screen effects but a shake as they hit the wall.
 */
public final class CloneFling implements Fx.Skill {
    @Override public boolean layered(){return true;}
    @Override public float lifetime(){return (END+50)/20f;}

    private static CloneClient.Throw of(Fx c){return CloneClient.ofCast(c.cast);}
    private static LivingEntity target(Fx c){return c.targets.isEmpty()?null:c.targets.get(0);}
    /** The one marked's feet as they are drawn this frame (where the throw has them, or where they stand). */
    private static Vec3d feet(Fx c,CloneClient.Throw d,LivingEntity e){
        Vec3d at=CloneClient.drawn(d,c.age);
        return at!=null?at:e!=null?e.getLerpedPos(c.delta):d.targetFrom;
    }

    @Override public void alpha(Fx c,EffectMesh m){var d=of(c);if(d==null)return;draw(c,d,m,false);}
    @Override public void additive(Fx c,EffectMesh m){var d=of(c);if(d==null)return;draw(c,d,m,true);}

    /** How strongly the "!!!" shows at {@code t}: from MARK until the throw (a little past it until the server says they are seized), gone as it is blocked or called off. */
    static float markShown(CloneClient.Throw d,float t){
        if(t<MARK)return 0;
        if(d.ended()&&t>=d.ended)return 0;
        return d.seized()?(t<THROW?1:0):(t<THROW+6?1:0);
    }
    private static void draw(Fx c,CloneClient.Throw d,EffectMesh m,boolean light){
        float t=c.age;var e=target(c);
        double height=e!=null?e.getHeight():1.8,width=e!=null?e.getWidth():.6;
        Vec3d feet=feet(c,d,e),mid=feet.add(0,height*.55,0);
        // ── the "!!!" (or the flash of a guard that blocked it)
        if(e!=null){
            if(d.blocked()&&t>=d.ended&&t<d.ended+6){
                if(light){
                    float s=(t-d.ended)/6;
                    m.softDisc(e.getLerpedPos(c.delta).add(0,height*.6,0),.8+1.6*s,0xd8f0ff,.85f*(1-s));
                    FxLib.ring(m,e.getLerpedPos(c.delta).add(0,.05,0),.6+2.2*s,.07,0xbfe6ff,.8f*(1-s));
                }
            }else{
                float shown=markShown(d,t);
                if(shown>0)SwordDance.bangs(m,c.camera,e.getLerpedPos(c.delta).add(0,height+1.15,0),t-MARK,shown,light);
            }
        }
        doubled(c,d,m,t,height,width,light);
        if(!d.seized())return;
        // ── the throw reaching them: a streak off the double's hand, a ring round them, pixels thrown up off them
        float s=(t-THROW)/20f;
        if(s>=0&&s<.5f){
            Vec3d hand=hand(d,t);float near=near(mid,c.camera);
            if(s<.16f){
                float a=(1-s/.16f)*near;
                Vec3d head=hand.lerp(mid,Math.min(1,s/.05));
                if(light){m.glowLine(hand,head,.22,GLOW,.5f*a);m.softDisc(head,.5,GLOW,.3f*a);}
                else{m.softTaper(hand,head,.05,.11,PURPLE,.9f*a);m.softTaper(hand,head,.015,.04,WHITE,.9f*a);}
            }
            Vec3d under=d.targetFrom.add(0,.06,0);
            float u=s/.4f;
            if(u<1){
                if(light)FxLib.ring(m,under,.5+1.9*Ease.out(u,0,1),.16,GLOW,.4f*(1-u));
                else{FxLib.ring(m,under,.5+1.9*Ease.out(u,0,1),.07,PURPLE,.85f*(1-u));FxLib.ring(m,under,.42+1.5*Ease.out(u,0,1),.03,WHITE,.7f*(1-u));}
            }
            pixels(m,d.targetFrom.add(0,height*.5,0),UP,.75,5.5,.07,16,d.seed+3,s,light,c.camera);
        }
        // ── hanging: pixels rising under them
        if(t>=THROW&&t<FLING+1){
            float on=Ease.smooth(t,THROW,THROW+4)*(1-Ease.smooth(t,FLING-2,FLING+1));
            for(int j=0;j<12&&on>0;j++){
                double turn=(t/20.0)*(1.1+.6*h(j,0,d.seed,31))+h(j,0,d.seed,32),u=turn-Math.floor(turn);
                double angle=h(j,0,d.seed,33)*Math.PI*2+u*1.8,rad=(.25+.5*h(j,0,d.seed,34))*(1-.5*u);
                Vec3d p=d.targetFrom.add(Math.cos(angle)*rad,.05+u*lift(t),Math.sin(angle)*rad);
                float a=on*(float)(Math.min(1,u/.15)*(1-Ease.smooth((float)u,.75f,1)))*near(p,c.camera);if(a<=0)continue;
                var f=facing(c.camera,p);double half=.035+.03*h(j,0,d.seed,35);
                if(light)square(m,p,f,half*1.9,GLOW,.3f*a);
                else{square(m,p,f,half+.012,INK,.9f*a);square(m,p,f,half,j%4==0?WHITE:PURPLE,a);}
            }
            if(light)m.softDisc(d.targetFrom.add(0,.1,0),1.1,GLOW,.12f*on);
        }
        // ── flung: a streak behind them
        float fl=(t-FLING)/20f;
        if(fl>=0&&fl<.34f){
            Vec3d from=d.targetFrom.add(0,LIFT+height*.5,0);float a=(1-Ease.smooth(fl,.14f,.34f))*near(mid,c.camera);
            Vec3d tail=from.lerp(mid,Ease.smooth(fl,.08f,.3f));
            if(light){m.glowLine(tail,mid,.28,GLOW,.34f*a);m.softDisc(mid,.9,GLOW,.2f*a);}
            else{m.softTaper(tail,mid,.03,.17,PURPLE,.8f*a);m.softTaper(tail,mid,.01,.06,WHITE,.85f*a);}
        }
        // ── into the wall: a burst before them, rings on the ground, pixels
        float im=(t-IMPACT)/20f;
        if(d.struck()&&im>=0&&im<.6f){
            Vec3d base=d.wall.add(0,.06,0),chest=d.wall.add(d.f.multiply(SINK)).add(0,STUCK_UP+height*.55,0);
            Vec3d before=chest.subtract(d.f.multiply(.5));float near=near(before,c.camera);
            if(im<.16f){
                float a=(1-im/.16f)*near;
                if(light){
                    m.star(before,.55+2.2*im,WHITE,.8f*a);m.softDisc(before,.9+2*im,LILAC,.4f*a);
                    for(int j=0;j<8;j++){
                        double az=j*Math.PI/4+h(j,0,d.seed,41)*.5;var fc=facing(c.camera,before);
                        Vec3d way=fc[0].multiply(Math.cos(az)).add(fc[1].multiply(Math.sin(az)));
                        m.needle(before.add(way.multiply(.25)),before.add(way.multiply(.9+2.4*Ease.out(im,0,.12f)*(.6+.6*h(j,0,d.seed,42)))),.05,.3,WHITE,.8f*a);
                    }
                }
            }
            for(int k=0;k<2;k++){
                float u=(im-k*.06f)/(.34f+k*.12f);if(u<0||u>=1)continue;
                double r=.7+(2.6+k*1.6)*Ease.out(u,0,1);
                if(light)FxLib.ring(m,base,r,.22,k==0?LILAC:GLOW,.36f*(1-u));
                else FxLib.ring(m,base,r,k==0?.06:.09,k==0?WHITE:PURPLE,.85f*(1-u));
            }
            pixels(m,chest,d.f.negate(),.7,7,.075,22,d.seed+9,im,light,c.camera);
        }
        // ── held in the wall: bars of purple torn across them now and then
        if(d.struck()&&t>=IMPACT&&t<FREE&&e!=null&&e.isAlive()&&!(d.ended()&&t>=d.ended))
            bars(m,mid.subtract(d.f.multiply(.1)),height,width,t,d.seed+17,.45f,light,c.camera);
    }
    /** Where the double's right hand is at {@code t} ticks. */
    private static Vec3d hand(CloneClient.Throw d,float t){
        double[] h=CloneClient.hand(t/20f);
        return d.cloneAt().add(d.r.multiply(h[0])).add(0,h[1],0).add(d.f.multiply(h[2]));
    }
    /** The double's: the X on its face, the bars torn across its body, the pixels as it comes and as it goes. */
    private static void doubled(Fx c,CloneClient.Throw d,EffectMesh m,float t,double height,double width,boolean light){
        var e=target(c);double eyes=e!=null?e.getStandingEyeHeight():height*.9;
        float stopped=d.stopped()?d.ended:Float.NaN;
        var fr=CloneClient.CLIP.frame(Math.max(0,t)/20f);Vec3d feet=CloneClient.cloneFeet(d,fr);
        // its head, as its clip leans it over its middle
        double up=eyes-KagenoClips.PIVOT,lean=Math.toRadians(fr.pitch());
        Vec3d head=feet.add(0,KagenoClips.PIVOT+up*Math.cos(lean),0).add(d.f.multiply(up*Math.sin(lean))),body=feet.add(0,height*.5,0);
        // pixels as it comes, and thrown off it as it breaks up (and at once if it is called off)
        float in=(t-CLONE)/20f;
        if(in>=0&&in<PIXEL_LIFE)for(int k=0;k<3;k++)pixels(m,feet.add(0,height*(.2+.3*k),0),UP,.9,3.2,.06,8,d.seed+50+k,in,light,c.camera);
        float going=Float.isNaN(stopped)?GOING_AT:Math.min(GOING_AT,stopped);
        for(int k=0;k<5;k++){
            float s=(t-going-k*(Float.isNaN(stopped)||stopped>GOING_AT?1.5f:.4f))/20f;
            if(s>=0&&s<PIXEL_LIFE)pixels(m,feet.add(0,height*(.15+.18*k),0),UP,.8,3.6,.07,9,d.seed+60+k,s,light,c.camera);
        }
        if(!CloneClient.cloneShown(d,t))return;
        // the X on its face (the jab's own pixels), come with it
        Vec3d at=CorruptionJab.before(CorruptionJab.face(head.subtract(0,eyes,0),eyes),width*.5,c.camera);
        float near=near(at,c.camera);
        if(near>0){
            var f=facing(c.camera,at);float clock=t/20f;
            double turn=Math.toRadians(CorruptionJab.tip(clock,d.seed)),cs=Math.cos(turn),sn=Math.sin(turn);
            var x=new CorruptionJab.Shown(at,f[0].multiply(cs).add(f[1].multiply(sn)),f[1].multiply(cs).subtract(f[0].multiply(sn)),
                CorruptionJab.CELL*(1+.6*(1-Ease.smooth(t,CLONE,CLONE+3))),near,clock,d.seed);
            if(light)CorruptionJab.additive(m,x);else CorruptionJab.alpha(m,x);
        }
        bars(m,body,height,width,t,d.seed,1,light,c.camera);
        if(light)m.softDisc(body,1.3,GLOW,.1f);
    }
    /** The double begins to break up at this tick (CloneClient.GOING). */
    static final float GOING_AT=CloneClient.GOING;
    /** How many bars are torn across a body at once at most, and how often they change (a second). */
    static final int BARS=4;static final float BAR_RATE=14;
    /**
     * Bar {@code j} across a body {@code height} high and {@code width} wide at {@code t} ticks: {how high its middle is
     * (of the height), how far to the side (blocks), its half width, its half height, its colour}, or null while it does
     * not show ({@code often} of the time it does).
     */
    static double[] tear(int j,int seed,float t,double height,double width,float often){
        int n=(int)Math.floor(t/20f*BAR_RATE);
        if(h(n,j,seed,71)>=.55*often)return null;
        double c=h(n,j,seed,75);
        return new double[]{.12+.8*h(n,j,seed,72),(h(n,j,seed,73)-.5)*width*.9,width*(.35+.5*h(n,j,seed,74)),.025+.035*h(n,j,seed,76),c<.6?PURPLE:c<.8?DEEP:c<.92?WHITE:INK};
    }
    private static void bars(EffectMesh m,Vec3d body,double height,double width,float t,int seed,float often,boolean light,Vec3d camera){
        var f=facing(camera,body);float near=near(body,camera);if(near<=0)return;
        for(int j=0;j<BARS;j++){
            double[] b=tear(j,seed,t,height,width,often);if(b==null)continue;
            Vec3d p=body.add(0,(b[0]-.5)*height,0).add(f[0].multiply(b[1])).add(f[2].multiply(width*.5+.12));
            int rgb=(int)b[4];
            if(light){if(rgb!=INK)bar(m,p,f,b[2]*1.15,b[3]*2.4,GLOW,.22f*near);}
            else{bar(m,p,f,b[2]+.015,b[3]+.015,INK,.85f*near);bar(m,p,f,b[2],b[3],rgb,near);}
        }
    }

    // ───────────────────────────── the wall ─────────────────────────────

    /** How many spikes the wall has: its fan behind the one held in it, and the shorter ones to either side before it. */
    static final int FAN=9,SIDES=6,SPIKES=FAN+SIDES;
    /** It begins to come up at this tick (as they are flung), each spike up in this many ticks; let go, it sinks back over this many. */
    static final float RISE=FLING-2.5f,RISE_TICKS=3.2f,SINK_TICKS=8;
    /** How far under the ground a spike's foot is (blocks). */
    static final double BURIED=.4;
    /**
     * Spike {@code k} of the wall: {how far to the right of its middle its foot is, how far behind it (along the way
     * they are thrown), how far over to the right it leans from upright (degrees), how far it leans away along the way
     * (degrees), its length (blocks, out of the ground), its half width, how many ticks after the first it comes up}.
     * The fan: nine, the tallest upright in the middle, the rest splayed out to both sides, shorter and thinner outward.
     * Then six shorter ones in two clusters to either side before it, leaning back toward its user: the one thrown is
     * between them.
     */
    static double[] spike(int k,int seed){
        if(k<FAN){
            double a=-64+128.0*k/(FAN-1)+(h(k,0,seed,81)-.5)*9,out=Math.abs(a)/64;
            return new double[]{1.3*Math.sin(Math.toRadians(a))+(h(k,0,seed,82)-.5)*.2,.1+(h(k,0,seed,83)-.5)*.5,a,5+11*h(k,0,seed,84),
                4.3-2.3*Math.pow(out,1.2)+.5*h(k,0,seed,85),.47-.17*out,Math.abs(k-(FAN-1)*.5)*.3};
        }
        int j=k-FAN,side=j<SIDES/2?-1:1,i=j%(SIDES/2);
        double a=side*(34+22*i+(h(k,0,seed,81)-.5)*8);
        return new double[]{side*(.8+.38*i+.15*h(k,0,seed,82)),-.9-.3*h(k,0,seed,83)+.2*i,a,-(8+10*h(k,0,seed,84)),1.9-.4*i+.4*h(k,0,seed,85),.3-.04*i,1+i*.5};
    }
    /** How much of its length spike {@code k} shows at {@code t} ticks (0 none, 1 all; a little over as it comes up): up from RISE, jolted as they go into it, down again from {@code down} (when it lets them go, or when it was called off). */
    static double grown(double[] sp,float t,float down){
        float u=(float)((t-RISE-sp[6])/RISE_TICKS);if(u<=0)return 0;
        double up=u>=1?1:1+2.2*Math.pow(u-1,3)+1.2*Math.pow(u-1,2);
        float s=(t-IMPACT)/20f;
        if(s>=0&&s<.5f)up*=1+.05*Math.exp(-s/.09)*Math.cos(s*52);
        if(t>down)up*=1-Ease.smooth((float)(t-down-sp[6]*.6f),0,SINK_TICKS);
        return Math.max(0,up);
    }
    /** When the wall goes down again: as it lets them go, or as it was called off before. */
    private static float down(CloneClient.Throw d){return d.ended()&&d.ended<FREE?d.ended:FREE;}
    /** Pieces of the ground thrown as they go into the wall, and falling off it as it goes down. */
    static final int THROWN_PIECES=18,FALLEN_PIECES=12;
    @Override public void solids(Fx c,FxSolids so){
        var d=of(c);if(d==null||!d.seized())return;
        float t=c.age,down=down(d);if(t<RISE||t>down+SINK_TICKS+50)return;
        Vec3d base=d.wallBase(),up=Fx.UP;
        BlockState ground=so.ground(base.add(0,.3,0));
        if(ground==null)ground=so.ground(d.wall.add(0,.3,0));
        if(ground==null)ground=net.minecraft.block.Blocks.STONE.getDefaultState();
        BlockPos lit=BlockPos.ofFloored(base.x,d.wall.y+1,base.z);
        for(int k=0;k<SPIKES;k++){
            double[] sp=spike(k,d.seed);double g=grown(sp,t,down);if(g<=.01)continue;
            double a=Math.toRadians(sp[2]),lean=Math.toRadians(sp[3]);
            Vec3d axis=up.multiply(Math.cos(a)*Math.cos(lean)).add(d.r.multiply(Math.sin(a)*Math.cos(lean))).add(d.f.multiply(Math.sin(lean))).normalize();
            // (a corner toward its user, so two of its faces show from there)
            Vec3d side=d.f.subtract(axis.multiply(d.f.dotProduct(axis)));
            Vec3d turned=side.normalize().multiply(Math.cos(.5+k)).add(axis.crossProduct(side).normalize().multiply(Math.sin(.5+k)));
            Vec3d foot=new Vec3d(base.x,d.wall.y,base.z).add(d.r.multiply(sp[0])).add(d.f.multiply(sp[1])).subtract(axis.multiply(BURIED));
            so.spike(ground,lit,foot,axis,turned,(sp[4]+BURIED)*g,sp[5]*Math.min(1,.5+.5*g));
        }
        // pieces of the ground thrown back toward its user as they go in
        float im=(t-IMPACT)/20f;
        if(d.struck()&&im>=0&&im<2.2f)for(int j=0;j<THROWN_PIECES;j++){
            double az=(h(j,0,d.seed,91)-.5)*2.4,speed=3+5*h(j,0,d.seed,92);
            Vec3d v=d.f.multiply(-Math.cos(az)*speed).add(d.r.multiply(Math.sin(az)*speed)).add(0,3.5+5.5*h(j,0,d.seed,93),0);
            double size=(.12+.2*h(j,0,d.seed,94))*(1-Ease.smooth(im,1.5f,2.2f));
            Vec3d at=FxSolids.flight(d.wall.add(d.r.multiply((h(j,0,d.seed,95)-.5)*1.6)).add(0,.4+1.2*h(j,0,d.seed,96),0),v,d.wall.y+size*.5,im);
            so.chunk(ground,at,size,(float)(im*300*(h(j,0,d.seed,97)-.5)),(float)(im*260*h(j,0,d.seed,98)),(float)(j*40));
        }
        // and off the spikes as they go down
        float fall=(t-down)/20f;
        if(fall>=0&&fall<1.6f)for(int j=0;j<FALLEN_PIECES;j++){
            double[] sp=spike(j%FAN,d.seed);double a=Math.toRadians(sp[2]);
            Vec3d from=new Vec3d(base.x,d.wall.y,base.z).add(d.r.multiply(sp[0]+Math.sin(a)*sp[4]*.6)).add(0,Math.cos(a)*sp[4]*(.3+.5*h(j,0,d.seed,99)),0).add(d.f.multiply(sp[1]));
            Vec3d v=d.r.multiply((h(j,0,d.seed,101)-.5)*3).add(d.f.multiply(-1.5*h(j,0,d.seed,102))).add(0,1+2*h(j,0,d.seed,103),0);
            double size=(.14+.16*h(j,0,d.seed,104))*(1-Ease.smooth(fall,1f,1.6f));
            float born=fall-.02f*j;if(born<0)continue;
            so.chunk(ground,FxSolids.flight(from,v,d.wall.y+size*.5,born),size,(float)(born*220*(h(j,0,d.seed,105)-.5)),(float)(born*200),(float)(j*31));
        }
    }
    /** Dust thrown up as the wall comes out of the ground, as they go into it and as it goes down (the effect layer's anime dust). */
    public static void dust(Matrix4f matrix,Vec3d camera){
        if(CloneClient.all().isEmpty()||!FxLayer.available()||!KagenoFire.ready())return;
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;float delta=mc.getTickDelta();
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        for(var d:CloneClient.all()){
            if(!d.seized())continue;
            float t=d.age(delta),sec=t/20f;if(t<RISE||t>END+40)continue;
            Vec3d base=new Vec3d(d.wallBase().x,d.wall.y,d.wallBase().z);
            if(base.squaredDistanceTo(camera)>96*96)continue;
            FxLib.dustRing(base,sec,RISE/20f,.5,2.4,12,.85,dust,puffs,d.seed+61);
            if(d.struck())FxLib.dustRing(d.wall,sec,IMPACT/20f,.4,3.4,16,1.05,dust,puffs,d.seed+67);
            FxLib.dustRing(base,sec,down(d)/20f+.1f,.4,2.6,12,.9,dust,puffs,d.seed+71);
        }
        if(!dust.isEmpty())KagenoFire.dust(matrix,camera,dust,puffs,(float)(mc.world.getTime()+delta));
    }
    @Override public void frame(Fx c,FxFrame f){
        var d=of(c);if(d==null)return;float t=c.age;
        if(CloneClient.cloneShown(d,t))f.light(d.cloneAt().add(0,1,0),GLOW,3.5,.35f);
        if(!d.seized())return;
        float s=(t-THROW)/20f;if(s>=0&&s<.4f)f.light(d.targetFrom.add(0,1,0),GLOW,5,.7f*(1-s/.4f));
        // (flung fast: a short afterglow, as the effects' other fast things)
        if(t>=FLING-1&&t<IMPACT+3)f.afterglow(.1f);
        float im=(t-IMPACT)/20f;
        if(d.struck()&&im>=0&&im<.5f)f.light(d.wall.add(0,1.2,0),0xd9c2ff,11,1.1f*(1-im/.5f));
    }
    /** A shake as they go into the wall, and a little as they are seized. */
    @Override public float shake(StandClient.Cast cast,float sec){
        var d=CloneClient.ofCast(cast);if(d==null||!d.seized())return 0;
        float v=.3f*KnifeCombo.bump(sec,THROW/20f,.06f);
        if(d.struck())v=Math.max(v,1.5f*KnifeCombo.bump(sec,IMPACT/20f,.09f));
        return v;
    }
}
