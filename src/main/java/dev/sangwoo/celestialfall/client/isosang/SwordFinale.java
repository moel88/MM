package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * Lee So-sang's H again as drawn (2.101.0, user request: "이번에는 H를 사용한 상태에서 H를 한 번 더 누르면 되는 스킬 음성은
 * 저렇게 3개고 랜덤으로 하나 나오면 돼 스킬은 카메라가 플레이어로 빛추고 H 영역에 있던 모든 검이 전부 플레이어 뒤로 가고 앞으로
 * 꽂는 스킬이야 이걸 쓰면 H가 해제돼", the user's three videos of it read on a sheet and confirmed: "맞음, 이대로"; the cutscene
 * hers alone: "시전자만"):
 * <ul>
 * <li>a blue flash, a ring of blue light at her feet, and her own camera into its cutscene: the world goes dark under a
 * red dusk ({@link #ground}, IsosangCinema.dusk);</li>
 * <li>she leaps high off the pommel (IsosangRules.finaleDrawn) and every sword of her formation is pulled up out of the
 * ground, nearest first, and flies up round her trailing white light, swirling round her as she rises, swords of light
 * joining them out of bursts of blue ({@link #LIGHT}: as many again, the videos' swarm);</li>
 * <li>she comes down at the camera onto the ground where she stood, a great arc of blue light behind her; the swords
 * gather behind her back and spread there like wings, points out ({@link #fan}); a white ring bursts round her; she
 * raises two fingers before her face, her eyes alight;</li>
 * <li>her camera cuts round behind her as the swords fly from behind her all at once, long white streaks over her, and
 * come down slanting into the ground in a wide band ahead of her, nearest first, blue crystal flaring up where each
 * strikes, a splash of ink at its foot; the band flashes blue ({@link IsosangRules#FINALE_BURST});</li>
 * <li>her camera comes back to her; the swords stand a moment more and are gone one by one in light.</li>
 * </ul>
 * The formation's own swords are its swords: each is the formation's until it is pulled out (SwordFormation.pulled),
 * this one's from then, from the very place and pose it stood in. Every effect is a function of the swing's clock and
 * of where she stood.
 */
final class SwordFinale {
    private SwordFinale(){}
    /** How long it is drawn (seconds, IsosangClient.life): until the last of its swords' light is gone. */
    static final float LIFE=5.2f;
    /** How long her body takes to turn from her formation's way round to the band's (seconds, IsosangClient.finaleFacing). */
    static final float TURN=.14f;

    // ───────────────────────────── the body ─────────────────────────────
    // (the videos: on the pommel a dip, then up into the air, arms spread wide as the swords fly up round her; down at the
    // camera, a crouch as she lands, up tall with her arms open low; her right hand up before her face, two fingers raised
    // (no sword in it: hers went into the light with the rest); the hand thrown forward as they fly; watching them come
    // down; her sword back in her hand as her camera comes back to her)
    static final R6.Pose F_DIP=w(.03,-.1,-2,6,0,d(-50,40),d(-10,70),d(-62,-30),d(-80,6),d(-86,-6));
    static final R6.Pose F_SPRING=w(.03,0,-2,-4,0,d(-25,62),d(-55,90),d(-25,-62),d(-70,10),d(-100,-6));
    static final R6.Pose F_AIR=w(.02,.05,0,-10,0,d(20,95),d(-10,110),d(20,-95),d(-62,10),d(-100,-10));
    static final R6.Pose F_AIR2=w(.02,.05,0,-6,0,d(28,92),d(0,105),d(28,-92),d(-66,10),d(-98,-10));
    static final R6.Pose F_DESCEND=w(.03,0,0,8,0,d(-20,140),d(-40,150),d(-20,-140),d(-72,8),d(-98,-6));
    static final R6.Pose F_LAND=w(.06,-.28,0,14,0,d(-35,120),d(-45,140),d(-35,-120),22,-158);
    static final R6.Pose F_RISE=w(.02,0,0,-3,0,d(-45,55),d(-60,70),d(-45,-55),d(-90,4),d(-90,-4));
    static final R6.Pose F_SEAL=w(.01,0,4,-2,0,d(30,-50),d(80,-40),d(-55,-50),d(-90,4),d(-90,-4));
    static final R6.Pose F_SEAL2=w(.01,0,5,-3,0,d(33,-48),d(80,-40),d(-57,-52),d(-90,4),d(-90,-4));
    static final R6.Pose F_SEND=w(.04,0,-6,6,0,d(8,8),d(10,6),d(-60,-40),d(-82,10),d(-96,-8));
    static final R6.Pose F_WATCH=w(.03,0,-4,4,0,d(-6,10),d(4,8),d(-62,-40),d(-84,8),d(-94,-8));
    static final R6.Pose F_WATCH2=w(.02,0,-2,2,0,d(-28,14),d(-10,10),d(-68,-30),d(-88,5),d(-92,-5));
    static final R6.Pose F_LOWER=w(.01,0,0,1,0,d(-65,10),d(25,4),d(-80,-10),d(-90,3),d(-90,-3));
    static final Anim CLIP=Anim.joints(
        k(0,SwordFormation.H_ON),
        k(.08,F_DIP,Anim.Ease.OUT),
        k(FINALE_LEAP,F_SPRING),
        k(.42,F_AIR,Anim.Ease.OUT),
        k(.88,F_AIR2),
        k(1.02,F_DESCEND),
        k(FINALE_LAND,F_LAND,Anim.Ease.OUT),
        k(1.5,F_RISE),
        k(1.72,F_SEAL,Anim.Ease.OUT),
        k(2.2,F_SEAL2),
        k(2.36,F_SEND,Anim.Ease.OUT),
        k(2.75,F_WATCH),
        k(3.5,F_WATCH2),
        k(3.85,F_LOWER),
        k(FINALE_LENGTH/20f,STANCE)).after(IsosangSlash::grounded);

    /** Whether {@code e} holds nothing now (her sword gone into the light with the rest, from her leap until her camera comes back to her). */
    static boolean emptyHanded(Entity e){
        if(e==null)return false;
        var c=IsosangClient.finaleOf(e.getUuid());if(c==null)return false;
        float t=c.age(MinecraftClient.getInstance().getTickDelta())/20f;
        return t>=HAND_GONE&&t<HAND_BACK;
    }
    /** How far her right hand's two fingers are raised before her face (0 to 1, GGesture.applyFinale): the videos' seal. */
    static float sealWeight(float t){return Ease.window(t,FINALE_SEAL0-.08f,FINALE_SEAL1+.12f,.1f,.12f);}
    /** When her own sword goes into light as she leaps, and comes back to her hand (seconds). */
    static final float HAND_GONE=FINALE_LEAP-.02f,HAND_BACK=FINALE_BACK0;

    // ───────────────────────────── where it is ─────────────────────────────

    /** Its frame: where she stands (on the formation's ground), the band's way, its right, and how high the pommel she stood on is. */
    record Frame(Vec3d spot,Vec3d f,Vec3d r,double pommel){
        Vec3d at(double fw,double up,double rt){return spot.add(f.multiply(fw)).add(0,up,0).add(r.multiply(rt));}
    }
    static Frame frame(StandClient.Cast c){return frame(c,IsosangClient.finale(c.id()));}
    /** The same for the run {@code run} (the server's word of it: where she stands, the sword she stood on). */
    static Frame frame(StandClient.Cast c,IsosangClient.FinaleRun run){
        Vec3d f=Vec3d.fromPolar(0,c.yaw());
        return new Frame(run==null?c.origin():run.spot(),f,new Vec3d(-f.z,0,f.x),pommel(run));
    }
    /** How high over its ground the pommel she stood on stands (blocks: that sword's, SwordFormation.layout). */
    static double pommel(IsosangClient.FinaleRun run){
        if(run==null)return FORMATION_STAND;
        var l=SwordFormation.layout(run.form());return run.on()<l.length?l[run.on()].height():FORMATION_STAND;
    }
    /** Where her chest is drawn {@code t} seconds in (her body up her leap, IsosangRules.finaleDrawn). */
    static Vec3d chest(Frame fr,float t){return fr.spot.add(0,finaleDrawn(t,fr.pommel)+1.2,0);}
    /** The middle of the band its swords come down in (where its sound is heard from for those away from her). */
    static Vec3d bandMiddle(StandClient.Cast c){var fr=frame(c);return fr.at((FINALE_NEAR+FINALE_REACH)/2,0,0);}

    // ───────────────────────────── the swords ─────────────────────────────

    /**
     * How many swords it has: the formation's own (each pulled out of the ground) and as many of light again (2.101.0, my
     * choice: the videos' swarm is twice as thick as the formation's swords; they join the swirl out of bursts of blue).
     */
    static final int OWN=FORMATION_SWORDS,LIGHT=FORMATION_SWORDS,SWORDS=OWN+LIGHT;
    /**
     * One sword's way: its own (k &lt; OWN, the formation's sword k: where its pommel stood, its way down, its guard's turn)
     * or of light; when it is pulled out (or shows out of the light); its swirl round her (how far out, how high over her
     * chest, its angle at the start, its speed round); when it goes to its place in the fan behind her and where that is
     * (its middle and the way it points); when it is launched and where it comes down (the middle of it standing in the
     * ground slanting and the way it points); when it strikes and when it is gone; its seed.
     */
    record Plan(int k,boolean own,Vec3d planted,Vec3d down,double spin,float start,float join,double orbitR,double orbitUp,double orbitA,double orbitW,
                float gather,Vec3d fanMid,Vec3d fanDir,float launch,Vec3d foot,Vec3d stuckMid,Vec3d stuckDir,float plant,float vanish,int seed){}
    private static final Map<UUID,Plan[]> PLANS=new LinkedHashMap<>();
    static void clear(){PLANS.clear();}
    /** How long a sword pulled out rises straight up out of the ground, and how long it takes to join the swirl (seconds). */
    static final float RISE=.12f,JOIN=.3f,JOIN_PER=.035f,GATHER=.34f;
    /**
     * The fan behind her back (the videos: the swords spread behind her like wings, points out): its hub at her upper back,
     * rings of swords out round it from her left low over the top to her right low, each a little further back than the
     * last, leaning a little back.
     */
    static final double HUB_UP=1.3,HUB_BACK=.5,FAN_FROM=-84,FAN_TO=84,RING0=1.15,RING_STEP=.78;
    static final int RINGS=4,PER_RING=SWORDS/RINGS;
    /**
     * The band's swords (blocks from where she stands): from {@link #BAND_NEAR} out to {@link #BAND_FAR}, {@link #BAND_HALF}
     * either side (inside the band that strikes, IsosangRules.finaleIn), slanting {@link #SLANT0} to SLANT0+SLANT degrees off
     * upright, their hilts toward her, {@link #BURY} blocks of them in the ground.
     */
    static final double BAND_NEAR=2.6,BAND_FAR=14.8,BAND_HALF=7.6,SLANT0=28,SLANT=18,BURY=.55;
    /** Each sword's slot in the fan: its own swords on the even rings' slots, the light's between them. */
    static int slot(int j){return j<OWN?2*j:2*(j-OWN)+1;}
    /** A slot's angle round the fan (radians, 0 straight up, + to her right) and ring. */
    static double slotAngle(int s){int q=s%RINGS,i=s/RINGS;return Math.toRadians(FAN_FROM+(FAN_TO-FAN_FROM)*(i+(q%2==1?.5:0))/(PER_RING-.5));}
    static int ring(int s){return s%RINGS;}
    /** The ways of all of its swords (computed once for the swing: where its formation's swords stood, where the band's ground is). */
    static Plan[] plans(StandClient.Cast c){
        var p=PLANS.get(c.id());if(p!=null)return p;
        var run=IsosangClient.finale(c.id());if(run==null)return null;
        p=plans(c,run,MinecraftClient.getInstance());
        PLANS.put(c.id(),p);while(PLANS.size()>16)PLANS.remove(PLANS.keySet().iterator().next());
        return p;
    }
    /** The same for the run {@code run}, the band's ground looked for in {@code mc}'s world (level with where she stands with none). */
    static Plan[] plans(StandClient.Cast c,IsosangClient.FinaleRun run,MinecraftClient mc){
        Frame fr=frame(c,run);
        var layout=SwordFormation.layout(run.form());
        float formT=(c.start()-run.formStart())/20f;
        // the formation's own swords pulled out nearest her first (the one she stood on as she leaps off it)
        Integer[] near=new Integer[OWN];
        for(int k=0;k<OWN;k++)near[k]=k;
        Arrays.sort(near,Comparator.comparingDouble((Integer k)->SwordFormation.foot(run.centre(),run.formYaw(),layout[k]).squaredDistanceTo(fr.spot)));
        float[] pulls=new float[OWN];
        for(int r=0;r<OWN;r++){int k=near[r];pulls[k]=k==run.on()?FINALE_LEAP+.02f:PULL0+PULL_SPREAD*r/(OWN-1f)+.01f*(float)Ease.hash(k*31+5);}
        Vec3d hub=fr.at(-HUB_BACK,HUB_UP,0);
        Plan[] out=new Plan[SWORDS];
        for(int j=0;j<SWORDS;j++){
            int seed=j*977+run.form().hashCode()*31+13;boolean own=j<OWN;
            Vec3d planted=null,down=new Vec3d(0,-1,0);double spin=360*Ease.hash(seed+1);float start,join=JOIN;
            if(own){
                var b=layout[j];planted=SwordFormation.planted(run.centre(),run.formYaw(),b);down=SwordFormation.down(b);
                spin=SwordFormation.spinAt(b,formT+pulls[j]);start=pulls[j];
                // (the farther it stood from her, the longer it takes to fly up into the swirl round her)
                join=JOIN+JOIN_PER*(float)SwordFormation.foot(run.centre(),run.formYaw(),b).distanceTo(fr.spot);
            }else start=BORN0+BORN_SPREAD*(j-OWN)/(LIGHT-1f)+.03f*(float)Ease.hash(seed+2);
            double orbitR=own?1.8+2.4*Ease.hash(seed+3):2.4+2.6*Ease.hash(seed+3),orbitUp=-1.4+3.4*Ease.hash(seed+4);
            double orbitA=Math.PI*2*Ease.hash(seed+5),orbitW=5+2*Ease.hash(seed+6);
            // (it goes on into the fan once it has joined the swirl: never off its way into it)
            float gather=Math.max(GATHER0+GATHER_SPREAD*(float)Ease.hash(seed+7),start+(own?RISE+join+.04f:.15f));
            int s=slot(j);double phi=slotAngle(s),rho=RING0+RING_STEP*ring(s);
            Vec3d radial=fr.r.multiply(Math.sin(phi)).add(0,Math.cos(phi),0);
            Vec3d fanDir=radial.subtract(fr.f.multiply(.2)).normalize();
            Vec3d fanPommel=hub.add(radial.multiply(rho)).subtract(fr.f.multiply(.12+.16*rho));
            Vec3d fanMid=fanPommel.add(fanDir.multiply(SwordFormation.S_LEN/2));
            // where it comes down: across the band as it stood across the fan, its ring how far out
            double side=BAND_HALF*Math.sin(phi)*(.8+.2*Ease.hash(seed+8))+(Ease.hash(seed+9)-.5)*.9;
            double fw=BAND_NEAR+(BAND_FAR-BAND_NEAR)*((ring(s)+.15+.7*Ease.hash(seed+10))/RINGS);
            Vec3d foot=ground(mc,fr.at(fw,0,side),fr.spot.y);
            double slant=Math.toRadians(SLANT0+SLANT*Ease.hash(seed+11));
            Vec3d stuckDir=fr.f.multiply(Math.sin(slant)).add(fr.r.multiply(side/BAND_HALF*.25)).add(0,-Math.cos(slant),0).normalize();
            Vec3d point=foot.add(stuckDir.multiply(BURY)),stuckMid=point.subtract(stuckDir.multiply(SwordFormation.S_LEN/2));
            float plant=Math.min(FINALE_PLANT1,FINALE_PLANT0+(FINALE_PLANT1-FINALE_PLANT0-.04f)*(float)((fw-BAND_NEAR)/(BAND_FAR-BAND_NEAR))+.04f*(float)Ease.hash(seed+12));
            float launch=Math.min(plant-.2f,FINALE_LAUNCH-.02f+.1f*(float)Ease.hash(seed+13)+.02f*ring(s));
            float vanish=FINALE_GONE0+(FINALE_GONE1-FINALE_GONE0-.2f)*(float)Ease.hash(seed+14);
            out[j]=new Plan(j,own,planted,down,spin,start,join,orbitR,orbitUp,orbitA,orbitW,gather,fanMid,fanDir,launch,foot,stuckMid,stuckDir,plant,vanish,seed);
        }
        return out;
    }
    /** When the formation's swords are pulled out (seconds: the nearest as she leaps, the farthest by PULL0+PULL_SPREAD), and when the swords of light show. */
    static final float PULL0=.24f,PULL_SPREAD=.42f,BORN0=.34f,BORN_SPREAD=.5f,GATHER0=.98f,GATHER_SPREAD=.28f;
    /** When the formation's sword {@code k} is pulled out of the ground by H again {@code fin} (seconds into it; never for none). */
    static float pullAt(StandClient.Cast fin,int k){
        var p=plans(fin);return p==null||k<0||k>=OWN?Float.MAX_VALUE:p[k].start();
    }
    /** The ground under {@code at} (looked for 3 blocks over it to 4 under it in the client's world), else {@code at} at {@code floor}. */
    static Vec3d ground(MinecraftClient mc,Vec3d at,double floor){
        Vec3d level=new Vec3d(at.x,floor,at.z);
        if(mc==null||mc.world==null)return level;
        var hit=mc.world.raycast(new RaycastContext(level.add(0,3,0),level.add(0,-4,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        return hit.getType()==HitResult.Type.MISS?level:hit.getPos();
    }

    /** A sword as it is drawn: its middle, the way it points (pommel to point), its guard's turn, how strongly it shows, whether it flies (its light streaks behind it). */
    record Blade(Vec3d mid,Vec3d dir,double spin,float alpha,boolean flying){
        Vec3d pommel(){return mid.subtract(dir.multiply(SwordFormation.S_LEN/2));}
        Vec3d point(){return mid.add(dir.multiply(SwordFormation.S_LEN/2));}
    }
    /** Where sword {@code p} swirls round her {@code t} seconds in (its middle): round her chest as she rises and comes down. */
    static Vec3d orbit(Frame fr,Plan p,float t){
        // (round her as she rises and hangs; as she comes down they stay up there, swirling, until they gather behind her)
        double a=p.orbitA()+p.orbitW()*t;Vec3d c=chest(fr,Math.min(t,FINALE_DESCEND));
        return c.add(Math.cos(a)*p.orbitR(),p.orbitUp(),Math.sin(a)*p.orbitR());
    }
    /** The way a swirling sword points {@code t} seconds in: round her the way it goes, tilted with her rise and fall. */
    static Vec3d swirl(Frame fr,Plan p,float t){
        double vy=t>=FINALE_DESCEND?0:(finaleDrawn(Math.min(t+.01,FINALE_DESCEND),fr.pommel)-finaleDrawn(t-.01,fr.pommel))/.02;
        return round(p,t).add(0,.6*vy,0).normalize();
    }
    /** Its way round her (blocks a second, level: the swirl's own, not her rise or fall with it). */
    static Vec3d round(Plan p,float t){double a=p.orbitA()+p.orbitW()*t,v=p.orbitR()*p.orbitW();return new Vec3d(-Math.sin(a)*v,0,Math.cos(a)*v);}
    /** Its middle {@code t} seconds in (null when it is not drawn), before the way it points is found from how it moves. */
    static Vec3d mid(Frame fr,Plan p,float t){
        if(t<p.start()||t>=p.vanish()+FADE)return null;
        if(t>=p.plant())return p.stuckMid();
        if(t>=p.launch()){
            float x=Ease.unit(t,p.launch(),p.plant());double u=Math.pow(x,1.3);
            // (2.101.0: the videos' swords go straight on ahead of her in long streaks, not up and over: out of the fan
            // forward, a little up, then in along the way it will stand)
            Vec3d a=fanAt(p,p.launch()),b=a.add(0,LIFT_UP,0).add(fr.f.multiply(LIFT_ON)),d=p.stuckMid(),cc=d.subtract(p.stuckDir().multiply(APPROACH));
            return bezier(a,b,cc,d,u);
        }
        float fanned=p.gather()+GATHER;
        if(t>=fanned)return fanAt(p,t);
        if(t>=p.gather()){
            float u=Ease.smooth(t,p.gather(),fanned);
            // (on round from the swirl, not down with her as she comes down; out into its place point first: it comes in
            // along the way it will point)
            Vec3d a=orbit(fr,p,p.gather()),v=round(p,p.gather());
            return bezier(a,a.add(v.multiply(.06)),p.fanMid().subtract(p.fanDir().multiply(1.2)),fanAt(p,fanned),u);
        }
        if(!p.own())return orbit(fr,p,t);
        Vec3d stood=p.planted().add(p.down().multiply(SwordFormation.S_LEN/2)),top=stood.add(0,RISE_UP,0);
        if(t<p.start()+RISE)return stood.add(0,RISE_UP*Ease.out(t,p.start(),p.start()+RISE),0);
        float v=Ease.smooth(t,p.start()+RISE,p.start()+RISE+p.join());
        if(v>=1)return orbit(fr,p,t);
        // (up out of the ground and round into the swirl: it comes in along the way it will go round)
        Vec3d o=orbit(fr,p,t),go=round(p,t);
        return bezier(top,top.add(0,2.2,0).add(o.subtract(top).multiply(1,0,1).multiply(.25)),o.subtract(go.multiply(.1)),o,v);
    }
    /** How high a sword pulled out rises straight up before it flies (blocks), and its flight's start out of the fan (up a little and on ahead), and how far up its way down it comes in from. */
    static final double RISE_UP=1.5,LIFT_UP=.5,LIFT_ON=3.2,APPROACH=2.6;
    /** How long a sword takes to go (seconds). */
    static final float FADE=.22f;
    /** Its place in the fan, breathing a little. */
    static Vec3d fanAt(Plan p,float t){
        double s=p.seed();return p.fanMid().add(0,.05*Math.sin(t*2.3+s),0).add(p.fanDir().multiply(.04*Math.sin(t*3.1+s*.7)));
    }
    static Vec3d bezier(Vec3d a,Vec3d b,Vec3d c,Vec3d d,double u){
        double v=1-u;return a.multiply(v*v*v).add(b.multiply(3*v*v*u)).add(c.multiply(3*v*u*u)).add(d.multiply(u*u*u));
    }
    /** Sword {@code p} {@code t} seconds in (null when it is not drawn). */
    static Blade blade(Frame fr,Plan p,float t){
        Vec3d m=mid(fr,p,t);if(m==null)return null;
        float alpha=(p.own()?1:Ease.smooth(t,p.start(),p.start()+.1f))*(1-Ease.smooth(t,p.vanish(),p.vanish()+FADE));
        double spin=p.spin()+(t<p.launch()?SPIN*Math.max(0,t-p.start()-RISE):SPIN*Math.max(0,p.launch()-p.start()-RISE));
        // stuck: slanting into the ground, quivering a moment as it strikes
        if(t>=p.plant()){
            float s=t-p.plant();double q=Math.toRadians(QUIVER)*Math.sin(s*42)*Math.exp(-s/.12);
            Vec3d side=p.stuckDir().crossProduct(UP);side=side.lengthSquared()<1e-6?new Vec3d(1,0,0):side.normalize();
            Vec3d d=p.stuckDir().multiply(Math.cos(q)).add(side.crossProduct(p.stuckDir()).multiply(Math.sin(q))).normalize();
            Vec3d point=p.stuckMid().add(p.stuckDir().multiply(SwordFormation.S_LEN/2));
            return new Blade(point.subtract(d.multiply(SwordFormation.S_LEN/2)),d,spin,alpha,false);
        }
        // pulled out: pointing down as it stood, until it turns up as it rises
        if(p.own()&&t<p.start()+RISE*.5f)return new Blade(m,p.down(),spin,alpha,false);
        // in the fan: pointing out, as the wings' feathers
        float fanned=p.gather()+GATHER;
        if(t>=fanned&&t<p.launch())return new Blade(m,p.fanDir(),spin,alpha,false);
        // flying: launched, along its way (turned in from pointing out of the fan); swirling, round her the way it goes
        // round (her rise and fall with it); pulled out, turned up from pointing down as it rises, then round into the
        // swirl; gathering, turned from its way round her out to the way it points in the fan (each turned smoothly,
        // never flipping round where its way bends tight)
        Vec3d d;
        if(t>=p.launch()){
            Vec3d a=mid(fr,p,Math.max(p.launch(),t-WAY)),b=mid(fr,p,Math.min(p.plant(),t+WAY));
            Vec3d way=a==null||b==null||b.squaredDistanceTo(a)<1e-8?p.fanDir():b.subtract(a).normalize();
            d=slerp(p.fanDir(),way,Ease.smooth(t,p.launch(),p.launch()+Math.min(.1f,(p.plant()-p.launch())*.35f)));
        }else if(t>=p.gather())d=slerp(swirl(fr,p,p.gather()),p.fanDir(),Ease.smooth(t,p.gather(),fanned));
        else if(p.own()&&t<p.start()+RISE+p.join()){
            Vec3d upward=slerp(p.down(),UP,Ease.smooth(t,p.start()+RISE*.5f,p.start()+RISE+.14f));
            d=slerp(upward,swirl(fr,p,t),Ease.smooth(t,p.start()+RISE+p.join()*.3f,p.start()+RISE+p.join()));
        }else d=swirl(fr,p,t);
        return new Blade(m,d,spin,alpha,true);
    }
    static final double SPIN=140,QUIVER=6;
    static final float WAY=.03f;
    static final Vec3d UP=new Vec3d(0,1,0);
    static Vec3d slerp(Vec3d a,Vec3d b,double u){
        if(u<=0)return a;if(u>=1)return b;
        double dot=MathHelper.clamp(a.dotProduct(b),-1,1),th=Math.acos(dot);
        if(th<1e-4)return b;
        if(Math.PI-th<1e-3){Vec3d n=a.crossProduct(UP);n=n.lengthSquared()<1e-6?a.crossProduct(new Vec3d(1,0,0)):n;n=n.normalize();return a.multiply(Math.cos(th*u)).add(n.multiply(Math.sin(th*u))).normalize();}
        double s=Math.sin(th);return a.multiply(Math.sin((1-u)*th)/s).add(b.multiply(Math.sin(u*th)/s)).normalize();
    }
    /** Every sword drawn {@code t} seconds in (null for one not drawn then). */
    static Blade[] blades(StandClient.Cast c,float t){
        var plans=plans(c);if(plans==null)return new Blade[0];
        Frame fr=frame(c);float end=IsosangClient.finaleEnd(c.id());
        Blade[] out=new Blade[plans.length];
        for(int j=0;j<plans.length;j++){
            var p=plans[j];
            // called off: what has not come down yet goes now
            if(!Float.isNaN(end)&&t>=end&&p.plant()>end){var b=blade(fr,p,Math.min(t,end));if(b!=null&&t<end+.2f)out[j]=new Blade(b.mid(),b.dir(),b.spin(),b.alpha()*(1-Ease.smooth(t,end,end+.2f)),false);continue;}
            out[j]=blade(fr,p,t);
        }
        return out;
    }
    /** Whether its blows are still to come or came before it was called off: the band's burst only for one not called off before it. */
    static boolean comes(StandClient.Cast c,float at){float end=IsosangClient.finaleEnd(c.id());return Float.isNaN(end)||at<=end;}

    // ───────────────────────────── the passes ─────────────────────────────

    /** The blended pass: the swords' bodies (the farthest first), the crystal flaring up where each strikes, the ink flung off. */
    static void alpha(Fx c,EffectMesh m){
        float t=c.sec;var all=blades(c.cast,t);var plans=plans(c.cast);if(plans==null)return;
        Integer[] order=new Integer[all.length];for(int i=0;i<all.length;i++)order[i]=i;
        Arrays.sort(order,Comparator.comparingDouble((Integer i)->all[i]==null?0:-all[i].mid().squaredDistanceTo(c.camera)));
        for(int i:order){
            var b=all[i];if(b==null||b.alpha()<=.005f)continue;
            var p=plans[i];float flare=flare(p,b,t,false);
            SwordFormation.sword(m,c.camera,b.pommel(),SwordFormation.frame(b.dir(),b.spin()),b.alpha()*(p.own()?1:.88f),0,flare,0);
        }
        for(var p:plans)if(comes(c.cast,p.plant()))crystal(m,p,t,false);
    }
    /**
     * The light under the ink: the flash and the ring at her feet as it begins; her own sword gone into light as she leaps
     * and back as her camera comes back to her; the streak up under her; each sword's glow and the white light it streaks
     * behind it as it flies; each sword of light's burst as it shows; the great arc of blue behind her as she comes down;
     * the white ring round her; her eyes and her two fingers alight; as each strikes, a flash and a ring at its foot and the
     * crystal's light; the band's burst; the swords going in light.
     */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;Frame fr=frame(c.cast);var plans=plans(c.cast);if(plans==null)return;
        var all=blades(c.cast,t);
        // as it begins: a flash of blue, a ring of light at her feet on the pommel
        Vec3d feet0=fr.spot.add(0,fr.pommel+.05,0);
        float flash=FxLib.kick(t,FINALE_FLASH,.12f);
        if(flash>.01f){m.softDisc(feet0.add(0,.9,0),3.2,BLUE,.5f*flash);m.softDisc(feet0.add(0,.9,0),1.1,PALE,.45f*flash);glint8(m,facing(feet0.add(0,1,0),c.camera),1.4*flash,WHITE,.7f*flash);}
        if(t>=FINALE_FLASH-.01f&&t<FINALE_FLASH+.45f){
            float u=Ease.unit(t,FINALE_FLASH,FINALE_FLASH+.45f);
            FxLib.ring(m,feet0,.3+3.2*Ease.out(t,FINALE_FLASH,FINALE_FLASH+.35f),.16,AZURE,.75f*(1-u));
            FxLib.ring(m,feet0,.2+2*Ease.out(t,FINALE_FLASH,FINALE_FLASH+.25f),.05,WHITE,.7f*(1-u));
        }
        // her own sword into light as she leaps, and back as her camera comes back to her
        for(float at:new float[]{HAND_GONE,HAND_BACK}){
            float k=FxLib.kick(t,at,.1f);if(k<=.01f)continue;
            Vec3d h=hand(c,fr,at);if(h==null)continue;
            Vec3d o=facing(h,c.camera);m.softDisc(o,.9,AZURE,.45f*k);glint8(m,o,.6*k,WHITE,.85f*k);
        }
        // the streak of light up under her as she leaps
        float up=Ease.window(t,FINALE_LEAP,FINALE_TOP+.1f,.04f,.25f);
        if(up>.01f){Vec3d top=fr.spot.add(0,finaleDrawn(t,fr.pommel),0);m.softTaper(feet0,top,.9,.25,BLUE,.32f*up);m.line(feet0,top,.05,PALE,.6f*up);}
        // the swords: their glow, the light they streak behind them as they fly, the burst each of light shows out of
        for(int j=0;j<all.length;j++){
            var b=all[j];var p=plans[j];if(b==null)continue;
            float flare=flare(p,b,t,true);
            // (its outline lit less than the formation's under its night: against the dusk the fan was a mass of white)
            SwordFormation.swordGlow(m,b.pommel(),SwordFormation.frame(b.dir(),b.spin()),b.alpha(),0,GLOW_SKY,flare,0);
            if(b.flying())streak(m,fr,p,t,b);
            if(!p.own()){
                float k=FxLib.kick(t,p.start(),.09f);
                if(k>.01f){Vec3d o=facing(b.mid(),c.camera);m.softDisc(o,1.3,BLUE,.35f*k);glint8(m,o,.55*k,PALE,.7f*k);}
            }else{
                // pulled out: a flash of blue at its foot
                float k=FxLib.kick(t,p.start(),.12f);
                if(k>.01f){Vec3d g=p.planted().add(p.down().multiply(SwordFormation.S_LEN));m.softDisc(g.add(0,.3,0),1.2,BLUE,.35f*k);FxLib.ring(m,g.add(0,.05,0),.2+1.2*Ease.out(t,p.start(),p.start()+.25f),.06,PALE,.6f*k);}
            }
        }
        // the great arc of blue behind her as she comes down
        arc(m,fr,t);
        // the white ring round her as she stands (the videos' burst round her)
        ringBurst(c,m,fr,t);
        // her eyes and her two fingers alight
        seal(c,m,fr,t);
        // launched: a flash behind her as they go
        float go=FxLib.kick(t,FINALE_LAUNCH,.14f);
        if(go>.01f){Vec3d o=fr.at(-HUB_BACK-.4,HUB_UP+1.2,0);m.softDisc(o,4.5,BLUE,.32f*go);m.softDisc(o,1.6,PALE,.3f*go);}
        // each striking: a flash and a ring at its foot, the crystal's light
        for(var p:plans){
            if(!comes(c.cast,p.plant()))continue;
            float k=FxLib.kick(t,p.plant(),.1f);
            if(k>.01f){
                Vec3d g=p.foot();m.softDisc(g.add(0,.35,0),2.0,BLUE,.42f*k);m.star(g.add(0,.4,0),1.1,PALE,.5f*k);
                float u=Ease.unit(t,p.plant(),p.plant()+.3f);if(u<1)FxLib.ring(m,g.add(0,.05,0),.25+1.8*Ease.out(t,p.plant(),p.plant()+.28f),.09,AZURE,.6f*(1-u));
            }
            crystal(m,p,t,true);
        }
        burst(c,m,fr,t);
        // going: lit along its blade a moment, a scatter of motes off it
        for(int j=0;j<plans.length;j++){
            var p=plans[j];if(t<p.vanish()-.02f||t>=p.vanish()+.6f||!comes(c.cast,p.plant()))continue;
            Vec3d point=p.stuckMid().add(p.stuckDir().multiply(SwordFormation.S_LEN/2)),pommel=p.stuckMid().subtract(p.stuckDir().multiply(SwordFormation.S_LEN/2));
            float g=Ease.window(t,p.vanish()-.02f,p.vanish()+FADE,.04f,.1f);
            if(g>.01f){m.softLine(pommel,point,.3,BLUE,.3f*g);m.softLine(pommel,point,.12,PALE,.45f*g);}
            for(int s=0;s<6;s++){
                int seed=p.seed()*7+s*13;float born=p.vanish()+.12f*(float)Ease.hash(seed);float u=Ease.unit(t,born,born+.45f);if(u<=0||u>=1)continue;
                Vec3d q=pommel.lerp(point,.1+.8*Ease.hash(seed+1)).add((Ease.hash(seed+2)-.5)*.5*u,1.4*u,(Ease.hash(seed+3)-.5)*.5*u);
                glint8(m,facing(q,c.camera),.2*(1-u),s%3==0?WHITE:s%3==1?PALE:CYAN,.8f*(1-u));
            }
        }
    }
    /**
     * How strongly sword {@code b} flares with light (SwordFormation.sword's flare: a broad glow along it): its own swords
     * a little as they fly, the swords of light as they fly; standing in the fan a little, and standing in the ground hardly
     * (a broad glow round a standing sword looked like a pale slab, not a sword).
     */
    static float flare(Plan p,Blade b,float t,boolean light){
        if(b.flying())return p.own()?(light?.25f:.18f):(light?.35f:.4f);
        if(t>=p.plant())return p.own()?0:.05f;
        return p.own()?0:.16f;
    }
    /** The white light a flying sword streaks behind it: back along its way a few hundredths of a second, longer as it is launched. */
    static void streak(EffectMesh m,Frame fr,Plan p,float t,Blade b){
        float back=t>=p.launch()?STREAK_FLY:STREAK_SWIRL;
        Vec3d head=b.point();
        Vec3d tail=null;
        for(float s=back;s>.005f;s*=.5f){Vec3d m0=mid(fr,p,Math.max(p.start(),t-s));if(m0!=null){tail=m0.add(b.dir().multiply(SwordFormation.S_LEN/2));break;}}
        if(tail==null||tail.squaredDistanceTo(head)<.01)return;
        float a=b.alpha()*(t>=p.launch()?.9f:.7f);
        m.softTaper(tail,head,0,.32,BLUE,.32f*a);m.softTaper(tail,head,0,.12,PALE,.6f*a);m.line(tail.lerp(head,.35),head,.03,WHITE,.85f*a);
    }
    static final float STREAK_SWIRL=.07f,STREAK_FLY=.11f;
    /** How strongly its swords' outlines are lit (SwordFormation.swordGlow's sky: 1 the formation's own under its night). */
    static final float GLOW_SKY=.5f;
    /** Her right hand (where her sword was) at {@code t} as drawn: up her leap. */
    static Vec3d hand(Fx c,Frame fr,float t){
        var pose=R6.poseOf(CLIP.frame(t));
        return KagenoClips.blade(fr.spot.add(0,finaleDrawn(t,fr.pommel),0),c.cast.yaw(),pose)[0];
    }
    /** Her head's middle at {@code t} as drawn. */
    static Vec3d head(Fx c,Frame fr,float t){
        var pose=R6.poseOf(CLIP.frame(t));
        return KagenoClips.head(fr.spot.add(0,finaleDrawn(t,fr.pommel),0),c.cast.yaw(),pose);
    }
    /**
     * The great arc of blue light behind her as she comes down (the videos: a sweep of light round behind her as the swords
     * gather): round her upper back from high at her left down to her right, drawn on quickly and gone as she stands.
     */
    static void arc(EffectMesh m,Frame fr,float t){
        if(t<ARC0||t>=ARC1+.4f)return;
        float drawn=Ease.out(t,ARC0,ARC1),fade=1-Ease.smooth(t,ARC1+.05f,ARC1+.4f);if(drawn<=.01f||fade<=.01f)return;
        Vec3d hub=fr.at(-HUB_BACK-.3,HUB_UP+.2,0);int n=40;Vec3d prev=null;
        for(int i=0;i<=n;i++){
            double u=i/(double)n;if(u>drawn)break;
            double a=Math.toRadians(-150+200*u);
            Vec3d q=hub.add(fr.r.multiply(Math.cos(a)*ARC_R)).add(0,Math.sin(a)*ARC_R*.8+ARC_R*.15,0).subtract(fr.f.multiply(.4*Math.sin(Math.PI*u)));
            if(prev!=null){
                double head=1-Math.min(1,(drawn-u)/.35);float w=(float)(.25+.75*Math.sin(Math.PI*u));
                m.softLine(prev,q,.9*w,BLUE,.3f*fade);m.softLine(prev,q,.35*w,AZURE,.5f*fade);m.line(prev,q,.05*w,head>.5?WHITE:PALE,.7f*fade);
            }
            prev=q;
        }
        if(prev!=null){glint8(m,prev,.5,WHITE,.8f*fade*(1-Ease.smooth(t,ARC1,ARC1+.15f)));}
    }
    static final float ARC0=1.0f,ARC1=1.18f;
    static final double ARC_R=2.6;
    /** The white ring that bursts round her (a ring turned to the camera, soft and wide, with a flash), and a ring along the ground. */
    static void ringBurst(Fx c,EffectMesh m,Frame fr,float t){
        float at=FINALE_RING;if(t<at-.02f||t>=at+.45f)return;
        float a=Ease.smooth(t,at-.02f,at+.01f),u=Ease.unit(t,at,at+.4f);
        Vec3d o=fr.at(0,1.15,0);
        double r=.6+3.4*Ease.out(t,at,at+.24f);
        SwordCage.ringToCamera(m,o,c.camera,r,.9,PALE,.42f*a*(1-u));
        SwordCage.ringToCamera(m,o,c.camera,r*.96,.25,WHITE,.65f*a*(1-u));
        m.softDisc(facing(o,c.camera),2.6,AZURE,.3f*a*(1-u)*(1-u));
        FxLib.ring(m,fr.spot.add(0,.06,0),.4+4.5*Ease.out(t,at,at+.35f),.18,AZURE,.5f*a*(1-u));
    }
    /** Her eyes alight and a star at her two raised fingers while she holds them before her face (the videos' glowing eyes). */
    static void seal(Fx c,EffectMesh m,Frame fr,float t){
        float k=Ease.window(t,FINALE_SEAL0,FINALE_SEAL1+.12f,.08f,.12f);if(k<=.01f)return;
        // (small and on her: a star on her face at a close camera fills the frame and hides her)
        for(int s=-1;s<=1;s+=2){
            Vec3d eye=toward(onBody(c,fr,t,EYE_RT*s,EYE_UP,EYE_FW),c.camera,.04);
            m.softDisc(eye,.075,AZURE,.55f*k);m.softDisc(eye,.03,WHITE,.7f*k);
            if(s<0)glint8(m,eye,.1+.02*Math.sin(t*28),PALE,.75f*k);
        }
        var tip=GGesture.FINALE_TIP;
        Vec3d at=toward(onBody(c,fr,t,-tip.x/16,(24-tip.y)/16,-tip.z/16),c.camera,.05);
        m.softDisc(at,.2,AZURE,.3f*k);glint8(m,at,.085+.012*Math.sin(t*31),WHITE,.8f*k);
    }
    /** Where her eyes are on her face (blocks as the model is built: either side of its middle, over her feet, ahead). */
    static final double EYE_RT=.125,EYE_UP=1.72,EYE_FW=.255,MODEL_SCALE=.9375;
    /** A point on her torso {@code t} seconds in as she is drawn (the model drawn 0.9375 the size it is built). */
    static Vec3d onBody(Fx c,Frame fr,float t,double rt,double up,double fw){
        Vec3d feet=fr.spot.add(0,finaleDrawn(t,fr.pommel),0);
        Vec3d q=KagenoClips.onTorso(feet,IsosangClient.finaleFacing(c.cast,c.delta),R6.poseOf(CLIP.frame(t)),rt,up,fw);
        return feet.add(q.subtract(feet).multiply(MODEL_SCALE));
    }
    /** {@code p} moved {@code by} blocks toward the camera (over what it lies on). */
    static Vec3d toward(Vec3d p,Vec3d camera,double by){Vec3d d=camera.subtract(p);double l=d.length();return l<1e-4?p:p.add(d.multiply(Math.min(by,l*.5)/l));}
    /**
     * The crystal flaring up where a sword strikes (the videos' blue crystal fire): sharp shards of blue crystal standing
     * up round its foot, each two crossing diamonds fixed in the world (fire is not turned to the camera), shooting up as it
     * strikes and breaking away; the blended pass draws their bodies, the light pass their glow and white hearts.
     */
    static void crystal(EffectMesh m,Plan p,float t,boolean light){
        float s=t-p.plant();if(s<0||s>=CRYSTAL)return;
        float fade=1-Ease.smooth(s,.2f,CRYSTAL);if(fade<=.01f)return;
        for(int i=0;i<SHARDS;i++){
            int seed=p.seed()*13+i*29;
            // (each its own moment, up in a few hundredths of a second, sharp: the videos' jagged blue crystal)
            float born=.035f*(float)Ease.hash(seed+7),grow=Ease.out(s,born,born+.05f);if(grow<=.01f)continue;
            Vec3d way=new Vec3d((Ease.hash(seed)-.5)*1.3,1,(Ease.hash(seed+1)-.5)*1.3).normalize();
            double len=(1.1+1.7*Ease.hash(seed+2))*(i==0?1.25:1)*grow*(1+.18*s),w=.07+.09*Ease.hash(seed+3);
            Vec3d base=p.foot().add((Ease.hash(seed+4)-.5)*.6,-.05,(Ease.hash(seed+5)-.5)*.6).add(way.multiply(.35*s));
            Vec3d side=way.crossProduct(UP);side=side.lengthSquared()<1e-6?new Vec3d(1,0,0):side.normalize();
            double turn=Math.PI*Ease.hash(seed+6);side=side.multiply(Math.cos(turn)).add(way.crossProduct(side).multiply(Math.sin(turn))).normalize();
            Vec3d side2=way.crossProduct(side).normalize();
            Vec3d tip=base.add(way.multiply(len)),waist=base.add(way.multiply(len*(.2+.15*Ease.hash(seed+8))));
            if(!light){
                // a deep blue edge round a vivid blue body, on two crossing planes
                for(Vec3d sd:new Vec3d[]{side,side2}){
                    m.quad(base,waist.add(sd.multiply(w*1.35)),tip.add(way.multiply(.06)),waist.subtract(sd.multiply(w*1.35)),0x1b3ccf,.8f*fade);
                    m.quad(base,waist.add(sd.multiply(w)),tip,waist.subtract(sd.multiply(w)),i%3==0?0x8fd2ff:0x3f7dff,.85f*fade);
                }
            }else{
                m.softLine(base,tip,w*2.2,BLUE,.24f*fade);m.line(waist,tip,w*.28,WHITE,.85f*fade);m.line(base,waist,w*.4,PALE,.6f*fade);
                if(i==0)m.softDisc(base.add(0,.5,0),1.1,AZURE,.22f*fade);
            }
        }
    }
    static final int SHARDS=7;
    static final float CRYSTAL=.56f;
    /**
     * The band's burst as the last come down (FINALE_BURST, the videos' great blue flash): the ground of the band alight in
     * blue, a ring racing out along it, every sword lit up along its blade with a spike of light up out of its foot, and
     * stars rising all over it.
     */
    static void burst(Fx c,EffectMesh m,Frame fr,float t){
        float at=FINALE_BURST;if(t<at-.02f||t>=at+.9f||!comes(c.cast,at))return;
        float a=Ease.smooth(t,at-.02f,at+.01f),u=Ease.unit(t,at,at+.6f),fade=1-Ease.smooth(t,at+.08f,at+.7f);
        for(int i=0;i<5;i++){Vec3d o=fr.at(BAND_NEAR+(BAND_FAR-BAND_NEAR)*(i+.5)/5,.3,0);m.softDisc(o,7.5,BLUE,.22f*a*fade);m.softDisc(o,3.2,AZURE,.1f*a*fade);}
        Vec3d mid=fr.at((BAND_NEAR+BAND_FAR)/2,.06,0);
        FxLib.ring(m,mid,1+16*Ease.out(t,at,at+.5f),.5,AZURE,.6f*a*(1-u));
        FxLib.ring(m,mid,.6+11*Ease.out(t,at,at+.35f),.12,WHITE,.65f*a*(1-u));
        var plans=plans(c.cast);if(plans==null)return;
        float spike=a*(1-Ease.smooth(t,at+.05f,at+.4f));
        for(var p:plans){
            Vec3d point=p.stuckMid().add(p.stuckDir().multiply(SwordFormation.S_LEN/2)),pommel=p.stuckMid().subtract(p.stuckDir().multiply(SwordFormation.S_LEN/2));
            m.softLine(pommel,point,.5,CYAN,.35f*a*fade);
            if(spike>.01f)m.needle(p.foot(),p.foot().add(0,2+2.5*Ease.hash(p.seed()+40),0),.12,.3,Ease.hash(p.seed()+41)<.3?WHITE:PALE,.7f*spike);
        }
        for(int s=0;s<STARS;s++){
            int seed=s*53+911;float born=at+.02f+.18f*(float)Ease.hash(seed),v=Ease.unit(t,born,born+.7f);if(v<=0||v>=1)continue;
            Vec3d q=fr.at(BAND_NEAR+(BAND_FAR-BAND_NEAR)*Ease.hash(seed+1),.2+2.4*Ease.out(v,0,1),(Ease.hash(seed+2)-.5)*2*BAND_HALF);
            glint8(m,facing(q,c.camera),(.22+.2*Ease.hash(seed+3))*(1-.5*v),s%3==0?WHITE:s%3==1?PALE:CYAN,.85f*(1-v)*twinkle(t,seed));
        }
    }
    static final int STARS=48;
    /** Over the ink: a star at each sword's point as it strikes, and a few stars on the swords standing in the band. */
    static void overlay(Fx c,EffectMesh m){
        float t=c.sec;var plans=plans(c.cast);if(plans==null)return;
        for(var p:plans){
            if(!comes(c.cast,p.plant()))continue;
            float k=FxLib.kick(t,p.plant(),.08f);
            if(k>.01f)glint8(m,facing(p.foot().add(0,.25,0),c.camera),1.1*k+.15,WHITE,.85f*k);
            if(t>p.plant()+.1f&&t<p.vanish()){
                Vec3d q=p.stuckMid().add(p.stuckDir().multiply(-.3+.6*Ease.hash(p.seed()+60)));
                glint8(m,q,.14,Ease.hash(p.seed()+61)<.5?WHITE:PALE,.7f*twinkle(t,p.seed()));
            }
        }
    }
    /** The ink: a splash of ink at each sword's foot as it strikes (the videos' dark round each). */
    static void ink(Fx c){
        float t=c.sec;var plans=plans(c.cast);if(plans==null)return;
        for(var p:plans){
            if(t<p.plant()||!comes(c.cast,p.plant()))continue;
            GiantSweep.splatFor(p.foot().add(0,.004+.001*(p.seed()&7),0),.9+.5*Ease.hash(p.seed()+70),Math.PI*2*Ease.hash(p.seed()+71),1.2,p.seed()&15,t-p.plant()-.02f,.85f);
        }
    }
    /**
     * Lights: the flash as it begins, round her as the swords swirl, as each strikes, the band's burst; the afterglow cut
     * short while they swirl and fly (else they leave copies of themselves on the way); a deep blue impact frame as the band
     * bursts (a few frames, whole within 40 blocks, gone by 70).
     */
    static void frame(Fx c,FxFrame f){
        float t=c.sec;Frame fr=frame(c.cast);var plans=plans(c.cast);if(plans==null)return;
        float flash=FxLib.kick(t,FINALE_FLASH,.15f);if(flash>.01f)f.light(fr.spot.add(0,fr.pommel+1,0),0x9fc8ff,10,1.4f*flash);
        float swirl=Ease.window(t,FINALE_LEAP,GATHER0+GATHER+.2f,.1f,.25f);if(swirl>.01f)f.light(chest(fr,t),0x8cbcff,10,.8f*swirl);
        float fan=Ease.window(t,GATHER0+GATHER,FINALE_LAUNCH+.1f,.1f,.1f);if(fan>.01f)f.light(fr.at(-HUB_BACK,HUB_UP+1,0),0x8cbcff,8,.6f*fan);
        for(var p:plans){float k=comes(c.cast,p.plant())?FxLib.kick(t,p.plant(),.14f):0;if(k>.05f)f.light(p.foot().add(0,.8,0),0x9fd4ff,7,.9f*k);}
        if(comes(c.cast,FINALE_BURST)){
            float b=FxLib.kick(t,FINALE_BURST,.3f);if(b>.01f)f.light(fr.at((BAND_NEAR+BAND_FAR)/2,1.5,0),0xa8d8ff,26,1.8f*b);
            float imp=GGrand.impact(t,FINALE_BURST);
            float near=(float)Math.max(0,Math.min(1,(70-c.camera.distanceTo(fr.at(8,1,0)))/30));
            if(imp>0&&near>0)f.impact(.7f*near*imp,0,SwordCage.CAGE_LOOK);
        }
        if(t>=FINALE_LEAP&&t<GATHER0+GATHER+.1f||t>=FINALE_LAUNCH-.05f&&t<FINALE_PLANT1+.1f)f.afterglow(.05f);
    }
    /** A jolt as the swords strike (small each), harder as the band bursts; each over two frames. */
    static float shake(StandClient.Cast cast,float sec){
        float v=0;
        for(int i=0;i<blows(FINALE);i++)if(comes(cast,blowAt(FINALE,i)/20f))v=Math.max(v,(i==blows(FINALE)-1?.85f:.4f)*bump(sec,blowAt(FINALE,i)/20f,.1f));
        return v;
    }

    // ───────────────────────────── her cutscene ─────────────────────────────

    /**
     * Its dusk (her cutscene's): the sky a red dusk and the ground round her darkened, coming in as it flashes and going as
     * her camera comes back to her; only on her own client, in her cutscene (IsosangCinema.dusk darkens the fog with it).
     */
    static void ground(Fx c){
        var own=cinematic();if(own==null||!own.id().equals(c.cast.id()))return;
        float k=dusk(c.cast,c.sec);if(k<=.005f)return;
        Frame fr=frame(c.cast);
        // (in draw calls of their own, once: in the blended pass, as the formation's night is, KagenoFire.inkInterlude)
        if(dev.sangwoo.celestialfall.client.KagenoFire.crescentGain>.99f)dev.sangwoo.celestialfall.client.KagenoFire.inkInterlude(()->{
            float blue=wash(c.sec);
            FormationSky.draw(c,false,FormationSky.SOFT,1,m->duskDome(m,fr.spot,k,blue));
            FormationSky.draw(c,false,.35f,1,m->veil(m,fr.spot,k));
        });
    }
    /** How deep its dusk is {@code t} seconds in (0 to 1). */
    static float dusk(StandClient.Cast c,float t){return Ease.smooth(t,FINALE_FLASH,FINALE_FLASH+.12f)*(1-Ease.smooth(t,FINALE_BACK0,FINALE_BACK1));}
    /** The dusk's colours by elevation (degrees from its eye, a little over her): dark under the horizon, red low, orange, a deep red-brown overhead (the videos' burning sky). */
    static final double[] DUSK_EL={-16,-2,2,8,18,40,65,90};
    static final int[] DUSK_RGB={0x04060c,0x080a14,0x1e1208,0x341d08,0x3c2208,0x2c1808,0x1a100a,0x0e0a0e};
    /** The blue it flashes in with (the videos' blue flash over the world as the cutscene begins), how long it takes to go. */
    static final int WASH_RGB=0x1a2c78;
    static float wash(float t){return Ease.smooth(t,FINALE_FLASH-.02f,FINALE_FLASH+.04f)*(1-Ease.smooth(t,FINALE_FLASH+.08f,FINALE_FLASH+.55f));}
    static int duskRgb(double el){
        if(el<=DUSK_EL[0])return DUSK_RGB[0];
        for(int i=1;i<DUSK_EL.length;i++)if(el<=DUSK_EL[i]){double u=(el-DUSK_EL[i-1])/(DUSK_EL[i]-DUSK_EL[i-1]);return FormationSky.mix(DUSK_RGB[i-1],DUSK_RGB[i],u*u*(3-2*u));}
        return DUSK_RGB[DUSK_RGB.length-1];
    }
    static void duskDome(EffectMesh m,Vec3d spot,float k,float blue){
        Vec3d e=spot.add(0,FormationSky.SKY_EYE,0);int n=64;double[] rings=FormationSky.RINGS;double r=FormationSky.SKY_R*.9;
        for(int j=0;j+1<rings.length;j++){
            for(int i=0;i<n;i++){
                double a0=Math.PI*2*i/n,a1=Math.PI*2*(i+1)/n;
                Vec3d p00=e.add(FormationSky.dir(a0,rings[j]).multiply(r)),p01=e.add(FormationSky.dir(a1,rings[j]).multiply(r));
                Vec3d p11=e.add(FormationSky.dir(a1,rings[j+1]).multiply(r)),p10=e.add(FormationSky.dir(a0,rings[j+1]).multiply(r));
                int c0=FormationSky.mix(duskRgb(rings[j]),WASH_RGB,blue),c1=FormationSky.mix(duskRgb(rings[j+1]),WASH_RGB,blue);float al=.96f*k;
                m.vertex(p00,c0,al);m.vertex(p01,c0,al);m.vertex(p11,c1,al);m.vertex(p10,c1,al);
            }
        }
    }
    /** The ground darkened round her (out to {@link #VEIL_R} blocks, softly to nothing at its edge). */
    static void veil(EffectMesh m,Vec3d spot,float k){
        int n=64,rings=6;Vec3d o=spot.add(0,.03,0);
        for(int j=0;j<rings;j++){
            double r0=VEIL_R*j/rings,r1=VEIL_R*(j+1)/rings;float a0=VEIL_ALPHA*k*(float)(1-Math.pow(j/(double)rings,2)),a1=VEIL_ALPHA*k*(float)(1-Math.pow((j+1)/(double)rings,2));
            for(int i=0;i<n;i++){
                double t0=Math.PI*2*i/n,t1=Math.PI*2*(i+1)/n;
                m.vertex(o.add(Math.cos(t0)*r0,0,Math.sin(t0)*r0),VEIL_RGB,a0);m.vertex(o.add(Math.cos(t1)*r0,0,Math.sin(t1)*r0),VEIL_RGB,a0);
                m.vertex(o.add(Math.cos(t1)*r1,0,Math.sin(t1)*r1),VEIL_RGB,a1);m.vertex(o.add(Math.cos(t0)*r1,0,Math.sin(t0)*r1),VEIL_RGB,a1);
            }
        }
    }
    static final double VEIL_R=36;
    static final float VEIL_ALPHA=.6f;
    static final int VEIL_RGB=0x04070e;
    /** The local player's own H again whose cutscene is on now (their camera's setting allowing), or null. */
    static StandClient.Cast cinematic(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||!mc.player.isAlive()||!CelestialFall.CONFIG.cinematicCamera)return null;
        var c=IsosangClient.ownFinale();if(c==null)return null;
        float t=c.age(mc.getTickDelta())/20f;return t>=FINALE_FLASH&&t<FINALE_BACK1?c:null;
    }
    /** A shot: where the camera stands, what it looks at, its field of view (degrees). */
    record Shot(Vec3d eye,Vec3d look,double fov){}
    /**
     * Her camera's shots {@code t} seconds in (the videos'): low in front of her and to her left, wide, looking up after
     * her as she leaps and the swords swirl round her; in to her as she comes down at it; close before her, her chest and
     * face, as the swords spread behind her and she raises her fingers; cut round behind her at her left, wide, the band
     * ahead, as they fly and come down.
     */
    static Shot shot(StandClient.Cast c,float t){return shot(c,IsosangClient.finale(c.id()),t);}
    static Shot shot(StandClient.Cast c,IsosangClient.FinaleRun run,float t){
        Frame fr=frame(c,run);Vec3d chest=chest(fr,t);
        if(t<WIDE1){
            float u=Ease.unit(t,FINALE_FLASH,WIDE1);
            Vec3d eye=fr.at(WIDE[0]-.6*u,WIDE[1]+WIDE_RISE*finaleDrawn(t,fr.pommel),WIDE[2]);
            return new Shot(eye,chest.add(0,.3,0),FOV_WIDE);
        }
        if(t<CLOSE0){
            float u=Ease.smooth(t,WIDE1,CLOSE0);
            Vec3d a=fr.at(WIDE[0]-.6,WIDE[1]+WIDE_RISE*finaleDrawn(WIDE1,fr.pommel),WIDE[2]),b=fr.at(CLOSE[0],CLOSE[1],CLOSE[2]);
            return new Shot(a.lerp(b,u),chest.add(0,.3*(1-u),0).lerp(fr.at(0,CLOSE_LOOK,0),u),FOV_WIDE+(FOV_CLOSE-FOV_WIDE)*u);
        }
        if(t<FINALE_LAUNCH){
            float u=Ease.unit(t,CLOSE0,FINALE_LAUNCH);
            Vec3d eye=fr.at(CLOSE[0]-CLOSE_IN*u,CLOSE[1],CLOSE[2]);
            return new Shot(eye,fr.at(0,CLOSE_LOOK,0),FOV_CLOSE);
        }
        float u=Ease.unit(t,FINALE_LAUNCH,FINALE_BACK1);
        Vec3d eye=fr.at(BEHIND[0]+.8*u,BEHIND[1]+.5*u,BEHIND[2]);
        return new Shot(eye,fr.at(BEHIND_LOOK,.6,0),FOV_BEHIND);
    }
    /** The wide shot: its place (forward, up, right of where she stands); in close; behind her at her left, and what it looks at ahead. */
    static final double[] WIDE={6.2,.45,-2.6},CLOSE={2.95,1.5,.14},BEHIND={-6,2.2,-3.2};
    /** How much of her leap the wide shot rises with (it looks up at her nearer, not from the ground far off), where the close shot looks (her chest, her head and the fan's foot in frame) and how far it comes in. */
    static final double WIDE_RISE=.42,CLOSE_LOOK=1.5,CLOSE_IN=.3;
    static final double BEHIND_LOOK=8,FOV_WIDE=70,FOV_CLOSE=50,FOV_BEHIND=74;
    static final float WIDE1=.98f,CLOSE0=1.38f;
    /** Her camera in its cutscene: the shot, short of a block between her and it; going back to the game's camera as it ends. */
    static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){
        var c=cinematic();if(c==null)return null;
        float t=c.age(delta)/20f;Shot s=shot(c,t);Frame fr=frame(c);
        Vec3d eye=clear(chest(fr,Math.min(t,FINALE_LAND)),s.eye());Vec3d d=s.look().subtract(eye);if(d.lengthSquared()<1e-6)d=fr.f;
        d=d.normalize();
        float sy=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),sp=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));
        float back=Ease.smooth(t,FINALE_BACK0,FINALE_BACK1);
        if(back<=0)return new CinematicDirector.Pose(eye,sy,sp,0);
        return new CinematicDirector.Pose(eye.lerp(vanilla,back),sy+MathHelper.wrapDegrees(yaw-sy)*back,sp+(pitch-sp)*back,0);
    }
    static double fov(double vanilla,float delta){
        var c=cinematic();if(c==null)return vanilla;
        float t=c.age(delta)/20f;double f=shot(c,t).fov();
        return f+(vanilla-f)*Ease.smooth(t,FINALE_BACK0,FINALE_BACK1);
    }
    /** How deep her cutscene's dusk is for the local player now (0 to 1: the fog darkens with it). */
    static float duskNow(float delta){var c=cinematic();return c==null?0:dusk(c,c.age(delta)/20f);}
    /** The camera at {@code cam} pulled in toward {@code from} short of a block between them. */
    static Vec3d clear(Vec3d from,Vec3d cam){return SwordCage.clear(from,cam);}
}
