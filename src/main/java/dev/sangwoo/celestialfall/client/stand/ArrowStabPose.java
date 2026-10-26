package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.meteor.MiningPose;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.stand.StandArrowRules;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The stand arrow driven into one's own chest (2.70.0, StandArrowRules for the user's words), as a smooth function of
 * the stab's clock (seconds), on the articulated rig: the right hand lifts the arrow high beside the head, tip to the
 * sky, and holds it there a moment, trembling; it turns the arrow over, tip forward and then down at the chest, as the
 * left hand comes up to it; both fists draw it up a little (the load), then drive it down into the chest, faster and
 * faster, stopping dead as it goes in (HIT). The body folds over it, then arches back gasping as the hands are thrown
 * off it; the arrow, standing out of the chest, glows and is drawn into the body while they stand hunched over it,
 * shaking, the heart beating faster and faster (BEATS). As it is gone its choice shows (REVEAL): one it chose arches
 * back with the arms flung wide as the stand bursts out of them, then stands and turns to it; one it rejects
 * convulses, clutches the chest, drops to the knees and falls on their face (the server's death comes as they land).
 * <p>
 * Keyed channels on monotone cubic curves (MiningPose.Track), the plunge's key struck (it speeds up all the way into
 * it); the body's joints come from where the fists go (a two-bone solve, MiningPose.reach) and the legs from where the
 * feet stay (or, falling, from the knees on the ground). The arrow rides in the fists until it goes in, then stays
 * where it went in (it moves with the chest), and is drawn into it. Torso space throughout: model pixels from the
 * neck, +x the body's left, +y down, -z ahead (the shoulders at (∓5, 2, 0), the chest's front at z = -2).
 * <p>
 * Whatever the arrow chose follows from the pose it holds as the choice shows: a choice heard late (the server's word
 * is on its way) starts from that pose then, so nothing jumps.
 */
public final class ArrowStabPose {
    private ArrowStabPose(){}
    private static final float RAD=(float)Math.PI/180;
    /** Seconds on the stab's clock: the arrow goes in; its choice shows; one it chose is free; one it rejects dies. */
    public static final float HIT=StandArrowRules.HIT/20f,REVEAL=StandArrowRules.REVEAL/20f,FREE=StandArrowRules.FREE/20f,DEATH=StandArrowRules.DEATH_AT/20f;
    /** What the arrow did, as far as this client has heard. */
    public enum Fate{UNKNOWN,AWAKEN,DIE}

    // ---------------------------------------------------------------- the arrow

    /**
     * The arrow's sprite (item pixels; tools: its 32 px texture): its axis is the line y = x, along which the tip is
     * TIP_U from the corner, the golden head begins at HEAD_U, the hands close at GRIP_U (the bare shaft just under the
     * head) and the nock is at NOCK_U; the sprite's middle is at z = 8. Drawn SCALE model pixels to an item pixel.
     */
    public static final float TIP_U=21.57f,HEAD_U=12f,GRIP_U=10.2f,NOCK_U=1.77f,SCALE=.5f;
    private static final float R2=(float)Math.sqrt(.5);
    static final Vector3f ALONG=new Vector3f(R2,R2,0),ACROSS=new Vector3f(-R2,R2,0),FACE=new Vector3f(0,0,1);
    /** From the grip to the tip (model px). */
    static final float GRIP_TO_TIP=(TIP_U-GRIP_U)*SCALE;
    /** Where the tip goes into the chest (torso px: its front, the breastbone), and how far in it goes (px along the arrow). */
    static final Vector3f ENTRY=new Vector3f(0,4.3f,-2f);
    static final float DEPTH=2.4f;
    /** The arrow is drawn into the body over these seconds (gone before its choice shows, as the server spends it). */
    static final float SINK_FROM=1.78f,SINK_TO=2.16f;
    /** As it is drawn in it slides this far further in (px) while it shrinks to nothing where it went in. */
    static final float SINK_SLIDE=1.6f;
    /** The heart beating, faster and faster, from the arrow's going in until its choice shows (seconds). */
    public static final float[] BEATS={1.40f,1.68f,1.90f,2.06f,2.16f};

    // ---------------------------------------------------------------- the keys

    /** One key's channels (see {@link #row}). */
    private static final class K {
        float pitch,yaw,roll,bob,shift,lean,hp,hy,hr,join,liftR,liftL;
        float[] r={-5.6f,10.3f,-1},l={5.6f,10.3f,-.6f},a={0,.25f,-1},f={1,0,0},pr={-.6f,1,.4f},pl={.6f,1,.4f};
        K body(float p,float y,float r){pitch=p;yaw=y;roll=r;return this;}
        K hips(float b,float s,float l){bob=b;shift=s;lean=l;return this;}
        K head(float p,float y,float r){hp=p;hy=y;hr=r;return this;}
        K right(float x,float y,float z){r=new float[]{x,y,z};return this;}
        K left(float x,float y,float z){l=new float[]{x,y,z};return this;}
        K arrow(float x,float y,float z){a=new float[]{x,y,z};return this;}
        K face(float x,float y,float z){f=new float[]{x,y,z};return this;}
        K join(float j){join=j;return this;}
        K lift(float r,float l){liftR=r;liftL=l;return this;}
        K poles(float rx,float ry,float rz,float lx,float ly,float lz){pr=new float[]{rx,ry,rz};pl=new float[]{lx,ly,lz};return this;}
        K copy(){var k=new K();k.pitch=pitch;k.yaw=yaw;k.roll=roll;k.bob=bob;k.shift=shift;k.lean=lean;k.hp=hp;k.hy=hy;k.hr=hr;k.join=join;k.liftR=liftR;k.liftL=liftL;
            k.r=r.clone();k.l=l.clone();k.a=a.clone();k.f=f.clone();k.pr=pr.clone();k.pl=pl.clone();return k;}
        /** 0-5 the torso's pitch, yaw, roll and the hips' bob, shift, lean; 6-8 the head's (model space); 9-14 the fists; 15-20 the arrow's way and face; 21 join; 22-23 the shoulders' lift; 24-29 the elbows' poles. */
        float[] row(){return new float[]{pitch,yaw,roll,bob,shift,lean,hp,hy,hr,r[0],r[1],r[2],l[0],l[1],l[2],a[0],a[1],a[2],f[0],f[1],f[2],join,liftR,liftL,pr[0],pr[1],pr[2],pl[0],pl[1],pl[2]};}
    }
    /** The arrow's way and the fists as it goes in: from the grip, ENTRY + DEPTH along WAY_IN is the tip. */
    static final Vector3f WAY_IN=new Vector3f(0,.62f,.78f).normalize();
    static final Vector3f GRIP_IN=new Vector3f(WAY_IN).mul(DEPTH-GRIP_TO_TIP).add(ENTRY);
    /** The fists close on either side of the shaft once both hold it (px between their middles). */
    static final float CLASP=3.6f;

    /** Standing, the arrow in the right hand pointing ahead. */
    private static final K REST=new K();
    /** On the way up in front of the right shoulder, the arrow turned point down (its nock up in front of the face, never in it). */
    private static final K RISE=new K().body(-3,-2,1).hips(-.1f,0,-.1f).head(4,6,0).right(-5f,2f,-5.4f).arrow(.05f,1,-.05f).face(1,0,0)
        .left(4.6f,7.6f,-3f).lift(.5f,0).poles(-1,.7f,.4f,.8f,1,.3f);
    /**
     * Lifted high in front of the right shoulder, the tip already turned down at the chest (held so, it never passes
     * the face: the arrow's line runs in front of the chin); the left hand at the chest, bracing; leaning back, the
     * eyes on it.
     */
    private static final K RAISED=new K().body(-8,-4,3).hips(-.3f,0,-.3f).head(-10,10,0).right(-3.8f,-3.6f,-6.4f).arrow(.41f,.81f,.42f).face(1,0,-.5f)
        .left(3.8f,5f,-4.6f).lift(1.1f,0).poles(-1,.5f,.4f,1,.6f,.2f);
    /** Held there a moment, the hesitation (it trembles: {@link #shake}), the eyes going down to where it will go in. */
    private static final K HELD=RAISED.copy().body(-9,-3,2).head(8,6,0).right(-3.6f,-4.2f,-6.2f).arrow(.38f,.84f,.39f);
    /** The left hand coming up to it. */
    private static final K JOIN=new K().body(-5,-1,1).hips(-.25f,0,-.25f).head(14,2,0).right(-2.4f,-2.4f,-6.2f).left(1.4f,-2.2f,-6.2f)
        .arrow(.18f,.86f,.45f).face(1,0,-.2f).join(.7f).lift(.8f,.5f).poles(-.9f,.8f,.2f,.9f,1,.1f);
    /** Both fists in front of the face, the arrow tip down at the chest. */
    private static final Vector3f AIM_WAY=new Vector3f(0,.88f,.47f).normalize();
    private static final K AIM=new K().body(-3,0,0).hips(-.2f,0,-.2f).head(22,0,0).right(-CLASP/2,-1.6f,-6f).left(CLASP/2,-1.6f,-6f)
        .arrow(AIM_WAY.x,AIM_WAY.y,AIM_WAY.z).face(1,0,0).join(1).lift(.6f,.6f).poles(-.9f,1,.1f,.9f,1,.1f);
    /** Drawn up a little, the body leaning back: the load before the blow. */
    private static final K LOAD=AIM.copy().body(-8,0,0).hips(-.4f,0,-.6f).head(16,0,0).right(-CLASP/2,-3f,-6.3f).left(CLASP/2,-3f,-6.3f).arrow(0,.93f,.37f).lift(1,1);
    /** Driven in (struck: it speeds up all the way into this key and stops dead). */
    private static final K IN=AIM.copy().body(8,0,0).hips(1,0,.4f).head(38,0,0).right(GRIP_IN.x-CLASP/2,GRIP_IN.y,GRIP_IN.z).left(GRIP_IN.x+CLASP/2,GRIP_IN.y,GRIP_IN.z)
        .arrow(WAY_IN.x,WAY_IN.y,WAY_IN.z).lift(-.3f,-.3f).poles(-.9f,.8f,.5f,.9f,.8f,.5f);
    /** Folded over it, the hands still on it. */
    private static final K CRUNCH=IN.copy().body(24,0,0).hips(2,0,1).head(52,0,0).lift(.5f,.5f);
    /** Arched back, gasping, the hands thrown off it. */
    private static final K GASP=IN.copy().body(-16,0,0).hips(.4f,0,-.6f).head(-30,0,0).right(-6.5f,4f,-4.8f).left(6.5f,4f,-4.8f).lift(.8f,.8f).poles(-1,.6f,.3f,1,.6f,.3f);
    /** Hunched over it as it is drawn in, the hands hovering by the chest. */
    private static final K ABSORB=GASP.copy().body(7,0,0).hips(1,0,.3f).head(34,0,0).right(-5.2f,6.4f,-4.2f).left(5.2f,6.4f,-4.2f).lift(.2f,.2f).poles(-.8f,1,.3f,.8f,1,.3f);
    /** Still so as its choice shows (and as long as the word of it is on its way). */
    private static final K WAITING=ABSORB.copy().body(9,0,0).hips(1.2f,0,.3f).head(38,0,0).right(-5.4f,7f,-3.8f).left(5.4f,7f,-3.8f).lift(0,0);
    /** The common keys' times (seconds), and the struck one. */
    static final float[] TIMES={0,.16f,.34f,.62f,.76f,.86f,.96f,HIT,1.24f,1.48f,1.86f,REVEAL};
    private static final int STRUCK=7;
    private static final MiningPose.Track COMMON=new MiningPose.Track(TIMES,rows(REST,RISE,RAISED,HELD,JOIN,AIM,LOAD,IN,CRUNCH,GASP,ABSORB,WAITING),STRUCK);

    /** Chosen: arched back with the arms flung wide as the stand bursts out; held; then standing, turned to it (on their right). */
    private static final K BURST=WAITING.copy().body(-24,0,0).hips(-.6f,0,-1.2f).head(-48,0,0).right(-11f,5.5f,2.4f).left(11f,5.5f,2.4f).lift(-.5f,-.5f).poles(-.3f,1,1,.3f,1,1);
    private static final K GLORY=BURST.copy().body(-19,0,0).hips(-.4f,0,-.9f).head(-38,0,0).right(-10.5f,6.5f,2f).left(10.5f,6.5f,2f);
    private static final K TURNED=new K().body(0,8,0).head(-4,38,0).right(-5.6f,10.2f,-.8f).left(5.6f,10.2f,-.6f).arrow(WAY_IN.x,WAY_IN.y,WAY_IN.z).face(1,0,0);
    static final float[] AWAKEN_TIMES={0,.14f,.42f,.95f};
    private static final MiningPose.Track AWAKENED=new MiningPose.Track(AWAKEN_TIMES,rows(WAITING,BURST,GLORY,TURNED),1);

    /**
     * Rejected, on its own channels: 0 how far down on the knees, 1 how far fallen forward from them (degrees), 2 the
     * torso's hunch over the knees, 3-4 its yaw and roll, 5-7 the hips' bob, shift, lean while standing, 8-10 the head
     * (pitch on the torso's, yaw, roll), 11-16 the fists, 17-18 the shoulders' lift, 19-24 the elbows' poles.
     */
    private static float[] down(float kneel,float fall,float hunch,float roll,float bob,float headUp,float headRoll,float[] r,float[] l,float lift,float[] pr,float[] pl){
        return new float[]{kneel,fall,hunch,0,roll,bob,0,WAITING.lean,headUp,0,headRoll,r[0],r[1],r[2],l[0],l[1],l[2],lift,lift,pr[0],pr[1],pr[2],pl[0],pl[1],pl[2]};
    }
    static final float[] DIE_TIMES={0,.12f,.42f,.72f,1.05f,1.15f,1.32f};
    /** The one it rejects lands at this key (the fall speeds up all the way into it). */
    private static final int LANDS=4;
    /** When the one it rejects lands on their face (seconds after its choice shows). */
    public static final float LANDS_AT=DIE_TIMES[LANDS];
    private static final MiningPose.Track REJECTED=new MiningPose.Track(DIE_TIMES,new float[][]{
        down(0,0,WAITING.pitch,0,WAITING.bob,WAITING.hp-WAITING.pitch,0,WAITING.r,WAITING.l,0,WAITING.pr,WAITING.pl),
        // the convulsion: the hands clutch the chest, the knees give
        down(.2f,0,32,-3,2,22,0,new float[]{-1.6f,4.6f,-3.4f},new float[]{1.6f,4.6f,-3.4f},.6f,new float[]{-.9f,1,.3f},new float[]{.9f,1,.3f}),
        // on the knees, hunched over it
        down(1,0,24,2,2,26,0,new float[]{-1.9f,5.2f,-3.3f},new float[]{1.9f,5.2f,-3.3f},.4f,new float[]{-.9f,1,.3f},new float[]{.9f,1,.3f}),
        // swaying, the hands slipping off the chest
        down(1,0,36,-4,2,30,8,new float[]{-3f,8.8f,-3.4f},new float[]{3.4f,8.4f,-3.2f},0,new float[]{-.8f,1,.4f},new float[]{.8f,1,.4f}),
        // face down, the arms along the body, the head lifted a little, the chin on the ground
        down(1,90,36,0,2,-66,4,new float[]{-5.6f,10.6f,.3f},new float[]{5.6f,10.6f,.3f},0,new float[]{0,1,1},new float[]{0,1,1}),
        down(1,85,36,0,2,-58,5,new float[]{-5.6f,10.6f,.1f},new float[]{5.6f,10.6f,.1f},0,new float[]{0,1,1},new float[]{0,1,1}),
        down(1,90,36,0,2,-64,4,new float[]{-5.6f,10.6f,.3f},new float[]{5.6f,10.6f,.3f},0,new float[]{0,1,1},new float[]{0,1,1})},LANDS);

    private static float[][] rows(K... keys){var out=new float[keys.length][];for(int i=0;i<keys.length;i++)out[i]=keys[i].row();return out;}

    // ---------------------------------------------------------------- the frame

    /** The arrow this frame: its tip, way (toward the tip) and face (torso px), how far it has been drawn in (0 to 1), and whether it has gone in. */
    public record Arrow(Vector3f tip,Vector3f way,Vector3f face,float sink,boolean in){
        /** Where its nock is, and where its head begins (torso px), drawn in as it sinks. */
        public Vector3f nock(){return at(NOCK_U);}
        public Vector3f headBase(){return at(HEAD_U);}
        /** The point {@code u} item px along its axis (as {@link #matrix} draws it). */
        public Vector3f at(float u){return sunk(new Vector3f(way).mul((u-TIP_U)*SCALE).add(tip));}
        /** A point of the arrow where it is drawn this frame: shrunk toward where it went in, and slid further in, as it sinks. */
        Vector3f sunk(Vector3f p){
            if(sink<=0)return p;
            Vector3f entry=entry();
            return p.sub(entry).mul(1-sink).add(entry).add(new Vector3f(way).mul(SINK_SLIDE*sink));
        }
        /** Where it went in (torso px): where its axis crosses the chest's front. */
        public Vector3f entry(){
            if(Math.abs(way.z)<1e-4f)return new Vector3f(tip);
            float s=(ENTRY.z-tip.z)/way.z;return new Vector3f(way).mul(s).add(tip);
        }
        /**
         * Item blocks to torso blocks, for one of the two sprites crossed along its axis ({@code turn} 0 or 1: the second
         * turned a quarter about it, so it shows from every side).
         */
        public Matrix4f matrix(int turn){
            Vector3f a=new Vector3f(way),n=new Vector3f(face);
            if(turn==1)n=new Vector3f(a).cross(face).normalize();
            Vector3f b=new Vector3f(n).cross(a);
            Matrix3f to=new Matrix3f().setColumn(0,a).setColumn(1,b).setColumn(2,n);
            Matrix3f sprite=new Matrix3f().setColumn(0,ALONG).setColumn(1,ACROSS).setColumn(2,FACE);
            Matrix3f linear=to.mul(sprite.transpose()).scale(SCALE);
            Vector3f tipItem=new Vector3f(ALONG).mul(TIP_U).add(0,0,8);
            Vector3f origin=linear.transform(new Vector3f(tipItem)).negate().add(tip);
            Matrix4f m=new Matrix4f().set(linear).setTranslation(origin.div(16));
            if(sink>0){
                // shrunk toward where it went in and slid on in (in blocks)
                Vector3f e=entry().div(16),slide=new Vector3f(way).mul(SINK_SLIDE*sink/16);
                m=new Matrix4f().translation(e.x+slide.x,e.y+slide.y,e.z+slide.z).scale(1-sink).translate(-e.x,-e.y,-e.z).mul(m);
            }
            return m;
        }
    }
    /**
     * One frame: the body (the arms solved onto the fists), the shoulders' lift (px, drawn along the torso), where the
     * fists close (torso px), the arrow (null once it is gone) and how brightly it shines (0 to 1), and how far down
     * one it rejects lies (0 standing to 1 on their face).
     */
    public record Frame(MotionPose pose,float liftR,float liftL,Vector3f rightFist,Vector3f leftFist,float join,Arrow arrow,float glow,float fallen){}

    /**
     * The stab at {@code t} seconds on its clock; {@code fate} what the arrow chose (UNKNOWN until heard) and
     * {@code known} when this client heard it (seconds on the same clock; the choice shows from REVEAL, or from then if
     * later).
     */
    public static Frame frame(float t,Fate fate,float known){
        t=Math.max(0,t);
        float start=Math.max(REVEAL,known);
        float[] c=COMMON.at(Math.min(t,REVEAL));
        float fallen=0;
        MotionPose p=new MotionPose();
        float pitch,yaw,roll,bob,shift,lean,hp,hy,hr,liftR,liftL;
        Vector3f right,left,poleR,poleL;
        float kneel=0,fall=0;
        if(fate==Fate.DIE&&t>start){
            float[] d=REJECTED.at(t-start);
            kneel=clamp(d[0]);fall=d[1];
            pitch=fall+d[2]*(1-fall/90);yaw=d[3];roll=d[4];bob=d[5];shift=d[6];lean=d[7];
            hp=pitch+d[8];hy=d[9];hr=d[10];
            right=new Vector3f(d[11],d[12],d[13]);left=new Vector3f(d[14],d[15],d[16]);liftR=d[17];liftL=d[18];
            poleR=new Vector3f(d[19],d[20],d[21]);poleL=new Vector3f(d[22],d[23],d[24]);
            fallen=fall/90;
        }else{
            float[] k=fate==Fate.AWAKEN&&t>start?AWAKENED.at(Math.min(t-start,AWAKEN_TIMES[AWAKEN_TIMES.length-1])):c;
            pitch=k[0];yaw=k[1];roll=k[2];bob=k[3];shift=k[4];lean=k[5];hp=k[6];hy=k[7];hr=k[8];
            right=new Vector3f(k[9],k[10],k[11]);left=new Vector3f(k[12],k[13],k[14]);liftR=k[22];liftL=k[23];
            poleR=new Vector3f(k[24],k[25],k[26]);poleL=new Vector3f(k[27],k[28],k[29]);
        }
        // the trembling, the jolt as it goes in and the heart's beats, on top
        float[] s=shake(t,fate,start);
        pitch+=s[0];roll+=s[1];hp+=s[2];hr+=s[3];
        right.add(s[4],s[5],s[6]);left.add(s[7],s[8],s[9]);
        if(fate==Fate.DIE&&t>start)fall=Math.min(90,fall);
        p.rot(MotionPose.BODY,pitch,yaw,roll).rot(MotionPose.HEAD,hp,hy,hr);
        // the legs: the feet where they stand, or the knees on the ground and the body tipping forward over them
        legs(p,kneel,fall,bob,shift,lean);
        Vector3f rs=new Vector3f(-5,2-liftR,0),ls=new Vector3f(5,2-liftL,0);
        float[] ra=reach(rs,right,poleR,-1),la=reach(ls,left,poleL,1);
        p.rot(MotionPose.RIGHT_ARM,ra[0],ra[1],ra[2]).rot(MotionPose.LEFT_ARM,la[0],la[1],la[2]);
        p.elbow[0]=ra[3];p.elbow[1]=la[3];
        Vector3f rf=MiningPose.fist(rs,ra,-1),lf=MiningPose.fist(ls,la,1);
        return new Frame(p,liftR,liftL,rf,lf,t<HIT?clamp(c[21]):1,arrow(t,c,rf,lf),glow(t,fate,start),fallen);
    }
    /** The arrow at {@code t}: in the fists ({@code c} the common keys now) until it goes in, then where it went in, drawn in, gone. */
    static Arrow arrow(float t,float[] c,Vector3f rf,Vector3f lf){
        if(t>=SINK_TO)return null;
        if(t<HIT){
            Vector3f grip=new Vector3f(rf).lerp(new Vector3f(rf).add(lf).mul(.5f),clamp(c[21]));
            Vector3f way=new Vector3f(c[15],c[16],c[17]).normalize();
            Vector3f face=orth(new Vector3f(c[18],c[19],c[20]),way);
            return new Arrow(new Vector3f(way).mul(GRIP_TO_TIP).add(grip),way,face,0,false);
        }
        // where it went in: as the struck key left it (the hands there then), moving with the chest
        Arrow in=IN_ARROW;
        float sink=Ease.smooth(t,SINK_FROM,SINK_TO);
        return new Arrow(new Vector3f(in.tip()),new Vector3f(in.way()),new Vector3f(in.face()),sink,true);
    }
    /** The arrow as it goes in (the fists at the struck key: both on it). */
    private static final Arrow IN_ARROW;
    static{
        float[] c=COMMON.at(HIT);
        Vector3f rs=new Vector3f(-5,2-c[22],0),ls=new Vector3f(5,2-c[23],0);
        Vector3f rf=MiningPose.fist(rs,reach(rs,new Vector3f(c[9],c[10],c[11]),new Vector3f(c[24],c[25],c[26]),-1),-1);
        Vector3f lf=MiningPose.fist(ls,reach(ls,new Vector3f(c[12],c[13],c[14]),new Vector3f(c[27],c[28],c[29]),1),1);
        Vector3f grip=new Vector3f(rf).add(lf).mul(.5f);
        Vector3f way=new Vector3f(c[15],c[16],c[17]).normalize();
        IN_ARROW=new Arrow(new Vector3f(way).mul(GRIP_TO_TIP).add(grip),way,orth(new Vector3f(c[18],c[19],c[20]),way),0,true);
    }
    /** {@code v} made square to the unit {@code to} and unit (a square one if it runs along it). */
    private static Vector3f orth(Vector3f v,Vector3f to){
        Vector3f o=v.sub(new Vector3f(to).mul(v.dot(to)));
        if(o.lengthSquared()<1e-6f){o=new Vector3f(1,0,0).sub(new Vector3f(to).mul(to.x));if(o.lengthSquared()<1e-6f)o=new Vector3f(0,1,0).sub(new Vector3f(to).mul(to.y));}
        return o.normalize();
    }

    /**
     * What shakes the body on top of the keys (degrees and px): the arm trembling as it holds the arrow up (the
     * hesitation); the jolt through the body as the arrow goes in; the heart's beats as it is drawn in; the whole body
     * shaking harder and harder until its choice shows; and the convulsions of one it rejects, dying down as they
     * fall. Returns the torso's pitch and roll, the head's pitch and roll, and each fist's move.
     */
    static float[] shake(float t,Fate fate,float start){
        float[] s=new float[10];
        // the hesitation: the raised arm trembling
        float hold=Ease.smooth(t,.34f,.5f)*(1-Ease.smooth(t,.62f,.72f));
        float tremor=hold*.32f;
        s[4]+=tremor*wave(t,9.3f,0);s[5]+=tremor*wave(t,11.1f,1.3f);s[6]+=tremor*.6f*wave(t,8.2f,2.1f);
        // the jolt as it goes in
        float jolt=kick(t,HIT,.12f);
        s[0]+=7*jolt;s[2]+=9*jolt;
        // the heart's beats, and the shaking growing until its choice shows (and after, for one it rejects, until they fall)
        float beat=0;for(float b:BEATS)beat+=kick(t,b,.09f);
        s[0]+=2.4f*beat;s[2]+=1.6f*beat;s[5]+=.35f*beat;s[8]+=.35f*beat;
        float quake=Ease.smooth(t,1.25f,2.15f);
        if(t>start)quake*=fate==Fate.AWAKEN?1-Ease.smooth(t-start,0,.12f):fate==Fate.DIE?1-Ease.smooth(t-start,.6f,1f):1;
        float q=.9f*quake;
        s[1]+=q*1.2f*wave(t,13.7f,.4f);s[3]+=q*1.5f*wave(t,12.3f,2.2f);
        s[4]+=q*.4f*wave(t,14.9f,.9f);s[5]+=q*.4f*wave(t,16.3f,2.8f);s[6]+=q*.25f*wave(t,12.9f,1.1f);
        s[7]+=q*.4f*wave(t,15.7f,1.7f);s[8]+=q*.4f*wave(t,13.1f,.2f);s[9]+=q*.25f*wave(t,17.2f,2.4f);
        // the one it rejects: convulsing as its choice shows
        if(fate==Fate.DIE&&t>start){
            float u=t-start,fit=kick(u,.01f,.16f)+.6f*kick(u,.2f,.12f)+.4f*kick(u,.34f,.1f);
            s[0]+=6*fit;s[2]+=8*fit;s[5]+=.5f*fit;s[8]+=.5f*fit;
        }
        return s;
    }
    private static float clamp(float v){return v<0?0:v>1?1:v;}
    private static float wave(float t,float hz,float phase){return (float)(Math.sin(t*hz*Math.PI*2+phase)*.6+Math.sin(t*hz*1.73*Math.PI*2+phase*2.1)*.4);}
    /** A pulse coming in over a frame and a half at {@code at} and dying away with time constant {@code tau}. */
    static float kick(float t,float at,float tau){return t<at-.01f?0:Ease.smooth(t,at-.01f,at+.015f)*(float)Math.exp(-Math.max(0,t-at-.015f)/tau);}

    /**
     * How brightly the arrow shines (0 to 1): a glint as it is held up to the sky, out as it is turned, flaring as it
     * goes in, then with the heart's beats, brighter and brighter as it is drawn in; one it chose flares out with it as
     * the choice shows, for one it rejects it gutters out.
     */
    public static float glow(float t,Fate fate,float start){
        float raised=Ease.smooth(t,.22f,.4f)*(1-Ease.smooth(t,.62f,.76f))*.55f;
        float flare=kick(t,HIT,.2f);
        float beat=0;for(float b:BEATS)beat+=kick(t,b,.11f);
        float drawn=Ease.smooth(t,1.3f,2.16f);
        float g=Math.max(raised,Math.max(flare,.25f*drawn+.35f*beat*drawn+.55f*Ease.smooth(t,SINK_FROM,SINK_TO)));
        if(t>start)g*=1-Ease.smooth(t-start,0,fate==Fate.DIE?.35f:.2f);
        return clamp(g);
    }

    // ---------------------------------------------------------------- the arms

    /** A straight arm, from its shoulder: the elbow 4 px down, the fist 4.5 px further, 1 px off the arm's pivot (MiningPose's). */
    private static final float ELBOW=4,FIST=4.5f;
    /**
     * The arm's angles (pitch, yaw, roll as a model part takes them, degrees) and elbow bend (degrees) that close its
     * fist on {@code target} from {@code shoulder}, the elbow toward {@code pole}; {@code side} -1 for the right arm, 1
     * for the left. As MiningPose.reach, except that the arm's turn about itself is taken from the elbow's hinge, not
     * from where the bent elbow sits: so it holds as the arm straightens out (where the bent elbow's place runs out,
     * the twist swung round in a frame: the rejected one's arms, laid out straight along the body as they land).
     */
    static float[] reach(Vector3f shoulder,Vector3f target,Vector3f pole,float side){
        Vector3f d=new Vector3f(target).sub(shoulder);float dist=d.length();
        float c=(dist*dist-side*side-ELBOW*ELBOW-FIST*FIST)/(2*ELBOW*FIST);
        float bend=-(float)Math.acos(Math.max(-1,Math.min(1,c)));
        Vector3f fist=new Vector3f(side,ELBOW+FIST*(float)Math.cos(bend),FIST*(float)Math.sin(bend)).normalize();
        Vector3f aim=new Vector3f(d).normalize();
        // the side the elbow sits on, square to the fist's line: from the hinge (the elbow turns about the arm's x)
        Vector3f hinge=new Vector3f(1,0,0).sub(new Vector3f(fist).mul(fist.x)).normalize();
        Vector3f elbowLocal=new Vector3f(hinge).cross(fist).normalize();
        Vector3f elbowWanted=square(new Vector3f(pole),aim,new Vector3f(0,1,0));
        Matrix3f local=new Matrix3f().setColumn(0,fist).setColumn(1,elbowLocal).setColumn(2,new Vector3f(fist).cross(elbowLocal));
        Matrix3f wanted=new Matrix3f().setColumn(0,aim).setColumn(1,elbowWanted).setColumn(2,new Vector3f(aim).cross(elbowWanted));
        Quaternionf q=new Quaternionf().setFromNormalized(wanted.mul(local.transpose())).normalize();
        float pitch=(float)Math.atan2(2*(q.w*q.x+q.y*q.z),1-2*(q.x*q.x+q.y*q.y));
        float yaw=(float)Math.asin(Math.max(-1,Math.min(1,2*(q.w*q.y-q.z*q.x))));
        float roll=(float)Math.atan2(2*(q.w*q.z+q.x*q.y),1-2*(q.y*q.y+q.z*q.z));
        return new float[]{pitch/RAD,yaw/RAD,roll/RAD,bend/RAD};
    }
    /** The part of {@code v} square to the unit {@code axis}, made unit ({@code fallback}'s if {@code v} runs along it). */
    private static Vector3f square(Vector3f v,Vector3f axis,Vector3f fallback){
        Vector3f p=new Vector3f(v).sub(new Vector3f(axis).mul(v.dot(axis)));
        if(p.lengthSquared()<1e-8f)p=new Vector3f(fallback).sub(new Vector3f(axis).mul(fallback.dot(axis)));
        return p.normalize();
    }

    // ---------------------------------------------------------------- first person

    /**
     * From one's own eyes (HeldItemRendererMixin), its own keys, framed to be seen: the right hand brings the arrow up
     * into the top right of the picture, its golden head turned down toward the eyes (at the chest under them); the
     * left hand comes up to it in the middle; both draw it up, then drive it down out of the bottom of the picture; the
     * hands come back up into sight, shaking, empty, and hang there while it is drawn in; then are flung out of the
     * picture (chosen) or drop out of it (rejected). Per key: each fist (neck px: the eye 2.16 px above and 1 px ahead
     * of it; +x the picture's left, +y down, -z ahead), the arrow's way and face, and join. The forearms come up to the
     * fists from below the picture (VIEW_SHOULDER).
     */
    private static float[] view(float rx,float ry,float rz,float lx,float ly,float lz,float ax,float ay,float az,float fx,float fy,float fz,float join){
        return new float[]{rx,ry,rz,lx,ly,lz,ax,ay,az,fx,fy,fz,join};
    }
    private static final MiningPose.Track VIEW=new MiningPose.Track(TIMES,new float[][]{
        view(-5.5f,6.5f,-9,  6,10,-8,        0,.3f,-1,  1,0,0, 0),
        view(-5.2f,1.8f,-9.5f, 6,10,-8,      .1f,.95f,-.1f, 1,0,0, 0),
        view(-4.4f,-3.4f,-9.6f, 5.6f,9.5f,-8, .35f,.8f,.5f, 1,0,-.6f, 0),
        view(-4.2f,-3.8f,-9.4f, 5.6f,9.5f,-8, .32f,.82f,.48f, 1,0,-.6f, 0),
        view(-2.6f,-2f,-9.4f, 1.6f,-1.6f,-9.4f, .15f,.85f,.5f, 1,0,-.2f, .7f),
        view(-CLASP/2,-1.2f,-9, CLASP/2,-1.2f,-9, 0,.86f,.5f, 1,0,0, 1),
        view(-CLASP/2,-3.4f,-9.4f, CLASP/2,-3.4f,-9.4f, 0,.9f,.42f, 1,0,0, 1),
        view(-CLASP/2,9.5f,-3.5f, CLASP/2,9.5f,-3.5f, 0,.62f,.78f, 1,0,0, 1),
        view(-CLASP/2,9.8f,-3.2f, CLASP/2,9.8f,-3.2f, 0,.62f,.78f, 1,0,0, 1),
        view(-7.5f,3.5f,-8, 7.5f,3.5f,-8, 0,.62f,.78f, 1,0,0, 1),
        view(-3.6f,1.2f,-8.6f, 3.6f,1.4f,-8.6f, 0,.62f,.78f, 1,0,0, 1),
        view(-3.9f,2.4f,-8.3f, 3.9f,2.6f,-8.3f, 0,.62f,.78f, 1,0,0, 1)},STRUCK);
    private static final MiningPose.Track VIEW_AWAKEN=new MiningPose.Track(AWAKEN_TIMES,new float[][]{
        view(-3.9f,2.4f,-8.3f, 3.9f,2.6f,-8.3f, 0,.62f,.78f, 1,0,0, 1),
        view(-12f,3f,-5, 12f,3f,-5, 0,.62f,.78f, 1,0,0, 1),
        view(-12f,4f,-5, 12f,4f,-5, 0,.62f,.78f, 1,0,0, 1),
        view(-6f,11f,-6, 6f,11f,-6, 0,.62f,.78f, 1,0,0, 1)},1);
    private static final MiningPose.Track VIEW_DIE=new MiningPose.Track(new float[]{0,.12f,.42f},new float[][]{
        view(-3.9f,2.4f,-8.3f, 3.9f,2.6f,-8.3f, 0,.62f,.78f, 1,0,0, 1),
        view(-1.8f,10f,-3.5f, 1.8f,10f,-3.5f, 0,.62f,.78f, 1,0,0, 1),
        view(-2f,11f,-3, 2f,11f,-3, 0,.62f,.78f, 1,0,0, 1)},1);
    /** Each forearm comes up to its fist from here (px from the fist): from below the picture, the right from the right. */
    static final Vector3f VIEW_RIGHT_SHOULDER=new Vector3f(-3.5f,7.5f,1),VIEW_LEFT_SHOULDER=new Vector3f(3.5f,7.5f,1);
    /** One's own view this frame: each arm's shoulder and angles (pitch, yaw, roll and the elbow's bend), and the arrow (null once it is out of sight in the chest). */
    public record View(Vector3f rightShoulder,float[] rightArm,Vector3f leftShoulder,float[] leftArm,Arrow arrow){}
    public static View view(float t,Fate fate,float known){
        t=Math.max(0,t);
        float start=Math.max(REVEAL,known);
        float[] k=fate==Fate.AWAKEN&&t>start?VIEW_AWAKEN.at(Math.min(t-start,AWAKEN_TIMES[AWAKEN_TIMES.length-1]))
            :fate==Fate.DIE&&t>start?VIEW_DIE.at(Math.min(t-start,.42f)):VIEW.at(Math.min(t,REVEAL));
        float[] s=shake(t,fate,start);
        Vector3f right=new Vector3f(k[0]+s[4],k[1]+s[5],k[2]+s[6]),left=new Vector3f(k[3]+s[7],k[4]+s[8],k[5]+s[9]);
        Vector3f rs=new Vector3f(right).add(VIEW_RIGHT_SHOULDER),ls=new Vector3f(left).add(VIEW_LEFT_SHOULDER);
        float[] ra=reach(rs,right,new Vector3f(-1,1,.3f),-1),la=reach(ls,left,new Vector3f(1,1,.3f),1);
        Arrow arrow=null;
        if(t<HIT){
            Vector3f rf=MiningPose.fist(rs,ra,-1),lf=MiningPose.fist(ls,la,1);
            Vector3f grip=new Vector3f(rf).lerp(new Vector3f(rf).add(lf).mul(.5f),clamp(k[12]));
            Vector3f way=new Vector3f(k[6],k[7],k[8]).normalize();
            arrow=new Arrow(new Vector3f(way).mul(GRIP_TO_TIP).add(grip),way,orth(new Vector3f(k[9],k[10],k[11]),way),0,false);
        }
        return new View(rs,ra,ls,la,arrow);
    }

    // ---------------------------------------------------------------- the legs

    /** The feet where they stand (px from each hip, across and along): the right a little ahead, the left a little back. */
    private static final float[][] FEET={{-.5f,-.6f},{.5f,.9f}};
    /** On the knees: the hips this far down (px), the feet this far behind them and above the ground's line (the shins on the ground). */
    private static final float KNEEL_BOB=4;
    /** The thigh's and the shin's length (px). */
    private static final float BONE=6;
    /**
     * The legs: the feet planted where they stand; going down on the knees, the hips sink until the knees are on the
     * ground and the shins lie behind them; falling forward ({@code fall} degrees), the body tips over the knees until
     * it lies face down, the legs straight out behind.
     */
    static void legs(MotionPose p,float kneel,float fall,float bob,float shift,float lean){
        if(kneel<=0&&fall<=0){
            p.bob=bob;p.shift=shift;p.lean=lean;
            for(int i=0;i<2;i++)leg(p,i,FEET[i][0]-shift,11.75f-bob,FEET[i][1]-lean);
            return;
        }
        if(fall<=0){
            // sinking: from the planted feet to the kneel, the hips coming down
            p.bob=bob+(KNEEL_BOB-bob)*kneel;p.shift=shift*(1-kneel);p.lean=lean*(1-kneel);
            for(int i=0;i<2;i++){
                float x=(FEET[i][0]-shift)*(1-kneel),y=(11.75f-bob)+(BONE-(11.75f-bob))*kneel,z=(FEET[i][1]-lean)+(BONE-(FEET[i][1]-lean))*kneel;
                leg(p,i,x,y,z);
            }
            return;
        }
        // tipping forward over the knees (on the ground BONE under the kneeling hips)
        float a=fall*RAD,c=(float)Math.cos(a),s=(float)Math.sin(a);
        p.bob=KNEEL_BOB+BONE-BONE*c;p.shift=0;p.lean=-BONE*s;
        for(int i=0;i<2;i++)leg(p,i,0,BONE*c,BONE+BONE*s);
    }
    /** A two-segment leg reaching its foot (hip px), as the combat clips plant theirs (MiningPose's). */
    private static void leg(MotionPose p,int i,float x,float y,float z){
        float d=Math.min(11.98f,(float)Math.sqrt(x*x+y*y+z*z));
        float bend=(float)Math.acos(Math.max(0,d/12));
        p.joint[MotionPose.RIGHT_LEG+i][0]=(float)Math.toDegrees(Math.atan2(z,Math.hypot(x,y))-bend);
        p.joint[MotionPose.RIGHT_LEG+i][2]=(float)Math.toDegrees(-Math.atan2(x,Math.max(y,1e-3f)));
        p.knee[i]=(float)Math.toDegrees(2*bend);
    }

    /** Torso px to model px for the pose's torso (as MotionRig places it: its pivot at the neck, the hips at (shift, 12 + bob, lean)). */
    public static Vector3f toModel(MotionPose p,Vector3f torso){
        float[] b=p.joint[MotionPose.BODY];
        Quaternionf q=new Quaternionf().rotationZYX(b[2]*RAD,b[1]*RAD,b[0]*RAD);
        Vector3f hip=q.transform(new Vector3f(0,12,0));
        Vector3f neck=new Vector3f(p.shift-hip.x,12+p.bob-hip.y,p.lean-hip.z);
        return q.transform(new Vector3f(torso)).add(neck);
    }
}
