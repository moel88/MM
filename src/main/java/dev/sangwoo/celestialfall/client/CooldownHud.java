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
 * the frame's partial tick: the world's time is set again by the server every second and can step by a tick.
 */
public final class CooldownHud {
    /** The reference's measures, as shares of the screen's height (its left edge as a share of the width). */
    private static final double LEFT=.036,TOP=.374,BIG_W=.406,BIG_H=.094,SMALL_W=.271,SMALL_H=.054,GAP=.0137,
        BIG_TEXT=.0385,SMALL_TEXT=.027,BIG_PAD=.0208,SMALL_PAD=.0139,BIG_BAR=.0069,SMALL_BAR=.0028;
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
    private static final CooldownList LIST=new CooldownList();
    private static final long[] SEEN=new long[9];
    private static long ticks,dashSeen,punchesSeen,barrageSeen,heavySeen,fingerSeen,inhaleSeen,beatdownSeen,timeStopSeen,atomicSeen;
    private static boolean atomic;
    private static UUID owner;private static Object world;
    private static CooldownList.Metrics metrics;private static long metricsKey;
    private CooldownHud(){}

    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(CooldownHud::tick);
        HudRenderCallback.EVENT.register(CooldownHud::render);
    }
    /** The list's clock on this frame (ticks). */
    static double clock(float delta){return ticks+delta;}
    public static void clear(){LIST.clear();java.util.Arrays.fill(SEEN,0);dashSeen=punchesSeen=barrageSeen=heavySeen=fingerSeen=inhaleSeen=beatdownSeen=timeStopSeen=atomicSeen=0;atomic=false;owner=null;world=null;}
    /** The names the list shows now, top first (for the QA tools). */
    public static java.util.List<String> shown(){return LIST.entries().stream().filter(e->!e.leaving()).map(e->e.name).toList();}

    private static void tick(MinecraftClient mc){
        var p=mc.player;
        if(mc.world==null||p==null||!MapClient.enabled()){if(owner!=null)clear();return;}
        if(!p.getUuid().equals(owner)||mc.world!=world){clear();owner=p.getUuid();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;double now=ticks;long time=mc.world.getTime();
        var status=StandClient.STATES.get(p.getUuid());
        if(status!=null){
            long[] ready=status.ready();
            for(int slot=0;slot<Math.min(ready.length,SEEN.length);slot++){
                long r=ready[slot],was=SEEN[slot];SEEN[slot]=r;
                if(slot==G||r==was)continue;
                long left=r-time;
                // the attack's steps between hits stay off the list: only its rest after a combo shows
                if(slot==M1&&left>0&&left<COMBO_END)continue;
                show(slot,name(slot,p.getUuid()),left,cooling(slot),now);
            }
        }
        // Atomic: from the server's ledger once it has told it (2.73.0, LongCooldowns: ten minutes, begun again in full on
        // coming back or a reset; a new body's sword shows only what is left of it); else the sword's cooldown share at
        // this tick and one tick on gives its length
        boolean ledger=LongCooldownsClient.known();
        if(ledger){
            long atomicReady=LongCooldownsClient.ready(LongCooldowns.Skill.ATOMIC);
            if(atomicReady!=atomicSeen){show(G,NAMES[G],atomicReady-time,LongCooldownsClient.length(LongCooldowns.Skill.ATOMIC),now);atomicSeen=atomicReady;}
        }else{
            var cooldowns=p.getItemCooldownManager();
            float p0=cooldowns.getCooldownProgress(CelestialFall.ATOMIC_SWORD,0),p1=cooldowns.getCooldownProgress(CelestialFall.ATOMIC_SWORD,1);
            boolean running=p0>0;
            if(running&&!atomic&&p0>p1){double total=1/(double)(p0-p1),left=p0*total;LIST.add(G,NAMES[G],now-(total-left),now+left,now);}
            else if(!running&&atomic)LIST.finish(G,now);
            atomic=running;
        }
        // the dash (C): the combat state's tick it is ready again
        long dash=CombatClient.snapshot(p.getUuid()).dashReady();
        if(dash!=dashSeen){show(DASH,dashName(),dash-time,CombatState.DASH_COOLDOWN,now);dashSeen=dash;}
        // Star Platinum's punches (2.54.0): only their rest after a combo, as Kageno's attack (the server's ready tick)
        long punches=dev.sangwoo.celestialfall.client.stand.StandAttack.ready(p.getUuid());
        if(punches!=punchesSeen){show(PUNCHES,punchesName(),punches-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.REST,now);punchesSeen=punches;}
        // its rush (2.57.0): from when it is over (the server's ready tick, told as it ends)
        long barrage=dev.sangwoo.celestialfall.client.stand.StandRush.ready(p.getUuid());
        if(barrage!=barrageSeen){show(BARRAGE,barrageName(),barrage-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.RUSH_COOLDOWN,now);barrageSeen=barrage;}
        // its heavy punch (2.59.0): from when it is over, or stopped short (the server's ready tick, told with each of its punches' news)
        long heavy=dev.sangwoo.celestialfall.client.stand.StandAttack.heavyReady(p.getUuid());
        if(heavy!=heavySeen){show(HEAVY,heavyName(),heavy-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.HEAVY_COOLDOWN,now);heavySeen=heavy;}
        // its Star Finger (2.60.0): the same, told with each of its punches' news
        long finger=dev.sangwoo.celestialfall.client.stand.StandAttack.fingerReady(p.getUuid());
        if(finger!=fingerSeen){show(FINGER,fingerName(),finger-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.FINGER_COOLDOWN,now);fingerSeen=finger;}
        // its inhale (2.61.0): from when it is over (the server's ready tick, told as it ends)
        long inhale=dev.sangwoo.celestialfall.client.stand.StandInhale.ready(p.getUuid());
        if(inhale!=inhaleSeen){show(INHALE,inhaleName(),inhale-time,dev.sangwoo.celestialfall.stand.StarPlatinumRules.INHALE_COOLDOWN,now);inhaleSeen=inhale;}
        // its beatdown (2.64.0): from when its blow is over, or its cutscene (the server's ready tick, told with its punches'
        // news; 2.73.0: from its ledger, which keeps it as they leave or reset, once the server has told it)
        long beatdown=ledger?LongCooldownsClient.ready(LongCooldowns.Skill.BEATDOWN):dev.sangwoo.celestialfall.client.stand.StandAttack.beatdownReady(p.getUuid());
        int beatdownLength=ledger?LongCooldownsClient.length(LongCooldowns.Skill.BEATDOWN):dev.sangwoo.celestialfall.stand.StarPlatinumRules.BEATDOWN_COOLDOWN;
        if(beatdown!=beatdownSeen){show(BEATDOWN,beatdownName(),beatdown-time,beatdownLength,now);beatdownSeen=beatdown;}
        // its time stop (2.67.0): from when time moves again, or its call was cut short (the server's ready tick, told as it
        // ends; 2.73.0: from its ledger once the server has told it, as the beatdown's)
        long timeStop=ledger?LongCooldownsClient.ready(LongCooldowns.Skill.TIME_STOP):dev.sangwoo.celestialfall.client.stand.StandTimeStop.ready(p.getUuid());
        int timeStopLength=ledger?LongCooldownsClient.length(LongCooldowns.Skill.TIME_STOP):dev.sangwoo.celestialfall.stand.StarPlatinumRules.TS_COOLDOWN;
        if(timeStop!=timeStopSeen){show(TIME_STOP,timeStopName(),timeStop-time,timeStopLength,now);timeStopSeen=timeStop;}
        LIST.update(now);
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
        // oldest first, so a box sliding over another stays under the newer one
        for(int i=boxes.size()-1;i>=0;i--){
            var b=boxes.get(i);double p=b.presence(),big=b.big(),bh=b.height(),bw=b.width();
            // the box unfolds downwards from its slot's top edge (and folds back up into it when it leaves)
            boolean clip=p<1;
            if(clip){
                int x0=(int)Math.floor(b.x()*k)-2,y1=(int)Math.ceil((b.y()+bh*p)*k),y0=(int)Math.floor(b.y()*k);
                if(y1<=y0)continue;
                RenderSystem.enableScissor(x0,frameHeight-y1,(int)Math.ceil(bw*k)+4,y1-y0);
            }
            double top=b.y()-bh*(1-p),x=b.x();float a=(float)Math.min(1,p*1.6);
            RenderSystem.enableBlend();RenderSystem.disableDepthTest();RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.defaultBlendFunc();
            rect(matrix,x,top,x+bw,top+bh,fade(BOX,a),fade(BOX,a));
            double bar=Math.max(1/k,(SMALL_BAR+(BIG_BAR-SMALL_BAR)*big)*h),left=b.entry().remaining(now);
            double barRight=x+bw*left,barTop=top+bh-bar;
            if(left>0){
                // a soft sky-blue light over the bar, added, then the bar itself (lighter along its top edge)
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA,GlStateManager.DstFactor.ONE);
                rect(matrix,x,barTop-bar*2.2,barRight,barTop,(GLOW&0xffffff),fade(0xff000000|GLOW,a*.42f));
                RenderSystem.defaultBlendFunc();
                double edge=Math.min(bar*.45,1.2/k);
                rect(matrix,x,barTop,barRight,barTop+edge,fade(BAR_TOP,a),fade(BAR_TOP,a));
                rect(matrix,x,barTop+edge,barRight,top+bh,fade(BAR,a),fade(BAR,a));
            }
            double size=(SMALL_TEXT+(BIG_TEXT-SMALL_TEXT)*big)*h,pad=(SMALL_PAD+(BIG_PAD-SMALL_PAD)*big)*h;
            GachaFont.text(b.entry().name,(float)(x+pad),(float)(top+(bh-bar)/2),(float)size,GachaFont.Weight.MEDIUM)
                .color(0xffffffff).alpha(a).shadow(0x8c000000,.7f,0,(float)(size*.05)).draw(d);
            if(clip)RenderSystem.disableScissor();
        }
        RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
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
