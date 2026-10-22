package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.List;

/**
 * The hourglass island's roads (map v3.0, 2000 x 2000): the axis road north and south through both glasses, stopping
 * where the rift's white ground begins, twelve blocks across (a two-block sidewalk half a block up on either side of
 * eight blocks of asphalt with its edge lines and a dashed centre line). No loop roads (the user's request). Each zone
 * has its own straight streets off it: ten across in the old town at the mountain's foot, the capital's paved in
 * stone inside its walls (a main avenue from the axis road through the west gate to the castle, a road from the
 * south-west gate toward the rift, streets between), and twenty across (avenues) in the cyber city; they stop short of
 * the shore, the rift's white, the canal, the mountain and the castle's plateau. The roads follow the ground, smoothed
 * along their length and stepped in half blocks, so they ease up and down the slopes.
 */
final class Roads {
    static final byte NONE=0,ROAD=1,EDGE=2,CENTRE=3,WALK=4,JUNCTION=5;
    /** How a road is surfaced: asphalt with painted lines, stone paving without (the capital's), or wet asphalt with dark paving (the cyber city's). */
    static final int ASPHALT=0,STONE=1,WET=2;
    private final Townscape t;private final Canvas c;
    final byte[] map=new byte[Plan.W*Plan.D];
    /** The surface level of each road column in half blocks, and how far it lies from its road's centre line. */
    final int[] level=new int[Plan.W*Plan.D];
    private final float[] off=new float[Plan.W*Plan.D];
    private final byte[] dir=new byte[Plan.W*Plan.D],road=new byte[Plan.W*Plan.D],asphalt=new byte[Plan.W*Plan.D],paving=new byte[Plan.W*Plan.D];
    /** Every road's centre line, a point every half block: x, z, the tangent's x and z. */
    final List<double[][]> lines=new ArrayList<>();
    /** Each line's half width (with its sidewalks), its zone (-1 for the axis road) and its surface. */
    final List<Integer> halfs=new ArrayList<>(),zones=new ArrayList<>(),styles=new ArrayList<>();
    /** The capital's main avenue (west gate to the castle) and the road out of its south-west gate. */
    double[][] avenue,gateRoad;
    Roads(Townscape t){this.t=t;c=t.c;java.util.Arrays.fill(off,99);}
    static final BlockState WET_ASPHALT=TB.of("wet_asphalt"),CYBER_PAVING=TB.of("cyber_paving");

    boolean on(int x,int z){return Canvas.inPlan(x,z)&&map[Canvas.idx(x,z)]!=NONE;}
    /** Whether a column is road or sidewalk, or within {@code margin} of one. */
    boolean near(int x,int z,int margin){
        for(int dx=-margin;dx<=margin;dx++)for(int dz=-margin;dz<=margin;dz++)if(on(x+dx,z+dz))return true;
        return false;
    }
    /** The streets of a zone (its own streets, not the axis road). */
    List<double[][]> streets(int zone){
        List<double[][]> out=new ArrayList<>();
        for(int i=0;i<lines.size();i++)if(zones.get(i)==zone)out.add(lines.get(i));
        return out;
    }
    int half(double[][] line){int i=lines.indexOf(line);return i<0?Plan.ROAD_HALF:halfs.get(i);}

    void plan(){
        add(straight(0,-Plan.AXIS_END,0,-Plan.AXIS_START),Plan.ROAD_HALF,-1,ASPHALT);
        add(straight(0,Plan.AXIS_START,0,Plan.AXIS_END),Plan.ROAD_HALF,-1,ASPHALT);
        // the capital: the main avenue through the west gate to the castle's plateau, the road from the south-west gate
        double[] sw=Plan.wallAt(Plan.GATE_SW);
        avenue=straight(Plan.ROAD_HALF-1,Plan.CASTLE_Z,Plan.CASTLE_X-Plan.PLATEAU_R-2,Plan.CASTLE_Z);
        add(avenue,Plan.MAIN_HALF,Plan.CAPITAL,STONE);
        gateRoad=straight(sw[0]+4*Math.cos(Plan.GATE_SW),sw[1]+4*Math.sin(Plan.GATE_SW),Plan.ROAD_HALF-1,-Plan.AXIS_START-60);
        add(gateRoad,Plan.STREET_HALF,Plan.CAPITAL,STONE);
        grid(Plan.TOWN,-1,-1,0,0,74,70,Plan.STREET_HALF,ASPHALT);
        grid(Plan.CAPITAL,1,-1,Plan.WALL_X,Plan.WALL_Z,72,68,Plan.STREET_HALF,STONE);
        grid(Plan.CYBER,1,1,0,0,104,104,Plan.AVENUE_HALF,WET);
        for(int r=0;r<lines.size();r++)mark(r+1,lines.get(r),halfs.get(r),styles.get(r));
        Town.log("roads: "+lines.size()+" ("+streets(Plan.TOWN).size()+" town streets, "+streets(Plan.CAPITAL).size()+" capital, "+streets(Plan.CYBER).size()+" cyber)");
    }
    private void add(double[][] line,int half,int zone,int style){lines.add(line);halfs.add(half);zones.add(zone);styles.add(style);}

    static double[][] straight(double x0,double z0,double x1,double z1){
        double len=Math.hypot(x1-x0,z1-z0);int n=(int)(len*2);double[][] p=new double[n+1][];
        for(int k=0;k<=n;k++){double f=k/(double)n;p[k]=new double[]{x0+(x1-x0)*f,z0+(z1-z0)*f,(x1-x0)/len,(z1-z0)/len};}
        return p;
    }

    /**
     * A zone's own streets: lines running north-south {@code sx} blocks apart and east-west {@code sz} apart, counted
     * from (ox, oz) outward on the zone's side of the axis road and the rift ({@code ex}, {@code ez}), each kept where
     * it runs through open land of the zone, in stretches at least sixty blocks long.
     */
    private void grid(int zone,int ex,int ez,double ox,double oz,int sx,int sz,int half,int style){
        double xa=ox==0?ex*56:ox-sx*(int)(Plan.WALL_R/sx),za=oz==0?ez*(Plan.RIFT+60):oz-sz*(int)(Plan.WALL_R/sz);
        for(int k=0;k<60;k++){
            double x=ox==0?xa+ex*k*sx:xa+k*sx;
            if(Math.abs(x)>Plan.WIDE)break;
            runs(zone,half,style,x,ez*Plan.RIFT*0.6,x,ez*Plan.END,true);
        }
        for(int k=0;k<60;k++){
            double z=oz==0?za+ez*k*sz:za+k*sz;
            if(Math.abs(z)>Plan.END)break;
            runs(zone,half,style,0,z,ex*Plan.WIDE,z,false);
        }
    }
    private void runs(int zone,int half,int style,double x0,double z0,double x1,double z1,boolean ns){
        double len=Math.hypot(x1-x0,z1-z0),dx=(x1-x0)/len,dz=(z1-z0)/len;
        double start=-1;
        for(double s=0;s<=len+1;s+=1){
            double x=x0+dx*s,z=z0+dz*s;
            boolean ok=s<=len&&streetOk(zone,x,z,ns,half);
            if(ok&&start<0)start=s;
            if(!ok&&start>=0){
                double end=s-1;
                if(end-start>=60)add(straight(x0+dx*start,z0+dz*start,x0+dx*end,z0+dz*end),half,zone,style);
                start=-1;
            }
        }
    }
    /** Whether a zone's street may run through a point (going north-south or east-west). */
    private boolean streetOk(int zone,double x,double z,boolean ns,int half){
        int ix=(int)Math.floor(x),iz=(int)Math.floor(z);
        if(!Canvas.inPlan(ix,iz))return false;
        // its own zone, or the axis road it runs on to
        boolean axis=!ns&&Math.abs(x)<=Plan.ROAD_HALF+1;
        if(Plan.zone(x,z)!=zone&&!axis)return false;
        if(Plan.shore(x,z)>-(half+22))return false;
        if(Math.hypot(x,z)<Plan.riftEdge(x,z)+half+14)return false;
        // never along the axis road
        if(ns&&Math.abs(x)<half+Plan.ROAD_HALF+26)return false;
        switch(zone){
            case Plan.TOWN->{
                // not up the mountain: only on its gentle foot
                if(Math.hypot(x-Plan.MOUNT_X,z-Plan.MOUNT_Z)<Plan.MOUNT_R*0.72||c.ground(ix,iz)>90)return false;
                if(z>Plan.CANAL_Z0-14&&z<Plan.CANAL_Z1+14&&Math.abs(x-Plan.canalX(z))<Plan.canalHalf(z)+8+half)return false;
                // nor over the shrine's stairway
                if(Math.abs(z-Plan.MOUNT_Z)<half+5&&x<Plan.MOUNT_X+Plan.MOUNT_R+30)return false;
            }
            case Plan.CAPITAL->{
                // inside the walls, clear of the castle's plateau, the cathedral and the main avenue's sides
                if(Plan.insideWalls(x,z)<half+12)return false;
                if(Math.hypot(x-Plan.CASTLE_X,z-Plan.CASTLE_Z)<Plan.PLATEAU_R+18)return false;
                if(Math.abs(x-Plan.CATHEDRAL_X)<26+half&&z>Plan.CATHEDRAL_Z-40-half&&z<Plan.CATHEDRAL_Z+70+half)return false;
                if(!ns&&Math.abs(z-Plan.CASTLE_Z)<Plan.MAIN_HALF+half+24)return false;
            }
            default->{}
        }
        return true;
    }

    /** Marks a road's columns: the nearest centre line point decides a column's part and level. */
    private void mark(int id,double[][] p,int half,int style){
        int n=p.length,walk=half>=8?3:2;double asp=half-walk;
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
            for(double o=-half+0.25;o<=half-0.25;o+=0.5){
                int cx=(int)Math.round(x+nx*o),cz=(int)Math.round(z+nz*o);
                if(!Canvas.inPlan(cx,cz)||Plan.shore(cx,cz)>-1.5)continue;
                int i=Canvas.idx(cx,cz);double a=Math.abs(o);
                byte part=a>=asp?WALK:a>=asp-0.6?EDGE:a<0.6&&paint?CENTRE:ROAD;
                if(map[i]!=NONE&&road[i]!=id&&a<asp&&off[i]<asphalt[i]){map[i]=JUNCTION;continue;}
                if(map[i]==JUNCTION)continue;
                if(a>=off[i])continue;
                if(style!=ASPHALT&&(part==EDGE||part==CENTRE))part=ROAD;
                off[i]=(float)a;road[i]=(byte)id;map[i]=part;asphalt[i]=(byte)asp;paving[i]=(byte)style;
                level[i]=(int)Math.round(2*lv[k])+(part==WALK&&style!=WET?1:0);
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
            if(paving[i]==WET){
                full=b==WALK?CYBER_PAVING:WET_ASPHALT;slab=b==WALK?TB.pavingSlab():TB.asphaltSlab();
            }else if(paving[i]==STONE){
                // the capital's paving: grey stone setts, a border of bricks at the sides
                double h=Pal.hash(x,17,z);
                if(b==WALK){full=Blocks.STONE_BRICKS.getDefaultState();slab=Blocks.STONE_BRICK_SLAB.getDefaultState();}
                else{full=h<0.55?Blocks.POLISHED_ANDESITE.getDefaultState():h<0.8?Blocks.ANDESITE.getDefaultState():h<0.93?Blocks.STONE.getDefaultState():Blocks.COBBLESTONE.getDefaultState();
                    slab=Blocks.POLISHED_ANDESITE_SLAB.getDefaultState();}
            }else switch(b){
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
            if(!half&&b==ROAD&&paving[i]==ASPHALT&&Pal.hash(x,9,z)<0.0025)c.set(x,y+1,z,Canvas.trapdoor(Blocks.IRON_TRAPDOOR,Direction.NORTH,false,false));
        }
    }
}
