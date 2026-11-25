package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.xchara.XCharaBlink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.xchara.GlitchFx.*;
import static dev.sangwoo.celestialfall.xchara.BlinkRules.*;

/**
 * X Chara's Z, the teleport, as every client shows it (2.138.0, user request: "z스킬을 누르면 몸에 보라색 아우라가 생기고
 * 앞으로 짧게 순간이동하는 스킬이야", with a screen recording; XCharaBlink for its rules, BlinkRules for its timing). Z on
 * the map asks for it (XCharaClient.key), the hack on or off. The server tells when it starts, when its user is moved
 * (from where to where), and when it is called off.
 * <p>
 * What it draws, as the video has it: from the press their body flickering between itself and a flat purple
 * silhouette, knife and all ({@link #solid}: drawn again through their own renderer in one colour, as the pull's),
 * purple bars torn across it between ({@link #dash}) and a purple glow about them; as they are moved, them where the
 * server put them from the frame this client hears of it ({@link #offset}), the bars left hanging where they stood,
 * thinning out to nothing in half a second ({@link #left}), and their body flickering on where they came, its purple
 * gone in a fifth of a second and its bars in half. The sound is the pull's burst, as the user said (the recording's
 * is that very file: "기존에 있던거 쓰고"), begun as this client hears of the move. No screen effect. Everything is a
 * function of its clock.
 */
public final class BlinkClient {
    /** The burst of sound is not played if this client hears of the move more than this many ticks after it should have come. */
    static final int SOUND_LATE=4;
    /** For how long their user is drawn where they were put though this client has them elsewhere yet (ticks), and how near they must be to count as there (blocks). */
    static final int SETTLE=8;static final double THERE=.4;
    /** How many bars may be torn across the body at once, and left where they stood; how often they change (a second). */
    static final int DASHES=22,LEFT_DASHES=26;static final float RATE=30;
    /** The silhouette's purple and how much bigger than the body it is drawn (the pull's: the video's two are the one look). */
    static final int FLAT=PullClient.FLAT;static final float SWELL=PullClient.SWELL;
    /** The bars' vivid magenta purple (the pull's). */
    static final int BAR=PullClient.BAR;
    /**
     * The flicker, by the video's frames of a sixtieth: before the move (from the press, over again if the move is
     * late) and after it. 1 the flat purple silhouette, 0 themselves with the bars across them.
     */
    static final int[] BEFORE={1,1,0,1,1,0,0,0,1,1,0,1},ARRIVED={1,1,0,0,0,0,1,1,1,1,1};

    /** One as this client heard of it. */
    static final class Cast {
        final UUID user;final int id;final long start,heard;final int received;final float yaw;final int seed;
        float ended=Float.NaN;int how=-1;Vec3d from,to;boolean there;
        XCharaClient.Voice burst;
        Cast(UUID user,int id,long start,long heard,float yaw){
            this.user=user;this.id=id;this.start=start;this.heard=heard;received=StandClient.ticks();this.yaw=yaw;seed=(int)(start*31+id*7919);
        }
        float age(float delta){return heard-start+(StandClient.ticks()-received)+delta;}
        boolean ended(){return !Float.isNaN(ended);}
        boolean gone(){return how==XCharaBlink.GONE;}
        boolean cut(){return how==XCharaBlink.CUT;}
        /** Seconds since the press. */
        float sec(float delta){return age(delta)/20f;}
        /** Seconds since the move (negative before it, or if there was none). */
        float since(float delta){return gone()?(age(delta)-ended)/20f:-1;}
    }
    private static final Map<UUID,Cast> CASTS=new HashMap<>();
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static Object world;
    private BlinkClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaBlink.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();int id=b.readVarInt();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong();float yaw=b.readFloat();
            Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),to=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
            long ready=b.readLong();
            if(!Float.isFinite(yaw)||type>XCharaBlink.CUT||!finite(from)||!finite(to))return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                if(world!=mc.world){clear();world=mc.world;}
                if(type!=XCharaBlink.START)READY.put(user,ready);
                if(type==XCharaBlink.START){
                    if(now<start||now-start>GO+40)return;
                    var old=CASTS.remove(user);if(old!=null)quiet(old);
                    CASTS.put(user,new Cast(user,id,start,now,yaw));
                    return;
                }
                var d=CASTS.get(user);
                if(d==null||d.start!=start||d.ended())return;
                d.ended=d.age(0);d.how=type;
                if(type!=XCharaBlink.GONE)return;
                d.from=from;d.to=to;
                // the burst of the move, begun as this client hears of it (its file starts on its onset); none if it comes
                // long after (SOUND_LATE)
                if(d.age(0)<=GO+SOUND_LATE){
                    d.burst=new XCharaClient.Voice(CelestialFall.id("xchara_pull"),()->loudness(mc,d));
                    mc.getSoundManager().play(d.burst);
                }
                // this client has them there at once (their shadow and their label with them): not sliding there over the
                // three ticks the game takes to bring a tracked body to its new place (its own player the game puts there)
                var e=mc.world.getEntityById(d.id);
                if(e!=null&&e!=mc.player&&e.getUuid().equals(d.user)){
                    e.setPosition(to);e.resetPosition();e.updateTrackedPositionAndAngles(to.x,to.y,to.z,e.getYaw(),e.getPitch(),1,false);
                }
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(BlinkClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(BlinkClient::clear));
        WorldRenderEvents.AFTER_ENTITIES.register(BlinkClient::render);
    }
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static void quiet(Cast d){if(d.burst!=null&&!d.burst.isDone())d.burst.fade();}
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var d:CASTS.values())if(d.burst!=null&&!d.burst.isDone())mc.getSoundManager().stop(d.burst);
        CASTS.clear();READY.clear();shown=List.of();lefts=List.of();world=null;
    }
    /** Its sound for this client: loud for its user, fading out to 40 blocks from the nearer of where they stood and where they came for the rest. */
    static float loudness(MinecraftClient mc,Cast d){
        if(mc.player==null||mc.world==null||d.from==null)return 0;
        if(mc.player.getUuid().equals(d.user))return (float)CelestialFall.CONFIG.effectVolume;
        double far=Math.sqrt(Math.min(mc.player.squaredDistanceTo(d.from),mc.player.squaredDistanceTo(d.to)));
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/40));
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!CASTS.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        CASTS.values().removeIf(d->d.age(0)>GO+60);
        READY.values().removeIf(at->at<mc.world.getTime()-20);
        for(var d:CASTS.values()){
            if(!d.gone()||d.there)continue;
            var e=mc.world.getEntityById(d.id);
            if(e==null||!e.getUuid().equals(d.user)||e.getPos().squaredDistanceTo(d.to)<THERE*THERE||d.age(0)-d.ended>SETTLE)d.there=true;
        }
    }

    // ───────────────────────────── who ─────────────────────────────

    /** When this player's is ready again (the server's world time; long ago if never). */
    public static long ready(UUID player){return READY.getOrDefault(player,Long.MIN_VALUE);}
    private static Cast of(Entity e){
        if(CASTS.isEmpty()||e==null)return null;
        var d=CASTS.get(e.getUuid());
        return d!=null&&d.id==e.getId()?d:null;
    }
    /**
     * Where one moved is drawn from where this client has them (EntityRenderer.getPositionOffset): at once where the
     * server put them, from the frame this client hears of the move, while their tracked place still catches up. Not
     * this client's own player: the game puts them there at once.
     */
    public static Vec3d offset(Entity e,float delta){
        var d=of(e);if(d==null||!d.gone()||d.there||e==MinecraftClient.getInstance().player)return null;
        return d.to.subtract(e.getLerpedPos(delta));
    }

    // ───────────────────────────── the flicker ─────────────────────────────

    private static float hash(int a,int b){int h=a*374761393+b*668265263;h=(h^(h>>>13))*1274126177;h^=h>>>16;return (h&0xffffff)/(float)0x1000000;}
    /**
     * Whether they are the flat purple silhouette {@code sec} seconds after the press, moved {@code since} seconds ago
     * (negative: not yet): by the video's frames of a sixtieth, BEFORE until the move and ARRIVED after it, and never
     * past that.
     */
    static boolean solid(float sec,float since){
        if(since>=0){int k=(int)Math.floor(since*60+1e-3);return k<ARRIVED.length&&ARRIVED[k]==1;}
        if(sec<0)return false;
        return BEFORE[(int)Math.floor(sec*60+1e-3)%BEFORE.length]==1;
    }
    /** How much of the bars across their body shows (0 to 1): all from the press and for a fifth of a second after the move, thinning out to nothing by AFTER. */
    static float bars(float sec,float since){
        if(since>=0)return since>=AFTER/20f?0:1-Ease.smooth(since,.2f,AFTER/20f);
        return sec<0?0:1;
    }
    /** How much of the bars left where they stood shows {@code since} seconds after the move: all at first, thinning out to nothing by LEFT. */
    static float left(float since){return since<0||since>=LEFT/20f?0:1-Ease.smooth(since,.08f,LEFT/20f);}
    /**
     * Bar {@code j} torn across a body {@code height} high and {@code width} wide at {@code clock} seconds of its
     * flicker, {@code much} of them showing: {how high its middle is (of the height), how far to the side (blocks),
     * its half width, its half height, its colour}, or null while it does not show. Short dashes, other every
     * thirtieth of a second.
     */
    static double[] dash(int j,float clock,float much,int seed,double width){
        if(much<=0)return null;
        int n=(int)Math.floor(clock*RATE);
        if(hash(seed+n*131,j*7+1)>=.7f*much)return null;
        float c=hash(seed+n*17,j*7+5),h=hash(seed+n*71,j*7+2);
        return new double[]{j%3==0?.08+.84*h:.1+.58*h,(hash(seed+n*37,j*7+4)-.5)*width*1.3,width*(.1+.24*hash(seed+n*29,j*7+3)),
            .018+.016*hash(seed+n*43,j*7+6),c<.7f?BAR:c<.86f?PURPLE:LILAC};
    }
    /**
     * Bar {@code j} of those left where they stood, as {@link #dash}: a knot of them about their chest's height (the
     * video's: between their waist and the top of their head, no wider than they were).
     */
    static double[] leftDash(int j,float since,int seed,double width){
        float much=left(since);if(much<=0)return null;
        int n=(int)Math.floor(since*RATE);
        if(hash(seed+n*113,j*5+1)>=.75f*much)return null;
        float c=hash(seed+n*19,j*5+4);
        return new double[]{.5+.42*hash(seed+n*67,j*5+2),(hash(seed+n*31,j*5+3)-.5)*width*1.1,width*(.12+.26*hash(seed+n*23,j*5+3)),
            .018+.016*hash(seed+n*47,j*5+2),c<.7f?BAR:c<.86f?PURPLE:LILAC};
    }
    /** How strongly the flash of the move shows on them {@code since} seconds after it (1 as they are put there, gone in a fifth of a second). */
    static float flash(float since){return since<0?0:(float)Math.exp(-since/.06f)*(1-Ease.smooth(since,.12f,.2f));}
    /** How strongly the purple about their body glows: on in two frames from the press, there until the move and a fifth of a second after, gone by AFTER (called off: gone in a tenth). */
    static float aura(float sec,float since,float cutFor){
        if(sec<0)return 0;
        float a=Ease.smooth(sec,0,.035f);
        if(since>=0)a*=1-Ease.smooth(since,.2f,AFTER/20f);
        if(cutFor>=0)a*=1-Ease.smooth(cutFor,0,.1f);
        return a;
    }

    /** A body flickering to draw this frame, and bars left where one stood. */
    private record Shown(Vec3d feet,double height,double width,float sec,float since,float aura,int seed){}
    private record Left(Vec3d feet,double height,double width,float since,int seed){}
    private static List<Shown> shown=List.of();
    private static List<Left> lefts=List.of();
    /** Where this one's feet are drawn this frame (with where the move has them). */
    private static Vec3d feet(Entity e,float delta){Vec3d p=e.getLerpedPos(delta),o=offset(e,delta);return o==null?p:p.add(o);}
    /** Seconds since it was called off (negative if it was not). */
    private static float cutFor(Cast d,float delta){return d.cut()?(d.age(delta)-d.ended)/20f:-1;}
    /** Works out what to draw this frame; whether there is anything. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        if(CASTS.isEmpty()||mc.world==null){shown=List.of();lefts=List.of();return false;}
        var out=new ArrayList<Shown>();var ls=new ArrayList<Left>();
        for(var d:CASTS.values()){
            float sec=d.sec(delta),since=d.since(delta),cut=cutFor(d,delta);
            if(since>=0&&since<LEFT/20f&&d.from.squaredDistanceTo(camera)<96*96){
                double h=1.8,w=.6;
                if(mc.world.getEntityById(d.id) instanceof LivingEntity e){h=e.getHeight();w=e.getWidth();}
                ls.add(new Left(d.from,h,w,since,d.seed^0x5bd1));
            }
            if(!(mc.world.getEntityById(d.id) instanceof LivingEntity e)||!e.getUuid().equals(d.user)||!e.isAlive())continue;
            if(sec<0||since>=AFTER/20f+.05f||cut>=.1f||!d.ended()&&sec>GO/20f+.5f)continue;
            // (not this client's own body in the first person)
            if(e==mc.getCameraEntity()&&!mc.gameRenderer.getCamera().isThirdPerson())continue;
            Vec3d feet=feet(e,delta);
            if(feet.squaredDistanceTo(camera)>96*96)continue;
            out.add(new Shown(feet,e.getHeight(),e.getWidth(),sec,cut>=0?-2:since,aura(sec,since,cut),d.seed));
        }
        shown=out;lefts=ls;
        return !out.isEmpty()||!ls.isEmpty();
    }
    /** The blended pass: the bars torn across them, and those left where they stood. */
    public static void alpha(EffectMesh m){
        for(var x:shown)if(x.since>-2)dashes(m,x,false);
        for(var x:lefts)leftDashes(m,x,false);
    }
    /** The light, added: the purple about them, a little glow of the bars, the flash of the move. */
    public static void additive(EffectMesh m){
        for(var x:shown){
            if(x.since>-2)dashes(m,x,true);
            Vec3d mid=x.feet.add(0,x.height*.5,0);float near=near(mid,m.camera());
            if(x.aura>0&&near>0){
                // (brighter the frames they are the silhouette)
                float lit=x.since>-2&&solid(x.sec,x.since)?1:.6f;
                m.softDisc(mid,x.width*.5+.95,GLOW,.34f*x.aura*lit*near);
                m.softDisc(mid.add(0,x.height*.1,0),x.width*.5+.5,PURPLE,.22f*x.aura*lit*near);
            }
            float f=flash(x.since);
            if(f>0)m.softDisc(x.feet.add(0,x.height*.55,0),.9+.5*x.since/.2,GLOW,.55f*f);
        }
        for(var x:lefts){
            leftDashes(m,x,true);
            float l=left(x.since);
            Vec3d knot=x.feet.add(0,x.height*.7,0);
            m.softDisc(knot,x.width*.5+.4,GLOW,.2f*l*near(knot,m.camera()));
        }
    }
    private static void dashes(EffectMesh m,Shown x,boolean light){
        // (none over the silhouette: the frames they are it, they are one flat purple)
        if(solid(x.sec,x.since)&&FxLayer.available())return;
        Vec3d mid=x.feet.add(0,x.height*.5,0);float near=near(mid,m.camera());if(near<=0)return;
        Vec3d[] f=facing(m.camera(),mid);Vec3d front=f[2].multiply(x.width*.5+.14);
        float much=bars(x.sec,x.since);
        for(int j=0;j<DASHES;j++){
            double[] b=dash(j,x.sec,much,x.seed,x.width);if(b==null)continue;
            Vec3d p=x.feet.add(0,b[0]*x.height,0).add(f[0].multiply(b[1])).add(front);
            if(light)bar(m,p,f,b[2]*1.2,b[3]*2.6,GLOW,.22f*near);
            // (its purple laid over, not added: purple before a bright sky)
            else bar(m,p,f,b[2],b[3],(int)b[4],near);
        }
    }
    private static void leftDashes(EffectMesh m,Left x,boolean light){
        Vec3d mid=x.feet.add(0,x.height*.7,0);float near=near(mid,m.camera());if(near<=0)return;
        Vec3d[] f=facing(m.camera(),mid);
        for(int j=0;j<LEFT_DASHES;j++){
            double[] b=leftDash(j,x.since,x.seed,x.width);if(b==null)continue;
            Vec3d p=x.feet.add(0,b[0]*x.height,0).add(f[0].multiply(b[1]));
            if(light)bar(m,p,f,b[2]*1.2,b[3]*2.4,GLOW,.14f*near);
            else bar(m,p,f,b[2],b[3],(int)b[4],near);
        }
    }
    /** A little purple light on the world round them, and where they stood as they go. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var x:shown){
            if(x.aura>0)f.light(x.feet.add(0,x.height*.5,0),GLOW,3.0,.35f*x.aura);
            float l=flash(x.since);if(l>0)f.light(x.feet.add(0,x.height*.5,0),GLOW,3.2,.6f*l);
        }
        for(var x:lefts){float l=left(x.since);if(l>0)f.light(x.feet.add(0,x.height*.6,0),GLOW,2.6,.4f*l);}
    }

    // ───────────────────────────── the silhouette ─────────────────────────────

    /**
     * Draws the ones teleporting as their flat purple silhouette, the frames they are it: each drawn again through
     * their own renderer where they are drawn, a little bigger, every layer of theirs the one colour (the knife in
     * their hand with them, as the video has it). Not where a shader pack draws the world, and not this client's own
     * body in the first person.
     */
    private static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();
        if(CASTS.isEmpty()||mc.world==null||!(ctx.consumers() instanceof VertexConsumerProvider.Immediate immediate)||!FxLayer.available())return;
        float delta=ctx.tickDelta();Vec3d cam=ctx.camera().getPos();
        boolean drew=false;
        for(var d:CASTS.values()){
            float sec=d.sec(delta),since=d.since(delta);
            if(d.cut()||!d.ended()&&sec>GO/20f+.5f||!solid(sec,since))continue;
            if(!(mc.world.getEntityById(d.id) instanceof LivingEntity e)||!e.getUuid().equals(d.user)||!e.isAlive()||e.isInvisible())continue;
            if(e==mc.getCameraEntity()&&!ctx.camera().isThirdPerson())continue;
            Vec3d at=feet(e,delta);
            if(at.squaredDistanceTo(cam)>96*96)continue;
            FlatBody.draw(mc,ctx.matrixStack(),immediate,cam,e,at,delta,FLAT,SWELL);
            drew=true;
        }
        if(drew)immediate.draw(RenderLayer.getDebugQuads());
    }

    /**
     * Whether this one is the flat purple silhouette this frame (and it is not the silhouette being drawn now): what
     * they hold is not drawn as itself then (StandHeldItemFeatureMixin). The knife is three blocks long and thin: the
     * silhouette, a little bigger about the body's middle, does not cover it, and drawn as itself it showed black
     * through the purple from most sides (the video's knife is purple with the body).
     */
    public static boolean flat(Entity e){
        if(CASTS.isEmpty()||FlatBody.drawing()||!FxLayer.available())return false;
        var d=of(e);if(d==null||d.cut())return false;
        float delta=MinecraftClient.getInstance().getTickDelta(),sec=d.sec(delta);
        return (d.ended()||sec<=GO/20f+.5f)&&solid(sec,d.since(delta));
    }

    /** For the capture tools and the checks: its casts as "age,how,seconds since the move,there". */
    public static String describe(float delta){
        if(CASTS.isEmpty())return "";
        var b=new StringBuilder();
        for(var d:CASTS.values())b.append(b.length()>0?" ":"").append(String.format(java.util.Locale.ROOT,"blink@%.1f,%d,%.2f,%d",d.age(delta),d.how,d.since(delta),d.there?1:0));
        return b.toString();
    }
    /** For the checks: whether this player's burst has begun on this client (1 or 0; - if it was never asked for), or null if no cast of theirs is known. */
    public static String sounds(UUID user){var d=CASTS.get(user);return d==null?null:d.burst==null?"-":d.burst.begun()?"1":"0";}
}
