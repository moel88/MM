package dev.sangwoo.celestialfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.SpellTimeline;
import dev.sangwoo.celestialfall.MountainScale;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

public final class CelestialRenderer {
    private static final Vec3d X = new Vec3d(1,0,0), Y = new Vec3d(0,1,0), Z = new Vec3d(0,0,1);
    private static final double[][] STARS = {
        {-0.91,0.12},{-0.65,-0.61},{0.04,-0.93},{0.78,-0.53},
        {0.94,0.25},{0.4,0.88},{-0.41,0.8},{-0.24,-0.17},{0.3,0.1}
    };
    private static final int[][] EDGES = {
        {0,1},{1,2},{2,3},{3,4},{4,5},{5,6},{6,0},
        {0,3},{3,6},{6,2},{2,5},{5,1},{1,4},{4,0},{7,8},{7,2},{8,5}
    };
    private CelestialRenderer() {}
    private static int quality(int low,int medium,int high) {
        return CelestialFall.CONFIG.visualQuality == 0 ? low : CelestialFall.CONFIG.visualQuality == 1 ? medium : high;
    }
    private static int segments() { return quality(48,64,96); }
    private static void begin(boolean additive, boolean depthWrite) {
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(depthWrite);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1,1,1,1);
    }
    private static void end() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1,1,1,1);
    }

    /** Invoked after vanilla sky and before terrain. World time is never changed. */
    public static void renderSky(MatrixStack matrices, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || !CelestialFall.CONFIG.skyEffect) return;
        ClientSpell spell = CelestialFallClient.nearest();
        // G keeps its daytime sky (the reference never darkens it).
        var heaven=HeavenClient.nearest();
        if (spell == null && heaven==null) return;
        float age = spell!=null?spell.age(mc.world.getTime(),tickDelta):heaven.age(tickDelta);
        float visibility = spell!=null?SpellTimeline.visibility(age):dev.sangwoo.celestialfall.HeavenTimeline.visibility(age);
        double distance = mc.player.getPos().distanceTo(spell!=null?spell.target:heaven.target);
        visibility *= (float)Math.max(0,Math.min(1,(640-distance)/240));
        if (visibility <= 0) return;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        try {
            begin(false,false); RenderSystem.disableDepthTest();
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            EffectMesh m = new EffectMesh(buffer,matrices.peek().getPositionMatrix(),Vec3d.ZERO);
            // Hemisphere fades into the vanilla horizon instead of replacing the terrain lighting.
            for(int row=0;row<14;row++) for(int col=0;col<48;col++) {
                double p0=-0.06+row*(Math.PI/2+0.06)/14,p1=-0.06+(row+1)*(Math.PI/2+0.06)/14;
                double a0=col*Math.PI*2/48,a1=(col+1)*Math.PI*2/48;
                float a=visibility*0.98f;
                int rgb=heaven!=null?(row<3?0x354558:0x101E31):(row<3?0x17132E:0x03020E);
                m.quad(spherical(a0,p0,80),spherical(a1,p0,80),spherical(a1,p1,80),spherical(a0,p1,80),rgb,a);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            begin(true,false);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            m = new EffectMesh(buffer,matrices.peek().getPositionMatrix(),Vec3d.ZERO);
            int starCount=spell==null?0:quality(130,240,380);
            java.util.Random rng=new java.util.Random(721640);
            for(int i=0;i<starCount;i++) {
                double az=rng.nextDouble()*Math.PI*2,el=Math.asin(0.06+rng.nextDouble()*0.94);
                Vec3d p=spherical(az,el,78);
                double size=0.035+rng.nextDouble()*0.12;
                float pulse=(float)(0.6+0.4*Math.sin(age*0.07+i));
                m.star(p,size,i%4==0?0xB797FF:0xDDEAFF,visibility*pulse);
            }
            // Soft procedural nebula clouds: colored, overlapping stars with very wide halos.
            if(spell!=null && CelestialFall.CONFIG.visualQuality>0) for(int i=0;i<quality(0,70,110);i++) {
                double az=i*0.077+0.3,el=0.34+0.17*Math.sin(az*1.7);
                Vec3d p=spherical(az,el,79);
                m.star(p,2.4+Math.sin(i*8.1),i%3==0?0x4753D8:0x6930BA,visibility*0.018f);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally { end(); }
    }

    private static Vec3d spherical(double az,double el,double radius) {
        return new Vec3d(Math.cos(az)*Math.cos(el)*radius,Math.sin(el)*radius,Math.sin(az)*Math.cos(el)*radius);
    }

    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.world==null || CelestialFallClient.SPELLS.isEmpty()) return;
        ClientSpell primary=CinematicDirector.active();
        if(primary==null)primary=CelestialFallClient.nearest();
        if(primary!=null) {
            float age=primary.age(mc.world.getTime(),context.tickDelta());
            double focusHeight=age<SpellTimeline.DESCENT_START?MountainScale.SUMMON_HEIGHT:
                age<SpellTimeline.CONTACT?SpellTimeline.swordTipHeight(age)+45:12;
            Vec3d point=primary.target.add(0,focusHeight,0).subtract(context.camera().getPos());
            org.joml.Vector4f clip=new org.joml.Vector4f((float)point.x,(float)point.y,(float)point.z,1);
            clip.mul(context.matrixStack().peek().getPositionMatrix()).mul(context.projectionMatrix());
            if(clip.w>.01f)AnimePostProcessor.focus(clip.x/clip.w*.5f+.5f,clip.y/clip.w*.5f+.5f);
            else AnimePostProcessor.focus(.5f,.5f);
        }
        ImpactEffects.renderBlocks(context);
        MatrixStack matrices=context.matrixStack();
        Vec3d camera=context.camera().getPos();
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        matrices.push(); matrices.translate(-camera.x,-camera.y,-camera.z);
        try {
            RenderSystem.enableDepthTest();
            // First draw the actual blade. Depth writes make it a proper world object.
            begin(false,true);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            EffectMesh mesh=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            for(ClientSpell spell:CelestialFallClient.spells()) {
                if(!spell.dimension.equals(mc.world.getRegistryKey().getValue()))continue;
                float age=spell.age(mc.world.getTime(),context.tickDelta());
                float appear=SpellTimeline.smooth(SpellTimeline.progress(age,SpellTimeline.REVEAL,SpellTimeline.DESCENT_START))*SpellTimeline.visibility(age);
                AnimeGeometry.rift(mesh.scaled(spell.target,MountainScale.MODEL),spell,age);
                if(appear>0) solidSword(mesh.scaled(spell.target,MountainScale.MODEL),spell,age,appear);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            // Batched additive geometry provides glow without requiring a shader pack.
            begin(true,false);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
            mesh=new EffectMesh(buffer,matrices.peek().getPositionMatrix(),camera);
            for(ClientSpell spell:CelestialFallClient.spells()) {
                if(!spell.dimension.equals(mc.world.getRegistryKey().getValue()))continue;
                float age=spell.age(mc.world.getTime(),context.tickDelta());
                EffectMesh giant=mesh.scaled(spell.target,MountainScale.MODEL);
                constellation(giant,spell,age);
                collidingConstellations(giant,spell,age);
                groundSeal(giant,spell,age);
                swordGlow(giant,spell,age);
                impact(giant,spell,age);
                AnimeGeometry.energy(giant,spell,age);
                ImpactEffects.glow(mesh,spell,age);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            ImpactEffects.renderSmoke(context,matrices);
        } finally { matrices.pop(); end(); }
    }

    private static Vec3d swordPoint(ClientSpell s,float age,double x,double y,double z) {
        double angle=Math.toRadians(s.yaw);
        return s.target.add(x*Math.cos(angle)-z*Math.sin(angle),SpellTimeline.swordTipHeight(age)/MountainScale.MODEL+y,
            x*Math.sin(angle)+z*Math.cos(angle));
    }
    private static void face(EffectMesh m,ClientSpell s,float age,double[][] vertices,int color,float alpha) {
        m.quad(swordPoint(s,age,vertices[0][0],vertices[0][1],vertices[0][2]),
            swordPoint(s,age,vertices[1][0],vertices[1][1],vertices[1][2]),
            swordPoint(s,age,vertices[2][0],vertices[2][1],vertices[2][2]),
            swordPoint(s,age,vertices[3][0],vertices[3][1],vertices[3][2]),color,alpha);
    }
    private static void swordBox(EffectMesh m,ClientSpell s,float age,double x0,double y0,double z0,
                                 double x1,double y1,double z1,int color,float alpha) {
        face(m,s,age,new double[][]{{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0}},color,alpha);
        face(m,s,age,new double[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}},color,alpha);
        face(m,s,age,new double[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}},color,alpha);
        face(m,s,age,new double[][]{{x1,y0,z0},{x1,y0,z1},{x1,y1,z1},{x1,y1,z0}},color,alpha);
        face(m,s,age,new double[][]{{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}},color,alpha);
        face(m,s,age,new double[][]{{x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1}},color,alpha);
    }
    private static void solidSword(EffectMesh m,ClientSpell s,float age,float alpha) {
        // Faceted tip and two colored blade faces, 48 blocks long.
        for(int side:new int[]{-1,1}) {
            face(m,s,age,new double[][]{{0,0,0},{-2.25,7,side*0.48},{-2.25,48,side*0.48},{0,48,side*0.82}},
                side==1?0x5A3891:0x291C58,alpha);
            face(m,s,age,new double[][]{{0,0,0},{2.25,7,side*0.48},{2.25,48,side*0.48},{0,48,side*0.82}},
                side==1?0x283E76:0x20294C,alpha);
        }
        face(m,s,age,new double[][]{{-2.25,7,-0.48},{-2.25,7,0.48},{-2.25,48,0.48},{-2.25,48,-0.48}},0xC9ACFC,alpha);
        face(m,s,age,new double[][]{{2.25,7,-0.48},{2.25,7,0.48},{2.25,48,0.48},{2.25,48,-0.48}},0xA6E1FF,alpha);
        swordBox(m,s,age,-3.1,47,-1.15,3.1,49,1.15,0xE9D3A2,alpha);
        swordBox(m,s,age,-0.8,49,-0.75,0.8,58,0.75,0x31204F,alpha);
        for(int i=0;i<8;i++)swordBox(m,s,age,-0.93,49+i,-0.9,0.93,49.22+i,0.9,0xD6B5EF,alpha);
        swordBox(m,s,age,-1.5,58,-1.1,1.5,59.7,1.1,0xEAD7AA,alpha);
        // A rising crescent guard, segmented so its curved silhouette stays visible from any angle.
        for(int i=-9;i<9;i++) {
            double y0=48+0.048*i*i,y1=48+0.048*(i+1)*(i+1);
            swordBox(m,s,age,i,Math.min(y0,y1)-0.35,-0.9,i+1,Math.max(y0,y1)+0.45,0.9,0xE7D1AC,alpha);
        }
    }

    private static void constellation(EffectMesh m,ClientSpell s,float age) {
        float trace=SpellTimeline.progress(age,SpellTimeline.TRACE_START,SpellTimeline.CONVERGE_START);
        if(trace<=0 || age>170)return;
        float gather=SpellTimeline.smooth(SpellTimeline.progress(age,SpellTimeline.CONVERGE_START,SpellTimeline.DESCENT_START));
        float opacity=SpellTimeline.visibility(age)*(1-SpellTimeline.progress(age,SpellTimeline.DESCENT_START,170));
        double radius=(MountainScale.CONSTELLATION_RADIUS/MountainScale.MODEL)*(1-0.88*gather);
        double rotation=age*0.0018;
        Vec3d center=s.target.add(0,MountainScale.SUMMON_HEIGHT/MountainScale.MODEL,0);
        int seg=segments();
        m.ring(center,X,Z,radius*1.12,seg,Math.min(1,trace*1.8f),0.07,0xB57AFF,opacity,rotation);
        m.ring(center,X,Z,radius*1.19,seg,Math.min(1,trace*1.4f),0.05,0x8AA9FF,opacity*0.6f,-rotation);
        for(int i=0;i<48;i++) {
            if((float)i/48>trace) break;
            double angle=i*Math.PI*2/48+rotation;
            Vec3d a=center.add(Math.cos(angle)*radius*1.13,0,Math.sin(angle)*radius*1.13);
            Vec3d b=center.add(Math.cos(angle)*radius*(i%4==0?1.18:1.155),0,Math.sin(angle)*radius*(i%4==0?1.18:1.155));
            m.line(a,b,0.055,0xD1BAFF,opacity);
        }
        Vec3d[] points=new Vec3d[STARS.length];
        for(int i=0;i<STARS.length;i++) {
            double x=STARS[i][0]*radius,z=STARS[i][1]*radius;
            points[i]=center.add(x*Math.cos(rotation)-z*Math.sin(rotation),0,x*Math.sin(rotation)+z*Math.cos(rotation));
            float twinkle=(float)(0.78+0.22*Math.sin(age*0.15+i*1.4));
            m.star(points[i],1.1*(1-gather)+0.25,0xD6B5FF,opacity*twinkle);
        }
        for(int i=0;i<EDGES.length;i++) {
            float draw=SpellTimeline.clamp(trace*EDGES.length-i);
            Vec3d a=points[EDGES[i][0]],b=points[EDGES[i][1]];
            if(draw>0)m.glowLine(a,a.lerp(b,draw),0.09,0xBCA1FF,opacity);
        }
        if(age>100) {
            int streams=quality(18,30,48);
            for(int i=0;i<streams;i++) {
                double t=((age-100)*0.035+i/(double)streams)%1;
                double a=i*2.399+age*0.033,r=34*(1-t);
                Vec3d p=center.add(Math.cos(a)*r,Math.sin(t*Math.PI)*4,Math.sin(a)*r);
                m.star(p,0.3+0.5*t,0xB7EFFF,opacity*(float)t);
            }
            m.star(center,3+gather*5,0xDEBAFF,opacity*gather);
        }
    }

    private static void collidingConstellations(EffectMesh m,ClientSpell s,float age) {
        if(age<24 || age>156)return;
        Vec3d hub=s.target.add(0,MountainScale.SUMMON_HEIGHT/MountainScale.MODEL,0);
        for(int group=0;group<SpellTimeline.CLASH_COUNT;group++) {
            int collision=SpellTimeline.clashTick(group);
            float approach=SpellTimeline.smooth(SpellTimeline.progress(age,collision-27,collision));
            float after=age-collision;
            float visible=SpellTimeline.smooth(SpellTimeline.progress(age,24+group*10,45+group*10))
                *(1-SpellTimeline.progress(age,collision,collision+8));
            double angle=group*Math.PI*2/3+age*.002;
            Vec3d centre=hub.add(Math.cos(angle)*67*(1-approach),(group-1)*8*(1-approach),Math.sin(angle)*67*(1-approach));
            double radius=11*(1-.7*approach);
            if(visible>0) {
                int color=group==1?0xACCFFF:0xCE8AFF;
                m.ring(centre,X,Z,radius,segments(),1,.13,color,visible,age*.006);
                m.ring(centre,X,Z,radius*1.13,segments(),1,.06,0xD6AAFF,visible*.7f,-age*.009);
                Vec3d[] points=new Vec3d[7];
                for(int i=0;i<7;i++) {
                    double a=i*Math.PI*2/7+group+age*.004;
                    points[i]=centre.add(Math.cos(a)*radius*.9,0,Math.sin(a)*radius*.9);
                    m.star(points[i],.9,color,visible);
                }
                for(int i=0;i<7;i++)m.glowLine(points[i],points[(i+3)%7],.09,color,visible);
                if(approach>0)m.glowLine(centre,centre.add(Math.cos(angle)*8*approach,0,Math.sin(angle)*8*approach),.4,color,visible*.6f);
            }
            if(after>=0 && after<18) {
                float pulse=1-after/18;
                m.star(hub,5+9*pulse,0xF6D9FF,pulse);
                m.ring(hub,X,Z,2+after*2.4,segments(),1,.16+pulse*.35,0xDBA5FF,pulse,0);
                m.ring(hub,X,Y,2+after*1.7,segments(),1,.12,0xABBAFF,pulse*.55f,group);
            }
        }
    }

    private static void groundSeal(EffectMesh m,ClientSpell s,float age) {
        if(age>=SpellTimeline.IMPACT)return;
        float a=SpellTimeline.visibility(age)*0.65f;
        Vec3d c=s.target.add(0,0.035,0);
        double perimeter=s.visualRadius/MountainScale.MODEL;
        m.ring(c,X,Z,perimeter,segments(),1,.12,0xF69DFF,a*.75f,0);
        m.ring(c,X,Z,perimeter*.96,segments(),1,.04,0xB861FF,a*.45f,age*.002);
        m.ring(c,X,Z,4,segments(),1,0.045,0xAD88FF,a,age*0.006);
        m.ring(c,X,Z,4.5,segments(),1,0.025,0xA5C7FF,a*0.5f,0);
        for(int i=0;i<5;i++) {
            double x=i*Math.PI*2/5+age*0.006,y=(i+2)*Math.PI*2/5+age*0.006;
            m.line(c.add(Math.cos(x)*3.8,0,Math.sin(x)*3.8),c.add(Math.cos(y)*3.8,0,Math.sin(y)*3.8),0.03,0xD0ACFF,a);
        }
        for(int i=0;i<8;i++) {
            double t=(age*0.017+i/8.0)%1,angle=i*Math.PI/4+age*0.03;
            m.star(c.add(Math.cos(angle)*4,t*6,Math.sin(angle)*4),0.1,0xDDD2FF,a*(float)(1-t));
        }
    }

    private static void swordGlow(EffectMesh m,ClientSpell s,float age) {
        float appear=SpellTimeline.smooth(SpellTimeline.progress(age,SpellTimeline.REVEAL,SpellTimeline.DESCENT_START))*SpellTimeline.visibility(age);
        if(appear<=0)return;
        for(int side:new int[]{-1,1}) {
            double z=side*0.85;
            m.glowLine(swordPoint(s,age,0,0,0),swordPoint(s,age,0,48,z),0.10,0xC8B1FF,appear);
            for(int sign:new int[]{-1,1}) {
                m.glowLine(swordPoint(s,age,0,0,0),swordPoint(s,age,sign*2.25,7,side*0.5),0.10,0xC3C4FF,appear);
                m.glowLine(swordPoint(s,age,sign*2.25,7,side*0.5),swordPoint(s,age,sign*2.25,48,side*0.5),0.12,0xB2CFFF,appear);
            }
            for(int i=0;i<10;i++) {
                double h=9+i*3.8;
                float rune=appear*SpellTimeline.smooth(SpellTimeline.progress(age,SpellTimeline.REVEAL+i*.9f,SpellTimeline.REVEAL+5+i*.9f));
                m.glowLine(swordPoint(s,age,-0.7,h,z),swordPoint(s,age,0.7,h+1.2,z),0.055,0x99E8FF,rune);
                m.glowLine(swordPoint(s,age,0.7,h+1.2,z),swordPoint(s,age,-0.3,h+2,z),0.055,0xDAABFF,rune);
            }
        }
        m.star(swordPoint(s,age,0,48,0),3.2,0xF4D9FF,appear);
        m.star(swordPoint(s,age,0,60.7,0),2.2,0xDDA7FF,appear);
        if(age>=SpellTimeline.DESCENT_START && age<SpellTimeline.IMPACT) {
            float speed=SpellTimeline.progress(age,SpellTimeline.DESCENT_START,SpellTimeline.IMPACT);
            Vec3d tip=swordPoint(s,age,0,0,0);
            m.star(tip,3+speed*3,0xFFDFB0,appear);
            int strands=quality(9,14,21);
            for(int i=0;i<strands;i++) {
                double a=i*Math.PI*2/strands+age*0.11,r=0.5+(i%5)*0.6;
                Vec3d low=tip.add(Math.cos(a)*r,2,Math.sin(a)*r);
                Vec3d high=tip.add(Math.cos(a+0.8)*r*2,25+speed*60+(i%4)*3,Math.sin(a+0.8)*r*2);
                m.glowLine(low,high,0.16+(i%3)*0.06,i%3==0?0xFFC679:0xCCB3FF,appear*(0.4f+0.5f*speed));
            }
        }
        if(age>=SpellTimeline.IMPACT) {
            int count=quality(14,24,40);
            for(int i=0;i<count;i++) {
                double t=(age*0.009+i/(double)count)%1,a=i*2.4+age*0.018;
                m.star(swordPoint(s,age,Math.cos(a)*4,t*62,Math.sin(a)*4),0.12+0.12*Math.sin(i),0xCAB2FF,appear*0.8f);
            }
        }
    }

    private static void impact(EffectMesh m,ClientSpell s,float age) {
        float t=age-SpellTimeline.IMPACT;
        if(t<0 || t>145)return;
        Vec3d c=s.target.add(0,.2,0);
        for(int wave=0;wave<SpellTimeline.WAVE_COUNT;wave++) {
            float time=age-SpellTimeline.waveTick(wave);
            if(time<0 || time>96)continue;
            float fade=1-SpellTimeline.progress(time,0,96);
            double r=SpellTimeline.waveRadius(age,wave)/MountainScale.MODEL;
            int color=wave==1?0xEFF7FF:wave==2?0x8456FF:0xD4A2FF;
            m.ring(c.add(0,wave*.14,0),X,Z,r,segments(),1,.25+fade*.7,color,fade,0);
            m.ring(c.add(0,.3+wave*.14,0),X,Z,r*.92,segments(),1,.14,color,fade*.65f,0);
            if(time<42) {
                float shell=1-SpellTimeline.progress(time,0,42);
                int lat=quality(8,10,13),lon=quality(28,40,56);
                for(int y=0;y<lat;y++)for(int x=0;x<lon;x++) {
                    double e0=y*Math.PI/2/lat,e1=(y+1)*Math.PI/2/lat,a0=x*Math.PI*2/lon,a1=(x+1)*Math.PI*2/lon;
                    m.quad(c.add(spherical(a0,e0,r)),c.add(spherical(a1,e0,r)),
                        c.add(spherical(a1,e1,r)),c.add(spherical(a0,e1,r)),color,shell*.065f);
                }
                for(int y=1;y<5;y++) {
                    double e=y*Math.PI/10;
                    m.ring(c.add(0,r*Math.sin(e),0),X,Z,r*Math.cos(e),segments(),1,.09,color,shell*.35f,0);
                }
            }
        }
        if(t<58) {
            float fade=1-SpellTimeline.progress(t,10,58);
            double core=3+17*SpellTimeline.smooth(SpellTimeline.progress(t,0,17));
            for(int layer=0;layer<3;layer++) {
                double r=core*(1-layer*.23);
                for(int y=0;y<10;y++)for(int x=0;x<quality(24,32,40);x++) {
                    int lon=quality(24,32,40);
                    double e0=y*Math.PI/20,e1=(y+1)*Math.PI/20,a0=x*Math.PI*2/lon,a1=(x+1)*Math.PI*2/lon;
                    m.quad(c.add(spherical(a0,e0,r)),c.add(spherical(a1,e0,r)),c.add(spherical(a1,e1,r)),c.add(spherical(a0,e1,r)),
                        layer==2?0xFFFFFF:layer==1?0xD5AAFF:0x843FFF,fade*(.12f+layer*.06f));
                }
            }
            m.glowLine(c,c.add(0,110,0),8+fade*10,0x9C49FF,fade*.6f);
            m.glowLine(c,c.add(0,100,0),2.3,0xF7ECFF,fade);
            m.star(c.add(0,5,0),26*fade,0xFFFFFF,fade);
        }
        float fade=1-SpellTimeline.progress(t,10,140);
        for(int i=0;i<quality(30,55,80);i++) {
            double a=i*2.399,r=t*(.28+(i%7)*.06),h=Math.max(.3,t*.58-t*t*.005);
            Vec3d p=c.add(Math.cos(a)*r,h,Math.sin(a)*r);
            m.glowLine(p,p.add(Math.cos(a)*-2,-.6,Math.sin(a)*-2),.12,0xE7C7FF,fade);
            m.star(p,.2+fade*.4,0xEED5FF,fade);
        }
    }
}
