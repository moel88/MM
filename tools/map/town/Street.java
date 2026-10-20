package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * What stands along the streets: tall concrete power poles with a yellow-and-black sleeve at their foot, crossarms with
 * insulators, transformers and a street lamp reaching out over the road (Botand's own town blocks), the wires hung
 * from pole to pole (drawn by the mod as thin sagging lines, so they are written into the map's definition, not built
 * of blocks), the main street's lamps with their flags and its arch, the coastal road's tall sodium lamps; vending
 * machines glowing in twos and threes, red post boxes, rubbish points, bus stops and traffic mirrors.
 */
final class Street {
    private final Townscape t;private final Canvas c;private final Random rnd;
    /** Every pole, in order along each run, for wiring: its x, top y, z and the axis its crossarm lies along. */
    private final List<int[][]> runs=new ArrayList<>();
    int poles,lamps,machines;
    Street(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    void build(){
        // poles along the north edge of each east-west street, and the west edge of each alley
        for(int k=0;k<4;k++){
            List<int[]> run=new ArrayList<>();
            for(int x=Plan.TOWN_X0+8;x<=Plan.TOWN_X1-6;x+=17+rnd.nextInt(4)){
                int px=clearOfCrossings(x);if(px==Integer.MIN_VALUE)continue;
                int[] top=pole(px,Plan.ST_N[k],Direction.SOUTH,run.size()%2==0?1:0,rnd.nextInt(3)>0);if(top!=null)run.add(top);
            }
            runs.add(run.toArray(new int[0][]));
        }
        for(int[] s:Plan.SIDE_STREETS){
            List<int[]> run=new ArrayList<>();
            for(int z=Plan.ST_N[3]+8;z<=Plan.ST_S[0];z+=16+rnd.nextInt(4)){
                if(Plan.street(z)>=0||Plan.street(z-2)>=0||Plan.street(z+2)>=0)continue;
                int[] top=pole(s[0],z,Direction.EAST,run.size()%2==1?1:0,rnd.nextInt(3)>0);if(top!=null)run.add(top);
            }
            runs.add(run.toArray(new int[0][]));
        }
        // the photo street: poles along its east kerb about twenty metres apart, the first just ahead of the photo's
        // camera with its lamp out over the road, and a few on the west kerb
        List<int[]> photo=new ArrayList<>();
        for(int z=(int)Plan.PHOTO1_Z+4;z<=Plan.RAIL_Z0-3;z+=19+rnd.nextInt(3)){
            if(Plan.street(z)>=0||Plan.street(z-2)>=0||Plan.street(z+2)>=0)z+=5;
            int[] top=pole(Plan.PHOTO_ROAD_X1+1,z,Direction.WEST,photo.size()%3==1?2:photo.size()%3==2?1:0,photo.isEmpty()||rnd.nextBoolean());
            if(top!=null)photo.add(top);
        }
        runs.add(photo.toArray(new int[0][]));
        List<int[]> photoWest=new ArrayList<>();
        for(int z=(int)Plan.PHOTO1_Z+38;z<=Plan.RAIL_Z0-3;z+=24){
            if(Plan.street(z)>=0||Plan.street(z-2)>=0||Plan.street(z+2)>=0)z+=5;
            int[] top=pole(Plan.PHOTO_ROAD_X0-1,z,Direction.EAST,1,false);if(top!=null)photoWest.add(top);
        }
        runs.add(photoWest.toArray(new int[0][]));
        // terrace streets: poles on the street's south edge, following its curve
        for(int i=0;i<Plan.TERRACE_Y.length;i++){
            List<int[]> run=new ArrayList<>();
            for(int x=Plan.TERRACE_XW[i]+6;x<=Plan.TERRACE_XE[i]-6;x+=16+rnd.nextInt(4)){
                if(Math.abs(x)<=7)continue;
                boolean ramp=false;for(int[] r:Plan.RAMPS)if(x>=r[0]-2&&x<=r[1]+2)ramp=true;if(ramp)continue;
                int[] top=pole(x,Plan.terraceStreetZ0(i,x),Direction.NORTH,run.size()%2==0?1:0,rnd.nextInt(3)>0);if(top!=null)run.add(top);
            }
            runs.add(run.toArray(new int[0][]));
        }
        // the main street: poles on its east side; lamps with flags on the west
        List<int[]> main=new ArrayList<>();
        for(int z=Plan.ST_N[3]+6;z<=Plan.RAIL_Z0-3;z+=14){
            if(Plan.street(z)>=0||Plan.street(z-2)>=0||Plan.street(z+2)>=0)continue;
            int[] top=pole(Plan.MAIN_X1,z,Direction.WEST,0,false);if(top!=null)main.add(top);
            mainLamp(Plan.MAIN_X0,z+7);
        }
        runs.add(main.toArray(new int[0][]));
        // the coastal road: poles along the railway fence, tall sodium lamps along the promenade
        List<int[]> coast=new ArrayList<>();
        for(int x=Plan.TUNNEL_W+12;x<=Plan.TUNNEL_E-8;x+=18){
            boolean crossing=false;for(int[] xs:Plan.CROSSINGS)if(x>=xs[0]-3&&x<=xs[1]+3)crossing=true;
            if(crossing||Math.abs(x)<=8)continue;
            int[] top=pole(x,Plan.RAIL_FENCE_Z-1,Direction.SOUTH,coast.size()%3==0?1:0,false);if(top!=null)coast.add(top);
        }
        runs.add(coast.toArray(new int[0][]));
        for(int x=Plan.HARBOR_X0+6;x<Plan.LOT_X0;x+=22)if(Math.abs(x)>9&&(x<Plan.PHOTO_X0-2||x>Plan.PHOTO_X1+2))sodium(x,Plan.PROM_Z0);
        Town.log("street: "+poles+" poles, "+lamps+" lamps");
    }
    /** Hangs the wires along every run (after the lot and the hillside have added theirs). */
    void wireRuns(){
        for(int[][] run:runs)for(int i=0;i+1<run.length;i++)span(run[i],run[i+1]);
        Town.log("wires: "+t.wires.size()+" spans");
    }
    void addRun(List<int[]> run){runs.add(run.toArray(new int[0][]));}
    /** A pole position along an east-west street clear of the corridors' crossings, or MIN_VALUE. */
    private int clearOfCrossings(int x){
        for(int[] s:Layout.CORRIDORS)if(x>=s[0]-3&&x<=s[1]+3)return x+(s[1]+4-x)<=Plan.TOWN_X1-6?s[1]+4:Integer.MIN_VALUE;
        return x;
    }

    /**
     * A concrete power pole at the street's edge, eleven blocks tall: the yellow-and-black sleeve at its foot, a
     * crossarm with insulators at the top lying across the street (square to the wires that run along it), a second
     * one lower down on some, {@code transformers} transformers on the side away from the road, and on {@code lamp}
     * poles a street lamp reaching out over the road ({@code road} is the side the road lies on). Returns the pole
     * (x, top y, z, crossarm axis 0 x / 1 z), or null where there is no room.
     */
    int[] pole(int x,int z,Direction road,int transformers,boolean lamp){
        if(!Canvas.inPlan(x,z))return null;
        int g=c.ground(x,z),top=g+11;
        for(int y=g+1;y<=top;y++)if(!c.air(x,y,z))return null;
        for(int y=g+1;y<=top;y++)c.set(x,y,z,y<=g+2?TB.SLEEVE:TB.POLE);
        Direction.Axis across=road.getAxis();
        c.set(x,top-1,z,TB.crossarm(true,across));
        for(int d=-1;d<=1;d+=2){
            int ax=x+road.getOffsetX()*d,az=z+road.getOffsetZ()*d;
            if(c.air(ax,top-1,az))c.set(ax,top-1,az,TB.crossarm(false,across));
        }
        boolean second=Pal.hash(x,17,z)<0.5;
        if(second){c.set(x,top-3,z,TB.crossarm(true,across));int ax=x+road.getOffsetX(),az=z+road.getOffsetZ();if(c.air(ax,top-3,az))c.set(ax,top-3,az,TB.crossarm(false,across));}
        Direction back=road.getOpposite();
        if(transformers>0){
            int bx=x+back.getOffsetX(),bz=z+back.getOffsetZ();
            if(c.air(bx,top-5,bz))c.set(bx,top-5,bz,TB.facing("pole_transformer",back));
            if(transformers>1){
                Direction side=road.rotateYClockwise();int sx=x+side.getOffsetX(),sz=z+side.getOffsetZ();
                if(c.air(sx,top-5,sz))c.set(sx,top-5,sz,TB.facing("pole_transformer",side));
            }
        }
        if(lamp){
            // the street lamp: an arm reaching a metre and a half out over the road, the lamp at its end
            int ax=x+road.getOffsetX(),az=z+road.getOffsetZ(),hx=ax+road.getOffsetX(),hz=az+road.getOffsetZ();
            if(c.air(ax,g+8,az)&&c.air(hx,g+8,hz)){c.set(ax,g+8,az,TB.facing("street_lamp_arm",road));c.set(hx,g+8,hz,TB.facing("street_lamp",road));lamps++;}
        }
        poles++;
        return new int[]{x,top,z,across==Direction.Axis.X?0:1};
    }

    /**
     * Wires from one pole to the next: the three power lines from the insulators along the top crossarm, a fourth
     * from the lower crossarm, and a telephone cable lower down the pole, each sagging a little more than the last.
     */
    private void span(int[] a,int[] b){
        double dx=b[0]-a[0],dz=b[2]-a[2],len=Math.hypot(dx,dz);
        if(len<4||len>34)return;
        double sag=0.35+rnd.nextDouble()*0.25+len*0.012;
        for(int k=-1;k<=1;k++){
            double[] p=arm(a,k*1.35),q=arm(b,k*1.35);
            t.wire(p[0],a[1]-0.05,p[1],q[0],b[1]-0.05,q[1],sag*(1+0.08*k));
        }
        double[] p=arm(a,0.9),q=arm(b,0.9);
        t.wire(p[0],a[1]-2.05,p[1],q[0],b[1]-2.05,q[1],sag*1.25);
        t.wire(a[0]+0.5,a[1]-5.6,a[2]+0.5,b[0]+0.5,b[1]-5.6,b[2]+0.5,sag*1.6+0.2);
        if(Pal.hash(a[0],5,a[2])<0.5)t.wire(a[0]+0.5,a[1]-6.4,a[2]+0.5,b[0]+0.5,b[1]-6.4,b[2]+0.5,sag*1.9+0.3);
    }
    /** A point along a pole's crossarm, {@code off} blocks out from the pole's middle. */
    private static double[] arm(int[] p,double off){
        return p[3]==0?new double[]{p[0]+0.5+off,p[2]+0.5}:new double[]{p[0]+0.5,p[2]+0.5+off};
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
