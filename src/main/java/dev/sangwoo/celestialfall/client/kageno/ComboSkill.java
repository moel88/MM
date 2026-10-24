package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
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
 * <li>관통 찌르기: drawn back side-on with light gathering at the point, a lunge thrust drives a lance of light through
 * the target and sends it flying along the thrust (the server launches it with the blow); the body drives on through,
 * holds while the target flies and flicks the blade clean.</li>
 * </ol>
 * Each slash is drawn along the path the blade really sweeps (crisp violet bands: an ink line on the tip edge, a thin
 * white core, a burning tail), and on the effect layer it lights the world and bends the air behind it. Seconds after
 * the click; every effect is a function of the cast clock, so it moves at the render frame rate.
 */
final class ComboSkill implements Fx.Skill {
    static final float HIT=.30f;
    static final Vec3d UP=new Vec3d(0,1,0);
    /** Ink, deep purple, violet, lilac, bright violet, magenta, pink, white. */
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
    static final R6.Pose H1_GATHER=w(-.08,-.16,48,12,-4,d(-60,-62),d(-24,-156),d(-64,-116),d(-74,30),d(-80,202));
    static final R6.Pose H1_COIL=w(-.1,-.2,56,14,-5,d(-62,-70),d(-22,-164),d(-66,-120),d(-73,32),d(-79,204));
    /** Halfway: the hips have led, the hand sweeps low across the front and the blade comes out of the sheath. */
    static final R6.Pose H1_DRAW=w(.08,-.15,6,10,0,d(-30,-24),d(-8,-50),d(-52,-130),d(-70,26),d(-78,198));
    /** The cut: rising through the front right with a lunge, the free hand thrown back. */
    static final R6.Pose H1_CUT=w(.24,-.12,-34,8,3,d(10,32),d(30,44),d(-36,-140),d(-58,18),d(-72,192));
    /** Followed through high to the right and behind, the hips wound round past the front. */
    static final R6.Pose H1_END=w(.28,-.08,-56,0,4,d(46,92),d(60,128),d(-28,-128),d(-64,16),d(-76,194));
    static final Anim H1=Anim.joints(
        k(0,KagenoClips.IDLE),
        k(.10,H1_GATHER),
        // The coil keeps winding (a moving hold); then the hips lead and the hand whips the blade up across the front.
        k(.20,H1_COIL),
        k(.255,H1_DRAW,Anim.Ease.LINEAR),
        k(HIT,H1_CUT,Anim.Ease.LINEAR),
        k(.40,H1_END,Anim.Ease.OUT),
        k(.62,w(.26,-.08,-51,2,3,d(42,88),d(56,122),d(-32,-124),d(-66,16),d(-76,194))),
        k(.95,KagenoClips.IDLE)).stops(Anim.stop(HIT,.05,.09));

    // ───────────────────────────── 2 되베기 ─────────────────────────────
    /** Raised: the sword over the right shoulder pointing back, the hips turned right, the free hand out in front. */
    static final R6.Pose H2_RAISE=w(.26,.02,-60,-6,5,d(66,74),d(64,164),d(-12,-56),d(-72,20),d(-78,196));
    static final R6.Pose H2_LOAD=w(.24,.05,-66,-9,6,d(70,82),d(58,176),d(-8,-50),d(-74,20),d(-79,196));
    /** Halfway down: over the front right, the hips coming round. */
    static final R6.Pose H2_MID=w(.32,-.08,-14,10,0,d(28,24),d(22,30),d(-24,-100),d(-66,18),d(-72,194));
    /** The cut: down across the front to the left with a deep step in. */
    static final R6.Pose H2_CUT=w(.42,-.22,32,20,-3,d(-24,-22),d(-32,-38),d(-30,-154),d(-56,16),d(-66,190));
    /** Followed through low behind the left. */
    static final R6.Pose H2_END=w(.46,-.28,54,22,-4,d(-48,-66),d(-40,-124),d(-42,-164),d(-54,14),d(-64,188));
    static final Anim H2=Anim.joints(
        k(0,H1_END),
        k(.12,H2_RAISE),
        k(.20,H2_LOAD),
        k(.255,H2_MID,Anim.Ease.LINEAR),
        k(HIT,H2_CUT,Anim.Ease.LINEAR),
        k(.42,H2_END,Anim.Ease.OUT),
        k(.62,w(.44,-.26,50,20,-3,d(-46,-62),d(-40,-118),d(-44,-160),d(-55,14),d(-65,188))),
        k(.95,KagenoClips.IDLE)).stops(Anim.stop(HIT,.055,.1));

    // ───────────────────────────── 3 회전 내려찍기 ─────────────────────────────
    // Body-space keys: the full turn is the root's yaw (to the left, carrying on from hit 2), the limbs ride with it.
    /** Down for the leap: the sword gathered up in front. */
    static final R6.Pose H3_CROUCH=pose().at(.48,-.4,0).body(44,28,-2).head(38,-18,0).rArm(-18,12).lArm(-42,-70).blade(4,8).rLeg(-56,52).lLeg(-62,-132);
    /** Rising and turning, the sword going up overhead. */
    static final R6.Pose H3_RISE=pose().at(.52,.42,0).body(150,-6,0).head(0,0,0).rArm(72,20).lArm(-20,-80).blade(62,150).rLeg(-68,18).lLeg(-76,-162);
    /** The top of the leap: turned most of the way round, the sword loaded overhead pointing back. */
    static final R6.Pose H3_TOP=pose().at(.56,.62,0).body(300,-10,0).head(0,-4,0).rArm(86,10).lArm(-10,-95).blade(28,178).rLeg(-60,12).lLeg(-70,-172);
    /** Coming down facing the front, the sword still loaded (a moving hold before the strike). */
    static final R6.Pose H3_LOADED=pose().at(.6,.4,0).body(350,-8,0).head(0,-8,0).rArm(84,-24).lArm(-14,-100).blade(30,196).rLeg(-62,14).lLeg(-70,-168);
    /** The slam: landed facing the front, leaning into it, the blade's point on the ground ahead. */
    static final R6.Pose H3_SLAM=pose().at(.64,-.2,0).body(358,28,0).head(4,-20,0).rArm(-26,14).lArm(-50,-120).blade(-13,16).rLeg(-58,30).lLeg(-64,-150);
    static final R6.Pose H3_END=pose().at(.62,-.3,0).body(358,30,0).head(4,-22,0).rArm(-28,16).lArm(-58,-110).blade(-16,18).rLeg(-58,30).lLeg(-64,-148);
    static final Anim H3=Anim.joints(
        k(0,H2_END),
        k(.06,H3_CROUCH),
        // The turn in the air comes first; the sword comes straight down over the head once the body faces the front.
        k(.12,H3_RISE),
        k(.19,H3_TOP),
        k(.235,H3_LOADED),
        k(HIT,H3_SLAM,Anim.Ease.LINEAR),
        k(.40,pose().at(.64,-.34,0).body(358,32,0).head(4,-24,0).rArm(-30,15).lArm(-56,-120).blade(-17,17).rLeg(-56,32).lLeg(-62,-150),Anim.Ease.OUT),
        k(.62,H3_END),
        // A whole turn on: the rest pose faces the same way as the start.
        k(.95,KagenoClips.IDLE.body(360,0,0))).stops(Anim.stop(HIT,.08,.13));

    // ───────────────────────────── 4 관통 찌르기 ─────────────────────────────
    /** Drawn back: side-on, the hand at the right hip with the blade level at the target, the free hand aiming. */
    static final R6.Pose H4_DRAW=w(.5,-.22,-34,6,0,d(-50,162),d(0,4),d(-6,-12),d(-72,22),d(-76,192));
    static final R6.Pose H4_LOAD=w(.42,-.28,-42,4,0,d(-48,170),d(2,2),d(-2,-8),d(-72,24),d(-76,194));
    /** The thrust: the sword shoulder driven through, a long lunge, the free arm thrown back. */
    static final R6.Pose THRUST=w(.78,-.28,46,12,0,d(-4,-8),d(0,-2),d(-26,-172),d(-52,16),d(-58,192));
    /** Driven on through after the blow: the body follows the blade, held there while the target flies. */
    static final R6.Pose H4_THROUGH=w(.88,-.32,50,14,0,d(-3,-6),d(1,-2),d(-28,-176),d(-50,16),d(-58,192));
    /** The blade flicked clean, down and out to the right, as the body comes back up. */
    static final R6.Pose H4_FLICK=w(.62,-.14,12,8,0,d(-42,38),d(-46,64),d(-56,-96),d(-66,14),d(-72,190));
    static final Anim H4=Anim.joints(
        // Hit 3 ended a whole turn on; this clip starts at the same pose with the turn taken off.
        k(0,H3_END.body(-2,30,0)),
        k(.12,H4_DRAW),
        k(.24,H4_LOAD),
        k(HIT,THRUST,Anim.Ease.IN),
        k(.38,H4_THROUGH,Anim.Ease.OUT),
        // Held out along the thrust (a moving hold) while the target flies, then pulled back and flicked clean.
        k(.6,w(.86,-.3,48,13,0,d(-4,-6),d(0,-2),d(-30,-174),d(-51,16),d(-58,192))),
        k(.76,H4_FLICK),
        k(.86,w(.56,-.1,8,6,0,d(-48,34),d(-52,58),d(-58,-90),d(-68,14),d(-74,190)),Anim.Ease.OUT),
        k(1.05,KagenoClips.IDLE)).stops(Anim.stop(HIT,.08,.12));

    @Override public Anim clip(int combo){return hit(combo);}
    static Anim hit(int combo){return switch(combo){case 2->H2;case 3->H3;case 4->H4;default->H1;};}
    @Override public float lifetime(){return 1.25f;}
    @Override public boolean layered(){return true;}

    // ───────────────────────────── where things are ─────────────────────────────

    /**
     * What the blow left, held from the first frame at or past it: the caster's feet, whom it struck and their chests
     * as it landed (hit 4's targets fly off out of the blade's reach from there).
     */
    private record Memo(Vec3d feet,int[] ids,Vec3d[] at){}
    private static final Map<UUID,Memo> MEMOS=new LinkedHashMap<>();
    static Memo blow(Fx c){
        var m=MEMOS.get(c.cast.id());if(m!=null)return m;
        var hit=near(c,c.pos);
        m=new Memo(c.pos,hit.stream().mapToInt(LivingEntity::getId).toArray(),hit.stream().map(e->chest(c,e)).toArray(Vec3d[]::new));
        MEMOS.put(c.cast.id(),m);
        while(MEMOS.size()>32)MEMOS.remove(MEMOS.keySet().iterator().next());
        return m;
    }
    /** The caster's feet: following them up to the blow, then held where it fell (the floor and the air react there). */
    static Vec3d anchor(Fx c){return c.sec<HIT?c.pos:blow(c).feet();}
    /** Who the blade can strike from {@code at}, picked as the server does (within 3.4 blocks, not behind). */
    static List<LivingEntity> near(Fx c,Vec3d at){
        var world=c.mc.world;if(world==null)return List.of();
        UUID caster=c.cast.caster();
        var out=world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(4.4),e->e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!e.getUuid().equals(caster)&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&e.getPos().squaredDistanceTo(at)<=3.4*3.4&&e.getPos().subtract(at).multiply(1,0,1).normalize().dotProduct(c.f)>-.05);
        return out.size()>4?out.subList(0,4):out;
    }
    /** Who the blade strikes: those in reach up to the blow, then the ones it struck, with the server's list once it comes. */
    static List<LivingEntity> struck(Fx c){
        if(c.sec<HIT)return c.targets.isEmpty()?near(c,c.pos):c.targets;
        var world=c.mc.world;if(world==null)return List.of();
        List<LivingEntity> out=new ArrayList<>();
        for(int id:blow(c).ids())if(world.getEntityById(id) instanceof LivingEntity e&&e.isAlive())out.add(e);
        for(var e:c.targets)if(!out.contains(e))out.add(e);
        return out;
    }
    static Vec3d chest(Fx c,LivingEntity e){return e.getLerpedPos(c.delta).add(0,e.getHeight()*.58,0);}
    /** Where {@code e} was as the blow struck it: its chest held at the blow, else where the server says it stood. */
    static Vec3d struckAt(Fx c,LivingEntity e){
        var m=blow(c);for(int i=0;i<m.ids().length;i++)if(m.ids()[i]==e.getId())return m.at()[i];
        for(var t:c.cast.targets())if(t.entity()==e.getId())return t.position().add(0,e.getHeight()*.58,0);
        return chest(c,e);
    }
    /** Sword hand and blade direction at clip time {@code sec} of hit {@code combo}, on the caster as drawn. */
    static Vec3d[] blade(Fx c,int combo,float sec){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(hit(combo).frame(sec)));}
    static Vec3d[] bladeNow(Fx c){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(KagenoClips.action(c.cast,c.delta)));}
    /** Where hit 3's blade meets the floor: under the tip at the slam, on the caster's floor. */
    static Vec3d slamPoint(Fx c){
        var hb=KagenoClips.blade(anchor(c),c.cast.yaw(),R6.poseOf(H3.frame(HIT)));Vec3d tip=hb[0].add(hb[1].multiply(TIP));
        return new Vec3d(tip.x,anchor(c).y,tip.z);
    }
    /** Where hit 4's thrust lands: the first struck target's chest (following it up to the blow), else just past the blade's point. */
    static Vec3d impact(Fx c){
        if(c.sec<HIT){for(var e:struck(c))return chest(c,e);return c.pos.add(c.f.multiply(2.3)).add(0,1.15,0);}
        var m=blow(c);return m.at().length>0?m.at()[0]:m.feet().add(c.f.multiply(2.3)).add(0,1.15,0);
    }
    /** The floor under a point, at the caster's floor. */
    static Vec3d floor(Fx c,Vec3d p){return new Vec3d(p.x,anchor(c).y,p.z);}

    // ───────────────────────────── timing ─────────────────────────────

    /**
     * A slash swept by the blade: drawn along its path from clip time {@code from}, the head riding the blade until
     * {@code to}, eaten from the tail over [eat0, eat1]; its edges lie {@code inner}..{@code outer} blocks along the
     * blade from the hand (reaching a little past the tip).
     */
    record Slash(float from,float to,float eat0,float eat1,int style,double inner,double outer,int seed){}
    /** The slash of hits 1-3; hit 4 is a thrust (its lance), not a slash. */
    static Slash slash(int combo){
        return switch(combo){
            case 2->new Slash(.21f,.40f,.31f,.47f,13,.2,2.55,5);
            case 3->new Slash(.235f,.305f,.33f,.5f,13,.18,2.7,9);
            case 4->null;
            default->new Slash(.21f,.385f,.30f,.45f,13,.2,2.5,1);
        };
    }
    static float kick(float t,float at,float tau){return FxLib.kick(t,at,tau);}
    /** Brightness of the blade's aura: lit for the swing, flaring as it strikes; hit 4 lights it as it is drawn back and holds it out along the thrust. */
    static float glow(float t,int combo){
        if(combo==4)return Ease.window(t,.12f,.66f,.05f,.16f)*(.7f+.3f*Ease.smooth(t,.12f,.28f))+1.0f*kick(t,HIT,.09f);
        float from=combo==3?.06f:.17f;
        return Ease.window(t,from,.46f,.04f,.1f)+.7f*kick(t,HIT,.07f);
    }
    /** Hit 4's lance: how far it has shot past the blade's point (blocks) and how bright it is. */
    static final double LANCE=5.5;
    static float lanceLit(float t){return Ease.smooth(t,HIT-.035f,HIT+.005f)*(1-Ease.smooth(t,HIT+.07f,HIT+.2f));}
    /** Hit 4's struck targets in flight: their trail comes in with the blow and is gone as they come down. */
    static float flying(float t){return t<HIT?0:Ease.smooth(t,HIT,HIT+.04f)*(1-Ease.smooth(t,HIT+.42f,HIT+.7f));}

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
        float t=c.sec;Slash s=slash(c.cast.combo());if(s==null||t<s.from()||t>=s.eat1())return null;
        float head=Math.min(t,s.to());if(head<=s.from()+.004f)return null;
        return swept(c,c.cast.combo(),s.from(),head,s.inner(),s.outer());
    }
    /**
     * Every slash along the blade's own path (crisp violet), drawn on as the blade sweeps and eaten from the tail;
     * hit 3 also leaves the tip's ring over the head as it turns in the air.
     */
    @Override public void crescents(Fx c){
        float t=c.sec;int combo=c.cast.combo();Slash s=slash(combo);
        var sw=slashNow(c);
        if(sw!=null){
            float a=Ease.smooth(t,s.from(),s.from()+.02f);
            KagenoFire.ribbon(sw.inner(),sw.outer(),sw.along(),sw.n(),s.style(),s.seed(),Ease.smooth(t,s.eat0(),s.eat1()),1,a);
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
        sparks(c,m,t,combo);
        if(combo==1)draw(c,m,t);
        impacts(c,m,t,combo,HIT);
        if(combo==3)slam(c,m,t,true);
        if(combo==4){gather(c,m,t);lance(c,m,t,true);shock(c,m,t);trail(c,m,t);}
    }
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==3)slam(c,m,t,false);
        if(combo==4)lance(c,m,t,false);
    }
    /** The blade's violet aura while it cuts, brightest as it strikes. */
    static void aura(Fx c,EffectMesh m,float t,int combo){
        float g=glow(t,combo);if(g<=.01f)return;
        var hb=bladeNow(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));float on=Math.min(1,g);
        NeonThrustSkill.neon(m,guard,tip,.055*(1+.5*Math.max(0,Math.min(1,g-1))),Math.min(1.6f,g),AURA);
        m.softLine(guard,tip,1.0,AURA[0],.26f*on);
        m.softDisc(tip,.18+.14*on,AURA[3],.8f*on);
    }
    /**
     * Sparks thrown off the edge of every slash as it sweeps: small violet, lilac and white motes, each born where the
     * edge passed, flying on along the sweep and outwards, slowing and fading in under a quarter second.
     */
    static void sparks(Fx c,EffectMesh m,float t,int combo){
        Slash s=slash(combo);if(s==null||t<s.from()||t>=s.to()+.26f)return;
        var sw=swept(c,combo,s.from(),s.to(),s.inner(),s.outer());int n=22;
        for(int k=0;k<n;k++){
            float born=s.from()+(s.to()-s.from())*(k+.5f*(float)Ease.hash(k*13+combo*7))/n,age=t-born,life=.14f+.1f*(float)Ease.hash(k*17+combo*5);
            if(age<0||age>=life)continue;
            double x=(born-s.from())/(s.to()-s.from())*(sw.n()-1);int i=Math.min(sw.n()-2,(int)x);double f=x-i;
            Vec3d edge=sw.outer()[i].add(sw.outer()[i+1].subtract(sw.outer()[i]).multiply(f)),root=sw.inner()[i].add(sw.inner()[i+1].subtract(sw.inner()[i]).multiply(f));
            Vec3d way=sw.outer()[i+1].subtract(sw.outer()[i]);if(way.lengthSquared()<1e-8)continue;way=way.normalize();
            Vec3d out=edge.subtract(root);out=out.lengthSquared()<1e-8?UP:out.normalize();
            double reach=.75+.35*Ease.hash(k*19+combo),speed=2.6+2.4*Ease.hash(k*23+combo),u=age/life;
            Vec3d at=root.add(edge.subtract(root).multiply(reach)),v=way.multiply(speed).add(out.multiply(speed*.45*(Ease.hash(k*29+combo)-.2)));
            Vec3d q=at.add(v.multiply(life*(1-Math.pow(1-u,2))*.5)).add(0,-.6*age*age,0);
            float a=(float)(1-u)*Ease.smooth(age,0,.02f);int col=k%4==0?WHITE:k%4==1?PINK:LILAC;
            m.needle(q.subtract(v.normalize().multiply(.14*(1-u)+.03)),q,.05,.3,col,a);
            m.softDisc(q,.045*(1-.5*u),col,.8f*a);
        }
    }
    /** Hit 1: a glint where the blade leaves the "sheath" at the hip. */
    static void draw(Fx c,EffectMesh m,float t){
        if(t<.19f||t>=.29f)return;
        float a=Ease.smooth(t,.19f,.215f)*(1-Ease.smooth(t,.24f,.29f));
        var hb=blade(c,1,Math.min(t,.24f));Vec3d p=hb[0].add(hb[1].multiply(.35));
        TimeWalkerSkill.glint(m,p,.85*a+.2,LILAC,a);
    }
    /** Every struck target: a white-violet burst and sparks thrown the way the blade went through (hit 4's where the thrust met it). */
    static void impacts(Fx c,EffectMesh m,float t,int combo,float at){
        if(t<at-.01f||t>=at+.36f)return;
        var hb0=blade(c,combo,at-.012f);var hb1=blade(c,combo,at+.012f);
        Vec3d way=hb1[0].add(hb1[1].multiply(TIP)).subtract(hb0[0].add(hb0[1].multiply(TIP)));
        way=way.lengthSquared()<1e-6?c.f:way.normalize();
        Vec3d side=way.crossProduct(UP);if(side.lengthSquared()<1e-4)side=c.right;side=side.normalize();Vec3d up=side.crossProduct(way).normalize();
        float u=Ease.unit(t,at,at+.18f),a=Ease.smooth(t,at-.01f,at+.01f);
        boolean big=combo==4;
        for(var e:struck(c)){
            // Hit 4's targets fly off with the blow: its burst stays where the thrust met them.
            Vec3d p=big&&t>=at?struckAt(c,e):chest(c,e);
            m.softDisc(p,(big?2.6:1.45)*(1-.4*u)+.25,VIOLET,.6f*a*(1-u));m.softDisc(p,(big?.9:.55)*(1-u)+.12,WHITE,a*(1-u));
            FxLib.ringPlane(m,p,side,up,.25+(big?2.4:1.3)*Ease.out(t,at,at+.16f),big?.28:.22,LILAC,.85f*a*(1-Ease.unit(t,at,at+.16f)));
            float s=a*(1-Ease.smooth(t,at+.08f,at+.3f));
            for(int k=0;k<(big?24:14);k++){
                double ang=(Ease.hash(k*3+combo*17+e.getId())-.5)*2.2,rise=(Ease.hash(k*5+combo*11)-.5)*1.6;
                Vec3d dir=way.multiply(Math.cos(ang)).add(side.multiply(Math.sin(ang))).add(up.multiply(rise)).normalize();
                double len=(.6+1.1*Ease.hash(k*7+combo))*(big?1.5:1)*Ease.out(t,at,at+.14f);
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
        TimeWalkerSkill.glint(m,p.add(0,.15,0),2.1*(1-.5*u)+.35,VIOLET,a*(1-u));
        FxLib.ring(m,p.add(0,.06,0),.4+6.4*Ease.out(t,HIT,HIT+.4f),.6,SEAL,.75f*a*(1-u));
        FxLib.ring(m,p.add(0,.05,0),.3+3.6*Ease.out(t,HIT,HIT+.3f),.35,MAGENTA,.65f*a*(1-Ease.unit(t,HIT,HIT+.3f)));
        FxLib.column(m,p,3.4*Ease.out(t,HIT,HIT+.06f),.36*(1-u)+.08,VIOLET,a*(1-Ease.smooth(t,HIT+.04f,HIT+.3f)));
        float s=a*(1-Ease.smooth(t,HIT+.1f,HIT+.45f));
        for(int k=0;k<26;k++){
            double az=Ease.hash(k*7+301)*Math.PI*2,el=.35+.9*Ease.hash(k*11+302),len=(.8+1.6*Ease.hash(k*13+303))*Ease.out(t,HIT,HIT+.16f);
            Vec3d dir=new Vec3d(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el));
            Vec3d q=p.add(0,.05,0).add(dir.multiply(len*.4));
            m.needle(q,q.add(dir.multiply(len*.6)),.06,.3,k%3==0?WHITE:PINK,s);
        }
    }
    /** Hit 4: light gathering into the blade's point as it is drawn back and loaded. */
    static void gather(Fx c,EffectMesh m,float t){
        if(t<.1f||t>=HIT+.02f)return;
        var hb=bladeNow(c);Vec3d tip=hb[0].add(hb[1].multiply(TIP));
        FxLib.converge(m,tip,1.7,16,t,.1f,HIT-.02f,LILAC,41);
        float k=Ease.smooth(t,.12f,HIT-.02f)*(1-Ease.smooth(t,HIT-.01f,HIT+.02f));
        m.softDisc(tip,.25+.35*k,SEAL,.7f*k);m.softDisc(tip,.1+.12*k,WHITE,.9f*k);
    }
    /**
     * Hit 4's thrust: a lance of light shot on from the blade's point through the target and far past it (ink edges,
     * violet body, white core), ringed at the point and where it meets the target.
     */
    static void lance(Fx c,EffectMesh m,float t,boolean additive){
        float a=lanceLit(t);if(a<=0)return;
        float grow=Ease.out(t,HIT-.035f,HIT+.02f);
        var hb=blade(c,4,HIT);Vec3d tip=hb[0].add(hb[1].multiply(TIP)),dir=hb[1];
        Vec3d a0=tip.subtract(dir.multiply(.8)),a1=tip.add(dir.multiply(LANCE*grow));
        if(!additive){m.needle(a0,a1,.58,.25,INK,.8f*a);return;}
        m.softLine(a0,a1,1.7,DEEP,.35f*a);
        m.needle(a0,a1,.44,.25,VIOLET,a);m.needle(a0,a1.subtract(dir.multiply(.5)),.16,.25,WHITE,a);
        FxLib.ringPlane(m,tip,c.right,UP,.25+.9*Ease.out(t,HIT-.02f,HIT+.1f),.16,WHITE,.8f*a);
        FxLib.ringPlane(m,impact(c),c.right,UP,.3+2.1*Ease.out(t,HIT,HIT+.12f),.26,LILAC,.9f*a);
    }
    /** Hit 4's shock: rings racing on along the thrust past the target one after another, widening as they go, and sparks flung on ahead. */
    static void shock(Fx c,EffectMesh m,float t){
        if(t<HIT-.01f||t>=HIT+.45f)return;
        Vec3d p=impact(c),dir=c.f;
        for(int k=0;k<3;k++){
            float at=HIT+.035f*k,u=Ease.unit(t,at,at+.28f);if(t<at||u>=1)continue;
            Vec3d o=p.add(dir.multiply(.5+1.6*k+1.4*Ease.out(t,at,at+.28f)));
            FxLib.ringPlane(m,o,c.right,UP,.35+(1.0+.5*k)*Ease.out(t,at,at+.22f),.2+.05*k,k==0?WHITE:LILAC,.85f*(1-u)*Ease.smooth(t,at,at+.015f));
        }
        float s=Ease.smooth(t,HIT-.01f,HIT+.01f)*(1-Ease.smooth(t,HIT+.12f,HIT+.4f));
        for(int k=0;k<28;k++){
            double az=(Ease.hash(k*3+611)-.5)*1.4,el=(Ease.hash(k*5+612)-.4)*1.1,go=(1.0+3.0*Ease.hash(k*7+613))*Ease.out(t,HIT,HIT+.3f);
            Vec3d d=dir.multiply(Math.cos(el)*Math.cos(az)).add(c.right.multiply(Math.cos(el)*Math.sin(az))).add(0,Math.sin(el),0);
            Vec3d q=p.add(d.multiply(.3+go)).add(0,-.8*Math.pow(Math.max(0,t-HIT),2),0);
            m.needle(q.subtract(d.multiply(.18+.2*Ease.hash(k*11+614))),q,.06,.3,k%3==0?WHITE:k%3==1?PINK:LILAC,s);
        }
    }
    /** Hit 4's struck targets fly off along the thrust trailing violet light from where it met them, sparks streaming off it. */
    static void trail(Fx c,EffectMesh m,float t){
        float a=flying(t);if(a<=0)return;
        for(var e:struck(c)){
            Vec3d q=chest(c,e),from=struckAt(c,e),back=from.subtract(q);double l=back.length();if(l<.3)continue;
            Vec3d way=back.multiply(-1/l),end=q.add(back.multiply(Math.min(1,3.6/l)));double span=Math.min(l,3.6);
            m.softTaper(q,end,1.1,.08,VIOLET,.7f*a);m.softTaper(q,end,.36,.02,WHITE,.85f*a);
            m.softDisc(q,.85,SEAL,.3f*a);
            for(int k=0;k<12;k++){
                double f=Ease.hash(k*7+621)*span,off=(Ease.hash(k*11+622)-.5)*.5,lift=(Ease.hash(k*13+623)-.5)*.5;
                Vec3d s=q.subtract(way.multiply(f)).add(c.right.multiply(off)).add(0,lift,0);
                float tw=.55f+.45f*(float)Math.sin(t*40+k*1.7);
                m.needle(s.subtract(way.multiply(.22)),s,.05,.3,k%3==0?WHITE:LILAC,a*tw*(float)(1-.7*f/span));
            }
        }
    }

    // ───────────────────────────── smoke and dust ─────────────────────────────

    /**
     * On the effect layer, dust kicked up by hit 2's step, hit 3's slam and hit 4's lunge, the blast under its
     * target and where each of its targets comes down.
     */
    @Override public void shaders(Fx c){
        if(!FxLayer.available())return;
        float t=c.sec;int combo=c.cast.combo();
        List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();
        if(combo==1)skid(c,t,HIT,.5,dust,puffs,3,7);
        if(combo==2)skid(c,t,HIT,.75,dust,puffs,5,11);
        if(combo==3)FxLib.dustRing(slamPoint(c),t,HIT,.8,4.0,22,1.1,dust,puffs,31);
        if(combo==4){
            skid(c,t,HIT,1.05,dust,puffs,7,23);
            if(t>=HIT)FxLib.dustRing(floor(c,impact(c)),t,HIT,.5,2.6,12,1.0,dust,puffs,47);
            int k=0;
            for(var e:struck(c)){var l=landing(c,e);if(l!=null)FxLib.dustRing(new Vec3d(l[1],l[2],l[3]),t,l[0],.4,2.4,10,.95,dust,puffs,53+k*11);k++;}
        }
        if(!dust.isEmpty())KagenoFire.dust(c.matrix,c.camera,dust,puffs,c.age);
    }
    /** Where and when each of hit 4's targets came down {time, x, y, z}, held from the first frame it is seen landed. */
    private static final Map<Long,float[]> LANDINGS=new LinkedHashMap<>();
    static float[] landing(Fx c,LivingEntity e){
        long key=c.cast.id().getLeastSignificantBits()*31+e.getId();
        var l=LANDINGS.get(key);if(l!=null||c.sec<HIT+.25f||c.sec>=HIT+1.2f||!e.isOnGround())return l;
        Vec3d p=e.getLerpedPos(c.delta);var world=c.mc.world;double y=p.y;
        // The top of the block under its feet (the position may still be easing down to it).
        if(world!=null)for(int i=0;i<4;i++){var pos=net.minecraft.util.math.BlockPos.ofFloored(p.x,p.y+.3-i,p.z);
            if(!world.getBlockState(pos).getCollisionShape(world,pos).isEmpty()){y=pos.getY()+world.getBlockState(pos).getCollisionShape(world,pos).getMax(net.minecraft.util.math.Direction.Axis.Y);break;}}
        l=new float[]{c.sec,(float)p.x,(float)y,(float)p.z};
        LANDINGS.put(key,l);while(LANDINGS.size()>64)LANDINGS.remove(LANDINGS.keySet().iterator().next());
        return l;
    }
    /** Puffs kicked up at the front foot by a step (it lands {@code fw} blocks ahead of the cast spot). */
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
     * hit 3's flash on the floor; hit 4's point gathering light, its lance lighting the way ahead, the blow's burst
     * with a light shaft and a slight colour split for viewers close by, and each struck target lit as it flies.
     * Decals: hit 3's gash torn in the floor along the slam, glowing and cooling; a scuff blown along the floor under
     * hit 4's blow.
     */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;int combo=c.cast.combo();
        float g=Math.min(1.4f,glow(t,combo));
        if(g>.02f){var hb=bladeNow(c);f.light(hb[0].add(hb[1].multiply(.85)),AURA[1],5.5,1.15f*g);}
        float hit=kick(t,HIT,.1f);
        if(hit>.01f)for(var e:struck(c))f.light(combo==4&&t>=HIT?struckAt(c,e):chest(c,e),0xe8c8ff,combo==4?9:7,(combo==4?3.0f:2.4f)*hit);
        float near=(float)Math.max(0,Math.min(1,(22-c.camera.distanceTo(anchor(c)))/10));
        if(combo==3&&t>=HIT-.01f){
            Vec3d p=slamPoint(c);
            f.light(p.add(0,.5,0),0xc77dff,11,3.6f*kick(t,HIT,.15f)+.6f*Ease.window(t,HIT,HIT+.6f,.04f,.4f));
            float heat=1-Ease.smooth(t,HIT+.1f,HIT+.8f),al=Ease.smooth(t,HIT,HIT+.04f)*(1-Ease.smooth(t,1.0f,1.18f));double open=Ease.out(t,HIT,HIT+.1f);
            // A gash along the slam and four cracks fanning out from it.
            f.decal(p,c.f,2.1*open+.2,.62,heat,al,5.3f);
            f.decal(p.add(c.right.multiply(.25)),c.f.multiply(.5).add(c.right.multiply(.87)),1.3*open+.15,.34,heat*.8f,.85f*al,9.1f);
            f.decal(p.subtract(c.right.multiply(.25)),c.f.multiply(.5).subtract(c.right.multiply(.87)),1.2*open+.15,.32,heat*.8f,.85f*al,13.7f);
            f.decal(p.subtract(c.f.multiply(.3)).add(c.right.multiply(.3)),c.f.multiply(-.6).add(c.right.multiply(.8)),.9*open+.12,.26,heat*.7f,.75f*al,21.4f);
            f.decal(p.subtract(c.f.multiply(.3)).subtract(c.right.multiply(.3)),c.f.multiply(-.6).subtract(c.right.multiply(.8)),.85*open+.12,.25,heat*.7f,.75f*al,27.2f);
            if(near>0)f.shaft(p.add(0,.3,0),0xe0b8ff,.28f*near*kick(t,HIT,.13f));
        }
        if(combo==4){
            Vec3d p=impact(c);
            if(t>=.12f&&t<HIT+.02f){var hb=bladeNow(c);f.light(hb[0].add(hb[1].multiply(TIP)),0xd8b0ff,4,1.4f*Ease.smooth(t,.12f,HIT-.02f)*(1-Ease.smooth(t,HIT-.01f,HIT+.02f)));}
            float lit=lanceLit(t);
            if(lit>0){var hb=blade(c,4,HIT);f.light(hb[0].add(hb[1].multiply(TIP+2.2)),0xc080ff,9,2.4f*lit);}
            if(t>=HIT-.01f){
                f.light(p,0xffd0f8,12,4.2f*kick(t,HIT,.16f));
                if(near>0){f.shaft(p,0xf0c8ff,.5f*near*kick(t,HIT,.2f));f.chroma(near*.008f*kick(t,HIT,.1f));}
                float heat=1-Ease.smooth(t,HIT+.05f,HIT+.5f),al=Ease.smooth(t,HIT,HIT+.04f)*(1-Ease.smooth(t,1.02f,1.18f));
                f.decal(floor(c,p).add(c.f.multiply(.6)),c.f,1.4*Ease.out(t,HIT,HIT+.12f)+.2,.42,heat,al,17.9f);
            }
            float fly=flying(t);
            if(fly>0)for(var e:struck(c))f.light(chest(c,e),0xb070ff,6,1.5f*fly);
        }
    }
    /** The air bends along each slash as it sweeps; hit 3 sends a shock ring over the floor; hit 4 bends it along the lance and in rings racing on past the target. */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;int combo=c.cast.combo();Slash s=slash(combo);
        var sw=slashNow(c);
        if(sw!=null)d.ribbon(sw.inner(),sw.outer(),sw.n(),.65f*Ease.smooth(t,s.from(),s.from()+.03f)*(1-Ease.smooth(t,s.to(),s.eat1())));
        if(combo==3&&t>=HIT-.01f&&t<HIT+.45f){
            float in=Ease.smooth(t,HIT-.01f,HIT+.015f);
            d.ring(slamPoint(c).add(0,.05,0),c.right,c.f,.5+9*Ease.out(t,HIT,HIT+.45f),1.7,.9f*in*(1-Ease.unit(t,HIT,HIT+.45f)));
        }
        if(combo==4){
            float lit=lanceLit(t);
            if(lit>0){var hb=blade(c,4,HIT);Vec3d tip=hb[0].add(hb[1].multiply(TIP));d.band(tip,tip.add(hb[1].multiply(LANCE*Ease.out(t,HIT-.035f,HIT+.02f))),UP,.9,.7f*lit);}
            if(t>=HIT-.01f&&t<HIT+.4f){
                Vec3d p=impact(c);float in=Ease.smooth(t,HIT-.01f,HIT+.015f);
                d.ring(p,c.right,UP,.4+3.6*Ease.out(t,HIT,HIT+.3f),1.1,.85f*in*(1-Ease.unit(t,HIT,HIT+.3f)));
                for(int k=1;k<3;k++){float at=HIT+.035f*k;if(t>=at&&t<at+.28f)d.ring(p.add(c.f.multiply(.5+1.6*k+1.4*Ease.out(t,at,at+.28f))),c.right,UP,.35+(1.0+.5*k)*Ease.out(t,at,at+.22f),.7,.6f*(1-Ease.unit(t,at,at+.28f)));}
            }
        }
    }
    /** Chunks torn from the floor: hit 3's slam throws them out round the impact (and the floor heaves in a ring); hit 4's blow kicks a few up under its target. */
    @Override public void solids(Fx c,FxSolids s){
        float t=c.sec;int combo=c.cast.combo();
        if(combo==3&&t>=HIT&&t<1.2f){Vec3d p=slamPoint(c);FxLib.chunks(s,p,t-HIT,20,801,4.2,7,.2,.6f);FxLib.heave(s,p,t-HIT,6,.3,13,.85,.7);}
        if(combo==4&&t>=HIT&&t<1.2f)FxLib.chunks(s,floor(c,impact(c)),t-HIT,8,901,1.6,6,.16,.5f);
    }

    // ───────────────────────────── camera ─────────────────────────────

    /** Shake as each hit lands, harder with the slam and the thrust; each comes in over two frames. */
    @Override public float shake(StandClient.Cast cast,float sec){
        int combo=cast.combo();
        return (combo==3?1.8f:combo==4?1.3f:combo==2?.6f:.5f)*bump(sec,HIT,combo==3?.13f:combo==4?.1f:.08f);
    }
    /**
     * The camera rides each blow: hit 1 rolls a little with the rising cut, hit 2 against it with the falling one, the
     * slam nods it down and the thrust pushes it on.
     */
    @Override public float[] kick(StandClient.Cast cast,float sec){
        int combo=cast.combo();
        if(sec<HIT-.02f||sec>=HIT+.6f)return null;
        float b=bump(sec,HIT,combo==3?.15f:.1f);
        return switch(combo){
            case 1->new float[]{.5f*b,-.3f*b,-1.0f*b};
            case 2->new float[]{-.6f*b,.5f*b,1.2f*b};
            case 3->new float[]{0,1.5f*b,0};
            default->new float[]{0,.8f*b,0};
        };
    }
    static float bump(float sec,float at,float tau){return sec<at-.02f?0:Ease.smooth(sec,at-.02f,at+.015f)*(float)Math.exp(-Math.max(0,sec-at-.015f)/tau);}
    @Override public int afterimageColor(){return 0x9a6cff;}
}
