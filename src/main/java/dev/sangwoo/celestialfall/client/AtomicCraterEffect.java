package dev.sangwoo.celestialfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.*;

/** Optical depression, fractured rim and lifted terrain fragments. Never edits a world block. */
final class AtomicCraterEffect {
    private AtomicCraterEffect(){}
    private static double radius(AtomicClient.Cast c){return Math.max(6,Math.min(16,c.radius*.35));}
    private static float fade(float age){return AtomicTimeline.ease(age,128,134)*(1-AtomicTimeline.ease(age,436,468));}
    private static Vec3d p(Vec3d o,double r,double angle,double y){return o.add(Math.cos(angle)*r,y,Math.sin(angle)*r);}
    static void surface(EffectMesh m,AtomicClient.Cast c,float age){
        float alpha=fade(age);if(alpha<=0)return;
        double radius=radius(c),spread=AtomicTimeline.ease(age,128,140);
        for(int band=0;band<7;band++)for(int j=0;j<64;j++){
            double a=j*Math.PI/32,b=(j+1)*Math.PI/32,ri=1.2+(radius-1.2)*band/7,ro=1.2+(radius-1.2)*(band+1)/7;
            double hi=.035+Math.pow(band/7.,3)*.38*spread,ho=.035+Math.pow((band+1)/7.,3)*.38*spread;
            int[] colors={0x24202c,0x343039,0x49434d,0x5e5861,0x79727d,0x918995,0xada8ae};
            m.quad(p(c.origin,ri,a,hi),p(c.origin,ri,b,hi),p(c.origin,ro,b,ho),p(c.origin,ro,a,ho),colors[band],alpha);
            if(j%3==0){Vec3d start=p(c.origin,ri,a,hi+.008),end=p(c.origin,ro,a+.012*Math.sin(j),ho+.008);m.line(start,end,.035+band*.009,0x120e1a,alpha*.9f);}
        }
    }
    static void blocks(WorldRenderContext context){
        var mc=MinecraftClient.getInstance();var matrices=context.matrixStack();var vertices=mc.getBufferBuilders().getEntityVertexConsumers();boolean drew=false;
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        for(var c:AtomicClient.CASTS.values()){
            float age=c.age(context.tickDelta()),alpha=fade(age);if(alpha<=0)continue;
            float rise=AtomicTimeline.ease(age,128,140)*(1-AtomicTimeline.ease(age,436,468));
            double radius=radius(c);int count=CelestialFall.CONFIG.visualQuality==0?18:28;
            for(int i=0;i<count;i++){
                double angle=i*Math.PI*2/count,r=radius*(.91+(i%3)*.035),x=c.origin.x+Math.cos(angle)*r,z=c.origin.z+Math.sin(angle)*r;
                var state=mc.world.getBlockState(BlockPos.ofFloored(x,c.origin.y-.25,z));
                if(state.isAir())state=net.minecraft.block.Blocks.STONE.getDefaultState();
                matrices.push();matrices.translate(x,c.origin.y-.18+rise*(.15+(i%4)*.06),z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)-angle));matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((15+(i%4)*9)*rise));
                matrices.scale(1.25f,.40f,.85f);matrices.translate(-.5,0,-.5);
                mc.getBlockRenderManager().renderBlockAsEntity(state,matrices,vertices,0xF000F0,OverlayTexture.DEFAULT_UV);matrices.pop();drew=true;
            }
        }
        if(drew)vertices.draw();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
    }
}
