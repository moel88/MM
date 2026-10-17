package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * M1 "검술의 대가", an original four-hit combo. Every hit is a separate cast (combo 1–4) and the blade connects 0.30 s
 * after the click (the server's hit tick 6).
 * <ol>
 * <li>발도: the sword drawn from the left hip in a rising diagonal cut out to the high right.</li>
 * <li>되베기: raised over the right shoulder, it comes back down the other diagonal with a step in.</li>
 * <li>회전 내려찍기: a leap with a full turn in the air, the sword held overhead, into a slam on the ground ahead: the
 * floor cracks, heaves and throws up chunks and dust.</li>
 * <li>봉인·승월: a lunge thrust pins the target in a violet seal (the server holds it from 0.3 s); the sword is drawn
 * down and at 0.7 s cuts straight up through it: the seal shatters and the target is launched (the server's launch).</li>
 * </ol>
 * Each slash is drawn along the path the blade really sweeps (crisp violet bands: an ink line on the tip edge, a thin
 * white core, a burning tail), and on the effect layer it lights the world and bends the air behind it. Seconds after
 * the click; every effect is a function of the cast clock, so it moves at the render frame rate.
 */
final class ComboSkill implements Fx.Skill {
    static final float HIT=.30f,LAUNCH=KagenoRules.M1_LAUNCH/20f;
    static final Vec3d UP=new Vec3d(0,1,0);
    /** Ink, deep purple, violet, lilac, the seal's violet, magenta, pink, white. */
    static final int INK=0x12052c,DEEP=0x3a0c8c,VIOLET=0x8a3cff,LILAC=0xc9a4ff,SEAL=0xa45cff,MAGENTA=0xe63cff,PINK=0xff8ae8,WHITE=0xffffff;
    /** The blade's aura while it cuts (haze, glow, body, core). */
    static final int[] AURA={0x4a12a8,0x8f45ff,0xd2a8ff,0xfff4ff};
    /** Blocks along the blade from the hand: the guard and the tip. */
    static final double GUARD=.24,TIP=1.35;

    static double[] d(double elevation,double azimuth){return new double[]{elevation,azimuth};}
    /** A pose written in the world ({@link NeonThrustSkill#world}): root forward and up, the torso's turn (left +), lean and roll, then the limbs and the blade. */
    static R6.Pose w(double fw,double up,float yaw,float pitch,float roll,double[] rArm,double[] blade,double[] lArm,double[] rLeg,double[] lLeg){
        return NeonThrustSkill.world(fw,up,yaw,pitch,roll,rArm,blade,lArm,rLeg,lLeg);
    }

    // ───────────────────────────── 1 발도 ─────────────────────────────
    /** Gathered: the hips turned left, low, the hand at the left hip with the blade lying back along it as if sheathed. */
    static final R6.Pose H1_GATHER=w(-.04,-.1,34,8,-3,d(-58,-55),d(-22,-150),d(-62,-112),d(-76,24),d(-78,200));
    static final R6.Pose H1_COIL=w(-.05,-.13,40,10,-4,d(-60,-62),d(-20,-158),d(-64,-116),d(-75,26),d(-77,202));
    /** Halfway: the hips have led, the hand sweeps low across the front and the blade comes out of the sheath. */
    static final R6.Pose H1_DRAW=w(.04,-.1,8,8,0,d(-28,-22),d(-6,-48),d(-50,-128),d(-72,22),d(-76,198));
    /** The cut: rising through the front right with a step, the free hand thrown back. */
    static final R6.Pose H1_CUT=w(.12,-.06,-24,4,2,d(8,30),d(28,42),d(-38,-136),d(-68,18),d(-72,196));
    /** Followed through high to the right and behind. */
    static final R6.Pose H1_END=w(.16,-.03,-40,-2,3,d(40,86),d(54,118),d(-30,-124),d(-70,16),d(-74,195));
    static final Anim H1=Anim.joints(
        k(0,KagenoClips.IDLE),
        k(.10,H1_GATHER),
        // The coil keeps winding (a moving hold); then the hips lead and the hand whips the blade up across the front.
        k(.20,H1_COIL),
        k(.255,H1_DRAW,Anim.Ease.LINEAR),
        k(HIT,H1_CUT,Anim.Ease.LINEAR),
        k(.40,H1_END,Anim.Ease.OUT),
        k(.62,w(.14,-.04,-37,0,2,d(36,84),d(50,114),d(-34,-120),d(-71,16),d(-75,195))),
        k(.95,KagenoClips.IDLE)).stops(Anim.stop(HIT,.035,.08));

    // ───────────────────────────── 2 되베기 ─────────────────────────────
    /** Raised: the sword over the right shoulder pointing back, the hips turned right, the free hand out in front. */
    static final R6.Pose H2_RAISE=w(.12,0,-46,-4,4,d(62,70),d(62,160),d(-12,-58),d(-74,18),d(-76,196));
    static final R6.Pose H2_LOAD=w(.1,.02,-52,-6,5,d(66,78),d(56,172),d(-8,-52),d(-75,18),d(-77,196));
    /** Halfway down: over the front right, the hips coming round. */
    static final R6.Pose H2_MID=w(.2,-.06,-12,8,0,d(26,24),d(22,30),d(-24,-100),d(-68,16),d(-72,194));
    /** The cut: down across the front to the left with a deep step in. */
    static final R6.Pose H2_CUT=w(.28,-.15,26,16,-2,d(-22,-20),d(-30,-36),d(-30,-150),d(-62,14),d(-66,190));
    /** Followed through low behind the left. */
    static final R6.Pose H2_END=w(.32,-.2,44,18,-3,d(-45,-62),d(-38,-118),d(-40,-160),d(-60,12),d(-64,188));
    static final Anim H2=Anim.joints(
        k(0,H1_END),
        k(.12,H2_RAISE),
        k(.20,H2_LOAD),
        k(.255,H2_MID,Anim.Ease.LINEAR),
        k(HIT,H2_CUT,Anim.Ease.LINEAR),
        k(.42,H2_END,Anim.Ease.OUT),
        k(.62,w(.3,-.18,41,16,-2,d(-44,-58),d(-38,-112),d(-42,-156),d(-61,12),d(-65,188))),
        k(.95,KagenoClips.IDLE)).stops(Anim.stop(HIT,.04,.09));

    // ───────────────────────────── 3 회전 내려찍기 ─────────────────────────────
    // Body-space keys: the full turn is the root's yaw (to the left, carrying on from hit 2), the limbs ride with it.
    /** Down for the leap: the sword gathered up in front. */
    static final R6.Pose H3_CROUCH=pose().at(.34,-.32,0).body(40,24,-2).head(36,-16,0).rArm(-18,12).lArm(-42,-70).blade(4,8).rLeg(-58,50).lLeg(-62,-130);
    /** Rising and turning, the sword going up overhead. */
    static final R6.Pose H3_RISE=pose().at(.4,.28,0).body(150,-4,0).head(0,0,0).rArm(70,20).lArm(-20,-80).blade(60,150).rLeg(-66,20).lLeg(-74,-160);
    /** The top of the leap: turned most of the way round, the sword loaded overhead pointing back. */
    static final R6.Pose H3_TOP=pose().at(.45,.34,0).body(300,-8,0).head(0,-4,0).rArm(84,10).lArm(-10,-95).blade(26,176).rLeg(-62,14).lLeg(-70,-170);
    /** Coming down facing the front, the sword still loaded (a moving hold before the strike). */
    static final R6.Pose H3_LOADED=pose().at(.48,.22,0).body(348,-6,0).head(0,-8,0).rArm(82,-24).lArm(-14,-100).blade(28,196).rLeg(-64,16).lLeg(-70,-168);
    /** The slam: landed facing the front, leaning into it, the blade's point on the ground ahead. */
    static final R6.Pose H3_SLAM=pose().at(.5,-.12,0).body(358,24,0).head(4,-18,0).rArm(-26,14).lArm(-50,-120).blade(-17,16).rLeg(-60,28).lLeg(-64,-150);
    static final R6.Pose H3_END=pose().at(.48,-.22,0).body(358,26,0).head(4,-20,0).rArm(-28,16).lArm(-58,-110).blade(-20,18).rLeg(-60,28).lLeg(-64,-148);
    static final Anim H3=Anim.joints(
        k(0,H2_END),
        k(.06,H3_CROUCH),
        // The turn in the air comes first; the sword comes straight down over the head once the body faces the front.
        k(.12,H3_RISE),
        k(.19,H3_TOP),
        k(.235,H3_LOADED),
        k(HIT,H3_SLAM,Anim.Ease.LINEAR),
        k(.40,pose().at(.5,-.24,0).body(358,28,0).head(4,-22,0).rArm(-30,15).lArm(-56,-120).blade(-21,17).rLeg(-58,30).lLeg(-62,-150),Anim.Ease.OUT),
        k(.62,H3_END),
        // A whole turn on: the rest pose faces the same way as the start.
        k(.95,KagenoClips.IDLE.body(360,0,0))).stops(Anim.stop(HIT,.05,.1));

    // ───────────────────────────── 4 봉인·승월 ─────────────────────────────
    /** Drawn back: side-on, the hand at the right hip with the blade level at the target, the free hand aiming. */
    static final R6.Pose H4_DRAW=w(.38,-.14,-30,4,0,d(-48,160),d(0,4),d(-6,-12),d(-72,20),d(-74,192));
    static final R6.Pose H4_LOAD=w(.32,-.18,-36,2,0,d(-46,168),d(2,2),d(-2,-8),d(-72,22),d(-74,194));
    /** The thrust: the sword shoulder driven through, a long lunge, the free arm thrown back. */
    static final R6.Pose THRUST=w(.56,-.2,42,10,0,d(-4,-8),d(0,-2),d(-26,-170),d(-56,14),d(-58,192));
    /** The rising cut through the seal: the blade straight up, the body rising off the ground. */
    static final R6.Pose H4_LAUNCH=w(.52,.1,-8,-10,0,d(54,-14),d(72,-28),d(-42,-150),d(-74,10),d(-78,186));
    static final Anim H4=Anim.joints(
        // Hit 3 ended a whole turn on; this clip starts at the same pose with the turn taken off.
        k(0,H3_END.body(-2,26,0)),
        k(.12,H4_DRAW),
        k(.24,H4_LOAD),
        k(HIT,THRUST,Anim.Ease.IN),
        k(.40,w(.58,-.22,44,11,0,d(-5,-7),d(-1,-2),d(-28,-172),d(-56,14),d(-58,192))),
        // Pulled out and dropped low in front, then cut straight up through the seal with a spring off the ground.
        k(.56,w(.5,-.3,8,18,0,d(-50,22),d(-38,28),d(-34,-120),d(-60,14),d(-62,190))),
        k(.66,w(.5,-.16,0,6,0,d(-4,8),d(24,8),d(-36,-140),d(-66,12),d(-70,188)),Anim.Ease.IN),
        k(LAUNCH,H4_LAUNCH,Anim.Ease.LINEAR),
        k(.84,w(.5,.22,-12,-14,0,d(78,-20),d(62,-150),d(-30,-140),d(-80,8),d(-82,184)),Anim.Ease.OUT),
        k(.98,w(.4,-.16,-6,8,0,d(-40,44),d(-32,72),d(-60,-40),d(-74,12),d(-76,190))),
        k(1.15,KagenoClips.IDLE)).stops(Anim.stop(HIT,.06,.1),Anim.stop(LAUNCH,.04,.08));

    @Override public Anim clip(int combo){return hit(combo);}
    static Anim hit(int combo){return switch(combo){case 2->H2;case 3->H3;case 4->H4;default->H1;};}
    @Override public float lifetime(){return 1.25f;}
    @Override public boolean layered(){return true;}

    // ───────────────────────────── where things are ─────────────────────────────

    /** Per-cast spots held once they happen: the caster's feet at the blow, hit 4's seal at the launch. */
    private record Memo(Vec3d feet,Vec3d seal){}
    private static final Map<UUID,Memo> MEMOS=new LinkedHashMap<>();
    private static Memo memo(Fx c){return MEMOS.get(c.cast.id());}
    private static void remember(Fx c,Vec3d feet,Vec3d seal){
        MEMOS.put(c.cast.id(),new Memo(feet,seal));
        while(MEMOS.size()>32)MEMOS.remove(MEMOS.keySet().iterator().next());
    }
    /** The caster's feet: following them up to the blow, then held where it fell (the floor and the air react there). */
    static Vec3d anchor(Fx c){
        if(c.sec<HIT)return c.pos;
        var m=memo(c);if(m==null){remember(c,c.pos,null);return c.pos;}
        return m.feet();
    }
    /** Who the blade strikes, picked as the server does (within 3.4 blocks, not behind); hit 4's held targets once they are known. */
    static List<LivingEntity> struck(Fx c){
        if(!c.targets.isEmpty())return c.targets;
        var world=c.mc.world;if(world==null)return List.of();
        Vec3d at=anchor(c);UUID caster=c.cast.caster();
        var out=world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(4.4),e->e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!e.getUuid().equals(caster)&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&e.getPos().squaredDistanceTo(at)<=3.4*3.4&&e.getPos().subtract(at).multiply(1,0,1).normalize().dotProduct(c.f)>-.05);
        return out.size()>4?out.subList(0,4):out;
    }
    static Vec3d chest(Fx c,LivingEntity e){return e.getLerpedPos(c.delta).add(0,e.getHeight()*.58,0);}
    /** Sword hand and blade direction at clip time {@code sec} of hit {@code combo}, on the caster as drawn. */
    static Vec3d[] blade(Fx c,int combo,float sec){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(hit(combo).frame(sec)));}
    static Vec3d[] bladeNow(Fx c){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(KagenoClips.action(c.cast,c.delta)));}
    /** Where hit 3's blade meets the floor: under the tip at the slam, on the caster's floor. */
    static Vec3d slamPoint(Fx c){
        var hb=KagenoClips.blade(anchor(c),c.cast.yaw(),R6.poseOf(H3.frame(HIT)));Vec3d tip=hb[0].add(hb[1].multiply(TIP));
        return new Vec3d(tip.x,anchor(c).y,tip.z);
    }
    /** Hit 4's seal: on the first struck target's chest, else just past the thrust's point; held where it was at the launch. */
    static Vec3d sealPoint(Fx c){
        var m=memo(c);if(c.sec>=LAUNCH&&m!=null&&m.seal()!=null)return m.seal();
        Vec3d p=null;for(var e:struck(c)){p=chest(c,e);break;}
        if(p==null)p=anchor(c).add(c.f.multiply(2.3)).add(0,1.15,0);
        if(c.sec>=LAUNCH)remember(c,anchor(c),p);
        return p;
    }
    /** Size of the seal: a ring round a body of the target's height. */
    static double sealSize(Fx c){for(var e:struck(c))return Math.max(.75,Math.min(1.6,e.getHeight()/1.8));return .85;}

    // ───────────────────────────── timing ─────────────────────────────

    /**
     * A slash swept by the blade: drawn along its path from clip time {@code from}, the head riding the blade until
     * {@code to}, eaten from the tail over [eat0, eat1]; its edges lie {@code inner}..{@code outer} blocks along the
     * blade from the hand (reaching a little past the tip).
     */
    record Slash(float from,float to,float eat0,float eat1,int style,double inner,double outer,int seed){}
    static Slash slash(int combo){
        return switch(combo){
            case 2->new Slash(.21f,.40f,.31f,.47f,13,.3,1.9,5);
            case 3->new Slash(.235f,.305f,.33f,.5f,13,.25,2.0,9);
            case 4->new Slash(.585f,.80f,.735f,.9f,14,.25,2.2,13);
            default->new Slash(.21f,.385f,.30f,.45f,13,.3,1.85,1);
        };
    }
    static float kick(float t,float at,float tau){return FxLib.kick(t,at,tau);}
    /** Brightness of the blade's aura: lit for the swing, flaring as it strikes; hit 4 keeps it lit through the pin and the rising cut. */
    static float glow(float t,int combo){
        if(combo==4)return Ease.window(t,.2f,.92f,.05f,.14f)*(.75f+.25f*Ease.smooth(t,.5f,.7f))+.8f*kick(t,HIT,.08f)+.9f*kick(t,LAUNCH,.1f);
        float from=combo==3?.06f:.17f;
        return Ease.window(t,from,.46f,.04f,.1f)+.7f*kick(t,HIT,.07f);
    }
    /** How strongly hit 4's seal shows: stamped in with the thrust, tightening as the sword comes up, gone as it shatters. */
    static float sealed(float t){return t<HIT-.01f||t>=LAUNCH+.035f?0:Ease.smooth(t,HIT-.01f,HIT+.03f)*(1-Ease.smooth(t,LAUNCH,LAUNCH+.035f));}
    /** Its charge as the sword is drawn down and comes up: 0 through the pin, 1 at the launch. */
    static float charge(float t){return Ease.smooth(t,.5f,LAUNCH);}

    // ───────────────────────────── the slashes ─────────────────────────────

    static final int SAMPLES=36;
    /** The band the blade sweeps between clip times {@code from} and {@code head}: edges and the fraction along the tip's path. */
    record Swept(Vec3d[] inner,Vec3d[] outer,float[] along,int n){}
    static Swept swept(Fx c,int combo,float from,float head,double inner,double outer){
        int n=SAMPLES+1;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];double len=0;
        for(int i=0;i<n;i++){
            var hb=blade(c,combo,from+(head-from)*i/(n-1));
            in[i]=hb[0].add(hb[1].multiply(inner));out[i]=hb[0].add(hb[1].multiply(outer));
            if(i>0)len+=out[i].distanceTo(out[i-1]);along[i]=(float)len;
        }
        for(int i=0;i<n;i++)along[i]=len>1e-6?(float)(along[i]/len):i/(float)(n-1);
        return new Swept(in,out,along,n);
    }
    /** The slash of this hit as drawn now, or null when there is none. */
    static Swept slashNow(Fx c){
        float t=c.sec;Slash s=slash(c.cast.combo());if(t<s.from()||t>=s.eat1())return null;
        float head=Math.min(t,s.to());if(head<=s.from()+.004f)return null;
        return swept(c,c.cast.combo(),s.from(),head,s.inner(),s.outer());
    }
    /**
     * Every hit's slash along the blade's own path (crisp violet, the launching cut wider and hotter), drawn on as the
     * blade sweeps and eaten from the tail; hit 3 also leaves the tip's ring over the head as it turns in the air.
     */
    @Override public void crescents(Fx c){
        float t=c.sec;int combo=c.cast.combo();Slash s=slash(combo);
        var sw=slashNow(c);
        if(sw!=null){
            float a=Ease.smooth(t,s.from(),s.from()+.02f);
            KagenoFire.ribbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),Ease.smooth(t,s.eat0(),s.eat1()),1,a);
            // The launching cut has a thinner echo just inside it.
            float echo=Math.min(t,s.to());
            if(combo==4&&echo>s.from()+.025f){var e=swept(c,4,s.from()+.02f,echo,.6,1.6);KagenoFire.ribbon(e.inner(),e.outer(),e.along(),e.n(),13,14,Ease.smooth(t,s.eat0()-.02f,s.eat1()-.04f),1,.7f*a);}
        }
        if(combo==3&&t>=.07f&&t<.28f){
            float from=Math.max(.07f,t-.09f),head=Math.min(t,.235f);
            if(head>from+.004f){var r=swept(c,3,from,head,1.05,1.6);KagenoFire.ribbon(r.inner(),r.outer(),r.along(),r.n(),13,11,0,1,.35f*(1-Ease.smooth(t,.22f,.28f)));}
        }
    }

    // ───────────────────────────── light ─────────────────────────────

    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        aura(c,m,t,combo);
        if(combo==1)draw(c,m,t);
        impacts(c,m,t,combo,HIT);
        if(combo==3)slam(c,m,t,true);
        if(combo==4){lance(c,m,t,true);seal(c,m,t,true);launch(c,m,t,true);}
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==3)slam(c,m,t,false);
        if(combo==4){lance(c,m,t,false);seal(c,m,t,false);launch(c,m,t,false);}
    }
    /** The blade's violet aura while it cuts, brightest as it strikes. */
    static void aura(Fx c,EffectMesh m,float t,int combo){
        float g=glow(t,combo);if(g<=.01f)return;
        var hb=bladeNow(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));float on=Math.min(1,g);
        NeonThrustSkill.neon(m,guard,tip,.045*(1+.4*Math.max(0,Math.min(1,g-1))),Math.min(1.5f,g),AURA);
        m.softLine(guard,tip,.8,AURA[0],.2f*on);
        m.softDisc(tip,.14+.1*on,AURA[3],.7f*on);
    }
    /** Hit 1: a glint where the blade leaves the "sheath" at the hip. */
    static void draw(Fx c,EffectMesh m,float t){
        if(t<.19f||t>=.29f)return;
        float a=Ease.smooth(t,.19f,.215f)*(1-Ease.smooth(t,.24f,.29f));
        var hb=blade(c,1,Math.min(t,.24f));Vec3d p=hb[0].add(hb[1].multiply(.35));
        TimeWalkerSkill.glint(m,p,.55*a+.15,LILAC,a);
    }
    /** Every struck target: a white-violet burst and sparks thrown the way the blade went through. */
    static void impacts(Fx c,EffectMesh m,float t,int combo,float at){
        if(t<at-.01f||t>=at+.36f)return;
        var hb0=blade(c,combo,at-.012f);var hb1=blade(c,combo,at+.012f);
        Vec3d way=hb1[0].add(hb1[1].multiply(TIP)).subtract(hb0[0].add(hb0[1].multiply(TIP)));
        way=way.lengthSquared()<1e-6?c.f:way.normalize();
        Vec3d side=way.crossProduct(UP);if(side.lengthSquared()<1e-4)side=c.right;side=side.normalize();Vec3d up=side.crossProduct(way).normalize();
        float u=Ease.unit(t,at,at+.18f),a=Ease.smooth(t,at-.01f,at+.01f);
        boolean big=combo==4;
        for(var e:struck(c)){
            Vec3d p=chest(c,e);
            m.softDisc(p,(big?1.5:1.0)*(1-.4*u)+.2,VIOLET,.5f*a*(1-u));m.softDisc(p,(big?.55:.4)*(1-u)+.1,WHITE,.95f*a*(1-u));
            float s=a*(1-Ease.smooth(t,at+.08f,at+.3f));
            for(int k=0;k<(big?14:9);k++){
                double ang=(Ease.hash(k*3+combo*17+e.getId())-.5)*2.2,rise=(Ease.hash(k*5+combo*11)-.5)*1.6;
                Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(up.multiply(rise)).normalize();
                double len=(.5+.9*Ease.hash(k*7+combo))*(big?1.4:1)*Ease.out(t,at,at+.14f);
                Vec3d q=p.add(dir.multiply(len*.35));
                m.needle(q,q.add(dir.multiply(len*.65)),.06,.3,k%3==0?WHITE:LILAC,s);
            }
        }
    }
    /** Hit 3's slam (both passes): a flash where the blade meets the floor, a shock ring racing out over it and sparks; the dark scorch under it. */
    static void slam(Fx c,EffectMesh m,float t,boolean additive){
        if(t<HIT-.01f||t>=HIT+.55f)return;
        Vec3d p=slamPoint(c);float u=Ease.unit(t,HIT,HIT+.38f),a=Ease.smooth(t,HIT-.01f,HIT+.01f);
        if(!additive){
            // The floor goes dark round the impact (outside the effect layer, where there is no scorch decal).
            if(!FxLayer.available()){float g=a*(1-Ease.smooth(t,HIT+.2f,HIT+.55f));FxLib.glow(m,p.add(0,.03,0),2.4,INK,.55f*g);}
            return;
        }
        TimeWalkerSkill.glint(m,p.add(0,.15,0),1.5*(1-.5*u)+.3,VIOLET,a*(1-u));
        FxLib.ring(m,p.add(0,.06,0),.4+4.6*Ease.out(t,HIT,HIT+.38f),.5,LILAC,.85f*a*(1-u));
        FxLib.ring(m,p.add(0,.05,0),.3+2.6*Ease.out(t,HIT,HIT+.3f),.3,MAGENTA,.6f*a*(1-Ease.unit(t,HIT,HIT+.3f)));
        float s=a*(1-Ease.smooth(t,HIT+.1f,HIT+.4f));
        for(int k=0;k<16;k++){
            double az=Ease.hash(k*7+301)*Math.PI*2,el=.35+.9*Ease.hash(k*11+302),len=(.6+1.2*Ease.hash(k*13+303))*Ease.out(t,HIT,HIT+.16f);
            Vec3d dir=new Vec3d(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el));
            Vec3d q=p.add(0,.05,0).add(dir.multiply(len*.4));
            m.needle(q,q.add(dir.multiply(len*.6)),.06,.3,k%3==0?WHITE:PINK,s);
        }
    }
    /** Hit 4's thrust: a lance of light running on from the blade's point through the target (ink edges, violet body, white core). */
    static void lance(Fx c,EffectMesh m,float t,boolean additive){
        if(t<HIT-.03f||t>=HIT+.13f)return;
        float grow=Ease.out(t,HIT-.03f,HIT+.01f),a=Ease.smooth(t,HIT-.03f,HIT-.01f)*(1-Ease.smooth(t,HIT+.05f,HIT+.13f));
        var hb=blade(c,4,HIT);Vec3d tip=hb[0].add(hb[1].multiply(TIP)),dir=hb[1];
        Vec3d a0=tip.subtract(dir.multiply(.6)),a1=tip.add(dir.multiply(2.6*grow));
        if(!additive){m.needle(a0,a1,.34,.25,INK,.8f*a);return;}
        m.needle(a0,a1,.24,.25,VIOLET,a);m.needle(a0,a1.subtract(dir.multiply(.3)),.09,.25,WHITE,a);
        Vec3d p=sealPoint(c);Vec3d side=c.right,up=UP;
        FxLib.ringPlane(m,p,side,up,.25+1.3*Ease.out(t,HIT,HIT+.12f),.2,LILAC,.9f*a);
    }
    /**
     * Hit 4's seal round the pinned target, facing the caster: an ink-edged violet ring and a dashed one turning
     * the other way, runes riding between them, a six-pointed star inside, and three bands binding the body. It is
     * stamped in with the thrust, pulses while it holds, tightens and burns brighter as the sword comes up.
     */
    static void seal(Fx c,EffectMesh m,float t,boolean additive){
        float on=sealed(t);if(on<=0)return;
        Vec3d p=sealPoint(c);double size=sealSize(c);float k=charge(t);
        double scale=size*(1+.55*(1-Ease.out(t,HIT,HIT+.07f)))*(1-.16*k);
        float pulse=.85f+.15f*(float)Math.sin(t*38),bright=on*pulse*(1+1.1f*k);
        Vec3d u=c.right,v=UP,n=c.f;double spin=(t-HIT)*1.9,draw=Math.PI*2*Ease.out(t,HIT,HIT+.06f);
        double R=1.0*scale,r2=.74*scale;
        if(!additive){
            float ink=.85f*on;
            annulus(m,p,u,v,R-.075,R+.075,spin,draw,INK,ink);
            for(int i=0;i<8;i++)annulus(m,p,u,v,r2-.05,r2+.05,-spin*1.3+i*Math.PI/4,Math.PI/4*.62*Math.min(1,draw/(Math.PI*2)),INK,ink);
            for(int i=0;i<6;i++)rune(m,p,u,v,R*.87,spin+i*Math.PI/3,scale*1.3,INK,ink);
            star(m,p,u,v,n,R*.66,spin*.5,.06*scale,INK,ink*.9f);
            for(var e:struck(c))bind(c,m,e,t,INK,.8f*on,.11);
            return;
        }
        annulus(m,p,u,v,R-.04,R+.04,spin,draw,SEAL,bright);
        annulus(m,p,u,v,R-.012,R+.012,spin,draw,WHITE,.9f*bright);
        for(int i=0;i<8;i++)annulus(m,p,u,v,r2-.028,r2+.028,-spin*1.3+i*Math.PI/4,Math.PI/4*.62*Math.min(1,draw/(Math.PI*2)),LILAC,bright);
        for(int i=0;i<6;i++)rune(m,p,u,v,R*.87,spin+i*Math.PI/3,scale,MAGENTA,bright);
        star(m,p,u,v,n,R*.66,spin*.5,.03*scale,LILAC,.85f*bright);
        m.softDisc(p,R*1.4,DEEP,.3f*on);m.softDisc(p,R*.5,SEAL,.35f*bright);
        for(var e:struck(c))bind(c,m,e,t,SEAL,bright,.06);
        // The stamp: a flash as it closes on the target.
        float stamp=1-Ease.smooth(t,HIT,HIT+.12f);if(stamp>0)TimeWalkerSkill.glint(m,p,1.2*stamp+.2,SEAL,stamp*on);
        // Motes drawn into the blade as it comes up through the seal.
        var hb=bladeNow(c);FxLib.converge(m,hb[0].add(hb[1].multiply(.9)),1.8,14,t,.5f,LAUNCH-.06f,LILAC,41);
    }
    /** A flat ring band in the plane (u,v): radii r0..r1, from angle a0 over len (radians). */
    static void annulus(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double r0,double r1,double a0,double len,int rgb,float alpha){
        if(alpha<=0||len<=1e-4||r1<=r0)return;int n=Math.max(4,(int)Math.ceil(len/(Math.PI*2)*72));
        for(int i=0;i<n;i++){
            double x0=a0+len*i/n,x1=a0+len*(i+1)/n;
            Vec3d d0=u.multiply(Math.cos(x0)).add(v.multiply(Math.sin(x0))),d1=u.multiply(Math.cos(x1)).add(v.multiply(Math.sin(x1)));
            m.quad(o.add(d0.multiply(r0)),o.add(d1.multiply(r0)),o.add(d1.multiply(r1)),o.add(d0.multiply(r1)),rgb,alpha);
        }
    }
    /** A rune: a small diamond pointing out from the centre at angle {@code a} on radius {@code r}. */
    static void rune(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double r,double a,double s,int rgb,float alpha){
        Vec3d out=u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a))),along=u.multiply(-Math.sin(a)).add(v.multiply(Math.cos(a)));
        Vec3d q=o.add(out.multiply(r));double l=.085*s,w=.045*s;
        m.quad(q.add(out.multiply(l)),q.add(along.multiply(w)),q.subtract(out.multiply(l)),q.subtract(along.multiply(w)),rgb,alpha);
    }
    /** Two interlaced triangles inscribed in radius {@code r}, drawn as flat lines {@code width} wide. */
    static void star(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,Vec3d n,double r,double a,double width,int rgb,float alpha){
        for(int tri=0;tri<2;tri++)for(int i=0;i<3;i++){
            double x0=a+tri*Math.PI/3+i*Math.PI*2/3,x1=x0+Math.PI*2/3;
            Vec3d p0=o.add(u.multiply(Math.cos(x0)*r)).add(v.multiply(Math.sin(x0)*r)),p1=o.add(u.multiply(Math.cos(x1)*r)).add(v.multiply(Math.sin(x1)*r));
            Vec3d side=n.crossProduct(p1.subtract(p0)).normalize().multiply(width*.5);
            m.quad(p0.subtract(side),p1.subtract(side),p1.add(side),p0.add(side),rgb,alpha);
        }
    }
    /** Three dashed bands binding the target's body (upright strips round it, tilted, turning). */
    static void bind(Fx c,EffectMesh m,LivingEntity e,float t,int rgb,float alpha,double height){
        Vec3d base=e.getLerpedPos(c.delta);double r=Math.max(.42,e.getWidth()*.78),h=e.getHeight();
        float grow=Ease.out(t,HIT+.02f,HIT+.1f);if(grow<=0)return;
        for(int b=0;b<3;b++){
            double y=h*(.28+.27*b),tilt=Math.toRadians(b==1?-9:11),turn=(b%2==0?1:-1)*(t-HIT)*2.4+b;
            Vec3d o=base.add(0,y,0);
            for(int s=0;s<6;s++){
                double a0=turn+s*Math.PI/3,len=Math.PI/3*.68*grow;int n=5;
                for(int i=0;i<n;i++){
                    double x0=a0+len*i/n,x1=a0+len*(i+1)/n;
                    Vec3d q0=o.add(Math.cos(x0)*r,Math.sin(x0)*Math.sin(tilt)*r,Math.sin(x0)*Math.cos(tilt)*r),q1=o.add(Math.cos(x1)*r,Math.sin(x1)*Math.sin(tilt)*r,Math.sin(x1)*Math.cos(tilt)*r);
                    m.quad(q0.add(0,-height*.5,0),q1.add(0,-height*.5,0),q1.add(0,height*.5,0),q0.add(0,height*.5,0),rgb,alpha);
                }
            }
        }
    }
    /** Shards the seal breaks into at the launch: where on the seal each starts (angle, radius fraction), its flight and spin. */
    static final int SHARDS=18;
    static Vec3d shardAt(Fx c,Vec3d p,double seal,int i,float age){
        double size=seal*.84,a=Ease.hash(i*7+501)*Math.PI*2,r=size*(.45+.6*Ease.hash(i*11+502));
        Vec3d out=c.right.multiply(Math.cos(a)).add(UP.multiply(Math.sin(a)));
        Vec3d v=out.multiply(2.5+4*Ease.hash(i*13+503)).add(c.f.multiply(1.5+2.5*Ease.hash(i*17+504))).add(0,1.2,0);
        double fly=(1-Math.exp(-age/.3))*.3;
        return p.add(out.multiply(r)).add(v.multiply(fly)).add(0,-1.6*age*age,0);
    }
    /** Hit 4's launch (both passes): the seal shatters into tumbling shards, a burst and rings at the target, and each launched target trails violet light. */
    static void launch(Fx c,EffectMesh m,float t,boolean additive){
        if(t<LAUNCH-.01f||t>=LAUNCH+.5f)return;
        float age=t-LAUNCH,a=Ease.smooth(t,LAUNCH-.01f,LAUNCH+.01f);Vec3d p=sealPoint(c);double seal=sealSize(c);var hit=struck(c);
        for(int i=0;i<SHARDS;i++){
            float fade=a*(1-Ease.smooth(age,.18f+.12f*(float)Ease.hash(i*19+505),.42f+.06f*(float)Ease.hash(i*23+506)));if(fade<=0)continue;
            Vec3d q=shardAt(c,p,seal,i,Math.max(0,age));double s=(.1+.12*Ease.hash(i*29+507))*(1-.5*Ease.unit(age,0,.45f));
            double spin=age*(8+10*Ease.hash(i*31+508))+i;
            Vec3d ax=new Vec3d(Math.cos(spin),Math.sin(spin*.7),Math.sin(spin)).normalize(),ay=ax.crossProduct(new Vec3d(.3,1,.2)).normalize();
            Vec3d v0=q.add(ax.multiply(s)),v1=q.add(ay.multiply(s*.8)).subtract(ax.multiply(s*.5)),v2=q.subtract(ay.multiply(s*.9)).subtract(ax.multiply(s*.3));
            if(!additive){m.triangle(v0,v1,v2,0x2a0a6e,.85f*fade);continue;}
            m.triangle(v0,v1,v2,VIOLET,.35f*fade);
            m.line(v0,v1,.025,LILAC,fade);m.line(v1,v2,.025,LILAC,fade);m.line(v2,v0,.025,WHITE,fade);
        }
        if(!additive)return;
        float u=Ease.unit(t,LAUNCH,LAUNCH+.3f);
        TimeWalkerSkill.glint(m,p,1.9*(1-.4*u)+.3,SEAL,a*(1-u));
        FxLib.ringPlane(m,p,c.right,UP,.5+2.2*Ease.out(t,LAUNCH,LAUNCH+.3f),.3,LILAC,.6f*a*(1-u));
        FxLib.ringPlane(m,p.add(0,-.5,0),c.right,c.f,.4+2.6*Ease.out(t,LAUNCH,LAUNCH+.25f),.3,MAGENTA,.7f*a*(1-Ease.unit(t,LAUNCH,LAUNCH+.25f)));
        float s=a*(1-Ease.smooth(age,.08f,.3f));
        for(int k=0;k<12;k++){
            double az=(Ease.hash(k*3+601)-.5)*1.6,el=1.0+.5*Ease.hash(k*5+602),len=(.8+1.4*Ease.hash(k*7+603))*Ease.out(t,LAUNCH,LAUNCH+.14f);
            Vec3d dir=c.right.multiply(Math.sin(az)*Math.cos(el)).add(c.f.multiply(Math.cos(az)*Math.cos(el)*.6)).add(0,Math.sin(el),0).normalize();
            Vec3d q=p.add(dir.multiply(len*.3));m.needle(q,q.add(dir.multiply(len*.7)),.07,.3,k%3==0?WHITE:PINK,s);
        }
        // Launched targets trail light behind them.
        float trail=Ease.smooth(age,.02f,.06f)*(1-Ease.smooth(age,.3f,.5f));
        if(trail>0)for(var e:hit){Vec3d q=chest(c,e);Vec3d back=p.subtract(q);double l=back.length();if(l<.2)continue;Vec3d end=q.add(back.multiply(Math.min(1,2.2/l)));m.softTaper(q,end,.7,.05,VIOLET,.7f*trail);m.softTaper(q,end,.22,.02,WHITE,.8f*trail);}
    }

    // ───────────────────────────── smoke and dust ─────────────────────────────

    /**
     * Fire-shader cells on fixed planes: dark violet smoke rising round hit 4's sealed target and blown out by the
     * launch; on the effect layer, dust kicked up by hit 2's step, hit 3's slam and hit 4's lunge and launch.
     */
    @Override public void shaders(Fx c){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==4&&t>=HIT&&t<LAUNCH+.45f){
            List<Vec3d> pts=new ArrayList<>();List<float[]> cells=new ArrayList<>();Vec3d p=sealPoint(c);
            float a=Ease.smooth(t,HIT,HIT+.1f)*(1-Ease.smooth(t,LAUNCH+.1f,LAUNCH+.45f)),blow=Ease.out(t,LAUNCH,LAUNCH+.4f);
            for(int i=0;i<7;i++){
                double az=Ease.hash(i*3+701)*Math.PI*2,rad=(.45+.5*Ease.hash(i*5+702))*(1+1.8*blow);
                float rise=(float)(((t-HIT)*.9+Ease.hash(i*7+703))%1);
                Vec3d q=p.add(c.right.multiply(Math.cos(az)*rad)).add(c.f.multiply(Math.sin(az)*rad*.6)).add(0,-.35+1.1*rise+.8*blow,0);
                pts.add(q);cells.add(new float[]{(float)((.42+.22*Ease.hash(i*11+704))*(1+.6*blow)),.55f*a*(float)Math.sin(Math.PI*rise),.3f+.12f*(float)Ease.hash(i*13+705),.3f,i*31+5});
            }
            KagenoFire.cells(c.matrix,c.camera,pts,cells,c.age);
        }
        if(!FxLayer.available())return;
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        if(combo==2)skid(c,t,HIT,.55,dust,puffs,3,11);
        if(combo==4)skid(c,t,HIT,.85,dust,puffs,3,23);
        if(combo==3)FxLib.dustRing(slamPoint(c),t,HIT,.7,2.8,16,1,dust,puffs,31);
        if(combo==4){Vec3d p=sealPoint(c);FxLib.dustRing(new Vec3d(p.x,anchor(c).y,p.z),t,LAUNCH,.5,2.2,10,1,dust,puffs,47);}
        if(!dust.isEmpty())KagenoFire.dust(c.matrix,c.camera,dust,puffs,c.age);
    }
    /** A few puffs kicked up at the front foot by a step (it lands {@code fw} blocks ahead of the cast spot). */
    static void skid(Fx c,float t,float at,double fw,List<Vec3d> dust,List<float[]> puffs,int n,int seed){
        for(int i=0;i<n;i++){
            float s=t-at-.02f*i,life=.55f+.2f*(float)Ease.hash(seed+i*3);if(s<0||s>=life)continue;float u=s/life;
            Vec3d q=anchor(c).add(c.f.multiply(fw+.15*i)).add(c.right.multiply(.2+(Ease.hash(seed+i*5)-.5)*.5)).add(0,.18+.3*u,0).subtract(c.f.multiply(.5*Ease.out(u,0,.6f)));
            dust.add(q);puffs.add(new float[]{(float)((.22+.22*Ease.out(u,0,.5f))*(.8+.4*Ease.hash(seed+i*7))),.85f*Ease.smooth(s,0,.04f)*(1-Ease.smooth(u,.5f,1)),.45f*(1-u),u,seed+i*29});
        }
    }
    // ───────────────────────────── effect layer ─────────────────────────────

    /**
     * Lights: the blade's aura as it cuts (brightest at the strike), a white-violet flash on every struck target,
     * hit 3's flash on the floor, hit 4's seal (brighter as it charges) and the launch's burst with a light shaft and
     * a slight colour split for viewers close by. Decals: hit 3's gash torn in the floor along the slam, glowing and
     * cooling; a short burn under hit 4's target where it was launched.
     */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;int combo=c.cast.combo();
        float g=Math.min(1.4f,glow(t,combo));
        if(g>.02f){var hb=bladeNow(c);f.light(hb[0].add(hb[1].multiply(.85)),AURA[1],4.2,.85f*g);}
        float hit=kick(t,HIT,.09f);
        if(hit>.01f)for(var e:struck(c))f.light(chest(c,e),0xe8c8ff,combo==4?7:5.5,(combo==4?2.4f:1.8f)*hit);
        float near=(float)Math.max(0,Math.min(1,(22-c.camera.distanceTo(anchor(c)))/10));
        if(combo==3&&t>=HIT-.01f){
            Vec3d p=slamPoint(c);
            f.light(p.add(0,.5,0),0xc77dff,9,2.8f*kick(t,HIT,.14f)+.5f*Ease.window(t,HIT,HIT+.6f,.04f,.4f));
            float heat=1-Ease.smooth(t,HIT+.1f,HIT+.75f),al=Ease.smooth(t,HIT,HIT+.04f)*(1-Ease.smooth(t,1.0f,1.18f));double open=Ease.out(t,HIT,HIT+.1f);
            f.decal(p,c.f,1.5*open+.2,.55,heat,al,5.3f);
            f.decal(p.add(c.right.multiply(.2)),c.f.multiply(.5).add(c.right.multiply(.87)),.9*open+.15,.32,heat*.8f,.85f*al,9.1f);
            f.decal(p.subtract(c.right.multiply(.2)),c.f.multiply(.5).subtract(c.right.multiply(.87)),.8*open+.15,.3,heat*.8f,.85f*al,13.7f);
            if(near>0)f.shaft(p.add(0,.3,0),0xe0b8ff,.18f*near*kick(t,HIT,.12f));
        }
        if(combo==4){
            float on=sealed(t);Vec3d p=sealPoint(c);
            if(on>0)f.light(p,SEAL,5.5,(.7f+1.4f*charge(t))*on*(.9f+.1f*(float)Math.sin(t*38)));
            if(t>=LAUNCH-.01f){
                f.light(p,0xffd0f8,11,3.2f*kick(t,LAUNCH,.16f));
                f.light(p.add(0,1,0),0xb050ff,8,.8f*Ease.window(t,LAUNCH+.05f,1.15f,.08f,.3f));
                if(near>0){f.shaft(p,0xf0c8ff,.42f*near*kick(t,LAUNCH,.2f));f.chroma(near*.006f*kick(t,LAUNCH,.1f));}
                float heat=1-Ease.smooth(t,LAUNCH+.05f,LAUNCH+.4f),al=Ease.smooth(t,LAUNCH,LAUNCH+.04f)*(1-Ease.smooth(t,1.02f,1.18f));
                f.decal(new Vec3d(p.x,anchor(c).y,p.z),c.right,.9*Ease.out(t,LAUNCH,LAUNCH+.1f)+.2,.42,heat,al,17.9f);
            }
        }
    }
    /** The air bends along each slash as it sweeps; hit 3 sends a shock ring over the floor; hit 4's thrust and launch ring the target. */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;int combo=c.cast.combo();Slash s=slash(combo);
        var sw=slashNow(c);
        if(sw!=null)d.ribbon(sw.inner(),sw.outer(),sw.n(),(combo==4?.8f:.5f)*Ease.smooth(t,s.from(),s.from()+.03f)*(1-Ease.smooth(t,s.to(),s.eat1())));
        if(combo==3&&t>=HIT-.01f&&t<HIT+.45f){
            float in=Ease.smooth(t,HIT-.01f,HIT+.015f);
            d.ring(slamPoint(c).add(0,.05,0),c.right,c.f,.5+7*Ease.out(t,HIT,HIT+.45f),1.5,.85f*in*(1-Ease.unit(t,HIT,HIT+.45f)));
        }
        if(combo==4){
            Vec3d p=sealPoint(c);
            if(t>=HIT-.01f&&t<HIT+.22f)d.ring(p,c.right,UP,.3+2.4*Ease.out(t,HIT,HIT+.22f),.8,.6f*Ease.smooth(t,HIT-.01f,HIT+.015f)*(1-Ease.unit(t,HIT,HIT+.22f)));
            if(t>=LAUNCH-.01f&&t<LAUNCH+.35f){
                float in=Ease.smooth(t,LAUNCH-.01f,LAUNCH+.015f);
                d.ring(p,c.right,UP,.5+4.5*Ease.out(t,LAUNCH,LAUNCH+.35f),1.2,.85f*in*(1-Ease.unit(t,LAUNCH,LAUNCH+.35f)));
                d.ring(new Vec3d(p.x,anchor(c).y+.05,p.z),c.right,c.f,.5+5.5*Ease.out(t,LAUNCH,LAUNCH+.35f),1.3,.7f*in*(1-Ease.unit(t,LAUNCH,LAUNCH+.35f)));
            }
        }
    }
    /** Chunks torn from the floor: hit 3's slam throws them out round the impact (and the floor heaves in a ring); hit 4's launch throws a few up under the target. */
    @Override public void solids(Fx c,FxSolids s){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==3&&t>=HIT&&t<1.2f){Vec3d p=slamPoint(c);FxLib.chunks(s,p,t-HIT,12,801,3.2,6.2,.18,.55f);FxLib.heave(s,p,t-HIT,4.6,.22,13,.75,.6);}
        if(combo==4&&t>=LAUNCH&&t<1.2f){Vec3d p=sealPoint(c);FxLib.chunks(s,new Vec3d(p.x,anchor(c).y,p.z),t-LAUNCH,7,901,1.6,7,.18,.3f);}
    }

    // ───────────────────────────── camera ─────────────────────────────

    /** Shake as each hit lands, harder with the slam and the launch; each comes in over two frames. */
    @Override public float shake(StandClient.Cast cast,float sec){
        int combo=cast.combo();
        float s=(combo==3?1.3f:combo==4?.55f:combo==2?.42f:.32f)*bump(sec,HIT,combo==3?.12f:.07f);
        if(combo==4)s=Math.max(s,1.15f*bump(sec,LAUNCH,.12f));
        return s;
    }
    /** The slam nods the camera down a little, the launch lifts it. */
    @Override public float[] kick(StandClient.Cast cast,float sec){
        int combo=cast.combo();
        if(combo==3&&sec>=HIT-.02f&&sec<HIT+.6f)return new float[]{0,1.1f*bump(sec,HIT,.14f),0};
        if(combo==4&&sec>=LAUNCH-.02f&&sec<LAUNCH+.6f)return new float[]{0,-1.3f*bump(sec,LAUNCH,.16f),0};
        return null;
    }
    static float bump(float sec,float at,float tau){return sec<at-.02f?0:Ease.smooth(sec,at-.02f,at+.015f)*(float)Math.exp(-Math.max(0,sec-at-.015f)/tau);}
    @Override public int afterimageColor(){return 0x9a6cff;}
}
