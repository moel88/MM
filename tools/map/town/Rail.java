package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.block.enums.RailShape;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

import static dev.sangwoo.botandtown.Kit.s;

/**
 * The single-track coast line and everything on it: ballast and rails from tunnel to tunnel, low fences, the town's
 * little station (a wooden station house, a platform with its canopy, benches and name board, the square in front
 * with a police box), a two-car local train waiting at the platform, level crossings with their striped posts,
 * warning lights and raised barriers, girder bridges over the river for the line and the coastal road, and tunnels
 * into both headlands closed off inside for works.
 */
final class Rail {
    private final Townscape t;private final Canvas c;private final Random rnd;
    Rail(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}
    static final int Y=Plan.COAST;

    void build(){
        track();
        bridges();
        crossing(Plan.MAIN_X0,Plan.MAIN_X1);crossing(Plan.CROSSING_X0,Plan.CROSSING_X1);
        platform();
        station();
        train();
        tunnel(Plan.TUNNEL_W,-1);tunnel(Plan.TUNNEL_E,1);
        Town.log("railway: "+c.placed);
    }
    boolean crossingX(int x){return x>=Plan.MAIN_X0&&x<=Plan.MAIN_X1||x>=Plan.CROSSING_X0&&x<=Plan.CROSSING_X1;}

    /** Ballast, rails and the fences along the line. */
    private void track(){
        for(int x=Plan.TUNNEL_W-30;x<=Plan.TUNNEL_E+30;x++){
            boolean river=Plan.river(x,Plan.TRACK_Z);
            if(river)continue;
            for(int z=Plan.TRACK_Z-1;z<=Plan.TRACK_Z+1;z++){
                if(crossingX(x))continue;
                double h=Pal.hash(x,3,z);
                c.set(x,Y,z,h<0.7?s(Blocks.GRAVEL):h<0.85?s(Blocks.ANDESITE):h<0.95?s(Blocks.TUFF):s(Blocks.COBBLESTONE));
                c.clear(x,Y+1,z,x,Y+6,z);
            }
            c.set(x,Y+1,Plan.TRACK_Z,Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE,RailShape.EAST_WEST));
            if(x<Plan.TUNNEL_W||x>Plan.TUNNEL_E||crossingX(x))continue;
            // verges of gravel and grass, a fence toward the road and, away from the station, toward the town
            for(int z:new int[]{Plan.TRACK_Z+2,Plan.TRACK_Z+3})if(c.ground(x,z)==Y)c.set(x,Y,z,Pal.hash(x,4,z)<0.5?s(Blocks.GRAVEL):s(Blocks.COARSE_DIRT));
            fence(x,Plan.RAIL_FENCE_Z);
            boolean platform=x>=Plan.PLATFORM_X0&&x<=Plan.PLATFORM_X1;
            if(!platform&&!(x>=Plan.MAIN_X0-1&&x<=Plan.MAIN_X1+1))fence(x,Plan.RAIL_Z0);
            if(!platform)for(int z=Plan.RAIL_Z0+1;z<Plan.TRACK_Z-1;z++)if(c.ground(x,z)==Y&&c.air(x,Y+1,z)){c.set(x,Y,z,s(Blocks.GRASS_BLOCK));if(rnd.nextInt(4)==0)c.set(x,Y+1,z,s(Blocks.GRASS));}
        }
    }
    private void fence(int x,int z){
        if(Plan.river(x,z)||!c.air(x,Y+1,z))return;
        c.set(x,Y+1,z,x%4==0?s(Blocks.ANDESITE_WALL):s(Blocks.IRON_BARS));
    }

    /** Girder bridges over the river: the line on steel, the coastal road on a concrete deck with railings and lamps. */
    private void bridges(){
        for(int x=Plan.TOWN_X1;x<=Plan.TUNNEL_E;x++){
            boolean any=false;for(int z=Plan.TRACK_Z-1;z<=Plan.PROM_Z1+1;z++)if(Plan.river(x,z))any=true;
            if(!any)continue;
            // the line: a dark steel girder deck with rails on it
            for(int z=Plan.TRACK_Z-1;z<=Plan.TRACK_Z+1;z++){
                c.set(x,Y-1,z,s(Blocks.POLISHED_DEEPSLATE));c.set(x,Y,z,z==Plan.TRACK_Z?s(Blocks.SPRUCE_PLANKS):s(Blocks.POLISHED_DEEPSLATE));
            }
            c.set(x,Y+1,Plan.TRACK_Z,Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE,RailShape.EAST_WEST));
            c.set(x,Y+1,Plan.TRACK_Z-1,s(Blocks.IRON_BARS));c.set(x,Y+1,Plan.TRACK_Z+1,s(Blocks.IRON_BARS));
            // the road: asphalt on a concrete deck, sidewalks, white railings
            for(int z=Plan.COAST_Z0-1;z<=Plan.PROM_Z1;z++){
                c.set(x,Y-1,z,s(Blocks.LIGHT_GRAY_CONCRETE));
                boolean road=z>=Plan.COAST_Z0&&z<=Plan.COAST_Z1;
                c.set(x,Y,z,road?(z==Plan.CENTRE_Z&&Math.floorMod(x,8)<4?s(Blocks.WHITE_CONCRETE):Pal.asphalt(x,z)):s(Blocks.SMOOTH_STONE));
                if(!road)c.set(x,Y+1,z,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
                c.ground(x,z,Y);
            }
            for(int z:new int[]{Plan.COAST_Z0-2,Plan.PROM_Z1+1}){c.set(x,Y-1,z,s(Blocks.LIGHT_GRAY_CONCRETE));c.set(x,Y,z,s(Blocks.LIGHT_GRAY_CONCRETE));c.set(x,Y+1,z,s(Blocks.LIGHT_GRAY_CONCRETE));c.set(x,Y+2,z,s(Blocks.IRON_BARS));}
            // piers in the water
            if(Math.floorMod(x,10)==0)for(int z=Plan.TRACK_Z-1;z<=Plan.PROM_Z1;z++)if(Plan.river(x,z)&&z!=Plan.COAST_Z0-1)
                for(int y=c.ground(x,z)+1;y<Y-1;y++)c.set(x,y,z,s(Blocks.LIGHT_GRAY_CONCRETE));
            if(Math.floorMod(x,16)==8){
                c.set(x,Y+2,Plan.PROM_Z1+1,s(Blocks.ANDESITE_WALL));c.set(x,Y+3,Plan.PROM_Z1+1,s(Blocks.ANDESITE_WALL));c.set(x,Y+4,Plan.PROM_Z1+1,s(Blocks.ANDESITE_WALL));
                c.set(x,Y+5,Plan.PROM_Z1+1,s(Blocks.IRON_BARS));c.set(x,Y+5,Plan.PROM_Z1,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,true));c.set(x,Y+4,Plan.PROM_Z1,s(Blocks.OCHRE_FROGLIGHT));
            }
        }
    }

    /**
     * A level crossing: rubber panels between the rails, and at two corners a striped post with its warning
     * lights and crossbuck, the barrier standing raised beside it.
     */
    private void crossing(int x0,int x1){
        for(int x=x0;x<=x1;x++)for(int z=Plan.RAIL_Z0;z<=Plan.RAIL_FENCE_Z;z++){
            boolean panel=z>=Plan.TRACK_Z-1&&z<=Plan.TRACK_Z+1;
            c.set(x,Y,z,panel?s(Blocks.BLACK_CONCRETE):z==Plan.RAIL_Z0||z==Plan.RAIL_FENCE_Z?s(Blocks.WHITE_CONCRETE):Pal.asphalt(x,z));
            c.clear(x,Y+1,z,x,Y+6,z);c.ground(x,z,Y);
        }
        for(int x=x0;x<=x1;x++)c.set(x,Y+1,Plan.TRACK_Z,Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE,RailShape.EAST_WEST));
        signal(x0-1,Plan.RAIL_Z0+1,Direction.EAST);signal(x1+1,Plan.RAIL_FENCE_Z-1,Direction.WEST);
    }
    private void signal(int x,int z,Direction road){
        BlockState[] post={s(Blocks.YELLOW_CONCRETE),s(Blocks.BLACK_CONCRETE),s(Blocks.ANDESITE_WALL),s(Blocks.ANDESITE_WALL),s(Blocks.BLACK_CONCRETE),s(Blocks.YELLOW_TERRACOTTA)};
        for(int i=0;i<post.length;i++)c.set(x,Y+1+i,z,post[i]);
        // the warning lights face both ways along the road
        Direction side=road.rotateYClockwise();
        c.set(x+side.getOffsetX(),Y+5,z+side.getOffsetZ(),s(Blocks.RED_STAINED_GLASS));
        c.set(x-side.getOffsetX(),Y+5,z-side.getOffsetZ(),s(Blocks.RED_STAINED_GLASS));
        // the raised barrier: a yellow and black pole standing up beside the post
        int bx=x-road.getOffsetX(),bz=z-road.getOffsetZ();
        for(int i=0;i<5;i++)c.set(bx,Y+1+i,bz,i%2==0?s(Blocks.YELLOW_STAINED_GLASS_PANE):s(Blocks.BLACK_STAINED_GLASS_PANE));
    }

    /** The platform: concrete, a yellow line along its edge, a canopy, benches, lamps and the name board. */
    private void platform(){
        for(int x=Plan.PLATFORM_X0;x<=Plan.PLATFORM_X1;x++){
            for(int z=Plan.RAIL_Z0;z<=Plan.PLATFORM_Z1;z++){
                c.set(x,Y,z,s(Blocks.STONE));
                c.set(x,Y+1,z,z==Plan.PLATFORM_Z1?s(Blocks.SMOOTH_STONE):z==Plan.PLATFORM_Z1-1?s(Blocks.YELLOW_CONCRETE):s(Blocks.POLISHED_ANDESITE));
                c.clear(x,Y+2,z,x,Y+7,z);c.ground(x,z,Y+1);
            }
            boolean ends=x==Plan.PLATFORM_X0||x==Plan.PLATFORM_X1;
            if(ends)for(int z=Plan.RAIL_Z0;z<=Plan.PLATFORM_Z1;z++)c.set(x,Y+2,z,s(Blocks.IRON_BARS));
            else c.set(x,Y+2,Plan.RAIL_Z0,s(Blocks.IRON_BARS));
        }
        // the canopy over the middle of the platform, opposite the station house
        int a=Plan.STATION_X0-2,b=Plan.STATION_X1+2;
        for(int x=a;x<=b;x++){
            if((x-a)%6==0)for(int y=Y+2;y<=Y+4;y++)c.set(x,y,Plan.RAIL_Z0+1,s(Blocks.DARK_OAK_FENCE));
            for(int z=Plan.RAIL_Z0;z<=Plan.PLATFORM_Z1;z++)c.set(x,Y+5,z,Canvas.slab(Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB,z==Plan.RAIL_Z0));
            if((x-a)%6==3)c.set(x,Y+4,Plan.RAIL_Z0+1,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
        }
        for(int x=a+2;x<b;x+=6){
            c.set(x,Y+2,Plan.RAIL_Z0+1,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.NORTH,false));c.set(x+1,Y+2,Plan.RAIL_Z0+1,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.NORTH,false));
        }
        // the station's name board (駅名標) facing the track: the name, its reading, the next stations
        int nx=Plan.STATION_X1+8;
        c.set(nx-1,Y+2,Plan.RAIL_Z0+1,s(Blocks.IRON_BARS));c.set(nx+1,Y+2,Plan.RAIL_Z0+1,s(Blocks.IRON_BARS));
        for(int x=nx-1;x<=nx+1;x++){c.set(x,Y+3,Plan.RAIL_Z0+1,s(Blocks.WHITE_CONCRETE));c.set(x,Y+4,Plan.RAIL_Z0+1,s(Blocks.WHITE_CONCRETE));}
        c.wallSign(nx,Y+4,Plan.RAIL_Z0+2,Blocks.BIRCH_WALL_SIGN,Direction.SOUTH,DyeColor.BLACK,false,"ぼたんど","BOTAND","보탠드");
        c.wallSign(nx-1,Y+3,Plan.RAIL_Z0+2,Blocks.BIRCH_WALL_SIGN,Direction.SOUTH,DyeColor.BLACK,false,"← みさき","Misaki");
        c.wallSign(nx+1,Y+3,Plan.RAIL_Z0+2,Blocks.BIRCH_WALL_SIGN,Direction.SOUTH,DyeColor.BLACK,false,"なぎさ →","Nagisa");
        c.wallSign(nx,Y+3,Plan.RAIL_Z0+2,Blocks.BIRCH_WALL_SIGN,Direction.SOUTH,DyeColor.LIGHT_BLUE,false,"","海岸線","Kaigan Line");
        t.street.vending(Plan.STATION_X1+3,Plan.RAIL_Z0,Direction.EAST,2);
    }

    /** The station house: a low wooden building under a tiled hip roof, its name over the door, a waiting room inside. */
    private void station(){
        var lot=new Lot(Plan.STATION_X0,28,Plan.STATION_X1,Plan.RAIL_Z0-1,Y,Direction.NORTH,"station");
        Frame f=Frame.of(c,lot);
        int W=f.W,D=f.D;
        BlockState post=Canvas.axis(Blocks.STRIPPED_SPRUCE_LOG,Direction.Axis.Y),boards=s(Blocks.SPRUCE_PLANKS),plaster=s(Blocks.CALCITE);
        f.fill(0,1,0,W-1,8,D-1,s(Blocks.AIR));
        Kit.shell(f,0,0,W-1,D-1,1,boards,post,s(Blocks.STRIPPED_DARK_OAK_WOOD),s(Blocks.POLISHED_ANDESITE),s(Blocks.STONE_BRICKS));
        // a taller ground floor: walls to by=4, plaster above the boards, posts every three blocks
        Houses.perimeter(f,0,0,W-1,D-1,(x,z,corner)->{
            boolean p=corner||(x%3==0&&(z==0||z==D-1))||(z%3==0&&(x==0||x==W-1));
            for(int y=1;y<=4;y++)f.set(x,y,z,p?post:y>=3?plaster:boards);
        });
        for(int x=0;x<W;x++)for(int z=0;z<D;z++)if(x>0&&x<W-1&&z>0&&z<D-1)f.set(x,4,z,s(Blocks.AIR));
        for(int x=0;x<W;x++)for(int z=0;z<D;z++)f.set(x,5,z,s(Blocks.SPRUCE_PLANKS));
        // windows, the wide entrance to the square, the gate to the platform
        for(int x=2;x<W-2;x+=3){f.set(x,2,0,s(Blocks.GLASS_PANE));f.set(x,3,0,s(Blocks.GLASS_PANE));f.set(x,2,D-1,s(Blocks.GLASS_PANE));}
        int mid=W/2;
        for(int x=mid-1;x<=mid+1;x++){f.set(x,1,0,s(Blocks.AIR));f.set(x,2,0,s(Blocks.AIR));f.set(x,3,0,s(Blocks.AIR));}
        for(int x=mid-1;x<=mid+1;x++){f.set(x,1,D-1,s(Blocks.AIR));f.set(x,2,D-1,s(Blocks.AIR));}
        // the name board over the entrance
        for(int x=mid-3;x<=mid+3;x++)f.set(x,4,-1,s(Blocks.DARK_OAK_PLANKS));
        f.wallSign(mid,4,-2,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"","ぼたんど駅");
        f.wallSign(mid-2,4,-2,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"","BOTAND");
        f.wallSign(mid+2,4,-2,Blocks.DARK_OAK_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"","STATION");
        for(int x=mid-2;x<=mid+2;x++)f.set(x,3,-1,Canvas.slab(Blocks.DEEPSLATE_TILE_SLAB,true));
        f.set(mid-2,2,-1,Canvas.rod(Blocks.END_ROD,Direction.NORTH));f.set(mid+2,2,-1,Canvas.rod(Blocks.END_ROD,Direction.NORTH));
        // the waiting room: benches along the walls, the ticket window, a lamp
        for(int x=2;x<mid-2;x++)f.set(x,1,D-2,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.SOUTH,false));
        for(int z=2;z<D-2;z++)f.set(W-2,1,z,s(Blocks.SMOOTH_QUARTZ));
        f.wallSign(W-3,3,D/2,Blocks.BIRCH_WALL_SIGN,Direction.WEST,DyeColor.BLACK,false,"きっぷうりば","Tickets","운임표");
        f.hangingLantern(mid,4,D/2,false);f.hangingLantern(4,4,D/2,false);f.hangingLantern(W-5,4,D/2,false);
        Kit.roof(f,0,0,W-1,D-1,6,Kit.HIP,true,Kit.KAWARA,plaster,s(Blocks.SPRUCE_PLANKS));
        // the square in front: pavers, planters with a cherry and a pine, the police box, a bus stop, a post box
        for(int x=Plan.MAIN_X1+1;x<=43;x++)for(int z=Plan.ST_S[0]+1;z<lot.z0;z++){
            c.set(x,Y,z,Pal.paverBlock(x,z));c.clear(x,Y+1,z,x,Y+6,z);c.ground(x,z,Y);
        }
        for(int x=Plan.MAIN_X1+1;x<=43;x++)if(x<lot.x0||x>lot.x1)for(int z=lot.z0;z<Plan.RAIL_Z0;z++){c.set(x,Y,z,Pal.paverBlock(x,z));c.clear(x,Y+1,z,x,Y+6,z);}
        planter(9,30,true);planter(39,34,false);
        policeBox(37,24);
        t.street.busStop(8,Plan.ST_S[0]+1,Direction.NORTH,"ぼたんど駅前");
        stall(16,true);stall(26,false);
        t.street.postBox(Plan.STATION_X1+2,Plan.ST_S[0]+2);
        t.street.vending(Plan.STATION_X0-3,lot.z0,Direction.SOUTH,2);
        c.wallSign(Plan.STATION_X1+4,Y+1,Plan.ST_S[0]+1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLACK,false,"タクシーのりば","TAXI");
        t.places.put("station",new int[]{(Plan.STATION_X0+Plan.STATION_X1)/2,Y+1,Plan.ST_S[0]+3});
    }
    /**
     * A market stall on the station square for one of the mod's two merchants: a counter of barrels, four posts, a
     * striped awning with the merchant's name, a lantern. The merchant stands behind the counter facing the street.
     */
    private void stall(int x0,boolean seller){
        int z0=Plan.ST_S[0]+1,y=Y;
        for(int x=x0;x<=x0+3;x++){
            c.set(x,y+1,z0,x==x0+1?s(Blocks.AIR):s(Blocks.BARREL));
            for(int z=z0;z<=z0+2;z++)c.set(x,y+4,z,s((x+z&1)==0?(seller?Blocks.RED_WOOL:Blocks.BLUE_WOOL):Blocks.WHITE_WOOL));
        }
        c.set(x0+1,y+1,z0,Canvas.slab(Blocks.SPRUCE_SLAB,true));
        for(int x:new int[]{x0,x0+3})for(int z:new int[]{z0,z0+2})for(int yy=y+1;yy<=y+3;yy++)if(!(z==z0&&yy==y+1))c.set(x,yy,z,s(Blocks.SPRUCE_FENCE));
        c.hangingLantern(x0+2,y+3,z0+1,false);
        c.wallSign(x0+1,y+4,z0-1,Blocks.SPRUCE_WALL_SIGN,Direction.NORTH,DyeColor.WHITE,true,"",seller?"아이템 판매":"아이템 매입",seller?"로카":"카카");
        t.merchants.add(new int[]{x0+1,y+1,z0+1,180});
    }
    private void planter(int x,int z,boolean cherry){
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){boolean e=dx!=0||dz!=0;c.set(x+dx,Y+1,z+dz,e?s(Blocks.STONE_BRICKS):s(Blocks.GRASS_BLOCK));}
        if(cherry)t.nature.cherry(x,Y+2,z,6);else t.nature.pine(x,Y+2,z,5);
    }
    /** 交番: a small white police box with the red lamp over its door. */
    private void policeBox(int x0,int z0){
        var f=new Frame(c,x0,z0,x0+6,z0+5,Direction.NORTH,Y);
        Kit.shell(f,0,0,6,5,1,s(Blocks.WHITE_CONCRETE),s(Blocks.WHITE_CONCRETE),s(Blocks.LIGHT_GRAY_CONCRETE),s(Blocks.SMOOTH_STONE),s(Blocks.SMOOTH_STONE));
        f.set(3,1,0,s(Blocks.AIR));f.set(3,2,0,s(Blocks.AIR));
        f.set(1,2,0,s(Blocks.GLASS_PANE));f.set(5,2,0,s(Blocks.GLASS_PANE));
        f.set(3,4,-1,s(Blocks.SHROOMLIGHT));
        f.wallSign(2,3,-1,Blocks.BIRCH_WALL_SIGN,Direction.NORTH,DyeColor.BLACK,false,"交番","KOBAN","파출소");
        f.hangingLantern(3,0,2,false);
        Kit.roof(f,0,0,6,5,4,Kit.FLAT,true,Kit.SLATE,s(Blocks.WHITE_CONCRETE),s(Blocks.SMOOTH_STONE));
        f.set(3,3,2,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
    }

    /** The local train waiting at the platform: two cream cars with Botand's sky-blue band, doors open. */
    private void train(){
        int x0=Plan.PLATFORM_X0+5;
        for(int car=0;car<2;car++){
            int a=x0+car*21,b=a+19;
            for(int x=a;x<=b;x++)for(int z=Plan.TRACK_Z-1;z<=Plan.TRACK_Z+1;z++){
                boolean side=z!=Plan.TRACK_Z,end=x==a||x==b,door=(x-a==4||x-a==5||x-a==14||x-a==15)&&side;
                c.set(x,Y+1,z,end&&z==Plan.TRACK_Z?s(Blocks.BLACK_CONCRETE):s(Blocks.GRAY_CONCRETE));
                c.set(x,Y+2,z,door?s(Blocks.AIR):side||end?s(Blocks.LIGHT_BLUE_CONCRETE):s(Blocks.POLISHED_ANDESITE));
                BlockState glass=end?s(Blocks.BLACK_STAINED_GLASS):s(Blocks.GLASS);
                c.set(x,Y+3,z,door?s(Blocks.AIR):side||end?glass:s(Blocks.AIR));
                c.set(x,Y+4,z,side||end?s(Blocks.WHITE_CONCRETE):s(Blocks.AIR));
                c.set(x,Y+5,z,Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false));
            }
            // seats along the sides, lights in the ceiling
            for(int x=a+1;x<b;x++){if(x-a==4||x-a==5||x-a==14||x-a==15)continue;c.set(x,Y+3,Plan.TRACK_Z,s(Blocks.AIR));}
            for(int x=a+2;x<b;x+=5)c.set(x,Y+4,Plan.TRACK_Z,s(Blocks.SEA_LANTERN));
            int front=car==0?a:b;
            c.set(front,Y+2,Plan.TRACK_Z-1,s(Blocks.SEA_LANTERN));c.set(front,Y+2,Plan.TRACK_Z+1,s(Blocks.SEA_LANTERN));
        }
        c.wallSign(x0-1,Y+4,Plan.TRACK_Z,Blocks.BIRCH_WALL_SIGN,Direction.WEST,DyeColor.BLACK,false,"","ぼたんど","ワンマン");
    }

    /**
     * A tunnel into a headland for the line and the road: concrete portals with their names, lined tubes lit with
     * sodium lamps, closed some way in by works barriers ({@code dir} -1 west, 1 east).
     */
    private void tunnel(int x0,int dir){
        int len=30;
        for(int k=1;k<=len;k++){
            int x=x0+dir*k;
            // the road tube: z 45..53, 7 high; the rail tube: z 39..43, 6 high
            tube(x,Plan.COAST_Z0-1,Plan.PROM_Z1-2,7,k==len);
            tube(x,Plan.TRACK_Z-2,Plan.TRACK_Z+2,6,k==len);
            if(k%6==3){c.set(x,Y+7,Plan.CENTRE_Z,s(Blocks.OCHRE_FROGLIGHT));c.set(x,Y+6,Plan.TRACK_Z,s(Blocks.OCHRE_FROGLIGHT));}
        }
        // portal faces: a concrete frame round each opening and the name plate
        int fx=x0+dir;
        for(int z=Plan.TRACK_Z-3;z<=Plan.PROM_Z1-1;z++)for(int y=Y+1;y<=Y+9;y++){
            boolean open=!c.air(fx,y,z)?false:true;
            if(!open&&!c.water(fx,y,z))c.set(fx,y,z,y>=Y+8?s(Blocks.SMOOTH_STONE):s(Blocks.LIGHT_GRAY_CONCRETE));
        }
        int sx=x0;
        c.wallSign(sx,Y+8,Plan.CENTRE_Z,Blocks.DARK_OAK_WALL_SIGN,dir<0?Direction.EAST:Direction.WEST,DyeColor.WHITE,false,dir<0?"みさきトンネル":"なぎさトンネル",dir<0?"MISAKI TUNNEL":"NAGISA TUNNEL","L = 412 m");
        int bx=x0+dir*len;
        for(int z=Plan.COAST_Z0;z<=Plan.COAST_Z1;z+=2)c.set(bx-dir,Y+1,z,s(Blocks.RED_CONCRETE));
        c.wallSign(bx-dir*2,Y+2,Plan.CENTRE_Z,Blocks.BIRCH_WALL_SIGN,dir<0?Direction.EAST:Direction.WEST,DyeColor.RED,false,"この先","工事中","通行止め","ご迷惑をおかけします");
    }
    private void tube(int x,int z0,int z1,int h,boolean closed){
        for(int z=z0;z<=z1;z++){
            for(int y=Y-2;y<=Y+h+1;y++){
                boolean wall=z==z0||z==z1||y==Y+h+1||y<=Y;
                boolean arch=(z==z0+1||z==z1-1)&&y==Y+h;
                if(closed)c.set(x,y,z,s(Blocks.LIGHT_GRAY_CONCRETE));
                else if(wall||arch)c.set(x,y,z,y==Y?Pal.asphalt(x,z):y<Y?s(Blocks.STONE):s(Blocks.LIGHT_GRAY_CONCRETE));
                else c.set(x,y,z,s(Blocks.AIR));
            }
        }
        if(!closed&&z1-z0==8){
            for(int z=z0+1;z<z1;z++){c.set(x,Y,z,z==Plan.CENTRE_Z&&Math.floorMod(x,8)<4?s(Blocks.WHITE_CONCRETE):Pal.asphalt(x,z));}
        }
        if(!closed&&z1-z0==4){
            for(int z=z0+1;z<z1;z++)c.set(x,Y,z,s(Blocks.GRAVEL));
            c.set(x,Y+1,Plan.TRACK_Z,Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE,RailShape.EAST_WEST));
        }
    }
}
