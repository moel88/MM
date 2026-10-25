package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.CinematicDirector;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.FxLib;
import dev.sangwoo.celestialfall.stand.StarPlatinumBeatdown;
import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;
import static dev.sangwoo.celestialfall.stand.StarPlatinumStance.*;

/**
 * Star Platinum's beatdown's cutscene as this client draws it (2.64.0, user request: "이제 G 비트다운을 만들어야해 이건
 * 컷씬이 나와야하고 소리대로 설명을 해줄게 ... 야레야레라고 할때 플레이어가 손으로 머리를 잡고 머리를 흔들어 그리고
 * 스타플래티나 라고 외칠거거든 그때 스타플래티나 보여주고 오라오라 러쉬 엄청 꽂을꺼야 그걸 컷씬으로 만들어줘야해", "컷씬
 * 나올때는 비트다운 컷씬이 시작되면 화면 상단과 하단에서 검은색 레터박스 바가 애니메이션과 함께 나타나도록 구현해 주고
 * 비트다운 꽂을때는 플레이어의 멱살을 잡고 올려치고 그대로 오라러쉬를 꽂아줘 찰지게 때리고 날려야해 컷씬 신경써서
 * 만들어줘"). The server says when one starts (its user, the one struck, where everyone is, where they come down) and
 * when it ends (StarPlatinumBeatdown); everything between is drawn from the beatdown's own clock (seconds from G: the
 * server's age as it was sent, this client's ticks since and the frame's part of a tick), on the user's recording's
 * time (StarPlatinumRules.BEAT_*): the blow lands and holds a moment, the stand goes back into its user; its user says
 * "yare yare daze" with a hand on their head, shaking it; at "Star Platinum!" the stand bursts out of them; it comes
 * round, grabs the one struck by the collar, uppercuts them into the air, rushes them there, throws five big ORAs and
 * the last one sends them flying to where they come down.
 * <p>
 * Here: when it runs, where the one struck is shown and how their body turns ({@link #victim}, drawn there by the
 * renderers, which put the body there in place of where it is), how the stand moves through it ({@link #shape}, the
 * stance it takes and where it stands, and {@link #standOut}: when it goes back in and comes out), the camera of its
 * user and of the one struck ({@link #camera}: shots cut to the beats of the recording, the black bars sliding in and
 * out, {@link #bars}) and its sound (the rest of the recording, from BEAT_SOUND). StandBeatdownFx draws its light,
 * BeatdownPose its user's body and the one struck's.
 */
public final class StandBeatdown {
    // ───────────────────────────── its moments (seconds from G) ─────────────────────────────

    static final float CUT=BEAT_CUT/20f,SOUND=BEAT_SOUND/20f;
    /** The blow holds this long as it lands (the hit-stop), and the one struck is knocked back to where they are held by KNOCKED. */
    static final float STOP=.15f,KNOCKED=.95f;
    /** The stand goes back into its user once its blow has held (StandTimeline's going back). */
    static final float RETREAT=.42f;
    static final float YARE=BEAT_YARE/20f,YARE_END=BEAT_YARE_END/20f,REVEAL=BEAT_REVEAL/20f;
    /** The stand comes round from its user's shoulder to the front (as a punch's does) for the grab. */
    static final float ENGAGE=3.55f,ENGAGED=3.8f;
    static final float GRAB=BEAT_GRAB/20f,UPPER=BEAT_UPPERCUT/20f;
    static final float RUSH_FROM=BEAT_RUSH_FIRST/20f,RUSH_TO=BEAT_RUSH_LAST/20f,RUSH_EVERY=BEAT_RUSH_EVERY/20f;
    static final float[] BIG=new float[BEAT_BIG.length];
    static{for(int i=0;i<BIG.length;i++)BIG[i]=BEAT_BIG[i]/20f;}
    static final float FINAL=BEAT_FINAL/20f,LAND=BEAT_LAND/20f,END=BEAT_END/20f;
    /** The one struck lies where they came down until GETUP, and is up again at UP. */
    static final float GETUP=12.0f,STOOD=12.42f;
    /** The stand goes back to its user's shoulder at the end: from LEAVE, over RETURN seconds. */
    static final float LEAVE=12.0f,RETURN=.45f;
    /** The camera comes back to its user over this long once it is over (or cut short). */
    static final float HANDBACK=.3f;
    /** The black bars: this high (share of the screen's height each), in over BARS_IN once the blow lands, out over BARS_OUT. */
    public static final float BAR=.12f;static final float BARS_IN=.38f,BARS_OUT=.35f;

    // ───────────────────────────── where the one struck is ─────────────────────────────

    /** How high their middle is (blocks above their feet): what their body turns about in the air. */
    static final float MIDDLE=.9f;
    /** How far their body is shown lowered as it lies (blocks), lying on its back with its middle near the ground. */
    static final float LIE=.62f;
    /** How far they slide on once down (blocks along the blow): where they come down is this short of where they end. */
    static final double SLIDE=.35;
    /** How long the one struck takes to be turned round to face its user as the blow lands (seconds). */
    static final float FACE=.15f;

    /** A cutscene as this client heard of it. */
    public static final class Beat {
        public final UUID user,victimId;public final int victim;final long serverAge,received;public final float yaw;
        public final Vec3d from,victimFrom,hold,land;public final double drift;public final float damage;
        /** Its way, to its right, and how far ahead of its user the one struck is held. */
        public final Vec3d f,r;public final double reach;final int seed;
        /** When it ended (on its clock, seconds; NaN while it runs) and whether it was cut short. */
        float ended=Float.NaN;boolean cut;
        /** Whether this client's player, the one struck, has cried out at its first blow (quietHurt). */
        boolean cried;
        StandAttack.Shout voice;
        Beat(UUID user,int victim,UUID victimId,long start,long serverAge,float yaw,Vec3d from,Vec3d victimFrom,Vec3d hold,double drift,Vec3d land,float damage){
            this.user=user;this.victim=victim;this.victimId=victimId;this.serverAge=serverAge;received=ticks;this.yaw=yaw;
            this.from=from;this.victimFrom=victimFrom;this.hold=hold;this.drift=drift;this.land=land;this.damage=damage;
            f=Vec3d.fromPolar(0,yaw);r=new Vec3d(-f.z,0,f.x);reach=hold.subtract(from).dotProduct(f);
            seed=(int)(start*31+user.getLeastSignificantBits()&0x7fffffff);
        }
        /** Seconds on its clock this frame. */
        public float age(float delta){return (serverAge+ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        /** When it is over on its clock: its end, or when it was cut short. */
        public float over(){return Float.isNaN(ended)?END:Math.min(END,ended);}
        /** A point ahead along its way, up and to its right of {@code o}. */
        Vec3d at(Vec3d o,double fw,double up,double rt){return o.add(f.multiply(fw)).add(r.multiply(rt)).add(0,up,0);}
        /** For the multiplayer check: whether the server said it ended, whether it was cut short, and its cutscene's sound here. */
        public boolean ended(){return !Float.isNaN(ended);}
        public boolean wasCut(){return cut;}
        public net.minecraft.client.sound.SoundInstance voice(){return voice;}
    }
    /** Where the one struck is shown this moment: their feet, how far back their body leans (degrees), its roll, its spin about the up axis, what it turns about (blocks over the feet), and how wholly the cutscene has them (0 where they are, 1 where it shows them). */
    public record Body(Vec3d feet,float lean,float roll,float spin,float pivot,float hold){}

    private static final Map<UUID,Beat> BEATS=new HashMap<>();
    static long ticks;
    private static Object world;
    private StandBeatdown(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(StarPlatinumBeatdown.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();
            long start=b.readLong(),now=b.readLong();float yaw=b.readFloat();int victim=b.readInt();UUID victimId=b.readUuid();
            Vec3d from=vec(b),victimFrom=vec(b),hold=vec(b);double drift=b.readDouble();Vec3d land=vec(b);float damage=b.readFloat();boolean cut=b.readBoolean();
            if(!Float.isFinite(yaw)||!Double.isFinite(drift)||now<start||now-start>BEAT_END+200)return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                if(world!=mc.world){clear();world=mc.world;}
                if(type==StarPlatinumBeatdown.START)start(mc,new Beat(user,victim,victimId,start,now-start,yaw,from,victimFrom,hold,drift,land,damage));
                else{var beat=BEATS.get(user);if(beat!=null&&Float.isNaN(beat.ended)){beat.ended=beat.age(0);beat.cut=cut;if(cut&&beat.voice!=null)beat.voice.fade();}}
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(StandBeatdown::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(StandBeatdown::clear));
    }
    private static Vec3d vec(net.minecraft.network.PacketByteBuf b){return new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var b:BEATS.values())if(b.voice!=null&&!b.voice.isDone())mc.getSoundManager().stop(b.voice);
        BEATS.clear();world=null;
    }
    /** Its user used it (the blow is on its way): their body flashes sky blue, as every skill with a cutscene's does. */
    static void used(UUID user){dev.sangwoo.celestialfall.client.CutsceneFlash.flash(user);}
    private static void start(MinecraftClient mc,Beat b){
        var old=BEATS.put(b.user,b);
        if(old!=null&&old.voice!=null)old.voice.fade();
        // the rest of the recording, from its whole tick: loud for its user and the one struck, fading out to 40 blocks
        // for those near; none if it was heard too late to come on its beat
        if(mc.player!=null&&b.serverAge<=BEAT_SOUND+2){
            boolean in=mc.player.getUuid().equals(b.user)||mc.player.getUuid().equals(b.victimId);
            double far=in?0:b.from.distanceTo(mc.player.getPos());
            float volume=(float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/40));
            if(volume>.01f){
                b.voice=new StandAttack.Shout(CelestialFall.id("star_platinum_beatdown"),volume);
                int delay=(int)(BEAT_SOUND-b.serverAge);
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
        // a cutscene long over is let go (once the one struck is shown where they really are)
        BEATS.values().removeIf(b->b.age(0)>b.over()+2.5f);
    }

    // ───────────────────────────── who is in one ─────────────────────────────

    /** Every cutscene this client knows of (StandBeatdownFx draws those near). */
    static java.util.Collection<Beat> all(){return BEATS.values();}
    /** This player's cutscene (as its user), while it shows. */
    public static Beat of(UUID user){var b=BEATS.get(user);return b!=null&&b.age(0)<=b.over()+2.5f?b:null;}
    /** The cutscene that has this one (its user or the one struck), while it runs (not once it is over). */
    public static Beat running(Entity e){
        if(e==null)return null;
        for(var b:BEATS.values()){float t=b.age(0);if(t>=CUT&&t<b.over()&&(b.user.equals(e.getUuid())||b.victim==e.getId()&&b.victimId.equals(e.getUuid())))return b;}
        return null;
    }
    /** Whether this one is held where they are by a cutscene now (its user or the one struck): they neither move nor act (KagenoManager.frozen on this client). */
    public static boolean holds(Entity e){return MapClient.enabled()&&running(e)!=null;}
    /** The cutscene the one struck is in, while it shows them (into the moments after it, until they are shown where they really are). */
    static Beat struck(Entity e){
        for(var b:BEATS.values())if(b.victim==e.getId()&&b.victimId.equals(e.getUuid())){float t=b.age(0);if(t>=CUT-.05f&&t<=b.over()+2.5f)return b;}
        return null;
    }
    /**
     * Whether this client's player holds back their cry of pain at a blow of a cutscene on them. Others hear the one
     * struck cry out only at its uppercut, its last blow and the fall (StarPlatinumBeatdown.blow: the rush and the big
     * ORAs land quietly, the recording carries them), but a player's own cry is played by their own client as each blow's
     * damage comes (RushHurtSoundMixin): so here too, the first blow cries (the uppercut: nothing harms them before it),
     * then none until halfway from the last big ORA to the last blow, when the last blow and the fall do. The damage comes
     * at the server's age of the blow on this clock, give or take a tick, however late it comes (as the cutscene's word did).
     */
    public static boolean quietHurt(net.minecraft.entity.LivingEntity e){
        var mc=MinecraftClient.getInstance();if(mc.player==null||e!=mc.player)return false;
        for(var b:BEATS.values()){
            if(b.victim!=e.getId()||!b.victimId.equals(e.getUuid()))continue;
            float t=b.age(0);if(t<CUT||t>b.over()+.5f)continue;
            if(!b.cried){b.cried=true;return false;}
            return t<(BIG[BIG.length-1]+FINAL)*.5f;
        }
        return false;
    }
    /** The cutscene the local player is in (its user, or the one struck), while its camera has them. */
    public static Beat cinematic(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!mc.player.isAlive()||!MapClient.enabled()||!CelestialFall.CONFIG.cinematicCamera)return null;
        for(var b:BEATS.values()){
            if(!b.user.equals(mc.player.getUuid())&&!(b.victimId.equals(mc.player.getUuid())))continue;
            float t=b.age(0);if(t>=CUT&&t<b.over()+HANDBACK)return b;
        }
        return null;
    }

    // ───────────────────────────── the one struck ─────────────────────────────

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** A jolt that comes in over two frames at {@code at} and dies away (time constant {@code tau}). */
    static float kick(float t,float at,float tau){return FxLib.kick(t,at,tau);}
    /** The rush's blows (from 0) that have landed by {@code t}. */
    static int blows(float t){return t<RUSH_FROM?0:Math.min(BEAT_RUSH_HITS,(int)Math.floor((t-RUSH_FROM)/RUSH_EVERY+1e-4)+1);}
    /** The time of the rush's blow {@code k} (from 0). */
    static float blowAt(int k){return RUSH_FROM+k*RUSH_EVERY;}

    /**
     * Where the one struck is shown at {@code t} (the beatdown's clock), before it is over: their feet and how their
     * body turns, worked out along its beats. Knocked back from where they stood to where they are held, swaying dazed;
     * yanked up by the collar; thrown up by the uppercut and held up in the air by the rush, every blow jolting them and
     * the rush driving them slowly back; each big ORA knocking them further back; then sent flying in an arc, turning
     * over once, to come down on their back short of where they end, slide on there and get up.
     */
    public static Body victim(Beat b,float t){
        // held still through the blow's hit-stop, turned round to face its user as it lands
        float face=smooth((t-CUT)/FACE);
        if(t<CUT+STOP-.02f)return new Body(b.victimFrom,0,0,0,MIDDLE,face);
        if(t>=FINAL)return flight(b,t);
        double fw=0,up=0,rt=0;float lean=0,roll=0,spin=0;
        // knocked back from where they stood to where they are held, leaning back with it
        float knock=Ease.out(t,CUT+STOP,KNOCKED);
        lean+=26*kick(t,CUT+STOP,.32f);
        // dazed, swaying (from the knock until the grab)
        float daze=smooth((t-.6f)/.6f)*(1-smooth((t-(GRAB-.06f))/.08f));
        lean+=daze*(6+4*(float)Math.sin(t*Math.PI*2/1.3));roll+=daze*5*(float)Math.sin(t*Math.PI*2/1.7+1);rt+=daze*.05*Math.sin(t*Math.PI*2/1.7);
        // yanked in and up by the collar, bent forward over the fist that has them; held there dangling
        float yank=Ease.out(t,GRAB-.03f,GRAB+.1f),held=yank*(1-smooth((t-UPPER)/.06f));
        fw-=.32*yank;up+=.38*yank-.08*smooth((t-(UPPER-.14f))/.1f)*yank;
        lean-=14*held;lean+=held*3*(float)Math.sin((t-GRAB)*9);roll+=held*4*(float)Math.sin((t-GRAB)*7+.5);
        // the uppercut: up into the air, arched right back, settling a little
        float upper=Ease.out(t,UPPER,UPPER+.35f);
        up+=(BEAT_LIFT-.3)*upper;fw+=.42*upper;
        lean+=38*kick(t,UPPER,.18f)+16*smooth((t-UPPER)/.2f);
        // held up in the air by the rush: bobbing, driven slowly back, jolted by every blow (each its own way)
        float rush=smooth((t-RUSH_FROM)/.1f);
        up+=rush*.06*Math.sin((t-RUSH_FROM)*Math.PI*2/.9);
        fw+=b.drift*.5*Ease.unit(t,RUSH_FROM,RUSH_TO+.1f);
        int n=blows(t);
        for(int k=Math.max(0,n-4);k<n;k++){
            float at=blowAt(k),j=kick(t,at,.05f);if(j<=0)continue;
            int s=b.seed+k*7919;
            fw+=.07*j;lean+=(float)(5+6*Ease.hash(s))*j;roll+=(float)((Ease.hash(s+1)-.5)*18)*j;spin+=(float)((Ease.hash(s+2)-.5)*14)*j;up+=(Ease.hash(s+3)-.5)*.08*j;
        }
        lean+=rush*(1-smooth((t-RUSH_TO)/.3f))*2*(float)Math.sin(t*61);
        // the big ORAs: each knocks them back on, jolting the body round; between the third and the fourth they sag
        double step=b.drift*.5/BIG.length;
        for(int i=0;i<BIG.length;i++){
            float at=BIG[i];
            fw+=step*Ease.out(t,at,at+.12f);
            float j=kick(t,at,.22f);
            lean+=22*j;roll+=(i%2==0?14:-14)*j;spin+=(i%2==0?-9:9)*j;
            up-=.05*Ease.out(t,at,at+.2f);
        }
        float sag=smooth((t-(BIG[2]+.15f))/.35f)*(1-smooth((t-(BIG[3]-.08f))/.08f));
        up-=.15*sag;lean+=10*sag;
        // the last blow coming: a breath held, still
        Vec3d o=b.victimFrom.lerp(b.hold,knock);
        return new Body(b.at(o,fw,up,rt),lean,roll,spin,MIDDLE,face);
    }
    /** Where the last blow sends them from: where they are shown just before it lands. */
    static Body launch(Beat b){return victim(b,FINAL-1e-4f);}
    /**
     * Sent flying by the last blow: from where they were along an arc (quick at first, slowing, rising first) to where
     * they come down (SLIDE short of where they end), turning over backwards once and a quarter to land on their back;
     * a bounce, a slide on to where they end, lying a moment, then up.
     */
    static Body flight(Beat b,float t){
        Body from=launch(b);
        Vec3d down=b.land.subtract(b.f.multiply(SLIDE));
        // the blow holds them a moment (its hit-stop), then they fly
        float u=Ease.unit(t,FINAL+.06f,LAND),h=1-(float)Math.pow(1-u,1.6);
        double dist=Math.hypot(down.x-from.feet().x,down.z-from.feet().z);
        double apex=1.4+.08*dist;
        double x=from.feet().x+(down.x-from.feet().x)*h,z=from.feet().z+(down.z-from.feet().z)*h;
        double y=from.feet().y+(down.y-from.feet().y)*u+apex*4*u*(1-u);
        float turn=(float)(1-Math.pow(1-u,2));
        float lean=from.lean()+(450-from.lean())*turn;
        float roll=from.roll()*(1-u),spin=from.spin()*(1-u)+30*(float)Math.sin(u*Math.PI)*((b.seed&1)==0?1:-1);
        // down: a bounce and a slide on to where they end
        float bounce=t<LAND?0:(float)Math.max(0,Math.sin(Math.PI*Ease.unit(t,LAND+.02f,LAND+.3f)))*(1-Ease.unit(t,LAND+.02f,LAND+.3f)*.3f);
        y+=.35*bounce;
        float slide=Ease.out(t,LAND,LAND+.4f);
        x+=b.f.x*SLIDE*slide;z+=b.f.z*SLIDE*slide;
        // up again: from lying on the back (450 degrees: once over and a quarter) back to standing (360)
        float rise=smooth((t-GETUP)/(STOOD-GETUP));
        if(t>=LAND)lean=450-90*rise;
        // lowered as the body lies (about its middle, its lowest point kept near the ground), from as it comes down
        float theta=(float)Math.toRadians(MathHelper.clamp(lean-360,0,90)),ground=smooth((t-(LAND-.15f))/.15f);
        y-=ground*(MIDDLE*(1-Math.cos(theta))-.25*Math.sin(theta));
        return new Body(new Vec3d(x,y,z),lean,roll,spin,MIDDLE,1);
    }
    /** Whether the one struck lies dead where they came down (the fall killed them: the server deals what is left of its damage as they land). */
    static boolean killed(Entity e,float t){return t>=LAND-.05f&&e instanceof net.minecraft.entity.LivingEntity l&&l.isDead();}
    /**
     * Where the one struck is shown this frame, if a cutscene has them ({@code null} if none does): during it, where it
     * puts them; once it is over, where it left them (where they end, standing) until the server has them there too, so
     * that the move to where they really are shows nothing; cut short, from where it had them back to where they are.
     * Killed by the fall, they lie where they came down and do not get up.
     */
    public static Body shown(Entity e,float delta){
        var b=struck(e);if(b==null)return null;
        float t=b.age(delta),over=b.over();
        if(killed(e,t))return flight(b,Math.min(t,GETUP));
        if(t<over)return victim(b,t);
        Vec3d real=e.getLerpedPos(delta);
        if(b.cut){
            // cut short: back to where they really are over a quarter of a second
            float k=smooth((t-over)/.25f);if(k>=1)return null;
            var at=victim(b,over);
            return new Body(at.feet().lerp(real,k),at.lean()*(1-k),at.roll()*(1-k),at.spin()*(1-k),at.pivot(),1-k);
        }
        // over: standing where they end until they really are there (the server puts them there as it ends)
        if(real.squaredDistanceTo(b.land)<.04||t>over+2)return null;
        return new Body(b.land,0,0,0,MIDDLE,1);
    }
    /** The way the one struck faces in a cutscene: at its user. */
    public static float facing(Beat b){return b.yaw+180;}
    /** How far (blocks, world) the one struck is shown from where they are this frame (null if no cutscene has them). */
    public static Vec3d offset(Entity e,float delta){
        var body=shown(e,delta);if(body==null)return null;
        return body.feet().subtract(e.getLerpedPos(delta));
    }
    /**
     * Turns the one struck's body as the cutscene shows it (after the renderer's own turn to where it faces, before it
     * is drawn): to face its user, then leaning back, rolling and spinning about its middle.
     */
    public static void turn(Entity e,float delta,float bodyYaw,net.minecraft.client.util.math.MatrixStack m){
        var b=struck(e);if(b==null)return;
        var body=shown(e,delta);if(body==null)return;
        // dead, they lie as the cutscene laid them: not tipped over on their side as well (the renderer's turn as one dies, undone)
        if(e instanceof net.minecraft.entity.LivingEntity l&&l.deathTime>0){
            float k=Math.min(1,MathHelper.sqrt((l.deathTime+delta-1)/20f*1.6f));
            m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-k*90));
        }
        float face=MathHelper.wrapDegrees(bodyYaw-facing(b))*body.hold();
        if(face!=0)m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(face));
        if(body.lean()==0&&body.roll()==0&&body.spin()==0)return;
        m.translate(0,body.pivot(),0);
        // (after the renderer's turn, local +z is behind the body: a positive x turn tips the head back)
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(body.spin()));
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(body.lean()));
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(body.roll()));
        m.translate(0,-body.pivot(),0);
    }

    // ───────────────────────────── the stand ─────────────────────────────

    /**
     * Whether the stand of this cutscene's user is out at {@code t} (going back in once its blow has held, out again at
     * "Star Platinum!"), and since when (seconds on its clock); null outside the cutscene (the server's word then).
     */
    public static float[] standOut(Beat b,float t){
        float over=b.over();
        if(t<RETREAT||over<=RETREAT)return null;
        if(t<over)return t<REVEAL?new float[]{0,RETREAT}:new float[]{1,REVEAL};
        // cut short while it was in its user: it comes back out then
        if(over<REVEAL&&t<over+StandTimeline.SETTLED+.1f)return new float[]{1,over};
        return null;
    }
    /** The keys of the stand's stances from the grab to its last blow: {time, stance, ease (0 smooth, 1 hold, 2 strike)}. */
    static final float[][] KEYS={
        {ENGAGE,GUARD,0},{3.74f,COLLAR,0},{GRAB,COLLAR,1},{UPPER-.12f,COLLAR,1},{UPPER,UPPERCUT,2},{UPPER+.1f,UPPERCUT,1},
        // (the rush between, RUSH_FROM to RUSH_TO: StandRush.barrage)
        // (each wind-up and each strike given time enough for its arm's turn: no bone 40 degrees in a frame at 60 fps)
        {RUSH_TO+.05f,GUARD,0},{BIG[0]-.13f,WIND_R,0},{BIG[0]-.1f,WIND_R,1},{BIG[0],PUNCH_R,2},{BIG[0]+.02f,PUNCH_R,1},
        {BIG[1]-.11f,WIND_HOOK,0},{BIG[1]-.1f,WIND_HOOK,1},{BIG[1],HOOK,2},{BIG[1]+.06f,HOOK,1},
        {BIG[2]-.18f,WIND_L,0},{BIG[2]-.1f,WIND_L,1},{BIG[2],PUNCH_L,2},{BIG[2]+.1f,PUNCH_L,1},
        {BIG[2]+.4f,GUARD,0},{BIG[3]-.22f,WIND_BIG,0},{BIG[3]-.12f,WIND_BIG,1},{BIG[3],PUNCH_BIG,2},{BIG[3]+.02f,PUNCH_BIG,1},
        {BIG[4]-.11f,WIND_R,0},{BIG[4]-.1f,WIND_R,1},{BIG[4],PUNCH_R,2},{BIG[4]+.03f,PUNCH_R,1},
        {FINAL-.3f,WIND_HEAVY,0},{FINAL-.16f,WIND_HEAVY,1},{FINAL,PUNCH_HEAVY,2},{FINAL+.75f,PUNCH_HEAVY,1},{LEAVE,GUARD,0}};
    /** When each of its strikes lands, the stance it strikes in, and the fist (true: the right). */
    static final float[] STRIKES={UPPER,BIG[0],BIG[1],BIG[2],BIG[3],BIG[4],FINAL};
    static final int[] STRIKE_STANCE={UPPERCUT,PUNCH_R,HOOK,PUNCH_L,PUNCH_BIG,PUNCH_R,PUNCH_HEAVY};
    static final boolean[] STRIKE_RIGHT={true,true,false,false,true,true,true};
    /** The rush's moments: its fists out from RUSH_FROM (the first lands then), until RUSH_TO; the arms bigger as in the rush. */
    static final float BARRAGE_FROM=RUSH_FROM-StandRush.HALF;
    static float ease(int ease,float x){x=x<0?0:x>1?1:x;return ease==2?(float)Math.pow(x,1.6):smooth(x);}
    /** Adds the stand's stances at {@code t} by the keys into {@code w} (its share {@code share}). */
    static void keyed(float t,float[] w,float share){
        if(t<=KEYS[0][0]){w[(int)KEYS[0][1]]+=share;return;}
        for(int i=1;i<KEYS.length;i++){
            if(t>KEYS[i][0])continue;
            float s=ease((int)KEYS[i][2],(t-KEYS[i-1][0])/(KEYS[i][0]-KEYS[i-1][0]));
            w[(int)KEYS[i-1][1]]+=share*(1-s);w[(int)KEYS[i][1]]+=share*s;return;
        }
        w[(int)KEYS[KEYS.length-1][1]]+=share;
    }
    /**
     * How far the stand is into the rush's barrage at {@code t} (0 to 1): in from just after the uppercut lands (its
     * right fist up high comes down to the guard as the barrage's first fist, the left, goes out, BARRAGE_IN), out once
     * its last fist has landed (its fists back to the guard over BARRAGE_OUT).
     */
    static float barrage(float t){return smooth((t-(UPPER+.03f))/BARRAGE_IN)*(1-smooth((t-RUSH_TO-.02f)/BARRAGE_OUT));}
    static final float BARRAGE_IN=.15f,BARRAGE_OUT=.12f;
    /** How big its arms are at {@code t} through the rush (1 their size), as the rush's, and how far each is out. */
    static float armSize(float t){return 1+(StandRush.ARMS-1)*smooth((t-UPPER)/.2f)*(1-smooth((t-RUSH_TO)/.2f));}

    /**
     * The stand's shape through this user's cutscene this frame, {@code back} seconds before it (null outside the part
     * it leads: the grab to the end; before the grab the punch it threw and its going back in and coming out show):
     * its stances, how far it has come round to the front, the way it faces, where it stands (just in front of the one
     * struck, rising with them into the air), how far it dashes into each blow, its arms' size, the gust and its
     * afterimages.
     */
    public static StandPunch.Shape shape(UUID user,float delta,float back,StandPunch.Shape into){var b=of(user);return b==null?null:shape(b,delta,back,into);}
    static StandPunch.Shape shape(Beat b,float delta,float back,StandPunch.Shape into){
        float t=b.age(delta)-back,over=b.over();
        if(t<ENGAGE||t>over+RETURN+.1f)return null;
        into.clear();
        float into_=smooth((t-ENGAGE)/(ENGAGED-ENGAGE)),leave=smooth((t-LEAVE)/RETURN),cut=Float.isNaN(b.ended)||!b.cut?0:smooth((t-b.ended)/.3f);
        into.engage=StandPunch.burst((t-ENGAGE)/(ENGAGED-ENGAGE))*(1-leave)*(1-cut);
        if(into.engage<=0&&t>ENGAGED)return null;
        float bar=barrage(t);
        keyed(t,into.weights,1-bar);
        if(bar>0)StandRush.barrage(t-BARRAGE_FROM,b.seed,into.weights,bar);
        // where it stands: just in front of the one struck, on the blow's line, its fists reaching them (the rush's
        // bigger arms further out), rising with them into the air (its feet RISE_BELOW under theirs)
        Body v=victim(b,Math.min(t,FINAL-1e-4f));
        double ahead=v.feet().subtract(b.from).dotProduct(b.f);
        double gap=GAP_GRAB+(GAP_RUSH-GAP_GRAB)*smooth((t-(UPPER+.1f))/.25f)+(GAP_BIG-GAP_RUSH)*smooth((t-RUSH_TO)/.25f)+(GAP_FINAL-GAP_BIG)*smooth((t-(FINAL-.45f))/.2f);
        double lifted=Math.max(0,v.feet().y-b.from.y-RISE_BELOW-StandPresence.UP);
        into.yaw=b.yaw;into.side=0;
        into.ahead=(float)(ahead-gap);
        into.raise=(float)(.05+(lifted-.05)*smooth((t-UPPER)/.25f));
        // it dashes into each blow and is shoved on through it, as a punch's
        float lunge=0,gust=0,ghost=0;
        for(int i=0;i<STRIKES.length;i++){
            float at=STRIKES[i];
            lunge+=(float)(i==STRIKES.length-1?.55:.28)*kick(t,at,i==STRIKES.length-1?.4f:.16f);
            gust+=(i==STRIKES.length-1?2.2f:1.4f)*kick(t,at,.25f);
            ghost=Math.max(ghost,smooth((t-(at-.1f))/.03f)*(1-smooth((t-at-.12f)/.1f)));
        }
        gust+=bar*(.95f+.25f*StandRush.drive(t-BARRAGE_FROM));
        into.lunge=lunge;into.gust=gust;into.ghost=ghost;
        // its arms bigger through the rush, the one out bigger still, as the rush's
        float size=armSize(t);
        float left=0,right=0;for(int i:StandRush.LEFT)left+=into.weights[i];for(int i:StandRush.RIGHT)right+=into.weights[i];
        into.armL=size*(1+StandRush.STRETCH*left);into.armR=size*(1+StandRush.STRETCH*right);
        return into;
    }
    /** How far in front of the one struck the stand stands (blocks between its feet and theirs along the blow): grabbing, rushing (its arms bigger), throwing a big ORA, throwing its last. */
    static final double GAP_GRAB=1.38,GAP_RUSH=1.55,GAP_BIG=1.35,GAP_FINAL=1.5;
    /** How far under the one struck the stand floats once they are up in the air (blocks, its feet below theirs). */
    static final double RISE_BELOW=.55;
    /** The rush's phantom arms in the cutscene: how strongly afterimage {@code i} shows (0 none) and its stances into {@code into}, as the rush's. */
    public static float phantom(UUID user,int i,float delta,StarPlatinumLook into){var b=of(user);return b==null?0:phantom(b,i,delta,into);}
    static float phantom(Beat b,int i,float delta,StarPlatinumLook into){
        float t=b.age(delta),bar=barrage(t)*smooth((t-RUSH_FROM)/.08f);if(bar<=0)return 0;
        var w=into.weights;java.util.Arrays.fill(w,0);
        StandRush.barrage(t-BARRAGE_FROM-StandRush.phantomLag(i),b.seed+101*(i+1),w,1);
        float size=armSize(t),left=StandRush.out(w,StandRush.LEFT),right=StandRush.out(w,StandRush.RIGHT);
        into.armL=size*(1+StandRush.STRETCH*left);into.armR=size*(1+StandRush.STRETCH*right);
        into.showL=StandRush.phantomShow(left);into.showR=StandRush.phantomShow(right);
        return bar*StandRush.phantomStrength(i);
    }

    // ───────────────────────────── the camera ─────────────────────────────

    /** The shots' cuts, on the beats (StandBeatdownCamera lays each one out). */
    static final float[] CUTS={CUT,1.12f,2.6f,ENGAGE,UPPER,4.95f,6.2f,7.3f,8.4f,BIG[3]-.08f,FINAL-.3f,FINAL+.28f};

    /**
     * The camera of a cutscene's user and of the one struck this frame: shots cut on the beats of the recording
     * (StandBeatdownCamera), coming back to the gameplay camera ({@code vanilla}) over HANDBACK once it is over. Every
     * shot is kept out of the walls (its eye pulled in toward what it looks at).
     */
    public static CinematicDirector.Pose camera(Vec3d vanilla,float yaw,float pitch,float delta){
        var b=cinematic();if(b==null)return null;
        float t=b.age(delta);
        var shot=StandBeatdownCamera.at(b,Math.min(t,b.over()));
        var mc=MinecraftClient.getInstance();
        Vec3d eye=clear(mc,shot.look(),shot.eye());
        Vec3d d=shot.look().subtract(eye);if(d.lengthSquared()<1e-6)d=b.f;d=d.normalize();
        float sy=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),sp=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(d.y,-1,1)));
        float k=handback(b,t);
        if(k<=0)return new CinematicDirector.Pose(eye,sy,sp,shot.roll());
        return new CinematicDirector.Pose(eye.lerp(vanilla,k),sy+MathHelper.wrapDegrees(yaw-sy)*k,sp+(pitch-sp)*k,shot.roll()*(1-k));
    }
    /** How far the camera has come back to its user (0 the cutscene's, 1 the gameplay camera). */
    static float handback(Beat b,float t){return smooth((t-b.over())/HANDBACK);}
    public static double fov(double vanilla,float delta){
        var b=cinematic();if(b==null)return vanilla;
        float t=b.age(delta);double fov=StandBeatdownCamera.at(b,Math.min(t,b.over())).fov();
        return fov+(vanilla-fov)*handback(b,t);
    }
    public static float roll(float delta){
        var b=cinematic();if(b==null)return 0;
        float t=b.age(delta);return StandBeatdownCamera.at(b,Math.min(t,b.over())).roll()*(1-handback(b,t));
    }
    /** Keeps an eye out of the walls: pulled in along the way from what it looks at to where a block stands in it. */
    static Vec3d clear(MinecraftClient mc,Vec3d look,Vec3d eye){
        if(mc.world==null||mc.player==null)return eye;
        var hit=mc.world.raycast(new RaycastContext(look,eye,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        if(hit.getType()==HitResult.Type.MISS)return eye;
        Vec3d d=eye.subtract(look);double len=d.length();if(len<1e-4)return eye;
        return look.add(d.multiply(Math.max(.3,look.distanceTo(hit.getPos())-.35)/len));
    }
    /** How high the black bars are this frame (share of the screen's height each): in from the top and the bottom as its blow lands, out as it ends. */
    public static float bars(float delta){
        var b=cinematic();if(b==null)return 0;
        return bars(b,b.age(delta));
    }
    /** The bars at {@code t}: sliding in (quick, a touch past, settling) from the blow, sliding out at the end. */
    static float bars(Beat b,float t){
        float in=Ease.unit(t,CUT,CUT+BARS_IN);
        // in: eased out with a slight overshoot that settles (back easing), never under nothing
        float c=1.7f,u=in-1,ein=in>=1?1:1+u*u*((c+1)*u+c);
        float out=smooth((t-(b.over()-BARS_OUT+.05f))/BARS_OUT);
        return Math.max(0,BAR*ein*(1-out));
    }

    /** For the capture tools: this player's cutscene (its age, whether it ended, whether it was cut short, the bars), or null. */
    public static String describe(PlayerEntity p,float delta){
        var b=of(p.getUuid());if(b==null)return null;
        float t=b.age(delta);
        return String.format(java.util.Locale.ROOT,"%.3f,%d,%d,%.3f",t,Float.isNaN(b.ended)?0:1,b.cut?1:0,bars(b,t));
    }
}
