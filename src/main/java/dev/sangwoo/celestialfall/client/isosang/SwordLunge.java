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
 * 옆 (영상처럼)"); 2.96.3 set the game beside the video again (user request: "3참격이 너무 크고 이펙트가 여전히 조금 이상해
 * 돌진도 그렇고", the differences read on a sheet and confirmed: "맞음, 이대로", the slashes' size: "약 5블록·굵기 0.25", and
 * "저거 3참격 랜덤으로 나오게"):
 * <ul>
 * <li>as she coils on her sword the six swords of H's basic attack fan out behind her (SwordVolley, on T's clock) and a
 * thin black line of ink is drawn from behind her to the foe, the way she will go;</li>
 * <li>she sets off trailing a few see-through violet copies of herself (BodyGhost's silhouettes, IsosangSlash.afterimages);
 * a twentieth of a second later a long drop of light is laid from behind where she set out to the foe in a blink and
 * hides her (IsosangRules.lungeHidden): far wider than she is, in hard-edged lanes of white, sky, blue and navy, lilac at
 * its tail, a dark wedge of ink down one side, pointed at the foe as it is laid and opening round it, its rim a bright
 * blue line; it cools through cyan to a see-through blue and is gone, its rim turning to a broad band of ink with a cyan
 * edge inside, which thins as it is eaten and is gone by some 0.5 s (the ink pass's styles 8 and 6, {@link #ink});</li>
 * <li>she is seen again down low just in front of the foe at its side as its light goes (IsosangRules.lungeLanding),
 * then the orb of Z's flight on to a sword on the other side of the formation from hers (SwordStep, the formation's
 * flights) while the swords come down on the foe;</li>
 * <li>three slashes are drawn one after another just in front of the foe as she lands there: short thin blades pointed at
 * both ends (the ink pass's style 7: a white heart, royal blue, an edge of black ink), each at an angle of its own rolled
 * anew for every T, their lower ends leaning toward where she stands now (her camera is with her: 2.96.1,
 * "카메라는 플레어한테 고정"); bright a moment, then darkening into thin lines of ink and gone.</li>
 * </ul>
 * Every effect is a function of the swing's clock and of where the foe is drawn (the swords, the drop's head and the
 * slashes follow it).
 */
final class SwordLunge {
    private SwordLunge(){}
    /** How long T is drawn for (seconds, IsosangClient.life): until its last slash has thinned into ink and gone. */
    static final float LIFE=LUNGE_LENGTH/20f+.25f;

    // ───────────────────────────── the body ─────────────────────────────
    // (the video: on the pommel she coils, the sword drawn back low at the right, and goes, a few violet copies of her
    // as she sets off, then hidden in the light of her dash (IsosangRules.lungeHidden); seen again low in a crouch just in
    // front of the foe at its side, the sword swept through across the front to the left; out of sight as she flies on;
    // down on the far pommel, her feet together, and up as she stands there. The body faces the way she dashed until she
    // lands on the sword she flies on to, then turns back to the formation's way: IsosangClient.lungeFacing.)
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
     * [AIM0, AIM1], gone under her dash's light.
     */
    static final double AIM_BACK=3.2,AIM_WIDTH=.075;
    static final float AIM0=.04f,AIM1=.11f,AIM_GONE0=.22f,AIM_GONE1=.3f;

    // ───────────────────────────── the drop of light: her dash ─────────────────────────────

    /** When the light of her dash comes (seconds: the video's 0.2 s, a twentieth of a second after she sets off; 2.96.3, IsosangRules.LUNGE_LIGHT). */
    static float light(){return (LUNGE_GO+LUNGE_LIGHT)/20f;}
    /**
     * The drop of light her dash leaves (the video: laid from behind where she set out to the foe in a blink, far wider
     * than she is, pointed at the foe as it is laid and opening round it): its axis from {@link #TAIL} blocks behind her
     * chest on her sword ({@link #TAIL_DROP} lower) to the foe, {@link #HALF} blocks either side of it (its tail
     * narrowing to a point), its end round the foe; its head racing to the foe over [light-.01, light+{@link #RACE}] (on
     * the foe by the video's 0.24 s), its end opening round the foe over [light+.035, light+{@link #OPEN}]. Its rim a band
     * {@link #RIM} wide round it (2.96.3: broad, as the video's band of ink is once it cools).
     */
    static final double TAIL=2.2,TAIL_DROP=.45,HALF=1.6,RIM=.7;
    static final float RACE=.045f,OPEN=.1f;
    /**
     * When it cools (seconds after its light comes; the video: white-hot to 0.27 s, blue by 0.3 s, its rim going black
     * from 0.3 s and a broad band of ink by 0.37 s, thin by 0.44 s, all but gone by 0.5 s): its light through cyan to a
     * see-through blue over [FILL_INK0, FILL_INK1], eaten from its tail over [FILL_EAT0, FILL_EAT1]; its rim to ink over
     * [RIM_INK0, RIM_INK1] (the side of it on her left a little sooner, the video's), thinning as it is eaten from both
     * ends in toward the foe over [RIM_EAT0, RIM_EAT1].
     */
    static final float FILL_INK0=.06f,FILL_INK1=.15f,FILL_EAT0=.1f,FILL_EAT1=.2f,RIM_INK0=.09f,RIM_INK1=.17f,RIM_LEAD=.03f,RIM_EAT0=.18f,RIM_EAT1=.32f;
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
        float l=light();
        Vec3d chest=c.origin().add(0,CHEST,0),wl=way(c,foe);
        Vec3d tail=chest.subtract(wl.multiply(TAIL)).add(0,-TAIL_DROP,0),axis=foe.subtract(tail);
        double full=Math.max(.5,axis.length());
        return new Drop(tail,axis.multiply(1/full),new Vec3d(-wl.z,0,wl.x),full*Ease.out(t,l-.01f,l+RACE),Ease.smooth(t,l+.035f,l+OPEN));
    }
    /** Its light (the ink pass's style 8): across it from one edge to the other, along it from its tail to its end. */
    static void fill(Drop d,float erase,float ink,float alpha){
        double[] s=d.samples();int n=s.length;double tip=d.tip();
        Vec3d[] in=new Vec3d[n],out=new Vec3d[n];float[] along=new float[n];
        for(int i=0;i<n;i++){double w=d.width(s[i]);in[i]=d.at(s[i],-w);out[i]=d.at(s[i],w);along[i]=(float)(s[i]/tip);}
        KagenoFire.inkRibbon(in,out,along,n,8,5,erase,ink,alpha);
    }
    /**
     * The streaks of light laid with it (the video: white, sky and blue streaks at once from behind her to the foe, each
     * hard-edged, pointed at the foe, spread apart behind her): each from its own place round the drop's tail (to its side,
     * up, how wide) to its head, facing the camera along it; the ink pass's style 8 (its streaks: seeds 8 to 10, one stripe
     * each, white edged with cyan, cyan edged with blue, blue edged with navy).
     */
    static final double[][] LAYERS={{-1.0,.9,.5},{1.1,.55,.42},{.3,-.35,.36}};
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
     * on her right, inside its edge; {@code t} seconds in, its light having come at {@code l} (the left side goes to ink a
     * little sooner).
     */
    static void rim(Drop d,float t,float l,float erase,float alpha){
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
            float lead=along[i]<.5f?RIM_LEAD:0;ink[i]=Ease.smooth(t,l+RIM_INK0-lead,l+RIM_INK1-lead);
        }
        KagenoFire.inkRibbon(in,out,along,m,6,9,erase,ink,alpha);
    }

    // ───────────────────────────── the three slashes ─────────────────────────────

    /**
     * The three slashes (2.96.3, user request: "3참격이 너무 크고", their size the user's choice: "약 5블록·굵기 0.25", then
     * on seeing it "크기 조금만 더 키워도 될듯": a third bigger; and "저거 3참격 랜덤으로 나오게": the video's two uses cut
     * them each its own way; 2.96.6, user request: "3참격 더 커도 될듯": a third bigger again, 8.5 blocks and 0.42; "her"
     * below is the side her camera stays on, where she began the first T of her row, toward): each a thin blade {@link #LENGTH} blocks
     * long and {@link #WIDTH} wide passing just in front of the foe on her side ({@link #FRONT0} to {@link #FRONT1}
     * blocks toward her, up to {@link #SIDE} to its side, {@link #HIGH0} to {@link #HIGH1} over its feet), reaching on
     * further one way than the other (by up to {@link #SHIFT} blocks, so the three cross one another here and there and
     * never meet in one star), at an angle of its own off level in the plane facing her (no steeper than {@link #STEEP}
     * degrees, the three at least {@link #APART} apart), its lower end leaning {@link #LEAN0} to {@link #LEAN1} degrees
     * toward her (the video's blades reaching down toward its camera); never under the ground ({@link #GROUND} over it,
     * raised to {@link #HIGHEST} at most, else its lower part shorter) nor nearer her than {@link #CLEAR} (pushed back
     * past the foe when she is that near it). Rolled anew for every T from its swing's id (the server's, so the same on
     * every client). Their size: the user's "약 5블록·굵기 0.25", then "a little bigger" (6.5 and 0.32), then (2.96.6,
     * "3참격 더 커도 될듯") 8.5 and 0.42, then (2.100.1, "3참격을 조금만 더 키우고") a fifth bigger again: 10.2 and 0.5.
     */
    static final double LENGTH=10.2,WIDTH=.5;
    static final double FRONT0=.5,FRONT1=1.1,SIDE=.7,HIGH0=.4,HIGH1=1.6,SHIFT=1.1;
    static final double STEEP=50,APART=28,LEAN0=12,LEAN1=32;
    static final double GROUND=.15,HIGHEST=1.7,CLEAR=2.7;
    /** How fast each is drawn on (from its far tip toward her), how long it stays bright, how it cools to ink and is eaten (seconds from its cut). */
    static final float DRAW=.045f,LIT=.12f,DIM=.3f,INK0=.15f,INK1=.34f,EAT0=.3f,EAT1=.46f;
    /** A slash's line: its far tip beyond its crossing and its near tip toward her, and how wide it is. */
    record Blade(Vec3d far,Vec3d near,double width){
        Vec3d point(double u){return far.add(near.subtract(far).multiply(u));}
        Vec3d way(){return near.subtract(far).normalize();}
        /** Where along it it comes nearest {@code p}. */
        Vec3d nearest(Vec3d p){
            Vec3d d=near.subtract(far);double l2=d.lengthSquared();
            return point(l2<1e-12?0:Math.max(0,Math.min(1,p.subtract(far).dotProduct(d)/l2)));
        }
    }
    /** A number in [0, 1) for roll {@code k} of T {@code id}: the same on every client (the swing's id is the server's). */
    static double roll(UUID id,int k){
        long z=id.getMostSignificantBits()*0x9E3779B97F4A7C15L+id.getLeastSignificantBits()+(k+1)*0xBF58476D1CE4E5B9L;
        z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;z^=z>>>31;
        return (z>>>11)*0x1.0p-53;
    }
    /** The angles off level (degrees, in the plane facing her) of T {@code id}'s slashes: each its own, none steeper than STEEP, any two APART at least. */
    static double[] angles(UUID id){
        double[] a=new double[LUNGE_SLASHES];
        for(int j=0,k=0;j<a.length;k++){
            // (a few rolls at most: three such angles fit with room to spare; past 64 an even spread keeps it bounded)
            if(k>=64){for(int i=0;i<a.length;i++)a[i]=-STEEP*.8+1.6*STEEP*i/Math.max(1,a.length-1);break;}
            double v=-STEEP+2*STEEP*roll(id,k);boolean ok=true;
            for(int i=0;i<j;i++)ok&=Math.abs(v-a[i])>=APART;
            if(ok)a[j++]=v;
        }
        return a;
    }
    /**
     * Toward the side her camera stays on through her T (level, its length how far; 2.96.6): the sword she began the first
     * T of her row from (IsosangClient.chainFrom, the same on every client), else where this one began. Her camera stands
     * still there through them (2.96.5, IsosangClient.cameraHold) as the video's did, and the video's blades reach down
     * toward it; 2.96.2 to 2.96.4 reached toward the sword she flies on to, where her camera went with her then, which
     * since 2.96.5 put them on the far side of the foe from it.
     */
    static Vec3d toward(StandClient.Cast c,Vec3d foe){
        Vec3d began=IsosangClient.chainFrom(c.id());
        if(began!=null){Vec3d d=began.subtract(foe).multiply(1,0,1);if(d.lengthSquared()>1)return d;}
        Vec3d d=c.origin().subtract(foe).multiply(1,0,1);
        return d.lengthSquared()>1?d:Vec3d.fromPolar(0,c.yaw()).multiply(-6);
    }
    /** Slash {@code j} of T {@code id} on the foe at {@code foe}, {@code toward} her (toward). */
    static Blade blade(UUID id,Vec3d foe,Vec3d toward,int j){
        double far=Math.max(1e-6,toward.length());Vec3d fw=toward.multiply(1/far),rt=new Vec3d(-fw.z,0,fw.x);
        Vec3d feet=foe.subtract(0,FOE_MID,0);
        int r=8+j*8;double a=Math.toRadians(angles(id)[j]),lean=Math.toRadians(LEAN0+(LEAN1-LEAN0)*roll(id,r));
        double front=FRONT0+(FRONT1-FRONT0)*roll(id,r+1),side=SIDE*(2*roll(id,r+2)-1),high=HIGH0+(HIGH1-HIGH0)*roll(id,r+3);
        // its lower end toward her
        double sign=a>0?-1:1;
        Vec3d across=rt.multiply(Math.cos(a)).add(0,Math.sin(a),0);
        Vec3d dir=across.multiply(sign*Math.cos(lean)).add(fw.multiply(Math.sin(lean)));
        // reaching on further one way than the other; never under the ground: as high as its lower part needs (to HIGHEST
        // at most), else that part shorter
        double lower=LENGTH*.5+SHIFT*(2*roll(id,r+4)-1),down=Math.max(0,-dir.y);
        high=Math.min(HIGHEST,Math.max(high,GROUND+down*lower));
        if(down>1e-6)lower=Math.min(lower,(high-GROUND)/down);
        Vec3d at=feet.add(fw.multiply(front)).add(rt.multiply(side)).add(0,high,0);
        Blade b=new Blade(at.subtract(dir.multiply(LENGTH-lower)),at.add(dir.multiply(lower)),WIDTH);
        // never too near her: pushed back toward the foe (past it, when she is that near it)
        Vec3d her=feet.add(toward);
        for(int i=0;i<4;i++){
            double gap=level(b,her);if(gap>=CLEAR)break;
            Vec3d back=fw.multiply(gap-CLEAR-.05);b=new Blade(b.far().add(back),b.near().add(back),WIDTH);
        }
        return b;
    }
    /** How near slash {@code b} comes to {@code p} level (blocks). */
    static double level(Blade b,Vec3d p){
        double ax=b.far().x,az=b.far().z,dx=b.near().x-ax,dz=b.near().z-az,l2=dx*dx+dz*dz;
        double u=l2<1e-12?0:Math.max(0,Math.min(1,((p.x-ax)*dx+(p.z-az)*dz)/l2));
        return Math.hypot(ax+dx*u-p.x,az+dz*u-p.z);
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

    /**
     * How big the burst as she cuts the foe is, and each of the swords' bursts (IsosangEmpower.burst's size; 2.96.2: .75,
     * .8). Each slash's own is LungeSlashes.burst (2.100.1: .55, the last .85; .35 before).
     */
    static final double PASS_BURST=.75,SWORD_BURST=.8;

    // ───────────────────────────── the passes ─────────────────────────────

    /** The ink pass: the drop of light (its light and streaks, then its rim) and the three slashes. */
    static void ink(Fx c){
        float t=c.sec,k=kept(c.cast.id(),t);if(k<=.01f)return;
        Vec3d foe=SwordVolley.target(c.cast,c.delta);if(foe==null)return;
        float l=light();
        if(t>=l-.01f&&t<l+RIM_EAT1){
            Drop d=drop(c.cast,foe,t);
            if(t<l+FILL_EAT1){
                float erase=Ease.smooth(t,l+FILL_EAT0,l+FILL_EAT1),ink=Ease.smooth(t,l+FILL_INK0,l+FILL_INK1),a=k*Ease.smooth(t,l-.01f,l+.01f);
                fill(d,erase,ink,a);layers(d,c.camera,erase,Math.min(1,ink*1.25f),a);
            }
            rim(d,t,l,Ease.smooth(t,l+RIM_EAT0,l+RIM_EAT1),k*Ease.smooth(t,l-.01f,l+.02f));
        }
        Vec3d to=toward(c.cast,foe);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+EAT1||!cuts(c.cast.id(),j))continue;
            band(blade(c.cast.id(),foe,to,j),c.camera,drawn(j,t),3+j*4,Ease.smooth(t,at+EAT0,at+EAT1),Ease.smooth(t,at+INK0,at+INK1),k);
        }
    }
    /** The blended pass: the swords' bodies, the line of ink to the foe, the ink under each burst. */
    static void alpha(Fx c,EffectMesh m){
        SwordVolley.alpha(c,m);
        // (2.100.0) its moonlight and starlight: the crystal petals, the flower at the sword she leaves
        LungeLights.alpha(c,m);
        float t=c.sec,k=kept(c.cast.id(),t);Vec3d foe=SwordVolley.target(c.cast,c.delta);if(foe==null||k<=.01f)return;
        // the line of ink from behind her to the foe, the way she will go
        if(t>=AIM0&&t<AIM_GONE1){
            Vec3d wl=way(c.cast,foe),from=c.cast.origin().add(0,CHEST,0).subtract(wl.multiply(AIM_BACK)).add(0,-.5,0);
            Vec3d to=from.lerp(foe,Ease.out(t,AIM0,AIM1));
            m.line(from,to,AIM_WIDTH,INK,.9f*k*(1-Ease.smooth(t,AIM_GONE0,AIM_GONE1)));
        }
        Vec3d to=toward(c.cast,foe);
        for(int j=0;j<LUNGE_SLASHES;j++)if(cuts(c.cast.id(),j))IsosangEmpower.burstInk(c,m,blade(c.cast.id(),foe,to,j).nearest(foe),lungeSlash(j),LungeSlashes.burst(j),j*17+5);
        if(passes(c.cast.id()))IsosangEmpower.burstInk(c,m,foe,blowAt(LUNGE,0)/20f,PASS_BURST,3);
    }
    /**
     * The light under the ink: the swords' (SwordVolley), a violet shimmer round her as she coils, the burst on the foe as
     * she cuts it, and round each slash (2.100.1, LungeSlashes) its sword's aura while it is bright, a ring on the ground
     * under the foe and its burst (2.96.3 had only a faint blue glow along it).
     */
    static void additive(Fx c,EffectMesh m){
        SwordVolley.additive(c,m);
        // (2.100.0) its moonlight and starlight
        LungeLights.additive(c,m);
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
            float at=lungeSlash(j);if(t<at-.01f||t>=at+Math.max(DIM,LungeSlashes.RING_LIFE)||!cuts(c.cast.id(),j))continue;
            Blade b=blade(c.cast.id(),foe,to,j);
            // (2.100.1) its sword's aura, its lightning, the ring on the ground under the foe, a bigger burst
            LungeSlashes.aura(m,b,j,t,k);
            LungeSlashes.ground(m,foe,j,t,k);
            IsosangEmpower.burst(c,m,b.nearest(foe),b.way(),at,LungeSlashes.burst(j),j*17+5);
        }
    }
    /** Over the ink: the swords' starlight (SwordVolley), the head of the light racing to the foe, and each slash's cutting edge as it is drawn on, the flare of its heart and its lightning (LungeSlashes). */
    static void overlay(Fx c,EffectMesh m){
        SwordVolley.overlay(c,m);
        // (2.100.0) the stars on its dash's light and on its slashes
        LungeLights.overlay(c,m);
        float t=c.sec,k=kept(c.cast.id(),t);Vec3d foe=SwordVolley.target(c.cast,c.delta);if(foe==null||k<=.01f)return;
        float l=light();
        if(t>=l-.01f&&t<l+RACE+.05f){
            Vec3d head=drop(c.cast,foe,t).head();float a=k*Ease.smooth(t,l-.01f,l+.005f)*(1-Ease.smooth(t,l+RACE,l+RACE+.05f));
            m.softDisc(head,.8,AZURE,.25f*a);m.softDisc(head,.3,WHITE,.6f*a);glint8(m,head,.45,WHITE,.6f*a);
        }
        Vec3d to=toward(c.cast,foe);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+.3f||!cuts(c.cast.id(),j))continue;
            // (2.100.1) the cutting edge at its head as it is drawn on, the flare of white heat down its heart as it is cut,
            // its lightning
            Blade b=blade(c.cast.id(),foe,to,j);
            LungeSlashes.edge(c,m,b,j,t,k);
            LungeSlashes.lightning(c,m,b,j,t,k);
        }
    }
    /** Lights: the swords' (SwordVolley), the head of the light racing to the foe, the foe as she cuts it and as each slash cuts; the afterglow cut short throughout. */
    static void frame(Fx c,FxFrame f){
        SwordVolley.frame(c,f);
        LungeLights.frame(c,f);
        float t=c.sec,k=kept(c.cast.id(),t);if(k<=.01f)return;
        float l=light(),land=LUNGE_LAND/20f;
        Vec3d foe=SwordVolley.target(c.cast,c.delta);
        if(foe!=null){
            if(t>=l-.01f&&t<l+FILL_INK1){
                float a=k*Ease.smooth(t,l-.01f,l+.01f)*(1-Ease.smooth(t,l+FILL_INK0,l+FILL_INK1));
                Drop d=drop(c.cast,foe,t);f.light(d.head(),0xd6e6ff,8,.55f*a);f.light(d.at(d.length()*.45,0),0xb8c8ff,6,.3f*a);
            }
            float kp=passes(c.cast.id())?FxLib.kick(t,blowAt(LUNGE,0)/20f,.15f):0;if(kp>.01f)f.light(foe,0xcfe4ff,9,.8f*kp);
            for(int j=0;j<LUNGE_SLASHES;j++){float ks=cuts(c.cast.id(),j)?FxLib.kick(t,lungeSlash(j),.2f):0;if(ks>.01f)f.light(foe,0x9fc8ff,LungeSlashes.lightReach(j),LungeSlashes.lightBright(j)*ks);}
        }
        // (2.100.1) the last slash's impact frame
        LungeSlashes.frame(c,f,foe);
        // quick throughout (the dash, the flight on, the slashes): short afterglow, else each leaves copies of itself
        if(t<LUNGE_LENGTH/20f)f.afterglow(t<land+.1f?.02f:.05f);
    }
    /** A jolt as she cuts the foe (harder) and as each slash cuts, each over two frames; and the swords' (SwordVolley). */
    static float shake(StandClient.Cast cast,float sec){
        float v=SwordVolley.shake(cast,sec);
        if(passes(cast.id()))v=Math.max(v,.55f*bump(sec,blowAt(LUNGE,0)/20f,.08f));
        for(int j=0;j<LUNGE_SLASHES;j++)if(cuts(cast.id(),j))v=Math.max(v,LungeSlashes.jolt(j)*bump(sec,lungeSlash(j),.06f));
        return v;
    }
    /**
     * How strongly her body leaves afterimages {@code t} seconds in (BodyGhost: 1 their full strength): in full as she sets
     * off (the video's few violet copies of her as she goes) until her dash's light hides her (IsosangRules.lungeHidden),
     * as faint as the sword's hits' otherwise.
     */
    static float afterimages(float t){
        float go=LUNGE_GO/20f,l=light();
        return .25f+.75f*Ease.smooth(t,go-.03f,go)*(1-Ease.smooth(t,l+.02f,l+.06f));
    }
    /** A violet for the shimmer as she coils, and a deeper one for her afterimages as she dashes (the video's see-through violet copies of her). */
    static final int VIOLET=0x7a5cff,GHOST=0x6a46e6;
}
