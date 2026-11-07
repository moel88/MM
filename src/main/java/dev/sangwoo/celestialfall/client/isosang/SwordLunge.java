package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.KagenoFire;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * Lee So-sang's T as drawn (2.96.0, user request: "이번에는 T스킬 만들자 영상처럼 일단 T를 누르면 평타 6검이 날아가는 동시에
 * 빠르게 바라보는 상대에게 돌진 베어내고 적에게 3 참격으로 베이는 동시에 다른 검으로 넘어가는 스킬이야", the user's video of it
 * (e6830cf0, 60 fps, used twice)). 2.96.2 drew all of it anew (user request: "3참격 느낌이 영상이랑 너무 달라 영상을 보고 완전
 * 똑같이 만들어봐 지금 너무 이상해 3참격도 이상하고 돌진하는 것도 이상해 스킬 이펙트를 싹 다 갈아엎고 영상이랑 비슷하게
 * 만들어봐", the video read again frame by frame on a sheet and confirmed: "맞음, 이대로", and where she lands: "상대 바로 앞
 * 옆 (영상처럼)"):
 * <ul>
 * <li>as she coils on her sword the six swords of H's basic attack fan out behind her (SwordVolley, on T's clock) and a
 * thin black line of ink is drawn from behind her to the foe, the way she will go;</li>
 * <li>she dashes, her body trailing see-through violet copies of itself (BodyGhost's silhouettes, IsosangSlash.afterimages), and a long drop of light
 * is laid from behind where she set out to the foe in a blink: far wider than she is, white-hot along its middle in
 * lanes, cyan and blue to its sides, lilac at its tail, a dark wedge of ink down one side, pointed at the foe as it
 * is laid and opening round it, its rim a bright blue line; it cools through cyan to a see-through blue, its rim
 * turning to a band of ink with a cyan edge inside, which thins and is gone by some 0.6 s (the ink pass's styles 8
 * and 6, {@link #ink});</li>
 * <li>she is down low just in front of the foe at its side as its light goes (IsosangRules.lungeLanding), then the
 * orb of Z's flight on to a sword on the other side of the formation from hers (SwordStep, the formation's flights)
 * while the swords come down on the foe;</li>
 * <li>three slashes are drawn across it one after another as she lands there: long, thin blades pointed at both ends
 * (the ink pass's style 7: a white heart, royal blue, an edge of black ink), two crossing diagonals and one nearly
 * level through its legs, crossing on it and reaching long toward where she stands now (her camera is with her:
 * 2.96.1, "카메라는 플레어한테 고정"), so from there they spread out from the foe toward her as the video's do toward
 * its camera; bright a moment, then darkening into thin lines of ink and gone.</li>
 * </ul>
 * Every effect is a function of the swing's clock and of where the foe is drawn (the swords, the drop's head and the
 * slashes follow it).
 */
final class SwordLunge {
    private SwordLunge(){}
    /** How long T is drawn for (seconds, IsosangClient.life): until its last slash has thinned into ink and gone. */
    static final float LIFE=LUNGE_LENGTH/20f+.25f;

    // ───────────────────────────── the body ─────────────────────────────
    // (the video: on the pommel she coils, the sword drawn back low at the right, and goes, seen as she dashes, her
    // afterimages violet; low in a crouch just in front of the foe at its side, the sword swept through across the front
    // to the left; out of sight as she flies on; down on the far pommel, her feet together, and up as she stands there.
    // The body faces the way she dashed until she lands on the sword she flies on to, then turns back to the formation's
    // way: IsosangClient.lungeFacing.)
    /** Coiled on the pommel: a dip, the sword drawn back low at the right, the free hand reaching ahead. */
    static final R6.Pose L_COIL=w(.04,-.16,-16,14,0,d(-32,118),d(-16,168),d(-22,-40),d(-72,8),d(-102,-8));
    /** Going: lunging out flat, the sword trailing. */
    static final R6.Pose L_GO=w(.16,-.06,-8,32,0,d(-12,150),d(-6,176),d(4,-22),d(-58,6),d(-122,-6));
    /** Cutting as she comes: the sword across the front. */
    static final R6.Pose L_CUT=w(.12,-.14,14,24,2,d(-8,10),d(-6,-20),d(-30,140),d(-60,6),d(-118,-6));
    /** Down in front of the foe in a low crouch, the sword swept through out low at the left, the free arm back. */
    static final R6.Pose L_THROUGH=w(.1,-.32,24,20,4,d(-14,-72),d(-12,-90),d(-28,150),30,-150);
    static final R6.Pose L_KNEEL=w(.1,-.3,22,17,4,d(-16,-76),d(-14,-92),d(-30,150),30,-150);
    /** Down on the far pommel: her feet together, bowing a little into it, her arms out for balance (as she lands from H's leap). */
    static final R6.Pose L_ON=w(.03,0,-2,9,0,d(-20,62),d(-6,96),d(-12,-74),d(-85,7),d(-87,-7));
    static final R6.Pose L_ON2=w(.02,0,-1,5,0,d(-8,74),d(4,100),d(-2,-80),d(-87,5),d(-88,-5));
    static final Anim CLIP=Anim.joints(
        k(0,STANCE),
        k(.08,L_COIL,Anim.Ease.OUT),
        k(LUNGE_GO/20f,L_GO,Anim.Ease.IN),
        k(.28,L_CUT),
        k(LUNGE_LAND/20f,L_THROUGH,Anim.Ease.OUT),
        k(LUNGE_HOP/20f-.02f,L_KNEEL),
        k((LUNGE_HOP+Z_TRAVEL)/20f,L_ON,Anim.Ease.OUT),
        k(.86,L_ON2),
        k(1.05,SwordFormation.H_ON),
        k(LUNGE_LENGTH/20f,STANCE)).after(IsosangSlash::grounded);

    // ───────────────────────────── where things are ─────────────────────────────

    /** How high over her feet her chest is (on the pommel and on the ground). */
    static final double CHEST=1.05;
    /** How high over the foe's feet the point the swords and slashes come down on is (SwordVolley.target: 55% of its height; a player's). */
    static final double FOE_MID=1.0;
    /** Where she lands in front of the foe (the server's word), or where she stood. */
    static Vec3d past(StandClient.Cast c){var r=IsosangClient.lunge(c.id());return r==null?c.origin():r.past();}
    /** The way she dashes at {@code foe} (level, unit): from where she stood to it. */
    static Vec3d way(StandClient.Cast c,Vec3d foe){
        Vec3d d=foe==null?Vec3d.ZERO:foe.subtract(c.origin()).multiply(1,0,1);
        return d.lengthSquared()<1e-4?Vec3d.fromPolar(0,c.yaw()):d.normalize();
    }
    /** Whether slash {@code j} of T {@code id} is cut (or will be): not one due after it was called off. */
    static boolean cuts(UUID id,int j){float end=IsosangClient.volleyEnd(id);return Float.isNaN(end)||lungeSlash(j)<=end;}
    /** Whether its dash cuts the foe (not called off before it came). */
    static boolean passes(UUID id){float end=IsosangClient.volleyEnd(id);return Float.isNaN(end)||blowAt(LUNGE,0)/20f<=end;}
    /** How much of T {@code id} still shows {@code t} seconds in: all of it, or, called off, fading over a tenth of a second from then. */
    static float kept(UUID id,float t){return SwordVolley.kept(id,t);}

    // ───────────────────────────── the line of ink: the way she will go ─────────────────────────────

    /**
     * The thin black line drawn from behind her to the foe as she coils (the video: from behind her, through her, to the
     * foe, a moment before she goes): from {@link #AIM_BACK} blocks behind her chest, a little lower, drawn on over
     * [AIM0, AIM1], gone as her dash's light cools.
     */
    static final double AIM_BACK=3.2,AIM_WIDTH=.075;
    static final float AIM0=.04f,AIM1=.11f,AIM_GONE0=.22f,AIM_GONE1=.32f;

    // ───────────────────────────── the drop of light: her dash ─────────────────────────────

    /**
     * The drop of light her dash leaves (the video: laid from behind where she set out to the foe in a blink, far wider
     * than she is, pointed at the foe as it is laid and opening round it): its axis from {@link #TAIL} blocks behind her
     * chest on her sword ({@link #TAIL_DROP} lower) to the foe, {@link #HALF} blocks either side of it (its tail
     * narrowing to a point), its end round the foe; its head racing to the foe over [GO-.01, GO+{@link #RACE}], its end
     * opening round the foe over [GO+.05, GO+{@link #OPEN}]. Its rim a band {@link #RIM} wide round it.
     */
    static final double TAIL=2.2,TAIL_DROP=.45,HALF=1.6,RIM=.55;
    static final float RACE=.06f,OPEN=.13f;
    /**
     * When it cools (seconds after she sets off): its light through cyan to a see-through blue over [FILL_INK0, FILL_INK1],
     * eaten from its tail over [FILL_EAT0, FILL_EAT1]; its rim to ink over [RIM_INK0, RIM_INK1] (the side of it on her
     * left a little sooner, the video's), thinning and eaten from both ends in toward the foe over [RIM_EAT0, RIM_EAT1]
     * (the video: white-hot 0.2 to 0.27 s, blue by 0.33 s, ink by 0.37 s, thin by 0.42 s, gone by some 0.6 s).
     */
    static final float FILL_INK0=.08f,FILL_INK1=.27f,FILL_EAT0=.17f,FILL_EAT1=.36f,RIM_INK0=.1f,RIM_INK1=.27f,RIM_LEAD=.045f,RIM_EAT0=.26f,RIM_EAT1=.48f;
    /** Its axis, how far its head has come and how round its end is now. */
    record Drop(Vec3d tail,Vec3d along,Vec3d side,double length,double open){
        Vec3d at(double s,double l){return tail.add(along.multiply(s)).add(side.multiply(l));}
        /** How far along its axis its end is. */
        double tip(){return length+.35+(HALF-.35)*open;}
        /** How far either side of its axis it reaches {@code s} blocks along it. */
        double width(double s){
            double taper=Math.min(2.4,length*.3);
            double w=s<taper?HALF*(.16+.84*Math.sin(Math.PI*.5*s/taper)):HALF;
            double round=s>length?Math.sqrt(Math.max(0,HALF*HALF-(s-length)*(s-length))):HALF;
            double point=HALF*Math.max(0,Math.min(1,(length+.35-s)/(1.6*HALF+.35)));
            return Math.min(w,round*open+point*(1-open));
        }
        /** Where its axis meets along it to its end: evenly to the foe, then closer as it rounds off. */
        double[] samples(){
            int n1=20,n2=10;double[] s=new double[n1+n2+1];double tip=tip();
            for(int i=0;i<=n1;i++)s[i]=length*i/n1;
            for(int i=1;i<=n2;i++)s[n1+i]=length+(tip-length)*Math.sin(Math.PI*.5*i/n2);
            return s;
        }
        /** Its head now (on its axis). */
        Vec3d head(){return at(length,0);}
    }
    /** The drop {@code t} seconds in, the foe at {@code foe}. */
    static Drop drop(StandClient.Cast c,Vec3d foe,float t){
        float go=LUNGE_GO/20f;
        Vec3d chest=c.origin().add(0,CHEST,0),wl=way(c,foe);
        Vec3d tail=chest.subtract(wl.multiply(TAIL)).add(0,-TAIL_DROP,0),axis=foe.subtract(tail);
        double full=Math.max(.5,axis.length());
        return new Drop(tail,axis.multiply(1/full),new Vec3d(-wl.z,0,wl.x),full*Ease.out(t,go-.01f,go+RACE),Ease.smooth(t,go+.05f,go+OPEN));
    }
    /** Its light (the ink pass's style 8): across it from one edge to the other, along it from its tail to its end. */
    static void fill(Drop d,float erase,float ink,float alpha){
        double[] s=d.samples();int n=s.length;double tip=d.tip();
        Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){double w=d.width(s[i]);in[i]=d.at(s[i],-w);out[i]=d.at(s[i],w);along[i]=(float)(s[i]/tip);}
        KagenoFire.inkRibbon(in,out,along,n,8,5,erase,ink,alpha);
    }
    /**
     * The layers of light laid with it (the video: several white, sky and blue bands at once from behind her to the foe,
     * wider than she is, pointed at the foe, spread apart behind her): each from its own place round the drop's tail (to
     * its side, up, how wide) to its head, facing the camera along it; the ink pass's style 8 (its layers: seeds 8 to 15,
     * no wedge of ink).
     */
    static final double[][] LAYERS={{-1.1,1.0,.8},{1.2,.6,.7},{-.5,-.4,.9},{.6,1.6,.55}};
    static void layers(Drop d,Vec3d camera,float erase,float ink,float alpha){
        Vec3d head=d.head();
        for(int i=0;i<LAYERS.length;i++){
            double[] l=LAYERS[i];Vec3d tail=d.tail().add(d.side().multiply(l[0])).add(0,l[1],0);
            Vec3d axis=head.subtract(tail);double len=axis.length();if(len<.3)continue;Vec3d dir=axis.multiply(1/len);
            int n=12;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];Vec3d prev=null;
            for(int j=0;j<n;j++){
                double u=j/(double)(n-1);Vec3d p=tail.add(axis.multiply(u));
                double w=l[2]*.5*Math.pow(1-u,.55)*(.35+.65*Math.min(1,u/.12));
                Vec3d sd=dir.crossProduct(camera.subtract(p));sd=sd.lengthSquared()<1e-8?(prev==null?d.side():prev):sd.normalize();prev=sd;
                in[j]=p.subtract(sd.multiply(w));out[j]=p.add(sd.multiply(w));along[j]=(float)u;
            }
            KagenoFire.inkRibbon(in,out,along,n,8,8+i,erase,ink,alpha);
        }
    }
    /**
     * Its rim (the ink pass's style 6): a band of even width round it, from its tail on her left round the foe to its tail
     * on her right, inside its edge; {@code t} seconds in (the left side goes to ink a little sooner).
     */
    static void rim(Drop d,float t,float go,float erase,float alpha){
        double[] s=d.samples();int n=s.length,m=2*n-1;
        double[] qs=new double[m],ql=new double[m];
        for(int i=0;i<n;i++){qs[i]=s[i];ql[i]=-d.width(s[i]);}
        for(int i=1;i<n;i++){qs[n-1+i]=s[n-1-i];ql[n-1+i]=d.width(s[n-1-i]);}
        double[] len=new double[m];for(int i=1;i<m;i++)len[i]=len[i-1]+Math.hypot(qs[i]-qs[i-1],ql[i]-ql[i-1]);
        double total=Math.max(1e-6,len[m-1]);
        Vec3d[] in=new Vec3d[m],out=new Vec3d[m];float[] along=new float[m],ink=new float[m];
        for(int i=0;i<m;i++){
            int a=Math.max(0,i-1),b=Math.min(m-1,i+1);double ts=qs[b]-qs[a],tl=ql[b]-ql[a],tn=Math.hypot(ts,tl);
            // inward: the way along it turned a right angle toward its axis
            double ns=tn<1e-9?0:-tl/tn,nl=tn<1e-9?0:ts/tn;
            double room=Math.abs(nl)>1e-3?Math.abs(ql[i])/Math.abs(nl):99,wd=Math.min(RIM,.85*room);
            out[i]=d.at(qs[i],ql[i]);in[i]=d.at(qs[i]+ns*wd,ql[i]+nl*wd);
            along[i]=(float)(len[i]/total);
            float lead=along[i]<.5f?RIM_LEAD:0;ink[i]=Ease.smooth(t,go+RIM_INK0-lead,go+RIM_INK1-lead);
        }
        KagenoFire.inkRibbon(in,out,along,m,6,9,erase,ink,alpha);
    }

    // ───────────────────────────── the three slashes ─────────────────────────────

    /**
     * Each slash (the video: three long thin blades crossing on the foe, two diagonals and one nearly level through its
     * legs, spreading out from it toward its camera and on out of the picture): where it crosses the foe's column (up from
     * its feet, and to the right of the way toward her), how far past her its near tip reaches (for the level one: what
     * share of the way to her it reaches, 3 to {@link #LEVEL_REACH} blocks, and how far to her right), how high that tip is
     * over the foe's feet, and how wide it is (blocks). The diagonals run out past her at her sides, going by her at least
     * {@link #PASS} blocks off (her camera is with her, behind her: from it they spread down out of the picture as the
     * video's do toward its camera); the level one runs off across.
     */
    static final double[][] BLADES={
        {1.5,-.15, 1.0,0,.2, .6},
        {.45,.10, .5,-8.5,.3, .5},
        {1.2,.15, .6,0,.2, .6}};
    /** How far each reaches beyond the foe (blocks); how far toward her the level one reaches at most; how far beside her the diagonals go by. */
    static final double FAR=2.4,LEVEL_REACH=5,PASS=3;
    /** How fast each is drawn on (from its far tip toward her), how long it stays bright, how it cools to ink and is eaten (seconds from its cut). */
    static final float DRAW=.045f,LIT=.12f,DIM=.3f,INK0=.15f,INK0_FIRST=.02f,INK1=.34f,EAT0=.3f,EAT1=.46f;
    /** A slash's line: its far tip beyond the foe and its near tip toward her, and how wide it is. */
    record Blade(Vec3d far,Vec3d near,double width){
        Vec3d point(double u){return far.add(near.subtract(far).multiply(u));}
        Vec3d way(){return near.subtract(far).normalize();}
    }
    /**
     * Toward where she stands as the slashes cut (level, its length how far): the sword she flies on to (the server's
     * word), else back the way she came.
     */
    static Vec3d toward(StandClient.Cast c,Vec3d foe){
        var run=IsosangClient.lunge(c.id());var form=IsosangClient.formationOf(c.caster());
        var f=form==null?null:IsosangClient.formation(form.id());
        if(run!=null&&f!=null){Vec3d d=IsosangClient.place(form.id(),f,run.to()).subtract(foe).multiply(1,0,1);if(d.lengthSquared()>1)return d;}
        Vec3d d=c.origin().subtract(foe).multiply(1,0,1);
        return d.lengthSquared()>1?d:Vec3d.fromPolar(0,c.yaw()).multiply(-6);
    }
    /** Slash {@code j} on the foe at {@code foe}, {@code toward} her (toward). */
    static Blade blade(Vec3d foe,Vec3d toward,int j){
        double[] b=BLADES[j];double far=toward.length();Vec3d fw=toward.multiply(1/far),rt=new Vec3d(-fw.z,0,fw.x);
        Vec3d feet=foe.subtract(0,FOE_MID,0),at=feet.add(0,b[0],0).add(rt.multiply(b[1]));
        Vec3d near;
        if(j==1)near=feet.add(fw.multiply(Math.max(3,Math.min(LEVEL_REACH,b[2]*far)))).add(rt.multiply(b[3])).add(0,b[4],0);
        else{
            // turned off the way to her just enough to go by her PASS blocks off (a little more), the first to her right
            double turn=Math.min(Math.toRadians(70),(far>PASS+.1?Math.asin(PASS/far):Math.toRadians(70))+Math.toRadians(j==0?4:6));
            double reach=Math.min(far+9,(far+b[2])/Math.cos(turn)),side=j==0?1:-1;
            Vec3d level=fw.multiply(Math.cos(turn)).add(rt.multiply(side*Math.sin(turn)));
            near=new Vec3d(at.x,feet.y+b[4],at.z).add(level.multiply(reach));
        }
        Vec3d dir=near.subtract(at).normalize();
        return new Blade(at.subtract(dir.multiply(FAR)),near,b[5]);
    }
    /** Its band (the ink pass's style 7), facing the camera along it, drawn on from its far tip as far as {@code head}. */
    static void band(Blade b,Vec3d camera,double head,int seed,float erase,float ink,float alpha){
        if(head<=.01||alpha<=.01f)return;
        Vec3d d=b.way();int n=15;Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];Vec3d prev=null;
        for(int i=0;i<n;i++){
            double u=head*i/(n-1);Vec3d p=b.point(u);
            Vec3d s=d.crossProduct(camera.subtract(p));
            s=s.lengthSquared()<1e-8?(prev==null?new Vec3d(0,1,0):prev):s.normalize();prev=s;
            in[i]=p.subtract(s.multiply(b.width()*.5));out[i]=p.add(s.multiply(b.width()*.5));along[i]=(float)u;
        }
        KagenoFire.inkRibbon(in,out,along,n,7,seed,erase,ink,alpha);
    }
    /** How far along slash {@code j} its head has come {@code t} seconds in. */
    static double drawn(int j,float t){float at=lungeSlash(j);return Ease.out(t,at-.01f,at+DRAW);}
    /** How bright slash {@code j} is {@code t} seconds in (its light, not its ink). */
    static float lit(int j,float t){float at=lungeSlash(j);return Ease.smooth(t,at-.01f,at+.01f)*(1-Ease.smooth(t,at+LIT,at+DIM));}

    /** How big each slash's burst is, the burst as she cuts the foe, and each of the swords' bursts (IsosangEmpower.burst's size; 2.96.1: .85, 1.1, .8). */
    static final double SLASH_BURST=.65,PASS_BURST=.75,SWORD_BURST=.8;

    // ───────────────────────────── the passes ─────────────────────────────

    /** The ink pass: the drop of light (its light, then its rim) and the three slashes. */
    static void ink(Fx c){
        float t=c.sec,k=kept(c.cast.id(),t);if(k<=.01f)return;
        Vec3d foe=SwordVolley.target(c.cast,c.delta);if(foe==null)return;
        float go=LUNGE_GO/20f;
        if(t>=go-.01f&&t<go+RIM_EAT1){
            Drop d=drop(c.cast,foe,t);
            if(t<go+FILL_EAT1){
                float erase=Ease.smooth(t,go+FILL_EAT0,go+FILL_EAT1),ink=Ease.smooth(t,go+FILL_INK0,go+FILL_INK1),a=k*Ease.smooth(t,go-.01f,go+.01f);
                fill(d,erase,ink,a);layers(d,c.camera,erase,Math.min(1,ink*1.25f),a);
            }
            rim(d,t,go,Ease.smooth(t,go+RIM_EAT0,go+RIM_EAT1),k*Ease.smooth(t,go-.01f,go+.02f));
        }
        Vec3d to=toward(c.cast,foe);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+EAT1||!cuts(c.cast.id(),j))continue;
            float ink=Ease.smooth(t,at+(j==1?INK0_FIRST:INK0),at+INK1);
            band(blade(foe,to,j),c.camera,drawn(j,t),3+j*4,Ease.smooth(t,at+EAT0,at+EAT1),ink,k);
        }
    }
    /** The blended pass: the swords' bodies, the line of ink to the foe, the ink under each burst. */
    static void alpha(Fx c,EffectMesh m){
        SwordVolley.alpha(c,m);
        float t=c.sec,k=kept(c.cast.id(),t);Vec3d foe=SwordVolley.target(c.cast,c.delta);if(foe==null||k<=.01f)return;
        // the line of ink from behind her to the foe, the way she will go
        if(t>=AIM0&&t<AIM_GONE1){
            Vec3d wl=way(c.cast,foe),from=c.cast.origin().add(0,CHEST,0).subtract(wl.multiply(AIM_BACK)).add(0,-.5,0);
            Vec3d to=from.lerp(foe,Ease.out(t,AIM0,AIM1));
            m.line(from,to,AIM_WIDTH,INK,.9f*k*(1-Ease.smooth(t,AIM_GONE0,AIM_GONE1)));
        }
        for(int j=0;j<LUNGE_SLASHES;j++)if(cuts(c.cast.id(),j))IsosangEmpower.burstInk(c,m,foe,lungeSlash(j),SLASH_BURST,j*17+5);
        if(passes(c.cast.id()))IsosangEmpower.burstInk(c,m,foe,blowAt(LUNGE,0)/20f,PASS_BURST,3);
    }
    /**
     * The light under the ink: the swords' (SwordVolley), a violet shimmer round her as she coils, the burst on the foe as
     * she cuts it, a soft blue glow round each slash while it is bright, and its burst.
     */
    static void additive(Fx c,EffectMesh m){
        SwordVolley.additive(c,m);
        float t=c.sec,k=kept(c.cast.id(),t);if(k<=.01f)return;
        Vec3d foe=SwordVolley.target(c.cast,c.delta);
        float go=LUNGE_GO/20f;
        // coiling: a violet shimmer gathering round her
        if(t<go+.03f){
            float a=Ease.smooth(t,.02f,go-.02f)*(1-Ease.smooth(t,go,go+.03f))*k;Vec3d at=c.cast.origin().add(0,CHEST,0);
            m.softDisc(at,1.3,VIOLET,.3f*a);m.softDisc(at,.6,LILAC,.4f*a);
        }
        if(foe==null)return;
        if(passes(c.cast.id()))IsosangEmpower.burst(c,m,foe,way(c.cast,foe),blowAt(LUNGE,0)/20f,PASS_BURST,3);
        Vec3d to=toward(c.cast,foe);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+DIM||!cuts(c.cast.id(),j))continue;
            Blade b=blade(foe,to,j);float lit=k*lit(j,t);
            Vec3d head=b.point(drawn(j,t));
            m.needle(b.far(),head,b.width()*2.6,.55,BLUE,.22f*lit);m.needle(b.far(),head,b.width()*1.4,.55,AZURE,.3f*lit);
            IsosangEmpower.burst(c,m,foe,b.way(),at,SLASH_BURST,j*17+5);
        }
    }
    /** Over the ink: the swords' starlight (SwordVolley), the head of the light racing to the foe, and a glint at each slash's head as it is drawn on and at its tips. */
    static void overlay(Fx c,EffectMesh m){
        SwordVolley.overlay(c,m);
        float t=c.sec,k=kept(c.cast.id(),t);Vec3d foe=SwordVolley.target(c.cast,c.delta);if(foe==null||k<=.01f)return;
        float go=LUNGE_GO/20f;
        if(t>=go-.01f&&t<go+RACE+.06f){
            Vec3d head=drop(c.cast,foe,t).head();float a=k*Ease.smooth(t,go-.01f,go+.005f)*(1-Ease.smooth(t,go+RACE,go+RACE+.06f));
            m.softDisc(head,1.5,AZURE,.4f*a);m.softDisc(head,.55,WHITE,.85f*a);glint8(m,head,.7,WHITE,.8f*a);
        }
        Vec3d to=toward(c.cast,foe);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+.25f||!cuts(c.cast.id(),j))continue;
            Blade b=blade(foe,to,j);double h=drawn(j,t);
            float draw=k*Ease.smooth(t,at-.01f,at)*(1-Ease.smooth(t,at+DRAW,at+DRAW+.04f));
            if(draw>.01f){Vec3d p=b.point(h);glint8(m,p,.55,WHITE,.9f*draw);m.softDisc(p,.5,PALE,.6f*draw);}
            float tips=k*Ease.smooth(t,at+DRAW-.01f,at+DRAW+.01f)*(1-Ease.smooth(t,at+.1f,at+.25f));
            glint8(m,b.near(),.45,WHITE,.8f*tips);glint8(m,b.far(),.3,PALE,.6f*tips);
        }
    }
    /** Lights: the swords' (SwordVolley), the head of the light racing to the foe, the foe as she cuts it and as each slash cuts; the afterglow cut short throughout. */
    static void frame(Fx c,FxFrame f){
        SwordVolley.frame(c,f);
        float t=c.sec,k=kept(c.cast.id(),t);if(k<=.01f)return;
        float go=LUNGE_GO/20f,land=LUNGE_LAND/20f;
        Vec3d foe=SwordVolley.target(c.cast,c.delta);
        if(foe!=null){
            if(t>=go-.01f&&t<go+FILL_INK1){
                float a=k*Ease.smooth(t,go-.01f,go+.01f)*(1-Ease.smooth(t,go+FILL_INK0,go+FILL_INK1));
                Drop d=drop(c.cast,foe,t);f.light(d.head(),0xd6e6ff,10,1.0f*a);f.light(d.at(d.length()*.45,0),0xb8c8ff,8,.6f*a);
            }
            float kp=passes(c.cast.id())?FxLib.kick(t,blowAt(LUNGE,0)/20f,.15f):0;if(kp>.01f)f.light(foe,0xcfe4ff,9,.9f*kp);
            for(int j=0;j<LUNGE_SLASHES;j++){float ks=cuts(c.cast.id(),j)?FxLib.kick(t,lungeSlash(j),.18f):0;if(ks>.01f)f.light(foe,0x9fc8ff,10,1.0f*ks);}
        }
        // quick throughout (the dash, the flight on, the slashes): short afterglow, else each leaves copies of itself
        if(t<LUNGE_LENGTH/20f)f.afterglow(t<land+.1f?.02f:.05f);
    }
    /** A jolt as she cuts the foe (harder) and as each slash cuts, each over two frames; and the swords' (SwordVolley). */
    static float shake(StandClient.Cast cast,float sec){
        float v=SwordVolley.shake(cast,sec);
        if(passes(cast.id()))v=Math.max(v,.55f*bump(sec,blowAt(LUNGE,0)/20f,.08f));
        for(int j=0;j<LUNGE_SLASHES;j++)if(cuts(cast.id(),j))v=Math.max(v,.3f*bump(sec,lungeSlash(j),.06f));
        return v;
    }
    /**
     * How strongly her body leaves afterimages {@code t} seconds in (BodyGhost: 1 their full strength): in full as she
     * dashes (the video's violet copies of her as she goes), as faint as the sword's hits' otherwise.
     */
    static float afterimages(float t){
        float go=LUNGE_GO/20f,land=LUNGE_LAND/20f;
        return .25f+.75f*Ease.smooth(t,go-.03f,go)*(1-Ease.smooth(t,land+.05f,land+.15f));
    }
    /** A violet for the shimmer as she coils, and a deeper one for her afterimages as she dashes (the video's see-through violet copies of her). */
    static final int VIOLET=0x7a5cff,GHOST=0x6a46e6;
}
