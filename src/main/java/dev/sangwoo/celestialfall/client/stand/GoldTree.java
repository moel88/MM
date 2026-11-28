package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import dev.sangwoo.celestialfall.stand.GoldExperienceTree;
import dev.sangwoo.celestialfall.stand.GoldTreeEntity;
import dev.sangwoo.celestialfall.stand.StandManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;

/**
 * Gold Experience's H, the tree of life, as it is drawn, and its key (2.152.0: GoldExperienceRules has the user's words).
 * The server runs it (GoldExperienceTree); here is what the stand does through it, on its own clock: it comes round to its
 * user's front right, turned on them, and goes down on its knee and lays its open palm on the ground before their feet
 * (the user's "스탠드가 땅을 만지고 나무가 자라나게"); held there, it stays kneeling where it touched the ground as the tree
 * bursts up under its user and flings them up, then gets up and goes back to them in the air. The tree itself is the
 * server's entity (GoldTreeEntity, drawn by GoldTreeRenderer, grown by GoldTreeModel); its light is GoldTreeFx's.
 * H is the key (Gold Experience's user's on the map). Every moment is a function of the clocks.
 * <p>
 * Its sounds (2.153.0, laid together from recordings: tools/stand/make_gold_sounds.py; GoldSounds) come on its moments:
 * the palm laid on the ground and the light swelling to the burst, the burst (the ground, the wood tearing as it grows,
 * twigs, leaves, the rush of its user flung up), a thud on wood with every blow it turns back, and its withering.
 */
public final class GoldTree {
    /** The stand's moments (seconds on its clock from the press): its palm on the ground (the server's TREE_TOUCH), the tree bursting up (TREE_BURST). */
    public static final float TOUCH=GoldExperienceRules.TREE_TOUCH/20f,BURST=GoldExperienceRules.TREE_BURST/20f;
    /** It comes round to its place (seconds), comes into its stances, gets up after the burst and takes this long, goes back to its user; stopped short, it gets up this fast. */
    static final float COME=.3f,TAKE=.1f,RISE_FROM=BURST+.22f,RISE=.6f,LEAVE=.15f,RETURN=.5f,CUT=.3f;
    /** Where it kneels: degrees round to the right of the way its user faced, how far from their feet (blocks), turned on them. */
    static final float SIDE=62f,AHEAD=1.45f;
    /**
     * The sounds' moments (seconds from the press): the touch's file comes in as the palm goes down, its pat at .25 s on
     * the palm laid on the ground (TOUCH) and its light swelling to the burst; the burst; the withering (the server's
     * TREE_WITHER: early, it comes when it is told).
     */
    static final float TOUCH_SOUND=.1f,WITHER=GoldExperienceRules.TREE_WITHER/20f;
    static final GoldSounds.Cue[] SOUNDS={new GoldSounds.Cue(TOUCH_SOUND,"gold_tree_touch",1),new GoldSounds.Cue(BURST,"gold_tree_burst",1),
        new GoldSounds.Cue(WITHER,"gold_tree_wither",1)};

    /** An H as heard. */
    public static final class Tree {
        public final long start;final long serverAge,received;public final float yaw;public Vec3d spot;
        /** Its clock when the tree burst up, when it was stopped short, when the tree began to wither early (NaN: not); the tree's entity. */
        float burst=Float.NaN,stopped=Float.NaN,withered=Float.NaN;int tree=-1;
        /** Whom the burst struck (and whose guard took it), and the blows the tree turned back. */
        final List<Hit> hits=new ArrayList<>();final List<Turned> turned=new ArrayList<>();
        long ready;
        /** Its sounds (2.153.0). */
        final GoldSounds.Run sound=new GoldSounds.Run(SOUNDS);
        Tree(long start,long serverAge,long received,float yaw,Vec3d spot){this.start=start;this.serverAge=serverAge;this.received=received;this.yaw=yaw;this.spot=spot;}
        /** Seconds on its clock this frame. */
        public float age(float delta){return (serverAge+StandRush.ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        public boolean burst(){return !Float.isNaN(burst);}
    }
    /** One the burst struck, and whether a guard took it. */
    record Hit(int entity,boolean guarded){}
    /** A blow the tree turned back: its clock then (seconds), the tree, who struck it, how much went back to them, whether it landed. */
    record Turned(float at,int tree,int striker,float amount,boolean landed){}
    private static final Map<UUID,Tree> TREES=new HashMap<>();
    /** The client tick each tree last turned a blow back (for its jolt). */
    private static final Map<Integer,Long> JOLTS=new HashMap<>();
    private static Object world;
    private static boolean heldH;
    private GoldTree(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(GoldExperienceTree.EVENT,(mc,handler,b,response)->{
            int type=b.readUnsignedByte();UUID maker=b.readUuid();Identifier dimension=b.readIdentifier();
            long start=b.readLong(),serverNow=b.readLong();float yaw=b.readFloat();
            Vec3d at=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());long ready=b.readLong();int tree=b.readInt();
            int n=b.readVarInt();List<Hit> hits=new ArrayList<>();
            for(int i=0;i<n&&i<256;i++)hits.add(new Hit(b.readInt(),b.readBoolean()));
            int whom=b.readInt();float amount=b.readFloat();boolean flag=b.readBoolean();
            if(!Float.isFinite(yaw)||!Double.isFinite(at.x+at.y+at.z))return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){stopSounds(mc);TREES.clear();JOLTS.clear();world=mc.world;}
                event(mc,maker,type,start,serverNow,yaw,at,ready,tree,hits,whom,amount,flag);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->{stopSounds(mc);TREES.clear();JOLTS.clear();world=null;heldH=false;}));
        ClientTickEvents.END_CLIENT_TICK.register(GoldTree::tick);
        // its maker's crosshair passes through their own tree (they cannot strike it to any end)
        GoldTreeEntity.clientHits=t->{var p=MinecraftClient.getInstance().player;return p==null||!t.ownedBy(p);};
    }
    private static void event(MinecraftClient mc,UUID maker,int type,long start,long serverNow,float yaw,Vec3d at,long ready,int tree,List<Hit> hits,int whom,float amount,boolean flag){
        var t=TREES.get(maker);
        switch(type){
            case GoldExperienceTree.TOUCH->{if(t!=null)t.sound.cut();t=new Tree(start,serverNow-start,StandRush.ticks,yaw,at);t.ready=ready;TREES.put(maker,t);}
            case GoldExperienceTree.BURST->{
                // (one who came near it only now hears the burst alone)
                if(t==null||t.start!=start){if(t!=null)t.sound.cut();t=new Tree(start,serverNow-start,StandRush.ticks,yaw,at);TREES.put(maker,t);}
                t.burst=(serverNow-start)/20f;t.tree=tree;t.spot=at;t.ready=ready;t.hits.clear();t.hits.addAll(hits);
            }
            case GoldExperienceTree.STOPPED->{
                if(t==null||t.start!=start)return;t.ready=ready;if(Float.isNaN(t.stopped))t.stopped=Math.max(0,t.age(0));
                if(!t.burst())t.sound.cut();
            }
            case GoldExperienceTree.REFLECTED->{
                JOLTS.put(tree,StandRush.ticks);
                if(t!=null&&t.start==start)t.turned.add(new Turned((serverNow-start)/20f,tree,whom,amount,flag));
                // a thud on wood, and its leaves
                boolean ours=mc.player!=null&&(mc.player.getUuid().equals(maker)||mc.player.getId()==whom);
                var e=mc.world.getEntityById(tree);
                GoldSounds.play(mc,"gold_tree_knock",GoldSounds.volume(mc,ours,e!=null?e.getPos():at),1);
            }
            case GoldExperienceTree.WITHERED->{
                if(t==null||t.start!=start)return;t.ready=ready;
                if(Float.isNaN(t.withered)){
                    t.withered=(serverNow-start)/20f;
                    // withering early: now, not on its moment (what is still sounding of the burst goes on)
                    if(t.withered<WITHER-.1f){
                        t.sound.played.set(t.sound.cues.length-1);
                        GoldSounds.play(mc,"gold_tree_wither",volume(mc,maker,t),1);
                    }
                }
            }
            default->{}
        }
    }
    /** How loud the tree's sounds are here: all of it for its maker and whom the burst struck, fading out from them and where it stands. */
    static float volume(MinecraftClient mc,UUID maker,Tree t){
        if(mc.world==null||mc.player==null)return 0;
        boolean ours=mc.player.getUuid().equals(maker);
        for(var h:t.hits)ours|=h.entity()==mc.player.getId();
        var m=mc.world.getPlayerByUuid(maker);
        return GoldSounds.volume(mc,ours,m==null?null:m.getPos(),t.spot);
    }
    private static void stopSounds(MinecraftClient mc){for(var t:TREES.values())t.sound.stop(mc);}
    private static void tick(MinecraftClient mc){
        if(mc.world==null)return;
        if(!mc.isPaused())for(var e:TREES.entrySet()){
            var t=e.getValue();float age=t.age(0);if(age>GoldExperienceRules.TREE_END/20f+2)continue;
            t.sound.tick(mc,age,volume(mc,e.getKey(),t));
        }
        JOLTS.values().removeIf(at->StandRush.ticks-at>40);
        TREES.values().removeIf(t->t.age(0)>GoldExperienceRules.TREE_END/20f+2&&t.ready<=mc.world.getTime());
    }
    private static boolean usable(MinecraftClient mc){return MapClient.enabled()&&StandManager.goldExperience(mc.player)&&GoldPresence.out(mc.player)&&CombatClient.combatInput()&&mc.player.isOnGround();}

    // ───────────────────────────── what others ask ─────────────────────────────

    /** This player's tree of life as heard (null if none). */
    public static Tree of(UUID id){return TREES.get(id);}
    static Map<UUID,Tree> all(){return TREES;}
    /** The world tick this player's H is ready again (0 if they have not used it: its cooldown runs from the tree's end). */
    public static long ready(UUID id){var t=TREES.get(id);return t==null?0:t.ready;}
    /** This player's stand is not Gold Experience: its cooldown is gone, as the server forgets it. */
    public static void forget(UUID id){var t=TREES.get(id);if(t!=null)t.ready=0;}
    /** Whether this player stands held with the stand's palm on the ground (the tree not up yet): they may do nothing. */
    public static boolean holding(UUID id){
        var t=TREES.get(id);if(t==null||!Float.isNaN(t.stopped))return false;
        float a=t.age(0);return a>=0&&a<(t.burst()?t.burst:BURST+.25f);
    }
    public static boolean localHolding(){var p=MinecraftClient.getInstance().player;return p!=null&&holding(p.getUuid());}
    /** The local player's movement is held (their tree on its way: GoldExperienceTree holds them). */
    public static boolean movementLocked(){return localHolding();}
    /** Seconds since tree entity {@code tree} last turned a blow back (NaN if it has not lately): its jolt. */
    static double jolted(int tree,float delta){var at=JOLTS.get(tree);return at==null?Double.NaN:(StandRush.ticks-at+Math.max(0,Math.min(1,delta)))/20.0;}
    /** For the checks: this player's tree as "age,burst,stopped,tree,hits,turned", or null. */
    public static String describe(UUID id,float delta){
        var t=TREES.get(id);if(t==null)return null;
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%d,%d,%d,%d",t.age(delta),t.burst()?1:0,Float.isNaN(t.stopped)?0:1,t.tree,t.hits.size(),t.turned.size());
    }

    /** H for Gold Experience's user on the map. Returns whether it took the key. */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(key!=GLFW.GLFW_KEY_H||window!=mc.getWindow().getHandle())return false;
        if(action==GLFW.GLFW_RELEASE){if(!heldH)return false;heldH=false;return true;}
        if(heldH)return true;
        if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.goldExperience(mc.player))return false;
        heldH=true;
        if(usable(mc)&&ClientPlayNetworking.canSend(GoldExperienceTree.INPUT)){
            BotandCamera.prepareInteraction();
            // the way it flings them is the way they face now: the turn goes to the server before the press
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
            ClientPlayNetworking.send(GoldExperienceTree.INPUT,PacketByteBufs.empty());
        }
        return true;
    }

    // ───────────────────────────── the stand ─────────────────────────────

    static float smooth(float v){v=v<0?0:v>1?1:v;return v*v*(3-2*v);}
    /** When the stand gets up (seconds): after the burst, or as it was stopped short. */
    static float rises(Tree t){return Float.isNaN(t.stopped)?RISE_FROM:Math.min(RISE_FROM,t.stopped);}
    static float riseTakes(Tree t){return Float.isNaN(t.stopped)||t.stopped>=RISE_FROM?RISE:CUT;}
    /** When it starts back to its user. */
    static float leaves(Tree t){return rises(t)+LEAVE;}
    /** How far down on its knee, its palm on the ground, it is {@code a} seconds in (0 at rest, 1 kneeling). */
    public static float kneel(Tree t,float a){
        float down=smooth(a/TOUCH);
        return down*(1-smooth((a-rises(t))/riseTakes(t)));
    }
    /** How far it is pinned to the ground where it touched it (1 there; 0 following its user wherever they are). */
    static float pinned(Tree t,float a){return 1-smooth((a-rises(t))/(riseTakes(t)*.8f));}
    /** Whether it shows in it {@code a} seconds in. */
    static boolean shows(Tree t,float a){return a>=0&&a<=leaves(t)+RETURN;}
    /** Adds its stances {@code a} seconds in into {@code w}: from its rest down on its knee, and back. */
    public static void stances(Tree t,float a,float[] w){float k=kneel(t,a);w[GROUND]+=k;w[IDLE]+=1-k;}
    /**
     * Tree {@code t} this frame on a look that already holds the stand's pose beside its user: down on its knee at their
     * front right, turned on them, pinned where it touched the ground until it gets up. Returns whether it shows.
     */
    static boolean pose(Tree t,float delta,GoldExperienceLook look,GoldRush.Shape shape,float[] scratch){
        float a=t.age(delta);if(!shows(t,a))return false;
        java.util.Arrays.fill(scratch,0);
        stances(t,a,scratch);
        float in=smooth(a/TAKE)*(1-smooth((a-leaves(t))/RETURN));
        for(int i=0;i<scratch.length;i++)look.weights[i]=look.weights[i]*(1-in)+scratch[i]*in;
        shape.engage=smooth(a/COME)*(1-smooth((a-leaves(t))/RETURN));
        shape.yaw=t.yaw;shape.side=SIDE;shape.ahead=AHEAD;shape.inward=1;shape.raise=(float)-StandPresence.UP;shape.lean=0;
        shape.pin(t.spot,pinned(t,a));
        return true;
    }
    /** How brightly its aura burns (0 to 1): up as its palm goes to the ground, at its height as the tree bursts up, then dying away. */
    static float glow(Tree t,float a){
        float up=smooth((a-.12f)/(BURST-.12f)),off=1-smooth((a-BURST)/.6f);
        if(!Float.isNaN(t.stopped))off=Math.min(off,1-smooth((a-t.stopped)/.2f));
        return up*off;
    }
    /** Where its palm lies on the ground (world), its body drawn as {@code body}. */
    static Vec3d palm(StandPresence.Body body){
        float[] f=GoldRush.FISTS[GROUND][1];
        double r=Math.toRadians(body.yaw()),fx=-Math.sin(r),fz=Math.cos(r),s=body.scale();
        return body.at().add((fx*f[0]-fz*f[1])*s,f[2]*s,(fz*f[0]+fx*f[1])*s);
    }
    /** For the capture tools: where its palm lies, its body drawn as {@code body}. */
    public static Vec3d palmAt(StandPresence.Body body){return palm(body);}
    static float yawTo(Vec3d from,Vec3d to){return (float)Math.toDegrees(Math.atan2(-(to.x-from.x),to.z-from.z));}
    static float lerp(float k,float a,float b){return MathHelper.lerp(k,a,b);}
}
