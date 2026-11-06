package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;
import static dev.sangwoo.celestialfall.client.isosang.IsosangSlash.*;
import static dev.sangwoo.celestialfall.client.isosang.SwordFormation.*;

/**
 * Z across the formation's swords (2.93.0, user request: "H 스킬에서 박혀있는 모든 검이 전부 상호작용 되게 바꿔줘 그리고 동시에
 * z스킬 만들자 쳐다보는 검(쉬프락은 가운데 십자가로 쳐다보는 검)에 자신에게만 보이는 흰색 테두리가 생기고 아래에는 푸른색 꽃이
 * 피어나고 z스킬을 누르면 10초 동안 최대 3번 쳐다보는 검으로 이동 할 수 있어", the user's video of it (68d5f592) read on a
 * sheet and confirmed: "맞음, 이대로"; the flower too only for her). While her formation stands every one of its swords
 * may be the one she looks at ({@link #aimed}: the camera's middle, as R held's foe, whether or not the shift lock is
 * on): only she sees it, white along its outline with a blue flower opening at its foot, while Z may move her
 * ({@link #ready}). Z flies her onto it (IsosangClient: Z with the sword's place, the server's IsosangAttack.cross);
 * everyone sees the flight: her body gone into a blue bubble, a blue orb trailing white streaks to the other sword in
 * Z_TRAVEL ticks, and a burst of blue lightning as she stands on its pommel, petal shards drifting about her; a white
 * flower blooms at the foot of the sword she leaves and of the one she lands on and is gone again ({@link #bloom},
 * 2.93.1). Her body is not drawn while she flies ({@link #hides}).
 */
public final class SwordStep {
    private SwordStep(){}

    /** How far off the camera's middle a sword may be and still be the one she looks at (radians, either side). */
    static final double AIM=Math.toRadians(9);
    /** How high over the pommel her body's middle is (blocks): where the bubble is left and the orb flies. */
    static final double CHEST=.95;

    // ───────────────────────────── the sword she looks at ─────────────────────────────

    /**
     * Which sword of the formation of swing {@code id} (its middle {@code centre}, the way its user faced {@code yaw}) is
     * looked at from {@code eye} along {@code look}: the one whose blade, from its foot to its pommel, comes nearest the
     * line of sight, within {@link #AIM} of it and ahead; never {@code on} (the one she stands on). -1 for none.
     */
    static int aimed(UUID id,Vec3d centre,float yaw,int on,Vec3d eye,Vec3d look){
        var all=layout(id);int best=-1;double near=AIM;
        for(int k=0;k<all.length;k++){
            if(k==on)continue;
            Vec3d foot=foot(centre,yaw,all[k]),top=planted(centre,yaw,all[k]);
            for(int i=0;i<=6;i++){
                Vec3d d=foot.lerp(top,i/6.0).subtract(eye);double len=d.length();if(len<.3)continue;
                double cos=d.dotProduct(look)/len;if(cos<=0)continue;
                double a=Math.acos(Math.min(1,cos));if(a<near){near=a;best=k;}
            }
        }
        return best;
    }
    /** The local player's formation now (their own, under way), or null. */
    static StandClient.Cast own(){return IsosangClient.ownFormation();}
    /**
     * Whether Z may move the user of formation {@code c} at {@code t} seconds into it: its swords all planted and it has
     * not ended (IsosangRules.zTime), not flying now, and Z usable (its window open, or ready again: the server's word).
     */
    static boolean ready(StandClient.Cast c,float t){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return false;
        if(!IsosangRules.zTime(t)||IsosangClient.flying(c.id(),t*20)>=0||!Float.isNaN(IsosangClient.formationEnd(c.id())))return false;
        return IsosangClient.zState(c.caster()).usable(mc.world.getTime());
    }
    /** The sword the local player looks at now for Z (-1 for none: no formation of theirs, Z not usable, none in sight). */
    static int aimedNow(MinecraftClient mc){
        var c=own();if(c==null||mc.gameRenderer==null)return -1;var f=IsosangClient.formation(c.id());if(f==null)return -1;
        if(!ready(c,c.age(mc.getTickDelta())/20f))return -1;
        var cam=mc.gameRenderer.getCamera();
        return aimed(c.id(),f.centre(),f.yaw(),IsosangClient.perch(c.id()),cam.getPos(),Vec3d.fromPolar(cam.getPitch(),cam.getYaw()));
    }

    /** (The film harness, 2.93.0) The middles of the local player's own formation's swords now (world), or null for none. */
    public static Vec3d[] middles(){
        var c=own();if(c==null)return null;var all=layout(c.id());Vec3d centre=centre(c);float yaw=yaw(c);
        Vec3d[] out=new Vec3d[all.length];
        for(int k=0;k<all.length;k++)out[k]=foot(centre,yaw,all[k]).lerp(planted(centre,yaw,all[k]),.5);
        return out;
    }
    /** (The film harness) The sword of their own formation the local player stands on now (-1 for none), and the one they look at for Z. */
    public static int perchNow(){var c=own();return c==null?-1:IsosangClient.perch(c.id());}
    public static int aimedForFilm(){return aimedNow(MinecraftClient.getInstance());}

    // the mark on it as it changes (its flower opening, the last one's closing), on the formation's clock
    private static UUID markOf;private static int mark=-1,was=-1;private static float markAt=-9,lastT=-9;

    /** The sword marked for the local player at {@code t} (their own formation), and when the mark moved (for its flower). */
    private static int mark(Fx c,float t){
        if(!c.cast.caster().equals(localId()))return -1;
        if(!c.cast.id().equals(markOf)){markOf=c.cast.id();mark=-1;was=-1;markAt=-9;lastT=-9;}
        if(t==lastT)return mark;lastT=t;
        int now=-1;
        if(ready(c.cast,t)){
            var mc=MinecraftClient.getInstance();var cam=mc.gameRenderer.getCamera();
            now=aimed(c.cast.id(),centre(c.cast),yaw(c.cast),IsosangClient.perch(c.cast.id()),c.camera,Vec3d.fromPolar(cam.getPitch(),cam.getYaw()));
        }
        if(now!=mark){was=mark;mark=now;markAt=t;}
        return mark;
    }
    private static UUID localId(){var p=MinecraftClient.getInstance().player;return p==null?null:p.getUuid();}
    /** How open the mark on sword {@code k} is at {@code t} (0 to 1): opening as it is looked at, closing as the look leaves it. */
    private static float open(int k,float t){
        if(k==mark&&k>=0)return (float)Math.min(1,backOut(Ease.unit(t,markAt,markAt+MARK_OPEN)));
        if(k==was&&k>=0)return 1-Ease.smooth(t,markAt,markAt+MARK_CLOSE);
        return 0;
    }
    static final float MARK_OPEN=.16f,MARK_CLOSE=.1f;

    // ───────────────────────────── drawing ─────────────────────────────

    /** The blended pass: the mark's blue flower (hers only), and the bubble and petals each flight leaves (everyone's). */
    static void alpha(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        mark(c,t);
        var all=layout(c.cast.id());
        for(int k:new int[]{mark,was}){
            if(k<0||k>=all.length)continue;float a=open(k,t);if(a<=.01f)continue;
            flower(m,foot(centre,yaw,all[k]).add(0,.03,0),all[k].seed(),t,a,false);
        }
        for(var fl:IsosangClient.FLIGHTS.getOrDefault(c.cast.id(),java.util.List.of())){
            float since=since(c,fl,t);if(since<-.05f||since>FLIGHT_LIFE)continue;
            Vec3d from=foot(centre,yaw,all[fl.from()]),to=foot(centre,yaw,all[fl.to()]);
            // (2.93.1, user request: "원본 영상 잘 보면 검에서 이동하면 검 아래 꽃이 피어났다가 사라짐") the white flower at the
            // foot of the sword she leaves and of the one she lands on, blooming and gone again
            bloom(m,from.add(0,.03,0),all[fl.from()].seed(),departs(since),false,t);
            bloom(m,to.add(0,.03,0),all[fl.to()].seed()+5,arrives(since),false,t);
            drift(m,planted(centre,yaw,all[fl.to()]).add(0,CHEST,0),all[fl.to()].seed(),since-flight(),false);
            bubble(m,c.camera,planted(centre,yaw,all[fl.from()]).add(0,CHEST,0),since,false);
        }
    }
    /** The light: the mark's white outline and its flower's light (hers only); each flight's bubble, orb, streaks and burst. */
    static void additive(Fx c,EffectMesh m,float t,Vec3d centre,float yaw){
        mark(c,t);
        var all=layout(c.cast.id());
        for(int k:new int[]{mark,was}){
            if(k<0||k>=all.length)continue;float a=open(k,t);if(a<=.01f)continue;
            var b=all[k];
            outline(m,c.camera,planted(centre,yaw,b),frame(down(b),spinAt(b,t)),Math.min(1,a));
            flower(m,foot(centre,yaw,b).add(0,.03,0),b.seed(),t,a,true);
        }
        for(var fl:IsosangClient.FLIGHTS.getOrDefault(c.cast.id(),java.util.List.of())){
            float since=since(c,fl,t);if(since<-.05f||since>FLIGHT_LIFE)continue;
            Vec3d a=planted(centre,yaw,all[fl.from()]).add(0,CHEST,0),b=planted(centre,yaw,all[fl.to()]).add(0,CHEST,0);
            bubble(m,c.camera,a,since,true);
            orb(m,a,b,since,c.cast.id().hashCode()+(int)fl.moved());
            burst(m,b,since-flight(),c.cast.id().hashCode()+(int)fl.moved(),t);
            bloom(m,foot(centre,yaw,all[fl.from()]).add(0,.03,0),all[fl.from()].seed(),departs(since),true,t);
            bloom(m,foot(centre,yaw,all[fl.to()]).add(0,.03,0),all[fl.to()].seed()+5,arrives(since),true,t);
            drift(m,b,all[fl.to()].seed(),since-flight(),true);
        }
    }
    /** Seconds since flight {@code fl} of formation {@code c} began, at {@code t} seconds into it. */
    static float since(Fx c,IsosangClient.Flight fl,float t){return t-(fl.moved()-c.cast.start())/20f;}
    /** How long a flight takes (seconds). */
    static float flight(){return IsosangRules.Z_TRAVEL/20f;}
    /** How long a flight's light lasts after it begins (seconds). */
    static final float FLIGHT_LIFE=1.0f;

    /**
     * The white outline of the sword she looks at (hers only, the video's: a crisp white line round all of it): its outline
     * edges as white lines about as wide on the screen wherever it stands (OUTLINE blocks, wider with the distance: some
     * 2.5 pixels at 960 x 540), in a narrow pale glow.
     */
    static void outline(EffectMesh m,Vec3d camera,Vec3d pommel,Vec3d[] f,float a){
        double w=Math.max(OUTLINE,camera.distanceTo(pommel)*OUTLINE_PER);
        for(var e:EDGES){
            if(!e.outline())continue;
            Vec3d p=at(pommel,f,e.a()),q=at(pommel,f,e.b());
            m.line(p,q,w,WHITE,.95f*a);m.softLine(p,q,w*3.2,PALE,.28f*a);
        }
    }
    static final double OUTLINE=.035,OUTLINE_PER=.0055;
    /**
     * The blue flower at the foot of the sword she looks at (hers only, the video's: crystal petals opening round its foot
     * like a lotus, deep navy with white rims): six petals rising out of the ground and curling out at their tips, three
     * smaller ones standing up inside them, turning slowly; their bodies blended ({@code light} false), or their light
     * added (white rims, a pale vein, a faint blue glow on the ground and a glint).
     */
    static void flower(EffectMesh m,Vec3d f,int seed,float t,float open,boolean light){
        double size=FLOWER*open;if(size<=.01)return;float a=Math.min(1,open);
        double turn=t*.35;
        for(int ring=0;ring<2;ring++){
            int petals=ring==0?6:3;
            for(int j=0;j<petals;j++){
                int s=seed*29+j*11+ring*101;
                double az=turn*(ring==0?1:-1)+Math.PI*2*(j+(ring==0?0:.5)+.2*(Ease.hash(s)-.5))/petals;
                double el=Math.toRadians(ring==0?32+16*Ease.hash(s+1):62+10*Ease.hash(s+1));
                double len=(ring==0?.55+.15*Ease.hash(s+2):.36+.08*Ease.hash(s+2))*size,wid=(ring==0?.2:.13)*size*(.9+.2*Ease.hash(s+3));
                Vec3d out=new Vec3d(Math.cos(az),0,Math.sin(az)),side=new Vec3d(-Math.sin(az),0,Math.cos(az));
                Vec3d dir=out.multiply(Math.cos(el)).add(0,Math.sin(el),0);
                Vec3d mid=f.add(dir.multiply(len*.45)),tip=f.add(dir.multiply(len)).add(out.multiply(.18*len)).add(0,-.06*len,0);
                Vec3d l=mid.add(side.multiply(wid)),r=mid.subtract(side.multiply(wid));
                if(!light){
                    m.vertex(f,NAVY,.95f*a);m.vertex(l,DEEP,.92f*a);m.vertex(tip,BLUE,.95f*a);m.vertex(tip,BLUE,.95f*a);
                    m.vertex(f,NAVY,.95f*a);m.vertex(tip,BLUE,.95f*a);m.vertex(r,DEEP,.92f*a);m.vertex(r,DEEP,.92f*a);
                }else{
                    double w=.022*size;
                    m.line(l,tip,w,WHITE,.75f*a);m.line(r,tip,w,WHITE,.75f*a);m.line(f,l,w*.7,PALE,.45f*a);m.line(f,r,w*.7,PALE,.45f*a);
                    m.line(f.lerp(mid,.3),tip.lerp(mid,.2),w*.6,AZURE,.35f*a);
                }
            }
        }
        if(light){
            m.softDisc(f.add(0,.06,0),.85*size,BLUE,.2f*a);
            glint8(m,f.add(0,.3*size,0),.12*size*(.65+.35*twinkle(t,seed)),WHITE,.55f*a);
        }
    }
    static final double FLOWER=1.2;
    /**
     * The bubble left where she was as she flies off (the video's): a blue sphere of light about her body, a bright rim and
     * a faint inside (light: turned to the camera, as every flash of light), swelling a little and gone in BUBBLE seconds.
     */
    static void bubble(EffectMesh m,Vec3d camera,Vec3d at,float since,boolean light){
        if(since<0||since>BUBBLE)return;
        float a=Ease.smooth(since,0,.03f)*(1-Ease.smooth(since,BUBBLE*.45f,BUBBLE));double r=.82+.22*Ease.out(since,0,BUBBLE);
        Vec3d view=camera.subtract(at).normalize(),u=view.crossProduct(UP);if(u.lengthSquared()<1e-6)u=new Vec3d(1,0,0);u=u.normalize();Vec3d v=u.crossProduct(view).normalize();
        if(!light){m.softDisc(at,r,0x2346c8,.32f*a);return;}
        // (bright at its rim and faint inside, as a bubble of light is)
        for(int k=0;k<4;k++)m.ring(at,u,v,r*(1-.07*k),48,1,.05,k==0?AZURE:BLUE,(.5f-.11f*k)*a,0);
        m.ring(at,u,v,r*.95,48,1,.022,PALE,.4f*a,0);m.softDisc(at,r,BLUE,.08f*a);
        glint8(m,at.add(u.multiply(-r*.45)).add(v.multiply(r*.45)),.14,WHITE,.6f*a);
    }
    static final float BUBBLE=.38f;
    /**
     * The orb she flies as (the video's): a blue light with a white heart from one pommel to the other over IsosangRules'
     * flight (zAlong, zArc), white streaks trailing back along its way, the streaks fading once it has landed.
     */
    static void orb(EffectMesh m,Vec3d a,Vec3d b,float since,int seed){
        float fl=flight();if(since<0||since>fl+STREAK_FADE)return;
        float u=Math.min(1,since/fl),fade=1-Ease.smooth(since,fl,fl+STREAK_FADE);
        Vec3d at=path(a,b,u);
        if(since<=fl+.02f){
            float in=Ease.smooth(since,0,.03f)*(1-Ease.smooth(since,fl-.01f,fl+.02f));
            m.softDisc(at,.62,BLUE,.55f*in);m.softDisc(at,.34,AZURE,.8f*in);m.softDisc(at,.16,WHITE,.95f*in);
        }
        // the streaks: thin white ribbons along its way, waving a little about it, from where it is back toward where it set out
        Vec3d d=b.subtract(a);double len=d.length();if(len<1e-3)return;
        Vec3d side=d.crossProduct(UP);if(side.lengthSquared()<1e-6)side=new Vec3d(1,0,0);side=side.normalize();Vec3d up=side.crossProduct(d).normalize();
        double back=Math.min(u,STREAK_BACK/len);
        for(int s=0;s<STREAKS;s++){
            double ph=Ease.hash(seed+s*13)*Math.PI*2,amp=.08+.16*Ease.hash(seed+s*13+1),w=.012+.012*Ease.hash(seed+s*13+2);
            Vec3d prev=null;int n=10;
            for(int i=0;i<=n;i++){
                double v=u-back*(i/(double)n);if(v<0)break;
                double wave=Math.sin(ph+v*9);
                Vec3d q=path(a,b,(float)v).add(side.multiply(wave*amp*(1-v*.3))).add(up.multiply(Math.cos(ph+v*7)*amp*.6));
                float k=(float)(1-i/(double)n);
                if(prev!=null){m.line(prev,q,w,WHITE,.8f*k*fade);m.softLine(prev,q,w*6,PALE,.22f*k*fade);}
                prev=q;
            }
        }
    }
    static final int STREAKS=5;
    static final double STREAK_BACK=5.5;
    static final float STREAK_FADE=.16f;
    /** Where the orb is {@code u} through its flight from {@code a} to {@code b}: IsosangRules' ease and low arc. */
    static Vec3d path(Vec3d a,Vec3d b,float u){return a.lerp(b,IsosangRules.zAlong(u)).add(0,IsosangRules.zArc(u),0);}
    /**
     * The burst as she lands (the video's): blue lightning crackling round her body for BURST seconds, a flash of light
     * and a ring of light running out round her feet.
     */
    static void burst(EffectMesh m,Vec3d at,float since,int seed,float t){
        if(since<0||since>BURST)return;
        float k=FxLib.kick(since,.01f,.1f),a=1-Ease.smooth(since,BURST*.4f,BURST);
        m.softDisc(at,1.1,BLUE,.5f*k+.15f*a);m.softDisc(at,.45,WHITE,.7f*k);
        for(int i=0;i<BOLTS;i++){
            double az=Math.PI*2*(i+Ease.hash(seed+i*5))/BOLTS,el=(Ease.hash(seed+i*5+1)-.5)*1.4;
            Vec3d dir=new Vec3d(Math.cos(az)*Math.cos(el),Math.sin(el),Math.sin(az)*Math.cos(el));
            double reach=.55+.6*Ease.hash(seed+i*5+2);
            bolt(m,at.add(dir.multiply(.15)),at.add(dir.multiply(reach)),seed+i*31,t,.12,.022,WHITE,AZURE,.85f*a);
        }
        double r=.3+1.6*Ease.out(since,0,BURST);
        m.ring(at.add(0,-CHEST+.05,0),new Vec3d(1,0,0),new Vec3d(0,0,1),r,40,1,.05,AZURE,.6f*a,0);
    }
    static final float BURST=.45f;
    static final int BOLTS=6;
    /**
     * The white flower at a sword's foot as she flies from it or onto it (2.93.1, user request: "원본 영상 잘 보면 검에서
     * 이동하면 검 아래 꽃이 피어났다가 사라짐", the video read frame by frame): white crystal petals, blue at their foot,
     * opening from a closed bud ({@code oa[0]} 0) round the sword's foot and curling out at their tips ({@code oa[0]} 1, a
     * little over as they open), six outer and three inner ones, a faint ring of light on the ground once open; their bodies
     * blended ({@code light} false) or their light added (white rims, a blue glow, a glint), {@code oa[1]} strong.
     */
    static void bloom(EffectMesh m,Vec3d f,int seed,float[] oa,boolean light,float t){
        float open=oa[0],a=oa[1];if(a<=.01f)return;
        double size=BLOOM,turn=Ease.hash(seed+3)*Math.PI*2+t*.25,wide=Math.min(1,open);
        for(int ring=0;ring<2;ring++){
            int petals=ring==0?6:3;
            for(int j=0;j<petals;j++){
                int s=seed*31+j*7+ring*97;
                double az=turn+Math.PI*2*(j+(ring==0?0:.5)+.15*(Ease.hash(s)-.5))/petals;
                double el=Math.toRadians(ring==0?84-(46+8*Ease.hash(s+1))*open:88-24*open);
                double len=(ring==0?.5+.12*Ease.hash(s+2):.34+.06*Ease.hash(s+2))*size,wid=(ring==0?.19:.12)*size*(.45+.55*wide);
                Vec3d out=new Vec3d(Math.cos(az),0,Math.sin(az)),side=new Vec3d(-Math.sin(az),0,Math.cos(az));
                Vec3d dir=out.multiply(Math.cos(el)).add(0,Math.sin(el),0);
                Vec3d mid=f.add(dir.multiply(len*.42)),tip=f.add(dir.multiply(len)).add(out.multiply(.16*len*open)).add(0,-.05*len*open,0);
                Vec3d l=mid.add(side.multiply(wid)),r=mid.subtract(side.multiply(wid));
                if(!light){
                    m.vertex(f,BLOOM_BASE,.9f*a);m.vertex(l,BLOOM_MID,.88f*a);m.vertex(tip,WHITE,.92f*a);m.vertex(tip,WHITE,.92f*a);
                    m.vertex(f,BLOOM_BASE,.9f*a);m.vertex(tip,WHITE,.92f*a);m.vertex(r,BLOOM_MID,.88f*a);m.vertex(r,BLOOM_MID,.88f*a);
                }else{
                    double w=.02*size;
                    m.line(l,tip,w,WHITE,.7f*a);m.line(r,tip,w,WHITE,.7f*a);m.line(f,l,w*.7,PALE,.4f*a);m.line(f,r,w*.7,PALE,.4f*a);
                }
            }
        }
        if(light){
            m.softDisc(f.add(0,.08,0),.7*size,BLUE,.22f*a);
            m.ring(f.add(0,.02,0),new Vec3d(1,0,0),new Vec3d(0,0,1),.55*size*(.5+.5*wide),40,1,.02,PALE,.35f*a*(float)wide,0);
            glint8(m,f.add(0,.25*size,0),.1*size*(.6+.4*twinkle(t,seed)),WHITE,.5f*a);
        }
    }
    static final double BLOOM=1.05;
    static final int BLOOM_BASE=0x3a62e0,BLOOM_MID=0xc8dcff;
    /**
     * How open and how strong the flower at the sword she leaves is {@code s} seconds into her flight (the video: open by
     * 0.12 s, gone some half a second on): {open, strength}.
     */
    static float[] departs(float s){
        return new float[]{(float)Math.min(1.08,backOut(Ease.unit(s,0,DEPART_OPEN))),Ease.smooth(s,0,.04f)*(1-Ease.smooth(s,DEPART_FADE0,DEPART_FADE1))};
    }
    /**
     * The same for the sword she lands on (the video: a closed bud there as the orb comes, 0.15 s on, opening as she lands
     * and stands on it, full by 0.38 s, gone by 0.65 s).
     */
    static float[] arrives(float s){
        return new float[]{(float)Math.min(1.08,backOut(Ease.unit(s,ARRIVE_OPEN0,ARRIVE_OPEN1))),Ease.smooth(s,ARRIVE_BUD,ARRIVE_BUD+.05f)*(1-Ease.smooth(s,ARRIVE_FADE0,ARRIVE_FADE1))};
    }
    static final float DEPART_OPEN=.14f,DEPART_FADE0=.32f,DEPART_FADE1=.52f,ARRIVE_BUD=.13f,ARRIVE_OPEN0=.24f,ARRIVE_OPEN1=.4f,ARRIVE_FADE0=.46f,ARRIVE_FADE1=.66f;
    /**
     * Small petal shards drifting out about her body as she lands (the video's), turning, fading in DRIFT seconds: their
     * bodies blended or a faint blue glow added.
     */
    static void drift(EffectMesh m,Vec3d at,int seed,float s,boolean light){
        if(s<0||s>DRIFT)return;
        float a=Ease.smooth(s,0,.05f)*(1-Ease.smooth(s,DRIFT*.5f,DRIFT));double go=Ease.out(s,0,DRIFT);
        for(int j=0;j<6;j++){
            int q=seed*41+j*17;double az=Math.PI*2*(j+Ease.hash(q))/6,spin=s*(5+4*Ease.hash(q+1));
            Vec3d out=new Vec3d(Math.cos(az),0,Math.sin(az));
            Vec3d c=at.add(out.multiply(.35+.55*go*(.6+.4*Ease.hash(q+2)))).add(0,(Ease.hash(q+3)-.4)*.6+.25*go,0);
            double size=.09*(1-.35*go);
            Vec3d u=new Vec3d(Math.cos(az+spin),.5,Math.sin(az+spin)).normalize().multiply(size),v=out.crossProduct(UP).normalize().multiply(size*.4);
            if(!light)m.quad(c.subtract(u),c.subtract(v),c.add(u),c.add(v),0xd8e6ff,.85f*a);
            else m.softDisc(c,size*1.8,AZURE,.25f*a);
        }
    }
    static final float DRIFT=.55f;

    /** Whether {@code e}'s body is left out of the frame now (2.93.0: she is the orb while she flies). */
    public static boolean hides(Entity e){
        if(!(e instanceof PlayerEntity))return false;
        var mc=MinecraftClient.getInstance();
        for(var c:IsosangClient.SWINGS.values())
            if(c.combo()==IsosangRules.FORMATION&&c.caster().equals(e.getUuid())&&IsosangClient.flying(c.id(),c.age(mc.getTickDelta()))>=0)return true;
        return false;
    }
}
