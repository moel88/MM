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
    private void draw(BlockState state,BlockPos at){
        var consumers=mc.getBufferBuilders().getEntityVertexConsumers();
        int light=WorldRenderer.getLightmapCoordinates(mc.world,at.up());
        mc.getBlockRenderManager().renderBlockAsEntity(state,matrices,consumers,light,OverlayTexture.DEFAULT_UV);
        drawn++;
    }
    void finish(){if(drawn>0)mc.getBufferBuilders().getEntityVertexConsumers().draw();}
    public int drawn(){return drawn;}
}
