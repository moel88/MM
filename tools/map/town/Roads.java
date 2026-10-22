package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.List;

/**
 * The hourglass island's roads (map v3.0): a loop round the middle of each glass and the axis road north and south
 * through both, stopping where the rift's white ground begins. Each is twelve blocks across, a two-block sidewalk half a
 * block up on either side of eight blocks of asphalt with its edge lines and a dashed centre line. The roads follow the
 * ground, smoothed along their length and stepped in half blocks, so they ease up and down the terraces.
 */
final class Roads {
    static final byte NONE=0,ROAD=1,EDGE=2,CENTRE=3,WALK=4,JUNCTION=5;
    private final Townscape t;private final Canvas c;
    final byte[] map=new byte[Plan.W*Plan.D];
    /** The surface level of each road column in half blocks, and how far it lies from its road's centre line. */
    final int[] level=new int[Plan.W*Plan.D];
    private final float[] off=new float[Plan.W*Plan.D];
    private final byte[] dir=new byte[Plan.W*Plan.D],road=new byte[Plan.W*Plan.D];
    /** Every road's centre line, a point every half block: x, z, the tangent's x and z. */
    final List<double[][]> lines=new ArrayList<>();
    Roads(Townscape t){this.t=t;c=t.c;java.util.Arrays.fill(off,99);}

    boolean on(int x,int z){return Canvas.inPlan(x,z)&&map[Canvas.idx(x,z)]!=NONE;}
    /** Whether a column is road or sidewalk, or within {@code margin} of one. */
    boolean near(int x,int z,int margin){
        for(int dx=-margin;dx<=margin;dx++)for(int dz=-margin;dz<=margin;dz++)if(on(x+dx,z+dz))return true;
        return false;
    }

    void plan(){
        lines.add(loop(Plan.LOOP_N_CZ));lines.add(loop(Plan.LOOP_S_CZ));
        lines.add(straight(0,-Plan.AXIS_END,0,-Plan.AXIS_START));
        lines.add(straight(0,Plan.AXIS_START,0,Plan.AXIS_END));
        for(int r=0;r<lines.size();r++)mark(r+1,lines.get(r));
    }
    private static double[][] loop(int cz){
        double perimeter=Math.PI*(3*(Plan.LOOP_RX+Plan.LOOP_RZ)-Math.sqrt((3*Plan.LOOP_RX+Plan.LOOP_RZ)*(Plan.LOOP_RX+3*Plan.LOOP_RZ)));
        int n=(int)(perimeter*2);double[][] p=new double[n+1][];
        for(int k=0;k<=n;k++){
            double a=2*Math.PI*k/n;
            double tx=-Plan.LOOP_RX*Math.sin(a),tz=Plan.LOOP_RZ*Math.cos(a),l=Math.hypot(tx,tz);
            p[k]=new double[]{Plan.loopX(a),Plan.loopZ(cz,a),tx/l,tz/l};
        }
        return p;
    }
    static double[][] straight(double x0,double z0,double x1,double z1){
        double len=Math.hypot(x1-x0,z1-z0);int n=(int)(len*2);double[][] p=new double[n+1][];
        for(int k=0;k<=n;k++){double f=k/(double)n;p[k]=new double[]{x0+(x1-x0)*f,z0+(z1-z0)*f,(x1-x0)/len,(z1-z0)/len};}
        return p;
    }
    /** Marks a road's columns: the nearest centre line point decides a column's part and level. */
    private void mark(int id,double[][] p){
        int n=p.length;
        // the level along the road: the ground under it, averaged over sixteen blocks either way
        double[] g=new double[n];
        for(int k=0;k<n;k++)g[k]=c.ground((int)Math.round(p[k][0]),(int)Math.round(p[k][1]));
        double[] lv=new double[n];
        for(int k=0;k<n;k++){double s=0;int m=0;for(int j=Math.max(0,k-32);j<=Math.min(n-1,k+32);j++){s+=g[j];m++;}lv[k]=s/m;}
        double dash=0;
        for(int k=0;k<n;k++){
            double x=p[k][0],z=p[k][1],tx=p[k][2],tz=p[k][3],nx=-tz,nz=tx;
            dash+=0.5;
            boolean paint=(dash%6)<3;
            for(double o=-Plan.ROAD_HALF+0.25;o<=Plan.ROAD_HALF-0.25;o+=0.5){
                int cx=(int)Math.round(x+nx*o),cz=(int)Math.round(z+nz*o);
                if(!Canvas.inPlan(cx,cz)||Plan.shore(cx,cz)>-1.5)continue;
                int i=Canvas.idx(cx,cz);double a=Math.abs(o);
                byte part=a>=4?WALK:a>=3.4?EDGE:a<0.6&&paint?CENTRE:ROAD;
                if(map[i]!=NONE&&road[i]!=id&&a<4&&off[i]<4){map[i]=JUNCTION;continue;}
                if(map[i]==JUNCTION)continue;
                if(a>=off[i])continue;
                off[i]=(float)a;road[i]=(byte)id;map[i]=part;
                level[i]=(int)Math.round(2*lv[k])+(part==WALK?1:0);
                // the kerb's side for an edge line (the way out from the centre), the road's axis for its centre line
                double sx=Math.signum(o)*nx,sz=Math.signum(o)*nz;
                dir[i]=(byte)(part==CENTRE?(Math.abs(tx)>=Math.abs(tz)?0:1):Direction.getFacing(sx,0,sz).getHorizontal());
            }
        }
    }

    /** Lays the planned roads on the ground. */
    void lay(){
        for(int x=Plan.X0;x<Plan.X0+Plan.W;x++)for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++){
            int i=Canvas.idx(x,z);
            byte b=map[i];if(b==NONE)continue;
            if(t.terrain.kind[i]==Terrain.SEA)continue;
            int lv=level[i],y=lv>>1;boolean half=(lv&1)==1;
            BlockState full,slab;
            switch(b){
                case EDGE->{Direction side=Direction.fromHorizontal(dir[i]);full=TB.edgeLine(side,false);slab=TB.edgeLine(side,true);}
                case CENTRE->{full=TB.painted("asphalt_center_line",dir[i]==0?Direction.Axis.X:Direction.Axis.Z);slab=TB.asphaltSlab();}
                case WALK->{full=TB.PAVING;slab=TB.pavingSlab();}
                default->{full=TB.ASPHALT;slab=TB.asphaltSlab();}
            }
            // fill up to the surface from the ground below, or cut the ground down to it; over the canal a culvert
            int wet=t.terrain.water[i];
            for(int yy=Math.min(c.ground(x,z)+1,y);yy<y;yy++)if(yy>wet)c.set(x,yy,z,wet!=0?Blocks.STONE_BRICKS.getDefaultState():Blocks.STONE.getDefaultState());
            if(b==WALK){
                if(half){c.set(x,y,z,Blocks.STONE.getDefaultState());c.set(x,y+1,z,slab);}else c.set(x,y,z,full);
            }else if(half){c.set(x,y,z,TB.ASPHALT);c.set(x,y+1,z,slab);}
            else c.set(x,y,z,full);
            int surface=half?y+1:y;
            c.ground(x,z,surface);
            for(int k=1;k<=10;k++)if(!c.air(x,surface+k,z))c.set(x,surface+k,z,Blocks.AIR.getDefaultState());
            if(!half&&b==ROAD&&Pal.hash(x,9,z)<0.0025)c.set(x,y+1,z,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,false,false));
        }
    }
}
