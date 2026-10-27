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
 * The stand arrow driven into one's own chest (2.70.0; 2.71.0 made it about ten seconds long, user request: "화살 꽂는
 * 모션이 10초는 됐으면 좋겠어 지금 너무 짧고 감동이 없어", StandArrowRules for the rest), as a smooth function of the
 * stab's clock (seconds), on the articulated rig. The right hand brings the arrow up before the eyes, its point to the
 * sky, turns it slowly in the fingers and raises it to the sky (its golden head catching the light); lowers it to the
 * chest, the head bowed, the shoulders sagging (the doubt), the left hand coming to it; both hands on it, a deep
 * breath; breathing out, it is turned over across the chest and its point set at the chest; raised high over the face
 * in both hands, point down, arched back, the eyes on it, held there trembling; one last lift (the load) and it is
 * driven down into the chest, faster and faster, stopping dead as it goes in (HIT). The body folds over it and is
 * frozen so a moment, shaking; arches back gasping as the hands are thrown off it; sways, and drops onto the right knee
 * (the body falling onto it), then both, hunched over it, the hands about it, while the arrow, glowing, is drawn into
 * the body and the heart beats faster and faster (BEATS); the head comes up, and for a moment everything is still
 * (STILL). Then its choice shows (REVEAL): one it chose throws the arms up and wide and the head back as the stand
 * bursts out of them, still on the knees, then gets up on the left foot, stands and turns to it; one it rejects
 * convulses, clutches the chest, sways and falls on their face (the server's death comes as they lie there).
 * <p>
 * Keyed channels on monotone cubic curves (MiningPose.Track), the plunge's key and the right knee's struck (they speed
 * up all the way into them); the body's joints come from where the fists go (a two-bone solve, {@link #reach}) and the
 * legs from where the feet stay or the knees go down ({@link #legs}). The arrow rides in the fists until it goes in
 * (aimed at where it goes in as it is driven down), then stays where it went in (it moves with the chest), and is drawn
 * into it. Torso space throughout: model pixels from the neck, +x the body's left, +y down, -z ahead (the shoulders at
 * (∓5, 2, 0), the chest's front at z = -2).
 * <p>
 * Whatever the arrow chose follows from the pose it holds as the choice shows: a choice heard late (the server's word
 * is on its way) starts from that pose then, so nothing jumps. No camera of its own: the player's camera stays theirs
 * (user request, 2.71.0: "컷씬이 나오면 안돼").
 */
public final class ArrowStabPose {
    private ArrowStabPose(){}
    private static final float RAD=(float)Math.PI/180;
    /** Seconds on the stab's clock: the arrow goes in; its choice shows; one it chose is free; one it rejects dies. */
    public static final float HIT=StandArrowRules.HIT/20f,REVEAL=StandArrowRules.REVEAL/20f,FREE=StandArrowRules.FREE/20f,DEATH=StandArrowRules.DEATH_AT/20f;
    /** What the arrow did, as far as this client has heard. */
    public enum Fate{UNKNOWN,AWAKEN,DIE}
    /**
     * The stages (seconds on the stab's clock; the light and the sounds go by them): brought up before the eyes; turned
     * in the fingers; lowered (the doubt) and dwelt on; the deep breath, both hands on it; taken up and out in the right
     * hand (the left going to the heart) and raised to the sky; turned point down up there; aimed at the chest;
     * trembling; the load (the blow
     * from there into HIT); folded over it; frozen; the gasp; swaying; the right knee down; both; drawn in; still.
     */
    public static final float LIFT=.5f,STUDY=1.1f,DOUBT=1.75f,DWELL=2.2f,BREATH=2.8f,UP=3.1f,RAISE=3.45f,TURN=3.95f,AIM=4.35f,TREMBLE=4.85f,
        LOAD=5.18f,CRUNCH=5.6f,FROZEN=6f,GASP=6.35f,SWAY=6.8f,KNEE=7.05f,KNEES=7.45f,ABSORB=8.3f,STILL=8.82f;

    // ---------------------------------------------------------------- the arrow

    /**
     * The arrow's sprite (item pixels; tools: its 32 px texture): its axis is the line y = x, along which the tip is
     * TIP_U from the corner, the golden head begins at HEAD_U, the hands close at GRIP_U (the bare shaft just under the
     * head) and the nock is at NOCK_U; the sprite's middle is at z = 8. Drawn SCALE model pixels to an item pixel.
     */
    public static final float TIP_U=21.57f,HEAD_U=12f,GRIP_U=10.2f,NOCK_U=1.77f,SCALE=.6f;
    private static final float R2=(float)Math.sqrt(.5);
    static final Vector3f ALONG=new Vector3f(R2,R2,0),ACROSS=new Vector3f(-R2,R2,0),FACE=new Vector3f(0,0,1);
    /** From the grip to the tip (model px). */
    static final float GRIP_TO_TIP=(TIP_U-GRIP_U)*SCALE;
    /** Where the tip goes into the chest (torso px: its front, the breastbone), and how far in it goes (px along the arrow). */
    static final Vector3f ENTRY=new Vector3f(0,4.3f,-2f);
    static final float DEPTH=2.4f;
    /** The arrow is drawn into the body over these seconds, on the knees (gone before the stillness, as the server spends it at REVEAL). */
    static final float SINK_FROM=7.75f,SINK_TO=8.55f;
    /** As it is drawn in it slides this far further in (px) while it shrinks to nothing where it went in. */
    static final float SINK_SLIDE=1.6f;
    /** The heart beating, faster and faster, from the arrow's going in until the stillness (seconds). */
    public static final float[] BEATS={5.95f,6.7f,7.3f,7.8f,8.17f,8.45f,8.65f,8.79f};

    // ---------------------------------------------------------------- the keys

    /** One key's channels (see {@link #row}). */
    private static final class K {
        float pitch,yaw,roll,bob,shift,lean,hp,hy,hr,join,liftR,liftL,kneelR,kneelL;
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
        K knees(float r,float l){kneelR=r;kneelL=l;return this;}
        K poles(float rx,float ry,float rz,float lx,float ly,float lz){pr=new float[]{rx,ry,rz};pl=new float[]{lx,ly,lz};return this;}
        K copy(){var k=new K();k.pitch=pitch;k.yaw=yaw;k.roll=roll;k.bob=bob;k.shift=shift;k.lean=lean;k.hp=hp;k.hy=hy;k.hr=hr;k.join=join;k.liftR=liftR;k.liftL=liftL;
            k.kneelR=kneelR;k.kneelL=kneelL;k.r=r.clone();k.l=l.clone();k.a=a.clone();k.f=f.clone();k.pr=pr.clone();k.pl=pl.clone();return k;}
        /**
         * 0-5 the torso's pitch, yaw, roll and the hips' bob, shift, lean; 6-8 the head's (model space); 9-14 the fists;
         * 15-20 the arrow's way and face; 21 join; 22-23 the shoulders' lift; 24-29 the elbows' poles; 30-31 how far
         * down each knee is (the right's, the left's).
         */
        float[] row(){return new float[]{pitch,yaw,roll,bob,shift,lean,hp,hy,hr,r[0],r[1],r[2],l[0],l[1],l[2],a[0],a[1],a[2],f[0],f[1],f[2],join,liftR,liftL,pr[0],pr[1],pr[2],pl[0],pl[1],pl[2],kneelR,kneelL};}
    }
    /** The arrow's way and the fists as it goes in: from the grip, ENTRY + DEPTH along WAY_IN is the tip. */
    static final Vector3f WAY_IN=new Vector3f(0,.62f,.78f).normalize();
    static final Vector3f GRIP_IN=new Vector3f(WAY_IN).mul(DEPTH-GRIP_TO_TIP).add(ENTRY);
    /** The fists close on either side of the shaft once both hold it (px between their middles). */
    static final float CLASP=3.6f;
    /** Where the right fist holds it high before the right of the face, aimed (its way toward where it goes in), and lifted for the blow (the load). */
    private static final Vector3f AIM_GRIP=new Vector3f(-5.6f,-4.6f,-6.2f),AIM_WAY=new Vector3f(ENTRY).sub(AIM_GRIP).normalize();
    private static final Vector3f LOAD_GRIP=new Vector3f(-5.7f,-5f,-6f),LOAD_WAY=new Vector3f(ENTRY).sub(LOAD_GRIP).normalize();

    /** Standing, the arrow in the right hand pointing ahead. */
    private static final K AT_REST=new K();
    /** Brought up before the eyes in the right hand, its point to the sky, close; the left hand rising to the chest; looking at it. */
    private static final K LIFTING=new K().body(2,3,0).head(5,13,-3).right(-2.8f,.2f,-6.6f).arrow(.12f,-1,-.15f).face(1,0,.2f)
        .left(4.4f,6.8f,-3.8f).lift(.3f,0).poles(-1,1,.2f,.9f,1,.3f);
    /** Turned slowly in the fingers (its golden head catching the light), held a little higher, the head tilted. */
    private static final K STUDYING=new K().body(1,4,0).head(3,12,7).right(-2.5f,-.8f,-6.5f).arrow(.04f,-1,-.22f).face(.25f,0,1)
        .left(3.6f,4.8f,-4.6f).lift(.45f,.1f).poles(-1,.9f,.1f,.9f,1,.2f);
    /** Lowered to the chest, the head bowed, the shoulders sagging (the doubt); the left hand coming to it; dwelt on, lower still. */
    private static final K DOUBTING=new K().body(7,0,0).hips(.3f,0,.2f).head(30,4,-3).right(-2.2f,3f,-5.8f).arrow(.05f,-.8f,-.6f).face(1,0,-.3f)
        .left(1.2f,3.2f,-5.6f).join(.45f).lift(-.2f,-.2f).poles(-1,1,.3f,1,1,.3f);
    private static final K DWELLING=DOUBTING.copy().body(9,0,1).hips(.4f,0,.25f).head(36,2,-5).right(-2.1f,3.6f,-5.6f).left(1.2f,3.8f,-5.4f).arrow(.05f,-.75f,-.66f).lift(-.35f,-.35f);
    /** Both hands on it, upright before the chest: the deep breath, the chest rising, the head coming up. */
    private static final K INHALING=new K().body(-5,0,0).hips(-.2f,0,-.3f).head(-6,0,0).right(-CLASP/2,1.2f,-6.6f).left(CLASP/2,1.2f,-6.6f)
        .arrow(0,-1,-.12f).face(1,0,0).join(1).lift(.95f,.95f).poles(-.9f,1,.1f,.9f,1,.1f);
    /** Taken up and out before the right shoulder in the right hand (clear of the face), the left hand going to the heart. */
    private static final K TAKEN_UP=new K().body(-4,-2,0).hips(-.2f,0,-.3f).head(-16,10,0).right(-6f,-1.6f,-5.2f).arrow(.05f,-1,-.1f).face(1,0,.3f)
        .left(2.6f,3.4f,-4.6f).lift(1.3f,.5f).poles(-1,.8f,.3f,.9f,1,.3f);
    /** Raised to the sky in the right hand, high over the right shoulder, the left hand pressed to the heart; arched back, the eyes on it. */
    private static final K RAISED=new K().body(-5,-3,0).hips(-.3f,0,-.4f).head(-30,14,0).right(-6.2f,-5.2f,-2.4f).arrow(.1f,-1,-.05f).face(1,0,.4f)
        .left(2.4f,4.4f,-4f).lift(1.8f,.3f).poles(-1,.2f,.6f,.9f,1,.3f);
    /** Turned point down up there, its tip sweeping forward and down beside the head. */
    private static final K TURNING=RAISED.copy().head(-26,12,0).right(-6.2f,-5f,-4.2f).arrow(-.05f,-.15f,-1).face(1,0,0).poles(-1,.3f,.4f,.9f,1,.3f);
    /** Aimed at the chest from high before the right of the face, the head thrown back with the eyes on it (the arrow's line clear of the chin). */
    private static final K AIMED=TURNING.copy().body(-9,-2,0).hips(-.4f,0,-.6f).head(-39,10,0).right(AIM_GRIP.x,AIM_GRIP.y,AIM_GRIP.z).arrow(AIM_WAY.x,AIM_WAY.y,AIM_WAY.z).poles(-1,.4f,.2f,.9f,1,.3f);
    /** Held there, trembling, the arm tiring, the left hand pressing the heart. */
    private static final K TREMBLING=AIMED.copy().body(-10,-2,0).head(-40,9,0).right(AIM_GRIP.x,AIM_GRIP.y+.3f,AIM_GRIP.z+.1f).left(2.3f,4.6f,-3.9f);
    /** The last lift before the blow (the load), the breath caught: aimed at where it goes in. */
    private static final K LOADED=AIMED.copy().body(-12,-3,0).hips(-.5f,0,-.8f).head(-42,8,0).right(LOAD_GRIP.x,LOAD_GRIP.y,LOAD_GRIP.z).arrow(LOAD_WAY.x,LOAD_WAY.y,LOAD_WAY.z).lift(2f,.3f);
    /** Driven in (struck: it speeds up all the way into this key and stops dead). */
    private static final K DRIVEN=INHALING.copy().body(8,0,0).hips(.8f,0,.3f).head(-14,0,0).right(GRIP_IN.x-CLASP/2,GRIP_IN.y,GRIP_IN.z).left(GRIP_IN.x+CLASP/2,GRIP_IN.y,GRIP_IN.z)
        .arrow(WAY_IN.x,WAY_IN.y,WAY_IN.z).lift(-.3f,-.3f).poles(-.9f,.8f,.5f,.9f,.8f,.5f);
    /** Folded over it, the hands still on it (the head, thrown back as it went in, coming down over it); frozen so a moment, shaking. */
    private static final K FOLDED=DRIVEN.copy().body(24,0,0).hips(1.2f,0,.5f).head(37,0,0).lift(.5f,.5f);
    private static final K STUNNED=FOLDED.copy().body(21,0,0).head(33,0,0).lift(.6f,.6f);
    /** Arched back, gasping, the hands thrown off it. */
    private static final K GASPING=DRIVEN.copy().body(-16,0,0).hips(.4f,0,-.6f).head(-30,0,0).right(-6.5f,4f,-4.8f).left(6.5f,4f,-4.8f).lift(.8f,.8f).poles(-1,.6f,.3f,1,.6f,.3f);
    /** Swaying, the knees giving. */
    private static final K SWAYING=GASPING.copy().body(-6,0,4).hips(.9f,-.3f,-.2f).head(-8,-6,6).right(-5.8f,5.6f,-4.4f).left(5.6f,5.2f,-4.6f).lift(.3f,.3f);
    /** Down on the right knee (struck: the body drops onto it), the hands going to the chest. */
    private static final K ONE_KNEE=SWAYING.copy().body(10,0,-3).hips(1.5f,0,0).head(30,4,-4).right(-2.6f,5.6f,-3.6f).left(2.8f,5.2f,-3.7f).knees(1,0).lift(.2f,.1f).poles(-.9f,1,.3f,.9f,1,.3f);
    /** On both knees, hunched over it, the hands about it. */
    private static final K KNEELING=ONE_KNEE.copy().body(16,0,0).head(40,0,0).right(-1.9f,5.4f,-3.3f).left(1.9f,5.4f,-3.3f).knees(1,1).lift(.3f,.3f);
    /** Being drawn in: the head coming up, the hands trembling apart. */
    private static final K ABSORBING=KNEELING.copy().body(8,0,0).head(18,0,0).right(-3.4f,6.2f,-3.8f).left(3.4f,6.2f,-3.8f).lift(.15f,.15f);
    /** Upright on the knees, still, the arms a little out: the moment before its choice. */
    private static final K STILLED=ABSORBING.copy().body(2,0,0).head(2,0,0).right(-4.6f,7.6f,-3.2f).left(4.6f,7.6f,-3.2f).lift(0,0);
    /** So as its choice shows (and as long as the word of it is on its way). */
    private static final K WAITING=STILLED.copy().body(1,0,0).head(0,0,0);
    /** The common keys' times (seconds), and the struck ones (the blow, the knee). */
    static final float[] TIMES={0,LIFT,STUDY,DOUBT,DWELL,BREATH,UP,RAISE,TURN,AIM,TREMBLE,LOAD,HIT,CRUNCH,FROZEN,GASP,SWAY,KNEE,KNEES,ABSORB,STILL,REVEAL};
    private static final int[] STRUCK={12,17};
    private static final MiningPose.Track COMMON=new MiningPose.Track(TIMES,rows(AT_REST,LIFTING,STUDYING,DOUBTING,DWELLING,INHALING,TAKEN_UP,RAISED,TURNING,AIMED,TREMBLING,
        LOADED,DRIVEN,FOLDED,STUNNED,GASPING,SWAYING,ONE_KNEE,KNEELING,ABSORBING,STILLED,WAITING),STRUCK);

    /**
     * Chosen: the arms flung up and wide and the head thrown back as the stand bursts out, still on the knees; held;
     * then up on the left foot, the hands by the knees; standing; turned to it (on their right).
     */
    private static final K BURST=WAITING.copy().body(-24,0,0).head(-48,0,0).right(-9.5f,-3.5f,1.2f).left(9.5f,-3.5f,1.2f).lift(.6f,.6f).poles(-.2f,1,.6f,.2f,1,.6f);
    private static final K GLORY=BURST.copy().body(-18,0,0).head(-36,0,0).right(-9.8f,-2.6f,1f).left(9.8f,-2.6f,1f).lift(.4f,.4f);
    private static final K RISING=GLORY.copy().body(8,0,0).head(6,0,0).right(-5.8f,7.6f,-3f).left(5.8f,7.6f,-3f).knees(1,0).lift(0,0).poles(-.9f,1,.2f,.9f,1,.2f);
    private static final K STANDING=new K().body(2,4,0).head(0,18,0).right(-5.6f,10.2f,-.8f).left(5.6f,10.2f,-.6f).arrow(WAY_IN.x,WAY_IN.y,WAY_IN.z).face(1,0,0);
    private static final K TURNED=new K().body(0,8,0).head(-4,38,0).right(-5.6f,10.2f,-.8f).left(5.6f,10.2f,-.6f).arrow(WAY_IN.x,WAY_IN.y,WAY_IN.z).face(1,0,0);
    static final float[] AWAKEN_TIMES={0,.28f,.7f,1.22f,1.66f,2f};
    private static final MiningPose.Track AWAKENED=new MiningPose.Track(AWAKEN_TIMES,rows(WAITING,BURST,GLORY,RISING,STANDING,TURNED),1);

    /**
     * Rejected, on its own channels: 0 how far down on the knees, 1 how far fallen forward from them (degrees), 2 the
     * torso's hunch over the knees, 3-4 its yaw and roll, 5-7 the hips' bob, shift, lean while standing, 8-10 the head
     * (pitch on the torso's, yaw, roll), 11-16 the fists, 17-18 the shoulders' lift, 19-24 the elbows' poles.
     */
    private static float[] down(float kneel,float fall,float hunch,float roll,float bob,float headUp,float headRoll,float[] r,float[] l,float lift,float[] pr,float[] pl){
        return new float[]{kneel,fall,hunch,0,roll,bob,0,WAITING.lean,headUp,0,headRoll,r[0],r[1],r[2],l[0],l[1],l[2],lift,lift,pr[0],pr[1],pr[2],pl[0],pl[1],pl[2]};
    }
    static final float[] DIE_TIMES={0,.12f,.45f,.75f,1.1f,1.2f,1.4f};
    /** The one it rejects lands at this key (the fall speeds up all the way into it). */
    private static final int LANDS=4;
    /** When the one it rejects lands on their face (seconds after its choice shows). */
    public static final float LANDS_AT=DIE_TIMES[LANDS];
    private static final MiningPose.Track REJECTED=new MiningPose.Track(DIE_TIMES,new float[][]{
        down(1,0,WAITING.pitch,0,WAITING.bob,WAITING.hp-WAITING.pitch,0,WAITING.r,WAITING.l,0,WAITING.pr,WAITING.pl),
        // the convulsion: the hands clutch the chest
        down(1,0,26,-3,WAITING.bob,20,0,new float[]{-1.6f,4.6f,-3.4f},new float[]{1.6f,4.6f,-3.4f},.6f,new float[]{-.9f,1,.3f},new float[]{.9f,1,.3f}),
        // hunched over it
        down(1,0,30,2,WAITING.bob,26,0,new float[]{-1.9f,5.2f,-3.3f},new float[]{1.9f,5.2f,-3.3f},.4f,new float[]{-.9f,1,.3f},new float[]{.9f,1,.3f}),
        // swaying, the hands slipping off the chest
        down(1,0,38,-4,WAITING.bob,30,8,new float[]{-3f,8.8f,-3.4f},new float[]{3.4f,8.4f,-3.2f},0,new float[]{-.8f,1,.4f},new float[]{.8f,1,.4f}),
        // face down, the arms along the body, the head lifted a little, the chin on the ground
        down(1,90,36,0,WAITING.bob,-66,4,new float[]{-5.6f,10.6f,.3f},new float[]{5.6f,10.6f,.3f},0,new float[]{0,1,1},new float[]{0,1,1}),
        down(1,85,36,0,WAITING.bob,-58,5,new float[]{-5.6f,10.6f,.1f},new float[]{5.6f,10.6f,.1f},0,new float[]{0,1,1},new float[]{0,1,1}),
        down(1,90,36,0,WAITING.bob,-64,4,new float[]{-5.6f,10.6f,.3f},new float[]{5.6f,10.6f,.3f},0,new float[]{0,1,1},new float[]{0,1,1})},LANDS);

    private static float[][] rows(K... keys){var out=new float[keys.length][];for(int i=0;i<keys.length;i++)out[i]=keys[i].row();return out;}
    /** The common keys' channels at {@code t} (as {@link K#row}), before what shakes the body (the tests: where the fists are meant to go). */
    static float[] keys(float t){return COMMON.at(t);}

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
        float kneelR,kneelL,fall=0;
        if(fate==Fate.DIE&&t>start){
            float[] d=REJECTED.at(t-start);
            kneelR=kneelL=clamp(d[0]);fall=d[1];
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
            kneelR=clamp(k[30]);kneelL=clamp(k[31]);
        }
        // the trembling, the jolt as it goes in and the heart's beats, on top
        float[] s=shake(t,fate,start);
        pitch+=s[0];roll+=s[1];hp+=s[2];hr+=s[3];
        right.add(s[4],s[5],s[6]);left.add(s[7],s[8],s[9]);
        if(fate==Fate.DIE&&t>start)fall=Math.min(90,fall);
        p.rot(MotionPose.BODY,pitch,yaw,roll).rot(MotionPose.HEAD,hp,hy,hr);
        // the legs: the feet where they stand, or the knees on the ground and the body tipping forward over them
        legs(p,kneelR,kneelL,fall,bob,shift,lean);
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
            // driven down from the load, it is aimed at where it goes in all the way (its line through ENTRY)
            float aimed=Ease.smooth(t,LOAD-.08f,LOAD);
            if(aimed>0)way.lerp(new Vector3f(ENTRY).sub(grip).normalize(),aimed).normalize();
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
     * What moves the body on top of the keys (degrees and px): the slow breaths while the arrow is looked at; the hand
     * trembling as it is lowered (the doubt); the arms trembling harder and harder as it is held high; the jolt through
     * the body as it goes in, and the shaking as they are frozen over it; the heavy panting after the gasp; the heart's
     * beats; the whole body shaking harder and harder on the knees as it is drawn in, stopping dead as everything goes
     * still; and the convulsions of one it rejects, dying down as they fall. Returns the torso's pitch and roll, the
     * head's pitch and roll, and each fist's move.
     */
    static float[] shake(float t,Fate fate,float start){
        float[] s=new float[10];
        // the slow breaths while it is looked at (the chest rising, the arms with it)
        float calm=1-Ease.smooth(t,DOUBT,BREATH);
        float breath=calm*(float)Math.sin(t*2*Math.PI*.32f);
        s[0]-=.9f*breath;s[2]-=.6f*breath;s[5]-=.15f*breath;s[8]-=.15f*breath;
        // the doubt: the lowered hand trembling a little
        float doubt=.16f*Ease.smooth(t,DOUBT-.35f,DOUBT)*(1-Ease.smooth(t,BREATH-.25f,BREATH));
        s[4]+=doubt*wave(t,8.7f,0);s[5]+=doubt*wave(t,10.9f,1.3f);s[6]+=doubt*.6f*wave(t,7.9f,2.1f);
        // raised and aimed: the arm trembling, harder and harder until the load (the hand on the heart a little)
        float hold=Ease.smooth(t,RAISE,AIM)*(1-Ease.smooth(t,LOAD,LOAD+.06f));
        float tremor=hold*(.14f+.36f*Ease.smooth(t,AIM,LOAD));
        s[4]+=tremor*wave(t,9.3f,0);s[5]+=tremor*wave(t,11.1f,1.3f);s[6]+=tremor*.6f*wave(t,8.2f,2.1f);
        s[7]+=.3f*tremor*wave(t,9.9f,.7f);s[8]+=.3f*tremor*wave(t,10.4f,2.6f);s[9]+=.2f*tremor*wave(t,8.8f,1.4f);
        s[1]+=hold*.5f*wave(t,7.1f,.3f);s[3]+=hold*.7f*wave(t,6.3f,1.9f);
        // the jolt as it goes in, and the shaking as they are frozen over it
        float jolt=kick(t,HIT,.12f);
        s[0]+=7*jolt;s[2]+=9*jolt;
        float shock=Ease.smooth(t,CRUNCH-.1f,CRUNCH+.1f)*(1-Ease.smooth(t,GASP-.12f,GASP+.04f));
        s[1]+=shock*1.1f*wave(t,15.3f,.4f);s[3]+=shock*1.3f*wave(t,13.7f,2.2f);
        s[4]+=shock*.25f*wave(t,16.1f,.9f);s[5]+=shock*.25f*wave(t,14.3f,2.8f);s[7]+=shock*.25f*wave(t,15.1f,1.7f);s[8]+=shock*.25f*wave(t,13.9f,.2f);
        // the heavy panting after the gasp, dying down on the knees
        float pant=Ease.smooth(t,GASP,GASP+.15f)*(1-Ease.smooth(t,KNEES,ABSORB))*(float)Math.sin((t-GASP)*2*Math.PI*1.35f);
        s[0]-=2.2f*pant;s[2]-=1.5f*pant;
        // the heart's beats
        float beat=0;for(float b:BEATS)beat+=kick(t,b,.09f);
        s[0]+=2.4f*beat;s[2]+=1.6f*beat;s[5]+=.35f*beat;s[8]+=.35f*beat;
        // the shaking growing on the knees as it is drawn in, stopping dead as everything goes still; for one it
        // rejects, again as its choice shows, dying down as they fall
        float quake=Ease.smooth(t,KNEES,STILL-.05f)*(1-Ease.smooth(t,STILL-.06f,STILL+.02f));
        if(fate==Fate.DIE&&t>start)quake=Math.max(quake,Ease.smooth(t-start,0,.05f)*(1-Ease.smooth(t-start,.6f,1f)));
        float q=1.05f*quake;
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
     * How brightly the arrow shines (0 to 1): its head glints as it is turned before the eyes, and goes dull as it is
     * lowered; it shines brightest raised to the sky, and again aimed at the chest; flares as it goes in; then the wound
     * glows, beating with the heart, brighter and brighter as it is drawn in on the knees, held in a little as everything
     * goes still; one it chose flares out with it as the choice shows, for one it rejects it gutters out.
     */
    public static float glow(float t,Fate fate,float start){
        float looked=Ease.smooth(t,LIFT-.25f,LIFT+.15f)*(1-Ease.smooth(t,DOUBT-.3f,DOUBT+.1f))*(.42f+.25f*(1-Ease.smooth(Math.abs(t-STUDY),0,.4f)));
        float sky=Ease.smooth(t,BREATH+.1f,RAISE)*(1-Ease.smooth(t,TURN-.1f,AIM))*.82f;
        float raised=Ease.smooth(t,TURN,AIM)*(1-Ease.smooth(t,LOAD+.04f,HIT))*.5f;
        float flare=kick(t,HIT,.2f);
        float beat=0;for(float b:BEATS)beat+=kick(t,b,.11f);
        float wound=Ease.smooth(t,HIT+.1f,HIT+.4f),drawn=Ease.smooth(t,KNEES-.3f,SINK_TO);
        float g=Math.max(Math.max(looked,Math.max(sky,raised)),Math.max(flare,wound*(.18f+.2f*beat)+.25f*drawn+.3f*beat*drawn+.4f*Ease.smooth(t,SINK_FROM,SINK_TO)));
        g*=1-.3f*Ease.smooth(t,STILL-.04f,STILL+.06f);
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
     * before the eyes, point up, turns it (its broad side to the eyes), raises it into the top of the picture and lowers
     * it, the left hand coming to it; both hands hold it upright, turn it over across the picture and set its point at
     * the chest (toward the eyes, under them); raise it high, trembling, its golden head down toward the eyes; drive it
     * down out of the bottom of the picture; the hands come back up into sight, shaking, empty, and go to the chest as
     * the knees go down; come up before the eyes, trembling, and hang there still; then are flung out of the picture
     * (chosen) or drop out of it (rejected). Per key: each fist (neck px: the eye 2.16 px above and 1 px ahead of it;
     * +x the picture's left, +y down, -z ahead), the arrow's way and face, and join. The forearms come up to the fists
     * from below the picture (VIEW_SHOULDER). The view does not kneel: the camera is the player's own (no cutscene).
     */
    private static float[] view(float rx,float ry,float rz,float lx,float ly,float lz,float ax,float ay,float az,float fx,float fy,float fz,float join){
        return new float[]{rx,ry,rz,lx,ly,lz,ax,ay,az,fx,fy,fz,join};
    }
    private static final MiningPose.Track VIEW=new MiningPose.Track(TIMES,new float[][]{
        view(-5.5f,6.5f,-9,  6,10,-8,  0,.3f,-1,  1,0,0, 0),
        view(-3.2f,.5f,-8.8f, 5.2f,8,-8, .08f,-1,-.1f, 1,0,.2f, 0),
        view(-2.6f,-.4f,-8.4f, 4.2f,5.8f,-8.6f, .03f,-1,-.2f, .25f,0,1, 0),
        view(-2.8f,4.2f,-8.2f, 1.4f,4.4f,-8.4f, .05f,-.8f,-.6f, 1,0,-.3f, .45f),
        view(-2.6f,4.8f,-8f, 1.2f,5f,-8.2f, .05f,-.75f,-.66f, 1,0,-.3f, .45f),
        view(-CLASP/2,3.2f,-8.8f, CLASP/2,3.2f,-8.8f, 0,-1,-.15f, 1,0,0, 1),
        view(-6.5f,-1.4f,-8.6f, 4.5f,8.5f,-6, .05f,-1,-.1f, 1,0,.3f, 0),
        view(-5f,-4.2f,-9.4f, 4.5f,8.5f,-6, .05f,-1,-.2f, 1,0,.4f, 0),
        view(-5.2f,-4.6f,-9f, 4.5f,8.5f,-6, .3f,-.3f,-.9f, 1,0,0, 0),
        view(-4.6f,-3.6f,-9.2f, 4.5f,8.5f,-6, .4f,.8f,.45f, 1,0,0, 0),
        view(-4.4f,-3.2f,-9.2f, 4.4f,8.4f,-6, .4f,.8f,.45f, 1,0,0, 0),
        view(-4.6f,-4.2f,-9f, 4.4f,8.4f,-6, .42f,.82f,.38f, 1,0,0, 0),
        view(-CLASP/2,9.5f,-3.5f, CLASP/2,9.5f,-3.5f, 0,.62f,.78f, 1,0,0, 1),
        view(-CLASP/2,9.8f,-3.2f, CLASP/2,9.8f,-3.2f, 0,.62f,.78f, 1,0,0, 1),
        view(-CLASP/2,9.6f,-3.4f, CLASP/2,9.6f,-3.4f, 0,.62f,.78f, 1,0,0, 1),
        view(-7.5f,3.5f,-8, 7.5f,3.5f,-8, 0,.62f,.78f, 1,0,0, 1),
        view(-6.2f,4.5f,-8.2f, 6f,4.2f,-8.4f, 0,.62f,.78f, 1,0,0, 1),
        view(-3.2f,6.8f,-6.5f, 3.4f,6.4f,-6.6f, 0,.62f,.78f, 1,0,0, 1),
        view(-2.4f,7.6f,-5.2f, 2.4f,7.6f,-5.2f, 0,.62f,.78f, 1,0,0, 1),
        view(-3.6f,3.6f,-8.4f, 3.6f,3.8f,-8.4f, 0,.62f,.78f, 1,0,0, 1),
        view(-4.2f,4.6f,-8.2f, 4.2f,4.8f,-8.2f, 0,.62f,.78f, 1,0,0, 1),
        view(-4.2f,4.6f,-8.2f, 4.2f,4.8f,-8.2f, 0,.62f,.78f, 1,0,0, 1)},STRUCK);
    private static final MiningPose.Track VIEW_AWAKEN=new MiningPose.Track(AWAKEN_TIMES,new float[][]{
        view(-4.2f,4.6f,-8.2f, 4.2f,4.8f,-8.2f, 0,.62f,.78f, 1,0,0, 1),
        view(-12f,1f,-5, 12f,1f,-5, 0,.62f,.78f, 1,0,0, 1),
        view(-12f,2f,-5, 12f,2f,-5, 0,.62f,.78f, 1,0,0, 1),
        view(-6f,11f,-6, 6f,11f,-6, 0,.62f,.78f, 1,0,0, 1),
        view(-6f,11f,-6, 6f,11f,-6, 0,.62f,.78f, 1,0,0, 1),
        view(-6f,11f,-6, 6f,11f,-6, 0,.62f,.78f, 1,0,0, 1)},1);
    private static final float[] VIEW_DIE_TIMES={0,.12f,.45f};
    private static final MiningPose.Track VIEW_DIE=new MiningPose.Track(VIEW_DIE_TIMES,new float[][]{
        view(-4.2f,4.6f,-8.2f, 4.2f,4.8f,-8.2f, 0,.62f,.78f, 1,0,0, 1),
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
            :fate==Fate.DIE&&t>start?VIEW_DIE.at(Math.min(t-start,VIEW_DIE_TIMES[2])):VIEW.at(Math.min(t,REVEAL));
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
    /** Where the foot's line ends standing, and where the shin lies kneeling (model px below the neck: the ground is at 24). */
    private static final float SOLE=23.75f,SHIN=22;
    /** A foot still planted as the other knee goes down steps this far forward (px), lifting this much on the way: down on one knee. */
    private static final float STEP=5,STEP_LIFT=1.2f;
    /**
     * The legs: the feet planted where they stand; going down on a knee ({@code kneelR}, {@code kneelL} from 0 to 1),
     * the hips sink with the lower one and each knee going down comes down onto the ground, its shin swinging back along
     * it, while a foot still planted steps forward under the knee it keeps up (down on one knee, as one gets down and
     * up), the rest of the way back as its own knee goes down; falling forward ({@code fall} degrees, on both knees),
     * the body tips over the knees until it lies face down, the legs straight out behind.
     */
    static void legs(MotionPose p,float kneelR,float kneelL,float fall,float bob,float shift,float lean){
        if(fall<=0){
            float drop=Math.max(kneelR,kneelL);
            p.bob=bob+(KNEEL_BOB-bob)*drop;p.shift=shift*(1-drop);p.lean=lean*(1-drop);
            float hip=12+p.bob;
            for(int i=0;i<2;i++){
                float k=i==0?kneelR:kneelL,step=Math.max(0,drop-k);
                float x=FEET[i][0]-p.shift,y=SOLE-hip-STEP_LIFT*(float)Math.sin(Math.PI*step),z=FEET[i][1]-p.lean-STEP*step;
                leg(p,i,x*(1-k),y+(SHIN-hip-y)*k,z+(BONE-z)*k);
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

    /** Where the knees rest on the ground kneeling (model px: under the hips, on the ground's line), right and left. */
    public static final Vector3f[] KNEELING_KNEES={new Vector3f(-1.9f,24,0),new Vector3f(1.9f,24,0)};
    /** Torso px to model px for the pose's torso (as MotionRig places it: its pivot at the neck, the hips at (shift, 12 + bob, lean)). */
    public static Vector3f toModel(MotionPose p,Vector3f torso){
        float[] b=p.joint[MotionPose.BODY];
        Quaternionf q=new Quaternionf().rotationZYX(b[2]*RAD,b[1]*RAD,b[0]*RAD);
        Vector3f hip=q.transform(new Vector3f(0,12,0));
        Vector3f neck=new Vector3f(p.shift-hip.x,12+p.bob-hip.y,p.lean-hip.z);
        return q.transform(new Vector3f(torso)).add(neck);
    }
}
