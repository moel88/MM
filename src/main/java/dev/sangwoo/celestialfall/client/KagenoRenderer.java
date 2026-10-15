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
                    case M1->KagenoStrikeEffects.basic(m,center,f,right,a,s.combo(),false);
                    case E->{
                        KagenoStrikeEffects.thrusts(m,center.subtract(right.multiply(.3)),f,right,a,false);
                        if(a>=50&&a<63){float t=(a-50)/13;crescent(m,center.add(f.multiply(.7)),UP,f,3.2,t,1-t,false,3.2);glint(m,center.add(f.multiply(1.7)).add(0,2.8,0),camera,.8,1-t);}
                    }
                    case R->{
                        KagenoStrikeEffects.stoppedCuts(m,s.origin(),f,right,a,false);
                        KagenoVfx.shock(m,pos.add(0,.08,0),a-36,15,4.6);
                    }
                    case T->{if(a<T_STRIKE)windup(m,center,camera,a);KagenoVfx.shock(m,s.origin().add(0,.1,0),a-T_STRIKE,10,4.5);KagenoVfx.burst(m,center,right,UP,a-T_STRIKE,7,2.0,12);}
                    case RUSH->{
                        if(a<RUSH_IGNITE)windup(m,center,camera,a);
                        KagenoStrikeEffects.rush(m,pos,f,right,a,false);
                    }
                    case Y->{if(a>=20){float t=a-20,alpha=1-KagenoMotion.smooth(a,32,54);
                        for(int j=0;j<3;j++){double r=Math.min(5,1+t*.5+j*.3);crescent(m,s.origin().add(0,.2+j*.4,0),X,Z,r,(t*.04f+j*.3f)%1,alpha*(1-j*.2f),false,5.7);}
                        sparks(m,s.origin().add(0,.6,0),right,UP,t,4.5,alpha,26);
                        KagenoVfx.shock(m,s.origin().add(0,.1,0),t,22,5.4);KagenoVfx.burst(m,s.origin().add(0,.4,0),right,UP,t,18,4,24);
                    }}
                    case H->{
                        KagenoStrikeEffects.landingSlide(m,s.origin(),f,right,a);
                        if(a>=32&&a<54){float alpha=KagenoMotion.smooth(a,32,36)*(1-KagenoMotion.smooth(a,50,54));glint(m,s.origin().add(0,70,0),camera,55.0,alpha);}
                        if(a>=47&&a<58){
                            Vec3d[] points={s.origin().add(right.multiply(-17)).add(f.multiply(-17)).add(0,70,0),s.origin().add(right.multiply(17)).add(f.multiply(-10)).add(0,70,0),s.origin().add(right.multiply(-14)).add(f.multiply(1)).add(0,70,0),s.origin().add(right.multiply(17)).add(f.multiply(10)).add(0,70,0),s.origin().add(right.multiply(-17)).add(f.multiply(17)).add(0,70,0),s.origin().add(0,1,0)};
                            for(int j=0;j<5;j++){float t=KagenoMotion.unit(a,47+j*1.4f,49+j*1.4f);if(t>0)m.glowLine(points[j],points[j].lerp(points[j+1],t),.32,0xf748ff,1-KagenoMotion.smooth(a,55,58));}
                        }
                        KagenoStrikeEffects.highest(m,s.origin(),f,right,a,false);
                        if(a>=96){float t=(a-96)/20;Vec3d behind=s.origin().subtract(f.multiply(3.5));
                            crescent(m,behind.add(0,.45,0),right,f,2.9+t*.7,t,1-t,false,5.7);sparks(m,behind.add(0,.5,0),right,UP,a,3.4,1-t,18);
                        }
                    }
                }
            }
            for(var p:mc.world.getPlayers())if(StandClient.visible(p)&&CombatClient.snapshot(p.getUuid()).guard()){
                Vec3d o=p.getLerpedPos(c.tickDelta()).add(0,1.02,0),view=camera.subtract(o).normalize();
                for(int y=0;y<16;y++)for(int x=0;x<32;x++){
                    double t0=-Math.PI/2+y*Math.PI/16,t1=t0+Math.PI/16,a0=x*Math.PI/16,a1=a0+Math.PI/16;
                    Vec3d n0=new Vec3d(Math.cos(t0)*Math.cos(a0),Math.sin(t0),Math.cos(t0)*Math.sin(a0)),n1=new Vec3d(Math.cos(t0)*Math.cos(a1),Math.sin(t0),Math.cos(t0)*Math.sin(a1)),n2=new Vec3d(Math.cos(t1)*Math.cos(a1),Math.sin(t1),Math.cos(t1)*Math.sin(a1)),n3=new Vec3d(Math.cos(t1)*Math.cos(a0),Math.sin(t1),Math.cos(t1)*Math.sin(a0));
                    float edge=(float)Math.pow(1-Math.abs(n0.add(n2).normalize().dotProduct(view)),5);m.quad(o.add(n0.multiply(1.1)),o.add(n1.multiply(1.1)),o.add(n2.multiply(1.1)),o.add(n3.multiply(1.1)),0xe5f7ff,.025f+edge*.6f);
                }
            }
            for(var entry:StandClient.STATES.entrySet()){
                if(entry.getValue().until()<=mc.world.getTime())continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
                Vec3d origin=p.getLerpedPos(c.tickDelta());if(origin.squaredDistanceTo(camera)>64*64)continue;float a=mc.world.getTime()%2000+c.tickDelta();
                // Thin flowing contour ribbons remain readable in daylight without covering the skin.
                for(int j=0;j<6;j++)for(int k=0;k<14;k++){
                    double t=k/14.,q=(k+1)/14.,angle=j*Math.PI/3+a*.012,flow=(a*.012+j*.173)%1;
                    double y=.08+t*1.85,y2=.08+q*1.85,r=.48+.06*Math.sin(t*5+a*.06+j),r2=.48+.06*Math.sin(q*5+a*.06+j);
                    Vec3d start=origin.add(Math.cos(angle+t*.7)*r,y,Math.sin(angle+t*.7)*r),end=origin.add(Math.cos(angle+q*.7)*r2,y2,Math.sin(angle+q*.7)*r2);
                    float opacity=(float)(Math.sin(Math.PI*t)*(.25+.75*Math.pow(.5+.5*Math.cos((t-flow)*Math.PI*2),4)));
                    m.glowLine(start,end,.012,0xc468ff,opacity*.65f);
                }
                for(int j=0;j<10;j++){float life=(a*.025f+j*.618f)%1;double angle=j*2.39996+a*.012;
                    Vec3d spark=origin.add(Math.cos(angle)*(.5+life*.3),.15+life*2.25,Math.sin(angle)*(.5+life*.3));
                    m.glowLine(spark,spark.add(0,.18+life*.2,0),.018,0xc758ff,(float)Math.sin(life*Math.PI)*.7f);
                }
            }
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
                    case M1->KagenoStrikeEffects.basic(m,pos.add(0,1.1,0),f,right,a,s.combo(),true);
                    case E->KagenoStrikeEffects.thrusts(m,pos.add(0,1.1,0).subtract(right.multiply(.3)),f,right,a,true);
                    case R->KagenoStrikeEffects.stoppedCuts(m,s.origin(),f,right,a,true);
                    case H->KagenoStrikeEffects.highest(m,s.origin(),f,right,a,true);
                    case RUSH->KagenoStrikeEffects.rush(m,pos,f,right,a,true);
                }
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.defaultBlendFunc();
            // Separate shader passes prevent blended fire from becoming flat polygon ribbons.
            for(var s:StandClient.CASTS.values()){
                float a=s.age(c.tickDelta());var owner=mc.world.getPlayerByUuid(s.caster());Vec3d pos=owner==null?s.origin():owner.getLerpedPos(c.tickDelta());Vec3d f=Vec3d.fromPolar(0,s.yaw());
                if(pos.squaredDistanceTo(camera)>80*80)continue;
                if(s.skill()==T&&a>=T_STRIKE)KagenoFire.field(matrices.peek().getPositionMatrix(),camera,s.origin(),a-T_STRIKE,4,1-KagenoMotion.smooth(a,88,104),true);
                if(s.skill()==RUSH&&a>=RUSH_IGNITE&&a<RUSH_START+3)KagenoFire.field(matrices.peek().getPositionMatrix(),camera,s.origin(),a-RUSH_IGNITE,3.6,1-KagenoMotion.smooth(a,23,RUSH_START+3),false);
                if(s.skill()==RUSH&&a>=RUSH_START)KagenoFire.rush(matrices.peek().getPositionMatrix(),camera,pos,f,a-RUSH_START,1-KagenoMotion.smooth(a,RUSH_FINISH,66));
            }
            long now=mc.world.getTime();
            for(var entry:StandClient.STATES.entrySet()){
                var s=entry.getValue();if(s.until()<=now)continue;var p=mc.world.getPlayerByUuid(entry.getKey());if(p==null||!StandClient.visible(p))continue;
                Vec3d origin=p.getLerpedPos(c.tickDelta()),f=Vec3d.fromPolar(0,p.bodyYaw),right=new Vec3d(f.z,0,-f.x);float age=now+c.tickDelta();
                if(origin.squaredDistanceTo(camera)>64*64)continue;
                KagenoFire.overdrive(matrices.peek().getPositionMatrix(),camera,origin,age,.85f);
            }
            rubble(c);
            // Target mark is owner-only and visible through walls.
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.disableDepthTest();
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);m=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            if(mc.player!=null){var local=StandClient.STATES.get(mc.player.getUuid());if(local!=null)for(var mark:local.marks())if(mark.until()>now){var e=mc.world.getEntityById(mark.entity());if(e!=null&&e.getUuid().equals(mark.id())&&e.isAlive())glint(m,e.getLerpedPos(c.tickDelta()).add(0,e.getHeight()+.35,0),camera,.17,.85f);}}
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }finally{matrices.pop();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);}
    }
    private static Vec3d radial(Vec3d o,Vec3d u,Vec3d v,double angle,double r){return o.add(u.multiply(Math.sin(angle)*r)).add(v.multiply(Math.cos(angle)*r));}
    /** Tapered crescent with a black spine, saturated violet rim and broad incandescent center. */
    private static void crescent(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double r,float phase,float alpha,boolean reverse,double length){
        crescent(m,o,u,v,r,phase,alpha,reverse,length,false);
    }
    private static void crescent(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,double r,float phase,float alpha,boolean reverse,double length,boolean luminous){
        if(alpha<=0)return;double sign=reverse?-1:1,head=sign*(-2.6+phase*5.8);
        int segments=CelestialFall.CONFIG.visualQuality==0?28:44;double[] edges=luminous?new double[]{0,.06,.16,.44,.61,.68}:new double[]{0,.16,.28,.44,.56,.62};int[] colors=luminous?new int[]{0x7c13e5,0xd75dff,0xfbc8ff,0xfff7ff,0xffffff}:new int[]{0x10071f,0x6100c7,0xc548ff,0xffeaff,0xffffff};
        for(int i=0;i<segments;i++){
            double t=i/(double)segments,q=(i+1)/(double)segments,angle=head-sign*t*length,next=head-sign*q*length;
            double taper=Math.pow(Math.sin(Math.PI*t),.65),taper2=Math.pow(Math.sin(Math.PI*q),.65);
            for(int j=0;j<colors.length;j++){
                double outer=r-edges[j]*taper,inner=r-edges[j+1]*taper,outer2=r-edges[j]*taper2,inner2=r-edges[j+1]*taper2;
                m.quad(radial(o,u,v,angle,outer),radial(o,u,v,next,outer2),radial(o,u,v,next,inner2),radial(o,u,v,angle,inner),colors[j],alpha*(float)(.3+.7*Math.sin(Math.PI*(t*.85+.1))));
            }
            if(i%2==0){Vec3d a=radial(o,u,v,angle,r+.14),b=radial(o,u,v,next+sign*.04,r+.14);m.glowLine(a,b,.018,0xe8d5ff,alpha*.75f);}
        }
        // Fine surrounding wind ribbons, offset from the solid sword path.
        for(int j=0;j<2;j++)m.ring(o.add(0,j==0?.1:-.15,0),u,v,r+.22+j*.15,64,.66f,.017,0xf5edff,alpha*.55f,head-sign*length*.7);
    }
    private static void blade(EffectMesh m,Vec3d p,Vec3d axis,double length,double width,int color,float alpha){
        Vec3d normal=axis.crossProduct(new Vec3d(.13,.5,1)).normalize();Vec3d a=p.add(axis.multiply(length)),b=p.subtract(axis.multiply(length));
        m.triangle(a,p.add(normal.multiply(width)),p.subtract(normal.multiply(width)),color,alpha);m.triangle(b,p.subtract(normal.multiply(width)),p.add(normal.multiply(width)),color,alpha);m.glowLine(a,b,width*.35,color,alpha*.6f);
    }
    private static void windup(EffectMesh m,Vec3d p,Vec3d camera,float a){for(int i=0;i<7;i++){double theta=i*2.4+a*.12;glint(m,p.add(Math.sin(theta)*(.6+i*.08),Math.cos(theta)*(.65+i*.04),Math.cos(theta)*.4),camera,.10+.08*Math.sin(a*.2+i),KagenoMotion.smooth(a,0,5));}}
    private static void sparks(EffectMesh m,Vec3d o,Vec3d u,Vec3d v,float a,double radius,float alpha,int count){
        for(int i=0;i<count;i++){double angle=i*2.39996+a*.025,r=radius*(.3+((i*.618+a*.018)%1)),len=.07+.28*(.5+.5*Math.sin(i*3.7));Vec3d axis=u.multiply(Math.cos(angle)).add(v.multiply(Math.sin(angle)));Vec3d p=o.add(axis.multiply(r)).add(0,Math.sin(i*7)*.22,0);blade(m,p,axis,len,.025,0xfbd3ff,alpha*.8f);}
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
        for(var cast:StandClient.CASTS.values())if(cast.skill()==Y){float a=cast.age(c.tickDelta());if(a<20||a>=58)continue;
            float rise=KagenoMotion.smooth(a,20,24)*(1-KagenoMotion.smooth(a,46,58));
            var state=mc.world.getBlockState(BlockPos.ofFloored(cast.origin()).down());if(state.isAir())state=net.minecraft.block.Blocks.STONE.getDefaultState();
            for(int i=0;i<11;i++){
                double angle=i*Math.PI*2/11,r=3.4+(i%3)*.15;matrix.push();matrix.translate(cast.origin().x+Math.cos(angle)*r,cast.origin().y-.45+(rise-.1)*.7,cast.origin().z+Math.sin(angle)*r);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotation((float)-angle));matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((18+i%3*13)*rise));
                matrix.scale(1.8f,.55f+(i%3)*.24f,1.3f);matrix.translate(-.5,0,-.5);
                mc.getBlockRenderManager().renderBlockAsEntity(state,matrix,providers,0xF000F0,OverlayTexture.DEFAULT_UV);matrix.pop();drew=true;
            }
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
