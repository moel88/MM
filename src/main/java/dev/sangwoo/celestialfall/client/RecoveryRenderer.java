package dev.sangwoo.celestialfall.client;

import dev.sangwoo.celestialfall.terrain.*;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import java.util.*;

/** Ordinary Minecraft translucent block-atlas rendering. No framebuffer, depth copy or shader pack.
 * Hidden faces are omitted and the original baked block meshes are cached per chunk archive. */
public final class RecoveryRenderer implements BlockEntityRenderer<RecoveryAnchor> {
    private record Face(BakedQuad quad,int color,float shade) {}
    private record Piece(RecoveryAnchor.Cell cell,List<Face> faces,int light) {}
    private record Mesh(int revision,List<Piece> pieces) {}
    private static net.minecraft.client.world.ClientWorld observedWorld;
    private final Map<RecoveryAnchor,Mesh> meshes=new WeakHashMap<>();
    public RecoveryRenderer(BlockEntityRendererFactory.Context context) {}
    public static void initialize() {
        BlockEntityRendererRegistry.register(TerrainRestoration.ANCHOR_ENTITY,RecoveryRenderer::new);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(observedWorld!=client.world){TerrainRestoration.clearClient(observedWorld);observedWorld=client.world;}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{TerrainRestoration.clearClient(observedWorld);observedWorld=null;});
    }
    @Override public boolean rendersOutsideBoundingBox(RecoveryAnchor a) { return true; }
    @Override public int getRenderDistance() { return 192; }
    @Override public void render(RecoveryAnchor a,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay) {
        var world=a.getWorld();if(world==null||a.cells.isEmpty())return;
        double now=world.getTime()+delta;
        boolean visible=false;
        for(var c:a.cells.values())if(RestorationTiming.alpha(now,c.solidAt)>0){visible=true;break;}
        if(!visible)return;
        Mesh mesh=meshes.get(a);
        if(mesh==null||mesh.revision!=a.revision){mesh=build(a);meshes.put(a,mesh);}
        var consumer=buffers.getBuffer(RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
        var matrix=matrices.peek().getPositionMatrix();var normals=matrices.peek().getNormalMatrix();
        for(Piece piece:mesh.pieces) {
            float alpha=(float)RestorationTiming.alpha(now,piece.cell.solidAt);if(alpha<=0)continue;
            float dx=piece.cell.pos.getX()-a.getPos().getX(),dy=piece.cell.pos.getY()-a.getPos().getY(),dz=piece.cell.pos.getZ()-a.getPos().getZ();
            for(Face face:piece.faces) {
                int[] v=face.quad.getVertexData();int stride=v.length/4;
                var normal=face.quad.getFace().getVector();
                for(int i=0;i<4;i++) {
                    int o=i*stride,packed=v[o+3];
                    float r=((face.color>>16)&255)/255f*((packed)&255)/255f*face.shade;
                    float g=((face.color>>8)&255)/255f*((packed>>8)&255)/255f*face.shade;
                    float b=(face.color&255)/255f*((packed>>16)&255)/255f*face.shade;
                    consumer.vertex(matrix,Float.intBitsToFloat(v[o])+dx,Float.intBitsToFloat(v[o+1])+dy,Float.intBitsToFloat(v[o+2])+dz)
                        .color(r,g,b,alpha).texture(Float.intBitsToFloat(v[o+4]),Float.intBitsToFloat(v[o+5]))
                        .overlay(OverlayTexture.DEFAULT_UV).light(piece.light).normal(normals,normal.getX(),normal.getY(),normal.getZ()).next();
                }
            }
        }
    }
    private Mesh build(RecoveryAnchor a) {
        var mc=MinecraftClient.getInstance();var world=a.getWorld();var pieces=new ArrayList<Piece>();var random=Random.create();
        for(var c:a.cells.values()) {
            // Built-in entity blocks (chests/signs) use their particle-material silhouette until solid.
            var model=mc.getBlockRenderManager().getModel(c.state);var faces=new ArrayList<Face>();
            for(Direction d:Direction.values()) {
                BlockPos neighbor=c.pos.offset(d);var saved=TerrainRestoration.original(world,neighbor);
                BlockState ns=saved==null?world.getBlockState(neighbor):saved.state;
                if(ns.isOpaqueFullCube(world,neighbor))continue;
                random.setSeed(c.state.getRenderingSeed(c.pos));
                for(var q:model.getQuads(c.state,d,random))add(faces,q,c,world,mc);
            }
            random.setSeed(c.state.getRenderingSeed(c.pos));
            for(var q:model.getQuads(c.state,null,random))add(faces,q,c,world,mc);
            if(c.state.getRenderType()==BlockRenderType.ENTITYBLOCK_ANIMATED&&faces.isEmpty()) {
                // The ordinary stone cube supplies topology only; UVs come from the original material.
                var cube=mc.getBlockRenderManager().getModel(Blocks.STONE.getDefaultState());var sprite=model.getParticleSprite();
                for(Direction d:Direction.values())for(var q:cube.getQuads(Blocks.STONE.getDefaultState(),d,random)) {
                    int[] v=q.getVertexData().clone();int stride=v.length/4;
                    float[] us={sprite.getMinU(),sprite.getMinU(),sprite.getMaxU(),sprite.getMaxU()};
                    float[] vs={sprite.getMinV(),sprite.getMaxV(),sprite.getMaxV(),sprite.getMinV()};
                    for(int i=0;i<4;i++){v[i*stride+4]=Float.floatToRawIntBits(us[i]);v[i*stride+5]=Float.floatToRawIntBits(vs[i]);}
                    add(faces,new BakedQuad(v,-1,d,sprite,true),c,world,mc);
                }
            }
            if(!faces.isEmpty())pieces.add(new Piece(c,faces,WorldRenderer.getLightmapCoordinates(world,c.pos)));
        }
        return new Mesh(a.revision,pieces);
    }
    private void add(List<Face> faces,BakedQuad q,RecoveryAnchor.Cell c,net.minecraft.world.World w,MinecraftClient mc) {
        int color=q.hasColor()?mc.getBlockColors().getColor(c.state,w,c.pos,q.getColorIndex()):0xffffff;
        faces.add(new Face(q,color,w.getBrightness(q.getFace(),q.hasShade())));
    }
}
