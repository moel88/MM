package dev.sangwoo.celestialfall.client.town;

import dev.sangwoo.celestialfall.map.MapDefinition;
import dev.sangwoo.celestialfall.map.MapWires;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Draws the map's wires, the power and telephone lines hung from pole to pole (sent by the server from the map's
 * definition): each one a chain of short straight pieces along its sag, a thin dark ribbon turned across the line of
 * sight so it reads as a line from any side, never thinner on screen than about a pixel so distant wires stay
 * unbroken, lit by the world's light where it hangs. All the wires in view go out in one strip, joined by empty
 * triangles, in the leash's render layer.
 */
public final class WireRenderer {
    /** Half the width of a wire seen close up (blocks), and of one seen far off (pixels). */
    static final double HALF=0.016,HALF_PIXELS=0.55;
    private static Identifier dimension;
    /** Seven numbers a wire: x0 y0 z0 x1 y1 z1 sag. */
    private static float[] data=new float[0];
    private static int[] light=new int[0];
    private static long lightAt=Long.MIN_VALUE;
    private WireRenderer(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(MapWires.CHANNEL,(mc,handler,b,response)->{
            var dim=b.readIdentifier();int n=Math.min(b.readVarInt(),MapDefinition.MAX_WIRES);
            float[] d=new float[n*7];for(int i=0;i<d.length;i++)d[i]=b.readFloat();
            mc.execute(()->{dimension=dim;data=d;light=new int[n];lightAt=Long.MIN_VALUE;});
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->{dimension=null;data=new float[0];light=new int[0];}));
        WorldRenderEvents.AFTER_ENTITIES.register(WireRenderer::render);
    }

    /** A point on a wire at t (0..1): the straight line between its ends, let down by its sag in a parabola. */
    static double[] at(float[] d,int o,double t){
        return new double[]{d[o]+(d[o+3]-d[o])*t,d[o+1]+(d[o+4]-d[o+1])*t-4*d[o+6]*t*(1-t),d[o+2]+(d[o+5]-d[o+2])*t};
    }

    private static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();
        if(data.length==0||mc.world==null||dimension==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
        var consumers=ctx.consumers();if(consumers==null)return;
        int n=data.length/7;
        long now=mc.world.getTime();
        if(now<lightAt||now-lightAt>=20){
            lightAt=now;
            for(int i=0;i<n;i++){double[] m=at(data,i*7,0.5);light[i]=WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(m[0],m[1],m[2]));}
        }
        Vec3d cam=ctx.camera().getPos();
        double far=mc.options.getClampedViewDistance()*16+16;
        double perPixel=2*Math.tan(Math.toRadians(mc.options.getFov().getValue()/2.0))/Math.max(1,mc.getWindow().getFramebufferHeight());
        VertexConsumer vc=consumers.getBuffer(RenderLayer.getLeash());
        Matrix4f mat=ctx.matrixStack().peek().getPositionMatrix();
        float[] last=null;
        for(int i=0;i<n;i++){
            int o=i*7;
            double[] mid=at(data,o,0.5);
            if(cam.squaredDistanceTo(mid[0],mid[1],mid[2])>far*far)continue;
            float sag=data[o+6];
            var box=new Box(Math.min(data[o],data[o+3]),Math.min(data[o+1],data[o+4])-sag,Math.min(data[o+2],data[o+5]),
                Math.max(data[o],data[o+3]),Math.max(data[o+1],data[o+4]),Math.max(data[o+2],data[o+5])).expand(0.5);
            if(ctx.frustum()!=null&&!ctx.frustum().isVisible(box))continue;
            double len=Math.sqrt(sq(data[o+3]-data[o])+sq(data[o+4]-data[o+1])+sq(data[o+5]-data[o+2]));
            int segs=MathHelper.clamp((int)Math.ceil(len/1.5),4,32),l=light[i];
            for(int k=0;k<=segs;k++){
                double t=k/(double)segs;double[] p=at(data,o,t);
                double tx=data[o+3]-data[o],ty=data[o+4]-data[o+1]-4*sag*(1-2*t),tz=data[o+5]-data[o+2];
                double vx=p[0]-cam.x,vy=p[1]-cam.y,vz=p[2]-cam.z;
                double sx=ty*vz-tz*vy,sy=tz*vx-tx*vz,sz=tx*vy-ty*vx,sl=Math.sqrt(sx*sx+sy*sy+sz*sz);
                if(sl<1e-6){sx=0;sy=1;sz=0;sl=1;}
                double half=Math.max(HALF,HALF_PIXELS*perPixel*Math.sqrt(vx*vx+vy*vy+vz*vz))/sl;
                float ax=(float)(vx+sx*half),ay=(float)(vy+sy*half),az=(float)(vz+sz*half),bx=(float)(vx-sx*half),by=(float)(vy-sy*half),bz=(float)(vz-sz*half);
                if(k==0&&last!=null){vertex(vc,mat,last[0],last[1],last[2],(int)last[3]);vertex(vc,mat,ax,ay,az,l);}
                vertex(vc,mat,ax,ay,az,l);vertex(vc,mat,bx,by,bz,l);
                if(k==segs)last=new float[]{bx,by,bz,l};
            }
        }
    }
    private static double sq(double v){return v*v;}
    private static void vertex(VertexConsumer vc,Matrix4f m,float x,float y,float z,int light){
        vc.vertex(m,x,y,z).color(34,34,37,255).light(light).next();
    }
}
