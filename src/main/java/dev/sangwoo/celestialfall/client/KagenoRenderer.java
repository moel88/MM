package dev.sangwoo.celestialfall.client;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** Per-skill reference geometry. All motion and ribbons use the cast clock, including late viewers. */
public final class KagenoRenderer {
    private static final Vec3d X=new Vec3d(1,0,0),UP=new Vec3d(0,1,0),Z=new Vec3d(0,0,1);
    private KagenoRenderer(){}
    public static void render(WorldRenderContext c){
        var mc=MinecraftClient.getInstance();if(mc.world==null||!MapClient.enabled()||StandClient.CASTS.isEmpty()&&StandClient.STATES.isEmpty())return;
        var matrices=c.matrixStack();var camera=c.camera().getPos();matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);
        var primary=StandClient.visualCast();
        if(primary!=null&&AtomicClient.CASTS.isEmpty()&&HeavenClient.CASTS.isEmpty()){
            Vec3d focus=primary.origin().add(0,primary.skill()==H?1+KagenoMotion.lift(primary.age(c.tickDelta())):1.1,0);
            var clip=new org.joml.Vector4f((float)focus.x,(float)focus.y,(float)focus.z,1).mul(matrices.peek().getPositionMatrix()).mul(c.projectionMatrix());
            AnimePostProcessor.focus(clip.w>.01f?clip.x/clip.w*.5f+.5f:.5f,clip.w>.01f?clip.y/clip.w*.5f+.5f:.5f);
        }
        var buffer=Tessellator.getInstance().getBuffer();
        try{
            RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.setShaderColor(1,1,1,1);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var m=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            for(var s:StandClient.CASTS.values()){
                float a=s.age(c.tickDelta());if(a<0||a>=DURATION[s.skill()])continue;
                var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(c.tickDelta());
                if(pos.squaredDistanceTo(camera)>80*80&&s!=StandClient.cinematic())continue;
                Vec3d f=Vec3d.fromPolar(0,s.yaw()),right=new Vec3d(f.z,0,-f.x),center=pos.add(0,1.1,0);
                switch(s.skill()){
                    case M1->KagenoFx.m1(m,pos,f,right,a,s.combo(),false);
                    case E->KagenoFx.e(m,pos,f,right,a,false);
                    case R->KagenoFx.r(m,s.origin(),f,right,a,false);
                    case T->KagenoFx.t(m,pos,s.origin(),f,right,a,targets(mc,s,c.tickDelta()),false);
                    case RUSH->KagenoFx.rush(m,pos,s.origin(),f,right,a,false);
                    case Y->KagenoFx.y(m,s.origin(),f,right,a,false);
                    case H->{
                        KagenoStrikeEffects.landingSlide(m,s.origin(),f,right,a);
                        if(a>=32&&a<54){float alpha=KagenoMotion.smooth(a,32,36)*(1-KagenoMotion.smooth(a,50,54));glint(m,s.origin().add(0,70,0),camera,55.0,alpha);}
                        if(a>=47&&a<58){
                            Vec3d[] points={s.origin().add(right.multiply(-17)).add(f.multiply(-17)).add(0,70,0),s.origin().add(right.multiply(17)).add(f.multiply(-10)).add(0,70,0),s.origin().add(right.multiply(-14)).add(f.multiply(1)).add(0,70,0),s.origin().add(right.multiply(17)).add(f.multiply(10)).add(0,70,0),s.origin().add(right.multiply(-17)).add(f.multiply(17)).add(0,70,0),s.origin().add(0,1,0)};
                            for(int j=0;j<5;j++){float t=KagenoMotion.unit(a,47+j*1.4f,49+j*1.4f);if(t>0)m.glowLine(points[j],points[j].lerp(points[j+1],t),.32,0xf748ff,1-KagenoMotion.smooth(a,55,58));}
                        }
                        KagenoStrikeEffects.highest(m,s.origin(),f,right,a,false);
                        KagenoFx.hFinal(m,s.origin(),f,right,a,false);
                    }
                }
            }
            for(var p:mc.world.getPlayers())if(StandClient.visible(p)&&CombatClient.snapshot(p.getUuid()).guard()){
                Vec3d o=p.getLerpedPos(c.tickDelta()).add(0,.95,0),view=camera.subtract(o).normalize();
                for(int y=0;y<16;y++)for(int x=0;x<32;x++){
                    double t0=-Math.PI/2+y*Math.PI/16,t1=t0+Math.PI/16,a0=x*Math.PI/16,a1=a0+Math.PI/16;
                    Vec3d n0=new Vec3d(Math.cos(t0)*Math.cos(a0),Math.sin(t0),Math.cos(t0)*Math.sin(a0)),n1=new Vec3d(Math.cos(t0)*Math.cos(a1),Math.sin(t0),Math.cos(t0)*Math.sin(a1)),n2=new Vec3d(Math.cos(t1)*Math.cos(a1),Math.sin(t1),Math.cos(t1)*Math.sin(a1)),n3=new Vec3d(Math.cos(t1)*Math.cos(a0),Math.sin(t1),Math.cos(t1)*Math.sin(a0));
                    float edge=(float)Math.pow(1-Math.abs(n0.add(n2).normalize().dotProduct(view)),4);m.quad(o.add(n0.multiply(1.32)),o.add(n1.multiply(1.32)),o.add(n2.multiply(1.32)),o.add(n3.multiply(1.32)),0xf2fbff,.06f+edge*.85f);
                }
            }
            overdrive(m,mc,c.tickDelta(),camera,false);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            // Emit light only at the authored E/R/H shapes; do not raise whole-world exposure.
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            m=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            for(var s:StandClient.CASTS.values()){
                float a=s.age(c.tickDelta());if(a<0||a>=DURATION[s.skill()])continue;
                var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(c.tickDelta());
                if(pos.squaredDistanceTo(camera)>80*80&&s!=StandClient.cinematic())continue;
                Vec3d f=Vec3d.fromPolar(0,s.yaw()),right=new Vec3d(f.z,0,-f.x);
                switch(s.skill()){
                    case M1->KagenoFx.m1(m,pos,f,right,a,s.combo(),true);
                    case E->KagenoFx.e(m,pos,f,right,a,true);
                    case R->KagenoFx.r(m,s.origin(),f,right,a,true);
                    case Y->KagenoFx.y(m,s.origin(),f,right,a,true);
                    case H->{KagenoStrikeEffects.highest(m,s.origin(),f,right,a,true);KagenoFx.hFinal(m,s.origin(),f,right,a,true);}
                    case RUSH->KagenoFx.rush(m,pos,s.origin(),f,right,a,true);
                    case T->KagenoFx.t(m,pos,s.origin(),f,right,a,targets(mc,s,c.tickDelta()),true);
                }
            }
            overdrive(m,mc,c.tickDelta(),camera,true);
            BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.defaultBlendFunc();
            // Separate shader passes prevent blended fire from becoming flat polygon ribbons.
            for(var s:StandClient.CASTS.values()){
                float a=s.age(c.tickDelta());if(a<0||a>=DURATION[s.skill()])continue;var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(c.tickDelta());
                if(pos.squaredDistanceTo(camera)>80*80)continue;Vec3d f=Vec3d.fromPolar(0,s.yaw()),right=new Vec3d(f.z,0,-f.x);
                var pts=new java.util.ArrayList<Vec3d>();var sizes=new java.util.ArrayList<Double>();float alpha=KagenoFx.smoke(pts,sizes,s.skill(),pos,s.origin(),f,right,a,s.combo());
                KagenoFire.smoke(matrices.peek().getPositionMatrix(),camera,pts,sizes,a,alpha);
            }
            for(var s:StandClient.CASTS.values()){
                float a=s.age(c.tickDelta());var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(c.tickDelta());Vec3d f=Vec3d.fromPolar(0,s.yaw());
                if(pos.squaredDistanceTo(camera)>80*80)continue;
                if(s.skill()==T&&a>=T_STRIKE)KagenoFire.field(matrices.peek().getPositionMatrix(),camera,s.origin(),a-T_STRIKE,6.2,1-KagenoMotion.smooth(a,98,112),true);
                if(s.skill()==RUSH&&a>=RUSH_IGNITE&&a<RUSH_START+3)KagenoFire.field(matrices.peek().getPositionMatrix(),camera,s.origin().add(f.multiply(1.2)),a-RUSH_IGNITE,5.2,1-KagenoMotion.smooth(a,24,RUSH_START+3),false);
                if(s.skill()==RUSH&&a>=RUSH_START)KagenoFire.rush(matrices.peek().getPositionMatrix(),camera,pos,f,a-RUSH_START,1-KagenoMotion.smooth(a,RUSH_FINISH,66));
                if(s.skill()==RUSH&&a>=RUSH_FINISH)KagenoFire.field(matrices.peek().getPositionMatrix(),camera,pos.add(f.multiply(1.2)),a-RUSH_FINISH+8,1.7,1-KagenoMotion.smooth(a,RUSH_FINISH+4,62),false);
            }
            long now=mc.world.getTime();
            if(KagenoFire.beginStrokes(matrices.peek().getPositionMatrix(),mc.world.getTime()+c.tickDelta())){
                for(var s:StandClient.CASTS.values()){
                    float a=s.age(c.tickDelta());if(a<0||a>=DURATION[s.skill()])continue;var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(c.tickDelta());
                    if(pos.squaredDistanceTo(camera)>80*80)continue;Vec3d f=Vec3d.fromPolar(0,s.yaw()),right=new Vec3d(f.z,0,-f.x);
                    KagenoFx.strokes(s.skill(),pos,s.origin(),f,right,a,s.combo());
                }
                KagenoFire.endStrokes();
            }
            // Full-frame blast flashes for nearby viewers.
            float flash=0;for(var s:StandClient.CASTS.values()){float a=s.age(c.tickDelta());if(s.origin().squaredDistanceTo(camera)<24*24)flash=Math.max(flash,KagenoFx.flash(s.skill(),a));}
            if(flash>0){RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.disableDepthTest();buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
                var look=c.camera().getHorizontalPlane();Vec3d fwd=new Vec3d(look.x(),look.y(),look.z());var up=c.camera().getVerticalPlane();Vec3d vu=new Vec3d(up.x(),up.y(),up.z());Vec3d side=fwd.crossProduct(vu).normalize();
                Vec3d p=camera.add(fwd.multiply(.2));m.quad(p.add(side.multiply(3)).add(vu.multiply(3)),p.subtract(side.multiply(3)).add(vu.multiply(3)),p.subtract(side.multiply(3)).subtract(vu.multiply(3)),p.add(side.multiply(3)).subtract(vu.multiply(3)),0xfff8ff,flash*.95f);
                BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.enableDepthTest();}
            rubble(c);
            // Target mark is owner-only and visible through walls.
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.disableDepthTest();
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            if(mc.player!=null){var local=StandClient.STATES.get(mc.player.getUuid());if(local!=null)for(var mark:local.marks())if(mark.until()>now){var e=mc.world.getEntityById(mark.entity());if(e!=null&&e.getUuid().equals(mark.id())&&e.isAlive())glint(m,e.getLerpedPos(c.tickDelta()).add(0,e.getHeight()+.35,0),camera,.17,.85f);}}
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }finally{matrices.pop();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);}
    }
    private static void overdrive(EffectMesh m,MinecraftClient mc,float delta,Vec3d camera,boolean light){
        long now=mc.world.getTime();
        for(var entry:StandClient.STATES.entrySet()){
            if(entry.getValue().until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
            Vec3d feet=p.getLerpedPos(delta);if(feet.squaredDistanceTo(camera)>64*64)continue;
            Vec3d f=Vec3d.fromPolar(0,p.getBodyYaw()),right=new Vec3d(f.z,0,-f.x);float t=now+delta;
            KagenoFx.q(m,feet,f,right,t,p.getHeight(),light);
            if(!(p==mc.player&&mc.options.getPerspective().isFirstPerson()))KagenoFx.eye(m,p.getLerpedPos(delta).add(0,p.getStandingEyeHeight(),0),Vec3d.fromPolar(p.getPitch(delta),p.getHeadYaw()),Vec3d.fromPolar(0,p.getHeadYaw()+90).negate(),t,light);
        }
    }
    private static java.util.List<Vec3d> targets(MinecraftClient mc,StandClient.Cast s,float delta){
        var out=new java.util.ArrayList<Vec3d>();for(var t:s.targets()){var e=mc.world.getEntityById(t.entity());out.add(e!=null?e.getLerpedPos(delta):t.position());}
        if(s.skill()==T)for(var e:mc.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,new Box(s.origin(),s.origin()).expand(4.5),e->!e.getUuid().equals(s.caster())&&e.isAlive()&&e.squaredDistanceTo(s.origin())<=16.5))out.add(e.getLerpedPos(delta));
        return out;
    }
    private static void glint(EffectMesh m,Vec3d p,Vec3d camera,double radius,float alpha){
        Vec3d normal=camera.subtract(p).normalize(),u=normal.crossProduct(UP).normalize();if(u.lengthSquared()<.1)u=X;Vec3d v=u.crossProduct(normal).normalize();
        for(int layer=5;layer>=0;layer--){double width=radius*(.018+layer*.025);float opacity=alpha*(layer==0?1:.07f);int color=layer==0?0xffffff:0xf254ff;
            for(int i=0;i<4;i++){Vec3d axis=i==0?u:i==1?v:i==2?u.negate():v.negate(),perp=i%2==0?v:u;m.triangle(p.add(axis.multiply(radius*(1+layer*.1))),p.add(perp.multiply(width)),p.subtract(perp.multiply(width)),color,opacity);}
        }
        for(int i=0;i<32;i++){double a=i*Math.PI/16,b=(i+1)*Math.PI/16;m.triangle(p,p.add(u.multiply(Math.cos(a)*radius*.035)).add(v.multiply(Math.sin(a)*radius*.035)),p.add(u.multiply(Math.cos(b)*radius*.035)).add(v.multiply(Math.sin(b)*radius*.035)),0xffffff,alpha);}
    }
    private static void rubble(WorldRenderContext c){
        var mc=MinecraftClient.getInstance();var matrix=c.matrixStack();var providers=mc.getBufferBuilders().getEntityVertexConsumers();boolean drew=false;
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        for(var cast:StandClient.CASTS.values())if(cast.skill()==Y){float a=cast.age(c.tickDelta());float rise=KagenoFx.plateRise(a);if(rise<=0)continue;
            var origin=cast.origin();var state=mc.world.getBlockState(BlockPos.ofFloored(origin).down());if(state.isAir())state=net.minecraft.block.Blocks.STONE.getDefaultState();
            int light=net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(origin));
            for(int i=0;i<KagenoFx.Y_PLATES.length;i++){
                double[] q=KagenoFx.Y_PLATES[i];double angle=q[0]+cast.yaw()*Math.PI/180,r=q[1];
                // Outer edge lifted, inner edge buried: the opened crater of the reference.
                matrix.push();matrix.translate(origin.x+Math.cos(angle)*r,origin.y-q[5]*.6-.9*(1-rise),origin.z+Math.sin(angle)*r);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotation((float)-angle));matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)(q[4]*rise)));
                matrix.scale((float)q[2],(float)q[5],(float)q[3]);matrix.translate(-.5,0,-.5);
                mc.getBlockRenderManager().renderBlockAsEntity(state,matrix,providers,light,OverlayTexture.DEFAULT_UV);matrix.pop();drew=true;
            }
            // Small chunks thrown up at the feet during the slam.
            float chunk=KagenoMotion.smooth(a,KagenoFx.Y_SLAM,KagenoFx.Y_SLAM+2)*(1-KagenoMotion.smooth(a,KagenoFx.Y_SLAM+10,KagenoFx.Y_SLAM+16));
            if(chunk>0)for(int i=0;i<8;i++){double angle=i*.785+.3,r=.9+.5*KagenoFx.hash(i);float t=a-KagenoFx.Y_SLAM;
                matrix.push();matrix.translate(origin.x+Math.cos(angle)*r*(1+t*.04),origin.y-.25+.5*chunk+Math.max(0,t*.18-t*t*.012),origin.z+Math.sin(angle)*r*(1+t*.04));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotation((float)-angle));matrix.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(35*chunk));matrix.scale(.8f,.3f,.7f);matrix.translate(-.5,0,-.5);
                mc.getBlockRenderManager().renderBlockAsEntity(state,matrix,providers,light,OverlayTexture.DEFAULT_UV);matrix.pop();drew=true;}
        }if(drew)providers.draw();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
    }
    public static void hud(DrawContext draw,float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||!MapClient.enabled()||mc.options.hudHidden)return;
        var cinematic=StandClient.cinematic();int width=draw.getScaledWindowWidth(),height=draw.getScaledWindowHeight();
        if(cinematic!=null)return;
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
