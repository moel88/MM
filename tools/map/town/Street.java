package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * What stands along the streets: concrete power poles with crossarms, insulators and transformers, their wires
 * sagging from pole to pole and dropping to the houses, small street lamps on the poles, the main street's lamps
 * with their flags and its arch, the coastal road's tall sodium lamps; vending machines glowing in twos and threes,
 * red post boxes, rubbish points, bus stops and traffic mirrors.
 */
final class Street {
    private final Townscape t;private final Canvas c;private final Random rnd;
    /** Every pole's top (x, y, z), in order along each run, for wiring. */
    private final List<int[][]> runs=new ArrayList<>();
    int poles,wires,lamps,machines;
    Street(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        // poles along the north edge of each east-west street, and the west edge of each side street
        for(int k=0;k<4;k++){
            List<int[]> run=new ArrayList<>();
            for(int x=Plan.TOWN_X0+8;x<=Plan.TOWN_X1-6;x+=17+rnd.nextInt(4)){
                int px=clearOfCrossings(x);if(px==Integer.MIN_VALUE)continue;
                int[] top=pole(px,Plan.ST_N[k],Direction.SOUTH,run.size()%2==0);if(top!=null)run.add(top);
            }
            runs.add(run.toArray(new int[0][]));
        }
        for(int[] s:Plan.SIDE_STREETS){
            List<int[]> run=new ArrayList<>();
            int end=s[0]==Plan.CROSSING_X0?Plan.RAIL_Z0-2:Plan.ST_S[0];
            for(int z=Plan.ST_N[3]+8;z<=end;z+=16+rnd.nextInt(4)){
                if(Plan.street(z)>=0||Plan.street(z-2)>=0||Plan.street(z+2)>=0)continue;
                int[] top=pole(s[0],z,Direction.EAST,run.size()%2==1);if(top!=null)run.add(top);
            }
            runs.add(run.toArray(new int[0][]));
        }
        // terrace streets: poles on the street's south edge, following its curve
        for(int i=0;i<4;i++){
            List<int[]> run=new ArrayList<>();
            for(int x=Plan.TERRACE_XW[i]+6;x<=Plan.TERRACE_XE[i]-6;x+=16+rnd.nextInt(4)){
                if(Math.abs(x)<=7)continue;
                boolean ramp=false;for(int[] r:Plan.RAMPS)if(x>=r[0]-2&&x<=r[1]+2)ramp=true;if(ramp)continue;
                int[] top=pole(x,Plan.terraceStreetZ0(i,x),Direction.NORTH,run.size()%2==0);if(top!=null)run.add(top);
            }
            runs.add(run.toArray(new int[0][]));
        }
        // the main street: poles on its east side; lamps with flags on the west
        List<int[]> main=new ArrayList<>();
        for(int z=Plan.ST_N[3]+6;z<=Plan.RAIL_Z0-3;z+=14){
            if(Plan.street(z)>=0||Plan.street(z-2)>=0||Plan.street(z+2)>=0)continue;
            int[] top=pole(Plan.MAIN_X1,z,Direction.WEST,false);if(top!=null)main.add(top);
            mainLamp(Plan.MAIN_X0,z+7);
        }
        runs.add(main.toArray(new int[0][]));
        // the coastal road: poles along the railway fence, tall sodium lamps along the promenade
        List<int[]> coast=new ArrayList<>();
        for(int x=Plan.TUNNEL_W+12;x<=Plan.TUNNEL_E-12;x+=18){
            if(Plan.river(x,Plan.RAIL_FENCE_Z)||Math.abs(x)<=8||x>=Plan.CROSSING_X0-3&&x<=Plan.CROSSING_X1+3)continue;
            int[] top=pole(x,Plan.RAIL_FENCE_Z-1,Direction.SOUTH,coast.size()%3==0);if(top!=null)coast.add(top);
        }
        runs.add(coast.toArray(new int[0][]));
        for(int x=Plan.HARBOR_X0+6;x<=Plan.TOWN_X1+16;x+=22)if(Math.abs(x)>9)sodium(x,Plan.PROM_Z0);
        for(int[][] run:runs)for(int i=0;i+1<run.length;i++)span(run[i],run[i+1]);
        Town.log("street: "+poles+" poles, "+wires+" wire blocks, "+lamps+" lamps");
    }
    /** A pole position along an east-west street clear of the side streets' crossings, or MIN_VALUE. */
    private int clearOfCrossings(int x){
        for(int[] s:Layout.CORRIDORS)if(x>=s[0]-3&&x<=s[1]+3)return x+(s[1]+4-x)<=Plan.TOWN_X1-6?s[1]+4:Integer.MIN_VALUE;
        return x;
    }

    /**
     * A concrete power pole at the street's edge: a crossarm across the street direction with insulators on it,
     * a transformer on every other one, and on some a small lamp leaning out over the road ({@code road} is the
     * side the road lies on). Returns the top (x, y, z), or null where there is no room.
     */
    int[] pole(int x,int z,Direction road,boolean transformer){
        if(!Canvas.inPlan(x,z))return null;
        int g=c.ground(x,z);
        for(int y=g+1;y<=g+10;y++)if(!c.air(x,y,z))return null;
        for(int y=g+1;y<=g+10;y++)c.set(x,y,z,Blocks.ANDESITE_WALL.getDefaultState());
        int top=g+10;
        // the crossarm lies across the street, square to the wires that run along it
        for(int d=-1;d<=1;d+=2){
            int ax=x+road.getOffsetX()*d,az=z+road.getOffsetZ()*d;
            if(c.air(ax,top-1,az)){c.set(ax,top-1,az,Blocks.IRON_BARS.getDefaultState());if(c.air(ax,top,az))c.set(ax,top,az,Blocks.WHITE_CANDLE.getDefaultState());}
        }
        if(transformer){
            int bx=x-road.getOffsetX(),bz=z-road.getOffsetZ();
            if(c.air(bx,top-3,bz)&&c.air(bx,top-2,bz)){c.set(bx,top-3,bz,Blocks.CAULDRON.getDefaultState());c.set(bx,top-2,bz,Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());}
        }
        if(rnd.nextInt(3)>0){
            // the street lamp: an arm out over the road and a white tube under it
            int ax=x+road.getOffsetX(),az=z+road.getOffsetZ();
            if(c.air(ax,g+5,az)&&c.air(ax,g+4,az)){c.set(ax,g+5,az,Blocks.IRON_BARS.getDefaultState());c.set(ax,g+4,az,Canvas.rod(Blocks.END_ROD,Direction.DOWN));lamps++;}
        }
        poles++;
        return new int[]{x,top,z};
    }

    /** Wires from one pole to the next: two from the crossarm ends, one lower down the pole, each sagging. */
    private void span(int[] a,int[] b){
        double dx=b[0]-a[0],dz=b[2]-a[2],len=Math.hypot(dx,dz);
        if(len<4||len>30)return;
        double nx=-dz/len,nz=dx/len;
        // power lines from the crossarm, a telephone cable lower down; all well above the street
        double sag=0.45+rnd.nextDouble()*0.5;
        wire(a[0]+nx,a[1]-1,a[2]+nz,b[0]+nx,b[1]-1,b[2]+nz,sag);
        wire(a[0]-nx,a[1]-1,a[2]-nz,b[0]-nx,b[1]-1,b[2]-nz,sag*1.1);
        wire(a[0],a[1]-3,a[2],b[0],b[1]-3,b[2],sag*1.3);
        if(rnd.nextBoolean())wire(a[0],a[1]-4,a[2],b[0],b[1]-4,b[2],sag*1.2);
    }
    /** A wire from point to point hanging in a curve {@code sag} blocks deep at its middle, drawn in chains. */
    void wire(double x0,double y0,double z0,double x1,double y1,double z1,double sag){
        double len=Math.max(Math.abs(x1-x0),Math.abs(z1-z0));
        int steps=(int)Math.ceil(len*2);
        int lx=Integer.MIN_VALUE,ly=0,lz=0;
        for(int i=1;i<steps;i++){
            double s=i/(double)steps;
            double x=x0+(x1-x0)*s,z=z0+(z1-z0)*s,y=y0+(y1-y0)*s-sag*4*s*(1-s);
            int bx=(int)Math.round(x),by=(int)Math.round(y),bz=(int)Math.round(z);
            if(bx==lx&&by==ly&&bz==lz)continue;
            lx=bx;ly=by;lz=bz;
            if(!c.air(bx,by,bz))continue;
            Direction.Axis axis=Math.abs(x1-x0)>=Math.abs(z1-z0)?Direction.Axis.X:Direction.Axis.Z;
            c.set(bx,by,bz,Canvas.chain(axis));wires++;
        }
    }

    /** The main street's lamps: a dark post, a white globe under a little cap, flags in Botand's blue. */
    void mainLamp(int x,int z){
        int g=c.ground(x,z);
        for(int y=g+1;y<=g+6;y++)if(!c.air(x,y,z))return;
        for(int y=g+1;y<=g+5;y++)c.set(x,y,z,Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());
        c.set(x,g+6,z,Blocks.SEA_LANTERN.getDefaultState());
        c.set(x,g+7,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
        c.set(x+1,g+4,z,Blocks.LIGHT_BLUE_WALL_BANNER.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.EAST));
        lamps++;
    }
    /** The coastal road's tall lamps: a grey post, an arm out over the road, an orange sodium light. */
    void sodium(int x,int z){
        int g=c.ground(x,z);
        for(int y=g+1;y<=g+9;y++)if(!c.air(x,y,z))return;
        for(int y=g+1;y<=g+8;y++)c.set(x,y,z,Blocks.ANDESITE_WALL.getDefaultState());
        c.set(x,g+9,z,Blocks.IRON_BARS.getDefaultState());c.set(x,g+9,z-1,Blocks.IRON_BARS.getDefaultState());
        c.set(x,g+9,z-2,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));c.set(x,g+8,z-2,Blocks.OCHRE_FROGLIGHT.getDefaultState());
        lamps++;
    }

    /** A row of vending machines against something: glowing panels over coloured cabinets, a bin beside them. */
    void vending(int x,int z,Direction alongRow,int count){
        BlockState[] bodies={Blocks.RED_CONCRETE.getDefaultState(),Blocks.BLUE_CONCRETE.getDefaultState(),Blocks.WHITE_CONCRETE.getDefaultState(),
            Blocks.LIGHT_BLUE_CONCRETE.getDefaultState(),Blocks.GREEN_CONCRETE.getDefaultState(),Blocks.RED_CONCRETE.getDefaultState()};
        int g=c.ground(x,z);
        for(int i=0;i<count;i++){
            int vx=x+alongRow.getOffsetX()*i,vz=z+alongRow.getOffsetZ()*i;
            int vg=c.ground(vx,vz);
            if(!c.air(vx,vg+1,vz)||!c.air(vx,vg+2,vz))return;
            c.set(vx,vg+1,vz,bodies[rnd.nextInt(bodies.length)]);c.set(vx,vg+2,vz,Blocks.SEA_LANTERN.getDefaultState());
            if(c.air(vx,vg+3,vz))c.set(vx,vg+3,vz,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
            machines++;
        }
        int bx=x+alongRow.getOffsetX()*count,bz=z+alongRow.getOffsetZ()*count;
        if(c.air(bx,c.ground(bx,bz)+1,bz))c.set(bx,c.ground(bx,bz)+1,bz,Blocks.CAULDRON.getDefaultState());
    }
    /** A red post box. */
    void postBox(int x,int z){
        int g=c.ground(x,z);if(!c.air(x,g+1,z)||!c.air(x,g+2,z))return;
        c.set(x,g+1,z,Blocks.RED_CONCRETE.getDefaultState());c.set(x,g+2,z,Blocks.RED_CONCRETE.getDefaultState());c.set(x,g+3,z,Canvas.slab(Blocks.RED_NETHER_BRICK_SLAB,false));
    }
    /** A rubbish collection point: a low mesh cage with bags in it and the collection days on a sign. */
    void rubbish(int x,int z,Direction face){
        int g=c.ground(x,z);
        Direction side=face.rotateYClockwise();
        for(int i=0;i<3;i++)for(int j=0;j<2;j++){
            int bx=x+side.getOffsetX()*i-face.getOffsetX()*j,bz=z+side.getOffsetZ()*i-face.getOffsetZ()*j;
            if(!c.air(bx,g+1,bz))return;
        }
        for(int i=0;i<3;i++)for(int j=0;j<2;j++){
            int bx=x+side.getOffsetX()*i-face.getOffsetX()*j,bz=z+side.getOffsetZ()*i-face.getOffsetZ()*j;
            boolean edge=i==0||i==2||j==1;
            c.set(bx,g+1,bz,edge?Blocks.IRON_BARS.getDefaultState():rnd.nextBoolean()?Blocks.WHITE_WOOL.getDefaultState():Blocks.LIGHT_GRAY_WOOL.getDefaultState());
            c.set(bx,g+2,bz,Blocks.GREEN_CARPET.getDefaultState());
        }
        c.wallSign(x+side.getOffsetX(),g+1,z+side.getOffsetZ(),Blocks.BIRCH_WALL_SIGN,face,DyeColor.GREEN,false,"ゴミ集積所","燃えるゴミ 月・木","資源ゴミ 水");
    }
    /** A bus stop: the round sign on its post, a bench and the timetable. */
    void busStop(int x,int z,Direction face,String name){
        int g=c.ground(x,z);if(!c.air(x,g+1,z)||!c.air(x,g+3,z))return;
        c.set(x,g+1,z,Blocks.IRON_BARS.getDefaultState());c.set(x,g+2,z,Blocks.IRON_BARS.getDefaultState());c.set(x,g+3,z,Blocks.WHITE_CONCRETE.getDefaultState());
        c.wallSign(x+face.getOffsetX(),g+3,z+face.getOffsetZ(),Blocks.BIRCH_WALL_SIGN,face,DyeColor.BLUE,false,"バス停",name,"ぼたんど交通");
        Direction side=face.rotateYClockwise();
        int bx=x+side.getOffsetX()*2,bz=z+side.getOffsetZ()*2;
        if(c.air(bx,g+1,bz))c.set(bx,g+1,bz,Canvas.stairs(Blocks.SPRUCE_STAIRS,face.getOpposite(),false));
        if(c.air(bx+side.getOffsetX(),g+1,bz+side.getOffsetZ()))c.set(bx+side.getOffsetX(),g+1,bz+side.getOffsetZ(),Canvas.stairs(Blocks.SPRUCE_STAIRS,face.getOpposite(),false));
    }
    /** A traffic mirror at a blind corner: an orange post and the mirror on top. */
    void mirror(int x,int z){
        int g=c.ground(x,z);for(int y=g+1;y<=g+4;y++)if(!c.air(x,y,z))return;
        for(int y=g+1;y<=g+3;y++)c.set(x,y,z,Canvas.rod(Blocks.LIGHTNING_ROD,Direction.UP));
        c.set(x,g+4,z,Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
    }
}
