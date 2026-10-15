package dev.sangwoo.celestialfall.client;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.kageno.Ease;
import dev.sangwoo.celestialfall.client.kageno.Fx;
import dev.sangwoo.celestialfall.client.kageno.KagenoSkills;
import dev.sangwoo.celestialfall.client.kageno.KagenoStatusFx;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.*;
import java.util.ArrayList;
import java.util.List;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** Dispatches every Kageno cast to its rebuilt skill visuals, pass by pass. */
public final class KagenoRenderer {
    private KagenoRenderer(){}
    public static void render(WorldRenderContext c){
        var mc=MinecraftClient.getInstance();if(mc.world==null||!MapClient.enabled())return;
        var matrices=c.matrixStack();var camera=c.camera().getPos();float delta=c.tickDelta();
        matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);
        var matrix=matrices.peek().getPositionMatrix();
        List<Fx> casts=new ArrayList<>();
        for(var s:StandClient.CASTS.values()){
            var skill=KagenoSkills.of(s.skill());if(skill==null)continue;
            float a=s.age(delta);if(a<0||a/20f>=skill.lifetime())continue;
            var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(delta);
            if(pos.squaredDistanceTo(camera)>96*96&&s!=StandClient.cinematic())continue;
            casts.add(KagenoStatusFx.context(mc,s,delta,pos,camera,matrix,matrices,targets(mc,s,delta)));
        }
        var primary=StandClient.visualCast();
        if(primary!=null&&AtomicClient.CASTS.isEmpty()&&HeavenClient.CASTS.isEmpty()){
            Vec3d focus=primary.origin().add(0,1.1,0);
            var clip=new org.joml.Vector4f((float)focus.x,(float)focus.y,(float)focus.z,1).mul(matrix).mul(c.projectionMatrix());
            AnimePostProcessor.focus(clip.w>.01f?clip.x/clip.w*.5f+.5f:.5f,clip.w>.01f?clip.y/clip.w*.5f+.5f:.5f);
        }
        var buffer=Tessellator.getInstance().getBuffer();
        try{
            RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.setShaderColor(1,1,1,1);
            // Opaque-ish blended geometry.
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var m=new EffectMesh(buffer,matrix,camera);
            for(var x:casts)KagenoSkills.of(x.cast.skill()).alpha(x,m);
            KagenoStatusFx.alpha(mc,m,delta,camera);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            // Additive emission.
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(buffer,matrix,camera);
            for(var x:casts)KagenoSkills.of(x.cast.skill()).additive(x,m);
            KagenoStatusFx.additive(mc,m,delta,camera);
            BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.defaultBlendFunc();
            // Shader passes (fire, smoke, brush strokes) manage their own buffers.
            for(var x:casts)KagenoSkills.of(x.cast.skill()).shaders(x);
            if(KagenoFire.beginStrokes(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).strokes(x);KagenoFire.endStrokes();}
            if(KagenoFire.beginCrescents(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).crescents(x);KagenoFire.endCrescents();}
            // Same crescents again, added on top, for the bright glow of the reference slashes.
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);KagenoFire.crescentGain=.18f;
            if(KagenoFire.beginCrescents(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).crescents(x);KagenoFire.endCrescents();}
            KagenoFire.crescentGain=1;RenderSystem.defaultBlendFunc();
            for(var x:casts)KagenoSkills.of(x.cast.skill()).blocks(x);
            RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            // Full-frame flashes for nearby viewers.
            float flash=0;int color=0xffffff;
            for(var x:casts)if(x.origin.squaredDistanceTo(camera)<28*28){float v=KagenoSkills.of(x.cast.skill()).flash(x);if(v>flash){flash=v;color=KagenoSkills.of(x.cast.skill()).flashColor(x);}}
            if(flash>0){
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.disableDepthTest();
                buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(buffer,matrix,camera);
                var look=c.camera().getHorizontalPlane();Vec3d fwd=new Vec3d(look.x(),look.y(),look.z());var up=c.camera().getVerticalPlane();Vec3d vu=new Vec3d(up.x(),up.y(),up.z());Vec3d side=fwd.crossProduct(vu).normalize();
                Vec3d p=camera.add(fwd.multiply(.2));
                m.quad(p.add(side.multiply(3)).add(vu.multiply(3)),p.subtract(side.multiply(3)).add(vu.multiply(3)),p.subtract(side.multiply(3)).subtract(vu.multiply(3)),p.add(side.multiply(3)).subtract(vu.multiply(3)),color,Math.min(1,flash));
                BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.enableDepthTest();
            }
            // Owner-only shadow marks, visible through walls.
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.disableDepthTest();
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(buffer,matrix,camera);
            KagenoStatusFx.marks(mc,m,delta,camera);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }finally{matrices.pop();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);}
    }
    private static List<LivingEntity> targets(MinecraftClient mc,StandClient.Cast s,float delta){
        var out=new ArrayList<LivingEntity>();
        for(var t:s.targets()){var e=mc.world.getEntityById(t.entity());if(e instanceof LivingEntity l&&e.getUuid().equals(t.uuid()))out.add(l);}
        return out;
    }
    public static void hud(DrawContext draw,float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||!MapClient.enabled()||mc.options.hudHidden)return;
        var cinematic=StandClient.cinematic();int width=draw.getScaledWindowWidth(),height=draw.getScaledWindowHeight();
        if(cinematic!=null)return;
        // Reference UI: "N Hits" at the left, a third down. It flashes red #F62223 on every hit and
        // eases back to white in 0.25 s with a small drift and tilt; the purple timer below counts
        // seconds since the first hit of the combo.
        float since=(mc.world.getTime()-StandClient.hitTime+delta)/20f;
        if(StandClient.visible(mc.player)&&StandClient.hitCount>0&&since<2.75f){
            String text=StandClient.hitCount+(StandClient.hitCount==1?" Hit":" Hits");
            float scale=Math.max(1.2f,height*.0315f/7f),alpha=1-Ease.smooth(since,2.5f,2.75f);int a=Math.max(8,(int)(alpha*255));
            float k=Math.min(1,since/.25f);int r=(int)(246+(248-246)*k),g=(int)(34+(251-34)*k*k*.6f+(251-34)*k*.4f),b=(int)(35+(249-35)*k*k*.6f+(249-35)*k*.4f);
            float pop=1-Ease.smooth(since,0,.37f);
            var mat=draw.getMatrices();mat.push();mat.translate(width*.042f-pop*width*.0037f,height*.31f,0);
            mat.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-3*pop));mat.scale(scale,scale,1);
            draw.drawText(mc.textRenderer,text,0,-4,(a<<24)|(r<<16)|(Math.min(255,g)<<8)|Math.min(255,b),true);mat.pop();
            float combo=(mc.world.getTime()-StandClient.comboStart+delta)/20f;
            mat.push();mat.translate(width*.042f,height*.31f+scale*9,0);mat.scale(scale*.55f,scale*.55f,1);
            draw.drawText(mc.textRenderer,mc.player.getName().getString()+" : "+String.format(java.util.Locale.ROOT,"%.2f",Math.max(0,combo)),0,0,(a<<24)|0x9156e0,true);mat.pop();
        }
        if(!StandClient.visible(mc.player)||mc.currentScreen!=null||CinematicDirector.isActive())return;
        var state=StandClient.STATES.get(mc.player.getUuid());long now=mc.world.getTime();
        var panel=draw.getMatrices();panel.push();float ui=.72f;panel.translate(width-106,Math.max(68,height/2-40),0);panel.scale(ui,ui,1);
        int x=7,y=17;draw.fill(x-7,y-17,145,y+110,0xbe100e1c);
        draw.drawTextWithShadow(mc.textRenderer,"CID KAGENOU",x,y-12,0xffd2a7ff);
        int[] order={Q,E,R,T,Y,H,G};String[] keys={"Q","E","R","T","Y","H","G"};
        for(int i=0;i<order.length;i++){
            int skill=order[i];long wait=state==null?0:Math.max(0,state.ready()[skill]-now);String suffix=wait>0?" "+String.format(java.util.Locale.ROOT,"%.1f",wait/20.):"";
            String text=keys[i]+"  "+NAMES[skill]+suffix;float scale=Math.min(1,126f/Math.max(1,mc.textRenderer.getWidth(text)));
            var matrix=draw.getMatrices();matrix.push();matrix.translate(x,y+i*13,0);matrix.scale(scale,scale,1);draw.drawTextWithShadow(mc.textRenderer,text,0,0,wait>0?0xff887a9c:0xffe6ddf5);matrix.pop();
        }
        draw.drawTextWithShadow(mc.textRenderer,"T 길게: 돌진 · I: 가방",x,y+94,0xffa99bc0);
        panel.pop();
        if(state!=null&&state.until()>now)draw.drawCenteredTextWithShadow(mc.textRenderer,String.format(java.util.Locale.ROOT,"OVERDRIVE  %.1fs  ×%.2f",(state.until()-now)/20.,1+state.boost()),width/2,height-72,0xffdc9cff);
    }
}
