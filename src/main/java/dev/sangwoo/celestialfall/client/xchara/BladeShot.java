package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.EffectMesh;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.Anim;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.vfx.FxFrame;
import dev.sangwoo.celestialfall.xchara.ShotRules;
import dev.sangwoo.celestialfall.xchara.XChara;
import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import dev.sangwoo.celestialfall.xchara.XCharaRules;
import dev.sangwoo.celestialfall.xchara.XCharaShot;
import dev.sangwoo.celestialfall.xchara.XCharaUltimate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static dev.sangwoo.celestialfall.client.kageno.Anim.k;

/**
 * X Chara's Y with their hack off, the four knives shot, drawn (2.130.0, user request with their video of it, Roblox's
 * "Y - Blade Shot": "y스킬은 영상 보면 q활성화 상태의 검을 4개를 적에게 날리는 스킬이야 영상에서는 살짝 끊기면서 나가는데
 * 렉일뿐이고 그냥 나가면 돼"; the server's XCharaAttack.shot and XCharaShot, ShotRules). A skill of the knife's pipeline
 * (KnifeClient's swings; KnifeCombo's passes hand it on here).
 * <ul>
 * <li>Its user stands as they stood (the video's: no motion of the body; the knife in their hand stays black).</li>
 * <li>The knives are the knife itself, its own model lit as the hack lights it ("q활성화 상태의 검": the white and the
 * purple rim, HackKnife), the size it is in the hand, drawn in the world after the entities ({@link #models}). Each
 * comes to be where it hangs under blocks of purple breaking over it (the hack's own coming on, the video's glitch),
 * hangs there pointing ahead, and as it is let go turns to its way and flies, its point ShotRules.head along it.</li>
 * <li>What the effects' layer adds ({@link #draw}): a soft purple glow along each knife and a few blocks of purple
 * blinking at its grip's end (the video's), a streak of light behind one in flight, the knife's own burst on whom one
 * strikes (SlashFx.burst: the white star and the purple spikes, the video's), a white star and a ring where a guard
 * took one, a burst of pixels where one met a block or went out.</li>
 * <li>Its sounds are the user's video's: as each knife comes to be, the "!!!"'s own sound (the video plays that very
 * one; KnifeClient's voice), and a knife's strike, the video's own (tools/xchara/extract_shot.py), played as each is
 * seen to land.</li>
 * </ul>
 * Their own client tells the server where the middle of its screen points every tick until the last knife is gone
 * ({@link #tellAim}): each knife goes at where they look as it is let go (the user's "보는 방향으로 날아감"). No screen
 * effect but a small shake as a knife lands. Everything is a function of the skill's own clock and what the server told.
 */
public final class BladeShot {
    private BladeShot(){}
    /** One knife as this client was told of it: where it flies from and which way, how far it can; and whom it struck, how far along, whether they took it, where they stood. */
    static final class Blade {
        Vec3d from,dir;double reach=Double.NaN;
        int struck=-1;double far=Double.NaN;boolean took;Vec3d mid;
    }
    private static final Map<UUID,Blade[]> TOLD=new HashMap<>();
    /** How long one is drawn for (seconds): the last knife's flight at its longest, and what it leaves. */
    public static final float LIFE=(float)(ShotRules.over(ShotRules.BLADES-1)/20+.55);
    /**
     * Its sounds, by the user's video: each knife's coming to be is the "!!!"'s own sound (G's: the video plays that
     * very sound as each comes, the game's file laid on its recording agreeing 0.80; SOUND_PART of it, four of them
     * one over another), and a knife's strike is the video's own (tools/xchara/extract_shot.py; its onset
     * HIT_SOUND_LEAD ticks into its file: started that much before the knife is seen to land when there is time).
     */
    static final String SOUND="xchara_g_warn",HIT_SOUND="xchara_shot_hit";static final float SOUND_PART=.7f;
    /** A knife not let go when the skill stopped short is gone over this long (ticks); one never told of after it was to go, after this many. */
    static final float VANISH=2.4f,UNTOLD=4;
    /** As one is let go it turns from the way it hung to its way over this long (ticks). */
    static final float TURN=1.2f;
    /** A knife that has struck stays in them this long before it is gone (ticks); one at the end of its range thins out over its last blocks. */
    static final float STUCK=1.2f;static final double THIN=4;
    /** How far each knife is rolled about its length where it hangs (degrees: its flat a little to a camera behind its user, which sees it nearly point on). */
    static final float[] ROLL={-32,28,38,-24};
    /** The body: as it stood. */
    static final Anim CLIP=Anim.joints(k(0,KnifeCombo.STANCE),k(ShotRules.LENGTH/20f,KnifeCombo.STANCE)).after((sec,f)->KnifeCombo.AT_REST);

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaShot.EVENT,(mc,handler,b,response)->{
            UUID caster=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();UUID id=b.readUuid();int k=b.readUnsignedByte();
            if(k>=ShotRules.BLADES)return;
            if(type==XCharaShot.FIRED){
                Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),dir=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());double reach=b.readDouble();
                if(!finite(from)||!finite(dir)||!Double.isFinite(reach)||Math.abs(dir.length()-1)>.01)return;
                mc.execute(()->{
                    if(mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                    var blade=blade(id,k);blade.from=from;blade.dir=dir;blade.reach=Math.max(0,Math.min(ShotRules.RANGE,reach));
                });
            }else if(type==XCharaShot.STRUCK){
                int entity=b.readInt();double far=b.readDouble();boolean took=b.readBoolean();
                if(!Double.isFinite(far))return;
                mc.execute(()->{
                    if(mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                    var blade=blade(id,k);if(blade.from==null)return;
                    blade.struck=entity;blade.far=Math.max(0,Math.min(ShotRules.RANGE,far));blade.took=took;
                    Vec3d point=blade.from.add(blade.dir.multiply(blade.far));
                    blade.mid=mc.world.getEntityById(entity) instanceof LivingEntity e?e.getLerpedPos(mc.getTickDelta()).add(0,e.getHeight()*.55,0):point;
                    if(took)hitSound(mc,caster,id,k,blade,point);
                });
            }
        });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(BladeShot::models);
    }
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static Blade blade(UUID id,int k){
        var all=TOLD.computeIfAbsent(id,x->new Blade[ShotRules.BLADES]);
        if(all[k]==null)all[k]=new Blade();
        return all[k];
    }
    static Blade told(UUID id,int k){var all=TOLD.get(id);return all==null?null:all[k];}
    /** What was told of the skills this client draws no more is forgotten (KnifeClient's tick). */
    static void keep(java.util.Set<UUID> ids){TOLD.keySet().retainAll(ids);}
    static void clear(){TOLD.clear();}

    // ───────────────────────────── where a knife is ─────────────────────────────

    private static double[] arr(Vec3d v){return new double[]{v.x,v.y,v.z};}
    private static Vec3d vec(double[] v){return new Vec3d(v[0],v[1],v[2]);}
    /**
     * One knife this frame: its grip (the middle of its rod: its model's origin), the way it points, how much of it
     * shows (it comes and goes by its size), the seconds since it came to be (its hack's clock), whether it flies and
     * how far along its way its point is (blocks; NaN while it hangs).
     */
    public record Pose(Vec3d grip,Vec3d dir,float shown,float lit,boolean flying,double head){
        public Vec3d tip(){return grip.add(dir.multiply(ShotRules.TIP*size(shown)));}
    }
    /** A knife's size of its full one as it comes and goes. */
    static double size(float shown){return .5+.5*shown;}
    /** Where knife {@code k} hangs for a user who stood at {@code stood} facing {@code yaw}, and the way it points there (at a point well ahead of them, chest high: the four lean in on it). */
    static Vec3d spot(Vec3d stood,float yaw,int k){return vec(ShotRules.spot(arr(stood),yaw,k));}
    static Vec3d hangs(Vec3d stood,float yaw,int k){
        Vec3d f=Vec3d.fromPolar(0,yaw),want=stood.add(f.multiply(9)).add(0,1.25,0);
        return vec(ShotRules.way(ShotRules.spot(arr(stood),yaw,k),arr(stood.add(0,1.62,0)),arr(want)));
    }
    /**
     * Knife {@code k} {@code age} ticks into a skill begun at {@code stood} facing {@code yaw}, that stopped short
     * {@code stopped} ticks in (anything past its end if it did not), by what was {@code told} of it (null: nothing
     * yet). Null while there is nothing of it to draw.
     */
    public static Pose pose(Vec3d stood,float yaw,int k,float age,float stopped,Blade told){
        if(age<ShotRules.APPEAR[k])return null;
        float lit=(age-ShotRules.APPEAR[k])/20f,in=Ease.smooth(age,ShotRules.APPEAR[k],ShotRules.APPEAR[k]+2.4f);
        Vec3d hang=spot(stood,yaw,k).add(0,.045*Math.sin(age/20.0*5+k*1.7),0),ahead=hangs(stood,yaw,k);
        int fire=ShotRules.FIRE[k];
        // never let go: the skill stopped short before it was to be
        if(stopped<fire){
            if(age>=stopped+VANISH)return null;
            return new Pose(hang,ahead,in*(1-Ease.smooth(age,stopped,stopped+VANISH)),lit,false,Double.NaN);
        }
        boolean heard=told!=null&&told.from!=null;
        if(age<fire||!heard){
            // (not told of in time: the word is lost, and it is gone)
            if(age>=fire+UNTOLD)return null;
            return new Pose(hang,ahead,in,lit,false,Double.NaN);
        }
        double a=age-fire,head=ShotRules.head(a);
        boolean hit=told.struck>=0&&!Double.isNaN(told.far);double end=hit?Math.max(ShotRules.TIP,told.far):told.reach;
        float shown=in;
        if(head>=end){
            double past=a-ShotRules.reached(end);
            // in whom it struck a moment; at a block, or as far as it goes, gone
            if(!hit||past>=STUCK)return null;
            head=end;shown*=1-Ease.smooth((float)past,STUCK*.5f,STUCK);
        }else if(!hit&&told.reach>=ShotRules.RANGE-1e-6)shown*=(float)Math.max(0,Math.min(1,(end-head)/THIN));
        if(shown<=0)return null;
        float w=Ease.smooth((float)a,0,TURN);
        Vec3d dir=ahead.multiply(1-w).add(told.dir.multiply(w));dir=dir.lengthSquared()<1e-8?told.dir:dir.normalize();
        Vec3d fly=told.from.add(told.dir.multiply(head-ShotRules.TIP));
        Vec3d grip=hang.multiply(1-w).add(fly.multiply(w)).add(told.dir.multiply((head-ShotRules.TIP)*(1-w)));
        return new Pose(grip,dir,shown,lit,true,head);
    }
    private static float stopped(StandClient.Cast c){float u=KnifeClient.until(c.id());return u==Float.MAX_VALUE?Float.MAX_VALUE:u*20;}
    static Pose pose(StandClient.Cast c,int k,float age){return pose(c.origin(),c.yaw(),k,age,stopped(c),told(c.id(),k));}
    /** The ticks into its skill knife {@code k} is seen to land on whom it struck (NaN: it struck no one, as far as is told). */
    static float landed(Blade b,int k){return b==null||b.struck<0||Double.isNaN(b.far)?Float.NaN:(float)(ShotRules.FIRE[k]+ShotRules.reached(b.far));}
    /** The ticks into its skill knife {@code k}'s flight ends at a block or as far as it goes (NaN: not told, or it struck someone). */
    static float ended(Blade b,int k){return b==null||b.from==null||b.struck>=0?Float.NaN:(float)(ShotRules.FIRE[k]+ShotRules.reached(b.reach));}

    // ───────────────────────────── the knives themselves ─────────────────────────────

    private static XCharaKnifeRenderer renderer;private static ItemStack stack;
    private static final Vec3d UP=new Vec3d(0,1,0);
    /** The knife's spine's way for one pointing along {@code dir}, rolled {@code roll} degrees about it: up, as near as may be. */
    static Vec3d spine(Vec3d dir,float roll){
        Vec3d u=UP.subtract(dir.multiply(dir.y));if(u.lengthSquared()<1e-6)u=new Vec3d(1,0,0);u=u.normalize();
        double r=Math.toRadians(roll);
        return u.multiply(Math.cos(r)).add(dir.crossProduct(u).multiply(Math.sin(r))).normalize();
    }
    /** The knives as the knife's own model, lit as the hack lights it (AFTER_ENTITIES). */
    static void models(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();if(mc.world==null||KnifeClient.SWINGS.isEmpty()||ctx.consumers()==null||!MapClient.enabled())return;
        Vec3d camera=ctx.camera().getPos();var matrices=ctx.matrixStack();float delta=ctx.tickDelta();
        for(var c:KnifeClient.SWINGS.values()){
            if(c.combo()!=XCharaCombo.SHOT||c.origin().squaredDistanceTo(camera)>128*128)continue;
            float age=c.age(delta);
            for(int k=0;k<ShotRules.BLADES;k++){
                var p=pose(c,k,age);if(p==null||p.shown()<=.02f)continue;
                if(renderer==null){renderer=new XCharaKnifeRenderer();stack=new ItemStack(XChara.KNIFE);}
                Vec3d y=p.dir(),s=spine(y,ROLL[k]),x=s.negate(),z=x.crossProduct(y).normalize();
                float scale=(float)(ShotRules.SCALE*size(p.shown()));
                matrices.push();
                matrices.translate(p.grip().x-camera.x,p.grip().y-camera.y,p.grip().z-camera.z);
                matrices.multiply(new Quaternionf().setFromNormalized(new Matrix3f(new Vector3f((float)x.x,(float)x.y,(float)x.z),new Vector3f((float)y.x,(float)y.y,(float)y.z),new Vector3f((float)z.x,(float)z.y,(float)z.z))));
                matrices.scale(scale,scale,scale);
                matrices.translate(-.5,-.51,-.5);
                XCharaKnifeRenderer.hack=new HackLook.Frame(true,p.lit(),age/20f+k*1.3f,seed(c,k));
                try{renderer.render(stack,ModelTransformationMode.NONE,matrices,ctx.consumers(),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);}
                finally{XCharaKnifeRenderer.hack=null;}
                matrices.pop();
            }
        }
    }
    static int seed(StandClient.Cast c,int k){return (int)(c.start()*31+k*13+7);}

    // ───────────────────────────── what the effects' layer adds ─────────────────────────────

    /** Everything of the knives but their models, in one pass (the blended bodies, or the light). */
    static void draw(Fx c,EffectMesh m,boolean light){
        float age=c.age,stop=stopped(c.cast);
        for(int k=0;k<ShotRules.BLADES;k++){
            int seed=seed(c.cast,k);var told=told(c.cast.id(),k);
            var p=pose(c.cast,k,age);
            float since=(age-ShotRules.APPEAR[k])/20f;
            if(p!=null){
                float near=GlitchFx.near(p.grip(),c.camera)*p.shown();Vec3d tip=p.tip();
                if(near>0){
                    if(light){
                        // a soft purple along it, and a spark at its point as it comes to be
                        m.softLine(p.grip().subtract(p.dir().multiply(.3)),tip,.55,GlitchFx.GLOW,.22f*near);
                        if(since<.16f)m.star(p.grip().add(p.dir().multiply(ShotRules.TIP*.55)),.5*(1-since/.16f)+.12,GlitchFx.LILAC,.8f*(1-since/.16f)*near);
                    }
                    // blocks of purple blinking off its grip's end while it hangs (the video's)
                    if(!p.flying()){
                        int slot=(int)Math.floor(age/20f*14);var f=GlitchFx.facing(c.camera,p.grip());
                        for(int j=0;j<5;j++){
                            if(GlitchFx.h(j,slot,seed,31)>.5)continue;
                            Vec3d at=p.grip().subtract(p.dir().multiply(.25+.9*GlitchFx.h(j,slot,seed,32))).add(f[0].multiply((GlitchFx.h(j,slot,seed,33)-.5)*.3)).add(f[1].multiply((GlitchFx.h(j,slot,seed,34)-.5)*.3));
                            double half=.035+.05*GlitchFx.h(j,slot,seed,35);int rgb=GlitchFx.h(j,slot,seed,36)<.6?GlitchFx.PURPLE:GlitchFx.DEEP;
                            if(light)GlitchFx.square(m,at,f,half*1.8,GlitchFx.GLOW,.3f*near);else GlitchFx.square(m,at,f,half,rgb,.95f*near);
                        }
                    }else if(light){
                        // the streak behind one in flight: as long as it has flown this last twentieth of a second and a little
                        double back=Math.min(p.head()-ShotRules.TIP,ShotRules.SPEED*1.3);
                        if(back>.05){
                            Vec3d tail=tip.subtract(p.dir().multiply(back+ShotRules.TIP*.6));
                            m.softTaper(tail,tip,.05,.7,GlitchFx.GLOW,.4f*near);m.softTaper(tail,tip,.02,.16,GlitchFx.WHITE,.7f*near);
                        }
                    }
                }
            }
            // as it comes to be: a few pixels off it
            if(since>=0&&since<GlitchFx.PIXEL_LIFE){
                Vec3d mid=spot(c.origin,c.cast.yaw(),k).add(hangs(c.origin,c.cast.yaw(),k).multiply(ShotRules.TIP*.5));
                GlitchFx.pixels(m,mid,UP,1,2.2,.045,7,seed,since,light,c.camera);
            }
            // never let go: it breaks up where it hung
            if(stop<ShotRules.FIRE[k]&&age>=stop&&ShotRules.there(k,stop)){
                Vec3d mid=spot(c.origin,c.cast.yaw(),k).add(hangs(c.origin,c.cast.yaw(),k).multiply(ShotRules.TIP*.5));
                GlitchFx.pixels(m,mid,UP,1,3,.05,10,seed+5,(age-stop)/20f,light,c.camera);
            }
            if(told==null||told.from==null||stop<ShotRules.FIRE[k])continue;
            float landed=landed(told,k),ended=ended(told,k);
            if(!Float.isNaN(landed)){
                float s=(age-landed)/20f;if(s<0)continue;
                Vec3d point=told.from.add(told.dir.multiply(told.far));
                if(told.took)SlashFx.burst(m,told.mid==null?point:told.mid,told.dir,XCharaCombo.HACK_SLASH,seed,s,light,c.camera);
                else if(s<.3f){
                    // a guard took it: a white star and a ring about its way, the knife broken to pixels
                    float fade=1-s/.3f,near=GlitchFx.near(point,c.camera);
                    if(light&&near>0){
                        if(s<.14f)m.star(point,.7*(1-s/.14f)+.2,GlitchFx.WHITE,.9f*(1-s/.14f)*near);
                        Vec3d u=spine(told.dir,0),v=told.dir.crossProduct(u);
                        m.ring(point,u,v,.25+1.1*Ease.smooth(s,0,.22f),28,1,.05,GlitchFx.LILAC,.7f*fade*near,0);
                    }
                    GlitchFx.pixels(m,point,told.dir.negate(),.75,4,.05,9,seed+9,s,light,c.camera);
                }
            }else if(!Float.isNaN(ended)){
                float s=(age-ended)/20f;
                // against a block: broken to pixels there (as far as it goes it thins out to nothing instead)
                if(s>=0&&told.reach<ShotRules.RANGE-1e-6){
                    Vec3d point=told.from.add(told.dir.multiply(told.reach));float near=GlitchFx.near(point,c.camera);
                    if(light&&s<.12f&&near>0)m.star(point,.5*(1-s/.12f)+.15,GlitchFx.LILAC,.8f*(1-s/.12f)*near);
                    GlitchFx.pixels(m,point,told.dir.negate(),.8,4.5,.055,12,seed+3,s,light,c.camera);
                }
            }
        }
    }
    /** Its light on the world: a little purple by each knife, more on whom one strikes; little afterglow while they fly (their light would leave copies of them). */
    static void frame(Fx c,FxFrame f){
        boolean flying=false;
        for(int k=0;k<ShotRules.BLADES;k++){
            var p=pose(c.cast,k,c.age);
            if(p!=null){f.light(p.tip(),0x9a3cff,p.flying()?6:4,(p.flying()?.6f:.35f)*p.shown());flying|=p.flying();}
            var told=told(c.cast.id(),k);float landed=landed(told,k);
            if(!Float.isNaN(landed)&&told.took){
                float s=(c.age-landed)/20f;
                if(s>=0&&s<.3f)f.light(told.mid==null?told.from.add(told.dir.multiply(told.far)):told.mid,0x9a3cff,6,.7f*(1-s/.3f));
            }
        }
        if(flying)f.afterglow(.08f);
    }
    /** How hard a knife shakes the camera as it lands on someone. */
    static final float SHAKE=.3f;
    static float shake(StandClient.Cast cast,float sec){
        float sum=0;
        for(int k=0;k<ShotRules.BLADES;k++){
            var told=told(cast.id(),k);float landed=landed(told,k);
            if(!Float.isNaN(landed)&&told.took)sum+=SHAKE*KnifeCombo.bump(sec,landed/20f,.06f);
        }
        return sum;
    }

    // ───────────────────────────── its sound, and where its user looks ─────────────────────────────

    /** The strike's sound's onset is this far into its file (ticks): started that much before the knife is seen to land when there is time. */
    static final int HIT_SOUND_LEAD=1;
    private static void hitSound(MinecraftClient mc,UUID caster,UUID id,int k,Blade blade,Vec3d point){
        if(mc.player==null)return;
        var cast=KnifeClient.SWINGS.get(id);if(cast==null)return;
        java.util.function.Supplier<Float> loud=caster.equals(mc.player.getUuid())?()->(float)CelestialFall.CONFIG.effectVolume:()->{
            var m=MinecraftClient.getInstance();var user=m.world==null?null:m.world.getPlayerByUuid(caster);
            return Math.max(KnifeClient.loudness(m,point),KnifeClient.loudness(m,user==null?point:user.getPos()));
        };
        var v=new XCharaClient.Voice(CelestialFall.id(HIT_SOUND),loud);
        if(v.full()<=.01f)return;
        int wait=(int)Math.floor(landed(blade,k)-HIT_SOUND_LEAD-cast.age(0));
        if(wait>0)mc.getSoundManager().play(v,wait);else mc.getSoundManager().play(v);
        KnifeClient.heard(String.format(java.util.Locale.ROOT,"%s knife %d, told %.0f ticks in, played %d later at %.2f",HIT_SOUND,k,cast.age(0),Math.max(0,wait),v.full()));
    }
    /** For the capture tools: a point the local player's knives are sent at whatever their screen shows (a filming camera is not theirs); null, the screen's middle. */
    public static Vec3d aimAt;
    /** The point the middle of this client's screen is on now: the first block or body along it within the knives' range, or as far out as that (from where the camera's line passes its player: nothing between the camera and them). */
    static Vec3d aimPoint(MinecraftClient mc){
        if(aimAt!=null)return aimAt;
        var cam=mc.gameRenderer.getCamera();Vec3d c=cam.getPos(),look=Vec3d.fromPolar(cam.getPitch(),cam.getYaw()),eye=mc.player.getEyePos();
        Vec3d a=c.add(look.multiply(Math.max(0,eye.subtract(c).dotProduct(look)))),end=a.add(look.multiply(ShotRules.RANGE));
        var block=mc.world.raycast(new RaycastContext(a,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        if(block.getType()!=HitResult.Type.MISS)end=block.getPos();
        Vec3d best=end;double near=a.squaredDistanceTo(end);
        for(var e:mc.world.getEntitiesByClass(LivingEntity.class,new Box(a,end).expand(1.5),e->e!=mc.player&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            var hit=e.getBoundingBox().expand(ShotRules.GIRTH).raycast(a,end);
            if(hit.isPresent()&&a.squaredDistanceTo(hit.get())<near){near=a.squaredDistanceTo(hit.get());best=hit.get();}
        }
        return best;
    }
    /** Every tick: while the local player's own knives are not all gone, where the middle of the screen points is told to the server. */
    static void tellAim(MinecraftClient mc){
        if(mc.player==null||mc.world==null||!ClientPlayNetworking.canSend(XCharaUltimate.INPUT))return;
        StandClient.Cast mine=null;
        for(var c:KnifeClient.SWINGS.values())if(c.combo()==XCharaCombo.SHOT&&c.caster().equals(mc.player.getUuid()))mine=c;
        if(mine==null||KnifeClient.until(mine.id())!=Float.MAX_VALUE||mine.age(0)>ShotRules.FIRE[ShotRules.BLADES-1]+1)return;
        Vec3d at=aimPoint(mc);
        var b=PacketByteBufs.create();b.writeByte(XCharaRules.AIM);b.writeDouble(at.x);b.writeDouble(at.y);b.writeDouble(at.z);
        ClientPlayNetworking.send(XCharaUltimate.INPUT,b);
    }
    /** For the capture tools: knife {@code k} of this player's latest knives shot as "shown,flying,head,gripx,gripy,gripz,struck,far,took", or null. */
    public static String describe(net.minecraft.entity.player.PlayerEntity player,int k,float delta){
        StandClient.Cast best=null;
        for(var c:KnifeClient.SWINGS.values())if(c.combo()==XCharaCombo.SHOT&&c.caster().equals(player.getUuid()))best=c;
        if(best==null)return null;
        var p=pose(best,k,best.age(delta));var t=told(best.id(),k);
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%.2f,%.2f,%.2f,%.2f,%d,%.2f,%d",p==null?0:p.shown(),p!=null&&p.flying()?1:0,p==null||Double.isNaN(p.head())?-1:p.head(),
            p==null?0:p.grip().x,p==null?0:p.grip().y,p==null?0:p.grip().z,t==null?-1:t.struck,t==null||Double.isNaN(t.far)?-1:t.far,t!=null&&t.took?1:0);
    }
}
