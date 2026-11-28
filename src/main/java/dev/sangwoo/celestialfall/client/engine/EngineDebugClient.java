package dev.sangwoo.celestialfall.client.engine;

import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.engine.EngineDebug;
import dev.sangwoo.celestialfall.engine.EngineNet;
import dev.sangwoo.celestialfall.engine.Moves;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The combat engine's debug view on the client (2.157.0, {@code /botand engine debug}, EngineDebug): what the server
 * judged near this player for a second and a half. A blow's ray (green where it met someone, white where it did not),
 * each body it was judged against where it stood as the striker saw it (orange: wound back) and where it stands (grey);
 * and in the corner the last move's frame data (its startup, active frames and recovery, from the move as the engine
 * runs it, Moves) with how many ticks its targets were wound back, and this client's prediction.
 */
public final class EngineDebugClient {
    private EngineDebugClient(){}
    /** How long a judged blow is shown (client ticks). */
    static final int SHOWN=30;
    private record Judged(int attacker,int move,int seen,Vec3d eye,Vec3d end,double pad,int struck,List<Box> then,List<Box> now,int heard){}
    private static final List<Judged> JUDGED=new ArrayList<>();
    private static Judged last;
    private static Object world;

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(EngineNet.DEBUG,(mc,handler,b,response)->{
            int kind=b.readUnsignedByte();Identifier dim=b.readIdentifier();
            if(kind!=EngineDebug.RAY)return;
            int attacker=b.readVarInt(),move=b.readVarInt(),hit=b.readVarInt();long tick=b.readLong();int seen=b.readVarInt();
            Vec3d eye=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),end=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());double pad=b.readDouble();
            int struck=b.readVarInt(),n=Math.min(16,b.readVarInt());
            List<Box> then=new ArrayList<>(),now=new ArrayList<>();
            for(int i=0;i<n;i++){b.readVarInt();then.add(box(b));now.add(box(b));}
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                if(world!=mc.world){JUDGED.clear();world=mc.world;}
                var j=new Judged(attacker,move,seen,eye,end,pad,struck,then,now,StandClient.ticks());
                JUDGED.add(j);last=j;while(JUDGED.size()>32)JUDGED.remove(0);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->{JUDGED.clear();last=null;world=null;}));
        WorldRenderEvents.LAST.register(EngineDebugClient::render);
        HudRenderCallback.EVENT.register(EngineDebugClient::hud);
    }
    private static Box box(net.minecraft.network.PacketByteBuf b){return new Box(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble());}
    private static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();
        if(JUDGED.isEmpty()||mc.world==null||ctx.consumers()==null)return;
        JUDGED.removeIf(j->StandClient.ticks()-j.heard>SHOWN);
        var cam=ctx.camera().getPos();var m=ctx.matrixStack();var lines=ctx.consumers().getBuffer(RenderLayer.getLines());
        m.push();m.translate(-cam.x,-cam.y,-cam.z);
        for(var j:JUDGED){
            float fade=1-(StandClient.ticks()-j.heard)/(float)SHOWN;
            boolean met=j.struck>=0;
            line(m,lines,j.eye,j.end,met?.3f:1f,1f,met?.4f:1f,fade);
            for(int i=0;i<j.then.size();i++){
                WorldRenderer.drawBox(m,lines,j.now.get(i),.6f,.6f,.6f,.6f*fade);
                WorldRenderer.drawBox(m,lines,j.then.get(i).expand(j.pad),1f,.55f,.1f,fade);
            }
        }
        m.pop();
    }
    private static void line(net.minecraft.client.util.math.MatrixStack m,net.minecraft.client.render.VertexConsumer v,Vec3d a,Vec3d b,float r,float g,float bl,float alpha){
        var e=m.peek();Vec3d d=b.subtract(a).normalize();
        v.vertex(e.getPositionMatrix(),(float)a.x,(float)a.y,(float)a.z).color(r,g,bl,alpha).normal(e.getNormalMatrix(),(float)d.x,(float)d.y,(float)d.z).next();
        v.vertex(e.getPositionMatrix(),(float)b.x,(float)b.y,(float)b.z).color(r,g,bl,alpha).normal(e.getNormalMatrix(),(float)d.x,(float)d.y,(float)d.z).next();
    }
    private static void hud(DrawContext c,float delta){
        var mc=MinecraftClient.getInstance();
        if(last==null||mc.world==null||mc.options.hudHidden||!MapClient.enabled()||StandClient.ticks()-last.heard>200)return;
        var move=Moves.byNumber(last.move);if(move==null)return;
        int x=8,y=c.getScaledWindowHeight()/2-30;
        String[] text={
            "[engine] "+move.id+(last.struck>=0?"  HIT":"  miss"),
            "startup "+move.startup()+"  active "+move.active()+"  recovery "+move.recovery()+"  (ticks)",
            "rewound "+last.seen+" ticks ("+last.seen*50+" ms)",
            EngineClient.describe()
        };
        for(int i=0;i<text.length;i++)c.drawTextWithShadow(mc.textRenderer,text[i],x,y+i*10,i==0?0xff9fe6ff:0xffe6f3ff);
    }
}
