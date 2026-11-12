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
        // R's gray world goes on before the effects are drawn, so the cuts and the caster's outline keep their colour.
        AnimePostProcessor.renderMonochromeWorld(c.tickDelta());
        // Star Platinum's stopped world too (2.67.0): its punches keep their colour in it
        dev.sangwoo.celestialfall.client.stand.TimeStopView.world(c);
        var mc=MinecraftClient.getInstance();if(mc.world==null||!MapClient.enabled())return;
        var matrices=c.matrixStack();var camera=c.camera().getPos();float delta=c.tickDelta();
        var view=new org.joml.Matrix4f(matrices.peek().getPositionMatrix());
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
        // Lee So-sang's sword's hits (2.77.0): drawn as the casts are, from their own list
        for(var s:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            var skill=KagenoSkills.of(s.skill());if(skill==null)continue;
            // (2.82.0: H's formation lasts its whole length)
            float a=s.age(delta);if(a<0||a/20f>=dev.sangwoo.celestialfall.client.isosang.IsosangClient.lifeOf(s))continue;
            var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(delta);
            // (2.81.1) R's sword may stand far out from its user: drawn while either is near
            if(!dev.sangwoo.celestialfall.client.isosang.IsosangClient.shown(s,pos,camera,96))continue;
            casts.add(KagenoStatusFx.context(mc,s,delta,pos,camera,matrix,matrices,targets(mc,s,delta)));
        }
        // X Chara's knife's hits (2.114.0): drawn as the casts are, from their own list
        for(var s:dev.sangwoo.celestialfall.client.xchara.KnifeClient.SWINGS.values()){
            var skill=KagenoSkills.of(s.skill());if(skill==null)continue;
            float a=s.age(delta);if(a<0||a/20f>=skill.lifetime())continue;
            var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(delta);
            if(pos.squaredDistanceTo(camera)>96*96)continue;
            casts.add(KagenoStatusFx.context(mc,s,delta,pos,camera,matrix,matrices,targets(mc,s,delta)));
        }
        // X Chara's G (2.112.0): drawn as the casts are, from its own list (its strange space seen from up to some 30 blocks off)
        for(var s:dev.sangwoo.celestialfall.client.xchara.XCharaClient.CASTS.values()){
            var skill=KagenoSkills.of(s.skill());if(skill==null)continue;
            float a=s.age(delta);if(a<0||a/20f>=skill.lifetime())continue;
            var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(delta);
            if(pos.squaredDistanceTo(camera)>96*96)continue;
            casts.add(KagenoStatusFx.context(mc,s,delta,pos,camera,matrix,matrices,targets(mc,s,delta)));
        }
        var primary=StandClient.visualCast();
        if(primary!=null&&AtomicClient.CASTS.isEmpty()&&HeavenClient.CASTS.isEmpty()){
            Vec3d focus=primary.origin().add(0,1.1,0);
            var clip=new org.joml.Vector4f((float)focus.x,(float)focus.y,(float)focus.z,1).mul(matrix).mul(c.projectionMatrix());
            AnimePostProcessor.focus(clip.w>.01f?clip.x/clip.w*.5f+.5f:.5f,clip.w>.01f?clip.y/clip.w*.5f+.5f:.5f);
        }
        // Skills that opt in go through the HDR effect layer, and so do the guard and overdrive visuals while one of
        // them shows; the rest are drawn as before.
        boolean layer=dev.sangwoo.celestialfall.client.vfx.FxLayer.available();
        List<Fx> direct=new ArrayList<>(),layered=new ArrayList<>();
        for(var x:casts)(layer&&KagenoSkills.of(x.cast.skill()).layered()?layered:direct).add(x);
        // Star Platinum's punches (2.54.0), its rush (2.57.0), its inhale (2.61.0), its beatdown (2.64.0), its time
        // stop (2.68.0) and the stand arrow (2.70.0) go with the state effects
        boolean punches=dev.sangwoo.celestialfall.client.stand.StandPunchFx.prepare(mc,delta,camera)|dev.sangwoo.celestialfall.client.stand.StandRushFx.prepare(mc,delta,camera)
            |dev.sangwoo.celestialfall.client.stand.StandInhaleFx.prepare(mc,delta,camera)|dev.sangwoo.celestialfall.client.stand.StandBeatdownFx.prepare(mc,delta,camera)
            |dev.sangwoo.celestialfall.client.stand.StandTimeStopFx.prepare(mc,delta,camera)|dev.sangwoo.celestialfall.client.stand.StandArrowFx.prepare(mc,delta,camera)
            // (2.105.0) Lee So-sang's sword coming into her hand and going back into the light
            |dev.sangwoo.celestialfall.client.isosang.IsosangSheath.prepare(mc,delta,camera)
            // (2.115.0) X Chara's hack: the light about their knife and their panel
            |dev.sangwoo.celestialfall.client.xchara.HackFx.prepare(mc,delta,camera)
            // (2.119.0) X Chara's E: the lime light round the one it heals
            |dev.sangwoo.celestialfall.client.xchara.HealClient.prepare(mc,delta,camera);
        boolean statusLayered=layer&&(KagenoStatusFx.showing(mc,delta,camera)||punches);
        var buffer=Tessellator.getInstance().getBuffer();
        try{
            effects(mc,direct,!statusLayered,view,matrix,camera,delta,false);
            if(layered.isEmpty()&&!statusLayered)dev.sangwoo.celestialfall.client.vfx.FxLayer.idle();
            else layer(mc,layered,statusLayered,view,matrix,camera,delta);
            dev.sangwoo.celestialfall.client.kageno.HealNumbers.render(mc,matrices,delta,camera);
            dev.sangwoo.celestialfall.client.kageno.HitFeedback.numbers(mc,matrices,delta);
            RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            // Full-frame flashes for nearby viewers.
            float flash=0;int color=0xffffff;
            for(var x:casts)if(Math.min(x.origin.squaredDistanceTo(camera),x.pos.squaredDistanceTo(camera))<28*28){float v=KagenoSkills.of(x.cast.skill()).flash(x);if(v>flash){flash=v;color=KagenoSkills.of(x.cast.skill()).flashColor(x);}}
            float glow=KagenoStatusFx.flash(mc,delta,camera);if(glow>flash){flash=glow;color=0xfffcff;}
            if(flash>0){
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.disableDepthTest();
                buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var m=new EffectMesh(buffer,matrix,camera);
                var look=c.camera().getHorizontalPlane();Vec3d fwd=new Vec3d(look.x(),look.y(),look.z());var up=c.camera().getVerticalPlane();Vec3d vu=new Vec3d(up.x(),up.y(),up.z());Vec3d side=fwd.crossProduct(vu).normalize();
                Vec3d p=camera.add(fwd.multiply(.2));
                m.quad(p.add(side.multiply(3)).add(vu.multiply(3)),p.subtract(side.multiply(3)).add(vu.multiply(3)),p.subtract(side.multiply(3)).subtract(vu.multiply(3)),p.add(side.multiply(3)).subtract(vu.multiply(3)),color,Math.min(1,flash));
                BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.enableDepthTest();
            }
            // Geometry that reads through the white-out (Y's violet pillars), depth-tested against the world.
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var m=new EffectMesh(buffer,matrix,camera);
            for(var x:casts)KagenoSkills.of(x.cast.skill()).overFlash(x,m,false);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(buffer,matrix,camera);
            for(var x:casts)KagenoSkills.of(x.cast.skill()).overFlash(x,m,true);
            BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.defaultBlendFunc();
        }finally{matrices.pop();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);}
    }
    /** Light gain of the additive pass on the effect layer (above 1 the overlapping light turns white-hot). */
    static final float LAYER_GAIN=1f;
    /** Blocks over which effect surfaces fade out where they meet the world, on the effect layer. */
    static final float LAYER_SOFT=.35f;
    /**
     * The passes of every skill in order: blended geometry, body afterimages, additive light, fire and smoke cells,
     * brush strokes, brush crescents (plus their additive glow), glitch ribbons and ink ribbons (likewise), light over them and real blocks. {@code status} adds the guard and
     * overdrive visuals and the blood; on the effect layer ({@code layered}) the geometry passes get soft edges and
     * the light runs hotter.
     */
    private static void effects(MinecraftClient mc,List<Fx> casts,boolean status,org.joml.Matrix4f view,org.joml.Matrix4f matrix,Vec3d camera,float delta,boolean layered){
        layering=layered;
        try{effectPasses(mc,casts,status,view,matrix,camera,delta,layered);}finally{layering=false;}
    }
    private static boolean layering;
    /**
     * Whether the passes being drawn now are on the HDR effect layer (2.99.0: a skill drawing a batch of its own inside
     * one, as Lee So-sang's H's night does on its ground pass, draws it through FxLayer.draw if so).
     */
    public static boolean layering(){return layering;}
    private static void effectPasses(MinecraftClient mc,List<Fx> casts,boolean status,org.joml.Matrix4f view,org.joml.Matrix4f matrix,Vec3d camera,float delta,boolean layered){
        var buffer=Tessellator.getInstance().getBuffer();
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1,1,1,1);
        // Ink lying on the ground, under everything else (2.82.6, Lee So-sang's H): blended, then its light added again.
        if(KagenoFire.beginInk(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).ground(x);KagenoFire.endInk();}
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        KagenoFire.crescentGain=.18f;if(layered)KagenoFire.hot(1.8f);
        if(KagenoFire.beginInk(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).ground(x);KagenoFire.endInk();}
        KagenoFire.crescentGain=1;KagenoFire.hot(0);RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        // Opaque-ish blended geometry.
        buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var m=new EffectMesh(buffer,matrix,camera);
        for(var x:casts)KagenoSkills.of(x.cast.skill()).alpha(x,m);
        if(status){KagenoStatusFx.alpha(mc,m,delta,camera);dev.sangwoo.celestialfall.client.kageno.HitFeedback.blood(mc,m,delta);dev.sangwoo.celestialfall.client.stand.StandPunchFx.alpha(m);dev.sangwoo.celestialfall.client.stand.StandRushFx.alpha(m);dev.sangwoo.celestialfall.client.stand.StandInhaleFx.alpha(m);dev.sangwoo.celestialfall.client.stand.StandBeatdownFx.alpha(m);dev.sangwoo.celestialfall.client.stand.StandTimeStopFx.alpha(m);dev.sangwoo.celestialfall.client.stand.StandArrowFx.alpha(m);dev.sangwoo.celestialfall.client.xchara.HackFx.alpha(m);dev.sangwoo.celestialfall.client.xchara.HealClient.alpha(m);}
        if(layered)dev.sangwoo.celestialfall.client.vfx.FxLayer.draw(buffer.end(),false,1,LAYER_SOFT);else BufferRenderer.drawWithGlobalProgram(buffer.end());
        dev.sangwoo.celestialfall.client.kageno.BodyGhost.render(casts,view,camera,delta);
        // Additive emission.
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(buffer,matrix,camera);
        for(var x:casts)KagenoSkills.of(x.cast.skill()).additive(x,m);
        if(status){KagenoStatusFx.additive(mc,m,delta,camera,layered);dev.sangwoo.celestialfall.client.stand.StandPunchFx.additive(m);dev.sangwoo.celestialfall.client.stand.StandRushFx.additive(m);dev.sangwoo.celestialfall.client.stand.StandInhaleFx.additive(m);dev.sangwoo.celestialfall.client.stand.StandBeatdownFx.additive(m);dev.sangwoo.celestialfall.client.stand.StandTimeStopFx.additive(m);dev.sangwoo.celestialfall.client.stand.StandArrowFx.additive(m);dev.sangwoo.celestialfall.client.isosang.IsosangSheath.additive(m);dev.sangwoo.celestialfall.client.xchara.HackFx.additive(m);dev.sangwoo.celestialfall.client.xchara.HealClient.additive(m);}
        if(layered)dev.sangwoo.celestialfall.client.vfx.FxLayer.draw(buffer.end(),true,LAYER_GAIN,LAYER_SOFT);else BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.defaultBlendFunc();
        // Shader passes (fire, smoke, brush strokes) manage their own buffers.
        for(var x:casts)KagenoSkills.of(x.cast.skill()).shaders(x);
        if(status){dev.sangwoo.celestialfall.client.stand.StandPunchFx.dust(matrix,camera);dev.sangwoo.celestialfall.client.stand.StandRushFx.dust(matrix,camera);dev.sangwoo.celestialfall.client.stand.StandInhaleFx.dust(matrix,camera);dev.sangwoo.celestialfall.client.stand.StandBeatdownFx.dust(matrix,camera);dev.sangwoo.celestialfall.client.stand.StandTimeStopFx.dust(matrix,camera);dev.sangwoo.celestialfall.client.stand.StandArrowFx.dust(matrix,camera);}
        if(KagenoFire.beginStrokes(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).strokes(x);KagenoFire.endStrokes();}
        if(KagenoFire.beginCrescents(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).crescents(x);KagenoFire.endCrescents();}
        // Same crescents again, added on top, for the bright glow of the reference slashes (hotter on the layer).
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        KagenoFire.crescentGain=.18f;if(layered)KagenoFire.hot(1.8f);
        if(KagenoFire.beginCrescents(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).crescents(x);KagenoFire.endCrescents();}
        KagenoFire.crescentGain=1;KagenoFire.hot(0);RenderSystem.defaultBlendFunc();
        // Glitch ribbons (2.114.0, X Chara's knife's cuts): blended, then their light added again on top as the crescents' is.
        if(KagenoFire.beginGlitch(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).glitch(x);KagenoFire.endGlitch();}
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        KagenoFire.crescentGain=.18f;if(layered)KagenoFire.hot(1.8f);
        if(KagenoFire.beginGlitch(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).glitch(x);KagenoFire.endGlitch();}
        KagenoFire.crescentGain=1;KagenoFire.hot(0);RenderSystem.defaultBlendFunc();
        // Ink ribbons (2.77.0, Lee So-sang's slashes): blended, then their light added again on top as the crescents' is.
        if(KagenoFire.beginInk(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).ink(x);KagenoFire.endInk();}
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        KagenoFire.crescentGain=.18f;if(layered)KagenoFire.hot(1.8f);
        if(KagenoFire.beginInk(matrix,mc.world.getTime()+delta)){for(var x:casts)KagenoSkills.of(x.cast.skill()).ink(x);KagenoFire.endInk();}
        KagenoFire.crescentGain=1;KagenoFire.hot(0);
        // Light over the ink ribbons (2.77.5: the starlight on Lee So-sang's slashes, which the ribbons would cover).
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(buffer,matrix,camera);
        for(var x:casts)KagenoSkills.of(x.cast.skill()).overlay(x,m);
        if(layered)dev.sangwoo.celestialfall.client.vfx.FxLayer.draw(buffer.end(),true,LAYER_GAIN,LAYER_SOFT);else BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.defaultBlendFunc();
        // Real blocks: on the effect layer they were drawn on the screen before it, with the solids.
        if(!layered)for(var x:casts)KagenoSkills.of(x.cast.skill()).blocks(x);
    }
    /**
     * Layered casts (and the state effects when {@code status}): their solids and real blocks on the screen first
     * (with depth), then the effect layer: refraction, the world pass (lights, decals), every effect pass on the HDR
     * copy, then bloom, afterglow and the composite. If the layer cannot run this frame, they are drawn directly.
     */
    private static void layer(MinecraftClient mc,List<Fx> casts,boolean status,org.joml.Matrix4f view,org.joml.Matrix4f matrix,Vec3d camera,float delta){
        var frame=new dev.sangwoo.celestialfall.client.vfx.FxFrame();
        for(var x:casts)KagenoSkills.of(x.cast.skill()).frame(x,frame);
        if(status){KagenoStatusFx.frame(mc,frame,delta,camera);dev.sangwoo.celestialfall.client.stand.StandPunchFx.frame(frame,camera);dev.sangwoo.celestialfall.client.stand.StandRushFx.frame(frame,camera);dev.sangwoo.celestialfall.client.stand.StandInhaleFx.frame(frame,camera);dev.sangwoo.celestialfall.client.stand.StandBeatdownFx.frame(frame,camera);dev.sangwoo.celestialfall.client.stand.StandTimeStopFx.frame(frame,camera);dev.sangwoo.celestialfall.client.stand.StandArrowFx.frame(frame,camera);dev.sangwoo.celestialfall.client.isosang.IsosangSheath.frame(frame,camera);dev.sangwoo.celestialfall.client.xchara.HackFx.frame(frame,camera);dev.sangwoo.celestialfall.client.xchara.HealClient.frame(frame,camera);}
        var stack=new net.minecraft.client.util.math.MatrixStack();
        stack.peek().getPositionMatrix().set(view);stack.peek().getNormalMatrix().set(new org.joml.Matrix3f(view));
        var solids=dev.sangwoo.celestialfall.client.vfx.FxLayer.solids(stack,camera);
        for(var x:casts)KagenoSkills.of(x.cast.skill()).solids(x,solids);
        if(status){dev.sangwoo.celestialfall.client.stand.StandPunchFx.solids(solids);dev.sangwoo.celestialfall.client.stand.StandBeatdownFx.solids(solids);dev.sangwoo.celestialfall.client.stand.StandTimeStopFx.solids(solids);}
        dev.sangwoo.celestialfall.client.vfx.FxLayer.finishSolids(solids);
        for(var x:casts)KagenoSkills.of(x.cast.skill()).blocks(x);
        var d=dev.sangwoo.celestialfall.client.vfx.FxLayer.begin(view,matrix,camera);
        if(d==null){effects(mc,casts,status,view,matrix,camera,delta,false);return;}
        for(var x:casts)KagenoSkills.of(x.cast.skill()).distort(x,d);
        if(status)KagenoStatusFx.distort(mc,d,delta,camera);
        dev.sangwoo.celestialfall.client.vfx.FxLayer.scene(frame);
        effects(mc,casts,status,view,matrix,camera,delta,true);
        dev.sangwoo.celestialfall.client.vfx.FxLayer.finish(frame,(mc.world.getTime()+delta)/20.0);
    }
    private static List<LivingEntity> targets(MinecraftClient mc,StandClient.Cast s,float delta){
        var out=new ArrayList<LivingEntity>();
        for(var t:s.targets()){var e=mc.world.getEntityById(t.entity());if(e instanceof LivingEntity l&&e.getUuid().equals(t.uuid()))out.add(l);}
        return out;
    }
    public static void hud(DrawContext draw,float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||!MapClient.enabled()||mc.options.hudHidden)return;
        int width=draw.getScaledWindowWidth(),height=draw.getScaledWindowHeight();
        // (2.108.0) the "N Hits" of the reference UI is every character's now: HitCounterHud
        // The skill cooldowns are CooldownHud's list (2.42.0); the old panel of keys and seconds left is gone.
        if(!StandClient.visible(mc.player)||mc.currentScreen!=null||CinematicDirector.isActive())return;
        var state=StandClient.STATES.get(mc.player.getUuid());long now=mc.world.getTime();
        if(state!=null&&state.until()>now)draw.drawCenteredTextWithShadow(mc.textRenderer,String.format(java.util.Locale.ROOT,"OVERDRIVE  %.1fs  ×%.2f",(state.until()-now)/20.,1+state.boost()),width/2,height-72,0xffdc9cff);
    }
}
