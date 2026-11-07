package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.kageno.R6;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;
import static dev.sangwoo.celestialfall.isosang.IsosangRules.*;

/**
 * Lee So-sang's T as drawn (2.96.0, user request: "이번에는 T스킬 만들자 영상처럼 일단 T를 누르면 평타 6검이 날아가는 동시에
 * 빠르게 바라보는 상대에게 돌진 베어내고 적에게 3 참격으로 베이는 동시에 다른 검으로 넘어가는 스킬이야", the user's video of it
 * (e6830cf0, 60 fps, used twice) read on a sheet and confirmed). The six swords of H's basic attack fan out behind her
 * and are thrown (SwordVolley, on T's clock) as she coils on her sword and dashes straight through the foe she looks at:
 * her body gone into a streak of violet light, a white-hot line laid along her way with a thread of ink, a burst on the
 * foe as she cuts it in passing; down a few steps beyond it in a low crouch, her sword swept through; then she is the orb
 * of Z's flight on to the sword farthest from it (SwordStep, the formation's flights) while the swords come down on it
 * one after another and three slashes cross on it, each drawn on in a breath, white at its heart, blue round it and an
 * edge of ink, a burst as it cuts, thinning and gone. She lands on the far sword and turns back to the formation's way.
 * <p>Every effect is a function of the swing's clock and of where the foe is drawn (the swords and the slashes follow it).
 */
final class SwordLunge {
    private SwordLunge(){}
    /** How long T is drawn for (seconds, IsosangClient.life): until its last slash has thinned into ink and gone. */
    static final float LIFE=LUNGE_LENGTH/20f+.45f;

    // ───────────────────────────── the body ─────────────────────────────
    // (the video: on the pommel she coils, the sword drawn back low at the right, and goes; out of sight as she dashes (a
    // streak of light); seen again beyond the foe in a low crouch, the sword swept through across the front to the left;
    // out of sight as she flies on; down on the far pommel, her feet together, and up as she stands there. The body faces
    // the way she dashed until she lands on the far sword, then turns back to the formation's way: IsosangClient.lungeFacing.)
    /** Coiled on the pommel: a dip, the sword drawn back low at the right, the free hand reaching ahead. */
    static final R6.Pose L_COIL=w(.04,-.16,-16,14,0,d(-32,118),d(-16,168),d(-22,-40),d(-72,8),d(-102,-8));
    /** Gone: lunging out flat, the sword trailing. */
    static final R6.Pose L_GO=w(.16,-.06,-8,32,0,d(-12,150),d(-6,176),d(4,-22),d(-58,6),d(-122,-6));
    /** Cutting through (out of sight): the sword across the front. */
    static final R6.Pose L_CUT=w(.12,-.14,14,24,2,d(-8,10),d(-6,-20),d(-30,140),d(-60,6),d(-118,-6));
    /** Down beyond the foe in a low crouch, the sword swept through out low at the left, the free arm back. */
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

    /** Where she lands beyond the foe (the server's word), or where she stood. */
    static Vec3d past(StandClient.Cast c){var r=IsosangClient.lunge(c.id());return r==null?c.origin():r.past();}
    /** The way she dashes (level, unit). */
    static Vec3d way(StandClient.Cast c){
        Vec3d d=past(c).subtract(c.origin()).multiply(1,0,1);
        return d.lengthSquared()<1e-4?Vec3d.fromPolar(0,c.yaw()):d.normalize();
    }
    /** How high over her feet her streak runs (her chest), on the pommel and on the ground. */
    static final double CHEST=1.05;
    /** Where her streak is {@code t} seconds in: from her chest on the pommel to her chest where she lands. */
    static Vec3d streak(StandClient.Cast c,double t){return c.origin().add(0,CHEST,0).lerp(past(c).add(0,CHEST,0),lungeDash(t*20));}
    /** Whether slash {@code j} of T {@code id} is cut (or will be): not one due after it was called off. */
    static boolean cuts(UUID id,int j){float end=IsosangClient.volleyEnd(id);return Float.isNaN(end)||lungeSlash(j)<=end;}
    /** Whether its dash cuts the foe (not called off before it passed). */
    static boolean passes(UUID id){float end=IsosangClient.volleyEnd(id);return Float.isNaN(end)||blowAt(LUNGE,0)/20f<=end;}
    /** How much of T {@code id} still shows {@code t} seconds in: all of it, or, called off, fading over a tenth of a second from then. */
    static float kept(UUID id,float t){return SwordVolley.kept(id,t);}
    /**
     * Slash {@code j}'s line through the foe at {@code target} (unit, the way it is cut): two crossing diagonals and a
     * cut near level (the video's three strokes crossing on it), across the way she dashed, each a little out of its
     * plane so they cross in the air and not on a flat sheet.
     */
    static Vec3d slash(Vec3d way,int j){
        Vec3d right=new Vec3d(-way.z,0,way.x);
        double[] el={-50,46,-6},out={.22,-.18,.12};double a=Math.toRadians(el[j]);
        double side=j==1?-1:1;
        return right.multiply(side*Math.cos(a)).add(0,j==1?-Math.sin(a):Math.sin(a),0).add(way.multiply(out[j])).normalize();
    }
    /**
     * The caster's own camera through T (ticks into it, IsosangClient.cameraLag): held back where she landed beyond the foe
     * as she flies on until {@code CAMERA_HOLD}, glided on to her by {@code CAMERA_BACK} (the video: it stays by the foe
     * through the slashes, 0.7 to 0.9 s, and is on its way to her by 1 s, there by about 1.3 s).
     */
    static final float CAMERA_HOLD=19,CAMERA_BACK=31;
    /** How much of the formation's camera pull (3.2 blocks as she stands) it lets go while it holds by the foe (IsosangClient.lungeNear). */
    static final float CAMERA_NEAR=.7f;
    /** How near the foe (blocks, level) the caster's own camera passes as it glides on to her: round it, not through it. */
    static final double CAMERA_CLEAR=2.5;
    /**
     * Where the caster's own camera looks from (2.96.0, IsosangClient.cameraLag) {@code a} ticks into T: held where she
     * stood beyond the foe ({@code held}) until CAMERA_HOLD, then glided on to her ({@code her}) by CAMERA_BACK, eased. Her
     * far sword is mostly on the far side of the foe from there, so on its way it bows out round the foe at {@code foe}
     * (null for none) to CAMERA_CLEAR (level), on the side its straight way passes it, smoothly and none of it at either
     * end: it turns round the foe, facing it all the while (IsosangClient.cameraFace), and does not pass through its body.
     */
    static Vec3d cameraAt(float a,Vec3d held,Vec3d her,Vec3d foe){
        float s=Ease.smooth(a,CAMERA_HOLD,CAMERA_BACK);
        Vec3d at=held.lerp(her,s);
        if(foe==null||s<=0||s>=1)return at;
        double dx=her.x-held.x,dz=her.z-held.z,len=Math.hypot(dx,dz);if(len<1e-3)return at;
        double ux=dx/len,uz=dz/len,nx=-uz,nz=ux;
        double rx=at.x-foe.x,rz=at.z-foe.z,x=rx*ux+rz*uz,l=rx*nx+rz*nz;
        // a smooth bump CAMERA_CLEAR high where it passes the foe, none 2.2 times as far along (never nearer than CAMERA_CLEAR
        // with that width), let in over its first and last 1.5 blocks
        double w=2.2*CAMERA_CLEAR;if(Math.abs(x)>=w)return at;
        double q=1-(x/w)*(x/w),g=CAMERA_CLEAR*q*q*unit(s*len/1.5)*unit((1-s)*len/1.5);
        double lateral=(l>=0?1:-1)*Math.sqrt(l*l+g*g);
        return at.add(nx*(lateral-l),0,nz*(lateral-l));
    }
    private static double unit(double v){v=Math.max(0,Math.min(1,v));return v*v*(3-2*v);}
    /** How long each slash is (blocks, end to end), and how fast it is drawn on, holds its light and thins away (seconds). */
    static final double SLASH=4.6;
    static final float DRAW=.04f,HOLD_LIGHT=.07f,THIN=.28f,INK_GONE=.42f;

    // ───────────────────────────── the passes ─────────────────────────────

    /** The blended pass: the swords' bodies, the thread of ink along her way, the slashes' edges of ink, the ink under each burst. */
    static void alpha(Fx c,EffectMesh m){
        SwordVolley.alpha(c,m);
        float t=c.sec,k=kept(c.cast.id(),t);Vec3d target=SwordVolley.target(c.cast,c.delta);
        // the thread of ink she leaves along her way (the video's black line), drying from where she set out
        float go=LUNGE_GO/20f,land=LUNGE_LAND/20f;
        if(t>=go&&t<land+.45f){
            Vec3d a=streak(c.cast,go),b=streak(c.cast,Math.min(t,land));
            float dry=1-Ease.smooth(t,land+.12f,land+.45f);
            Vec3d tail=a.lerp(b,Ease.smooth(t,land+.05f,land+.45f));
            m.line(tail,b,.07,INK,.85f*dry*k);
        }
        if(target==null)return;
        Vec3d way=way(c.cast);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+INK_GONE||!cuts(c.cast.id(),j))continue;
            Vec3d d=slash(way,j),p0=target.subtract(d.multiply(SLASH*.5)),p1=p0.add(d.multiply(SLASH*Ease.out(t,at-.005f,at+DRAW)));
            float ink=Ease.smooth(t,at-.005f,at+.02f)*(1-Ease.smooth(t,at+THIN,at+INK_GONE));
            double thin=1-.55*Ease.smooth(t,at+.05f,at+THIN);
            m.needle(p0,p1,.6*thin,.55,INK,.62f*ink);
            IsosangEmpower.burstInk(c,m,target,at,.32,j*17+5);
        }
        if(passes(c.cast.id()))IsosangEmpower.burstInk(c,m,target,blowAt(LUNGE,0)/20f,.45,3);
    }
    /**
     * The light: the swords' (SwordVolley), a violet shimmer round her as she coils, her streak of light as she dashes
     * with a white-hot line along her way, a burst on the foe as she passes it, and the three slashes, each drawn on in a
     * breath with a burst as it cuts.
     */
    static void additive(Fx c,EffectMesh m){
        SwordVolley.additive(c,m);
        float t=c.sec,k=kept(c.cast.id(),t);if(k<=.01f)return;
        Vec3d target=SwordVolley.target(c.cast,c.delta),way=way(c.cast);
        float go=LUNGE_GO/20f,land=LUNGE_LAND/20f;
        // coiling: a violet shimmer gathering round her
        if(t<go+.03f){
            float a=Ease.smooth(t,.02f,go-.02f)*(1-Ease.smooth(t,go,go+.03f))*k;Vec3d at=c.cast.origin().add(0,CHEST,0);
            m.softDisc(at,1.0,VIOLET,.32f*a);m.softDisc(at,.45,LILAC,.4f*a);
        }
        // the dash: her streak of light where she is, the way she has come lit white-hot behind her, dying out once she is down
        if(t>=go-.01f&&t<land+.22f){
            Vec3d a=streak(c.cast,go),b=streak(c.cast,Math.min(t,land));
            float lit=k*Ease.smooth(t,go-.01f,go+.01f)*(1-Ease.smooth(t,land,land+.22f));
            if(b.squaredDistanceTo(a)>1e-4){
                m.softLine(a,b,1.3,BLUE,.3f*lit);m.softLine(a,b,.55,AZURE,.55f*lit);m.line(a,b,.1,PALE,.85f*lit);m.line(a,b,.035,WHITE,lit);
            }
            if(t<land+.02f){
                float on=k*Ease.smooth(t,go-.01f,go+.01f)*(1-Ease.smooth(t,land-.01f,land+.02f));
                m.softDisc(b,1.05,VIOLET,.42f*on);m.softDisc(b,.55,LILAC,.55f*on);m.softDisc(b,.22,WHITE,.8f*on);
                m.softTaper(b.subtract(way.multiply(2.6)),b,.05,1.0,LILAC,.4f*on);
            }
        }
        if(target==null)return;
        // the cut in passing, and each slash
        float pass=blowAt(LUNGE,0)/20f;
        if(passes(c.cast.id()))IsosangEmpower.burst(c,m,target,way,pass,.45,3);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+THIN+.04f||!cuts(c.cast.id(),j))continue;
            Vec3d d=slash(way,j),p0=target.subtract(d.multiply(SLASH*.5)),p1=p0.add(d.multiply(SLASH*Ease.out(t,at-.005f,at+DRAW)));
            float lit=k*Ease.smooth(t,at-.01f,at+.005f)*(1-Ease.smooth(t,at+HOLD_LIGHT,at+THIN+.04f));
            double thin=1-.6*Ease.smooth(t,at+.05f,at+THIN);
            m.needle(p0,p1,1.0*thin,.55,BLUE,.32f*lit);
            m.needle(p0,p1,.42*thin,.55,AZURE,.7f*lit);
            m.needle(p0,p1,.14*thin,.55,PALE,.95f*lit);
            m.needle(p0,p1,.05*thin,.55,WHITE,lit);
            // its head racing along it as it is drawn on
            if(t<at+DRAW+.02f){float h=lit*(1-Ease.smooth(t,at+DRAW,at+DRAW+.02f));m.softDisc(p1,.35,AZURE,.5f*h);m.softDisc(p1,.12,WHITE,.9f*h);}
            IsosangEmpower.burst(c,m,target,d,at,.32,j*17+5);
        }
    }
    /** Over it: the swords' starlight (SwordVolley), and a glint at each slash's ends as it is drawn. */
    static void overlay(Fx c,EffectMesh m){
        SwordVolley.overlay(c,m);
        float t=c.sec,k=kept(c.cast.id(),t);Vec3d target=SwordVolley.target(c.cast,c.delta);if(target==null||k<=.01f)return;
        Vec3d way=way(c.cast);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at||t>=at+.16f||!cuts(c.cast.id(),j))continue;
            Vec3d d=slash(way,j);float a=k*(1-Ease.unit(t,at+.04f,at+.16f));
            glint8(m,target.add(d.multiply(SLASH*.5)),.18,WHITE,.8f*a);glint8(m,target.subtract(d.multiply(SLASH*.5)),.12,PALE,.6f*a);
        }
    }
    /** Lights: the swords' (SwordVolley), her streak as she dashes, the foe as she passes it and as each slash cuts; the afterglow cut short throughout. */
    static void frame(Fx c,FxFrame f){
        SwordVolley.frame(c,f);
        float t=c.sec,k=kept(c.cast.id(),t);if(k<=.01f)return;
        float go=LUNGE_GO/20f,land=LUNGE_LAND/20f;
        if(t>=go&&t<land+.05f)f.light(streak(c.cast,Math.min(t,land)),0xb49cff,6,.9f*k);
        Vec3d target=SwordVolley.target(c.cast,c.delta);
        if(target!=null){
            float kp=passes(c.cast.id())?FxLib.kick(t,blowAt(LUNGE,0)/20f,.12f):0;if(kp>.01f)f.light(target,0xcfe4ff,6,1.0f*kp);
            for(int j=0;j<LUNGE_SLASHES;j++){float ks=cuts(c.cast.id(),j)?FxLib.kick(t,lungeSlash(j),.1f):0;if(ks>.01f)f.light(target,0x9fc8ff,5,.8f*ks);}
        }
        // quick throughout (the dash, the flight on, the slashes): short afterglow, else each leaves copies of itself
        if(t<LUNGE_LENGTH/20f)f.afterglow(t<land+.1f?.02f:.05f);
    }
    /** A jolt as she cuts through (harder) and as each slash cuts, each over two frames; and the swords' (SwordVolley). */
    static float shake(StandClient.Cast cast,float sec){
        float v=SwordVolley.shake(cast,sec);
        if(passes(cast.id()))v=Math.max(v,.55f*bump(sec,blowAt(LUNGE,0)/20f,.08f));
        for(int j=0;j<LUNGE_SLASHES;j++)if(cuts(cast.id(),j))v=Math.max(v,.3f*bump(sec,lungeSlash(j),.06f));
        return v;
    }
    /** A violet for her streak (the video's purple blur as she goes). */
    static final int VIOLET=0x7a5cff;
}
