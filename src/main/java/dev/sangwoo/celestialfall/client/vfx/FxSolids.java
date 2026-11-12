package dev.sangwoo.celestialfall.client.vfx;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Solid pieces of the world thrown about by the effects, drawn with the real block models before the effect
 * layer (so the effects are hidden behind them): chunks of the ground block flying and bouncing, and ground
 * blocks lifted in a travelling wave. Only a picture: the world's blocks never change. Every motion is a closed
 * function of time (no per-tick simulation), so it plays smoothly at any frame rate.
 */
public final class FxSolids {
    public static final double GRAVITY=22;
    private final MinecraftClient mc;
    private final MatrixStack matrices;
    private final Vec3d camera;
    private int drawn;
    FxSolids(MinecraftClient mc,MatrixStack matrices,Vec3d camera){this.mc=mc;this.matrices=matrices;this.camera=camera;}

    /**
     * Where a chunk thrown from {@code p0} with velocity {@code v0} is {@code t} seconds later, bouncing on the
     * plane y = {@code ground} (restitution .32, the sideways speed halved on each bounce) and resting after three
     * bounces. The chunk's lowest point never goes below the ground.
     */
    public static Vec3d flight(Vec3d p0,Vec3d v0,double ground,double t){
        double x=p0.x,y=Math.max(p0.y,ground),z=p0.z,vx=v0.x,vy=v0.y,vz=v0.z,left=Math.max(0,t);
        for(int bounce=0;bounce<4;bounce++){
            // Time to reach the ground from height y with upward speed vy.
            double h=y-ground,hit=(vy+Math.sqrt(vy*vy+2*GRAVITY*h))/GRAVITY;
            if(left<=hit)return new Vec3d(x+vx*left,y+vy*left-GRAVITY*left*left/2,z+vz*left);
            x+=vx*hit;z+=vz*hit;y=ground;double down=vy-GRAVITY*hit;left-=hit;
            vy=-down*.32;vx*=.5;vz*=.5;
            if(vy<.4)break;
        }
        // At rest, sliding to a stop.
        double slide=Math.min(left,.25);
        return new Vec3d(x+vx*slide*(1-slide/.5),ground,z+vz*slide*(1-slide/.5));
    }
    /** Height a lifted ground block rises to: a crest of {@code amplitude} travelling out at {@code speed} blocks a second. */
    public static double wave(double distance,double t,double amplitude,double speed,double width){
        if(t<=0)return 0;double front=speed*t,k=(distance-front)/width;
        if(Math.abs(k)>3)return 0;
        return amplitude*Math.exp(-k*k)*Math.exp(-distance/18);
    }

    /** The full block below {@code p} (the ground a chunk is torn from), or null if there is none within 3 blocks. */
    public BlockState ground(Vec3d p){
        World world=mc.world;if(world==null)return null;
        BlockPos pos=BlockPos.ofFloored(p);
        for(int i=0;i<4;i++){
            BlockState s=world.getBlockState(pos);
            if(!s.isAir()&&s.isFullCube(world,pos))return s;
            pos=pos.down();
        }
        return null;
    }
    /** Top surface height of the ground below {@code p} (falls back to p.y). */
    public double groundY(Vec3d p){
        World world=mc.world;if(world==null)return p.y;
        BlockPos pos=BlockPos.ofFloored(p.x,p.y+.5,p.z);
        for(int i=0;i<5;i++){
            BlockState s=world.getBlockState(pos);
            if(!s.isAir()&&s.isFullCube(world,pos))return pos.getY()+1;
            pos=pos.down();
        }
        return p.y;
    }
    /** The block at {@code pos} if it is a full block with open air above it (a piece of floor that can heave), else null. */
    public BlockState surface(BlockPos pos){
        World world=mc.world;if(world==null)return null;
        BlockState s=world.getBlockState(pos);
        if(s.isAir()||!s.isFullCube(world,pos))return null;
        BlockState above=world.getBlockState(pos.up());
        return above.isAir()||!above.isSolidBlock(world,pos.up())?s:null;
    }
    /** A chunk of {@code state}: a small copy of the block, {@code size} blocks across, turned by the angles (degrees). */
    public void chunk(BlockState state,Vec3d p,double size,float yaw,float pitch,float roll){
        if(state==null||!(size>.01)||p.squaredDistanceTo(camera)>64*64)return;
        matrices.push();
        matrices.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        matrices.scale((float)size,(float)size,(float)size);
        matrices.translate(-.5,-.5,-.5);
        draw(state,BlockPos.ofFloored(p));
        matrices.pop();
    }
    /** The block at {@code pos} drawn {@code lift} blocks higher, tilted {@code tilt} degrees about {@code axis} (horizontal). */
    public void lifted(BlockPos pos,BlockState state,double lift,float tilt,Vec3d axis){
        if(state==null||!(lift>.02)||Vec3d.ofCenter(pos).squaredDistanceTo(camera)>64*64)return;
        matrices.push();
        matrices.translate(pos.getX()+.5-camera.x,pos.getY()+.5+lift-camera.y,pos.getZ()+.5-camera.z);
        if(tilt!=0)matrices.multiply(RotationAxis.of(new org.joml.Vector3f((float)axis.x,0,(float)axis.z).normalize()).rotationDegrees(tilt));
        matrices.translate(-.5,-.5,-.5);
        draw(state,pos);
        matrices.pop();
    }
    /**
     * (2.125.0, X Chara's double's wall: "벽은 아래 있는 블록에 따라 바뀜") A pointed spike of the block {@code state}:
     * its foot at {@code foot}, {@code length} blocks along {@code axis} (unit), four-sided, {@code half} blocks from its
     * middle to a corner at its widest, a little up from its foot ({@code side}, unit and square to the axis, is the
     * way to one corner), then narrowing to its point. Its faces carry the block's top face (what the floor shows of
     * it), in its colour there (grass is green), lit as the world at {@code lit}.
     */
    public void spike(BlockState state,BlockPos lit,Vec3d foot,Vec3d axis,Vec3d side,double length,double half){
        if(state==null||!(length>.03)||!(half>.01)||foot.squaredDistanceTo(camera)>96*96)return;
        var model=mc.getBlockRenderManager().getModel(state);
        var quads=model.getQuads(state,net.minecraft.util.math.Direction.UP,net.minecraft.util.math.random.Random.create(42));
        var sprite=quads.isEmpty()?model.getParticleSprite():quads.get(0).getSprite();
        int tint=0xffffff;
        if(!quads.isEmpty()&&quads.get(0).hasColor()){int c=mc.getBlockColors().getColor(state,mc.world,lit,quads.get(0).getColorIndex());if(c!=-1)tint=c;}
        float r=(tint>>16&255)/255f,g=(tint>>8&255)/255f,b=(tint&255)/255f;
        int light=WorldRenderer.getLightmapCoordinates(mc.world,lit);
        var vc=mc.getBufferBuilders().getEntityVertexConsumers().getBuffer(net.minecraft.client.render.RenderLayer.getEntityCutoutNoCull(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
        var entry=matrices.peek();
        Vec3d other=axis.crossProduct(side).normalize();
        // its rings: the foot (narrower), the shoulder (its widest), and the point
        double shoulder=length*SPIKE_SHOULDER;
        Vec3d[] low=new Vec3d[4],mid=new Vec3d[4];Vec3d tip=foot.add(axis.multiply(length));
        for(int i=0;i<4;i++){
            Vec3d out=(i%2==0?side:other).multiply(i<2?1:-1);
            low[i]=foot.add(out.multiply(half*.72));mid[i]=foot.add(axis.multiply(shoulder)).add(out.multiply(half));
        }
        for(int i=0;i<4;i++){
            int j=(i+1)%4;float u0=sprite.getFrameU(i*4),u1=sprite.getFrameU(i*4+4);
            float v0=sprite.getFrameV(16),v1=sprite.getFrameV(16-16*SPIKE_SHOULDER),v2=sprite.getFrameV(0);
            Vec3d centre=foot.add(axis.multiply(length*.4));
            face(vc,entry,low[i],low[j],mid[j],mid[i],u0,v0,u1,v0,u1,v1,u0,v1,r,g,b,light,centre);
            float um=(u0+u1)*.5f;
            face(vc,entry,mid[i],mid[j],tip,tip,u0,v1,u1,v1,um,v2,um,v2,r,g,b,light,centre);
        }
        drawn++;
    }
    /** How far up a spike its widest is (of its length). */
    public static final double SPIKE_SHOULDER=.22;
    private void face(net.minecraft.client.render.VertexConsumer vc,MatrixStack.Entry entry,Vec3d a,Vec3d b,Vec3d c,Vec3d d,float ua,float va,float ub,float vb,float uc,float vcc,float ud,float vd,float r,float g,float bl,int light,Vec3d centre){
        Vec3d n=b.subtract(a).crossProduct(c.subtract(a));
        if(n.lengthSquared()<1e-12)n=c.subtract(b).crossProduct(d.subtract(b));
        if(n.lengthSquared()<1e-12)return;n=n.normalize();
        // (outward: away from the spike's middle)
        if(n.dotProduct(a.add(c).multiply(.5).subtract(centre))<0)n=n.negate();
        Vec3d[] p={a,b,c,d};float[] u={ua,ub,uc,ud},v={va,vb,vcc,vd};
        for(int i=0;i<4;i++)vc.vertex(entry.getPositionMatrix(),(float)(p[i].x-camera.x),(float)(p[i].y-camera.y),(float)(p[i].z-camera.z)).color(r,g,bl,1f).texture(u[i],v[i])
            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(),(float)n.x,(float)n.y,(float)n.z).next();
    }
    private void draw(BlockState state,BlockPos at){
        var consumers=mc.getBufferBuilders().getEntityVertexConsumers();
        int light=WorldRenderer.getLightmapCoordinates(mc.world,at.up());
        mc.getBlockRenderManager().renderBlockAsEntity(state,matrices,consumers,light,OverlayTexture.DEFAULT_UV);
        drawn++;
    }
    void finish(){if(drawn>0)mc.getBufferBuilders().getEntityVertexConsumers().draw();}
    public int drawn(){return drawn;}
}
