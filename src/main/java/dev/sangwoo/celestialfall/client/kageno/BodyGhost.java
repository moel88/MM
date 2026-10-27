package dev.sangwoo.celestialfall.client.kageno;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.client.KagenoSword;
import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import dev.sangwoo.celestialfall.client.PoseBlend;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Speed afterimages: copies of the player model exactly as it was drawn a few hundredths of a second earlier, at the
 * place it stood then, tinted and fading with age. Each part leaves a copy only while it moves fast and away from
 * where it is now, so a quick strike trails its sword arm and blade, a dash trails the whole body, and a copy never
 * lies on the body as a tinted film. The copies come from what was on screen, so a dash the server drives trails
 * along its real path.
 */
public final class BodyGhost {
    private BodyGhost(){}
    /** Copies drawn behind the body and the time between them (seconds). */
    static final int COPIES=5;
    static final float GAP=.03f;
    /** Part speeds (blocks per second) at which a part starts to leave copies and leaves them fully; the blade's tip sweeps faster than any limb. */
    static final float FROM=7,FULL=16,BLADE_FROM=11,BLADE_FULL=22;
    /** Distance from where the part is now (blocks) at which its copy starts to show and shows fully. */
    static final float NEAR=.12f,CLEAR=.35f;
    /** Opacity of the newest copy, and how long frames are kept (seconds). */
    static final float ALPHA=.42f,KEEP=.4f;
    static final int MAX_SHOTS=400;

    /** One frame of a player as drawn: the model's part angles and pivots, its transform and the sword's, relative to its feet. */
    record Shot(float time,Vec3d pos,Matrix4f body,float[][] parts,Matrix4f sword,MotionPlayerModel model,Identifier skin,Vec3d[] points){}
    private static final Map<UUID,Deque<Shot>> HISTORY=new HashMap<>();

    private static boolean world;
    private static UUID taking;
    private static float takingAt;
    private static Vec3d takingPos;
    private static Matrix4f inverse,body,sword;
    private static float[][] parts;
    private static MotionPlayerModel model;
    private static Identifier skin;

    public static void clear(){HISTORY.clear();taking=null;}
    /** Set while the world (not a screen) is being drawn: only those frames are kept. */
    public static void world(boolean drawing){world=drawing;if(!drawing)taking=null;}

    /** Starts taking the frame of {@code player}; {@code m} holds the transform to its feet. */
    public static void begin(AbstractClientPlayerEntity player,MatrixStack m,float delta,Vec3d offset){
        taking=null;var w=MinecraftClient.getInstance().world;
        if(!world||w==null||KagenoClips.animated(player.getUuid())==null)return;
        taking=player.getUuid();takingAt=(w.getTime()+delta)/20f;takingPos=player.getLerpedPos(delta).add(offset);
        inverse=new Matrix4f(m.peek().getPositionMatrix()).invert();body=sword=null;parts=null;model=null;skin=player.getSkinTexture();
    }
    /** The body model of the frame being taken is drawn now. */
    public static void body(MotionPlayerModel drawn,MatrixStack m){
        if(taking==null||body!=null)return;
        body=new Matrix4f(inverse).mul(m.peek().getPositionMatrix());parts=PoseBlend.capture(drawn);model=drawn;
    }
    /** The sword of the frame being taken is drawn now. */
    public static void sword(MatrixStack m){if(taking!=null&&sword==null)sword=new Matrix4f(inverse).mul(m.peek().getPositionMatrix());}
    public static void end(){
        if(taking==null)return;UUID id=taking;taking=null;if(body==null)return;
        var shots=HISTORY.computeIfAbsent(id,k->new ArrayDeque<>());
        // Another pass of the same frame (a mirror, a screen) keeps the first one.
        if(!shots.isEmpty()&&Math.abs(shots.peekLast().time-takingAt)<1e-5f)return;
        shots.addLast(new Shot(takingAt,takingPos,body,parts,sword,model,skin,points(takingPos,body,parts,sword)));
        while(shots.size()>MAX_SHOTS||shots.peekFirst().time<takingAt-KEEP)shots.removeFirst();
    }

    /** Ends of each part (head, body, arms, legs) and of the blade, in the world. */
    static Vec3d[] points(Vec3d pos,Matrix4f body,float[][] parts,Matrix4f sword){
        float[][] ends={{0,0,0,0,-8,0},{0,0,0,0,12,0},{0,0,0,0,10,0},{0,0,0,0,10,0},{0,0,0,0,12,0},{0,0,0,0,12,0}};
        Vec3d[] out=new Vec3d[14];
        for(int i=0;i<6;i++){
            float[] p=parts[i];var part=new Matrix4f(body).translate(p[3]/16,p[4]/16,p[5]/16).rotate(new Quaternionf().rotationZYX(p[2],p[1],p[0]));
            for(int e=0;e<2;e++)out[i*2+e]=at(pos,part,ends[i][e*3]/16,ends[i][e*3+1]/16,ends[i][e*3+2]/16);
        }
        if(sword!=null){out[12]=at(pos,sword,.5f,0,.5f);out[13]=at(pos,sword,.5f,31.5f/16,.5f);}
        return out;
    }
    private static Vec3d at(Vec3d pos,Matrix4f m,float x,float y,float z){var v=m.transformPosition(new Vector3f(x,y,z));return pos.add(v.x,v.y,v.z);}

    /** The frame closest to {@code time}, if one lies within half a gap of it. */
    static Shot nearest(Deque<Shot> shots,float time){
        Shot best=null;
        for(Iterator<Shot> i=shots.descendingIterator();i.hasNext();){Shot s=i.next();if(best==null||Math.abs(s.time-time)<Math.abs(best.time-time))best=s;else if(s.time<time)break;}
        return best!=null&&Math.abs(best.time-time)<=GAP*.6f?best:null;
    }
    /** How strongly a part shows in a copy: it moved at {@code speed} toward the next copy and lies {@code away} from where it is now. */
    static float strength(double speed,double away){return Ease.smooth((float)speed,FROM,FULL)*Ease.smooth((float)away,NEAR,CLEAR);}
    static float bladeStrength(double speed,double away){return Ease.smooth((float)speed,BLADE_FROM,BLADE_FULL)*Ease.smooth((float)away,NEAR,CLEAR);}
    /** Part {@code part} (0-5 the model's parts, 6 the sword): how far it went between two frames and how far it is from a third. */
    static double moved(Shot a,Shot b,int part){
        if(a.points[part*2]==null||b.points[part*2]==null)return 0;
        return Math.max(a.points[part*2].distanceTo(b.points[part*2]),a.points[part*2+1].distanceTo(b.points[part*2+1]));
    }

    private record Copy(Shot shot,float[] alpha,int rgb){}

    /** Draws the copies of every caster in {@code casts}; {@code view} is the camera's rotation, {@code camera} its position. */
    public static void render(List<Fx> casts,Matrix4f view,Vec3d camera,float delta){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        float now=(mc.world.getTime()+delta)/20f;
        HISTORY.values().removeIf(s->s.isEmpty()||s.peekLast().time<now-KEEP);
        List<Copy> copies=new ArrayList<>();
        for(Fx c:casts){
            var skill=KagenoSkills.of(c.cast.skill());if(skill==null||c.sec<0||skill.hidden(c.sec))continue;
            float own=skill.afterimages(c.sec),gain=own<0?1:Math.min(1,own);if(gain<=.01f)continue;
            var shots=HISTORY.get(c.cast.caster());if(shots==null||shots.isEmpty())continue;
            // Only while the body is on screen this frame (not in first person, not hidden).
            Shot current=shots.peekLast();if(Math.abs(current.time-now)>1e-4f)continue;
            Shot newer=current;
            for(int k=1;k<=COPIES;k++){
                Shot s=nearest(shots,now-k*GAP);if(s==null||s==newer||s.time>=newer.time)break;
                float dt=newer.time-s.time,fade=gain*ALPHA*(1-(k-1f)/COPIES);float[] alpha=new float[7];boolean any=false;
                for(int part=0;part<7;part++){double speed=moved(s,newer,part)/dt,away=moved(s,current,part);alpha[part]=fade*(part<6?strength(speed,away):bladeStrength(speed,away));any|=alpha[part]>.01f;}
                if(any)copies.add(new Copy(s,alpha,skill.afterimageColor()));
                newer=s;
            }
        }
        if(copies.isEmpty())return;
        try{
            RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.setShaderColor(1,1,1,1);
            // Bodies: the skin, tinted and see-through, oldest first; only the front faces so a copy reads as one shape.
            RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            var buffer=Tessellator.getInstance().getBuffer();
            Map<Identifier,List<Copy>> bySkin=new HashMap<>();
            for(int i=copies.size()-1;i>=0;i--)bySkin.computeIfAbsent(copies.get(i).shot.skin,k->new ArrayList<>()).add(copies.get(i));
            for(var e:bySkin.entrySet()){
                RenderSystem.setShaderTexture(0,e.getKey());
                buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_TEXTURE_COLOR);var sink=new Sink(buffer,true);
                for(Copy copy:e.getValue())drawBody(copy,sink,view,camera);
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
            // Blades: a glowing silhouette added on top.
            RenderSystem.disableCull();RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            buffer.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var glow=new Sink(buffer,false);
            for(int i=copies.size()-1;i>=0;i--){
                Copy copy=copies.get(i);if(copy.shot.sword==null||copy.alpha[6]<=.01f)continue;
                glow.tint(copy.rgb,.5f,copy.alpha[6]*1.4f);KagenoSword.renderMesh(stack(view,camera,copy.shot,copy.shot.sword),glow,0,OverlayTexture.DEFAULT_UV);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }finally{RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.setShader(GameRenderer::getPositionColorProgram);}
    }
    private static MatrixStack stack(Matrix4f view,Vec3d camera,Shot s,Matrix4f local){
        var m=new MatrixStack();m.peek().getPositionMatrix().set(view).translate((float)(s.pos.x-camera.x),(float)(s.pos.y-camera.y),(float)(s.pos.z-camera.z)).mul(local);return m;
    }
    private static void drawBody(Copy copy,Sink sink,Matrix4f view,Vec3d camera){
        var model=copy.shot.model;float[][] shown=PoseBlend.capture(model);
        try{
            PoseBlend.write(model,copy.shot.parts);model.overlays();
            var m=stack(view,camera,copy.shot,copy.shot.body);
            ModelPart[][] parts={{model.head,model.hat},{model.body,model.jacket},{model.rightArm,model.rightSleeve},{model.leftArm,model.leftSleeve},{model.rightLeg,model.rightPants},{model.leftLeg,model.leftPants}};
            for(int i=0;i<6;i++){
                if(copy.alpha[i]<=.01f)continue;sink.tint(copy.rgb,.45f,copy.alpha[i]);
                for(ModelPart p:parts[i])if(p.visible)p.render(m,sink,0,OverlayTexture.DEFAULT_UV);
            }
        }finally{PoseBlend.write(model,shown);model.overlays();}
    }

    /** Writes model and sword vertices into a plain buffer in one colour, dropping light, overlay and normals. */
    private static final class Sink implements VertexConsumer {
        private final BufferBuilder out;private final boolean textured;private float r,g,b,a;
        Sink(BufferBuilder out,boolean textured){this.out=out;this.textured=textured;}
        /** The tint {@code rgb} mixed {@code white} of the way to white, at opacity {@code alpha}. */
        void tint(int rgb,float white,float alpha){r=mix(rgb>>16,white);g=mix(rgb>>8,white);b=mix(rgb,white);a=Math.min(1,alpha);}
        private static float mix(int channel,float white){float c=(channel&255)/255f;return c+(1-c)*white;}
        @Override public VertexConsumer vertex(double x,double y,double z){out.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int red,int green,int blue,int alpha){return this;}
        @Override public VertexConsumer texture(float u,float v){if(textured)out.texture(u,v);return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer normal(float x,float y,float z){return this;}
        @Override public void next(){out.color(r,g,b,a);out.next();}
        @Override public void fixedColor(int red,int green,int blue,int alpha){}
        @Override public void unfixColor(){}
    }
}
