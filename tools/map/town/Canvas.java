package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * Writes the town into the world: plain block states without neighbour updates (water stays still, nothing falls or
 * pops), with the blocks whose look depends on their neighbours (fences, panes, walls, stairs) shaped afterwards the
 * way world generation shapes them. Tracks what it placed and a 2D occupancy grid so lots never overlap.
 */
final class Canvas {
    static final int FLAGS=Block.NOTIFY_LISTENERS|Block.FORCE_STATE;
    final ServerWorld w;
    private final BlockPos.Mutable m=new BlockPos.Mutable();
    private final LongOpenSet post=new LongOpenSet();
    long placed;
    /** The surface height of every column of the plan (the top solid block), filled by the terrain. */
    final int[] ground;
    /** What owns a column (0 free, else a code), so later pieces keep clear of earlier ones. */
    final byte[] owner;
    Canvas(ServerWorld w){this.w=w;ground=new int[Plan.W*Plan.D];owner=new byte[Plan.W*Plan.D];Arrays.fill(ground,Integer.MIN_VALUE);}

    static boolean inPlan(int x,int z){return x>=Plan.X0&&x<Plan.X0+Plan.W&&z>=Plan.Z0&&z<Plan.Z0+Plan.D;}
    static int idx(int x,int z){return (x-Plan.X0)*Plan.D+(z-Plan.Z0);}
    int ground(int x,int z){return inPlan(x,z)?ground[idx(x,z)]:Plan.SEA-12;}
    void ground(int x,int z,int y){if(inPlan(x,z))ground[idx(x,z)]=y;}
    int owner(int x,int z){return inPlan(x,z)?owner[idx(x,z)]:-1;}
    void own(int x,int z,int code){if(inPlan(x,z))owner[idx(x,z)]=(byte)code;}
    /** Whether a rectangle (inclusive) is free in the occupancy grid. */
    boolean free(int x0,int z0,int x1,int z1){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)if(owner(x,z)!=0)return false;
        return true;
    }
    void claim(int x0,int z0,int x1,int z1,int code){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)own(x,z,code);
    }

    void set(int x,int y,int z,BlockState s){
        m.set(x,y,z);
        w.setBlockState(m,s,FLAGS);placed++;
        Block b=s.getBlock();
        if(b instanceof FenceBlock||b instanceof PaneBlock||b instanceof WallBlock||b instanceof StairsBlock||b instanceof FenceGateBlock)post.add(m.asLong());
    }
    void set(int x,int y,int z,Block b){set(x,y,z,b.getDefaultState());}
    BlockState get(int x,int y,int z){m.set(x,y,z);return w.getBlockState(m);}
    boolean air(int x,int y,int z){return get(x,y,z).isAir();}
    void fill(int x0,int y0,int z0,int x1,int y1,int z1,BlockState s){
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int y=Math.min(y0,y1);y<=Math.max(y0,y1);y++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)set(x,y,z,s);
    }
    void fill(int x0,int y0,int z0,int x1,int y1,int z1,Block b){fill(x0,y0,z0,x1,y1,z1,b.getDefaultState());}
    /** Air over a box (clearing room for a building or a road cut). */
    void clear(int x0,int y0,int z0,int x1,int y1,int z1){fill(x0,y0,z0,x1,y1,z1,Blocks.AIR.getDefaultState());}

    // ---- shaped blocks ----
    static BlockState stairs(Block b,Direction facing,boolean top){
        return b.getDefaultState().with(StairsBlock.FACING,facing).with(StairsBlock.HALF,top?BlockHalf.TOP:BlockHalf.BOTTOM);
    }
    static BlockState slab(Block b,boolean top){return b.getDefaultState().with(SlabBlock.TYPE,top?SlabType.TOP:SlabType.BOTTOM);}
    static BlockState axis(Block b,Direction.Axis a){return b.getDefaultState().with(Properties.AXIS,a);}
    static BlockState leaves(Block b){return b.getDefaultState().with(LeavesBlock.PERSISTENT,true);}
    static BlockState facing(Block b,Direction d){return b.getDefaultState().with(Properties.HORIZONTAL_FACING,d);}
    static BlockState trapdoor(Block b,Direction facing,boolean open,boolean top){
        return b.getDefaultState().with(TrapdoorBlock.FACING,facing).with(TrapdoorBlock.OPEN,open).with(TrapdoorBlock.HALF,top?BlockHalf.TOP:BlockHalf.BOTTOM);
    }
    void door(int x,int y,int z,Block b,Direction facing,boolean rightHinge){
        var s=b.getDefaultState().with(DoorBlock.FACING,facing).with(DoorBlock.HINGE,rightHinge?DoorHinge.RIGHT:DoorHinge.LEFT);
        set(x,y,z,s.with(DoorBlock.HALF,DoubleBlockHalf.LOWER));set(x,y+1,z,s.with(DoorBlock.HALF,DoubleBlockHalf.UPPER));
    }
    void tallPlant(int x,int y,int z,Block b){
        set(x,y,z,b.getDefaultState().with(TallPlantBlock.HALF,DoubleBlockHalf.LOWER));set(x,y+1,z,b.getDefaultState().with(TallPlantBlock.HALF,DoubleBlockHalf.UPPER));
    }
    void hangingLantern(int x,int y,int z,boolean soul){set(x,y,z,(soul?Blocks.SOUL_LANTERN:Blocks.LANTERN).getDefaultState().with(LanternBlock.HANGING,true));}
    void lantern(int x,int y,int z){set(x,y,z,Blocks.LANTERN.getDefaultState());}
    /** A vine on the side of a block, hanging on the face toward {@code toWall}. */
    void vine(int x,int y,int z,Direction toWall){
        var p=switch(toWall){case NORTH->VineBlock.NORTH;case SOUTH->VineBlock.SOUTH;case EAST->VineBlock.EAST;default->VineBlock.WEST;};
        set(x,y,z,Blocks.VINE.getDefaultState().with(p,true));
    }
    /** A sign on a wall, facing out toward {@code facing}, with up to four lines. */
    void wallSign(int x,int y,int z,Block sign,Direction facing,DyeColor color,boolean glow,String... lines){
        set(x,y,z,sign.getDefaultState().with(WallSignBlock.FACING,facing));
        m.set(x,y,z);
        if(w.getBlockEntity(m) instanceof SignBlockEntity be){
            Text[] t=new Text[4];for(int i=0;i<4;i++)t[i]=Text.literal(i<lines.length?lines[i]:"");
            be.setText(new SignText(t,t,color,glow),true);be.markDirty();
        }
    }
    void standingSign(int x,int y,int z,Block sign,int rotation,DyeColor color,boolean glow,String... lines){
        set(x,y,z,sign.getDefaultState().with(SignBlock.ROTATION,rotation&15));
        m.set(x,y,z);
        if(w.getBlockEntity(m) instanceof SignBlockEntity be){
            Text[] t=new Text[4];for(int i=0;i<4;i++)t[i]=Text.literal(i<lines.length?lines[i]:"");
            be.setText(new SignText(t,t,color,glow),true);be.setText(new SignText(t,t,color,glow),false);be.markDirty();
        }
    }

    /** Shapes fences, panes, walls and stairs from their neighbours, as world generation does. */
    int finish(){
        int changed=0;
        for(long p:post.values()){
            m.set(p);var s=w.getBlockState(m);
            var n=Block.postProcessState(s,w,m);
            if(n!=s){w.setBlockState(m,n,FLAGS);changed++;}
        }
        return changed;
    }

    /** A small open-addressing set of longs (block positions), to avoid boxing millions of positions. */
    static final class LongOpenSet{
        private long[] keys=new long[1<<16];private boolean[] used=new boolean[1<<16];private int size;
        void add(long k){
            if(size*2>=keys.length)grow();
            int i=slot(k,keys.length);
            while(used[i]){if(keys[i]==k)return;i=(i+1)&(keys.length-1);}
            used[i]=true;keys[i]=k;size++;
        }
        private static int slot(long k,int n){long h=k*0x9E3779B97F4A7C15L;return (int)(h>>>40)&(n-1);}
        private void grow(){
            long[] ok=keys;boolean[] ou=used;keys=new long[ok.length*2];used=new boolean[ok.length*2];size=0;
            for(int i=0;i<ok.length;i++)if(ou[i])add(ok[i]);
        }
        long[] values(){long[] out=new long[size];int k=0;for(int i=0;i<keys.length;i++)if(used[i])out[k++]=keys[i];return out;}
    }
}
