package dev.sangwoo.celestialfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** Curved energy volume and angular circuitry, all rendered without touching blocks. */
public final class AtomicRenderer {
    private static final Vec3d X=new Vec3d(1,0,0),Y=new Vec3d(0,1,0),Z=new Vec3d(0,0,1);
    private AtomicRenderer() {}
    public static void render(WorldRenderContext c) {
        var mc=MinecraftClient.getInstance();if(mc.world==null || AtomicClient.CASTS.isEmpty())return;
        var primary=AtomicClient.cinematic();if(primary==null)primary=AtomicClient.nearest();
        if(primary==null)return;
        float age=primary.age(c.tickDelta());
        Vec3d focus=primary.origin.add(0,age<AtomicTimeline.IMPACT?1.4:primary.radius*.25,0).subtract(c.camera().getPos());
        Vector4f clip=new Vector4f((float)focus.x,(float)focus.y,(float)focus.z,1)
            .mul(c.matrixStack().peek().getPositionMatrix()).mul(c.projectionMatrix());
        AnimePostProcessor.focus(clip.w>.01f?clip.x/clip.w*.5f+.5f:.5f,clip.w>.01f?clip.y/clip.w*.5f+.5f:.5f);
        if(CelestialFallClient.SPELLS.isEmpty())ImpactEffects.renderBlocks(c);
        // Keep the real player visible in the ordinary gameplay view.
        var matrices=c.matrixStack();var camera=c.camera().getPos();var buffer=Tessellator.getInstance().getBuffer();
        matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);
        try {
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableCull();RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.setShaderColor(1,1,1,1);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            EffectMesh volume=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            for(var s:AtomicClient.CASTS.values()) {
                if(!s.dimension.equals(mc.world.getRegistryKey().getValue()))continue;
                if(c.camera().getPos().subtract(s.origin).horizontalLength()>AtomicTimeline.blastRadius(s.age(c.tickDelta()),s.radius)*1.05)AtomicColumnGeometry.body((a,b,d,e,rgb,alpha)->volume.quad(a.add(s.origin),b.add(s.origin),d.add(s.origin),e.add(s.origin),rgb,alpha),s.age(c.tickDelta()),s.radius,CelestialFall.CONFIG.visualQuality==0);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            EffectMesh haze=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            for(var s:AtomicClient.CASTS.values()) {
                if(!s.dimension.equals(mc.world.getRegistryKey().getValue()))continue;
                AtomicColumnGeometry.halo((a,b,d,e,rgb,alpha)->haze.quad(a.add(s.origin),b.add(s.origin),d.add(s.origin),e.add(s.origin),rgb,alpha),s.age(c.tickDelta()),s.radius,CelestialFall.CONFIG.visualQuality==0);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.setShaderColor(1,1,1,1);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            EffectMesh mesh=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            for(var s:AtomicClient.CASTS.values()) {
                if(!s.dimension.equals(mc.world.getRegistryKey().getValue()))continue;
                float a=s.age(c.tickDelta());charge(mesh,s,a);pressure(mesh,s,a);detonate(mesh,s,a);
                // Atomic supplies its own arena-sized glow; the mountain glow belongs to swords.
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            if(CelestialFallClient.SPELLS.isEmpty())ImpactEffects.renderSmoke(c,matrices);
            // Smoke and silhouetted debris use ordinary alpha, never additive black.
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            EffectMesh dark=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            for(var s:AtomicClient.CASTS.values()){darkDebris(dark,s,s.age(c.tickDelta()),camera);AtomicCraterEffect.surface(dark,s,s.age(c.tickDelta()));}
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            AtomicCraterEffect.blocks(c);
        } finally {
            matrices.pop();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
        }
    }
    private static int quality(int low,int high) { return CelestialFall.CONFIG.visualQuality==0?low:high; }
    private static void charge(EffectMesh m,AtomicClient.Cast s,float a) {
        if(a>=AtomicTimeline.IMPACT)return;
        float v=AtomicTimeline.ease(a,0,6)*(1-AtomicTimeline.ease(a,AtomicTimeline.IMPACT-3,AtomicTimeline.IMPACT));
        double radius=16;int tracks=quality(48,96);
        for(int i=0;i<tracks;i++){
            double az=i*Math.PI*2/tracks;Vec3d prev=s.origin.add(Math.cos(az)*.7,.04,Math.sin(az)*.7);
            for(int j=1;j<=9;j++){
                double rad=.7+j*radius/9,angle=az+(j%3==1?.055:j%3==2?-.045:0);
                Vec3d next=s.origin.add(Math.cos(angle)*rad,.045,Math.sin(angle)*rad);
                // Orthogonal PCB elbows, rather than a fan of near-straight rays.
                Vec3d elbow=i%2==0?new Vec3d(next.x,prev.y,prev.z):new Vec3d(prev.x,prev.y,next.z);
                m.glowLine(prev,elbow,.023,i%3==0?0xffffff:0xcda0ff,v);m.glowLine(elbow,next,.023,i%3==0?0xffffff:0xcda0ff,v);prev=next;
            }
            if(i%2==0){
                Vec3d ground=prev;double h=12+(i%7)*1.4;
                for(int j=1;j<=7;j++){
                    double angle=az+(j%3==0?.05:j%3==1?-.035:0);Vec3d next=s.origin.add(Math.cos(angle)*radius,h*j/7,Math.sin(angle)*radius);
                    Vec3d elbow=new Vec3d(prev.x,next.y,prev.z);m.glowLine(prev,elbow,.032,0xe4c6ff,v*.9f);m.glowLine(elbow,next,.032,0xe4c6ff,v*.9f);prev=next;
                }
                double y=(a*.12+i*.83)%5;Vec3d star=ground.add(0,y,0);m.glowLine(star.add(0,-.6,0),star.add(0,1.3,0),.055,0xf9e1ff,v);m.star(star,.23,0xf1d0ff,v);
            }
        }
        for(int i=0;i<quality(18,44);i++){
            double az=i*2.399963,rad=2+i%13;Vec3d point=s.origin.add(Math.cos(az)*rad,(a*.055+i*.31)%4.2,Math.sin(az)*rad);
            m.star(point,.07+(i%4)*.035,0xf3caff,v);m.glowLine(point.add(0,-.25,0),point.add(0,.6,0),.022,0xb885ff,v*.7f);
        }
        if(a>=AtomicTimeline.SILENCE && a<AtomicTimeline.INK_START) {
            float fadeSlash=(1-AtomicTimeline.ease(a,AtomicTimeline.SILENCE+6,AtomicTimeline.INK_START));
            m.glowLine(s.origin.add(-18,-2,-2),s.origin.add(18,7,2),.018,0xBA91FF,fadeSlash);
        }
    }
    private static void pressure(EffectMesh m,AtomicClient.Cast s,float a) {
        if(a<116 || a>=AtomicTimeline.IMPACT)return;
        float p=AtomicTimeline.ease(a,116,AtomicTimeline.IMPACT);
        double r=s.radius*(1-.83*p);
        for(int i=0;i<3;i++)m.ring(s.origin.add(0,.12+i*.025,0),X,Z,r+i*.5,72,1,.08+p*.18,
            0xAD43FF,p*(.25f+i*.08f),0);
    }
    private static void detonate(EffectMesh m,AtomicClient.Cast s,float a) {
        AtomicColumnGeometry.energy((p,q,r,t,rgb,alpha)->m.quad(p.add(s.origin),q.add(s.origin),r.add(s.origin),t.add(s.origin),rgb,alpha),a,s.radius,CelestialFall.CONFIG.visualQuality==0);
    }
    private static void puff(EffectMesh m,Vec3d p,Vec3d camera,double size,int color,float alpha) {
        Vec3d normal=camera.subtract(p).normalize(),right=normal.crossProduct(Y).normalize();
        if(right.lengthSquared()<.01)right=X;
        Vec3d up=right.crossProduct(normal).normalize();
        // A radial alpha gradient gives a soft smoke silhouette without spawning particles.
        for(int i=0;i<16;i++) {
            double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
            m.vertex(p,color,alpha);m.vertex(p,color,alpha);
            m.vertex(p.add(right.multiply(Math.cos(a)*size)).add(up.multiply(Math.sin(a)*size)),color,0);
            m.vertex(p.add(right.multiply(Math.cos(b)*size)).add(up.multiply(Math.sin(b)*size)),color,0);
        }
    }
    private static void darkDebris(EffectMesh m,AtomicClient.Cast s,float a,Vec3d camera) {
        if(a>=116 && a<AtomicTimeline.IMPACT && CelestialFall.CONFIG.explosionSmoke) {
            float p=AtomicTimeline.ease(a,116,AtomicTimeline.IMPACT);
            for(int i=0;i<quality(24,52);i++) {
                double az=i*2.3999,r=5+p*(s.radius-5);
                puff(m,s.origin.add(Math.cos(az)*r,1+p*(3+i%7),Math.sin(az)*r),camera,s.radius*(.07+p*.08),0x373444,p*.65f);
            }
        }
        if(a<AtomicTimeline.IMPACT || a>=AtomicTimeline.END)return;
        float fade=AtomicTimeline.column(a);double radius=AtomicTimeline.blastRadius(a,s.radius),t=a-AtomicTimeline.IMPACT;
        if(CelestialFall.CONFIG.explosionSmoke)for(int i=0;i<quality(48,110);i++) {
            double az=i*2.399963+t*.001,r=radius*(1.01+.075*Math.sin(i*3.17));
            double y=s.radius*(.025+.075*Math.abs(Math.sin(i*1.91+t*.02)));
            puff(m,s.origin.add(Math.cos(az)*r,y,Math.sin(az)*r),camera,radius*(.075+i%4*.016),
                i%3==0?0x221C31:0x080612,fade*.96f);
        }
        if(CelestialFall.CONFIG.flyingBlocks)for(int i=0;i<quality(55,160);i++) {
            double az=i*2.399963+t*.0008,r=radius*(.92+(i%13)/45.);
            double life=(t*.007+i*.618)%1,y=life*AtomicTimeline.columnHeight(s.radius)*.85;
            double size=radius*(.006+(i%5)*.0025);
            Vec3d p=s.origin.add(Math.cos(az)*r,y,Math.sin(az)*r);
            // Render-only rock silhouettes; no FallingBlockEntity, block deletion, or dropped items.
            m.box(p,-size,-size*.7,-size*.6,size,size*.7,size*.6,0x100A1D,fade*(float)Math.sin(life*Math.PI));
        }
    }
}
