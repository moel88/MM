package dev.sangwoo.celestialfall.client.town;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sangwoo.celestialfall.map.MapBackdrop;
import dev.sangwoo.celestialfall.map.MapDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.*;

/**
 * Draws the map's backdrop: the mountains of the far coast across the water (sent by the server from the map's
 * definition), too far off to be blocks and beyond any view distance. Each is a hill of rings around its summit, its
 * shore and ridges shaped by its seed, lit by the sun and hazed toward the colour of the fog by its distance, the way
 * far land fades over the sea. It goes down right after the sky and before anything else, shrunk toward the camera so
 * it keeps its place and size in view, without depth: everything in the world draws over it. A shader pack (Iris)
 * redraws the sky over whatever came before the world, so with one the backdrop goes down after the world instead, at
 * the far end of the view's depth where only the sky is left, still without depth of its own: the pack takes it for
 * sky (its low clouds can pass in front). Far hills go first, and each hill from its summit out to its shore, so
 * nearer slopes cover farther ones.
 */
public final class BackdropRenderer {
    static final int RINGS=22,SEGMENTS=96;
    /** The water's surface the hills rise from. */
    static final float SEA=62.4f;
    /** Haze takes half a hill's colour at this distance (blocks). */
    static final double HAZE_HALF=1500;
    /**
     * The backdrop's colour under a shader pack, which hazes it itself: the blue of a far mountain, before the pack's
     * lift. Measured on photo 1, whose mountain is the sky's colour times about 0.52, 0.71 and 0.89; the pack makes of
     * a colour c about 0.5 + 0.55 c, so the hills are given no red and much blue, and the sun shades their slopes.
     */
    static final double[] SHADER_TINT={0.0,0.18,0.53};
    private static Identifier dimension;
    private static Hill[] hills=new Hill[0];
    private BackdropRenderer(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(MapBackdrop.CHANNEL,(mc,handler,b,response)->{
            var dim=b.readIdentifier();int n=Math.min(b.readVarInt(),MapDefinition.MAX_PEAKS);
            var peaks=new ArrayList<MapDefinition.Peak>();
            for(int i=0;i<n;i++)peaks.add(new MapDefinition.Peak(b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readInt()));
            mc.execute(()->{dimension=dim;hills=peaks.stream().map(Hill::new).toArray(Hill[]::new);});
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->{dimension=null;hills=new Hill[0];}));
        WorldRenderEvents.AFTER_SETUP.register(ctx->render(ctx,false));
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->render(ctx,true));
    }

    /** How many mountains the server has sent for the dimension the player is in now. */
    public static int count(){
        var mc=MinecraftClient.getInstance();
        return mc.world!=null&&dimension!=null&&mc.world.getRegistryKey().getValue().equals(dimension)?hills.length:0;
    }

    /** One mountain: its points (a summit, then ring after ring out to the shore), their normals and colours. */
    static final class Hill {
        final double cx,cz,reach;
        final double[] pos;final float[] normal,base;
        /** Triangles as point indices, from the summit outward. */
        final int[] tris;
        Hill(MapDefinition.Peak p){
            cx=p.x();cz=p.z();reach=Math.max(p.rx(),p.rz())*1.3;
            var r=new Random(p.seed()*341873128712L+132897987541L);
            // the shore wanders in and out, and ridges run down from the top between valleys
            double[] sa=new double[7],sp=new double[7],ra=new double[10],rp=new double[10];
            for(int k=1;k<7;k++){sa[k]=r.nextGaussian()*0.10/k;sp[k]=r.nextDouble()*Math.PI*2;}
            for(int k=2;k<10;k++){ra[k]=r.nextGaussian()*0.55/Math.sqrt(k);rp[k]=r.nextDouble()*Math.PI*2;}
            int n=1+RINGS*SEGMENTS;
            pos=new double[n*3];normal=new float[n*3];base=new float[n*3];
            double height=p.top()-SEA;
            pos[0]=cx;pos[1]=p.top();pos[2]=cz;
            for(int i=1;i<=RINGS;i++){
                double rho=i/(double)RINGS;
                for(int j=0;j<SEGMENTS;j++){
                    double t=j*Math.PI*2/SEGMENTS,shore=1,ridge=0;
                    for(int k=1;k<7;k++)shore+=sa[k]*Math.sin(k*t+sp[k]);
                    for(int k=2;k<10;k++)ridge+=ra[k]*Math.sin(k*t+rp[k]+rho*1.7);
                    double fall=Math.pow(1-rho*rho,1.6)*(1+0.32*ridge*4*rho*(1-rho));
                    double x=cx+p.rx()*rho*shore*Math.cos(t),z=cz+p.rz()*rho*shore*Math.sin(t);
                    int v=1+(i-1)*SEGMENTS+j;
                    pos[v*3]=x;pos[v*3+1]=i==RINGS?SEA-1.5:SEA+height*Math.max(0,fall);pos[v*3+2]=z;
                }
            }
            List<int[]> list=new ArrayList<>();
            for(int j=0;j<SEGMENTS;j++)list.add(new int[]{0,ring(1,j),ring(1,j+1)});
            for(int i=1;i<RINGS;i++)for(int j=0;j<SEGMENTS;j++){
                list.add(new int[]{ring(i,j),ring(i+1,j),ring(i+1,j+1)});
                list.add(new int[]{ring(i,j),ring(i+1,j+1),ring(i,j+1)});
            }
            tris=new int[list.size()*3];
            for(int k=0;k<list.size();k++){
                int[] t=list.get(k);
                // every triangle faces up and out of the hill
                double[] nrm=cross(t[0],t[1],t[2]);
                if(nrm[1]<0){int s=t[1];t[1]=t[2];t[2]=s;nrm[0]=-nrm[0];nrm[1]=-nrm[1];nrm[2]=-nrm[2];}
                for(int c=0;c<3;c++){tris[k*3+c]=t[c];for(int a=0;a<3;a++)normal[t[c]*3+a]+=(float)nrm[a];}
            }
            for(int v=0;v<n;v++){
                float l=(float)Math.sqrt(normal[v*3]*normal[v*3]+normal[v*3+1]*normal[v*3+1]+normal[v*3+2]*normal[v*3+2]);
                if(l>0)for(int a=0;a<3;a++)normal[v*3+a]/=l;
                // wooded lower slopes, greyer rock and grass toward the top, a little of each everywhere
                double up=MathHelper.clamp((pos[v*3+1]-SEA)/Math.max(1,height),0,1),tint=r.nextGaussian()*0.04;
                base[v*3]=(float)(0.20+0.10*up+tint);base[v*3+1]=(float)(0.28+0.07*up+tint);base[v*3+2]=(float)(0.25+0.12*up+tint);
            }
        }
        private static int ring(int i,int j){return 1+(i-1)*SEGMENTS+Math.floorMod(j,SEGMENTS);}
        private double[] cross(int a,int b,int c){
            double ux=pos[b*3]-pos[a*3],uy=pos[b*3+1]-pos[a*3+1],uz=pos[b*3+2]-pos[a*3+2];
            double vx=pos[c*3]-pos[a*3],vy=pos[c*3+1]-pos[a*3+1],vz=pos[c*3+2]-pos[a*3+2];
            return new double[]{uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx};
        }
        double distance(Vec3d cam){return Math.hypot(cx-cam.x,cz-cam.z);}
    }

    private static Object iris;private static java.lang.reflect.Method irisInUse;private static boolean irisLooked;
    /** Whether a shader pack is drawing the world (Iris's own answer when it is there). */
    static boolean shaders(){
        if(!irisLooked){
            irisLooked=true;
            try{var c=Class.forName("net.irisshaders.iris.api.v0.IrisApi");iris=c.getMethod("getInstance").invoke(null);irisInUse=c.getMethod("isShaderPackInUse");}
            catch(ReflectiveOperationException|LinkageError e){iris=null;}
        }
        try{return iris!=null&&(Boolean)irisInUse.invoke(iris);}catch(ReflectiveOperationException e){return false;}
    }

    private static void render(WorldRenderContext ctx,boolean late){
        var mc=MinecraftClient.getInstance();
        if(hills.length==0||mc.world==null||dimension==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
        // a shader pack redraws what was under the world before it, so with one the backdrop goes down after the world
        boolean packs=shaders();
        if(late!=packs)return;
        Vec3d cam=ctx.camera().getPos();
        // shrink the whole backdrop toward the camera (which keeps every point where it is in view) until its farthest
        // point is well inside the fog, so neither the view's depth nor the fog cuts it off; drawn after the world, it
        // goes out near the far end of the view's depth instead, so it only shows where nothing else was drawn
        double farthest=1;
        for(var h:hills)farthest=Math.max(farthest,h.distance(cam)+h.reach);
        double draw=late?mc.gameRenderer.getFarPlaneDistance()*0.9:MathHelper.clamp(mc.gameRenderer.getViewDistance()*0.4,12,56),s=draw/farthest;
        float[] fog=RenderSystem.getShaderFogColor();
        double angle=mc.world.getSkyAngle(ctx.tickDelta())*Math.PI*2;
        double sx=-Math.sin(angle),sy=Math.cos(angle);
        Hill[] order=hills.clone();
        Arrays.sort(order,Comparator.comparingDouble((Hill h)->h.distance(cam)).reversed());
        Matrix4f mat=ctx.matrixStack().peek().getPositionMatrix();
        BufferBuilder buf=Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR_LIGHT);
        for(var h:order){
            int n=h.pos.length/3;
            float[] col=new float[n*3];
            for(int v=0;v<n;v++){
                double dx=h.pos[v*3]-cam.x,dz=h.pos[v*3+2]-cam.z;
                double lit=0.62+0.55*Math.max(0,h.normal[v*3]*sx+h.normal[v*3+1]*sy);
                double haze=1-Math.pow(0.5,Math.sqrt(dx*dx+dz*dz)/HAZE_HALF);
                for(int a=0;a<3;a++){
                    // a shader pack takes the backdrop for sky and lifts it toward its own haze, so it is given the
                    // deep blue that comes out as the photo's mountain
                    double c=packs?SHADER_TINT[a]*lit:h.base[v*3+a]*lit*(1-haze)+fog[a]*haze;
                    col[v*3+a]=(float)MathHelper.clamp(c,0,1);
                }
            }
            for(int k=0;k<h.tris.length;k++){
                int v=h.tris[k];
                buf.vertex(mat,(float)((h.pos[v*3]-cam.x)*s),(float)((h.pos[v*3+1]-cam.y)*s),(float)((h.pos[v*3+2]-cam.z)*s))
                    .color(col[v*3],col[v*3+1],col[v*3+2],1f).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next();
            }
        }
        var lightmap=mc.gameRenderer.getLightmapTextureManager();
        lightmap.enable();
        RenderSystem.setShader(GameRenderer::getRenderTypeLeashProgram);
        if(late){RenderSystem.enableDepthTest();RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);}
        else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        lightmap.disable();
    }
}
