package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.xchara.XCharaPull;
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
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.client.xchara.GlitchFx.*;
import static dev.sangwoo.celestialfall.xchara.PullRules.*;

/**
 * X Chara's Y with the hack on, the pull, as every client shows it (2.127.0, user request: "이번에는 q활성화 Y스킬
 * 효과음은 영상에 있고 영 상 보면 상대에게 !!!가 뜨고 상대가 내 앞으로 순간이동 돼", with a screen recording; XCharaPull
 * for its rules, PullRules for its timing). Y on the map with the hack on asks for it with the one the camera's middle
 * looks at (XCharaClient.key, as G, R and T). The server tells when it starts, when the one marked is pulled (from
 * where to where), and when it is blocked or called off.
 * <p>
 * What it draws, as the video has it: the "!!!" over the one marked until the pull (G's own, SwordDance.bangs, with
 * G's warning sound as it starts: the video's is that very sound); the one pulled where the server put them from the
 * frame this client hears of it ({@link #offset}: not sliding there as their tracked place catches up); for half a
 * second after, their body flickering between a flat purple silhouette ({@link #solid}: drawn again through their own
 * renderer in one colour) and purple bars torn across it ({@link #dash}), the bars thinning out to nothing; a pale
 * flash on them if their guard blocked it. The video's burst of sound comes with the pull (xchara_pull, lifted from the
 * recording: tools/xchara/extract_pull.py). Its user's panel is pressed as it is called ({@link #press}). No screen
 * effect. Everything is a function of its clock.
 */
public final class PullClient {
    /** The "!!!" stands this far over the head (G's, R's and T's place). */
    static final double BANGS=1.15;
    /** The burst of sound is not played if this client hears of the pull more than this many ticks after it should have come. */
    static final int SOUND_LATE=4;
    /** For how long the one pulled is drawn where they were put though this client has them elsewhere yet (ticks), and how near they must be to count as there (blocks). */
    static final int SETTLE=8;static final double THERE=.4;
    /** How many bars may be torn across the body at once after the pull, and how often they and the silhouette change (a second: the video's two frames of sixty). */
    static final int DASHES=24;static final float RATE=30;
    /** The silhouette's purple (the video's), and how much bigger than the body it is drawn (so it covers it). */
    static final int FLAT=0x7c30ff;static final float SWELL=1.035f;
    /** The bars' colours: a vivid magenta purple mostly (the video's), some lilac. */
    static final int BAR=0xc23cff;

    /** One being cast as this client heard of it. */
    static final class Mark {
        final UUID user,targetId;final int target;final long start,heard;final int received;final float yaw;final int seed;
        float ended=Float.NaN;int how=-1;Vec3d from,to;boolean there;
        XCharaClient.Voice warn,burst;
        Mark(UUID user,int target,UUID targetId,long start,long heard,float yaw){
            this.user=user;this.target=target;this.targetId=targetId;this.start=start;this.heard=heard;received=StandClient.ticks();this.yaw=yaw;seed=(int)(start*31+target*7919);
        }
        float age(float delta){return heard-start+(StandClient.ticks()-received)+delta;}
        boolean ended(){return !Float.isNaN(ended);}
        boolean pulled(){return how==XCharaPull.PULLED;}
        boolean blocked(){return how==XCharaPull.BLOCKED;}
        boolean stopped(){return how==XCharaPull.BLOCKED||how==XCharaPull.CUT;}
        /** Seconds since the pull (negative before it, or if there was none). */
        float since(float delta){return pulled()?(age(delta)-ended)/20f:-1;}
    }
    private static final Map<UUID,Mark> MARKS=new HashMap<>();
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static Object world;
    private PullClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaPull.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong();int target=b.readVarInt();UUID targetId=b.readUuid();float yaw=b.readFloat();
            Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),to=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
            long ready=b.readLong();
            if(!Float.isFinite(yaw)||type>XCharaPull.CUT||!finite(from)||!finite(to))return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                if(world!=mc.world){clear();world=mc.world;}
                if(type!=XCharaPull.START)READY.put(user,ready);
                if(type==XCharaPull.START){
                    if(now<start||now-start>PULL+40)return;
                    var old=MARKS.remove(user);if(old!=null)quiet(old);
                    var d=new Mark(user,target,targetId,start,now,yaw);MARKS.put(user,d);
                    // its user looks at the one marked (the server turned them too)
                    if(mc.player!=null&&mc.player.getUuid().equals(user)){mc.player.setYaw(yaw);mc.player.setHeadYaw(yaw);mc.player.setBodyYaw(yaw);mc.player.prevYaw=yaw;}
                    // G's warning sound (the video's own), none if heard too late to come with the "!!!"
                    if(d.age(0)<=MARK+4){
                        d.warn=new XCharaClient.Voice(CelestialFall.id("xchara_g_warn"),()->loudness(mc,d));
                        mc.getSoundManager().play(d.warn);
                    }
                    return;
                }
                var d=MARKS.get(user);
                if(d==null||d.start!=start||d.ended())return;
                d.ended=d.age(0);d.how=type;
                if(type==XCharaPull.PULLED){
                    d.from=from;d.to=to;
                    // the burst of the pull, begun as this client hears of it (its file starts on its onset): on the one put
                    // there, whenever the server's word comes; none if it comes long after (SOUND_LATE)
                    if(d.age(0)<=PULL+SOUND_LATE){
                        d.burst=new XCharaClient.Voice(CelestialFall.id("xchara_pull"),()->loudness(mc,d));
                        mc.getSoundManager().play(d.burst);
                    }
                    // this client has them there at once (their shadow and their label with them): not sliding there over the
                    // three ticks the game takes to bring a tracked body to its new place
                    var e=mc.world==null?null:mc.world.getEntityById(d.target);
                    if(e!=null&&e!=mc.player&&e.getUuid().equals(d.targetId)&&finite(to)){
                        e.setPosition(to);e.resetPosition();e.updateTrackedPositionAndAngles(to.x,to.y,to.z,e.getYaw(),e.getPitch(),1,false);
                    }
                }
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(PullClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(PullClient::clear));
        WorldRenderEvents.AFTER_ENTITIES.register(PullClient::render);
    }
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static void quiet(Mark d){
        if(d.warn!=null&&!d.warn.isDone())d.warn.fade();
        if(d.burst!=null&&!d.burst.isDone())d.burst.fade();
    }
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var d:MARKS.values()){
            if(d.warn!=null&&!d.warn.isDone())mc.getSoundManager().stop(d.warn);
            if(d.burst!=null&&!d.burst.isDone())mc.getSoundManager().stop(d.burst);
        }
        MARKS.clear();READY.clear();shown=List.of();marks=List.of();world=null;
    }
    /** Its sounds for this client: loud for its two, fading out to 40 blocks from the nearer of them for the rest. */
    static float loudness(MinecraftClient mc,Mark d){
        if(mc.player==null||mc.world==null)return 0;
        if(mc.player.getUuid().equals(d.user)||mc.player.getUuid().equals(d.targetId))return (float)CelestialFall.CONFIG.effectVolume;
        double far=Double.MAX_VALUE;
        var u=mc.world.getPlayerByUuid(d.user);if(u!=null)far=Math.min(far,mc.player.distanceTo(u));
        var t=mc.world.getEntityById(d.target);if(t!=null)far=Math.min(far,mc.player.distanceTo(t));
        return far==Double.MAX_VALUE?0:(float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/40));
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!MARKS.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        MARKS.values().removeIf(d->d.age(0)>PULL+60);
        READY.values().removeIf(at->at<mc.world.getTime()-20);
        // the one pulled is there once this client has them where the server put them
        for(var d:MARKS.values()){
            if(!d.pulled()||d.there)continue;
            var e=mc.world.getEntityById(d.target);
            if(e==null||!e.getUuid().equals(d.targetId)||e.getPos().squaredDistanceTo(d.to)<THERE*THERE||d.age(0)-d.ended>SETTLE)d.there=true;
        }
    }

    // ───────────────────────────── who ─────────────────────────────

    /** When this player's is ready again (the server's world time; long ago if never). */
    public static long ready(UUID player){return READY.getOrDefault(player,Long.MIN_VALUE);}
    /** Whether this one is held where they stand by one now (KagenoManager.frozen on this client): its user through its "!!!". */
    public static boolean holds(Entity e){
        if(e==null||MARKS.isEmpty()||!MapClient.enabled())return false;
        var d=MARKS.get(e.getUuid());
        return d!=null&&!d.ended()&&d.age(0)>=0&&d.age(0)<PULL+2;
    }
    /** The cast that pulled this one, if its flicker still shows on them. */
    private static Mark pulling(Entity e){
        if(MARKS.isEmpty())return null;
        for(var d:MARKS.values())if(d.pulled()&&d.target==e.getId()&&d.targetId.equals(e.getUuid()))return d;
        return null;
    }
    /**
     * Where the one pulled is drawn from where this client has them (EntityRenderer.getPositionOffset): at once where
     * the server put them, from the frame this client hears of the pull, while their tracked place still catches up
     * (another's body is moved over a few ticks; drawn so, they would slide there). Not this client's own player: they
     * are put there at once.
     */
    public static Vec3d offset(Entity e,float delta){
        if(MARKS.isEmpty()||e==null)return null;
        var d=pulling(e);if(d==null||d.there||e==MinecraftClient.getInstance().player)return null;
        return d.to.subtract(e.getLerpedPos(delta));
    }
    /** The panel pressed {@code sec} seconds into it (stopped short at {@code stopped}, NaN if not): {how far the arm and the panel have come before the body, how far the hand is down on it, how lit the panel is, 1 for violet}. */
    static float[] pressAt(float sec,float stopped){
        float lift=Ease.smooth(sec,0,.1f)*(1-Ease.smooth(sec,.3f,.5f));
        if(!Float.isNaN(stopped))lift*=1-Ease.smooth(sec,stopped,stopped+.15f);
        float tap=Ease.smooth(sec,.04f,.1f)*(1-Ease.smooth(sec,.13f,.22f));
        // lit as it is pressed, and again as the pull comes
        float lit=Math.max(Ease.smooth(sec,.08f,.1f)*(1-Ease.smooth(sec,.16f,.3f)),Ease.smooth(sec,PULL/20f-.04f,PULL/20f)*(1-Ease.smooth(sec,PULL/20f+.06f,PULL/20f+.3f)));
        return new float[]{lift,tap,Float.isNaN(stopped)?lit:0,1};
    }
    /** This player's press of the panel for their pull this frame, or null. */
    public static float[] press(UUID id,float delta){
        var d=MARKS.get(id);if(d==null)return null;
        float sec=d.age(delta)/20f;if(sec>PULL/20f+.4f)return null;
        return pressAt(sec,d.stopped()?d.ended/20f:Float.NaN);
    }

    // ───────────────────────────── the flicker ─────────────────────────────

    private static float hash(int a,int b){int h=a*374761393+b*668265263;h=(h^(h>>>13))*1274126177;h^=h>>>16;return (h&0xffffff)/(float)0x1000000;}
    /**
     * Whether the one pulled is the flat purple silhouette {@code s} seconds after the pull. As the video has it, by
     * its frames of a sixtieth: themselves for two (the "!!!" still over them), purple for two, bars, purple, bars,
     * purple for four, and bars from then on.
     */
    static boolean solid(float s){
        int k=(int)Math.floor(s*60+1e-3);
        return k>=2&&k<=3||k==5||k>=7&&k<=10;
    }
    /** How much of the bars shows {@code s} seconds after the pull (0 to 1): none before the first purple, all for a fifth of a second, thinning out to nothing by the half second. */
    static float bars(float s){
        if(s<1/30f||s>=GLITCH/20f)return 0;
        return 1-Ease.smooth(s,.2f,GLITCH/20f);
    }
    /**
     * Bar {@code j} torn across a body {@code height} high and {@code width} wide {@code s} seconds after the pull:
     * {how high its middle is (of the height), how far to the side (blocks), its half width, its half height, its
     * colour}, or null while it does not show. Short dashes, many at first, other every thirtieth of a second.
     */
    static double[] dash(int j,float s,int seed,double height,double width){
        float much=bars(s);if(much<=0)return null;
        int n=(int)Math.floor(s*RATE);
        if(hash(seed+n*131,j*7+1)>=.8f*much)return null;
        float c=hash(seed+n*17,j*7+5),h=hash(seed+n*71,j*7+2);
        // the video's: most of them on the trunk and the legs (two in three under the shoulders), some out past the body's side
        return new double[]{j%3==0?.08+.84*h:.1+.58*h,(hash(seed+n*37,j*7+4)-.5)*width*1.3,width*(.1+.24*hash(seed+n*29,j*7+3)),
            .018+.016*hash(seed+n*43,j*7+6),c<.7f?BAR:c<.86f?PURPLE:LILAC};
    }
    /** How strongly the flash of the pull shows on them {@code s} seconds after it (1 as they are put there, gone in a fifth of a second). */
    static float flash(float s){return s<0?0:(float)Math.exp(-s/.06f)*(1-Ease.smooth(s,.12f,.2f));}

    /** One pulled to draw this frame, and a "!!!" (or a blocked flash). */
    private record Shown(Vec3d feet,double height,double width,float s,int seed,float near){}
    private record Marked(Vec3d feet,double height,float t,float shown,float blocked){}
    private static List<Shown> shown=List.of();
    private static List<Marked> marks=List.of();
    /** Where this one's feet are drawn this frame (with where the pull has them). */
    private static Vec3d feet(Entity e,float delta){Vec3d p=e.getLerpedPos(delta),o=offset(e,delta);return o==null?p:p.add(o);}
    /** Works out what to draw this frame; whether there is anything. */
    public static boolean prepare(MinecraftClient mc,float delta,Vec3d camera){
        if(MARKS.isEmpty()||mc.world==null){shown=List.of();marks=List.of();return false;}
        var out=new ArrayList<Shown>();var ms=new ArrayList<Marked>();
        for(var d:MARKS.values()){
            if(!(mc.world.getEntityById(d.target) instanceof LivingEntity e)||!e.getUuid().equals(d.targetId))continue;
            float t=d.age(delta);Vec3d feet=feet(e,delta);
            if(feet.squaredDistanceTo(camera)>96*96)continue;
            if(d.blocked()&&t>=d.ended&&t<d.ended+6)ms.add(new Marked(feet,e.getHeight(),t,0,(t-d.ended)/6));
            // (over them through the pull and two of the video's frames after, where they stand then; a little past PULL until the server says how it went)
            else if(t>=MARK&&(!d.ended()&&t<PULL+6||d.pulled()&&d.since(delta)<1/30f))ms.add(new Marked(feet,e.getHeight(),t,1,-1));
            float s=d.since(delta);
            if(s>=0&&s<GLITCH/20f+.05f&&e.isAlive()){
                boolean own=e==mc.getCameraEntity()&&!mc.gameRenderer.getCamera().isThirdPerson();
                if(!own)out.add(new Shown(feet,e.getHeight(),e.getWidth(),s,d.seed,1));
            }
        }
        shown=out;marks=ms;
        return !out.isEmpty()||!ms.isEmpty();
    }
    /** The blended pass: the bars torn across the one pulled, the "!!!"s' marks. */
    public static void alpha(EffectMesh m){
        for(var x:marks)if(x.shown>0)SwordDance.bangs(m,m.camera(),x.feet.add(0,x.height+BANGS,0),x.t-MARK,x.shown,false);
        for(var x:shown)dashes(m,x,false);
    }
    /** The light, added: a little glow of the bars, the flash of the pull, a blocked one's flash. */
    public static void additive(EffectMesh m){
        for(var x:marks){
            if(x.shown>0)SwordDance.bangs(m,m.camera(),x.feet.add(0,x.height+BANGS,0),x.t-MARK,x.shown,true);
            else if(x.blocked>=0){
                float s=x.blocked;
                m.softDisc(x.feet.add(0,x.height*.6,0),.8+1.6*s,0xd8f0ff,.85f*(1-s));
                FxLib.ring(m,x.feet.add(0,.05,0),.6+2.2*s,.07,0xbfe6ff,.8f*(1-s));
            }
        }
        for(var x:shown){
            dashes(m,x,true);
            float f=flash(x.s);
            if(f>0)m.softDisc(x.feet.add(0,x.height*.55,0),.9+.5*x.s/.2,GLOW,.55f*f);
            // (a faint purple about them while they are the silhouette)
            if(solid(x.s))m.softDisc(x.feet.add(0,x.height*.5,0),x.width*.5+.75,GLOW,.2f);
        }
    }
    private static void dashes(EffectMesh m,Shown x,boolean light){
        Vec3d mid=x.feet.add(0,x.height*.5,0);float near=near(mid,m.camera());if(near<=0)return;
        Vec3d[] f=facing(m.camera(),mid);Vec3d front=f[2].multiply(x.width*.5+.14);
        for(int j=0;j<DASHES;j++){
            double[] b=dash(j,x.s,x.seed,x.height,x.width);if(b==null)continue;
            Vec3d p=x.feet.add(0,b[0]*x.height,0).add(f[0].multiply(b[1])).add(front);
            if(light)bar(m,p,f,b[2]*1.2,b[3]*2.6,GLOW,.22f*near);
            // (its purple laid over, not added: purple before a bright sky)
            else bar(m,p,f,b[2],b[3],(int)b[4],near);
        }
    }
    /** A little purple light on the world round one just pulled. */
    public static void frame(FxFrame f,Vec3d camera){
        for(var x:shown){float l=flash(x.s);if(l>0)f.light(x.feet.add(0,x.height*.5,0),GLOW,3.2,.6f*l);}
    }

    // ───────────────────────────── the silhouette ─────────────────────────────

    private static boolean flat;
    /** Whether a body is being drawn again as the pull's flat silhouette now (no name over it then: EntityRenderer.render). */
    public static boolean silhouetting(){return flat;}
    /** Every layer asked of it is the one flat colour: a body drawn through it is its silhouette. */
    private static final class Flat implements VertexConsumerProvider,VertexConsumer {
        final VertexConsumer to;final int r,g,b;double x,y,z;float ny;
        Flat(VertexConsumer to,int rgb){this.to=to;r=rgb>>16&255;g=rgb>>8&255;b=rgb&255;}
        @Override public VertexConsumer getBuffer(RenderLayer layer){return this;}
        @Override public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;ny=0;return this;}
        @Override public VertexConsumer color(int red,int green,int blue,int alpha){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer normal(float nx,float ny,float nz){this.ny=ny;return this;}
        @Override public void next(){
            // (a little lighter where it faces up, so its form still reads: the video's is shaded so)
            float shade=.88f+.12f*MathHelper.clamp(ny,-1,1);
            to.vertex(x,y,z).color(Math.min(255,(int)(r*shade)),Math.min(255,(int)(g*shade)),Math.min(255,(int)(b*shade)),255).next();
        }
        @Override public void fixedColor(int red,int green,int blue,int alpha){}
        @Override public void unfixColor(){}
    }
    /**
     * Draws the ones pulled as their flat purple silhouette, the frames they are it: each drawn again through their own
     * renderer where they are drawn, a little bigger, every layer of theirs the one colour (no texture, no light: the
     * shape of whatever they are). Not where a shader pack draws the world (as the effects' layer: the bars alone
     * there), and not this client's own body in the first person.
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();
        if(MARKS.isEmpty()||mc.world==null||!(ctx.consumers() instanceof VertexConsumerProvider.Immediate immediate)||!FxLayer.available())return;
        float delta=ctx.tickDelta();Vec3d cam=ctx.camera().getPos();var dispatcher=mc.getEntityRenderDispatcher();
        boolean drew=false;
        for(var d:MARKS.values()){
            float s=d.since(delta);if(s<0||!solid(s))continue;
            if(!(mc.world.getEntityById(d.target) instanceof LivingEntity e)||!e.getUuid().equals(d.targetId)||!e.isAlive()||e.isInvisible())continue;
            if(e==mc.getCameraEntity()&&!ctx.camera().isThirdPerson())continue;
            Vec3d at=feet(e,delta);
            if(at.squaredDistanceTo(cam)>96*96)continue;
            var renderer=(net.minecraft.client.render.entity.EntityRenderer)dispatcher.getRenderer(e);
            var m=ctx.matrixStack();double half=e.getHeight()*.5;
            m.push();
            m.translate(at.x-cam.x,at.y-cam.y+half,at.z-cam.z);m.scale(SWELL,SWELL,SWELL);m.translate(0,-half,0);
            flat=true;
            try{renderer.render(e,MathHelper.lerp(delta,e.prevYaw,e.getYaw()),delta,m,new Flat(immediate.getBuffer(RenderLayer.getDebugQuads()),FLAT),0xF000F0);}
            finally{flat=false;m.pop();}
            drew=true;
        }
        if(drew)immediate.draw(RenderLayer.getDebugQuads());
    }

    /** For the capture tools and the checks: its casts as "age,how,seconds since the pull,there". */
    public static String describe(float delta){
        if(MARKS.isEmpty())return "";
        var b=new StringBuilder();
        for(var d:MARKS.values())b.append(b.length()>0?" ":"").append(String.format(java.util.Locale.ROOT,"cast@%.1f,%d,%.2f,%d",d.age(delta),d.how,d.since(delta),d.there?1:0));
        return b.toString();
    }
    /** For the checks: whether this player's sounds have begun on this client, as "warning,burst" (1 or 0 each; - if it was never asked for), or null if no cast of theirs is known. */
    public static String sounds(UUID user){var d=MARKS.get(user);return d==null?null:begun(d.warn)+","+begun(d.burst);}
    private static String begun(XCharaClient.Voice v){return v==null?"-":v.begun()?"1":"0";}
}
