package dev.sangwoo.celestialfall.client.kageno;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.vfx.FxDistort;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxSolids;
import dev.sangwoo.celestialfall.stand.KagenoRules;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.client.kageno.Anim.*;
import static dev.sangwoo.celestialfall.client.kageno.R6.pose;

/**
 * R "시간을 걷는 자", after the user's recordings (standing from four sides, walking from behind). The sword's purple
 * aura flares as the body dips and raises the sword into a hanging guard over the head; at 0.5 s the world goes gray
 * and the blade turns cyan. Four fast cuts and one big wound-up sweep each leave one long white-lavender line frozen in
 * the air ahead: the four slash across the front at angles drawn anew every cast, the big one lies almost level. The
 * body settles low with the sword down. Colour returns at 1.77 s: the lines
 * go violet and vanish into a column of light as the four stored cuts burst out as purple slashes (the server's four
 * stored hits at 1.8 s), then at 1.9 s the big cut lands as one long purple slash (5 hits in all) with a white
 * starburst and a magenta band exploding sideways along it with streaks, sparks, pink stars, shards and smoke. Walking, the same arms and sword play over the walk cycle
 * ({@link #walking}). Seconds after the cast.
 */
final class TimeWalkerSkill implements Fx.Skill {
    static final double H=1.8;
    /** The world turns gray, colour returns, the stored cuts land, the release (the server's ticks). */
    static final float GRAY=KagenoRules.R_GRAY/20f,COLOR=1.769f,STORED=KagenoRules.R_STORED/20f,RELEASE=KagenoRules.R_RELEASE/20f;
    /** The end of each strike (four cuts, then the big sweep): the cut it leaves appears in the air ahead. */
    static final float[] CUTS={1.00f,1.14f,1.28f,1.42f,1.60f};
    /** The sword's aura (haze, glow, body, core): purple, and cyan while the world is stopped. */
    static final int[] AURA={0x5a1ab8,0x9447ff,0xd7a6ff,0xfff3ff},ICE={0x157f9e,0x3fd6f2,0xb6f5ff,0xffffff};
    /** The cuts' white and lavender, the violet they turn, the release's magenta, pink and rose. */
    static final int WHITE=0xffffff,LAVENDER=0xdcc8ff,LILAC=0xb995ff,VIOLET=0x9a4dff,MAGENTA=0xff2bd6,PINK=0xff6fe2,ROSE=0xffbdf0;
    /** The guard and the tip, in blocks along the blade from the hand. */
    static final double GUARD=.24,TIP=1.35;

    static final R6.Pose GUARD_HIGH=pose().body(-6,-5,5).head(0,14,0).rArm(72,-24).lArm(52,-12).blade(-44,48).rLeg(-86,10).lLeg(-84,-24);
    static final R6.Pose CROUCH=pose().at(0,-.38,0).body(-8,30,0).head(0,-18,0).rArm(-54,44).lArm(-46,-26).blade(-56,36).rLeg(-60,38).lLeg(-62,-152);
    /**
     * Each cut is wound up the other way at its cock and sweeps across the front to its cut in 0.07 s; the big sweep is
     * coiled deeper and longer and takes 0.1 s.
     */
    static final float[] COCKS={.93f,1.07f,1.21f,1.35f,1.50f};
    /**
     * Wound up: left, low right, low left, right, then coiled far to the left and low for the big sweep. Through every
     * strike the blade rides above the sword arm (the wrist cocked up), so the arm swings without rolling over itself.
     */
    static final R6.Pose[] COCKED={
        pose().at(0,-.08,0).body(28,6,-4).head(-14,6,0).rArm(-2,-70).lArm(-48,24).blade(35,-85).rLeg(-80,22).lLeg(-82,-30),
        pose().at(0,-.14,.05).body(-32,10,4).head(18,6,0).rArm(-30,85).lArm(0,-80).blade(-5,100).rLeg(-78,30).lLeg(-80,-36),
        pose().at(0,-.12,-.06).body(32,10,-5).head(-16,8,0).rArm(-35,-50).lArm(-52,28).blade(-8,-60).rLeg(-80,22).lLeg(-82,-30),
        pose().at(0,-.12,.06).body(-34,10,4).head(18,4,0).rArm(-8,95).lArm(-6,-76).blade(25,105).rLeg(-74,30).lLeg(-78,-40),
        pose().at(-.06,-.26,-.08).body(48,16,-8).head(-22,6,0).rArm(-6,-88).lArm(-46,36).blade(26,-112).rLeg(-66,30).lLeg(-70,-150)};
    /** Struck through: right, high left, high right, left, and the big sweep out to the right with a step. */
    static final R6.Pose[] STRUCK={
        pose().at(0,-.12,.05).body(-30,8,4).head(16,4,0).rArm(-10,80).lArm(4,-88).blade(8,92).rLeg(-78,30).lLeg(-80,-34),
        pose().at(0,-.04,-.04).body(24,-6,-6).head(-12,-8,0).rArm(40,-50).lArm(-30,-30).blade(62,-60).rLeg(-82,18).lLeg(-82,-26),
        pose().at(0,-.02,.05).body(-22,-6,5).head(12,-8,0).rArm(42,55).lArm(-20,-60).blade(64,66).rLeg(-84,16).lLeg(-82,-28),
        pose().at(0,-.08,-.06).body(30,6,-5).head(-16,4,0).rArm(-6,-65).lArm(-50,26).blade(14,-80).rLeg(-80,22).lLeg(-82,-30),
        pose().at(.14,-.2,.1).body(-50,12,6).head(22,4,0).rArm(-6,96).lArm(10,-96).blade(14,114).rLeg(-62,30).lLeg(-66,-150)};
    /** Halfway through each strike the sword passes in front of the body (arm and blade: elevation, azimuth). */
    static final double[][] ACROSS={{-2,5,30,5},{10,0,40,-5},{8,0,38,4},{-4,-5,28,-5},{-4,4,26,8}};
    static final Anim CLIP;
    static {
        List<Key> keys=new ArrayList<>(List.of(
            k(0,KagenoClips.IDLE),
            // A step in: the free arm swings out, the sword drops back, then the body dips low with both hands on the hilt.
            k(.06,pose().at(0,-.04,0).body(-14,8,0).head(8,0,0).rArm(-62,48).lArm(-8,-48).blade(-28,118).rLeg(-82,14).lLeg(-84,-22)),
            k(.17,pose().at(0,-.36,0).body(-24,26,-10).head(14,-18,0).rArm(-14,-12).lArm(-10,8).blade(-34,42).rLeg(-62,36).lLeg(-66,-150),Anim.Ease.OUT),
            // Rising, the hands carry the sword up past the face into the hanging guard: hilt over the head, blade down to the right.
            k(.30,pose().at(0,-.14,0).body(-12,6,-4).head(6,-6,0).rArm(48,-16).lArm(30,-6).blade(-12,58).rLeg(-76,20).lLeg(-80,-60)),
            k(.42,GUARD_HIGH),
            k(GRAY,GUARD_HIGH.at(0,-.03,0).body(-6,-3,5).blade(-46,50)),
            k(.80,GUARD_HIGH.at(0,-.05,0).head(0,18,0).rArm(70,-22).blade(-48,52))));
        // Four cuts and the big sweep: wound up (a beat of stillness), swept through the front at full speed, on into the next wind-up.
        for(int i=0;i<CUTS.length;i++){
            float cock=COCKS[i],cut=CUTS[i];double[] a=ACROSS[i];
            keys.add(k(cock,COCKED[i]));
            keys.add(k((cock+cut)/2,R6.Pose.lerp(COCKED[i],STRUCK[i],.5f).rArm(a[0],a[1]).blade(a[2],a[3]),Anim.Ease.LINEAR));
            keys.add(k(cut,STRUCK[i],Anim.Ease.LINEAR));
        }
        keys.addAll(List.of(
            // The sword comes down into a low stance; it holds as the cuts release in front.
            // The big sweep's follow-through settles a beat before the sword comes down into a low stance; it holds there as the cuts release in front.
            k(1.66,STRUCK[STRUCK.length-1].at(.14,-.22,.1).body(-54,14,6).rArm(-12,100).blade(4,122)),
            k(1.72,pose().at(.06,-.3,.04).body(-16,22,2).head(6,-10,0).rArm(-44,62).lArm(-36,-40).blade(-46,70).rLeg(-62,36).lLeg(-64,-150)),
            k(1.78,CROUCH),
            k(RELEASE,CROUCH.at(0,-.4,0).body(-8,33,0)),
            // Up again with a flick of the blade, then the rest.
            k(2.08,pose().at(0,-.22,0).body(-6,16,0).head(0,-6,0).rArm(-60,40).lArm(-60,-20).blade(-50,48).rLeg(-72,26).lLeg(-74,-120)),
            k(2.30,pose().at(0,-.04,0).body(-10,4,0).rArm(-36,84).lArm(-80,-12).blade(-46,112).rLeg(-86,8).lLeg(-86,-14),Anim.Ease.OUT),
            k(2.62,KagenoClips.IDLE)));
        CLIP=Anim.of(keys.toArray(new Key[0])).stops(Anim.stop(RELEASE,.06,.12));
    }
    @Override public Anim clip(int combo){return CLIP;}
    @Override public float lifetime(){return 4.0f;}

    /**
     * The walking cast: at {@code w} 1 the legs walk (the model's walk cycle) and the body stays over them: no dip or
     * crouch, a lighter lean and a smaller twist, while the arms and the sword play the same cuts.
     */
    static R6.Frame walking(R6.Frame f,float w){
        if(w<=0)return f;
        return new R6.Frame(f.joints(),f.grip(),f.fw()*(1-w),f.up()*(1-.85f*w),f.rt()*(1-w),f.yaw()*(1-.45f*w),f.pitch()*(1-.6f*w),f.roll()*(1-.7f*w));
    }

    // ───────────────────────────── timing ─────────────────────────────

    /** How cyan the sword is: purple until the world stops, cyan through the gray, purple again as colour returns. */
    static float cyan(float t){return Ease.smooth(t,GRAY-.03f,GRAY+.03f)*(1-Ease.smooth(t,COLOR-.02f,COLOR+.06f));}
    /** Brightness of the sword's aura: it catches, flares as the sword goes up, flashes as the world stops and on every cut, dies after the rest. */
    static float glow(float t){
        if(t<=0)return 0;
        float g=Ease.smooth(t,0,.12f)+.7f*Ease.window(t,.28f,.5f,.08f,.08f);
        g+=.9f*(float)Math.exp(-Math.abs(t-GRAY)/.035);
        for(float k:COCKS)if(t>=k)g+=.45f*(float)Math.exp(-(t-k)/.07);
        return g*(1-Ease.smooth(t,2.35f,2.8f));
    }
    /** Fraction of the blade the aura has run up, from the guard. */
    static float lit(float t){return Ease.smooth(t,0,.12f);}
    /** Strength of the frozen cuts: drawn in at their strikes, gone soon after colour returns. */
    static float frozen(float t){return 1-Ease.smooth(t,COLOR,STORED+.035f);}

    // ───────────────────────────── geometry ─────────────────────────────

    /** Sword hand and blade direction of the body as drawn (the walking cast included). */
    static Vec3d[] sword(Fx c){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(KagenoClips.action(c.cast,c.delta)));}
    static Vec3d[] swordAt(Fx c,float sec,float w){return KagenoClips.blade(c.pos,c.cast.yaw(),R6.poseOf(walking(CLIP.frame(sec),w)));}
    /** Where the cuts cross: the first time-stopped target ahead, otherwise 3.2 blocks ahead at chest height. */
    static Vec3d crossing(Fx c){
        for(var t:c.cast.targets()){
            Vec3d d=t.position().subtract(c.origin);double fw=d.dotProduct(c.f);
            if(fw>.8&&fw<7.5&&Math.abs(d.dotProduct(c.right))<fw*1.2)return t.position().add(0,height(c,t.entity())*.62,0);
        }
        return c.at0(3.2,1.25,0);
    }
    static double height(Fx c,int id){for(var e:c.targets)if(e.getId()==id)return e.getHeight();return 1.8;}
    /** One frozen cut: centre, direction (toward the caster's right side), length, core width and the end it was drawn from (-1 left, 1 right). */
    record Cut(Vec3d centre,Vec3d dir,double length,double width,int from){}
    /** Tilts of the four cuts lie within ±TILT degrees, at least GAP degrees apart. */
    static final double TILT=55,GAP=20;
    /**
     * The cuts of one cast, drawn from its id (the same on every client): the four cuts slash across the front at
     * irregular angles, a new set every cast (tilts at least {@value #GAP}° apart in a random order, so both
     * diagonals are always there), each turned a little toward or away from the caster and offset round the crossing;
     * the big sweep's cut lies almost level, longest and widest. Each row: turn off the sideways axis toward the front,
     * elevation (degrees), length, offsets up, right, forward (blocks), core width.
     */
    static double[][] spec(long seed){
        var r=new java.util.SplittableRandom(seed);int n=CUTS.length-1;
        // Irregular gaps: the slack beyond the minimum spacing is shared out at random.
        double[] w=new double[n+1];double sum=0;for(int i=0;i<=n;i++){w[i]=-Math.log(1-r.nextDouble());sum+=w[i];}
        double free=2*TILT-(n-1)*GAP,at=-TILT;double[] tilt=new double[n];
        for(int i=0;i<n;i++){at+=(i>0?GAP:0)+free*w[i]/sum;tilt[i]=at;}
        for(int i=n-1;i>0;i--){int j=r.nextInt(i+1);double k=tilt[i];tilt[i]=tilt[j];tilt[j]=k;}
        double[][] out=new double[n+1][];
        for(int i=0;i<n;i++)out[i]=new double[]{-22+44*r.nextDouble(),tilt[i],9.5+3.5*r.nextDouble(),-.35+.8*r.nextDouble(),-.45+.9*r.nextDouble(),-.2+.6*r.nextDouble(),.062+.01*r.nextDouble()};
        out[n]=new double[]{-5+10*r.nextDouble(),-6+12*r.nextDouble(),16,0,0,0,.11};
        return out;
    }
    static long seed(Fx c){var id=c.cast.id();return id.getMostSignificantBits()*31+id.getLeastSignificantBits();}
    static List<Cut> cuts(Fx c){
        List<Cut> out=new ArrayList<>();Vec3d x=crossing(c);double[][] spec=spec(seed(c));
        for(int i=0;i<spec.length;i++){
            double[] s=spec[i];double yaw=Math.toRadians(s[0]),el=Math.toRadians(s[1]);
            Vec3d dir=c.right.multiply(Math.cos(el)*Math.cos(yaw)).add(c.f.multiply(Math.cos(el)*Math.sin(yaw))).add(0,Math.sin(el),0);
            out.add(new Cut(x.add(0,s[3],0).add(c.right.multiply(s[4])).add(c.f.multiply(s[5])),dir,s[2],s[6],i%2==0?-1:1));
        }
        return out;
    }
    /** How far cut i has been drawn (0..1): it races across with its strike. */
    static float drawn(float t,int i){return Ease.smooth(t,COCKS[i],CUTS[i]);}
    /** Ends of cut i as drawn so far. */
    static Vec3d[] span(Cut cut,float k){
        Vec3d start=cut.centre().add(cut.dir().multiply(cut.from()*cut.length()*.5));
        return new Vec3d[]{start,start.add(cut.dir().multiply(-cut.from()*cut.length()*k))};
    }
    static Vec3d across(Vec3d d){Vec3d x=d.crossProduct(Fx.UP);if(x.lengthSquared()<1e-4)x=d.crossProduct(new Vec3d(1,0,0));return x.normalize();}
    static int mix(int a,int b,float k){return NeonThrustSkill.mix(a,b,Math.max(0,Math.min(1,k)));}
    static int[] palette(float k){int[] p=new int[4];for(int i=0;i<4;i++)p[i]=mix(AURA[i],ICE[i],k);return p;}

    // ───────────────────────────── effects ─────────────────────────────

    @Override public void additive(Fx c,EffectMesh m){
        float t=c.sec;
        blade(c,m,t);
        freeze(c,m,t);
        if(t>=COCKS[0]&&t<STORED+.05f){strikes(c,m,t);frozenCuts(c,m,t);}
        rift(c,m,t,true);
        burst(c,m,t,true);
        slashLight(c,m,t);
        band(c,m,t,true);
        hits(c,m,t);
    }
    /** The colour that has to stay saturated over a bright sky: the violet rays, the band's magenta body and the shards. */
    @Override public void alpha(Fx c,EffectMesh m){
        float t=c.sec;
        rift(c,m,t,false);
        burst(c,m,t,false);
        band(c,m,t,false);
    }

    /** The sword's aura: a neon tube up the blade (purple, cyan while time stands still), a bloom along it, glints as it turns. */
    static void blade(Fx c,EffectMesh m,float t){
        float g=glow(t),f=lit(t);if(g<=0||f<=0)return;
        var hb=sword(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP)),end=guard.add(tip.subtract(guard).multiply(f));
        float k=cyan(t),on=Math.min(1,g);int[] p=palette(k);
        NeonThrustSkill.neon(m,guard,end,.065*(1+.4*Math.max(0,Math.min(1,g-1))),Math.min(1.7f,g),p);
        // A wide glow wrapped round the whole blade, like the aura in the recordings.
        m.softLine(guard,end,1.1,p[0],.3f*on);m.softLine(guard,end,.55,p[1],.35f*on);
        for(int i=0;i<=5;i++)m.softDisc(guard.add(end.subtract(guard).multiply(i/5.0)),.42,p[1],.18f*on);
        if(f>=1)m.softDisc(tip,.12,p[3],.85f*on);
        // The flare at the hand as the sword goes up; motes gather into it.
        float raise=Ease.window(t,.26f,.5f,.06f,.06f);
        if(raise>0){m.softDisc(hb[0],.55+.25*raise,p[1],.5f*raise);m.softDisc(hb[0],.22,WHITE,.8f*raise);m.star(hb[0],.6*raise,p[2],.8f*raise);}
        FxLib.converge(m,hb[0],1.6,10,t,.18f,.46f,AURA[2],301);
        // The flip to cyan as the world stops: a white flash runs the blade.
        float flip=(float)Math.exp(-Math.abs(t-GRAY)/.04);
        if(flip>.02f){for(int i=0;i<=6;i++)m.softDisc(guard.add(tip.subtract(guard).multiply(i/6.0)),.18+.2*flip,ICE[2],.55f*flip);m.star(tip,1.1*flip,WHITE,flip);}
        // Purple sparkles drifting up off the blade before and after the stop.
        float drift=g*(1-k)*(1-Ease.smooth(t,2.3f,2.7f));
        if(drift>0)for(int i=0;i<9;i++){
            float ph=(float)((t*1.6+Ease.hash(i*7+3))%1);
            Vec3d q=guard.add(tip.subtract(guard).multiply(Ease.hash(i*5+1))).add((Ease.hash(i*11)-.5)*.3,.5*ph,(Ease.hash(i*13)-.5)*.3);
            m.star(q,.07+.05*Ease.hash(i*17),i%3==0?WHITE:AURA[2],.8f*Math.min(1,drift)*(float)Math.sin(Math.PI*ph));
        }
    }
    /** The world stops: a cyan-white pulse at the chest, a ring racing out to the edge of the time stop, glints hanging in the air until colour returns. */
    static void freeze(Fx c,EffectMesh m,float t){
        Vec3d chest=c.pos.add(0,1.1,0);
        if(t>=GRAY-.01f&&t<GRAY+.55f){
            float u=Ease.unit(t,GRAY,GRAY+.55f),in=Ease.smooth(t,GRAY-.01f,GRAY+.02f);
            float pulse=in*(1-Ease.smooth(t,GRAY+.04f,GRAY+.24f));
            m.softDisc(chest,1.4*(1-u)+.3,ICE[1],.45f*pulse);m.softDisc(chest,.6*(1-u)+.15,WHITE,.85f*pulse);
            FxLib.ring(m,new Vec3d(c.origin.x,c.origin.y+.05,c.origin.z),.4+KagenoRules.STOP_RADIUS*Ease.out(t,GRAY,GRAY+.45f),.6,0xe6fbff,.85f*in*(1-u));
            FxLib.ringPlane(m,chest,c.f,Fx.UP,.3+3.4*H*Ease.out(t,GRAY,GRAY+.4f),.32,0xeef8ff,.7f*in*(1-Ease.unit(t,GRAY,GRAY+.4f)));
        }
        if(t>=GRAY+.08f&&t<COLOR+.15f){
            float a=Ease.smooth(t,GRAY+.08f,GRAY+.35f)*(1-Ease.smooth(t,COLOR,COLOR+.15f));
            for(int k=0;k<30;k++){
                double ang=Ease.hash(k*7+41)*Math.PI*2,r=(1+5.5*Ease.hash(k*5+42)),y=.3+3.6*Ease.hash(k*3+43),drift=(t-GRAY)*.06;
                Vec3d p=c.origin.add(Math.cos(ang)*r,y+drift,Math.sin(ang)*r);
                float tw=.6f+.4f*(float)Math.sin(t*(4+3*Ease.hash(k*11))+k);
                m.star(p,.05+.06*Ease.hash(k*13),k%3==0?WHITE:0xd8f4ff,a*tw);
            }
        }
    }
    /** Each strike smears the real blade, and its cut flashes where it starts with a spray of glass-like sparks. */
    static void strikes(Fx c,EffectMesh m,float t){
        float w=KagenoClips.walkWeight(c.cast,c.delta);var list=cuts(c);
        for(int i=0;i<CUTS.length;i++){
            float s=t-COCKS[i],span=CUTS[i]-COCKS[i];if(s<0||s>=span+.05f)continue;
            // The smear: the blade's sweep over the last 0.05 s, brightest at its head.
            float a=Ease.smooth(s,0,.015f)*(1-Ease.smooth(s,span,span+.05f));int n=6;
            Vec3d[] in=new Vec3d[n+1],out=new Vec3d[n+1];
            for(int j=0;j<=n;j++){var hb=swordAt(c,Math.max(COCKS[i],t-.05f*j/n),w);in[j]=hb[0].add(hb[1].multiply(GUARD));out[j]=hb[0].add(hb[1].multiply(TIP+.15));}
            for(int j=0;j<n;j++){float a0=a*(1-(float)j/n),a1=a*(1-(float)(j+1)/n);int col=mix(ICE[2],LAVENDER,(float)j/n);
                m.vertex(in[j],col,a0*.06f);m.vertex(out[j],col,a0*.5f);m.vertex(out[j+1],col,a1*.5f);m.vertex(in[j+1],col,a1*.06f);}
            // A thin bright edge along the path of the tip.
            for(int j=0;j<n;j++)m.line(out[j],out[j+1],.05,WHITE,a*(1-(float)j/n)*.8f);
            // The cut's birth: a flash at its start and sparks thrown along it (bigger for the last, big sweep).
            float b=t-COCKS[i];if(b<0||b>=.2f)continue;boolean big=i==CUTS.length-1;
            var cut=list.get(i);Vec3d[] e=span(cut,drawn(t,i));float u=b/.2f;
            glint(m,e[0],(big?1.3:.7)*(1-u)+.15,LAVENDER,(1-u)*Ease.smooth(b,0,.02f));
            if(big)glint(m,e[1],1.1*(1-u)+.2,WHITE,(1-u)*Ease.smooth(b,.04f,.07f));
            for(int k=0;k<(big?6:3);k++){
                Vec3d d=cut.dir().multiply(-cut.from()).multiply(.6+.6*Ease.hash(i*31+k*3)).add((Ease.hash(i*17+k*5)-.5)*.9,(Ease.hash(i*19+k*7)-.4)*.9,(Ease.hash(i*23+k*11)-.5)*.9).normalize();
                Vec3d p=e[0].add(d.multiply(.3+2.4*u*(.5+.5*Ease.hash(i*29+k))));
                m.needle(p,p.add(d.multiply(.28)),.05,.5,WHITE,(1-u)*(1-u));
            }
        }
    }
    /**
     * The cuts hang frozen in the air, one line each (four cuts and the big one, nothing else): a white core tapering
     * at both ends inside lavender glow and a glint running along it. They turn violet and thin out as colour returns.
     */
    static void frozenCuts(Fx c,EffectMesh m,float t){
        float fade=frozen(t);if(fade<=0)return;float tint=Ease.smooth(t,COLOR-.02f,COLOR+.05f),thin=1-.6f*tint;
        int glow=mix(0xc9a2ff,VIOLET,tint),haze=mix(0x9d6cff,0x8c3cff,tint);
        var list=cuts(c);
        for(int i=0;i<list.size();i++){
            float k=drawn(t,i);if(k<=0)continue;var cut=list.get(i);Vec3d[] e=span(cut,k);Vec3d a=e[0],b=e[1];
            float on=fade*Ease.smooth(t,COCKS[i],COCKS[i]+.015f);double wd=cut.width()*1.5*thin;
            m.softLine(a,b,wd*12,haze,.26f*on);
            m.softLine(a,b,wd*5,glow,.6f*on);
            m.needle(a,b,wd*2.4,.5,glow,.85f*on);
            m.needle(a,b,wd,.5,WHITE,on);
            // A glint travelling along the cut while it hangs.
            float ph=(float)((t*.9+Ease.hash(i*13+5))%1);
            if(k>=1)m.star(a.lerp(b,ph),.22,glow,.7f*on*(float)Math.sin(Math.PI*ph));
        }
    }
    /**
     * Colour back: the cuts vanish into a tall column of light standing on the crossing, a translucent pink body with
     * bright edges and sparks crawling up them, that swells for two frames and collapses into the burst.
     */
    static void rift(Fx c,EffectMesh m,float t,boolean additive){
        if(t<STORED-.01f||t>=STORED+.1f)return;
        Vec3d x=crossing(c);float open=Ease.out(t,STORED-.01f,STORED+.025f),u=Ease.unit(t,STORED,STORED+.1f),a=open*(1-u*u);
        double r=(.5+1.7*open)*(1-.75*u*u);
        Vec3d lo=new Vec3d(x.x,Math.min(x.y-1,c.origin.y),x.z),hi=lo.add(0,8*(.7+.3*open),0),side=across(m.camera().subtract(x).multiply(1,0,1));
        if(!additive){m.softLine(lo,hi,r*2.6,0x8a40e0,.28f*a);return;}
        m.softLine(lo,hi,r*2.2,PINK,.4f*a);
        m.softLine(lo,hi,r*1.1,ROSE,.45f*a);
        m.needle(lo,hi,.55*(1-.6*u),.4,WHITE,a);
        for(int s=-1;s<=1;s+=2){
            Vec3d e=side.multiply(s*r*.95);
            m.needle(lo.add(e),hi.add(e),.4,.5,ROSE,.75f*a);m.needle(lo.add(e),hi.add(e),.12,.5,WHITE,a);
            FxLib.zig(m,lo.add(side.multiply(s*r*1.05)),hi.add(side.multiply(s*r*.8)),9,.05,ROSE,.85f*a,(int)(t*40)+s*17);
        }
    }
    /** A violet glow swelling round the crossing as the stored cuts land (the purple slashes are {@link #crescents}), then the white starburst. */
    static void burst(Fx c,EffectMesh m,float t,boolean additive){
        Vec3d x=crossing(c);
        if(t>=STORED&&t<RELEASE+.18f){
            float u=Ease.unit(t,STORED,RELEASE+.18f),r=(float)(1.2+3.3*Ease.out(t,STORED,RELEASE+.1f)),a=Ease.smooth(t,STORED,STORED+.03f)*(1-u*u);
            if(additive){m.softDisc(x,r*.85,VIOLET,.4f*a);m.softDisc(x,r*.4,LILAC,.35f*a);}else m.softDisc(x,r,0x8a3cf0,.22f*a);
        }
        if(additive&&t>=RELEASE-.01f&&t<RELEASE+.16f){
            float u=Ease.unit(t,RELEASE,RELEASE+.16f),a=Ease.smooth(t,RELEASE-.01f,RELEASE+.015f)*(1-u);double s=1-.35*u;
            m.softDisc(x,2.6*s,PINK,.5f*a);m.softDisc(x,1.6*s,WHITE,.9f*a);
            FxLib.flare(m,x,2.2*s,ROSE,a,45);
        }
    }
    /**
     * The release: a magenta band explodes sideways through the crossing. A white-hot core line races out to ±9 blocks
     * inside a magenta glow; streaks fly out along it, sparks and pink star glints burst off it and pink shards tumble
     * out and fall while the smoke ({@link #shaders}) hangs.
     */
    static void band(Fx c,EffectMesh m,float t,boolean additive){
        if(t<RELEASE-.01f||t>=RELEASE+1.9f)return;
        Vec3d x=crossing(c),r=c.right;float in=Ease.smooth(t,RELEASE-.01f,RELEASE+.02f);
        double reach=9*Ease.out(t,RELEASE,RELEASE+.09f);float body=in*(1-Ease.smooth(t,RELEASE+.18f,RELEASE+.6f));
        Vec3d a=x.subtract(r.multiply(reach)),b=x.add(r.multiply(reach));
        if(body>0){
            if(additive){
                m.softLine(a,b,2.2,MAGENTA,.4f*body);m.softLine(a,b,1.2,PINK,.55f*body);m.softLine(a,b,.5,ROSE,.85f*body);m.needle(a,b,.18,.5,WHITE,body);
                // A second, thinner core line just above and one below: the band reads as layered energy.
                for(int s=-1;s<=1;s+=2)m.needle(a.add(0,s*.32,0),b.add(0,s*.32,0),.07,.5,ROSE,.7f*body);
                FxLib.glow(m,new Vec3d(x.x,c.origin.y+.06,x.z),7,MAGENTA,.35f*body);
            }else{m.softLine(a,b,2.6,MAGENTA,.55f*body);m.softLine(a,b,4.2,0x8a1fd0,.22f*body);}
        }
        // The band's glow lingers as a magenta haze after the core is gone.
        float haze=Ease.smooth(t,RELEASE+.05f,RELEASE+.2f)*(1-Ease.smooth(t,RELEASE+.5f,RELEASE+1.3f));
        if(haze>0){Vec3d ha=x.subtract(r.multiply(8)),hb=x.add(r.multiply(8));if(additive)m.softLine(ha,hb,2.4,MAGENTA,.22f*haze);else m.softLine(ha,hb,3,0x9a20c8,.25f*haze);}
        // Streaks racing out along the band.
        for(int k=0;k<30;k++){
            float born=RELEASE+.12f*(float)Ease.hash(k*3+1),u=(t-born)/(.28f+.25f*(float)Ease.hash(k*5+2));if(u<0||u>=1)continue;
            double side=Ease.hash(k*7+3)<.5?-1:1,go=(.3+2*Ease.hash(k*11+4))+(5+9*Ease.hash(k*13+5))*(1-(1-u)*(1-u));
            Vec3d p=x.add(r.multiply(side*go)).add(0,(Ease.hash(k*17+6)-.5)*1.2,0).add(c.f.multiply((Ease.hash(k*19+7)-.5)*1.0));
            double len=(.5+1.5*Ease.hash(k*23+8))*(1-.5*u);
            if(additive)m.needle(p,p.add(r.multiply(side*len)),.09,.3,k%3==0?WHITE:k%3==1?PINK:ROSE,(1-u));
            else if(k%2==0)m.needle(p,p.add(r.multiply(side*len)),.16,.3,MAGENTA,.5f*(1-u));
        }
        if(!additive){shards(c,m,t,x,false);return;}
        // Sparks thrown off the band, falling.
        for(int k=0;k<32;k++){
            float born=RELEASE+.02f+.1f*(float)Ease.hash(k*3+9),life=.35f+.3f*(float)Ease.hash(k*5+10),s=t-born;if(s<0||s>=life)continue;float u=s/life;
            Vec3d p0=x.add(r.multiply((Ease.hash(k*7+11)-.5)*14)).add(0,(Ease.hash(k*11+12)-.5)*.8,0);
            Vec3d v=new Vec3d((Ease.hash(k*13+13)-.5)*6,2+5*Ease.hash(k*17+14),(Ease.hash(k*19+15)-.5)*6);
            Vec3d p=p0.add(v.multiply(s)).add(0,-4.9*s*s,0),dv=v.add(0,-9.8*s,0).normalize();
            m.needle(p,p.subtract(dv.multiply(.35*(1-u)+.1)),.05,.3,k%2==0?WHITE:PINK,1-u);
        }
        // White sparkles twinkling all along it.
        for(int k=0;k<40;k++){
            float born=RELEASE+.03f+.7f*(float)Ease.hash(k*3+61),u=(t-born)/(.2f+.2f*(float)Ease.hash(k*5+62));if(u<0||u>=1)continue;
            Vec3d p=x.add(r.multiply((Ease.hash(k*7+63)-.5)*16)).add(0,(Ease.hash(k*11+64)-.4)*2.2,0).add(c.f.multiply((Ease.hash(k*13+65)-.5)*1.6));
            m.star(p,.1+.12*Ease.hash(k*17+66),k%3==0?ROSE:WHITE,(float)Math.sin(Math.PI*u));
        }
        // Pink four-point stars blooming along the band.
        for(int k=0;k<24;k++){
            float born=RELEASE+.02f+.55f*(float)Ease.hash(k*3+21),u=(t-born)/.32f;if(u<0||u>=1)continue;
            Vec3d p=x.add(r.multiply((Ease.hash(k*5+22)-.5)*15)).add(0,(Ease.hash(k*7+23)-.35)*2,0).add(c.f.multiply((Ease.hash(k*11+24)-.5)*1.4));
            float s=(float)Math.sin(Math.PI*u);FxLib.flare(m,p,(.35+.5*Ease.hash(k*13+25))*s,PINK,s,Ease.hash(k*17)*90);
        }
        shards(c,m,t,x,true);
    }
    /** Pink glass shards: out and up off the band, tumbling, then falling as they fade. */
    static void shards(Fx c,EffectMesh m,float t,Vec3d x,boolean additive){
        for(int k=0;k<40;k++){
            float born=RELEASE+.03f+.14f*(float)Ease.hash(k*3+31),life=.9f+.8f*(float)Ease.hash(k*5+32),s=t-born;if(s<0||s>=life)continue;float u=s/life;
            Vec3d p0=x.add(c.right.multiply((Ease.hash(k*7+33)-.5)*13)).add(0,(Ease.hash(k*11+34)-.5)*.9,0);
            double drag=1-Math.exp(-s*2.2);
            Vec3d v=new Vec3d((Ease.hash(k*13+35)-.5)*4.5,1.5+3.5*Ease.hash(k*17+36),(Ease.hash(k*19+37)-.5)*4.5);
            Vec3d p=p0.add(v.multiply(drag/2.2)).add(0,-1.6*s*s,0);
            double size=(.12+.2*Ease.hash(k*23+38))*(1-.5*u),spin=s*(5+7*Ease.hash(k*29+39))+k;
            Vec3d d=new Vec3d(Math.cos(spin),Math.sin(spin*.7),Math.sin(spin)).normalize().multiply(size),side=across(d).multiply(Math.cos(spin*1.3)).add(0,Math.sin(spin*1.3),0).normalize();
            float a=(1-Ease.smooth(u,.55f,1))*Ease.smooth(s,0,.03f);
            if(additive)m.needle(p.subtract(d),p.add(d),side,size*1.1,.5,k%4==0?WHITE:k%4==1?PINK:ROSE,.9f*a);
            else m.needle(p.subtract(d),p.add(d),side,size*1.3,.5,k%3==0?MAGENTA:PINK,.7f*a);
        }
    }
    /** Every time-stopped target flashes as the stored cuts land, and bursts magenta at the release. */
    static void hits(Fx c,EffectMesh m,float t){
        if(t<STORED-.01f||t>=RELEASE+.25f)return;
        for(var e:c.targets){
            Vec3d p=e.getLerpedPos(c.delta).add(0,e.getHeight()*.6,0);
            if(t<STORED+.16f){float u=Ease.unit(t,STORED,STORED+.16f),a=Ease.smooth(t,STORED-.01f,STORED+.01f)*(1-u);glint(m,p,.9*(1-u)+.2,LAVENDER,a);FxLib.sparks(m,p,8,1.2,.04,WHITE,a,e.getId()*7,Ease.out(t,STORED,STORED+.12f));}
            if(t>=RELEASE-.01f){float u=Ease.unit(t,RELEASE,RELEASE+.25f),a=Ease.smooth(t,RELEASE-.01f,RELEASE+.01f)*(1-u);FxLib.flare(m,p,1.1*(1-.4*u),MAGENTA,a,45);m.softDisc(p,1.1,PINK,.5f*a);}
        }
    }
    /** Chord direction (start end to end end), the way the slash bows, its radius and half-angle (radians) for cut i. */
    static Vec3d[] slashPlane(Cut cut,int i){
        Vec3d chord=cut.dir().multiply(-cut.from()),bulge=Fx.UP.subtract(chord.multiply(chord.dotProduct(Fx.UP)));
        if(bulge.lengthSquared()<1e-4)bulge=new Vec3d(0,0,1);bulge=bulge.normalize();
        return new Vec3d[]{chord,i%2==1?bulge.negate():bulge};
    }
    /** When slash i bursts out: the four stored cuts one after another from 1.8 s, the big cut with the final hit at 1.9 s. */
    static float slashAt(int i,long seed){
        int n=CUTS.length-1;if(i>=n)return RELEASE-.01f;
        // The four stored cuts burst out in an order and at gaps drawn per cast: the first with the stored hits at 1.8 s.
        var r=new java.util.SplittableRandom(seed*0x9E3779B97F4A7C15L+7);
        int[] order=new int[n];for(int k=0;k<n;k++)order[k]=k;
        for(int k=n-1;k>0;k--){int j=r.nextInt(k+1);int x=order[k];order[k]=order[j];order[j]=x;}
        float at=STORED;
        for(int k=0;k<n;k++){if(order[k]==i)return at;at+=.01f+.012f*(float)r.nextDouble();}
        return at;
    }
    static float slashAt(Fx c,int i){return slashAt(i,seed(c));}
    static float slashLife(int i){return i==CUTS.length-1?.5f:.32f;}
    /**
     * The cuts land as purple slashes along their frozen lines: at 1.8 s the four stored cuts burst out one after another
     * (the server's four stored hits), at 1.9 s the big cut sweeps out as one long, wide slash (the final hit). Each is a
     * brush crescent that sweeps out in a few frames and wears away from its tail.
     */
    @Override public void crescents(Fx c){
        float t=c.sec;if(t<STORED-.01f||t>=RELEASE+.5f)return;
        var list=cuts(c);
        for(int i=0;i<list.size();i++){
            boolean big=i==list.size()-1;float age=t-slashAt(c,i),life=slashLife(i);if(age<0||age>=life)continue;
            var cut=list.get(i);Vec3d[] pl=slashPlane(cut,i);
            double half=cut.length()*(big?.4:.28),h=Math.toRadians(big?62:55),r=half/Math.sin(h),deg=Math.toDegrees(h);
            Vec3d o=cut.centre().subtract(pl[1].multiply(r*Math.cos(h)));
            float grow=Ease.out(age,0,big?.07f:.05f),erase=Ease.smooth(age,big?.14f:.1f,life),a=Ease.smooth(age,0,.015f);
            KagenoFire.crescent(o,pl[1],pl[0],r,r*(big?.3:.26),-deg,2*deg*grow,big?12:11,i*3+7,erase,.7f,a,.15);
            if(big)KagenoFire.crescent(o,pl[1],pl[0],r*.9,r*.12,-deg+6,(2*deg-12)*grow,5,i*3+8,erase,.7f,.8f*a,.1);
        }
    }
    /** A round glint: soft light and a small white star (no long cross arms that would read as more cuts). */
    static void glint(EffectMesh m,Vec3d p,double r,int rgb,float alpha){
        if(alpha<=0||r<=0)return;
        m.softDisc(p,r,rgb,alpha*.45f);m.softDisc(p,r*.55,rgb,alpha*.7f);m.softDisc(p,r*.3,WHITE,alpha);m.star(p,r*.35,WHITE,alpha*.8f);
    }
    /** Light on the purple slashes: a glint running with each one's head and sparks thrown off it. */
    static void slashLight(Fx c,EffectMesh m,float t){
        if(t<STORED-.01f||t>=RELEASE+.3f)return;
        var list=cuts(c);
        for(int i=0;i<list.size();i++){
            boolean big=i==list.size()-1;float age=t-slashAt(c,i);if(age<0||age>=.22f)continue;
            var cut=list.get(i);Vec3d[] pl=slashPlane(cut,i);
            double half=cut.length()*(big?.4:.28),h=Math.toRadians(big?62:55),r=half/Math.sin(h);
            float grow=Ease.out(age,0,big?.07f:.05f),u=age/.22f;double ang=-h+2*h*grow;
            Vec3d o=cut.centre().subtract(pl[1].multiply(r*Math.cos(h))),head=o.add(pl[1].multiply(Math.cos(ang)*r)).add(pl[0].multiply(Math.sin(ang)*r));
            glint(m,head,(big?1.5:.8)*(1-u)+.2,0xc77dff,(1-u)*Ease.smooth(age,0,.015f));
            for(int k=0;k<(big?5:2);k++){
                Vec3d d=pl[0].multiply(.8).add((Ease.hash(i*37+k*3)-.5)*1.2,(Ease.hash(i*41+k*5)-.4)*1.2,(Ease.hash(i*43+k*7)-.5)*1.2).normalize();
                Vec3d p=head.add(d.multiply((big?3:1.8)*u*(.5+.5*Ease.hash(i*47+k))));
                m.needle(p,p.add(d.multiply(.3)),.06,.5,k%2==0?WHITE:0xd9a8ff,(1-u)*(1-u));
            }
        }
    }
    /** Fire-shader cells on fixed planes: the purple aura licking off the sword, and the magenta smoke the band leaves hanging. */
    @Override public void shaders(Fx c){
        float t=c.sec;List<Vec3d> pts=new ArrayList<>();List<float[]> cells=new ArrayList<>();
        float aura=Math.min(1,glow(t))*(1-.75f*cyan(t));
        if(aura>.02f){
            var hb=sword(c);Vec3d guard=hb[0].add(hb[1].multiply(GUARD)),tip=hb[0].add(hb[1].multiply(TIP));float f=lit(t);
            for(int i=0;i<7;i++){
                float ph=(float)((t*2.2+Ease.hash(i*5+1))%1);int cycle=(int)Math.floor(t*2.2+Ease.hash(i*5+1));
                Vec3d p=guard.add(tip.subtract(guard).multiply(f*(.08+.84*Ease.hash(i*7+2)))).add(0,.25*ph,0);
                pts.add(p);cells.add(new float[]{(float)((.16+.1*Ease.hash(i*11+3))*(1-.4*ph)),.55f*aura*(float)Math.sin(Math.PI*ph),.55f+.12f*(float)Ease.hash(i*13),.9f,i*31+cycle*7+3});
            }
        }
        if(t>=RELEASE&&t<RELEASE+1.4f){
            Vec3d x=crossing(c);float a=Ease.smooth(t,RELEASE,RELEASE+.08f)*(1-Ease.smooth(t,RELEASE+.45f,RELEASE+1.4f)),grow=Ease.out(t,RELEASE,RELEASE+.9f);
            for(int i=0;i<28;i++){
                // Puffs packed unevenly along the band (denser near the crossing), overlapping into one cloud.
                double along=(Ease.hash(i*3+51)-.5),spread=Math.signum(along)*Math.pow(Math.abs(along)*2,1.4)*7.5;
                Vec3d p=x.add(c.right.multiply(spread)).add(0,(Ease.hash(i*5+52)-.5)*.9+.6*grow*Ease.hash(i*7+53),0).add(c.f.multiply((Ease.hash(i*11+54)-.5)*1.4));
                float near=(float)(1-.4*Math.abs(along)*2);
                pts.add(p);cells.add(new float[]{(float)((.7+.6*Ease.hash(i*13+55))*(.65+.45*grow)*(.8+.3*near)),.55f*a*near,.82f+.14f*(float)Ease.hash(i*17+56),.3f,i*37+11});
            }
        }
        if(!pts.isEmpty())KagenoFire.cells(c.matrix,c.camera,pts,cells,c.age);
        // Dust thrown up where the floor heaves, on the same fixed planes: each puff rises as the wave passes under it.
        if(t>=RELEASE&&t<RELEASE+1.6f&&dev.sangwoo.celestialfall.client.vfx.FxLayer.available()){
            List<Vec3d> dust=new ArrayList<>();List<float[]> puffs=new ArrayList<>();Vec3d floor=floorUnder(c);
            for(int i=0;i<18;i++){
                double side=(Ease.hash(i*7+83)-.5)*2;
                float born=RELEASE+(float)(Math.abs(side)*HEAVE/HEAVE_SPEED)+.04f*(float)Ease.hash(i*3+81),s=t-born,life=.8f+.5f*(float)Ease.hash(i*5+82);
                if(s<0||s>=life)continue;float u=s/life;
                dust.add(floor.add(c.right.multiply(side*HEAVE*.95)).add(c.f.multiply((Ease.hash(i*11+84)-.5)*2.2)).add(0,.3+.55*u,0));
                puffs.add(new float[]{(float)((.4+.55*Ease.out(u,0,.6f))*(.8+.4*Ease.hash(i*13+85))),.9f*Ease.smooth(s,0,.05f)*(1-Ease.smooth(u,.55f,1)),.55f*(1-u),u,i*29+5});
            }
            if(!dust.isEmpty())KagenoFire.dust(c.matrix,c.camera,dust,puffs,c.age);
        }
    }
    /** A pink-white flash for viewers near the crossing as the slit opens and as the band explodes. */
    @Override public float flash(Fx c){
        float t=c.sec;if(t<STORED-.02f||t>=RELEASE+.4f)return 0;
        double d=c.camera.distanceTo(crossing(c));float near=(float)Math.max(0,Math.min(1,(16-d)/10));
        float slit=.3f*Ease.window(t,STORED-.01f,STORED+.06f,.02f,.04f);
        // On the effect layer the impact frame takes the first frames of the release; the flash follows it, softer.
        float release=dev.sangwoo.celestialfall.client.vfx.FxLayer.available()
            ?(t>=RELEASE+.09f?.3f*Ease.smooth(t,RELEASE+.09f,RELEASE+.115f)*(float)Math.exp(-Math.max(0,t-RELEASE-.115f)/.1):0)
            :t>=RELEASE-.01f?.55f*Ease.smooth(t,RELEASE-.01f,RELEASE+.015f)*(float)Math.exp(-Math.max(0,t-RELEASE-.015f)/.08):0;
        return near*Math.max(slit,release);
    }
    @Override public int flashColor(Fx c){return 0xffd8f6;}
    // ───────────────────────────── effect layer ─────────────────────────────

    @Override public boolean layered(){return true;}
    /**
     * The finisher's impact frame: in over two frames as the final cut lands, held about three frames (white shapes on
     * black, then over a frame and a half dark shapes on white), out over two frames.
     */
    static float impact(float t){
        if(t<RELEASE-.01f||t>=RELEASE+.125f)return 0;
        return Ease.smooth(t,RELEASE-.01f,RELEASE+.02f)*(1-Ease.smooth(t,RELEASE+.085f,RELEASE+.125f));
    }
    static float impactMode(float t){return Ease.smooth(t,RELEASE+.04f,RELEASE+.075f);}
    /** A pulse that comes in over a frame at {@code at} and dies away with time constant {@code tau}. */
    static float kick(float t,float at,float tau){return t<at-.01f?0:Ease.smooth(t,at-.01f,at+.015f)*(float)Math.exp(-Math.max(0,t-at-.015f)/tau);}
    /** Top of the floor under the crossing. */
    static Vec3d floorUnder(Fx c){Vec3d x=crossing(c);return new Vec3d(x.x,c.origin.y,x.z);}
    /**
     * Lights: the sword's aura (cyan while time stands still), the stop's pulse, the frozen cuts, every cut's birth,
     * the column, each purple slash, then the release's white-pink burst with magenta along the band and the haze it
     * leaves. Light shafts from the column and the burst, the impact frame and a chromatic split for viewers near the
     * crossing, and the big cut's scar on the floor: glowing as it tears open along the band, cooling to a burn.
     */
    @Override public void frame(Fx c,FxFrame f){
        float t=c.sec;Vec3d x=crossing(c);var list=cuts(c);
        float g=Math.min(1.4f,glow(t))*lit(t);
        if(g>.01f){var hb=sword(c);f.light(hb[0].add(hb[1].multiply((GUARD+TIP)*.5)),palette(cyan(t))[1],4.5,.9f*g);}
        float stop=Ease.smooth(t,GRAY-.01f,GRAY+.02f)*(1-Ease.smooth(t,GRAY+.04f,GRAY+.3f));
        if(stop>0)f.light(c.pos.add(0,1.1,0),ICE[2],8,2.2f*stop);
        float held=frozen(t)*Ease.smooth(t,COCKS[0],CUTS[0]);
        if(held>0)f.light(x,0xc9a2ff,6,.7f*held);
        for(int i=0;i<CUTS.length;i++){float b=t-COCKS[i];if(b>=0&&b<.15f)f.light(span(list.get(i),drawn(t,i))[0],LAVENDER,4,1.4f*(1-b/.15f)*Ease.smooth(b,0,.015f));}
        if(t>=STORED-.01f&&t<STORED+.12f){float u=Ease.unit(t,STORED,STORED+.12f);f.light(x,ROSE,10,1.6f*Ease.smooth(t,STORED-.01f,STORED+.02f)*(1-u*u));}
        for(int i=0;i<list.size();i++){
            float age=t-slashAt(c,i),life=slashLife(i);if(age<0||age>=life)continue;boolean big=i==list.size()-1;
            f.light(list.get(i).centre(),big?0xc06bff:VIOLET,big?9:6,(big?2.2f:1.2f)*Ease.smooth(age,0,.02f)*(1-Ease.smooth(age,.05f,life)));
        }
        if(t>=RELEASE-.01f){
            f.light(x,0xffd6f6,15,3f*kick(t,RELEASE,.16f));
            float band=Ease.smooth(t,RELEASE,RELEASE+.04f)*(1-Ease.smooth(t,RELEASE+.15f,RELEASE+.9f));
            for(int k=-1;k<=1;k+=2)f.light(x.add(c.right.multiply(k*5.5)),MAGENTA,9,1.5f*band);
            float haze=Ease.smooth(t,RELEASE+.1f,RELEASE+.3f)*(1-Ease.smooth(t,RELEASE+.6f,RELEASE+1.8f));
            f.light(x.add(0,.5,0),0xd040ff,11,.7f*haze);
        }
        float shaft=.22f*Ease.window(t,STORED-.01f,STORED+.1f,.02f,.03f)+.6f*kick(t,RELEASE,.18f);
        if(shaft>.01f)f.shaft(x,0xffc8f4,shaft);
        float near=(float)Math.max(0,Math.min(1,(26-c.camera.distanceTo(x))/12));
        if(near>0){f.impact(near*impact(t),impactMode(t));f.chroma(near*.01f*kick(t,RELEASE,.12f));}
        if(t>=RELEASE){
            float heat=1-Ease.smooth(t,RELEASE+.2f,RELEASE+1.6f),a=Ease.smooth(t,RELEASE,RELEASE+.06f)*(1-Ease.smooth(t,3.5f,3.95f));
            Vec3d g0=floorUnder(c);double open=Ease.out(t,RELEASE,RELEASE+.12f);
            f.decal(g0,c.right,7.5*open+.3,.85,heat,a,3.1f);
            f.decal(g0.add(c.f.multiply(.4)),c.right.multiply(.9).add(c.f.multiply(.43)),3.8*open+.2,.45,heat*.85f,.85f*a,7.3f);
            f.decal(g0.subtract(c.f.multiply(.4)),c.right.multiply(.9).subtract(c.f.multiply(.43)),3.4*open+.2,.42,heat*.85f,.85f*a,11.7f);
        }
    }
    /** Air bent along every purple slash as it sweeps (most along the big one), a shock ring racing over the floor and one round the crossing at the release. */
    @Override public void distort(Fx c,FxDistort d){
        float t=c.sec;if(t<STORED-.01f||t>=RELEASE+.6f)return;
        var list=cuts(c);Vec3d x=crossing(c);
        for(int i=0;i<list.size();i++){
            boolean big=i==list.size()-1;float age=t-slashAt(c,i),life=big?.3f:.18f;if(age<0||age>=life)continue;
            var cut=list.get(i);Vec3d[] pl=slashPlane(cut,i);
            double half=cut.length()*(big?.4:.28),h=Math.toRadians(big?62:55),r=half/Math.sin(h),deg=Math.toDegrees(h);
            Vec3d o=cut.centre().subtract(pl[1].multiply(r*Math.cos(h)));
            float grow=Ease.out(age,0,big?.07f:.05f),s=(big?1f:.55f)*Ease.smooth(age,0,.02f)*(1-Ease.smooth(age,.03f,life));
            d.arc(o,pl[1],pl[0],r*1.04,r*(big?.42:.34),-deg,2*deg*grow,s);
        }
        if(t>=RELEASE-.01f){
            float in=Ease.smooth(t,RELEASE-.01f,RELEASE+.015f);
            d.ring(floorUnder(c).add(0,.05,0),c.right,c.f,.6+14*Ease.out(t,RELEASE,RELEASE+.45f),1.8,.9f*in*(1-Ease.unit(t,RELEASE,RELEASE+.45f)));
            d.ring(x,c.right,Fx.UP,.5+6*Ease.out(t,RELEASE,RELEASE+.3f),1.1,.8f*in*(1-Ease.unit(t,RELEASE,RELEASE+.3f)));
        }
    }
    /** Chunks thrown along the band at the release (out, up, bouncing on the floor). */
    static final int CHUNKS=26;
    static Vec3d chunkStart(Vec3d right,Vec3d forward,Vec3d floor,int k){
        double side=(Ease.hash(k*5+72)-.5)*2;
        return floor.add(0,.15,0).add(right.multiply(side*7.5)).add(forward.multiply((Ease.hash(k*7+73)-.5)*1.6));
    }
    static Vec3d chunkVelocity(Vec3d right,Vec3d forward,int k){
        double side=Ease.hash(k*5+72)-.5;
        return right.multiply(Math.signum(side)*(1+4*Ease.hash(k*11+74))).add(forward.multiply((Ease.hash(k*13+75)-.5)*3)).add(0,5+6*Ease.hash(k*17+76),0);
    }
    static double chunkSize(int k,float age){return (.14+.2*Ease.hash(k*19+77))*(1-Ease.smooth(age,1.55f,1.95f));}
    static float chunkBorn(int k){return .015f*(float)Ease.hash(k*3+71);}
    /** How far out (blocks) and how fast (blocks a second) the floor heaves along the band. */
    static final int HEAVE=11;static final double HEAVE_SPEED=26;
    /**
     * The release tears the floor: chunks of the floor block fly up along the band, spin, bounce and settle, shrinking
     * away after 1.6 s; the floor heaves in a wave running out both ways along the band (not under the crossing).
     */
    @Override public void solids(Fx c,FxSolids s){
        float t=c.sec;if(t<RELEASE||t>=RELEASE+2.1f)return;
        float since=t-RELEASE;Vec3d floor=floorUnder(c);
        BlockState block=s.ground(floor.add(0,-.5,0));
        if(block!=null)for(int k=0;k<CHUNKS;k++){
            float age=since-chunkBorn(k);if(age<0)continue;
            double size=chunkSize(k,age);
            Vec3d p=FxSolids.flight(chunkStart(c.right,c.f,floor,k),chunkVelocity(c.right,c.f,k),floor.y+size*.5,age);
            float spin=(float)((240+420*Ease.hash(k*23+78))*.6*(1-Math.exp(-age/.6)));
            s.chunk(block,p,size,spin+k*37,spin*.7f+k*11,spin*.4f);
        }
        java.util.Set<BlockPos> done=new java.util.HashSet<>();
        BlockPos base=BlockPos.ofFloored(floor.x,floor.y-.5,floor.z);
        for(int k=-HEAVE;k<=HEAVE;k++)for(int lat=-1;lat<=1;lat++){
            Vec3d q=Vec3d.ofCenter(base).add(c.right.multiply(k)).add(c.f.multiply(lat));
            BlockPos pos=BlockPos.ofFloored(q);if(!done.add(pos))continue;
            double dist=Math.hypot(pos.getX()+.5-floor.x,pos.getZ()+.5-floor.z);
            double lift=FxSolids.wave(dist,since,.55,HEAVE_SPEED,1.3)*Ease.smooth((float)dist,1.2f,2.6f)*(lat==0?1:.7);
            if(lift>.02)s.lifted(pos,s.surface(pos),lift,(float)(lift*28*Math.signum(k)),c.f);
        }
    }

    /** Shake as the stored cuts land and harder with the release, each coming in over two frames. */
    @Override public float shake(float sec){
        float s=0;
        if(sec>=STORED-.02f)s=Math.max(s,.9f*Ease.smooth(sec,STORED-.02f,STORED+.015f)*(float)Math.exp(-Math.max(0,sec-STORED-.015f)/.08));
        if(sec>=RELEASE-.02f)s=Math.max(s,2.2f*Ease.smooth(sec,RELEASE-.02f,RELEASE+.015f)*(float)Math.exp(-Math.max(0,sec-RELEASE-.015f)/.16));
        return s;
    }
    @Override public int afterimageColor(){return 0x9d86ff;}
}
