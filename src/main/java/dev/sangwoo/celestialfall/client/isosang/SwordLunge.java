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
 * of Z's flight on to a sword on the other side of the formation from hers (2.96.1, the server's roll; 2.96.0 the one
 * farthest from the foe; SwordStep, the formation's flights) while the swords come down on it one after another and
 * three slashes cross on it, each drawn on in a breath, white at its heart, blue round it and an edge of ink, a burst
 * as it cuts, thinning and gone. She lands on that sword and turns back to the formation's way. 2.96.1 (user request:
 * "이펙트가 너무 작고 3참격이 안 보이는 거 같아"): all of it near twice as big, the slashes laid across the way she sees the
 * foe from there.
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
    // the way she dashed until she lands on the sword she flies on to, then turns back to the formation's way: IsosangClient.lungeFacing.)
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
     * The way the slashes are cut across (level, unit; 2.96.1, user request: "이펙트가 너무 작고 3참격이 안 보이는 거 같아"):
     * from the sword she flies on to, to the foe. She stands there as they cut, her camera with her and turned onto the
     * foe, so each is seen at its full length (2.96.0 laid them across the way she dashed: from beside they were short
     * lines). The way she dashed while that sword is not known.
     */
    static Vec3d slashWay(StandClient.Cast c,Vec3d target){
        var run=IsosangClient.lunge(c.id());var form=IsosangClient.formationOf(c.caster());
        var f=form==null?null:IsosangClient.formation(form.id());
        if(run!=null&&f!=null&&target!=null){
            Vec3d d=target.subtract(IsosangClient.place(form.id(),f,run.to())).multiply(1,0,1);
            if(d.lengthSquared()>1)return d.normalize();
        }
        return way(c);
    }
    /**
     * Slash {@code j}'s line through the foe at {@code target} (unit, the way it is cut): two crossing diagonals and a
     * cut near level (the video's three strokes crossing on it), across {@code way} (slashWay), each a little out of its
     * plane so they cross in the air and not on a flat sheet.
     */
    static Vec3d slash(Vec3d way,int j){
        Vec3d right=new Vec3d(-way.z,0,way.x);
        double[] el={-50,46,-6},out={.22,-.18,.12};double a=Math.toRadians(el[j]);
        double side=j==1?-1:1;
        return right.multiply(side*Math.cos(a)).add(0,j==1?-Math.sin(a):Math.sin(a),0).add(way.multiply(out[j])).normalize();
    }
    /**
     * How long each slash is (blocks, end to end), and how fast it is drawn on, holds its light and thins away (seconds):
     * 2.96.1 near twice as long and wide, and lit some twice as long, seen from her far sword (2.96.0: 4.6 blocks, lit
     * 0.07 s, thinned by 0.28 s, gone by 0.42 s).
     */
    static final double SLASH=8.5;
    static final float DRAW=.06f,HOLD_LIGHT=.16f,THIN=.45f,INK_GONE=.75f;
    /** How big each slash's burst is, the burst as she cuts through, and each of the swords' bursts (IsosangEmpower.burst's size; 2.96.0: .32, .45, .4). */
    static final double SLASH_BURST=.85,PASS_BURST=1.1,SWORD_BURST=.8;

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
            m.line(tail,b,.13,INK,.85f*dry*k);
        }
        if(target==null)return;
        Vec3d way=slashWay(c.cast,target);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+INK_GONE||!cuts(c.cast.id(),j))continue;
            Vec3d d=slash(way,j),p0=target.subtract(d.multiply(SLASH*.5)),p1=p0.add(d.multiply(SLASH*Ease.out(t,at-.005f,at+DRAW)));
            float ink=Ease.smooth(t,at-.005f,at+.03f)*(1-Ease.smooth(t,at+THIN,at+INK_GONE));
            double thin=1-.5*Ease.smooth(t,at+.08f,at+THIN);
            m.needle(p0,p1,1.35*thin,.55,INK,.7f*ink);
            IsosangEmpower.burstInk(c,m,target,at,SLASH_BURST,j*17+5);
        }
        if(passes(c.cast.id()))IsosangEmpower.burstInk(c,m,target,blowAt(LUNGE,0)/20f,PASS_BURST,3);
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
        // (2.96.1: all of it near twice as big as 2.96.0's, seen from her far sword)
        float go=LUNGE_GO/20f,land=LUNGE_LAND/20f;
        // coiling: a violet shimmer gathering round her
        if(t<go+.03f){
            float a=Ease.smooth(t,.02f,go-.02f)*(1-Ease.smooth(t,go,go+.03f))*k;Vec3d at=c.cast.origin().add(0,CHEST,0);
            m.softDisc(at,1.7,VIOLET,.34f*a);m.softDisc(at,.8,LILAC,.42f*a);
        }
        // the dash: her streak of light where she is, the way she has come lit white-hot behind her, dying out once she is down
        if(t>=go-.01f&&t<land+.22f){
            Vec3d a=streak(c.cast,go),b=streak(c.cast,Math.min(t,land));
            float lit=k*Ease.smooth(t,go-.01f,go+.01f)*(1-Ease.smooth(t,land,land+.22f));
            if(b.squaredDistanceTo(a)>1e-4){
                m.softLine(a,b,2.4,BLUE,.3f*lit);m.softLine(a,b,1.0,AZURE,.55f*lit);m.line(a,b,.2,PALE,.85f*lit);m.line(a,b,.07,WHITE,lit);
            }
            if(t<land+.02f){
                float on=k*Ease.smooth(t,go-.01f,go+.01f)*(1-Ease.smooth(t,land-.01f,land+.02f));
                m.softDisc(b,1.9,VIOLET,.42f*on);m.softDisc(b,1.0,LILAC,.55f*on);m.softDisc(b,.4,WHITE,.8f*on);
                m.softTaper(b.subtract(way.multiply(4.0)),b,.1,1.8,LILAC,.4f*on);
            }
        }
        if(target==null)return;
        // the cut in passing, and each slash
        float pass=blowAt(LUNGE,0)/20f;
        if(passes(c.cast.id()))IsosangEmpower.burst(c,m,target,way,pass,PASS_BURST,3);
        Vec3d cut=slashWay(c.cast,target);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at-.01f||t>=at+THIN+.06f||!cuts(c.cast.id(),j))continue;
            Vec3d d=slash(cut,j),p0=target.subtract(d.multiply(SLASH*.5)),p1=p0.add(d.multiply(SLASH*Ease.out(t,at-.005f,at+DRAW)));
            float lit=k*Ease.smooth(t,at-.01f,at+.005f)*(1-Ease.smooth(t,at+HOLD_LIGHT,at+THIN+.06f));
            double thin=1-.55*Ease.smooth(t,at+.08f,at+THIN);
            m.needle(p0,p1,2.2*thin,.55,BLUE,.34f*lit);
            m.needle(p0,p1,.95*thin,.55,AZURE,.72f*lit);
            m.needle(p0,p1,.32*thin,.55,PALE,.95f*lit);
            m.needle(p0,p1,.12*thin,.55,WHITE,lit);
            // its head racing along it as it is drawn on
            if(t<at+DRAW+.03f){float h=lit*(1-Ease.smooth(t,at+DRAW,at+DRAW+.03f));m.softDisc(p1,.8,AZURE,.5f*h);m.softDisc(p1,.3,WHITE,.9f*h);}
            IsosangEmpower.burst(c,m,target,d,at,SLASH_BURST,j*17+5);
        }
    }
    /** Over it: the swords' starlight (SwordVolley), and a glint at each slash's ends as it is drawn. */
    static void overlay(Fx c,EffectMesh m){
        SwordVolley.overlay(c,m);
        float t=c.sec,k=kept(c.cast.id(),t);Vec3d target=SwordVolley.target(c.cast,c.delta);if(target==null||k<=.01f)return;
        Vec3d way=slashWay(c.cast,target);
        for(int j=0;j<LUNGE_SLASHES;j++){
            float at=lungeSlash(j);if(t<at||t>=at+.3f||!cuts(c.cast.id(),j))continue;
            Vec3d d=slash(way,j);float a=k*(1-Ease.unit(t,at+.08f,at+.3f));
            glint8(m,target.add(d.multiply(SLASH*.5)),.5,WHITE,.85f*a);glint8(m,target.subtract(d.multiply(SLASH*.5)),.35,PALE,.65f*a);
        }
    }
    /** Lights: the swords' (SwordVolley), her streak as she dashes, the foe as she passes it and as each slash cuts; the afterglow cut short throughout. */
    static void frame(Fx c,FxFrame f){
        SwordVolley.frame(c,f);
        float t=c.sec,k=kept(c.cast.id(),t);if(k<=.01f)return;
        float go=LUNGE_GO/20f,land=LUNGE_LAND/20f;
        if(t>=go&&t<land+.05f)f.light(streak(c.cast,Math.min(t,land)),0xb49cff,9,.95f*k);
        Vec3d target=SwordVolley.target(c.cast,c.delta);
        if(target!=null){
            float kp=passes(c.cast.id())?FxLib.kick(t,blowAt(LUNGE,0)/20f,.15f):0;if(kp>.01f)f.light(target,0xcfe4ff,9,1.0f*kp);
            for(int j=0;j<LUNGE_SLASHES;j++){float ks=cuts(c.cast.id(),j)?FxLib.kick(t,lungeSlash(j),.18f):0;if(ks>.01f)f.light(target,0x9fc8ff,10,1.0f*ks);}
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
