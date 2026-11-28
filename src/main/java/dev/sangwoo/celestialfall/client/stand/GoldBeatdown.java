package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.client.stand.StandBeatdown.Body;
import dev.sangwoo.celestialfall.stand.GoldExperienceBeatdown;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.StandManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;

/**
 * Gold Experience's beatdown as this client draws it: the seven-page muda (2.150.0, user request: "이번에는 G스킬
 * 비트다운이고 7페이지 무다러쉬야 효과음은 영상을 보면 되고 비트다운도 영상을 참고해서 만들고", with the scene's video;
 * "눈 클로즈업 빼고 영상 그대로"). The server says when its opening punch begins, whether anything came of it, and when
 * a cutscene starts (its user, the one struck, where everyone is, how high there is room to drive them, where they
 * come down) and ends (GoldExperienceBeatdown); everything between is drawn from the beatdown's own clock (seconds from
 * the punch's start), on the video's sound's time (GoldExperienceRules.BEAT_*, {@link #rec}).
 * <p>
 * The punch: the stand comes round to the front of its user, winds its right fist far back and drives it in (the
 * overdrive punch's stances, slower: it is to be seen coming). Nothing comes of it, it goes back as that punch does.
 * Landing, the cutscene: the fist holds a moment and the rush begins at once, "muda muda ..." (the muda rush's fists,
 * its arms bigger, afterimages of them all round it), the one struck knocked back to where they are held and up into
 * the air, wrenched by every fist; at the "WRYYY" the stand goes under them, leaning back, and drives them up and up
 * with its fists, the one struck tipped over face down above it; high up the rush goes on; then it draws its fist far
 * back and on the last "muda!" one blow sends them flying far off, turning over, to come down on their back, slide and
 * get up. {@link #victim} is where the one struck is shown and how their body turns (the renderers put the body there
 * in place of where it is, StandBeatdown's hooks), {@link #pose} the stand's stances and place, {@link #camera} the
 * camera of its user and of the one struck (GoldBeatdownCamera's shots, the video's) with the black bars, and its
 * sound the video's from its second shot on. GoldBeatdownFx draws its light, GoldBeatdownPose its user's body and the
 * one struck's limbs.
 */
public final class GoldBeatdown {
    // ───────────────────────────── its moments (seconds from the punch's start) ─────────────────────────────

    static final float CUT=BEAT_CUT/20f;
    /** The moment of the video's sound {@code tau} seconds into its cut (the cut begins with the video's second shot), on the beatdown's clock. */
    static float rec(float tau){return CUT+tau;}
    /** The opening punch: wound up, loaded until, landing at CUT; nothing coming of it, held out until, back in its guard. */
    static final float WOUND=.45f,LOAD=.62f,HELD=1.1f,GUARDED=1.42f;
    /** Stopped short it is back in its guard; it stays a moment in front once in its guard and then goes back; its stances take over. */
    static final float STOPPED=.15f,LINGER=.12f,RETURN=.4f,TAKE=.1f;
    /** The blow holds this long as it lands (the hit-stop); the one struck is knocked back to where they are held by KNOCKED. */
    static final float STOP=.1f,KNOCKED=CUT+.55f;
    /** The rush: its barrage begins (its first fist lands HALF on), every fist until it draws back. */
    static final float BARRAGE_FROM=CUT+.06f,WIND=BEAT_WIND/20f;
    /** "WRYYY": the stand goes under them and drives them up, risen by RISEN. */
    static final float WRY=BEAT_WRY/20f,WRY_END=BEAT_WRY_END/20f,RISEN=WRY_END-.3f;
    static final float FINAL=BEAT_FINAL/20f,LAND=BEAT_LAND/20f,END=BEAT_END/20f;
    /** The last blow holds the one struck where it lands this long before they fly. */
    static final float FINAL_STOP=.12f;
    /** The one struck lies where they came down until GETUP, and is up again at STOOD. */
    static final float GETUP=LAND+.3f,STOOD=LAND+.72f;
    /** The stand goes back to its user's shoulder at the end: from LEAVE. */
    static final float LEAVE=LAND+.2f;
    /** The camera comes back to its user over this long once it is over (or cut short). */
    static final float HANDBACK=.3f;
    static final float BARS_IN=.38f,BARS_OUT=.35f;

    // ───────────────────────────── the bodies ─────────────────────────────

    /** How high the one struck's middle is, how far their body is shown lowered as it lies, how far they slide on once down, how long they take to be turned to face its user (StandBeatdown's). */
    static final float MIDDLE=StandBeatdown.MIDDLE,LIE=StandBeatdown.LIE,FACE=StandBeatdown.FACE;
    static final double SLIDE=StandBeatdown.SLIDE,EYES=1.62;
    /** How far forward the one struck is tipped over above the stand once it is under them (degrees: face down at it). */
    static final float PRONE=58;
    /** How far back the stand leans under them (degrees), driving its fists up. */
    static final float LEAN=44;
    /** How big its arms are through the rush (the muda rush's 1.15, Star Platinum's beatdown's 1.3). */
    static final float ARMS=1.2f;
    /** How far in front of its user it stands for its punch with no one in its way, and no nearer or further than; how deep its fists go into whom they meet. */
    static final float AHEAD=1.05f,NEAR=.75f,FAR=2.1f,BITE=.15f;

    /** A beatdown as this client heard of it: its opening punch, and the cutscene if one came of it. */
    public static final class Beat {
        public final UUID user;public final long start;final long serverAge,received;public final float yaw,aim;final int seed;
        /** (2.156.0) The skin its user's Gold Experience is drawn in (GoldSkin: its fists and size go by it); set as it begins and every frame it is drawn. */
        public GoldSkin skin=GoldSkin.ORIGINAL;
        /** Its way and its right. */
        public final Vec3d f,r;
        /** The punch stopped short (its clock then; NaN: not), landed (NaN: not yet), whom it met (-1 no one) and whether it broke their guard. */
        float stopped=Float.NaN,landedAt=Float.NaN;int met=-1;boolean broke;
        /** The cutscene, once it started: the one struck, where everyone is, how far and how high they are driven, where they come down. */
        boolean scene;public int victim=-1;public UUID victimId;public Vec3d from,victimFrom,hold,land;public double drift,lift,high;public float damage;
        /** The clock when the cutscene was heard of (its stances take over from the punch's from then). */
        float sceneAt;
        /** When it ended (seconds; NaN while it runs) and whether it was cut short. */
        float ended=Float.NaN;boolean cut;
        boolean cried;StandAttack.Shout voice;
        /** The world tick G is ready again (0: not known, the cutscene still on). */
        long ready;
        Beat(UUID user,long start,long serverAge,float yaw,float aim){
            this.user=user;this.start=start;this.serverAge=serverAge;received=ticks;this.yaw=yaw;this.aim=aim;skin=GoldSkin.of(user);
            f=Vec3d.fromPolar(0,yaw);r=new Vec3d(-f.z,0,f.x);
            seed=(int)(start*31+user.getLeastSignificantBits()&0x7fffffff);
        }
        /** Seconds on its clock this frame. */
        public float age(float delta){return (serverAge+ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        /** When it is over on its clock: the cutscene's end (or when it was cut short), or its punch back in its guard. */
        public float over(){
            if(scene)return Float.isNaN(ended)?END:Math.min(END,ended);
            return Float.isNaN(stopped)?GUARDED:Math.min(GUARDED,stopped+STOPPED);
        }
        Vec3d at(Vec3d o,double fw,double up,double rt){return o.add(f.multiply(fw)).add(r.multiply(rt)).add(0,up,0);}
        public boolean scene(){return scene;}
        public boolean ended(){return !Float.isNaN(ended);}
        public boolean wasCut(){return cut;}
        public boolean landed(){return !Float.isNaN(landedAt);}
        public boolean broke(){return broke;}
        public int met(){return met;}
        public net.minecraft.client.sound.SoundInstance voice(){return voice;}
    }
    private static final Map<UUID,Beat> BEATS=new HashMap<>();
    static long ticks;
    private static Object world;
    private static boolean heldG;
    private GoldBeatdown(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(GoldExperienceBeatdown.EVENT,(mc,handler,b,response)->{
            int type=b.readUnsignedByte();UUID user=b.readUuid();Identifier dim=b.readIdentifier();
            long start=b.readLong(),now=b.readLong();float yaw=b.readFloat();long ready=b.readLong();
            if(type<=GoldExperienceBeatdown.STOPPED){
                float aim=b.readFloat();int met=b.readInt();boolean broke=b.readBoolean();
                if(!Float.isFinite(yaw))return;
                mc.execute(()->{if(heard(mc,dim))punch(user,type,start,now,yaw,ready,aim,met,broke);});
                return;
            }
            int victim=b.readInt();UUID victimId=b.readUuid();
            Vec3d from=vec(b),victimFrom=vec(b),hold=vec(b);double drift=b.readDouble(),lift=b.readDouble(),high=b.readDouble();Vec3d land=vec(b);
            float damage=b.readFloat();boolean cut=b.readBoolean();
            if(!Float.isFinite(yaw)||!Double.isFinite(drift)||!Double.isFinite(lift)||!Double.isFinite(high)||now<start||now-start>BEAT_END+200)return;
            mc.execute(()->{
                if(!heard(mc,dim))return;
                if(type==GoldExperienceBeatdown.START){
                    var beat=BEATS.get(user);
                    if(beat==null||beat.start!=start){beat=new Beat(user,start,now-start,yaw,Float.NaN);BEATS.put(user,beat);}
                    beat.victim=victim;beat.victimId=victimId;beat.from=from;beat.victimFrom=victimFrom;beat.hold=hold;beat.drift=drift;beat.lift=lift;beat.high=Math.max(lift,high);beat.land=land;beat.damage=damage;
                    beat.scene=true;beat.sceneAt=Math.max(CUT,beat.age(0));beat.landedAt=CUT;beat.met=victim;beat.ready=0;
                    start(mc,beat);
                }else{
                    var beat=BEATS.get(user);
                    if(beat!=null&&beat.scene&&Float.isNaN(beat.ended)){beat.ended=beat.age(0);beat.cut=cut;beat.ready=ready;if(cut&&beat.voice!=null)beat.voice.fade();}
                }
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(GoldBeatdown::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->{clear();heldG=false;}));
    }
    private static boolean heard(MinecraftClient mc,Identifier dim){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return false;
        if(world!=mc.world){clear();world=mc.world;}
        return true;
    }
    private static Vec3d vec(net.minecraft.network.PacketByteBuf b){return new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
    private static void punch(UUID user,int type,long start,long now,float yaw,long ready,float aim,int met,boolean broke){
        var beat=BEATS.get(user);
        switch(type){
            case GoldExperienceBeatdown.OPEN->{
                var old=beat;
                beat=new Beat(user,start,now-start,yaw,aim);beat.ready=ready;BEATS.put(user,beat);
                if(old!=null&&old.voice!=null)old.voice.fade();
            }
            case GoldExperienceBeatdown.LANDED->{
                if(beat==null||beat.start!=start||beat.scene)return;
                beat.ready=ready;beat.landedAt=(now-start)/20f;beat.met=met;beat.broke=broke;
            }
            case GoldExperienceBeatdown.STOPPED->{
                // (the glow cut short tells only its cooldown: no punch was thrown)
                if(beat==null||beat.scene||beat.start!=start){
                    if(beat==null||!beat.scene||beat.ended()){var only=new Beat(user,start,1000,yaw,Float.NaN);only.stopped=0;only.ready=ready;BEATS.put(user,only);}
                    return;
                }
                beat.ready=ready;if(Float.isNaN(beat.stopped))beat.stopped=Math.max(0,beat.age(0));
            }
            default->{}
        }
    }
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var b:BEATS.values())if(b.voice!=null&&!b.voice.isDone())mc.getSoundManager().stop(b.voice);
        BEATS.clear();world=null;
    }
    /** The cutscene starts: its sound, the video's from its second shot on, on its tick: loud for its two, fading out to 40 blocks for those near; none if it was heard too late to come on its beat. */
    private static void start(MinecraftClient mc,Beat b){
        long age=b.serverAge+ticks-b.received;
        if(mc.player!=null&&age<=BEAT_SOUND+2&&b.voice==null){
            boolean in=mc.player.getUuid().equals(b.user)||mc.player.getUuid().equals(b.victimId);
            double far=in?0:b.from.distanceTo(mc.player.getPos());
            float volume=(float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/40));
            if(volume>.01f){
                b.voice=new StandAttack.Shout(CelestialFall.id("gold_experience_beatdown"),volume);
                int delay=(int)(BEAT_SOUND-age);
                if(delay>0)mc.getSoundManager().play(b.voice,delay);else mc.getSoundManager().play(b.voice);
            }
        }
        if(mc.player!=null&&(mc.player.getUuid().equals(b.user)||mc.player.getUuid().equals(b.victimId)))dev.sangwoo.celestialfall.client.integration.CinematicsBridge.nativeCutscene();
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!BEATS.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        // one long over is let go once its cooldown is told and past (CooldownHud reads it from here)
        long now=mc.world.getTime();
        BEATS.values().removeIf(b->b.age(0)>b.over()+2.5f&&now>=b.ready);
    }

    // ───────────────────────────── who is in one ─────────────────────────────

    static java.util.Collection<Beat> all(){return BEATS.values();}
    /** This player's beatdown (as its user), while it shows. */
    public static Beat of(UUID user){var b=BEATS.get(user);return b!=null&&b.age(0)<=b.over()+2.5f?b:null;}
    /** The world tick this player's beatdown is ready again (0 if not known). */
    public static long ready(UUID id){var b=BEATS.get(id);return b==null?0:b.ready;}
    /** This player's stand is not Gold Experience: its cooldown is gone, as the server forgets it. */
    public static void forget(UUID id){var b=BEATS.get(id);if(b!=null)b.ready=0;}
    /** The cutscene that has this one (its user or the one struck), while it runs. */
    public static Beat running(Entity e){
        if(e==null)return null;
        for(var b:BEATS.values()){if(!b.scene)continue;float t=b.age(0);if(t>=CUT&&t<b.over()&&(b.user.equals(e.getUuid())||b.victim==e.getId()&&b.victimId.equals(e.getUuid())))return b;}
        return null;
    }
    /** Whether this one is held where they are by a cutscene now. */
    public static boolean holds(Entity e){return MapClient.enabled()&&running(e)!=null;}
    /** The cutscene the one struck is in, while it shows them (into the moments after it, until they are shown where they really are). */
    static Beat struck(Entity e){
        for(var b:BEATS.values())if(b.scene&&b.victim==e.getId()&&b.victimId.equals(e.getUuid())){float t=b.age(0);if(t>=CUT-.05f&&t<=b.over()+2.5f)return b;}
        return null;
    }
    /** Whether this client's player holds back their cry of pain at a blow of a cutscene on them: all through the rush (the recording carries it), until the last blow. */
    public static boolean quietHurt(net.minecraft.entity.LivingEntity e){
        var mc=MinecraftClient.getInstance();if(mc.player==null||e!=mc.player)return false;
        for(var b:BEATS.values()){
            if(!b.scene||b.victim!=e.getId()||!b.victimId.equals(e.getUuid()))continue;
            float t=b.age(0);if(t<CUT||t>b.over()+.5f)continue;
            return t<(WIND+FINAL)*.5f;
        }
        return false;
    }
    /** The cutscene the local player is in (its user, or the one struck), while its camera has them. */
    public static Beat cinematic(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!mc.player.isAlive()||!MapClient.enabled()||!CelestialFall.CONFIG.cinematicCamera)return null;
        for(var b:BEATS.values()){
            if(!b.scene||!b.user.equals(mc.player.getUuid())&&!b.victimId.equals(mc.player.getUuid()))continue;
            float t=b.age(0);if(t>=CUT&&t<b.over()+HANDBACK)return b;
        }
        return null;
    }

    // ───────────────────────────── the rush's fists ─────────────────────────────

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    static float kick(float t,float at,float tau){return FxLib.kick(t,at,tau);}
    /** The rush's fists as drawn: one landing every GoldRush.HALF from just after the punch until the stand draws back. */
    static final int FISTS=(int)Math.floor((WIND-BARRAGE_FROM)/GoldRush.HALF+1e-4);
    /** The rush's fists (from 0) that have landed by {@code t}. */
    static int fists(float t){return t<BARRAGE_FROM+GoldRush.HALF?0:Math.min(FISTS,(int)Math.floor((t-BARRAGE_FROM)/GoldRush.HALF+1e-4));}
    /** When the rush's fist {@code k} (from 0) lands. */
    static float fistAt(int k){return BARRAGE_FROM+(k+1)*GoldRush.HALF;}
    /** The stance fist {@code k} strikes in (GoldRush.fist: left and right in turn, at the middle, high or low). */
    static int fistStance(Beat b,int k){return GoldRush.fist(k,b.seed);}
    /** Which side fist {@code k} comes in on: 1 the stand's right, -1 its left. */
    static int fistSide(int k){return (k&1)==0?-1:1;}
    /** How high a stance's fist lands: 0 the chest, 1 the face, 2 the belly. */
    static int fistHeight(int stance){return stance==RUSH_L_HI||stance==RUSH_R_HI?1:stance==RUSH_L_LO||stance==RUSH_R_LO?2:0;}
    /** How much the barrage is on at {@code t} (0 to 1): from just after the punch lands until it draws back for the last. */
    static float barrage(float t){return smooth((t-BARRAGE_FROM)/.12f)*(1-smooth((t-WIND-.02f)/.12f));}

    // ───────────────────────────── the one struck ─────────────────────────────

    /** How far the stand is under the one struck at {@code t} (0 in front of them, face to face; 1 under them): from the "WRYYY" on. */
    static float under(float t){return smooth((t-(WRY-.1f))/.6f);}
    /** How high the one struck is held at {@code t} (blocks over where they stood): up as the rush begins, up again through the "WRYYY". */
    static double height(Beat b,float t){return b.lift*Ease.out(t,CUT+STOP,KNOCKED+.1f)+(b.high-b.lift)*smooth((t-WRY)/(RISEN-WRY));}
    /** Where the one struck is shown at {@code t}, before it is over. */
    public static Body victim(Beat b,float t){return victim(b,t,true);}
    /**
     * Where the one struck is shown at {@code t}, with the fists' jolts or without them ({@code jolted}: the stand keeps
     * its place by where they are held, not by each jolt): held still through the punch's hit-stop, turned to face its
     * user; knocked back to where they are held and up into the air, bent back over the blow; held up there by the
     * rush, driven on along its way, wrenched by every fist (the face snapped back, the chest knocked back, doubled up
     * over one to the belly, twisted away from the side it comes in on); at the "WRYYY" tipped over face down above the
     * stand and driven up and up, each fist under them jolting them higher; hanging limp as it draws back; then sent flying.
     */
    static Body victim(Beat b,float t,boolean jolted){
        float face=smooth((t-CUT)/FACE);
        if(t<CUT+STOP-.02f)return new Body(b.victimFrom,0,0,0,MIDDLE,face);
        if(t>=FINAL)return flight(b,t);
        double fw=0,up=height(b,t),rt=0;float lean=0,roll=0,spin=0;
        float knock=Ease.out(t,CUT+STOP,KNOCKED),side=(b.seed&1)==0?1:-1;
        lean+=30*kick(t,CUT+STOP,.3f);roll+=9*side*kick(t,CUT+STOP,.3f);spin+=11*side*kick(t,CUT+STOP,.4f);
        float w=under(t);
        // tipped over face down above the stand
        lean-=PRONE*w;
        // bobbing in the air, driven on along the rush's way
        float rush=smooth((t-(CUT+.15f))/.2f),rushing=rush*(1-smooth((t-WIND)/.25f));
        up+=rush*.06*Math.sin((t-CUT)*Math.PI*2/.9);
        fw+=b.drift*Ease.unit(t,CUT+.6f,WRY);
        int n=jolted?fists(t):0;
        for(int k=Math.max(0,n-4);k<n;k++){
            float j=kick(t,fistAt(k),.06f);if(j<=0)continue;
            int s=b.seed+k*7919,h=fistHeight(fistStance(b,k)),in=fistSide(k);
            float x=(float)Ease.hash(s);
            // face to face: knocked back along the way; under them: jolted up
            fw+=(h==0?.12:h==1?.07:.09)*j*(1-w);
            up+=((h==2?.07:h==1?.03:.01)*(1-w)+.11*w)*j;
            lean+=((h==0?11+7*x:h==1?5+4*x:-(11+6*x))*(1-w)+(6+5*x)*(h==1?-1:1)*w)*j;
            spin+=in*(h==1?7:13)*(.75f+.5f*(float)Ease.hash(s+2))*j;
            roll-=in*(5+6*(float)Ease.hash(s+1))*j;
        }
        lean+=rushing*3*(float)Math.sin(t*61);roll+=rushing*2*(float)Math.sin(t*47+1);
        // the last blow coming: they hang there limp
        float hang=smooth((t-(WIND+.05f))/.3f);
        lean-=6*hang;up-=.12*hang;
        Vec3d o=b.victimFrom.lerp(b.hold,knock);
        return new Body(b.at(o,fw,up,rt),lean,roll,spin,MIDDLE,face);
    }
    /** Where the last blow sends them from. */
    static Body launch(Beat b){return victim(b,FINAL-1e-4f);}
    /** Sent flying by the last blow: held a moment, then along an arc to where they come down (SLIDE short of where they end), turning over to land on their back; a bounce, a slide, lying a moment, then up. */
    static Body flight(Beat b,float t){
        Body from=launch(b);
        Vec3d down=b.land.subtract(b.f.multiply(SLIDE));
        float u=Ease.unit(t,FINAL+FINAL_STOP,LAND),h=1-(float)Math.pow(1-u,1.5);
        double dist=Math.hypot(down.x-from.feet().x,down.z-from.feet().z);
        double apex=1.6+.1*dist;
        double x=from.feet().x+(down.x-from.feet().x)*h,z=from.feet().z+(down.z-from.feet().z)*h;
        double y=from.feet().y+(down.y-from.feet().y)*u*u+apex*4*u*(1-u);
        float turn=(float)(1-Math.pow(1-u,2));
        float lean=from.lean()+(450-from.lean())*turn;
        float roll=from.roll()*(1-u),spin=from.spin()*(1-u)+30*(float)Math.sin(u*Math.PI)*((b.seed&1)==0?1:-1);
        float bounce=t<LAND?0:(float)Math.max(0,Math.sin(Math.PI*Ease.unit(t,LAND+.02f,LAND+.3f)))*(1-Ease.unit(t,LAND+.02f,LAND+.3f)*.3f);
        y+=.35*bounce;
        float slide=Ease.out(t,LAND,LAND+.4f);
        x+=b.f.x*SLIDE*slide;z+=b.f.z*SLIDE*slide;
        float rise=smooth((t-GETUP)/(STOOD-GETUP));
        if(t>=LAND)lean=450-90*rise;
        float theta=(float)Math.toRadians(MathHelper.clamp(lean-360,0,90)),ground=smooth((t-(LAND-.15f))/.15f);
        y-=ground*(MIDDLE*(1-Math.cos(theta))-.25*Math.sin(theta));
        return new Body(new Vec3d(x,y,z),lean,roll,spin,MIDDLE,1);
    }
    static boolean killed(Entity e,float t){return t>=LAND-.05f&&e instanceof net.minecraft.entity.LivingEntity l&&l.isDead();}
    /** Where the one struck is shown this frame, if a cutscene has them (null if none does): StandBeatdown.shown's ways. */
    public static Body shown(Entity e,float delta){
        var b=struck(e);if(b==null)return null;
        float t=b.age(delta),over=b.over();
        if(killed(e,t))return flight(b,Math.min(t,GETUP));
        if(t<over)return victim(b,t);
        Vec3d real=e.getLerpedPos(delta);
        if(b.cut){
            float k=smooth((t-over)/.25f);if(k>=1)return null;
            var at=victim(b,over);
            return new Body(at.feet().lerp(real,k),at.lean()*(1-k),at.roll()*(1-k),at.spin()*(1-k),at.pivot(),1-k);
        }
        if(real.squaredDistanceTo(b.land)<.04||t>over+2)return null;
        return new Body(b.land,0,0,0,MIDDLE,1);
    }
    /** The way the one struck faces in a cutscene: at its user. */
    public static float facing(Beat b){return b.yaw+180;}
    /** A point of the one struck's body as shown: {@code along} blocks out of their front (negative: toward the stand) and {@code up} over their feet as they stand, tipped as they lean about their middle. */
    static Vec3d point(Beat b,Body v,double along,double up){
        double l=Math.toRadians(v.lean()),q=up-MIDDLE;
        return v.feet().add(0,MIDDLE+q*Math.cos(l)-along*Math.sin(l),0).add(b.f.multiply(along*Math.cos(l)+q*Math.sin(l)));
    }
    /** The one struck's head (their eyes) and chest as shown. */
    static Vec3d head(Beat b,Body v){return point(b,v,0,EYES);}
    static Vec3d chest(Beat b,Body v){return point(b,v,0,1.15);}

    // ───────────────────────────── the stand ─────────────────────────────

    /** How far wound up the opening punch is, and how far thrown (landing at CUT, fastest as it lands). */
    static float wind(float t){return smooth(t/WOUND);}
    static float strike(float t){float u=MathHelper.clamp((t-LOAD)/(CUT-LOAD),0,1);return (float)Math.pow(u,1.6);}
    /**
     * The keys of the stand's stances through a cutscene but for the rush: {time, stance, ease (0 smooth, 1 hold, 2
     * strike)}: the punch, held as it lands; past the rush the guard, wound far back, loaded, the last blow thrown, held
     * out, the guard.
     */
    static final float[][] KEYS={{0,GUARD,0},{WOUND,OVER_WIND,0},{LOAD,OVER_WIND,1},{CUT,OVER_PUNCH,2},{CUT+.06f,OVER_PUNCH,1},
        {WIND+.1f,GUARD,0},{WIND+.55f,OVER_WIND,0},{FINAL-.17f,OVER_WIND,1},{FINAL,OVER_PUNCH,2},{FINAL+.75f,OVER_PUNCH,1},{LEAVE,GUARD,0}};
    /** Nothing coming of the punch: held out, then back in its guard. */
    static final float[][] MISSED={{0,GUARD,0},{WOUND,OVER_WIND,0},{LOAD,OVER_WIND,1},{CUT,OVER_PUNCH,2},{HELD,OVER_PUNCH,1},{GUARDED,GUARD,0}};
    static float ease(int ease,float x){x=x<0?0:x>1?1:x;return ease==2?(float)Math.pow(x,1.6):smooth(x);}
    static void keyed(float[][] keys,float t,float[] w,float share){
        if(t<=keys[0][0]){w[(int)keys[0][1]]+=share;return;}
        for(int i=1;i<keys.length;i++){
            if(t>keys[i][0])continue;
            float s=ease((int)keys[i][2],(t-keys[i-1][0])/(keys[i][0]-keys[i-1][0]));
            w[(int)keys[i-1][1]]+=share*(1-s);w[(int)keys[i][1]]+=share*s;return;
        }
        w[(int)keys[keys.length-1][1]]+=share;
    }
    /** How much of the rush's barrage its stances are at {@code t}: as {@link #barrage}, but in from when this client heard of the cutscene (never before). */
    static float bar(Beat b,float t){return b.scene?barrage(t)*smooth((t-b.sceneAt)/.1f+.5f):0;}
    /** Adds the stand's stances at {@code t} into {@code w}. Stopped short or cut short it turns back to its guard from the pose it had got to. */
    static void stances(Beat b,float t,float[] w){
        float at=t,back=0;
        if(!b.scene&&!Float.isNaN(b.stopped)){at=Math.min(t,b.stopped);back=smooth((t-b.stopped)/STOPPED);}
        if(b.scene&&b.cut&&!Float.isNaN(b.ended)){at=Math.min(t,b.ended);back=smooth((t-b.ended)/.25f);}
        float bar=bar(b,at);
        keyed(b.scene?KEYS:MISSED,at,w,(1-bar)*(1-back));
        if(bar>0)GoldRush.barrage(at-BARRAGE_FROM,b.seed,w,bar*(1-back));
        w[GUARD]+=back;
    }
    /** Whether beatdown {@code b} shows the stand {@code t} seconds into it. */
    static boolean shows(Beat b,float t){return t>=0&&t<=leaves(b)+RETURN+.05f;}
    /** When the stand goes back to its user's shoulder. */
    static float leaves(Beat b){
        if(!b.scene)return b.over()+LINGER;
        return b.cut&&!Float.isNaN(b.ended)?b.ended+.1f:LEAVE;
    }
    /** How far round to the front of its user it is (0 at their shoulder, 1 in front). */
    static float engage(Beat b,float t){return StandPunch.burst(t/StandRush.RISE)*(1-smooth((t-leaves(b))/RETURN));}
    /** How big its arms are at {@code t} before either stretches out. */
    static float armSize(Beat b,float t){
        float punch=1+(GoldPunch.ARMS-1)*smooth(t/StandRush.RISE);
        if(!b.scene)return 1+(punch-1)*(1-smooth((t-b.over()+.3f)/.3f));
        float over=b.cut&&!Float.isNaN(b.ended)?b.ended:LEAVE;
        return 1+(punch-1+(ARMS-GoldPunch.ARMS)*smooth((t-CUT)/.2f)*(1-smooth((t-WIND)/.2f)))*(1-smooth((t-over)/.3f));
    }
    /** How far the stand leans back at {@code t} (degrees): under the one struck, its fists driven up at them; less if there is no room for it to get under them. */
    static float lean(Beat b,float t){
        if(!b.scene)return 0;
        return LEAN*under(t)*room(b,t)*(1-smooth((t-(FINAL+.3f))/.4f));
    }
    /** How much of the way under the one struck there is room for (0 to 1): they must be high enough over the ground for the stand to float beneath them. */
    static float room(Beat b,float t){return smooth((float)((height(b,Math.min(t,FINAL-1e-4f))-.6)/1.2));}
    /** Where a fist at full stretch is from the stand's feet as it leans {@code lean} degrees back: {ahead, up} (blocks). */
    static double[] reach(GoldSkin skin,float lean,float size,int stance,int side){
        float[] g=GoldRush.grown(skin,stance,side,size);double waist=skin.waist;
        double l=Math.toRadians(-lean),q=g[2]-waist;
        return new double[]{g[0]*Math.cos(l)+q*Math.sin(l),waist+q*Math.cos(l)-g[0]*Math.sin(l)};
    }
    /** Where on the one struck its fists land (their chest's front, a little into them), as they are held at {@code t} without the fists' jolts. */
    static Vec3d target(Beat b,float t){return point(b,victim(b,Math.min(t,FINAL-1e-4f),false),-.3+BITE,1.2);}
    /** The stand's feet at {@code t} through a cutscene: where its fist at full stretch lands on the one struck (never under the ground its user stands on), its dash into the last blow on top. */
    static Vec3d standFeet(Beat b,float t){
        float lean=lean(b,t),size=ARMS*(1+GoldRush.STRETCH);
        double[] reach=reach(b.skin,lean,size,RUSH_L,0);
        Vec3d to=target(b,t);
        double ahead=to.subtract(b.from).dotProduct(b.f)-reach[0]-FINAL_BACK*smooth((t-(WIND+.05f))/.35f)*(1-ease(2,(t-(FINAL-.17f))/.17f));
        double y=Math.max(b.from.y+.05,to.y-reach[1]);
        return b.from.add(b.f.multiply(ahead+lunge(t))).withAxis(net.minecraft.util.math.Direction.Axis.Y,y);
    }
    /** How far it draws back from them for its last blow (blocks), and how far it dashes on into it. */
    static final double FINAL_BACK=.5;
    static float lunge(float t){return .45f*kick(t,FINAL,.4f);}
    /** A point of the stand's body at {@code t}: {@code ahead} of its feet and {@code up} over them as it stands, tipped as it leans about its waist. */
    static Vec3d standPoint(Beat b,float t,double ahead,double up){
        double waist=b.skin.waist,l=Math.toRadians(-lean(b,t)),q=up-waist;
        return standFeet(b,t).add(0,waist+q*Math.cos(l)-ahead*Math.sin(l),0).add(b.f.multiply(ahead*Math.cos(l)+q*Math.sin(l)));
    }
    /** How far in front of its user it stands for its punch (the overdrive punch's way: its fist goes BITE into the first one in its way). */
    static float place(Beat b){
        if(Float.isNaN(b.aim))return AHEAD;
        return MathHelper.clamp(b.aim+BITE-GoldPunch.fistReach(b.skin),NEAR,FAR);
    }
    /**
     * Beatdown {@code b} this frame on a look that already holds the stand's pose: its stances take over (and give way
     * again as it goes back), its arms grow, it leans back under the one struck, and {@code shape} says where it stands.
     * Returns whether it shows.
     */
    static boolean pose(Beat b,float delta,GoldExperienceLook look,GoldRush.Shape shape,float[] scratch){
        float t=b.age(delta);if(!shows(b,t))return false;
        java.util.Arrays.fill(scratch,0);
        stances(b,t,scratch);
        float in=smooth(t/TAKE)*(1-smooth((t-leaves(b))/RETURN));
        for(int i=0;i<scratch.length;i++)look.weights[i]=look.weights[i]*(1-in)+scratch[i]*in;
        float size=armSize(b,t),left=GoldRush.out(scratch,GoldRush.LEFT),right=GoldRush.out(scratch,GoldRush.RIGHT);
        look.armL=look.armL*(1-in)+size*(1+GoldRush.STRETCH*left)*in;look.armR=look.armR*(1-in)+size*(1+GoldRush.STRETCH*right)*in;
        shape.engage=engage(b,t);shape.yaw=b.yaw;shape.side=0;shape.inward=0;
        float ahead=place(b),raise=GoldRush.RAISE,lean=0;
        if(b.scene){
            // from where its punch landed to where the rush has it, as the one struck is knocked back and up
            float k=smooth((t-CUT)/.3f);Vec3d feet=standFeet(b,t);
            ahead+=((float)feet.subtract(b.from).dotProduct(b.f)-ahead)*k;
            raise+=((float)(feet.y-b.from.y-StandPresence.UP)-raise)*k;
            lean=-lean(b,t);
        }
        shape.ahead=ahead;shape.raise=raise;shape.lean=lean;
        return true;
    }
    /** How strongly afterimage {@code i} of the rush's arms shows in a cutscene (0 none), its stances into {@code into}: from a moment into the rush until it draws back. */
    static float phantom(Beat b,int i,float delta,GoldExperienceLook into){
        if(!b.scene||i>=GoldRush.PHANTOMS.length)return 0;
        float t=b.age(delta),at=b.cut&&!Float.isNaN(b.ended)?Math.min(t,b.ended):t;
        float show=barrage(at)*phantoms(at)*(b.cut&&!Float.isNaN(b.ended)?1-smooth((t-b.ended)/.15f):1);if(show<=0)return 0;
        var w=into.weights;java.util.Arrays.fill(w,0);
        GoldRush.barrage(at-BARRAGE_FROM-GoldRush.PHANTOMS[i][0],b.seed+101*(i+1),w,1);
        float size=armSize(b,t),left=GoldRush.out(w,GoldRush.LEFT),right=GoldRush.out(w,GoldRush.RIGHT);
        into.armL=size*(1+GoldRush.STRETCH*left);into.armR=size*(1+GoldRush.STRETCH*right);
        into.showL=smooth(left);into.showR=smooth(right);
        return show*GoldRush.PHANTOMS[i][4]*.8f;
    }
    /**
     * How strongly the afterimages of its arms show at {@code t} (0 to 1), by the cutscene's shots (the first take had
     * them all through it: in the close shots they were slabs of gold across the whole frame, hiding the fists and the
     * one struck): none through the first cuts back and forth, some from behind the one struck, none in the close shots
     * of them, all of them in the shot from their eyes (the video's, the stand's many fists), fewer from then on.
     */
    static float phantoms(float t){
        float behind=.5f*smooth((t-GoldBeatdownCamera.C3)/.15f)*(1-smooth((t-(GoldBeatdownCamera.C4-.12f))/.12f));
        float eyes=smooth((t-GoldBeatdownCamera.C5)/.15f)*(1-.55f*smooth((t-(WRY-.1f))/.2f));
        return Math.max(behind,eyes);
    }
    /** How much brighter its aura burns at {@code t}: through the rush, more through the "WRYYY", and as it loads its last blow. */
    static float glow(Beat b,float t){
        if(!b.scene)return .5f*wind(t)*(1-smooth((t-CUT)/.3f));
        return .5f*barrage(t)+.35f*smooth((t-WRY)/.4f)*(1-smooth((t-WRY_END)/.6f))+.7f*smooth((t-WIND)/.5f)*(1-smooth((t-FINAL)/.3f));
    }

    // ───────────────────────────── the camera ─────────────────────────────

    /** The camera of a cutscene's user and of the one struck this frame (GoldBeatdownCamera's shots, every blow jolting it), coming back to the gameplay camera over HANDBACK once it is over. Kept out of the walls. */
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){
        var b=cinematic();if(b==null)return null;
        float t=b.age(delta),at=Math.min(t,b.over());
        var shot=GoldBeatdownCamera.at(b,at);var jolt=GoldBeatdownCamera.jolt(b,at);
        var mc=MinecraftClient.getInstance();
        Vec3d eye=StandBeatdown.clear(mc,shot.look(),shot.eye());
        Vec3d d=shot.look().subtract(eye);if(d.lengthSquared()<1e-6)d=b.f;d=d.normalize();
        float sy=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),sp=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));
        if(jolt!=StandBeatdownCamera.Jolt.NONE){
            Vec3d right=d.crossProduct(new Vec3d(0,1,0));right=right.lengthSquared()<1e-6?b.r:right.normalize();
            Vec3d up=right.crossProduct(d);
            eye=eye.add(right.multiply(jolt.right())).add(up.multiply(jolt.up()));
            sy+=(float)(jolt.right()*StandBeatdown.JOLT_TURN);sp-=(float)(jolt.up()*StandBeatdown.JOLT_TURN);
        }
        float roll=shot.roll()+jolt.roll(),k=handback(b,t);
        if(k<=0){shotEye=eye;return new CinematicDirector.Pose(eye,sy,sp,roll);}
        shotEye=eye.lerp(vanilla,k);
        return new CinematicDirector.Pose(shotEye,sy+MathHelper.wrapDegrees(yaw-sy)*k,sp+(pitch-sp)*k,roll*(1-k));
    }
    private static Vec3d shotEye;
    /** Whether {@code e} is not drawn this frame for this client's camera while it is the cutscene's: the one struck while its shot is from inside them (their own eyes). */
    public static boolean hidden(Entity e){
        var b=cinematic();if(b==null||shotEye==null)return false;
        if(b.victim!=e.getId()||!b.victimId.equals(e.getUuid()))return false;
        var mc=MinecraftClient.getInstance();float t=b.age(mc.getTickDelta());
        if(t>=b.over()||mc.gameRenderer.getCamera().getPos().squaredDistanceTo(shotEye)>.05*.05)return false;
        return GoldBeatdownCamera.at(b,t).inside();
    }
    static float handback(Beat b,float t){return smooth((t-b.over())/HANDBACK);}
    public static double fov(double vanilla,float delta){
        var b=cinematic();if(b==null)return vanilla;
        float t=b.age(delta),at=Math.min(t,b.over());
        double fov=GoldBeatdownCamera.at(b,at).fov()-GoldBeatdownCamera.jolt(b,at).lens();
        return fov+(vanilla-fov)*handback(b,t);
    }
    public static float roll(float delta){
        var b=cinematic();if(b==null)return 0;
        float t=b.age(delta),at=Math.min(t,b.over());
        return (GoldBeatdownCamera.at(b,at).roll()+GoldBeatdownCamera.jolt(b,at).roll())*(1-handback(b,t));
    }
    /** How high the black bars are this frame (share of the screen's height each): in as the punch lands, out as it ends. */
    public static float bars(float delta){
        var b=cinematic();if(b==null)return 0;
        return bars(b,b.age(delta));
    }
    static float bars(Beat b,float t){
        float in=Ease.unit(t,CUT,CUT+BARS_IN);
        float c=1.7f,u=in-1,ein=in>=1?1:1+u*u*((c+1)*u+c);
        float out=smooth((t-(b.over()-BARS_OUT+.05f))/BARS_OUT);
        return Math.max(0,StandBeatdown.BAR*ein*(1-out));
    }

    /** For the capture tools: this player's beatdown ("age,scene,ended,cut,landed,met,broke,stopped,bars"), or null. */
    public static String describe(PlayerEntity p,float delta){
        var b=BEATS.get(p.getUuid());if(b==null)return null;
        float t=b.age(delta);
        return String.format(java.util.Locale.ROOT,"%.3f,%d,%d,%d,%d,%d,%d,%d,%.3f",t,b.scene?1:0,Float.isNaN(b.ended)?0:1,b.cut?1:0,b.landed()?1:0,b.met,b.broke?1:0,Float.isNaN(b.stopped)?0:1,b.scene?bars(b,t):0);
    }

    /** G for Gold Experience's user on the map: the beatdown, if the stand is out. Returns whether it took the key. */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(key!=GLFW.GLFW_KEY_G||window!=mc.getWindow().getHandle())return false;
        if(action==GLFW.GLFW_RELEASE){if(!heldG)return false;heldG=false;return true;}
        if(heldG)return true;
        if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.goldExperience(mc.player))return false;
        heldG=true;
        if(GoldPresence.out(mc.player)&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(GoldExperienceBeatdown.INPUT)){
            BotandCamera.prepareInteraction();
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
            ClientPlayNetworking.send(GoldExperienceBeatdown.INPUT,PacketByteBufs.empty());
        }
        return true;
    }
}
