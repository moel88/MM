package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * Lee So-sang's G as drawn (2.83.0, user request: "이번에는 G스킬 궁극기 시전자만 컷씬이 나오고 범위는 30블록 효과음과 대사는
 * 내가 영상에 보냈고 저거 2개중에 랜덤으로 하나 나오게 하면 돼 컷씬은 저거랑 똑같이 나오게", the user's two videos of it read on
 * a sheet and their answers; then "그리고 저거 거대한 검 떨어지는거임" and "컷씬은 그냥 다른 공간 보여줘서 검 안보이게 하고 박히는
 * 순간 원래 월드에는 박히는 효과만 남게").
 * <p>Everyone sees her raise her free hand, light gathering at its fingertip, sweep it up and draw a stroke of gold in
 * the air, bring it before her face (a cross of light flaring there), lean back with her arms spread and call it down;
 * as it strikes, the ground ahead of her (IsosangRules.G_AHEAD) bursts (MeteorFx.blast: a meteor's landing, bigger),
 * and soon fire leaps up out of the ground round her as dust rolls over everything. The sword itself is never in the world.
 * <p>She alone sees it, as a cutscene taken in a space of its own ({@link GStage}), her own camera's shots of it as the
 * videos have them ({@link #shot}): her hand close in the black, the camera whipping round it as the light sweeps up,
 * along her forearm to the fingertip, her face behind her raised hand under a pale sky (and, in the second video's, her
 * face from the side), up at her from below as a giant golden sword comes down point first out of a storm over her, then
 * from the ground on a dusky plain as it comes down and strikes; and back to the game. Only the lower part of its blade is
 * ever seen, as in the videos: its point, its rim and its carving.
 */
final class FallingSword {
    private FallingSword(){}
    /** How long it is drawn (seconds): until the dust has cleared; and, called off, how long after that what it shows takes to go. */
    static final float LIFE=IsosangRules.G_CLEAR+.4f,GONE=.6f;
    static final Vec3d UP=new Vec3d(0,1,0);
    static final int GOLD=0xffc46a,GOLD_WHITE=0xffe9b8,WHITE=0xffffff,SHARD=0x3d8dff,EYE=0xffdca0;

    // ───────────────────────────── the body ─────────────────────────────

    // (Her free hand does it all, her sword held low at her side: it comes up before her, pointing across to her right;
    // sweeps up to point at the sky over her left; draws a stroke across; comes before her face, the hand at her nose; then
    // she leans back with her arms spread, looking up at the storm; points down at where the sword will strike; watches it
    // and lowers her hand. The body faces the way she faced, as every hit's.)
    static final R6.Pose G_RAISE=w(0,0,-6,2,0,d(-74,10),d(18,16),d(4,-55),d(-90,3),d(-90,-3));
    static final R6.Pose G_SWEEP=w(0,0,-10,-2,0,d(-74,10),d(16,18),d(48,-40),d(-90,3),d(-90,-3)).head(-8,-14,0);
    static final R6.Pose G_POINT=w(0,0,-12,-4,0,d(-74,10),d(16,18),d(54,-44),d(-90,3),d(-90,-3)).head(-10,-20,0);
    static final R6.Pose G_POINT2=w(0,0,-12,-4,0,d(-74,10),d(16,18),d(56,-40),d(-90,3),d(-90,-3)).head(-10,-22,0);
    static final R6.Pose G_STROKE=w(0,0,-4,-2,0,d(-74,10),d(16,18),d(50,22),d(-90,3),d(-90,-3)).head(-2,-12,0);
    // (2.83.1, the videos' face, pull-back and sky shots: her free hand's finger at her lips, her sword arm across her
    // waist under it, standing straight; user request, marked on the sky shot: "머리", "손")
    static final R6.Pose G_SHH=w(0,0,0,0,0,d(-12,-53),d(-55,-35),d(30,46),d(-90,4),d(-90,-4)).head(0,5,0);
    static final R6.Pose G_SHH2=w(0,0,0,1,0,d(-13,-52),d(-56,-34),d(31,45),d(-90,4),d(-90,-4)).head(1,6,1);
    static final R6.Pose G_CALL=w(0,0,0,6,0,d(-60,30),d(-10,40),d(-6,2),d(-90,3),d(-90,-3)).head(0,10,0);
    static final R6.Pose G_CALL2=w(0,0,0,4,0,d(-62,28),d(-6,36),d(-10,4),d(-90,3),d(-90,-3)).head(0,8,0);
    static final R6.Pose G_WATCH=w(0,0,0,2,0,d(-68,16),d(20,10),d(-44,-12),d(-90,3),d(-90,-3)).head(0,4,0);
    static final Anim CLIP=Anim.joints(
        k(0,STANCE),
        k(.16,G_RAISE,Anim.Ease.OUT),
        k(.3,G_RAISE),
        k(.44,G_SWEEP,Anim.Ease.OUT),
        k(.62,G_POINT),
        k(.83,G_POINT2),
        k(.96,G_STROKE,Anim.Ease.OUT),
        k(1.06,G_SHH,Anim.Ease.OUT),
        k(2.6,G_SHH2),
        k(2.8,G_CALL,Anim.Ease.OUT),
        k(3.3,G_CALL2),
        k(3.9,G_WATCH),
        k(IsosangRules.G_HOLD/20f,STANCE)).after(IsosangSlash::grounded);

    /** Her free hand and its fingertip at {@code t} as drawn, standing at {@code feet} facing {@code yaw}. */
    static Vec3d hand(Vec3d feet,float yaw,float t){return KagenoClips.freeHand(feet,yaw,R6.poseOf(CLIP.frame(t)));}
    static Vec3d tip(Vec3d feet,float yaw,float t){
        var p=R6.poseOf(CLIP.frame(t));Vec3d h=KagenoClips.freeHand(feet,yaw,p),s=KagenoClips.freeShoulder(feet,yaw,p);
        Vec3d d=h.subtract(s);return d.lengthSquared()<1e-6?h:h.add(d.normalize().multiply(.13));
    }
    /** Her head's middle at {@code t} as drawn. */
    static Vec3d head(Vec3d feet,float yaw,float t){return KagenoClips.head(feet,yaw,R6.poseOf(CLIP.frame(t)));}

    // ───────────────────────────── where it is ─────────────────────────────

    static Vec3d forward(StandClient.Cast c){return Vec3d.fromPolar(0,c.yaw());}
    static Vec3d right(Vec3d f){return new Vec3d(-f.z,0,f.x);}
    /** Where its sword strikes the ground (the server's word, else as far ahead of where she stood as it strikes). */
    static Vec3d strike(StandClient.Cast c){var u=IsosangClient.ultimate(c.id());return u!=null?u.strike():c.origin().add(forward(c).multiply(IsosangRules.G_AHEAD));}
    /** Which recording and face (0 the first video's, 1 the second's). */
    static int variant(StandClient.Cast c){var u=IsosangClient.ultimate(c.id());return u==null?0:u.variant();}
    /** When it was called off (seconds into it), or NaN. */
    static float off(StandClient.Cast c){return IsosangClient.ultimateEnd(c.id());}

    /** The space each shot is taken in (GStage), by when it is: black round her hand, pale sky behind her face, the storm, dusk; none once the game is back. */
    static int space(StandClient.Cast c,float t){
        if(t<0||t>=IsosangRules.G_BACK||!Float.isNaN(off(c)))return GStage.NONE;
        return t<S_GREY?GStage.BLACK:t<S_SKY?GStage.GREY:t<S_FALL?GStage.STORM:GStage.DUSK;
    }

    // ───────────────────────────── the sword ─────────────────────────────

    /**
     * Its blade's point (blocks: how far up its outline comes to its full width; how round it is: its edges' curve, fitted
     * with the sword's pose to the edges the user drew on the videos' sky shot), and how thick it is.
     */
    static final double POINT=6,ROUND=2.2,THICK=.9;
    /** Its half width at {@code y} blocks up from its point (tools/isosang/make_g_sword.py draws the same outline). */
    static double half(double y){
        double r=IsosangRules.BLADE_WIDTH/2;
        if(y<0)return 0;
        if(y<POINT){double u=y/POINT;return r*(1-Math.pow(1-u,ROUND));}
        return r;
    }
    /** How fast its half width grows up the blade at {@code y} (its outline's slope). */
    static double widening(double y){
        if(y<0||y>=POINT)return 0;
        return IsosangRules.BLADE_WIDTH/2*ROUND*Math.pow(1-y/POINT,ROUND-1)/POINT;
    }
    /**
     * The sword at a moment: its point, its long axis (from its point up its blade), its face's normal, how much of it
     * shows, how hot it glows (0 to 1), how dark it has gone (0 to 1), and how far up its blade it shows (it fades out
     * between {@code fadeFrom} and {@code fadeTo} blocks from its point: in the storm, into its eye).
     */
    record Blade(Vec3d point,Vec3d axis,Vec3d normal,float alpha,float heat,float dark,double fadeFrom,double fadeTo){
        Vec3d across(){return axis.crossProduct(normal);}
        Vec3d at(double x,double y,double z){return point.add(across().multiply(x)).add(axis.multiply(y)).add(normal.multiply(z));}
        /** How much of it shows {@code y} blocks up from its point. */
        float shown(double y){return alpha*(1-Ease.smooth((float)y,(float)fadeFrom,(float)fadeTo));}
    }
    /** {@code a} made square to {@code axis} (unit). */
    static Vec3d square(Vec3d a,Vec3d axis){Vec3d n=a.subtract(axis.multiply(a.dotProduct(axis)));return n.lengthSquared()<1e-8?right(axis).normalize():n.normalize();}

    /** Coming down: how high its point starts (blocks), how it leans (degrees, its top to her left as she faces it), how far it goes into the ground (G_SINK). */
    static final double FALL_FROM=2.2,FALL_LEAN=12;
    /** The times it darkens (seconds) as the game comes back (the videos': bright until 4.35 s, then the whole frame dims). */
    static final float DARK0=4.3f,DARK1=4.83f;
    /** The sword at {@code t} (null while no shot shows it): only in her own cutscene's storm and on its dusky plain. */
    static Blade blade(StandClient.Cast c,float t){
        if(t<S_SKY||t>=IsosangRules.G_BACK)return null;
        float gone=off(c);if(!Float.isNaN(gone))return null;
        if(t<S_FALL){
            // in the storm: the giant sword coming down point first out of its eye, its point where the user marked it and
            // its edges along the lines they drew, nearing as the videos' does; past the hole in the storm's ring its
            // clouds take it (isosang_blade, GStage)
            var k=skyView(c);
            double near=nearing(1.95f)/nearing(t);
            Vec3d point=k.at(SKY_TIP_X[0]+t*(SKY_TIP_X[1]+t*SKY_TIP_X[2]),SKY_TIP_Y[0]+t*(SKY_TIP_Y[1]+t*SKY_TIP_Y[2]),SKY_TIP_DEPTH*near,SKY_FOV);
            Vec3d axis=k.way(SKY_AXIS_UP,SKY_AXIS_RIGHT);
            return new Blade(point,axis,square(k.eye().subtract(point),axis),1,1,0,99,99);
        }
        // coming down point first where it strikes, and stuck there
        Vec3d s=strike(c),f=forward(c),r=right(f);
        double lean=Math.toRadians(FALL_LEAN);
        Vec3d axis=UP.multiply(Math.cos(lean)).subtract(r.multiply(Math.sin(lean))).normalize();
        Vec3d normal=square(f.negate(),axis);
        if(t<IsosangRules.G_STRIKE){
            float u=(t-S_FALL)/(IsosangRules.G_STRIKE-S_FALL);double q=Math.pow(u,1.35);
            return new Blade(s.add(axis.multiply(FALL_FROM*(1-q)-IsosangRules.G_SINK*q)),axis,normal,1,1,0,99,99);
        }
        double after=t-IsosangRules.G_STRIKE;
        double recoil=.18*Math.exp(-after/.08)*Math.sin(after*55);
        float heat=1-.5f*Ease.smooth(t,IsosangRules.G_STRIKE+.1f,DARK0)-.5f*Ease.smooth(t,DARK0,DARK1);
        float dark=.45f*Ease.smooth(t,DARK0,DARK1);
        return new Blade(s.add(axis.multiply(-IsosangRules.G_SINK+recoil)),axis,normal,1,Math.max(0,heat),dark,99,99);
    }
    /** The storm's eye as the sky shot sees it (the way to its middle from the camera). */
    static Vec3d stormEye(StandClient.Cast c,float t){return ring(c);}

    // ───────────────────────────── the effect layer ─────────────────────────────

    /** When she called it, what it is drawn from: the cast's own place (she is held there). */
    static Vec3d feet(Fx c){return c.origin;}
    /** Whether this G is the one the local player's camera is in now (what only her cutscene shows is drawn only then). */
    static boolean shown(Fx c){var own=cinematic();return own!=null&&own.id().equals(c.cast.id())&&c.sec<IsosangRules.G_BACK;}
    /**
     * The light at her fingertip, the stroke it draws, the cross before her face (everyone sees her hand); in her cutscene
     * the storm's eye, its lightning and the sword's edges and wind; the strike's flash where it struck (everyone).
     */
    static void additive(Fx c,EffectMesh m){
        float t=c.sec;Vec3d feet=feet(c);float yaw=c.cast.yaw();
        boolean gone=!Float.isNaN(off(c.cast));
        if(!gone)fingertip(c,m,t,feet,yaw);
        if(shown(c)){
            sky(c,m,t);
            var bl=blade(c.cast,t);
            if(bl!=null)edges(c,m,t,bl);
        }
        // the strike: a flash where its point bites
        float ks=FxLib.kick(t,IsosangRules.G_STRIKE,.18f);
        if(ks>.01f&&!gone){Vec3d s=strike(c.cast).add(0,1,0);m.softDisc(s,6,GOLD,.42f*ks);m.softDisc(s,2.4,GOLD_WHITE,.5f*ks);m.star(s,3.4,WHITE,.6f*ks);}
        if(!gone){rays(c,m,t);foot(c,m,t);}
    }
    /** The fire at its foot (3.1 to 4.4 s): a flickering orange glow over the ground where it bites, hot at first. */
    static void foot(Fx c,EffectMesh m,float t){
        float a=Ease.window(t,IsosangRules.G_STRIKE,DARK1,.05f,.6f);if(a<=.01f)return;
        Vec3d s=strike(c.cast).add(0,.5,0);
        float flick=.85f+.15f*(float)Math.sin(t*37)*(float)Math.sin(t*23+1);
        float hot=1-Ease.smooth(t,IsosangRules.G_STRIKE,DARK0);
        m.softDisc(s,4.5,0xff8a30,.4f*a*flick);m.softDisc(s.add(0,.3,0),1.8,0xffd080,(.3f+.4f*hot)*a*flick);
    }
    /** Sparks shooting out of where it bites as it strikes (0.6 s): long orange streaks, out and up, slowing and falling. */
    static void rays(Fx c,EffectMesh m,float t){
        float after=t-IsosangRules.G_STRIKE;if(after<0||after>.6f)return;
        Vec3d s=strike(c.cast);
        for(int i=0;i<30;i++){
            double ang=Ease.hash(i*29+3)*Math.PI*2,up=.35+.6*Ease.hash(i*31+5),sp=10+14*Ease.hash(i*37+7);
            Vec3d dir=new Vec3d(Math.cos(ang)*(1-up*.5),up,Math.sin(ang)*(1-up*.5)).normalize();
            double life=.3+.3*Ease.hash(i*41+9);if(after>life)continue;
            double a=after,head=sp*a*(1-a*.6),tail=Math.max(0,head-2.2-3*Ease.hash(i*43+1));
            Vec3d o=s.add(0,.4,0),p=o.add(dir.multiply(head)).add(0,-2*a*a,0),q=o.add(dir.multiply(tail)).add(0,-2*Math.max(0,a-.06)*Math.max(0,a-.06),0);
            float k=(float)(1-a/life);
            m.softLine(q,p,.09,0xff9a40,.55f*k);m.line(q,p,.025,0xfff0c0,.9f*k);
        }
    }
    /** The sword's edges catching the light, and the wind it tears through as it comes down. */
    static void edges(Fx c,EffectMesh m,float t,Blade bl){
        float glow=bl.heat()*(1-bl.dark());
        if(glow>.02f){
            double z=THICK*.5;double[] ys=GStage.SLICES;
            for(int s=-1;s<=1;s+=2)for(int i=0;i+1<ys.length;i++){
                float a=Math.min(bl.shown(ys[i]),bl.shown(ys[i+1]))*glow;if(a<=.01f)continue;
                Vec3d p=bl.at(s*half(ys[i]),ys[i],z),q=bl.at(s*half(ys[i+1]),ys[i+1],z);
                m.softLine(p,q,.35,GOLD,.16f*a);m.line(p,q,.03,GOLD_WHITE,.36f*a);
            }
        }
        if(t>=S_FALL&&t<IsosangRules.G_STRIKE+.1f){
            float w=Ease.smooth(t,S_FALL,S_FALL+.06f)*(1-Ease.smooth(t,IsosangRules.G_STRIKE,IsosangRules.G_STRIKE+.1f));
            // (the videos': white streaks bending round its edges as it comes down, each a long arc from its point up its sides)
            for(int i=0;i<18;i++){
                int side=i%2==0?1:-1;double y0=.6+(i/2)*.9,off=.3+.5*Ease.hash(i*7+3),len=5+4*Ease.hash(i*7+5);
                Vec3d prev=null;
                for(int k=0;k<=8;k++){
                    double y=y0+len*k/8,bend=off*(1+.6*Math.sin(k/8.0*Math.PI));
                    Vec3d p=bl.at(side*(half(y)+bend),y,(i%3-1)*THICK*.8);
                    if(prev!=null){float a=(float)Math.sin(k/8.0*Math.PI);m.softLine(prev,p,.08,0xeaf4ff,.45f*w*a);m.line(prev,p,.015,WHITE,.6f*w*a);}
                    prev=p;
                }
            }
        }
    }
    /**
     * The light at her fingertip (0.15 to 1.5 s): it gathers, a beam shoots off it as her hand sweeps up, it glows as she
     * points at the sky, draws a stroke of gold as her hand crosses, and flares into a cross before her face; a few blue
     * shards spin round her hand. Small: her cutscene's camera is a hand's length from it.
     */
    static void fingertip(Fx c,EffectMesh m,float t,Vec3d feet,float yaw){
        if(t<.15f||t>1.5f)return;
        Vec3d tip=tip(feet,yaw,t);
        float on=Ease.window(t,.17f,1.42f,.06f,.12f);
        float pulse=.85f+.15f*(float)Math.sin(t*31);
        // (the videos': a golden radiance round the fingertip that grows as the light gathers, a hot core, and a thin
        // streak through it level, like a lens's flare; it is light, facing the eye)
        // (smaller once it is before her face, a hand's length from the camera there: its light must not white her face out)
        float grow=(.15f+.85f*Ease.smooth(t,.33f,.6f))*(1-.65f*Ease.smooth(t,S_FACE-.03f,S_FACE+.03f));
        m.softDisc(tip,.3*grow,GOLD,.45f*on);m.softDisc(tip,.12*grow,GOLD_WHITE,.6f*on);m.softDisc(tip,.045*grow,WHITE,.9f*on);
        m.star(tip,(.13+.04*pulse)*grow*on,GOLD_WHITE,.95f*on);
        Vec3d look=tip.subtract(c.camera);look=look.lengthSquared()<1e-6?c.f:look.normalize();
        Vec3d level=look.crossProduct(UP);level=level.lengthSquared()<1e-6?c.right:level.normalize();
        m.softLine(tip.subtract(level.multiply(.9*grow)),tip.add(level.multiply(.9*grow)),.025,GOLD,.3f*on*grow);
        // the beam as her hand sweeps up, along the way the finger points
        float beam=Ease.window(t,.28f,.55f,.03f,.1f);
        if(beam>.01f){
            Vec3d h=hand(feet,yaw,t),d=tip.subtract(h);d=d.lengthSquared()<1e-6?UP:d.normalize();
            Vec3d end=tip.add(d.multiply(3.2));
            m.softLine(tip,end,.16,GOLD,.45f*beam);m.softLine(tip,end,.06,GOLD_WHITE,.6f*beam);m.line(tip,end,.02,WHITE,.85f*beam);
        }
        // the stroke: where the fingertip went from 0.83 s, left hanging in the air, fading
        if(t>.83f){
            float fade=1-Ease.smooth(t,1.02f,1.3f);
            if(fade>.01f){
                int n=14;Vec3d prev=null;float to=Math.min(t,.97f);
                for(int i=0;i<=n;i++){
                    float s=.83f+(to-.83f)*i/n;Vec3d p=tip(feet,yaw,s);
                    if(prev!=null){float w=i/(float)n;m.softLine(prev,p,.14+.1*w,GOLD,.5f*fade);m.softLine(prev,p,.05+.03*w,GOLD_WHITE,.6f*fade);m.line(prev,p,.014+.008*w,WHITE,.85f*fade);}
                    prev=p;
                }
            }
        }
        // the cross before her face (1.0 to 1.4 s): a streak right across the view and a shorter one up and down, a burst where they cross
        float cross=FxLib.kick(t,1.02f,.12f);
        if(cross>.01f){
            Vec3d right=level,up=right.crossProduct(look).normalize();
            m.softLine(tip.subtract(right.multiply(2.8)),tip.add(right.multiply(2.8)),.025,GOLD,.5f*cross);
            m.line(tip.subtract(right.multiply(2.4)),tip.add(right.multiply(2.4)),.005,WHITE,.85f*cross);
            m.softLine(tip.subtract(up.multiply(.8)),tip.add(up.multiply(.8)),.02,GOLD,.4f*cross);
            m.line(tip.subtract(up.multiply(.6)),tip.add(up.multiply(.6)),.004,WHITE,.75f*cross);
            m.star(tip,.18*cross,WHITE,.9f*cross);m.softDisc(tip,.16,GOLD_WHITE,.5f*cross);m.softDisc(tip,.4,GOLD,.16f*cross);
        }
        // blue shards spinning round her hand
        float sh=Ease.window(t,.38f,1.02f,.08f,.12f);
        if(sh>.01f){
            Vec3d h=hand(feet,yaw,t);
            for(int i=0;i<6;i++){
                double a=t*(2.2+i*.3)+i*1.05,r=.42+.16*Ease.hash(i*13+1),y=-.1+.36*Ease.hash(i*17+2);
                Vec3d p=h.add(Math.cos(a)*r,y+.06*Math.sin(t*5+i),Math.sin(a)*r);
                Vec3d u=new Vec3d(Math.cos(a+1.3),.4,Math.sin(a+1.3)).normalize().multiply(.13),v=new Vec3d(-Math.sin(a),.2,Math.cos(a)).normalize().multiply(.05);
                m.triangle(p.add(u),p.subtract(u).add(v),p.subtract(u).subtract(v),SHARD,.85f*sh);
            }
        }
    }
    /** Lightning flickering in the storm's clouds round its eye's ring (the videos' bolts at its right), a few quick bolts. */
    static void sky(Fx c,EffectMesh m,float t){
        if(t<S_SKY||t>=S_FALL)return;
        var k=skyView(c.cast);Vec3d e=ring(c.cast);
        Vec3d b1=e.crossProduct(UP).normalize(),b2=b1.crossProduct(e).normalize();
        for(int n=0;n<5;n++){
            float at=S_SKY+.18f+n*.2f+.06f*(float)Ease.hash(n*31+7);
            float a=Ease.window(t,at,at+.09f,.01f,.05f);if(a<=.01f)continue;
            // out in the clouds right of the ring, 40 blocks off
            double ang=-.9+1.4*Ease.hash(n*19+5),r0=Math.toRadians(HOLE+10+14*Ease.hash(n*23+7));
            Vec3d p=null;
            for(int s=0;s<6;s++){
                double ang2=ang+(Ease.hash(n*41+s)-.5)*.25,r1=r0+Math.toRadians(3.5*s);
                Vec3d d=e.multiply(Math.cos(r1)).add(b1.multiply(Math.sin(r1)*Math.cos(ang2))).add(b2.multiply(Math.sin(r1)*Math.sin(ang2)));
                Vec3d q=k.eye().add(d.multiply(40));
                if(p!=null){m.line(p,q,.12,0xe8f2ff,.85f*a);m.softLine(p,q,.7,0x9cc4ff,.3f*a);}
                p=q;
            }
        }
    }
    static void alpha(Fx c,EffectMesh m){}
    static void overlay(Fx c,EffectMesh m){}
    /** Lights: the fingertip, the sword's glow over the ground (her cutscene), the strike, the fire leaping up; afterglow cut short in the quick parts. */
    static void frame(Fx c,FxFrame f){
        float t=c.sec;Vec3d feet=feet(c);float yaw=c.cast.yaw();boolean gone=!Float.isNaN(off(c.cast));
        float on=Ease.window(t,.17f,1.42f,.06f,.12f);
        if(on>.01f&&!gone)f.light(tip(feet,yaw,t),0xffd08a,3,.6f*on);
        if(shown(c)){
            var bl=blade(c.cast,t);
            if(bl!=null){float glow=bl.heat()*bl.alpha()*(1-bl.dark());if(glow>.02f&&t>=S_FALL)f.light(bl.at(0,2,0),0xffb060,16,.8f*glow);}
        }
        float ks=FxLib.kick(t,IsosangRules.G_STRIKE,.5f);
        if(ks>.01f&&!gone)f.light(strike(c.cast).add(0,2,0),0xffa050,20,1.3f*ks);
        float ke=FxLib.kick(t,IsosangRules.G_ERUPT,.6f);
        if(ke>.01f&&!gone)f.light(feet.add(0,1.5,0),0xff9a40,16,1f*ke);
        if((t>.28f&&t<1.1f)||(t>S_FALL&&t<IsosangRules.G_STRIKE+.3f))f.afterglow(.08f);
    }
    /** Shake as it strikes and as the fire leaps up (in over two frames). */
    static float shake(StandClient.Cast cast,float sec){
        if(!Float.isNaN(off(cast))&&off(cast)<IsosangRules.G_STRIKE)return 0;
        return Math.max(2.6f*bump(sec,IsosangRules.G_STRIKE,.25f),1.2f*bump(sec,IsosangRules.G_ERUPT,.2f));
    }

    // ───────────────────────────── the news of it ─────────────────────────────

    /** G heard of (its swing begun on this client): where it strikes and the fire after it are drawn with a meteor's landing (MeteorFx). */
    static void begin(StandClient.Cast c){
        int seed=IsosangRules.formationSeed(c.id());
        dev.sangwoo.celestialfall.client.meteor.MeteorFx.blast(c.id(),strike(c),c.origin(),seed,c.age(0)-IsosangRules.G_IMPACT);
    }
    /** G called off before it struck: no landing. */
    static void calledOff(java.util.UUID id){dev.sangwoo.celestialfall.client.meteor.MeteorFx.unblast(id);}

    // ───────────────────────────── her cutscene ─────────────────────────────

    /** Her own G whose cutscene her camera is in now (null for none): until the game's camera has it back. */
    static StandClient.Cast cinematic(){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||!mc.player.isAlive()||!MapClient.enabled()||!CelestialFall.CONFIG.cinematicCamera)return null;
        var c=IsosangClient.ownUltimate();if(c==null)return null;
        float t=c.age(0)/20f;if(t<0||t>=IsosangRules.G_BACK||!Float.isNaN(off(c)))return null;
        return c;
    }
    /** A shot: where the eye is, what it looks at, its lens (degrees) and its roll. */
    record Shot(Vec3d eye,Vec3d look,double fov,float roll){}
    /** The shots' cuts (seconds): the hand, the whip round it, along the forearm, the stroke, the face, the sky, the sword coming down. */
    /** When her hand is up (seconds: G_RAISE's key). */
    static final float RAISED=.16f;
    static final float S_WHIP=.3f,S_ARM=.45f,S_STROKE=.83f,S_FACE=IsosangRules.G_FACE,S_SIDE=1.42f,S_SKY=IsosangRules.G_SKY,S_FALL=IsosangRules.G_FALL;
    /** The pale sky behind her face (seconds: the videos' 1.067 s frame; before it, her face in the dark as her hand comes down). */
    static final float S_GREY=1.058f;
    /** Whether her whole body is drawn now (from the cut to her face; in the shots of her hand, only her free arm). */
    static boolean wholeBody(float t){return t>=S_FACE;}
    /** A camera's frame: its eye, the way it looks, its right and its up. */
    record Frame(Vec3d eye,Vec3d f,Vec3d r,Vec3d u){
        /** The frame turned as the game turns its view by {@code roll} degrees (GameRendererMixin: the picture turns the other way). */
        Frame rolled(double roll){
            double a=Math.toRadians(-roll);
            return new Frame(eye,f,r.multiply(Math.cos(a)).add(u.multiply(Math.sin(a))),r.multiply(-Math.sin(a)).add(u.multiply(Math.cos(a))));
        }
        /** The point seen at {@code sx}, {@code sy} of the videos' frame (0 to 1 across and down), {@code depth} blocks deep, through a lens of {@code fov} degrees. */
        Vec3d at(double sx,double sy,double depth,double fov){
            double t=Math.tan(Math.toRadians(fov/2));
            return eye.add(f.add(r.multiply((2*sx-1)*t*ASPECT)).add(u.multiply((1-2*sy)*t)).multiply(depth));
        }
        /** The way {@code elevation} degrees up and {@code azimuth} degrees right of where it looks. */
        Vec3d way(double elevation,double azimuth){
            double e=Math.toRadians(elevation),z=Math.toRadians(azimuth);
            return f.multiply(Math.cos(e)*Math.cos(z)).add(r.multiply(Math.cos(e)*Math.sin(z))).add(u.multiply(Math.sin(e))).normalize();
        }
    }
    /** The videos' frame (1920 by 886): her cutscene shows it between black bars where the window is narrower (CinematicHud). */
    public static final double ASPECT=1920/886.0;
    /** How much of the window's height her cutscene's frame takes (1 where the window is as wide as the videos' or wider). */
    public static double frameHeight(){
        var w=MinecraftClient.getInstance().getWindow();if(w==null)return 1;
        return Math.min(1,w.getFramebufferWidth()/(double)Math.max(1,w.getFramebufferHeight())/ASPECT);
    }
    /** The window's lens for a shot's lens (degrees, up and down its frame): the frame's sides where the videos' are. */
    static double windowFov(double fov){return Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(fov/2))/frameHeight()));}
    /**
     * The sky shot (2.83.1, fitted to the user's video frame by frame, tools: the fit drew her body over its frames; user
     * request: "카메라를 더 올려다 봐야한다고", "오른쪽으로 있어야해 살짝 캐릭터가"): low before her, its eye half a block
     * under her feet 1.1 blocks before her, looking 60 degrees up and 42 to her left, turned 27.6 degrees, its lens wide
     * (84 degrees up and down the videos' frame): her head at .41 across and .52 down the frame (a little right of the
     * videos'), her hips at .40 and .70, her legs out of its bottom, wide (still all through the shot, as the videos' camera is).
     */
    static final double SKY_EYE_FW=1.114,SKY_EYE_SIDE=-.023,SKY_EYE_UP=-.326,SKY_PITCH=60,SKY_PAN=41.92,SKY_ROLL=27.61,SKY_FOV=84.42;
    static Frame skyFrame(StandClient.Cast c){
        Vec3d feet=c.origin(),f=forward(c),r=right(f);
        Vec3d eye=feet.add(f.multiply(SKY_EYE_FW)).subtract(r.multiply(SKY_EYE_SIDE)).add(0,SKY_EYE_UP,0);
        double pan=Math.toRadians(SKY_PAN),pitch=Math.toRadians(SKY_PITCH);
        Vec3d level=f.negate().multiply(Math.cos(pan)).add(r.negate().multiply(Math.sin(pan))).normalize();
        Vec3d look=level.multiply(Math.cos(pitch)).add(UP.multiply(Math.sin(pitch))).normalize();
        Vec3d cr=look.crossProduct(UP).normalize(),cu=cr.crossProduct(look).normalize();
        return new Frame(eye,look,cr,cu);
    }
    /** The sky shot's frame as its picture shows it (turned SKY_ROLL): what the slab and the storm are placed in. */
    static Frame skyView(StandClient.Cast c){return skyFrame(c).rolled(SKY_ROLL);}
    /**
     * The sword in the storm (2.83.1, user request: the edges they drew on the sky shot, "검 그거 아니야"; "이게 ㅈㄴ 큰 검이
     * 내려오는건데 구름에 가려져서 칼 끝 부분밖에 안 보이는거야"; "저거 아직 그림이지? 실제 모델 아니지?"): the giant sword itself,
     * its point at the corner they marked (.648 across and .653 down at 1.95 s; the corner tracked on the video over the
     * shot: across and down as quadratics in its seconds), 4.23 blocks from the camera then, its blade going up from its
     * point into the storm's eye, its face to the camera: its pose and its point's shape fitted to the lines they drew
     * (within .007 of every point of them); nearing as the videos' sword does (its picture grows .95 to 1.07 times over the
     * shot: NEARING, a quadratic in its seconds, within .015 of every frame measured).
     */
    static final double SKY_TIP_DEPTH=4.23,SKY_AXIS_UP=44.92,SKY_AXIS_RIGHT=-50.63;
    static final double[] SKY_TIP_X={.5989945,.01809719,.00359858},SKY_TIP_Y={.66175,-.06177856,.0290817};
    static final double[] NEARING={.70791,.19574,-.02285};
    static double nearing(float t){return NEARING[0]+t*(NEARING[1]+t*NEARING[2]);}
    /**
     * The storm's eye: where its middle is in the videos' frame (the ring of clouds round the slab, fitted) and how wide the
     * hole in the ring is round it (degrees through the sky shot's lens: the ring's clouds take the slab past it).
     */
    static final double RING_X=.5519,RING_Y=.4486,HOLE=29.5;
    static Vec3d ring(StandClient.Cast c){var k=skyView(c);return k.at(RING_X,RING_Y,1,SKY_FOV).subtract(k.eye()).normalize();}
    /**
     * The way the sword goes away into the storm from the ring's middle (square to it: up and to the left in the frame, as
     * far up as across in its pixels): the clouds take it only on that side, its point and its near edges clear of them
     * (user request: "검 끝이 구름에 묻히면 안되지").
     */
    static Vec3d farSide(StandClient.Cast c){
        var k=skyView(c);Vec3d e=ring(c),p=k.at(RING_X-.03,RING_Y-.065,1,SKY_FOV).subtract(k.eye()).normalize();
        return p.subtract(e.multiply(p.dotProduct(e))).normalize();
    }
    /** Coming down: from the ground before it (between her and it), looking up at it; pushing in a little after it strikes. */
    static final double FALL_BACK=8.6,FALL_SIDE=.45,FALL_UP=1.2,FALL_LOOK=2.8,FALL_PUSH=.3,FALL_FOV=58;
    /** Her camera's shot at {@code t} of the cast {@code c}. */
    static Shot shot(StandClient.Cast c,float t){
        Vec3d feet=c.origin();float yaw=c.yaw();Vec3d f=forward(c),r=right(f);
        if(t<S_FACE){
            Vec3d h=hand(feet,yaw,t),tip=tip(feet,yaw,t);
            // the way her finger points (her arm's line), and square to it level: the camera's sides of her hand
            Vec3d d=tip.subtract(h);d=d.lengthSquared()<1e-6?f:d.normalize();
            Vec3d across=d.crossProduct(UP);across=across.lengthSquared()<1e-6?r:across.normalize();
            // her hand side on, a little over it, her finger pointing to the left of the frame; then from under her
            // forearm, behind her hand on her arm's line, looking along it to her fingertip
            Vec3d side=across.multiply(-.72).add(0,.14,0),under=under(f,r);
            if(t<S_WHIP){
                // held on where her hand comes up to (0.16 s) as it comes up into the frame
                Vec3d h0=hand(feet,yaw,RAISED),d0=tip(feet,yaw,RAISED).subtract(h0).normalize(),a0=d0.crossProduct(UP).normalize();
                return new Shot(h0.add(a0.multiply(-.7)).add(0,.16,0).subtract(d0.multiply(.18+.04*t/S_WHIP)),h0.subtract(d0.multiply(.16)),44,0);
            }
            if(t<S_ARM){
                // the whip round her hand as it sweeps up: turned by the eased time, not by her arm (which starts fast)
                float u=Ease.smooth(t,S_WHIP,S_ARM);
                Vec3d h0=hand(feet,yaw,RAISED),d0=tip(feet,yaw,RAISED).subtract(h0).normalize(),a0=d0.crossProduct(UP).normalize();
                Vec3d h1=hand(feet,yaw,S_ARM),d1=tip(feet,yaw,S_ARM).subtract(h1).normalize(),a1=d1.crossProduct(UP).normalize();
                Vec3d from=a0.multiply(-.7).add(0,.16,0).subtract(d0.multiply(.22)),to=under(f,r);
                return new Shot(h.add(slerp(from,to,u)),h.subtract(d0.multiply(.16)).lerp(h.subtract(d1.multiply(.12)),u),44+8*u,-30*u);
            }
            if(t<S_STROKE){float u=(t-S_ARM)/(S_STROKE-S_ARM);return new Shot(h.add(under.multiply(1-.12*u)),h.subtract(d.multiply(.12)),52,-30+8*u);}
            float u=Ease.smooth(t,S_STROKE,S_FACE);Vec3d front=f.multiply(.72).subtract(r.multiply(.2)).add(0,-.24,0);
            return new Shot(h.add(slerp(under.multiply(.88),front,u)),h.subtract(d.multiply(.12)).lerp(tip,u),52,-22*(1-u));
        }
        if(t<S_FALL){
            var k=skyFrame(c);Shot sky=new Shot(k.eye(),k.eye().add(k.f()),SKY_FOV,(float)SKY_ROLL);
            if(t>=S_PULLED)return sky;
            Shot face=face(c,Math.min(t,S_PULL));
            if(t<S_PULL)return face;
            // the pull-back (the videos': fast, out and down from her face to the sky shot)
            float u=Ease.smooth(t,S_PULL,S_PULLED);
            return new Shot(face.eye().lerp(sky.eye(),u),face.look().lerp(sky.look(),u),face.fov()+(sky.fov()-face.fov())*u,face.roll()+(sky.roll()-face.roll())*u);
        }
        Vec3d s=strike(c);
        float u=Math.max(0,Math.min(1,(t-IsosangRules.G_STRIKE)/(IsosangRules.G_BACK-IsosangRules.G_STRIKE)));
        Vec3d eye=s.subtract(f.multiply(FALL_BACK-FALL_PUSH*Ease.smooth(u,0,1))).add(r.multiply(FALL_SIDE)).add(0,FALL_UP,0);
        return new Shot(eye,s.add(0,FALL_LOOK,0),FALL_FOV,0);
    }
    /**
     * The pull-back from her face to the sky shot (seconds, the videos': the storm behind her face from 1.517 s, the
     * camera out and down to the sky shot from 1.567 to 1.617 s, three frames), and the one frame of the sword alone in
     * the storm at its end (the videos' 2.717 s: she is gone from it) before the cut to it coming down.
     */
    static final float S_PULL=1.567f,S_PULLED=1.617f,S_ALONE=2.708f;
    /** Whether the sky shot shows the sword alone now (she is not drawn: the videos' frame before the cut). */
    static boolean alone(float t){return t>=S_ALONE&&t<S_FALL;}
    /**
     * The face shot (2.83.1, fitted to the videos' frames: her eyes found on each): square before her face, level, its lens
     * 48 degrees up and down the frame, as near as makes her eyes as far apart as the videos' (.135 of the frame's width:
     * the player model's eyes are a quarter block apart); where her eyes' middle is in the frame at each moment (FACE_AT:
     * seconds, across, down), the camera moving with it (slowly, then quickly down before the pull-back, a little shake).
     */
    static final double FACE_FOV=48,FACE_EYES=.135,EYE_SPACING=.25,EYE_DROP=.031;
    static final double[][] FACE_AT={{1.008,.470,.300},{1.067,.483,.3065},{1.25,.4935,.3255},{1.45,.5015,.3345},{1.483,.5055,.289},
        {1.5,.5085,.2425},{1.517,.508,.227},{1.533,.508,.247},{1.55,.5075,.257},{1.567,.5135,.2335}};
    /** Where her eyes' middle is in the face shot's frame at {@code t} (across, down): eased between the videos' frames. */
    static double[] faceAt(float t){
        double[][] k=FACE_AT;
        if(t<=k[0][0])return new double[]{k[0][1],k[0][2]};
        for(int i=0;i+1<k.length;i++)if(t<k[i+1][0]){
            double u=Ease.smooth((float)((t-k[i][0])/(k[i+1][0]-k[i][0])),0,1);
            return new double[]{k[i][1]+(k[i+1][1]-k[i][1])*u,k[i][2]+(k[i+1][2]-k[i][2])*u};
        }
        var e=k[k.length-1];return new double[]{e[1],e[2]};
    }
    /** The face shot at {@code t}: before her face, her finger at her lips. */
    static Shot face(StandClient.Cast c,float t){
        Vec3d feet=c.origin();float yaw=c.yaw();Vec3d f=forward(c),r=right(f);Vec3d head=head(feet,yaw,t);
        if(t>=S_SIDE&&variant(c)==1)return new Shot(head.add(f.multiply(.74)).subtract(r.multiply(.34)).add(0,-.02,0),head,46,0);
        // the frame at her face: as wide as puts her eyes FACE_EYES of it apart
        double wide=EYE_SPACING/FACE_EYES,high=wide/ASPECT,back=high/2/Math.tan(Math.toRadians(FACE_FOV/2));
        Vec3d eyes=head.add(f.multiply(.25)).add(0,-EYE_DROP,0);
        Vec3d look=f.negate(),cr=look.crossProduct(UP).normalize();
        double[] at=faceAt(t);
        Vec3d eye=eyes.add(f.multiply(back)).subtract(cr.multiply((at[0]-.5)*wide)).add(0,(at[1]-.5)*high,0);
        return new Shot(eye,eye.add(look),FACE_FOV,0);
    }
    /** Before her and below her hand ({@code f} the way she faces, {@code r} her right): her raised forearm runs from the bottom left of the frame to her hand at its top right, as the videos'. */
    static Vec3d under(Vec3d f,Vec3d r){return f.multiply(.78).add(r.multiply(.12)).add(0,-.42,0);}
    static Vec3d slerp(Vec3d a,Vec3d b,double u){
        double la=a.length(),lb=b.length();Vec3d na=a.multiply(1/la),nb=b.multiply(1/lb);
        double dot=MathHelper.clamp(na.dotProduct(nb),-1,1),ang=Math.acos(dot);
        Vec3d d=ang<1e-4?na.lerp(nb,u):na.multiply(Math.sin((1-u)*ang)/Math.sin(ang)).add(nb.multiply(Math.sin(u*ang)/Math.sin(ang)));
        return d.normalize().multiply(la+(lb-la)*u);
    }
    /** The jolt of the strike on her shot of it (blocks: the eye shaken up and across, coming in over two frames). */
    static double jolt(float t){return .22*bump(t,IsosangRules.G_STRIKE,.22f);}
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){
        var c=cinematic();if(c==null)return null;
        float t=c.age(delta)/20f;var s=shot(c,t);var mc=MinecraftClient.getInstance();
        // in its own space no block stands between (the world is not drawn there); in the world, kept out of the walls
        Vec3d eye=GStage.supported()?s.eye():clear(mc,s.look(),s.eye());
        double j=jolt(t);if(j>1e-4)eye=eye.add(0,j*Math.sin(t*61),j*.6*Math.cos(t*53));
        Vec3d d=s.look().subtract(eye);if(d.lengthSquared()<1e-6)d=forward(c);d=d.normalize();
        float sy=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),sp=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));
        return new CinematicDirector.Pose(eye,sy,sp,s.roll());
    }
    public static double fov(double vanilla,float delta){
        var c=cinematic();if(c==null)return vanilla;
        float t=c.age(delta)/20f;return windowFov(shot(c,t).fov()-8*bump(t,IsosangRules.G_STRIKE,.2f));
    }
    public static float roll(float delta){
        var c=cinematic();if(c==null)return 0;
        float t=c.age(delta)/20f;return shot(c,t).roll()+(float)(3*bump(t,IsosangRules.G_STRIKE,.18f)*Math.sin(t*47));
    }
    /** Keeps an eye out of the walls: pulled in toward what it looks at where a block stands between. */
    static Vec3d clear(MinecraftClient mc,Vec3d look,Vec3d eye){
        if(mc.world==null||mc.player==null)return eye;
        var hit=mc.world.raycast(new RaycastContext(look,eye,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        if(hit.getType()==HitResult.Type.MISS||hit.isInsideBlock()||hit.getPos().squaredDistanceTo(look)<.04)return eye;
        Vec3d d=eye.subtract(look);double len=d.length();if(len<1e-4)return eye;
        return look.add(d.multiply(Math.max(.3,look.distanceTo(hit.getPos())-.3)/len));
    }
    /**
     * How dark the world is for her camera now (0 to 1): a moment of black as the game comes back, before the fire leaps
     * up, as the videos have it; and where the world cannot be hidden (a shader pack's mod), black round her hand through
     * the first shots too (the fog closing in to a couple of blocks, the sky gone). Only her own G's, only her camera's.
     */
    /**
     * How thick the dust is round this client's camera now (0 to 1): as the fire leaps up out of the ground round her, the
     * dust rolls over everything near and clears in about a second and a half (the videos' game is a beige haze); for
     * anyone within 35 blocks of it (whole within 15), its user or not.
     */
    public static float dust(float delta){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.gameRenderer==null||!MapClient.enabled())return 0;
        Vec3d cam=mc.gameRenderer.getCamera().getPos();float most=0;
        for(var c:IsosangClient.SWINGS.values()){
            if(c.combo()!=IsosangRules.ULTIMATE||!Float.isNaN(off(c)))continue;
            float t=c.age(delta)/20f;
            float a=Ease.window(t,IsosangRules.G_ERUPT+.05f,IsosangRules.G_CLEAR+.3f,.3f,.9f);if(a<=0)continue;
            double far=cam.distanceTo(c.origin().lerp(strike(c),.45));
            most=Math.max(most,a*(1-Ease.smooth((float)far,15,35)));
        }
        return most;
    }
    /**
     * How far her cutscene's whole frame has dimmed (0 to 1) as it ends: the videos' frame (sky, ground and sword alike)
     * goes dim from 4.3 s to its cut, the game's view coming back on it (CinematicHud draws it over the frame).
     */
    public static float dim(float delta){
        var c=cinematic();if(c==null)return 0;
        float t=c.age(delta)/20f;return .62f*Ease.smooth(t,DARK0,IsosangRules.G_BACK);
    }
    public static float dark(float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled()||!CelestialFall.CONFIG.cinematicCamera)return 0;
        var c=IsosangClient.ownUltimate();if(c==null)return 0;
        float t=c.age(delta)/20f;
        if(!Float.isNaN(off(c)))return 0;
        float back=.55f*Ease.window(t,IsosangRules.G_BACK+.04f,IsosangRules.G_ERUPT+.06f,.04f,.08f);
        return GStage.supported()?back:Math.max(Ease.window(t,0,S_FACE,.03f,.04f),back);
    }
}
