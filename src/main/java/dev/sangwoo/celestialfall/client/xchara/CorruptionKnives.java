package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.isosang.IsosangLimbs;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.KnivesRules;
import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.d;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.client.xchara.KnifeCombo.on;

/**
 * X Chara's T with their hack off, the three small knives thrown, drawn (2.131.0, user request with their video of it,
 * Roblox's "T - Corruption Knives": "t스킬은 영상처럼 간단하게 다른손으로 작은 나이프 3개를 던지는 기술이야"; what I read
 * of it they confirmed, "맞음, 이대로"; the server's XCharaAttack.knives and XCharaShot, KnivesRules). A skill of the
 * knife's pipeline (KnifeClient's swings; KnifeCombo's passes hand it on here).
 * <ul>
 * <li>The body ({@link #CLIP}): the hand that holds no knife lights purple, three small purple knives in it; it is
 * raised back by the shoulder, the body turned after it and its weight on the back foot; then thrown forward, the body
 * coming round and going down over a step of the other foot, the arm whipping through and sweeping on low while the
 * knives leave it one after another; held low a moment, and up again. The knife in the other hand stays black, trailed
 * out behind them against the throw.</li>
 * <li>The knives ({@link #draw}, the effects' layer): each a small purple blade (a dark rim, a lilac core), fanned in
 * the fist until it leaves; in flight a thin dark-purple line drawn out behind it from where it left (the video's: a
 * knife crosses to whom it strikes between two of its frames, and the line stays a few more), eaten up from its tail;
 * the knife's own burst on whom one strikes (SlashFx.burst: the white star and the purple spikes, bigger with each
 * knife, the video's); a burst of pixels where one met a block.</li>
 * <li>Its sound is the user's video's own, played as it begins (tools/xchara/extract_knives.py); a knife's strike is
 * the knives shot's (BladeShot.HIT_SOUND: the video plays that very sound).</li>
 * </ul>
 * Their own client tells the server where the middle of its screen points until the last knife has left the hand
 * (BladeShot.tellAim): each goes at where they look as it leaves. No screen effect but a small shake as a knife lands.
 * Everything is a function of the skill's own clock and what the server told (BladeShot's store of the knives told of).
 */
public final class CorruptionKnives {
    private CorruptionKnives(){}
    /** How long one is drawn for (seconds): the last knife's flight at its longest, and what it leaves. */
    public static final float LIFE=(float)(KnivesRules.over(KnivesRules.KNIVES-1)/20+.6);
    /** Its sound: the user's video's own, as it begins. */
    static final String SOUND="xchara_knives";
    /** When each knife leaves the hand (seconds). */
    static float fire(int k){return KnivesRules.FIRE[k]/20f;}
    /** A knife not thrown when the skill stopped short is gone over this long (seconds). */
    static final float VANISH=.1f;
    /** A knife that has struck stays in them this long before it is gone (seconds). */
    static final float STUCK=.05f;
    /** The line behind a knife in flight: no longer than this (blocks), eaten up from its tail at the knife's own speed once it is, and gone this long after the knife has stopped (seconds). */
    static final double LINE=7;static final float LINE_AFTER=.12f;
    /** How wide the three are fanned in the fist (degrees either side of the middle one). */
    static final double FAN=20;

    // ───────────────────────────── the body ─────────────────────────────

    /**
     * The throw. Written as the swings are (KnifeCombo.on: the world's ways, the feet where they are put): the left
     * hand out and up to behind the left shoulder as the body turns left and sits back (wound: RAISED), a breath there,
     * then the hips and the body round to the right and down over a step of the right foot, the arm through level and
     * on down across the front, the knife's arm thrown back against it; low until HOLD, and back.
     */
    static final Anim CLIP=clip();
    private static Anim clip(){
        float raised=KnivesRules.RAISED/20f,f0=fire(0),f1=fire(1),f2=fire(2),hold=KnivesRules.HOLD/20f,end=KnivesRules.LENGTH/20f;
        Anim.Fix bent=IsosangLimbs.natural(IsosangLimbs.FLOOR);
        return Anim.joints(
            k(0,KnifeCombo.STANCE),
            // the hand out to the side and up, the body beginning to turn after it
            k(.08f,on(.01,-.03,-.04,12,-2,1,d(-80,24),d(2,10),d(-34,-98),.20,0,-.20,-.04)),
            // wound: the hand back by the shoulder, the left shoulder back, the weight on the back foot
            k(raised,on(.03,-.08,-.10,32,-6,2,d(-68,54),d(4,22),d(24,-130),.20,0,-.21,-.16)),
            k(f0-.075f,on(.03,-.08,-.11,33,-6,2,d(-67,56),d(4,24),d(28,-134),.20,0,-.21,-.16)),
            // let go from the ground up: the hips and the body first, the arm after them
            k(f0-.035f,on(.02,.01,-.15,15,5,1,d(-64,88),d(5,32),d(24,-90),.20,.12,-.21,-.16)),
            // the first leaves: the arm through ahead, the right foot down
            k(f0,on(0,.12,-.21,-8,17,0,d(-60,124),d(6,42),d(6,-22),.20,.30,-.21,-.16)),
            k(f0+.05f,on(-.01,.19,-.26,-20,24,-1,d(-56,138),d(6,48),d(0,-6),.20,.38,-.21,-.16)),
            // the second: the arm on across the front, sinking
            k(f1,on(-.02,.24,-.30,-27,29,-2,d(-53,146),d(6,52),d(-5,2),.20,.40,-.21,-.16)),
            // the third: swept on down
            k(f2,on(-.03,.28,-.34,-32,33,-2,d(-50,152),d(6,56),d(-14,10),.20,.40,-.21,-.16)),
            // low over the front foot, the hand carried on a little
            k(hold,on(-.03,.27,-.33,-30,32,-2,d(-52,150),d(6,54),d(-32,18),.20,.40,-.21,-.16)),
            k(end-.08f,on(-.01,.12,-.14,-12,13,-1,d(-72,80),d(3,24),d(-66,10),.20,.18,-.20,-.06)),
            k(end,KnifeCombo.STANCE))
            .after((sec,f)->{
                R6.Frame b=bent.apply(sec,f);
                float rest=Math.max(1-Ease.smooth(sec,0,KnifeCombo.OUT_OF_REST),Ease.smooth(sec,end-KnifeCombo.INTO_REST,end));
                return rest<=0?b:R6.blend(b,KnifeCombo.AT_REST,Math.min(1,rest));
            });
    }
    /** The throwing hand and its shoulder {@code sec} seconds in, for a user standing at {@code feet} facing {@code yaw}. */
    static Vec3d hand(Vec3d feet,float yaw,float sec){return KagenoClips.freeHand(feet,yaw,R6.poseOf(CLIP.frame(sec)));}
    static Vec3d shoulder(Vec3d feet,float yaw,float sec){return KagenoClips.freeShoulder(feet,yaw,R6.poseOf(CLIP.frame(sec)));}

    // ───────────────────────────── where a knife is ─────────────────────────────

    /**
     * One small knife this frame: its tail's end and the way it points, how much of it shows, whether it flies; and of
     * one in flight where the line behind it begins and how much of that shows.
     */
    public record Pose(Vec3d tail,Vec3d dir,float shown,boolean flying,Vec3d lineFrom,float line){
        public Vec3d tip(){return tail.add(dir.multiply(KnivesRules.TIP));}
    }
    /** The way knife {@code k} points in the fist: on from the forearm and up a little, the three fanned. */
    static Vec3d held(Vec3d feet,float yaw,int k,float sec){
        Vec3d hand=hand(feet,yaw,sec),arm=hand.subtract(shoulder(feet,yaw,sec)).normalize();
        Vec3d mid=arm.add(0,.55,0).normalize(),side=mid.crossProduct(Vec3d.fromPolar(0,yaw)).normalize();
        if(!Double.isFinite(side.x)||side.lengthSquared()<1e-6)side=new Vec3d(0,1,0);
        Vec3d across=side.crossProduct(mid).normalize();double a=Math.toRadians((k-1)*FAN);
        return mid.multiply(Math.cos(a)).add(across.multiply(Math.sin(a))).normalize();
    }
    /**
     * Knife {@code k} {@code sec} seconds into a throw begun at {@code feet} facing {@code yaw}, that stopped short
     * {@code stopped} seconds in (anything past its end if it did not), by what was {@code told} of it (null: nothing
     * yet). Null while there is nothing of it to draw.
     */
    public static Pose pose(Vec3d feet,float yaw,int k,float sec,float stopped,BladeShot.Blade told){
        float appear=KnivesRules.APPEAR/20f,fire=fire(k);
        if(sec<appear)return null;
        float in=Ease.smooth(sec,appear,appear+.07f);
        if(sec<fire||stopped<fire){
            float out=stopped<fire?1-Ease.smooth(sec,stopped,stopped+VANISH):1;
            if(out<=0)return null;
            float at=Math.min(sec,stopped<fire?stopped:sec);
            return new Pose(hand(feet,yaw,at),held(feet,yaw,k,at),in*out,false,null,0);
        }
        // (not told of: the word is late or lost; nothing of it until it comes)
        if(told==null||told.from==null)return null;
        double a=(sec-fire)*20,head=KnivesRules.head(a);
        boolean hit=told.struck>=0&&!Double.isNaN(told.far);double end=hit?Math.max(KnivesRules.TIP,told.far):told.reach;
        float shown=1,since=0;
        if(head>=end){
            since=(float)((a-KnivesRules.reached(end))/20);head=end;
            // in whom it struck a moment; at a block, or as far as it goes, gone
            shown=hit?1-Ease.smooth(since,STUCK*.5f,STUCK):0;
        }
        // the line behind it: from where it left, no longer than LINE; eaten up from its tail, and gone a moment after the knife stops
        double from=Math.min(head,Math.max(0,KnivesRules.head(a)-LINE));
        float line=1-Ease.smooth(since,LINE_AFTER*.3f,LINE_AFTER);
        if(shown<=0&&(line<=0||head-from<.02))return null;
        return new Pose(told.from.add(told.dir.multiply(head-KnivesRules.TIP)),told.dir,shown,true,told.from.add(told.dir.multiply(from)),line);
    }
    private static float stopped(StandClient.Cast c){return KnifeClient.until(c.id());}
    static Pose pose(StandClient.Cast c,int k,float sec){return pose(c.origin(),c.yaw(),k,sec,stopped(c),BladeShot.told(c.id(),k));}
    /** The seconds into its throw knife {@code k} is seen to land on whom it struck (NaN: it struck no one, as far as is told). */
    static float landed(BladeShot.Blade b,int k){return b==null||b.struck<0||Double.isNaN(b.far)?Float.NaN:(float)((KnivesRules.FIRE[k]+KnivesRules.reached(b.far))/20);}
    /** The seconds into its throw knife {@code k}'s flight ends at a block or as far as it goes (NaN: not told, or it struck someone). */
    static float ended(BladeShot.Blade b,int k){return b==null||b.from==null||b.struck>=0?Float.NaN:(float)((KnivesRules.FIRE[k]+KnivesRules.reached(b.reach))/20);}
    static int seed(StandClient.Cast c,int k){return (int)(c.start()*31+k*13+11);}
    /** Which of the knife's bursts knife {@code k} leaves on whom it strikes, and how big (the video's grow with each). */
    static int burstOf(int k){return k==KnivesRules.KNIVES-1?XCharaCombo.HACK_LUNGE:XCharaCombo.HACK_SLASH;}
    static double burstSize(int k){return k==0?.8:1;}

    // ───────────────────────────── what is drawn ─────────────────────────────

    static final int INK=0x14052e,LINE_RGB=0x4a14b4;
    /** One small knife: its body laid over what is behind it (a dark rim, purple, a lilac core), or its light. */
    private static void knife(EffectMesh m,Vec3d tail,Vec3d dir,float a,boolean light){
        Vec3d tip=tail.add(dir.multiply(KnivesRules.TIP)),heel=tail.add(dir.multiply(KnivesRules.TIP*.3));
        if(light){
            m.softLine(tail,tip,.34,GlitchFx.GLOW,.3f*a);
            m.needle(heel,tip,.03,.3,GlitchFx.WHITE,.5f*a);
        }else{
            // its grip, a short dark rod; its blade, widest by its heel
            m.line(tail,heel,.05,INK,.95f*a);
            m.needle(tail.add(dir.multiply(KnivesRules.TIP*.2)),tip.add(dir.multiply(.03)),.17,.22,INK,.9f*a);
            m.needle(heel,tip,.11,.22,GlitchFx.PURPLE,.98f*a);
            m.needle(heel.add(dir.multiply(.04)),tip.subtract(dir.multiply(.06)),.045,.25,GlitchFx.LILAC,.9f*a);
        }
    }
    /** Everything of the throw but the body, in one pass (the blended bodies, or the light). */
    static void draw(Fx c,EffectMesh m,boolean light){
        float sec=c.sec,stop=stopped(c.cast),appear=KnivesRules.APPEAR/20f;Vec3d feet=c.origin;float yaw=c.cast.yaw();
        // the hand lit purple while it holds a knife: a glow, and a few blocks of purple blinking off it (the video's)
        float until=Math.min(fire(KnivesRules.KNIVES-1),stop);
        if(sec>=appear&&sec<until+.15f){
            Vec3d hand=hand(feet,yaw,Math.min(sec,stop));
            float lit=Ease.smooth(sec,appear,appear+.06f)*(1-Ease.smooth(sec,until,until+.15f)),near=GlitchFx.near(hand,c.camera)*lit;
            if(near>0){
                if(light){
                    m.softDisc(hand,.42,GlitchFx.GLOW,.42f*near);
                    if(sec<appear+.14f)m.star(hand,.45*(1-(sec-appear)/.14f)+.1,GlitchFx.LILAC,.8f*(1-(sec-appear)/.14f)*near);
                }
                int slot=(int)Math.floor(sec*14);var f=GlitchFx.facing(c.camera,hand);int seed=seed(c.cast,7);
                for(int j=0;j<5;j++){
                    if(GlitchFx.h(j,slot,seed,31)>.55)continue;
                    Vec3d at=hand.add(f[0].multiply((GlitchFx.h(j,slot,seed,33)-.5)*.5)).add(f[1].multiply((GlitchFx.h(j,slot,seed,34)-.5)*.5));
                    double half=.03+.04*GlitchFx.h(j,slot,seed,35);int rgb=GlitchFx.h(j,slot,seed,36)<.6?GlitchFx.PURPLE:GlitchFx.DEEP;
                    if(light)GlitchFx.square(m,at,f,half*1.8,GlitchFx.GLOW,.3f*near);else GlitchFx.square(m,at,f,half,rgb,.95f*near);
                }
            }
        }
        for(int k=0;k<KnivesRules.KNIVES;k++){
            int seed=seed(c.cast,k);var told=BladeShot.told(c.cast.id(),k);
            var p=pose(c.cast,k,sec);
            if(p!=null){
                float near=GlitchFx.near(p.tail(),c.camera);
                if(p.flying()){
                    // the thin dark line drawn out behind it, and a faint light along it
                    Vec3d head=p.tail().add(p.dir().multiply(KnivesRules.TIP*.4));
                    if(p.line()>0&&head.squaredDistanceTo(p.lineFrom())>4e-4){
                        float la=p.line()*GlitchFx.near(head,c.camera);
                        if(light)m.softTaper(p.lineFrom(),head,.03,.2,GlitchFx.GLOW,.3f*la);
                        else{m.needle(p.lineFrom(),head,.07,.92,INK,.6f*la);m.needle(p.lineFrom(),head,.04,.92,LINE_RGB,.95f*la);}
                    }
                }
                if(p.shown()>0&&near>0)knife(m,p.tail(),p.dir(),p.shown()*near,light);
            }
            // never thrown: it breaks up in the hand
            if(stop<fire(k)&&sec>=stop&&stop>=appear)GlitchFx.pixels(m,hand(feet,yaw,stop),GlitchFx.UP,1,2.4,.035,4,seed+5,sec-stop,light,c.camera);
            if(told==null||told.from==null||stop<fire(k))continue;
            // as it leaves the hand: a small flick of light there
            float gone=sec-fire(k);
            if(light&&gone>=0&&gone<.08f){float near=GlitchFx.near(told.from,c.camera);if(near>0)m.star(told.from,.3*(1-gone/.08f)+.08,GlitchFx.LILAC,.7f*(1-gone/.08f)*near);}
            float landed=landed(told,k),ended=ended(told,k);
            if(!Float.isNaN(landed)){
                float s=sec-landed;if(s<0)continue;
                Vec3d point=told.from.add(told.dir.multiply(told.far)),mid=told.mid==null?point:told.mid;
                if(told.took)SlashFx.burst(m.scaled(SlashFx.burstAt(mid,c.camera),burstSize(k)),mid,told.dir,burstOf(k),seed,s,light,c.camera);
                else if(s<.2f){
                    float near=GlitchFx.near(point,c.camera);
                    if(light&&near>0)m.star(point,.5*(1-s/.2f)+.15,GlitchFx.WHITE,.8f*(1-s/.2f)*near);
                    GlitchFx.pixels(m,point,told.dir.negate(),.75,3,.04,6,seed+9,s,light,c.camera);
                }
            }else if(!Float.isNaN(ended)){
                float s=sec-ended;
                // against a block: broken to pixels there
                if(s>=0&&told.reach<KnivesRules.RANGE-1e-6){
                    Vec3d point=told.from.add(told.dir.multiply(told.reach));float near=GlitchFx.near(point,c.camera);
                    if(light&&s<.1f&&near>0)m.star(point,.35*(1-s/.1f)+.1,GlitchFx.LILAC,.8f*(1-s/.1f)*near);
                    GlitchFx.pixels(m,point,told.dir.negate(),.8,3.5,.04,7,seed+3,s,light,c.camera);
                }
            }
        }
    }
    /** Its light on the world: a little purple by the hand and by each knife, more on whom one strikes; little afterglow while they fly (their light would leave copies of them). */
    static void frame(Fx c,FxFrame f){
        float sec=c.sec,stop=stopped(c.cast),appear=KnivesRules.APPEAR/20f,until=Math.min(fire(KnivesRules.KNIVES-1),stop);
        if(sec>=appear&&sec<until+.15f)f.light(hand(c.origin,c.cast.yaw(),Math.min(sec,stop)),0x9a3cff,4,.4f*Ease.smooth(sec,appear,appear+.06f)*(1-Ease.smooth(sec,until,until+.15f)));
        boolean flying=false;
        for(int k=0;k<KnivesRules.KNIVES;k++){
            var p=pose(c.cast,k,sec);
            if(p!=null&&p.flying()){flying=true;if(p.shown()>0)f.light(p.tip(),0x9a3cff,5,.5f*p.shown());}
            var told=BladeShot.told(c.cast.id(),k);float landed=landed(told,k);
            if(!Float.isNaN(landed)&&told.took){
                float s=sec-landed;
                if(s>=0&&s<.3f)f.light(told.mid==null?told.from.add(told.dir.multiply(told.far)):told.mid,0x9a3cff,k==KnivesRules.KNIVES-1?7:5,(k==KnivesRules.KNIVES-1?.8f:.55f)*(1-s/.3f));
            }
        }
        if(flying)f.afterglow(.05f);
    }
    /** How hard each knife shakes the camera as it lands on someone. */
    static final float[] SHAKE={.2f,.22f,.4f};
    static float shake(StandClient.Cast cast,float sec){
        float sum=0;
        for(int k=0;k<KnivesRules.KNIVES;k++){
            var told=BladeShot.told(cast.id(),k);float landed=landed(told,k);
            if(!Float.isNaN(landed)&&told.took)sum+=SHAKE[k]*KnifeCombo.bump(sec,landed,.06f);
        }
        return sum;
    }
    /** For the capture tools: knife {@code k} of this player's latest throw as "shown,flying,tailx,taily,tailz,struck,far,took", or null. */
    public static String describe(net.minecraft.entity.player.PlayerEntity player,int k,float delta){
        StandClient.Cast best=null;
        for(var c:KnifeClient.SWINGS.values())if(c.combo()==XCharaCombo.KNIVES&&c.caster().equals(player.getUuid()))best=c;
        if(best==null)return null;
        var p=pose(best,k,best.age(delta)/20f);var t=BladeShot.told(best.id(),k);
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%.2f,%.2f,%.2f,%d,%.2f,%d",p==null?0:p.shown(),p!=null&&p.flying()?1:0,
            p==null?0:p.tail().x,p==null?0:p.tail().y,p==null?0:p.tail().z,t==null?-1:t.struck,t==null||Double.isNaN(t.far)?-1:t.far,t!=null&&t.took?1:0);
    }
}
