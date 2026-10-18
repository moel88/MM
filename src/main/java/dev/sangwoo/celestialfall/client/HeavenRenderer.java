package dev.sangwoo.celestialfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** Solid engraved gold blade, blue-white portal, finger spark and a terrain-preserving impact. */
public final class HeavenRenderer {
    private static final Vec3d X=new Vec3d(1,0,0),Y=new Vec3d(0,1,0),Z=new Vec3d(0,0,1);
    private static final double[] LEVELS=HeavenBlade.LEVELS;
    private HeavenRenderer() {}
    private static int quality(int low,int high) { return CelestialFall.CONFIG.visualQuality==0?low:high; }
    static Vec3d point(Vec3d target,float yaw,float age,double x,double y,double z) {
        return HeavenScene.bladePoint(target,yaw,age,x,y,z);
    }
    private static Vec3d point(HeavenClient.Cast s,float a,double x,double y,double z) { return point(s.target,s.yaw,a,x,y,z); }
    public static void render(WorldRenderContext c) {
        var mc=MinecraftClient.getInstance();if(mc.world==null || HeavenClient.CASTS.isEmpty())return;
        var primary=HeavenClient.cinematic();if(primary==null)primary=HeavenClient.nearest();if(primary==null)return;
        float a=primary.age(c.tickDelta());Vec3d focus=HeavenScene.effectFocus(primary.origin,primary.target,primary.yaw,a);
        Vec3d relative=focus.subtract(c.camera().getPos());Vector4f clip=new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1)
            .mul(c.matrixStack().peek().getPositionMatrix()).mul(c.projectionMatrix());
        AnimePostProcessor.focus(clip.w>.01?clip.x/clip.w*.5f+.5f:.5f,clip.w>.01?clip.y/clip.w*.5f+.5f:.5f);
        ImpactEffects.renderBlocks(c);
        for(var s:HeavenClient.CASTS.values())HeavenActor.render(c,s);
        var m=c.matrixStack();var camera=c.camera().getPos();var buffer=Tessellator.getInstance().getBuffer();
        m.push();m.translate(-camera.x,-camera.y,-camera.z);
        try {
            RenderSystem.enableDepthTest();RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.setShaderColor(1,1,1,1);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            EffectMesh mesh=new EffectMesh(buffer,m.peek().getPositionMatrix(),camera);
            for(var s:HeavenClient.CASTS.values()) { solidBlade(mesh,s,s.age(c.tickDelta()));introBackdrop(mesh,s,s.age(c.tickDelta())); }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            for(var s:HeavenClient.CASTS.values())clouds(buffer,c,s,s.age(c.tickDelta()));
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.depthMask(false);RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            mesh=new EffectMesh(buffer,m.peek().getPositionMatrix(),camera);
            for(var s:HeavenClient.CASTS.values()) {
                float age=s.age(c.tickDelta());finger(mesh,s,age);portal(mesh,s,age);bladeGlow(mesh,s,age);impact(mesh,s,age);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            ImpactEffects.renderSmoke(c,m);
        } finally {
            m.pop();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
        }
    }
    private static void solidBlade(EffectMesh m,HeavenClient.Cast s,float a) {
        float alpha=HeavenTimeline.visibility(a)*HeavenTimeline.reveal(a);if(alpha<=0)return;
        for(var f:HeavenBlade.visibleFaces(a))m.quad(point(s,a,f.a().x,f.a().y,f.a().z),point(s,a,f.b().x,f.b().y,f.b().z),
            point(s,a,f.c().x,f.c().y,f.c().z),point(s,a,f.d().x,f.d().y,f.d().z),f.color(),alpha);
    }
    private static void introBackdrop(EffectMesh m,HeavenClient.Cast s,float age) {
        if(HeavenClient.cinematic()!=s || age>=HeavenTimeline.FACE)return;
        float alpha=1-HeavenTimeline.ease(age,18,HeavenTimeline.FACE);
        var frame=HeavenActor.handFrame(s.origin,s.yaw,age);
        Vec3d normal=frame.position().subtract(frame.focus()).normalize();
        Vec3d right=normal.crossProduct(Y).normalize();if(right.lengthSquared()<.01)right=X;
        Vec3d up=right.crossProduct(normal).normalize(),centre=frame.focus().subtract(normal.multiply(2.5));
        Vec3d r=right.multiply(4),u=up.multiply(4);
        m.quad(centre.subtract(r).subtract(u),centre.add(r).subtract(u),centre.add(r).add(u),centre.subtract(r).add(u),0x010306,alpha);
    }
    private static void clouds(BufferBuilder buffer,WorldRenderContext c,HeavenClient.Cast s,float age) {
        float alpha=HeavenTimeline.portal(age);if(alpha<=0)return;
        var world=new org.joml.Matrix4f(c.matrixStack().peek().getPositionMatrix());
        for(int i=HeavenClouds.LAYERS.size()-1;i>=0;i--) {
            var layer=HeavenClouds.LAYERS.get(i);
            var matrix=new org.joml.Matrix4f(world).mul(HeavenScene.cloudTransform(s.origin,s.target,s.yaw,age,i));
            var mesh=new EffectMesh(buffer,matrix,c.camera().getPos());
            boolean low=CelestialFall.CONFIG.visualQuality==0;
            var colors=low?layer.lowColors():layer.highColors();
            for(var f:low?layer.low():layer.high()) {
                mesh.vertex(f.a(),colors.get(f.a()),alpha);mesh.vertex(f.b(),colors.get(f.b()),alpha);
                mesh.vertex(f.c(),colors.get(f.c()),alpha);mesh.vertex(f.d(),colors.get(f.d()),alpha);
            }
        }
    }
    private static void finger(EffectMesh m,HeavenClient.Cast s,float a) {
        if(a>=HeavenTimeline.PORTAL+4)return;
        Vec3d tip=HeavenActor.fingerTip(s.origin,HeavenScene.actorYaw(s.origin,s.target,s.yaw,a),a);
        float alpha=HeavenTimeline.ease(a,2,5)*(1-HeavenTimeline.ease(a,24,32));
        float flare=SpellTimeline.pulse(a,22,4);
        m.star(tip,.15+flare*.65,0xFFD465,alpha);
        Vec3d right=new Vec3d(Math.cos(Math.toRadians(s.yaw)),0,Math.sin(Math.toRadians(s.yaw)));
        double length=.20+HeavenTimeline.ease(a,3,16)*.9+flare*1.4;
        m.glowLine(tip.subtract(right.multiply(length)),tip.add(right.multiply(length)),.017,0xFFD278,alpha);
        m.glowLine(tip.add(0,-.16-flare*.35,0),tip.add(0,.16+flare*.35,0),.019,0xFFF0AC,alpha);
    }
    private static Vec3d portalPoint(HeavenClient.Cast s,float age,double angle,double radius,double depth) {
        var matrix=HeavenScene.cloudTransform(s.origin,s.target,s.yaw,age,0);
        var p=new org.joml.Vector3f((float)(Math.cos(angle)*radius),(float)(Math.sin(angle)*radius),(float)depth).mulPosition(matrix);
        return new Vec3d(p.x,p.y,p.z);
    }
    private static void portal(EffectMesh m,HeavenClient.Cast s,float age) {
        float alpha=HeavenTimeline.portal(age);if(alpha<=0)return;
        // Uneven tapered arcs follow circulation; no straight wheel spokes.
        int segments=quality(24,40);
        for(int arm=0;arm<7;arm++)for(int i=0;i<segments;i++) {
            double t=i/(double)segments,tt=(i+1)/(double)segments;
            double p=arm*.897+Math.sin(arm*3.2)*.1+t*.7,q=arm*.897+Math.sin(arm*3.2)*.1+tt*.7;
            double r=108+45*t,rr=108+45*tt;
            float taper=(float)Math.sin(Math.PI*t);
            m.glowLine(portalPoint(s,age,p,r,52),portalPoint(s,age,q,rr,52),1.4+3*taper,
                0xCBEAFF,alpha*taper*.32f);
        }
    }
    private static void bladeGlow(EffectMesh m,HeavenClient.Cast s,float a) {
        if(a<HeavenTimeline.REVEAL)return;float fade=HeavenTimeline.visibility(a)*HeavenTimeline.reveal(a);
        double exposed=HeavenBlade.exposedHeight(a);
        for(int half:new int[]{-1,1})for(int i=0;i<LEVELS.length-1;i++) {
            if(LEVELS[i]>=exposed)continue;double top=Math.min(exposed,LEVELS[i+1]);
            Vec3d p=point(s,a,half*HeavenTimeline.halfWidth(LEVELS[i]),LEVELS[i],.4),q=point(s,a,half*HeavenTimeline.halfWidth(top),top,.4);
            m.glowLine(p,q,1.8,0xFFC557,fade*.7f);
        }
        for(int i=0;i<quality(12,28);i++) {
            double x=(i%2==0?1:-1)*(10+i%7*7),y=20+(a*4+i*31)%660;
            if(y>exposed)continue;
            m.star(point(s,a,x,y,HeavenBlade.faceDepth(x,y)+.6),1.1+i%3,0xFFE6B0,fade*.55f);
        }
        if(a>=HeavenTimeline.DESCENT && a<HeavenTimeline.IMPACT)for(int i=0;i<18;i++) {
            double x=(i-8.5)*9;
            m.glowLine(point(s,a,x,90,15),point(s,a,x,Math.min(660,exposed),15),.5+i%3,0xFFC65C,HeavenTimeline.rush(a)*.3f);
        }
    }
    private static void impact(EffectMesh m,HeavenClient.Cast s,float a) {
        if(a<HeavenTimeline.IMPACT)return;
        float t=a-HeavenTimeline.IMPACT,fade=HeavenImpact.fade(a);
        if(fade<=0)return;
        for(int wave=0;wave<3;wave++) {
            double p=HeavenImpact.waveProgress(a,wave),r=(160+wave*20)*p;
            if(p>0 && p<1)for(int i=0;i<128;i++) {
                double u=i*Math.PI/64,v=(i+1)*Math.PI/64;
                Vec3d from=ImpactEffects.surfacePoint(s.fx,s.target.add(Math.cos(u)*r,.4,Math.sin(u)*r));
                Vec3d to=ImpactEffects.surfacePoint(s.fx,s.target.add(Math.cos(v)*r,.4,Math.sin(v)*r));
                m.glowLine(from,to,2.5+8*(1-p),wave==0?0xFFE6A3:0xDB9F54,fade*(float)(1-p));
            }
        }
        for(int i=0;i<quality(22,52);i++) {
            double az=i*2.39996,y=(35+i%9*8)*HeavenTimeline.ease(t,0,6)*(.55+.45*HeavenTimeline.pressure(a));
            Vec3d base=ImpactEffects.surfacePoint(s.fx,HeavenImpact.emitter(s.target,s.yaw,a,az,i%5*2));
            Vec3d end=base.add(Math.cos(az)*18,y,Math.sin(az)*18);
            m.glowLine(base,end,1+i%4, i%3==0?0xFFEA9A:0xFFAD45,fade*(.45f+.15f*(float)Math.sin(a*.13+i)));
        }
        for(int i=0;i<quality(35,100);i++) {
            double life=t-(i%20)*.7;if(life<0)continue;
            double cycle=Math.floor(life/40);if(HeavenTimeline.IMPACT+(i%20)*.7+cycle*40>HeavenTimeline.EMBED_END)continue;
            double p=(life%40)/40,az=i*2.39996;
            Vec3d base=ImpactEffects.surfacePoint(s.fx,HeavenImpact.emitter(s.target,s.yaw,a,az,0));
            m.star(base.add(Math.cos(az)*p*100,Math.sin(p*Math.PI)*(20+i%11*6),Math.sin(az)*p*100),
                .4+i%3*.25,0xFFD98A,fade*(float)Math.sin(p*Math.PI));
        }
    }
}
