package dev.sangwoo.celestialfall.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.LongCooldowns;
import dev.sangwoo.celestialfall.client.gacha.GachaFont;
import dev.sangwoo.celestialfall.combat.CombatState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import java.util.UUID;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/**
 * The skill cooldown list (2.42.0, user request: the old "CID KAGENOU" panel is gone, rebuilt after the user's Roblox
 * recording with its pink bars sky blue). On the left under the HP art, the skill used last in a big box on top and the
 * others still cooling in small boxes below it ({@link CooldownList}); each box's bar runs out to the left with its
 * cooldown. Kageno's cooldowns come from the stand's status (the tick each skill is ready again; T and T held share one,
 * named after the one used), Atomic's from its sword's item cooldown. The attack (M1) shows only the rest after a combo,
 * whether its four hits ended it (from when the fourth hit is over, 2.43.1) or a late hit broke it (user request,
 * 2.43.0), and the dash (C) its cooldown from the combat state (2.43.0; the bar by the hotbar is gone). Every cooldown
 * runs once its skill is over (user request, 2.44.0): the server's ready tick is the skill's end plus its cooldown, and
 * the box comes in when the cooldown begins. The list keeps its own clock, the client's ticks while the game runs plus
 * the frame's partial tick: the world's time is set again by the server every second and can step by a tick. 2.73.0
 * (user request: "쿨타임 UI도 지금 너무 크니까 줄여주고 쿨타임이 다 흘러서 UI가 사라질때 모래시계 처럼 흘러내려서 사라지게"):
 * about seven tenths of its size, tucked under the smaller HP art, and a finished box runs out like an hourglass, its
 * box draining through a funnel in its middle while its sand pours down out of its bottom, behind the boxes below, and
 * fades (CooldownList). 2.74.2 (user request: "걍 다른 스탠드를 끼면 다른 스탠드 쿨타임은 사라진다 이게 힘든가? 서로 스탠드가
 * 다른데 왜 흐르는건데"): only the stand they have now has cooldowns in it ({@link #shows}); another stand's run out at
 * once as the stand changes (a Rokakaka, an arrow, the gacha, an operator's command), and come back no more. 2.74.3:
 * the server forgets another stand's long ones too (LongCooldowns.forgetOthers), and begins the new stand's in full;
 * and the list reads no ready tick before the server has set the world's clock ({@link #timed}).
 */
public final class CooldownHud {
    /**
     * The reference's measures, as shares of the screen's height (its left edge as a share of the width); 2.73.0 about
     * seven tenths of them (until 2.72.0: top .374, big .406 by .094, small .271 by .054, gap .0137, text .0385 and .027,
     * padding .0208 and .0139, bars .0069 and .0028).
     */
    private static final double LEFT=.036,TOP=.3,BIG_W=.29,BIG_H=.066,SMALL_W=.195,SMALL_H=.04,GAP=.0105,
        BIG_TEXT=.027,SMALL_TEXT=.0195,BIG_PAD=.015,SMALL_PAD=.01,BIG_BAR=.005,SMALL_BAR=.0022;
    /** A finished box's sand: the light along its surface, and its grains (two colours). */
    static final int RIM=0x8ca8e8ff,GRAIN=0xffa8e8ff,GRAIN_DEEP=0xff5fc6f5;
    /** The boxes' dark translucent violet (the reference's), the bars' sky blue (the gacha's R; the reference's are pink) and their glow. */
    static final int BOX=0xba0e0814,BAR=0xff74d8ff,BAR_TOP=0xffa8e8ff,GLOW=0x2fb4ff;
    /**
     * The attack rests {@link dev.sangwoo.celestialfall.stand.KagenoRules#M1_REST} ticks after a combo and waits
     * {@link dev.sangwoo.celestialfall.stand.KagenoRules#M1_STEP} between hits: this many ticks or more still to run when
     * the status arrives (late by the ping) marks a rest; the combo's own rhythm stays off the list.
     */
    private static final int COMBO_END=14;
    /** The dash's place in the list (after the stand's skill slots), then Star Platinum's punches' rest (2.54.0), its rush (2.57.0), its heavy punch (2.59.0), its Star Finger (2.60.0), its inhale (2.61.0), its beatdown (2.64.0) and its time stop (2.67.0). */
    static final int DASH=10,PUNCHES=11,BARRAGE=12,HEAVY=13,FINGER=14,INHALE=15,BEATDOWN=16,TIME_STOP=17;
    /** Lee So-sang's attack's rest after a combo (2.77.0), after Star Platinum's; their E (2.80.0). */
    static final int ISOSANG=18,ISOSANG_E=19;
    private static final CooldownList LIST=new CooldownList();
    private static final long[] SEEN=new long[9];
    private static long ticks,dashSeen,punchesSeen,barrageSeen,heavySeen,fingerSeen,inhaleSeen,beatdownSeen,timeStopSeen,atomicSeen,isosangSeen,isosangESeen;
    private static boolean atomic;
    private static UUID owner;private static Object world;
    /** The world whose clock the server has set since it was made (2.74.3, WorldTimeMixin). */
    private static Object timed;
    private static CooldownList.Metrics metrics;private static long metricsKey;
    private CooldownHud(){}

    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(CooldownHud::tick);
        HudRenderCallback.EVENT.register(CooldownHud::render);
    }
    /** The list's clock on this frame (ticks). */
    static double clock(float delta){return ticks+delta;}
    public static void clear(){LIST.clear();java.util.Arrays.fill(SEEN,0);dashSeen=punchesSeen=barrageSeen=heavySeen=fingerSeen=inhaleSeen=beatdownSeen=timeStopSeen=atomicSeen=isosangSeen=isosangESeen=0;atomic=false;owner=null;world=null;}
    /**
     * The server has just set {@code world}'s clock (its time packet, WorldTimeMixin). Until it first does, a world just
     * joined counts from 0, and a ready tick read against it (the long cooldowns are told as the player joins: Fabric
     * runs the join as the game starts the connection, before it sends the world's time) would put its box far ahead
     * (2.74.3: in the multiplayer check, joining again with Atomic's ten minutes running, the box was some 80 s away).
     */
    public static void timed(Object world){timed=world;}
    /** The names the list shows now, top first (for the QA tools). */
    public static java.util.List<String> shown(){return LIST.entries().stream().filter(e->!e.leaving()).map(e->e.name).toList();}

    private static void tick(MinecraftClient mc){
        var p=mc.player;
        if(mc.world==null||p==null||!MapClient.enabled()){if(owner!=null)clear();return;}
        if(!p.getUuid().equals(owner)||mc.world!=world){clear();owner=p.getUuid();world=mc.world;}
        // (2.74.3) nothing told is read before the server has set this world's clock: the ready ticks are its ticks
        if(timed!=mc.world)return;
        if(mc.isPaused())return;
        ticks++;double now=ticks;long time=mc.world.getTime();
        // the stand they have (2.74.2): the server's word of it (ProgressClient), which stays as they die and come back to
        // life; another stand's cooldowns count as over (their boxes drain away), and Star Platinum's short ones are
        // forgotten here as the server forgets them (StandManager), so none comes back with the stand
        String stand=ProgressClient.profile.stand();boolean kageno=shows(G,stand),star=shows(PUNCHES,stand);
        if(!star){
            dev.sangwoo.celestialfall.client.stand.StandAttack.forget(p.getUuid());dev.sangwoo.celestialfall.client.stand.StandRush.forget(p.getUuid());
            dev.sangwoo.celestialfall.client.stand.StandInhale.forget(p.getUuid());
        }
        var status=StandClient.STATES.get(p.getUuid());
        if(status!=null){
            long[] ready=status.ready();
            for(int slot=0;slot<Math.min(ready.length,SEEN.length);slot++){
                long r=kageno?ready[slot]:0,was=SEEN[slot];SEEN[slot]=r;
                if(slot==G||r==was)continue;
                long left=r-time;
                // the attack's steps between hits stay off the list: only its rest after a combo shows
                if(slot==M1&&left>0&&left<COMBO_END)continue;
                show(slot,name(slot,p.getUuid()),left,cooling(slot),now);
            }
        }
        // Atomic: from the server's ledger once it has told it (2.73.0, LongCooldowns: ten minutes, begun again in full on
        // coming back, and since 2.74.0 on coming back to life or being given a stand); else the sword's cooldown share at
        // this tick and one tick on gives its length
        boolean ledger=LongCooldownsClient.known();
        if(ledger){
            long atomicReady=kageno?LongCooldownsClient.ready(LongCooldowns.Skill.ATOMIC):0;
            if(atomicReady!=atomicSeen){show(G,NAMES[G],atomicReady-time,LongCooldownsClient.length(LongCooldowns.Skill.ATOMIC),now);atomicSeen=atomicReady;}
        }else{
            var cooldowns=p.getItemCooldownManager();
            float p0=cooldowns.getCooldownProgress(CelestialFall.ATOMIC_SWORD,0),p1=cooldowns.getCooldownProgress(CelestialFall.ATOMIC_SWORD,1);
            boolean running=kageno&&p0>0;
            if(running&&!atomic&&p0>p1){double total=1/(double)(p0-p1),left=p0*total;LIST.add(G,NAMES[G],now-(total-left),now+left,now);}
            else if(!running&&atomic)LIST.finish(G,now);
            atomic=running;
        }
        // the dash (C): the combat state's tick it is ready again
        long dash=CombatClient.snapshot(p.getUuid()).dashReady();
        if(dash!=dashSeen){show(DASH,dashName(),dash-time,CombatState.DASH_COOLDOWN,now);dashSeen=dash;}
        // Star Platinum's punches (2.54.0): only their rest after a combo, as Kageno's attack (the server's ready tick)
        long punches=star?dev.sangwoo.celestialfall.client.stand.StandAttack.ready(p.getUuid()):0;
        if(punches!=punchesSeen){show(PUNCHES,punchesName(),punches-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.REST,now);punchesSeen=punches;}
        // its rush (2.57.0): from when it is over (the server's ready tick, told as it ends)
        long barrage=star?dev.sangwoo.celestialfall.client.stand.StandRush.ready(p.getUuid()):0;
        if(barrage!=barrageSeen){show(BARRAGE,barrageName(),barrage-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.RUSH_COOLDOWN,now);barrageSeen=barrage;}
        // its heavy punch (2.59.0): from when it is over, or stopped short (the server's ready tick, told with each of its punches' news)
        long heavy=star?dev.sangwoo.celestialfall.client.stand.StandAttack.heavyReady(p.getUuid()):0;
        if(heavy!=heavySeen){show(HEAVY,heavyName(),heavy-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.HEAVY_COOLDOWN,now);heavySeen=heavy;}
        // its Star Finger (2.60.0): the same, told with each of its punches' news
        long finger=star?dev.sangwoo.celestialfall.client.stand.StandAttack.fingerReady(p.getUuid()):0;
        if(finger!=fingerSeen){show(FINGER,fingerName(),finger-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.FINGER_COOLDOWN,now);fingerSeen=finger;}
        // its inhale (2.61.0): from when it is over (the server's ready tick, told as it ends)
        long inhale=star?dev.sangwoo.celestialfall.client.stand.StandInhale.ready(p.getUuid()):0;
        if(inhale!=inhaleSeen){show(INHALE,inhaleName(),inhale-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.INHALE_COOLDOWN,now);inhaleSeen=inhale;}
        // its beatdown (2.64.0): from when its blow is over, or its cutscene (the server's ready tick, told with its punches'
        // news; 2.73.0: from its ledger, which keeps it as they leave or die, once the server has told it)
        long beatdown=!star?0:ledger?LongCooldownsClient.ready(LongCooldowns.Skill.BEATDOWN):dev.sangwoo.celestialfall.client.stand.StandAttack.beatdownReady(p.getUuid());
        int beatdownLength=ledger?LongCooldownsClient.length(LongCooldowns.Skill.BEATDOWN):dev.sangwoo.celestialfall.stand.StarPlatinumRules.BEATDOWN_COOLDOWN;
        if(beatdown!=beatdownSeen){show(BEATDOWN,beatdownName(),beatdown-time,beatdownLength,now);beatdownSeen=beatdown;}
        // its time stop (2.67.0): from when time moves again, or its call was cut short (the server's ready tick, told as it
        // ends; 2.73.0: from its ledger once the server has told it, as the beatdown's)
        long timeStop=!star?0:ledger?LongCooldownsClient.ready(LongCooldowns.Skill.TIME_STOP):dev.sangwoo.celestialfall.client.stand.StandTimeStop.ready(p.getUuid());
        int timeStopLength=ledger?LongCooldownsClient.length(LongCooldowns.Skill.TIME_STOP):dev.sangwoo.celestialfall.stand.StarPlatinumRules.TS_COOLDOWN;
        if(timeStop!=timeStopSeen){show(TIME_STOP,timeStopName(),timeStop-time,timeStopLength,now);timeStopSeen=timeStop;}
        // Lee So-sang's attack (2.77.0): only its rest after a combo, as the others' (the server's ready tick, told with each hit's news)
        boolean isosang=shows(ISOSANG,stand);if(!isosang)dev.sangwoo.celestialfall.client.isosang.IsosangClient.forget(p.getUuid());
        long sword=isosang?dev.sangwoo.celestialfall.client.isosang.IsosangClient.ready(p.getUuid()):0;
        if(sword!=isosangSeen){show(ISOSANG,isosangName(),sword-time,dev.sangwoo.celestialfall.isosang.IsosangRules.REST,now);isosangSeen=sword;}
        // their E (2.80.0): from when the empowered combo it gave is over (the server's ready tick, told with each hit's news)
        long empower=isosang?dev.sangwoo.celestialfall.client.isosang.IsosangClient.eReady(p.getUuid()):0;
        if(empower!=isosangESeen){show(ISOSANG_E,isosangEName(),empower-time,dev.sangwoo.celestialfall.isosang.IsosangRules.E_COOLDOWN,now);isosangESeen=empower;}
        LIST.update(now);
    }
    /**
     * Whether the list shows this slot's cooldown to a player whose stand is {@code stand} (2.74.2): Kageno's skills and
     * Atomic to Kageno's user, Star Platinum's to its, and the dash to everyone; none of a stand's to one who has none
     * (after a Rokakaka). Since 2.74.3 the server keeps no long cooldown of another stand either (LongCooldowns): getting
     * the stand again (the arrow, the gacha) begins its long ones in full.
     */
    static boolean shows(int slot,String stand){
        if(slot==DASH)return true;
        // (2.77.0) Lee So-sang's to theirs
        if(slot==ISOSANG||slot==ISOSANG_E)return "isosang".equals(stand);
        return (slot<DASH?"kageno":"star_platinum").equals(stand);
    }
    /**
     * A cooldown that runs out {@code left} ticks from now, the last {@code length} ticks of that wait: until then its skill
     * is still being used and the list shows nothing for it (user request, 2.44.0). One that is over takes its box away.
     */
    private static void show(int slot,String name,long left,int length,double now){
        if(left<2)LIST.finish(slot,now);
        else LIST.schedule(slot,name,now+left-length,now+left,now);
    }
    /** The dash's name in the list: Korean like the stand's skill names beside it (KagenoRules.NAMES), whatever the game's language. */
    private static String dashName(){return "대시";}
    /** Star Platinum's punches' name in the list: its shout. */
    private static String punchesName(){return "오라!";}
    /** Star Platinum's rush's name in the list (the user's word for it). */
    private static String barrageName(){return "러시";}
    /** Its heavy punch's name in the list (the user's word for it). */
    private static String heavyName(){return "강펀치";}
    /** Its Star Finger's name in the list (the user's word for it). */
    private static String fingerName(){return "스타 핑거";}
    /** Its inhale's name in the list. */
    private static String inhaleName(){return "흡입";}
    /** Its beatdown's name in the list (the user's word for it). */
    private static String beatdownName(){return "비트다운";}
    /** Its time stop's name in the list (the user's word for it). */
    private static String timeStopName(){return "시간 정지";}
    /** Lee So-sang's attack's name in the list (the user's word for it, 2.77.0). */
    private static String isosangName(){return "평타";}
    /** Their E's name in the list (2.80.0; my choice, the user gave it none: it empowers the sword's next combo). */
    private static String isosangEName(){return "검 강화";}
    /** The skill's name; T's cooldown is shared with T held, so it is named after the one cast last. */
    private static String name(int slot,UUID me){
        if(slot!=T)return NAMES[slot];
        StandClient.Cast newest=null;
        for(var c:StandClient.CASTS.values())if(c.caster().equals(me)&&(c.skill()==T||c.skill()==RUSH)&&(newest==null||c.start()>newest.start()))newest=c;
        return newest!=null&&newest.skill()==RUSH?NAMES[RUSH]:NAMES[T];
    }

    public static void render(DrawContext d,float delta){
        var mc=MinecraftClient.getInstance();
        if(!MapClient.enabled()||mc.player==null||mc.world==null||mc.options.hudHidden||mc.player.isSpectator())return;
        if(mc.currentScreen!=null&&!(mc.currentScreen instanceof ChatScreen))return;
        double now=clock(delta);LIST.update(now);
        if(LIST.entries().isEmpty())return;
        int w=d.getScaledWindowWidth(),h=d.getScaledWindowHeight();
        var m=metrics(w,h);var boxes=LIST.layout(now,m);
        d.draw();
        var window=mc.getWindow();double k=window.getScaleFactor();int frameHeight=window.getFramebufferHeight();
        Matrix4f matrix=d.getMatrices().peek().getPositionMatrix();
        // the finished boxes' sand pours down out of their necks behind the boxes below (seen between and under them)
        for(var b:boxes)if(b.entry().leaving())pour(d,b,now);
        // oldest first, so a box sliding over another stays under the newer one
        for(int i=boxes.size()-1;i>=0;i--){
            var b=boxes.get(i);double p=b.presence(),big=b.big(),bh=b.height(),bw=b.width();
            boolean leaving=b.entry().leaving();
            // the box unfolds downwards from its slot's top edge (and a finished one rides up into it as its place closes)
            if(p<=0)continue;
            boolean clip=p<1;
            if(clip){
                int x0=(int)Math.floor(b.x()*k)-2,y1=(int)Math.ceil((b.y()+bh*p)*k),y0=(int)Math.floor(b.y()*k);
                if(y1<=y0)continue;
                RenderSystem.enableScissor(x0,frameHeight-y1,(int)Math.ceil(bw*k)+4,y1-y0);
            }
            double top=b.y()-bh*(1-p),x=b.x();float a=leaving?1:(float)Math.min(1,p*1.6);
            RenderSystem.enableBlend();RenderSystem.disableDepthTest();RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.defaultBlendFunc();
            double bar=Math.max(1/k,(SMALL_BAR+(BIG_BAR-SMALL_BAR)*big)*h),left=b.entry().remaining(now);
            double barRight=x+bw*left,barTop=top+bh-bar;
            double size=(SMALL_TEXT+(BIG_TEXT-SMALL_TEXT)*big)*h,pad=(SMALL_PAD+(BIG_PAD-SMALL_PAD)*big)*h;
            if(!leaving){
                rect(matrix,x,top,x+bw,top+bh,fade(BOX,a),fade(BOX,a));
                if(left>0){
                    // a soft sky-blue light over the bar, added, then the bar itself (lighter along its top edge)
                    RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);
                    rect(matrix,x,barTop-bar*2.2,barRight,barTop,(GLOW&0xffffff),fade(0xff000000|GLOW,a*.42f));
                    RenderSystem.defaultBlendFunc();
                    double edge=Math.min(bar*.45,1.2/k);
                    rect(matrix,x,barTop,barRight,barTop+edge,fade(BAR_TOP,a),fade(BAR_TOP,a));
                    rect(matrix,x,barTop+edge,barRight,top+bh,fade(BAR,a),fade(BAR,a));
                }
                GachaFont.text(b.entry().name,(float)(x+pad),(float)(top+(bh-bar)/2),(float)size,GachaFont.Weight.MEDIUM)
                    .color(0xffffffff).alpha(a).shadow(0x8c000000,.7f,0,(float)(size*.05)).draw(d);
            }else{
                // finished: it drains like an hourglass's upper bulb, down through a funnel in its middle
                double q=b.drain();
                drained(matrix,x,top,bw,bh,q,top,top+bh,BOX,k);
                if(left>0)drained(matrix,x,top,bw,bh,q,barTop,top+bh,BAR,k,barRight);
                rim(matrix,x,top,bw,bh,q,k);
                float fadeText=(float)(1-CooldownList.smooth(q/.45));
                if(fadeText>.01f)GachaFont.text(b.entry().name,(float)(x+pad),(float)(top+(bh-bar)/2+bh*.25*q),(float)size,GachaFont.Weight.MEDIUM)
                    .color(0xffffffff).alpha(fadeText).shadow(0x8c000000,.7f,0,(float)(size*.05)).draw(d);
            }
            if(clip)RenderSystem.disableScissor();
        }
        RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
    }
    /** The part of a box ({@code x}, {@code boxTop}, {@code bw} by {@code bh}) still full at {@code q} and between {@code y0} and {@code y1}: column by column under its sand's surface. */
    private static void drained(Matrix4f m,double x,double boxTop,double bw,double bh,double q,double y0,double y1,int color,double k){drained(m,x,boxTop,bw,bh,q,y0,y1,color,k,x+bw);}
    private static void drained(Matrix4f m,double x,double boxTop,double bw,double bh,double q,double y0,double y1,int color,double k,double right){
        if(right<=x||y1<=y0)return;
        int cols=Math.max(12,(int)Math.ceil(bw*k/2.5));
        var buf=Tessellator.getInstance().getBuffer();buf.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        int r=color>>16&255,g=color>>8&255,bl=color&255,al=color>>>24;boolean any=false;
        for(int c=0;c<cols;c++){
            double xa=x+bw*c/cols,xb=Math.min(right,x+bw*(c+1)/cols);if(xb<=xa)break;
            double sa=Math.max(y0,boxTop+bh*CooldownList.surface(q,2*(xa-x)/bw-1)),sb=Math.max(y0,boxTop+bh*CooldownList.surface(q,2*(xb-x)/bw-1));
            if(sa>=y1&&sb>=y1)continue;
            sa=Math.min(sa,y1);sb=Math.min(sb,y1);any=true;
            buf.vertex(m,(float)xa,(float)sa,0).color(r,g,bl,al).next();buf.vertex(m,(float)xa,(float)y1,0).color(r,g,bl,al).next();
            buf.vertex(m,(float)xb,(float)y1,0).color(r,g,bl,al).next();buf.vertex(m,(float)xb,(float)sb,0).color(r,g,bl,al).next();
        }
        var built=buf.end();if(any)BufferRenderer.drawWithGlobalProgram(built);
    }
    /** The light along a draining box's sand surface. */
    private static void rim(Matrix4f m,double x,double top,double bw,double bh,double q,double k){
        if(q<=0||q>=1)return;
        int cols=Math.max(12,(int)Math.ceil(bw*k/2.5));double thick=Math.max(1/k,bh*.06);
        var buf=Tessellator.getInstance().getBuffer();buf.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        boolean any=false;double bottom=top+bh;
        for(int c=0;c<cols;c++){
            double xa=x+bw*c/cols,xb=x+bw*(c+1)/cols;
            double sa=top+bh*CooldownList.surface(q,2.0*c/cols-1),sb=top+bh*CooldownList.surface(q,2.0*(c+1)/cols-1);
            if(sa>=bottom-thick&&sb>=bottom-thick)continue;
            float fa=(float)CooldownList.clamp((bottom-sa)/(bh*.3)),fb=(float)CooldownList.clamp((bottom-sb)/(bh*.3));any=true;
            int ca=fade(RIM,fa),cb=fade(RIM,fb);
            buf.vertex(m,(float)xa,(float)sa,0).color(ca>>16&255,ca>>8&255,ca&255,ca>>>24).next();buf.vertex(m,(float)xa,(float)Math.min(bottom,sa+thick),0).color(ca>>16&255,ca>>8&255,ca&255,0).next();
            buf.vertex(m,(float)xb,(float)Math.min(bottom,sb+thick),0).color(cb>>16&255,cb>>8&255,cb&255,0).next();buf.vertex(m,(float)xb,(float)sb,0).color(cb>>16&255,cb>>8&255,cb&255,cb>>>24).next();
        }
        var built=buf.end();if(any)BufferRenderer.drawWithGlobalProgram(built);
    }
    /** A finished box's sand pouring out of its neck (the middle of its bottom edge): a thin stream while it runs, and grains falling and fading. */
    private static void pour(DrawContext d,CooldownList.Box b,double now){
        double since=b.entry().since(now);if(since<0||since>=CooldownList.LEAVE)return;
        double bh=b.height(),neckX=b.x()+b.width()/2,neckY=b.y()+bh*b.presence();
        var m=d.getMatrices().peek().getPositionMatrix();
        var buf=dev.sangwoo.celestialfall.client.gacha.GachaArt.begin(d,false);
        float px=dev.sangwoo.celestialfall.client.ui.SkyUi.px();
        double stream=CooldownList.stream(since);
        if(stream>.01)dev.sangwoo.celestialfall.client.gacha.GachaArt.line(buf,m,(float)neckX,(float)neckY,(float)neckX,(float)(neckY+bh*.9),Math.max(px,(float)(bh*.05)),
            fade(GRAIN,(float)(.85*stream)),fade(GRAIN_DEEP,0));
        for(int i=0;i<CooldownList.GRAINS;i++){
            double[] g=CooldownList.grain(i,since);if(g[2]<=.01)continue;
            float r=(float)Math.max(px*.75,bh*.05*(.7+.6*CooldownList.hash(i*3+3)));
            dev.sangwoo.celestialfall.client.gacha.GachaArt.fill(buf,m,dev.sangwoo.celestialfall.client.gacha.GachaArt.circle((float)(neckX+g[0]*bh),(float)(neckY+g[1]*bh),r,6),
                fade(i%3==0?GRAIN_DEEP:GRAIN,(float)g[2]));
        }
        dev.sangwoo.celestialfall.client.gacha.GachaArt.end(buf);
    }
    /** The list's measures for this screen, widened so every skill's name fits its box. */
    private static CooldownList.Metrics metrics(int w,int h){
        long key=((long)w<<32)^((long)h<<12)^Math.round(MinecraftClient.getInstance().getWindow().getScaleFactor()*64)^(BotandHud.enabled()?1L<<62:0);
        if(metrics!=null&&key==metricsKey)return metrics;
        double top=h*TOP;
        // under the HP art and its money line when they reach this far down
        if(BotandHud.enabled())top=Math.max(top,BotandHudLayout.bottom(w)+6);
        double bigW=h*BIG_W,smallW=h*SMALL_W;
        for(int s=M1;s<=G+4;s++){
            String name=s==G+1?dashName():s==G+2?punchesName():s==G+3?barrageName():s==G+4?heavyName():NAMES[s];
            bigW=Math.max(bigW,GachaFont.width(name,(float)(h*BIG_TEXT),GachaFont.Weight.MEDIUM)+h*BIG_PAD*2.4);
            smallW=Math.max(smallW,GachaFont.width(name,(float)(h*SMALL_TEXT),GachaFont.Weight.MEDIUM)+h*SMALL_PAD*2.4);
        }
        metrics=new CooldownList.Metrics(Math.max(8,w*LEFT),top,bigW,h*BIG_H,smallW,h*SMALL_H,h*GAP);metricsKey=key;
        return metrics;
    }
    private static int fade(int argb,float a){return ((int)((argb>>>24)*Math.max(0,Math.min(1,a)))<<24)|(argb&0xffffff);}
    private static void rect(Matrix4f m,double x0,double y0,double x1,double y1,int top,int bottom){
        if(x1<=x0||y1<=y0)return;
        var b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        b.vertex(m,(float)x0,(float)y0,0).color(top>>16&255,top>>8&255,top&255,top>>>24).next();
        b.vertex(m,(float)x0,(float)y1,0).color(bottom>>16&255,bottom>>8&255,bottom&255,bottom>>>24).next();
        b.vertex(m,(float)x1,(float)y1,0).color(bottom>>16&255,bottom>>8&255,bottom&255,bottom>>>24).next();
        b.vertex(m,(float)x1,(float)y0,0).color(top>>16&255,top>>8&255,top&255,top>>>24).next();
        BufferRenderer.drawWithGlobalProgram(b.end());
    }
}
