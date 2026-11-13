package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.XCharaWeaken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.xchara.GlitchFx.*;
import static dev.sangwoo.celestialfall.xchara.WeakenRules.*;

/**
 * X Chara's T with the hack on, the weakening, on this client (2.126.0, user request: "바라보는 적에게 !!!(기존 !!!효과음
 * 사용)가 뜨고 머리 위에 보라색 글씨로 공격력 방어력이 25% 감소한다. 라고 뜨게 해주고 몸에도 보라색 아래 방향 화살표
 * 아우라가 방출되게 디버프는 20초 동안 지속 돼"; XCharaWeaken for its rules, WeakenRules for its timing). T on the map
 * with the hack on asks for it with the one the camera's middle looks at (XCharaClient.key, as G and R). The server
 * tells when it starts, when it takes (until when the one marked is weakened), when it is blocked or called off, and
 * once a second of everyone weakened near (so this client knows of one it came near later).
 * <p>
 * What it draws, with the state effects (KagenoRenderer): the "!!!" over the one marked while it may still be blocked
 * (G's own, SwordDance.bangs, with G's warning sound as it starts: the user's "기존 !!!효과음 사용"), a pale flash on
 * them if their guard blocked it; on one weakened, all its time, purple motes scattering out of their body
 * ({@link #mote}: each a little square facing the viewer, a dark line round it, its purple laid over and not added so
 * it stays purple before a bright sky; 2.126.0 had arrows pointing down, the user then: "화살표 말고 바깥쪽으로 보라색
 * 이펙트기 퍼져나가게"), a ring coming down them as it takes, a faint purple ring at their feet, and the words "공격력
 * 방어력이 25% 감소한다." in purple over their head ({@link #text}: the game's pixel typeface, drawn in the world
 * facing the viewer). Its user's panel is pressed as it is called ({@link #press}). Everything is a
 * function of its clock.
 */
public final class WeakenClient {
    /** The words over the head of one weakened (the user's). */
    public static final String WORDS="공격력 방어력이 25% 감소한다.";
    /**
     * Its motes (2.126.1, the user: "그냥 안쪽에서 화살표 말고 바깥쪽으로 보라색 이펙트기 퍼져나가게", and of what I asked: no
     * arrows at all, "흩어지는 보라 입자"): how many, how long each lives (seconds), how big (blocks across), and how far
     * out from the body it goes (blocks, the least and the most).
     */
    static final int MOTES=84;
    static final float LIFE=.95f;
    static final double MOTE=.075,REACH_LEAST=.75,REACH_MOST=1.55;
    /** It comes over this long and goes over this long (seconds), on one weakened. */
    static final float IN=.18f,OUT=.45f;
    /**
     * The words' middle stands this far over the top of the head (blocks: where the game draws a name, from 0.275 to
     * 0.525 over it; the user, 2.126.1: "글자 좀만 낮춰 그냥 저 가만히 있는 더미 이름표랑 겹치게 해"), and a pixel of
     * their letters is this big (blocks: the game's own pixel typeface, the user: "글자 폰트를 도트 처럼 만들어주고"; a
     * name's is 0.025).
     */
    static final double OVER=.4,DOT=.016;
    /** Their purple, and the deep purple line round them (the user: "더 보라색으로": the line is purple too, not black, so the small letters read purple). */
    static final int WORD=0xa436ff,WORD_LINE=0x340a6e;
    /** The "!!!" stands this far over the head (G's and R's place: over the name, well clear of the words). */
    static final double BANGS=1.15;
    /** One being cast as this client heard of it. */
    static final class Mark {
        final UUID user,targetId;final int target;final long start,heard;final int received;final float yaw;
        float ended=Float.NaN;int how=-1;XCharaClient.Voice warn;
        Mark(UUID user,int target,UUID targetId,long start,long heard,float yaw){this.user=user;this.target=target;this.targetId=targetId;this.start=start;this.heard=heard;received=StandClient.ticks();this.yaw=yaw;}
        float age(float delta){return heard-start+(StandClient.ticks()-received)+delta;}
        boolean ended(){return !Float.isNaN(ended);}
        boolean blocked(){return how==XCharaWeaken.BLOCKED;}
        boolean stopped(){return how==XCharaWeaken.BLOCKED||how==XCharaWeaken.CUT;}
    }
    /** One weakened as this client knows of it: how many ticks were left of it when last told, and when that was; when it first knew, and whether it saw it take. */
    static final class Weak {
        final int entity;final UUID id;long left;int told;final int since;final boolean fresh;final int seed;
        Weak(int entity,UUID id,long left,boolean fresh){this.entity=entity;this.id=id;this.left=left;told=since=StandClient.ticks();this.fresh=fresh;seed=entity*7919+since*31;}
        /** Ticks of it left now, and ticks since this client first knew of it. */
        float left(float delta){return left-(StandClient.ticks()-told)-delta;}
        float on(float delta){return StandClient.ticks()-since+delta;}
    }
    private static final Map<UUID,Mark> MARKS=new HashMap<>();
    private static final Map<Integer,Weak> WEAK=new HashMap<>();
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static Object world;
    private WeakenClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaWeaken.EVENT,(mc,handler,b,response)->{
            UUID user=b.readBoolean()?b.readUuid():null;Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong();int target=b.readVarInt();UUID targetId=b.readUuid();
            long until=b.readLong();float yaw=b.readFloat();long ready=b.readLong();
            if(!Float.isFinite(yaw)||type>XCharaWeaken.ENDED)return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                if(world!=mc.world){clear();world=mc.world;}
                if(type==XCharaWeaken.STATE||type==XCharaWeaken.APPLIED){
                    long left=until-now;if(left<=0||left>LASTS+40)return;
                    var w=WEAK.get(target);
                    // (weakened anew, it takes again: its ring comes down them once more)
                    if(w==null||!w.id.equals(targetId)||type==XCharaWeaken.APPLIED)WEAK.put(target,new Weak(target,targetId,left,type==XCharaWeaken.APPLIED));
                    else{w.left=left;w.told=StandClient.ticks();}
                }
                if(type==XCharaWeaken.ENDED){WEAK.remove(target);return;}
                if(type==XCharaWeaken.STATE||user==null)return;
                if(type!=XCharaWeaken.START)READY.put(user,ready);
                if(type==XCharaWeaken.START){
                    if(now<start||now-start>APPLY+40)return;
                    var old=MARKS.remove(user);if(old!=null&&old.warn!=null)old.warn.fade();
                    var d=new Mark(user,target,targetId,start,now,yaw);MARKS.put(user,d);
                    // its user looks at the one marked (the server turned them too)
                    if(mc.player!=null&&mc.player.getUuid().equals(user)){mc.player.setYaw(yaw);mc.player.setHeadYaw(yaw);mc.player.setBodyYaw(yaw);mc.player.prevYaw=yaw;}
                    // G's warning sound (the user: "기존 !!!효과음 사용"), none if heard too late to come with it
                    if(d.age(0)<=MARK+4){
                        d.warn=new XCharaClient.Voice(CelestialFall.id("xchara_g_warn"),()->loudness(mc,d));
                        mc.getSoundManager().play(d.warn);
                    }
                    return;
                }
                var d=MARKS.get(user);
                if(d==null||d.start!=start||d.ended())return;
                d.ended=d.age(0);d.how=type;
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(WeakenClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(WeakenClient::clear));
    }
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var d:MARKS.values())if(d.warn!=null&&!d.warn.isDone())mc.getSoundManager().stop(d.warn);
        MARKS.clear();WEAK.clear();READY.clear();shown=List.of();marks=List.of();world=null;
    }
    /** Its warning for this client: loud for its two, fading out to 40 blocks from the nearer of them for the rest. */
    static float loudness(MinecraftClient mc,Mark d){
        if(mc.player==null||mc.world==null)return 0;
        if(mc.player.getUuid().equals(d.user)||mc.player.getUuid().equals(d.targetId))return (float)CelestialFall.CONFIG.effectVolume;
        double far=Double.MAX_VALUE;
        var u=mc.world.getPlayerByUuid(d.user);if(u!=null)far=Math.min(far,mc.player.distanceTo(u));
        var t=mc.world.getEntityById(d.target);if(t!=null)far=Math.min(far,mc.player.distanceTo(t));
        return far==Double.MAX_VALUE?0:(float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/40));
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!MARKS.isEmpty()||!WEAK.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        MARKS.values().removeIf(d->d.age(0)>APPLY+60);
        // one whose time is out, or whom the server has not told of for a while (gone from near, or dead)
        WEAK.values().removeIf(w->w.left(0)<=0||StandClient.ticks()-w.told>70);
        READY.values().removeIf(at->at<mc.world.getTime()-20);
    }

    // ───────────────────────────── who ─────────────────────────────

    /** When this player's is ready again (the server's world time; long ago if never). */
    public static long ready(UUID player){return READY.getOrDefault(player,Long.MIN_VALUE);}
    /** Whether this one is weakened now, as this client knows. */
    public static boolean weakened(Entity e){
        if(e==null||WEAK.isEmpty())return false;
        var w=WEAK.get(e.getId());return w!=null&&w.id.equals(e.getUuid())&&w.left(0)>0;
    }
    /** What a blow dealt by {@code dealer} to the entity {@code victim} is multiplied by (for the numbers its dealer sees: HitFeedback). */
    public static float factor(Entity dealer,Entity victim){return scale(dealer!=victim&&weakened(dealer),weakened(victim));}
    /** Whether this one is held where they stand by one now (KagenoManager.frozen on this client): its user through its "!!!". */
    public static boolean holds(Entity e){
        if(e==null||MARKS.isEmpty()||!MapClient.enabled())return false;
        var d=MARKS.get(e.getUuid());
        return d!=null&&!d.ended()&&d.age(0)>=0&&d.age(0)<APPLY+2;
    }
    /** The panel pressed {@code sec} seconds into it (stopped short at {@code stopped}, NaN if not): {how far the arm and the panel have come before the body, how far the hand is down on it, how lit the panel is, 1 for violet}. */
    static float[] pressAt(float sec,float stopped){
        float lift=Ease.smooth(sec,0,.1f)*(1-Ease.smooth(sec,.34f,.55f));
        if(!Float.isNaN(stopped))lift*=1-Ease.smooth(sec,stopped,stopped+.15f);
        float tap=Ease.smooth(sec,.04f,.1f)*(1-Ease.smooth(sec,.13f,.22f));
        // lit as it is pressed, and again as the weakening takes
        float lit=Math.max(Ease.smooth(sec,.08f,.1f)*(1-Ease.smooth(sec,.16f,.4f)),Ease.smooth(sec,APPLY/20f-.04f,APPLY/20f)*(1-Ease.smooth(sec,APPLY/20f+.06f,APPLY/20f+.3f)));
        return new float[]{lift,tap,Float.isNaN(stopped)?lit:0,1};
    }
    /** This player's press of the panel for their weakening this frame, or null. */
    public static float[] press(UUID id,float delta){
        var d=MARKS.get(id);if(d==null)return null;
        float sec=d.age(delta)/20f;if(sec>APPLY/20f+.4f)return null;
        return pressAt(sec,d.stopped()?d.ended/20f:Float.NaN);
    }

    // ───────────────────────────── the motes ─────────────────────────────

    private static float hash(int a,int b){int h=a*374761393+b*668265263;h=(h^(h>>>13))*1274126177;h^=h>>>16;return (h&0xffffff)/(float)0x1000000;}
    /**
     * Mote {@code k} of one weakened at {@code clock} seconds: {the angle round the body it goes out at (radians), how
     * far out from the body's middle it starts (times the body's half width), how far it has gone out from there
     * (blocks), how high (a share of the body's height), how strongly it shows, its size (times MOTE), how bright (0
     * deep purple to 1 lilac)}. Each comes out of the body at a place and a height of its own, goes straight out from
     * it, quick at first and slowing, sinking a little, and fades; then it comes again somewhere else. They are spread
     * over the time, so many are always on their way out: a purple scattering off the body, all its time.
     */
    static float[] mote(int k,float clock,int seed){
        float turn=clock/LIFE+k/(float)MOTES+hash(seed,k)*.37f;
        int round=(int)Math.floor(turn);float u=turn-round;
        float angle=(float)(hash(seed+round*131,k*17+3)*Math.PI*2);
        float reach=(float)(REACH_LEAST+(REACH_MOST-REACH_LEAST)*hash(seed+round*57,k*5+1));
        // out of the body (from within it: it is seen as it leaves), quick at first and slowing
        float gone=1-(1-u)*(1-u);
        float high=.12f+.83f*hash(seed+round*91,k*7+2)-.08f*u*u;
        float show=Ease.smooth(u,0,.1f)*(1-Ease.smooth(u,.45f,1));
        float size=.7f+.6f*hash(seed+round*29,k*11+7);
        return new float[]{angle,.7f,reach*gone,high,show,size*(1-.35f*u),hash(seed+round*13,k*3+5)};
    }
    /** How strongly one weakened shows its weakening {@code on} seconds after this client knew of it, {@code left} seconds of it left. */
    static float shows(float on,float left){return left<=0?0:Ease.smooth(on,0,IN)*Ease.smooth(left,0,OUT);}
    /** How big the words are {@code on} seconds after it took (1 their size): popped in from bigger. */
    static float pop(float on,boolean fresh){return fresh?1+.4f*(1-Ease.smooth(on,0,.2f)):1;}

    /** One weakened to draw this frame. */
    private record Shown(Vec3d feet,double height,double width,float clock,float on,float alpha,int seed,boolean fresh,float near){}
    /** A "!!!" (or a blocked flash) to draw this frame. */
    private record Marked(Vec3d feet,double height,float t,float shown,float blocked,double over){}
    private static List<Shown> shown=List.of();
    private static List<Marked> marks=List.of();
    /** Works out what to draw this frame; whether there is anything. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        if(MARKS.isEmpty()&&WEAK.isEmpty()||mc.world==null){shown=List.of();marks=List.of();return false;}
        var out=new ArrayList<Shown>();var ms=new ArrayList<Marked>();
        for(var d:MARKS.values()){
            if(!(mc.world.getEntityById(d.target) instanceof LivingEntity e)||!e.getUuid().equals(d.targetId))continue;
            float t=d.age(delta);Vec3d feet=e.getLerpedPos(delta);
            if(feet.squaredDistanceTo(camera)>96*96)continue;
            if(d.blocked()&&t>=d.ended&&t<d.ended+6)ms.add(new Marked(feet,e.getHeight(),t,0,(t-d.ended)/6,BANGS));
            // (shown a little past APPLY until the server says it took)
            else if(!d.ended()&&t>=MARK&&t<APPLY+6)ms.add(new Marked(feet,e.getHeight(),t,1,-1,BANGS));
        }
        for(var w:WEAK.values()){
            if(!(mc.world.getEntityById(w.entity) instanceof LivingEntity e)||!e.getUuid().equals(w.id)||!e.isAlive())continue;
            Vec3d feet=e.getLerpedPos(delta);
            if(feet.squaredDistanceTo(camera)>96*96)continue;
            float on=w.on(delta)/20f,left=w.left(delta)/20f,a=shows(on,left);if(a<=0)continue;
            out.add(new Shown(feet,e.getHeight(),e.getWidth(),on,on,a,w.seed,w.fresh,1));
        }
        shown=out;marks=ms;
        return !out.isEmpty()||!ms.isEmpty();
    }
    /** The blended pass: the motes' purple, the "!!!"s' marks. */
    public static void alpha(EffectMesh m){
        for(var x:marks)if(x.shown>0)SwordDance.bangs(m,m.camera(),x.feet.add(0,x.height+x.over,0),x.t-MARK,x.shown,false);
        for(var x:shown)motes(m,x,false);
    }
    /** The light, added: a little glow of each mote, the haze round the body, the ring coming down them as it takes, the ring at their feet, a blocked one's flash. */
    public static void additive(EffectMesh m){
        for(var x:marks){
            if(x.shown>0)SwordDance.bangs(m,m.camera(),x.feet.add(0,x.height+x.over,0),x.t-MARK,x.shown,true);
            else if(x.blocked>=0){
                float s=x.blocked;
                m.softDisc(x.feet.add(0,x.height*.6,0),.8+1.6*s,0xd8f0ff,.85f*(1-s));
                FxLib.ring(m,x.feet.add(0,.05,0),.6+2.2*s,.07,0xbfe6ff,.8f*(1-s));
            }
        }
        for(var x:shown){
            motes(m,x,true);
            // at their feet, a faint ring breathing
            float breath=.6f+.4f*(float)Math.sin(x.clock*3.1f);
            FxLib.ring(m,x.feet.add(0,.04,0),x.width*.5+.38,.05,GLOW,.34f*x.alpha*breath);
            // as it takes: a ring coming down them, head to feet, and a flash
            if(x.fresh&&x.on<.55f){
                float s=x.on/.55f,down=(float)Math.pow(s,.8);
                Vec3d at=x.feet.add(0,x.height*(1.05-1.0*down),0);
                FxLib.ring(m,at,x.width*.5+.3+.25*Math.sin(s*Math.PI),.09,LILAC,.9f*(1-s));
                FxLib.ring(m,at.add(0,.12,0),x.width*.5+.2+.2*Math.sin(s*Math.PI),.05,GLOW,.7f*(1-s));
                m.softDisc(x.feet.add(0,x.height*.55,0),1.1+.8*s,GLOW,.5f*(1-s)*(1-s));
            }
        }
    }
    private static void motes(EffectMesh m,Shown x,boolean light){
        Vec3d mid=x.feet.add(0,x.height*.5,0);
        for(int k=0;k<MOTES;k++){
            float[] a=mote(k,x.clock,x.seed);if(a[4]<=0)continue;
            double r=x.width*.5*a[1]+a[2];
            Vec3d p=x.feet.add(Math.cos(a[0])*r,x.height*a[3],Math.sin(a[0])*r);
            float near=near(p,m.camera());if(near<=0)continue;
            float show=a[4]*x.alpha*near;
            Vec3d[] f=facing(m.camera(),p);
            double s=MOTE*a[5]*.5;
            if(light){
                m.softDisc(p,s*3.2,GLOW,.16f*show);
                // (a short streak behind it, back toward the body: it is going out)
                Vec3d back=new Vec3d(-Math.cos(a[0]),0,-Math.sin(a[0])).multiply(Math.min(a[2],.28));
                m.softTaper(p,p.add(back),s*.9,.005,a[6]>.5f?LILAC:GLOW,.3f*show);
            }else{
                // a little square facing the viewer: a dark line round it, its purple laid over (not added: purple before a bright sky)
                square(m,p,f[0],f[1],s+.012,INK,.8f*show);
                square(m,p,f[0],f[1],s,a[6]>.72f?LILAC:a[6]>.3f?PURPLE:DEEP,show);
            }
        }
        if(light){
            // the aura itself: a purple haze round the body, breathing
            float breath=.75f+.25f*(float)Math.sin(x.clock*2.4f);
            m.softDisc(mid,x.width*.5+.8,GLOW,.22f*x.alpha*breath);
            m.softDisc(x.feet.add(0,x.height*.22,0),x.width*.5+.5,DEEP,.2f*x.alpha*breath);
            m.softDisc(x.feet.add(0,x.height*.85,0),x.width*.5+.45,GLOW,.15f*x.alpha*breath);
        }
    }
    private static void square(EffectMesh m,Vec3d p,Vec3d right,Vec3d up,double h,int rgb,float alpha){
        Vec3d r=right.multiply(h),u=up.multiply(h);
        m.quad(p.subtract(r).add(u),p.add(r).add(u),p.add(r).subtract(u),p.subtract(r).subtract(u),rgb,alpha);
    }
    /** A little purple light on the world round one weakened. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var x:shown)f.light(x.feet.add(0,x.height*.5,0),GLOW,2.6,(.22f+(x.fresh&&x.on<.5f?.5f*(1-x.on/.5f):0))*x.alpha);
    }

    // ───────────────────────────── the words ─────────────────────────────

    /**
     * The words over the head of everyone weakened, in the world facing the viewer: the game's own pixel typeface (as a
     * name's; the user: "글자 폰트를 도트 처럼"), a strong purple with a dark line round each letter. Where a name is drawn
     * over the head, and over it; not over the viewer's own head in the first person. A little bigger from far off, so
     * they can still be read.
     */
    public static void text(MinecraftClient mc,MatrixStack matrices,float delta,Vec3d camera){
        if(shown.isEmpty()||mc.world==null)return;
        var vertices=mc.getBufferBuilders().getEntityVertexConsumers();boolean drew=false;
        var words=net.minecraft.text.Text.literal(WORDS).asOrderedText();float wide=mc.textRenderer.getWidth(words);
        for(var w:WEAK.values()){
            if(!(mc.world.getEntityById(w.entity) instanceof LivingEntity e)||!e.getUuid().equals(w.id)||!e.isAlive())continue;
            if(e==mc.getCameraEntity()&&!mc.gameRenderer.getCamera().isThirdPerson())continue;
            float on=w.on(delta)/20f,left=w.left(delta)/20f,a=shows(on,left);if(a<=0)continue;
            Vec3d head=e.getLerpedPos(delta).add(0,e.getHeight(),0);
            double far=head.distanceTo(camera);if(far>64||far<.6)continue;
            // (their foot stays where it is over the head as they grow with the distance)
            double half=DOT*4;
            Vec3d over=head.add(0,OVER-half+half*grown(far),0),toCamera=camera.subtract(over);
            // drawn nearer the viewer along their line of sight, before the body and the name: where they are on the screen is
            // the same, and nothing behind them cuts them off
            double all=toCamera.length(),nearer=all<1e-2?0:Math.min(all*.5,e.getWidth()*.5+.3);
            Vec3d at=nearer<=0?over:over.add(toCamera.multiply(nearer/all));
            // (and smaller by as much as they are nearer, so they look the size they are where they stand)
            float seen=nearer<=0?1:(float)((all-nearer)/all);
            // (blinking a little as it goes, as a thing on a screen)
            float blink=left<OUT?((int)Math.floor(left*26)%2==0?1:.35f):1;
            int alpha=Math.max(8,(int)(a*blink*255))<<24;
            matrices.push();matrices.translate(at.x,at.y,at.z);matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            float s=(float)(DOT*grown(far))*pop(on,w.fresh)*seen;matrices.scale(-s,-s,s);
            mc.textRenderer.drawWithOutline(words,-wide/2,-4,alpha|WORD,alpha|WORD_LINE,matrices.peek().getPositionMatrix(),vertices,net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();drew=true;
        }
        if(drew)vertices.draw();
    }
    /** How much bigger the words are from {@code far} blocks off (1 near, up to 1.5 from 15 blocks: they stay readable). */
    static float grown(double far){return (float)Math.max(1,Math.min(1.5,far/10));}

    /** For the capture tools and the checks: whom this client holds weakened, as "entity:seconds left", and its casts as "user's age,how". */
    public static String describe(float delta){
        if(WEAK.isEmpty()&&MARKS.isEmpty())return "";
        var b=new StringBuilder();
        for(var w:WEAK.values())b.append(b.length()>0?" ":"").append(w.entity).append(':').append(String.format(java.util.Locale.ROOT,"%.1f",w.left(delta)/20f));
        for(var d:MARKS.values())b.append(b.length()>0?" ":"").append("cast@").append(String.format(java.util.Locale.ROOT,"%.1f",d.age(delta))).append(',').append(d.how);
        return b.toString();
    }
    /** For the checks: whether this player's warning sound has begun on this client (null if no cast of theirs is known). */
    public static String sounds(UUID user){var d=MARKS.get(user);return d==null?null:d.warn==null?"-":d.warn.begun()?"1":"0";}
}
