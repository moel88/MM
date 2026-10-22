package dev.sangwoo.botandtown;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * The fantasy capital in the upper glass's east half (map v3.0, the user's request: '판타지 왕도는 거대한 성과 외벽으로'):
 * a whole city inside high stone walls eighteen blocks tall, round towers along them every forty blocks under blue
 * cones, two gatehouses (west on the axis road, south-west toward the rift); its streets paved in stone, the main
 * avenue from the west gate to the great castle; houses of stone below and plaster with dark timbers above under steep
 * blue, slate and green-copper roofs, some with a round turret at a corner; the cathedral north of the avenue across
 * its square and fountain; and the great castle on its plateau at the avenue's end: an octagon of curtain walls with
 * eight round towers, a gatehouse to the west, the palace with its corner towers and the great tower rising over all,
 * the island's second landmark. Lanterns on black posts with Botand's sky-blue banners light the streets.
 */
final class Capital {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int CODE=2;
    static final Kit.Roof[] ROOFS={Kit.NAVY,Kit.NAVY,Kit.NAVY,Kit.SLATE,Kit.NAVY,Kit.TEAL,Kit.NAVY,Kit.SLATE};
    /** The towers' cones: stairs of the blue slates (their tall side to the middle) over the full blocks. */
    static final Block CONE_STAIRS=TB.of("blue_shingles_stairs").getBlock();
    static final BlockState CONE=TB.of("blue_shingles");
    final List<Lot> lots=new ArrayList<>();
    Capital(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}
    private static BlockState s(Block b){return b.getDefaultState();}

    void build(){
        walls();
        castle();
        cathedral();
        // the houses: inside the walls only, along both sides of every street, then wherever they fit between
        var streets=t.roads.streets(Plan.CAPITAL);
        for(int x=Plan.WALL_X-Plan.WALL_R-40;x<=Plan.WALL_X+Plan.WALL_R+40;x++)for(int z=Plan.WALL_Z-Plan.WALL_R-40;z<=Plan.WALL_Z+Plan.WALL_R+40;z++)
            if(Canvas.inPlan(x,z)&&Plan.zone(x,z)==Plan.CAPITAL&&Plan.insideWalls(x,z)<12&&c.owner(x,z)==0)c.own(x,z,OUTSIDE);
        for(var s:streets)for(int side=-1;side<=1;side+=2)lots.addAll(t.lots.along(s,side,t.roads.half(s)+3,15,16,6,2,7,Plan.CAPITAL,"capital",CODE));
        lots.addAll(t.lots.fill(Plan.CAPITAL,13,13,21,2,5,2,"capital",CODE,70,rnd.nextLong()));
        int turrets=0;
        for(Lot l:lots){if(house(l))turrets++;}
        Town.log("capital: "+lots.size()+" houses ("+turrets+" with turrets), "+c.placed);
        for(var s:streets)lamps(s,1,t.roads.half(s));
        // outside the walls the land is the city's: open fields, free of houses
        for(int x=Plan.WALL_X-Plan.WALL_R-40;x<=Plan.WALL_X+Plan.WALL_R+40;x++)for(int z=Plan.WALL_Z-Plan.WALL_R-40;z<=Plan.WALL_Z+Plan.WALL_R+40;z++)
            if(Canvas.inPlan(x,z)&&c.owner(x,z)==OUTSIDE)c.own(x,z,0);
    }
    /** What the land outside the walls is held by while the houses are placed (so none stand outside them). */
    static final int OUTSIDE=12;

    // ---------------------------------------------------------------- the walls

    /**
     * The city walls: five blocks thick and eighteen tall from the ground along their line (a little uneven, as walls
     * built round a city), a walk on top behind battlements, arrow slits; a round tower every forty blocks or so, eight
     * across and twenty-eight tall under a blue cone; the two gates left open for their gatehouses.
     */
    private void walls(){
        int H=18,cx=Plan.WALL_X,cz=Plan.WALL_Z,R=Plan.WALL_R+20;
        BlockState wall=s(Blocks.STONE_BRICKS);
        for(int x=cx-R;x<=cx+R;x++)for(int z=cz-R;z<=cz+R;z++){
            if(!Canvas.inPlan(x,z))continue;
            double in=Plan.insideWalls(x+0.5,z+0.5);
            if(in<-2.5||in>2.5)continue;
            double a=Math.atan2(z+0.5-cz,x+0.5-cx);
            if(gateAt(a,9))continue;
            if(Plan.shore(x,z)>-1||t.terrain.kind[Canvas.idx(x,z)]==Terrain.SEA)continue;
            int g=c.ground(x,z),top=g+H;
            for(int y=g-2;y<=top;y++){
                double h=Pal.hash(x,y,z);
                boolean slit=in<-1.5&&(y-g)%7==4&&((int)Math.floor(a*Plan.WALL_R/3))%3==0;
                c.set(x,y,z,slit?s(Blocks.AIR):h<0.10?s(Blocks.CRACKED_STONE_BRICKS):h<0.18?s(Blocks.MOSSY_STONE_BRICKS):h<0.24?s(Blocks.ANDESITE):wall);
            }
            // the walk on top, battlements on the outer edge
            if(in<-1.5&&(((x+z)&1)==0))c.set(x,top+1,z,wall);
            c.clear(x,top+2,z,x,top+6,z);
            c.own(x,z,CODE);
        }
        int towers=0;
        int n=(int)Math.round(2*Math.PI*Plan.WALL_R/40);
        for(int k=0;k<n;k++){
            double a=k*2*Math.PI/n+0.05;
            if(gateAt(a,26))continue;
            double[] p=Plan.wallAt(a);
            if(Plan.shore(p[0],p[1])>-6)continue;
            int g=c.ground((int)Math.floor(p[0]),(int)Math.floor(p[1]));
            tower(p[0],p[1],g-1,g+H+10,4.0,wall,Blocks.DEEPSLATE_TILE_STAIRS,s(Blocks.DEEPSLATE_TILES),false);
            coneRoof(p[0],p[1],g+H+11,4.0);
            towers++;
        }
        gatehouse(Plan.GATE_W,true);gatehouse(Plan.GATE_SW,false);
        Town.log("capital: walls with "+towers+" towers and two gatehouses, "+c.placed);
    }
    /** Whether a bearing from the walls' middle falls within {@code w} blocks (along the wall) of a gate. */
    private static boolean gateAt(double a,double w){
        for(double g:new double[]{Plan.GATE_W,Plan.GATE_SW}){double d=Math.abs(Math.atan2(Math.sin(a-g),Math.cos(a-g)))*Plan.WALL_R;if(d<w)return true;}
        return false;
    }
    /**
     * A gatehouse at a bearing: two great round towers either side of the road through the walls under blue cones, an
     * arch over the road joining them, the portcullis drawn up under it, Botand's sky-blue banners on the towers.
     */
    private void gatehouse(double a,boolean main){
        double[] p=Plan.wallAt(a);
        double tx=-Math.sin(a),tz=Math.cos(a),nx=Math.cos(a),nz=Math.sin(a);
        int g=c.ground((int)Math.floor(p[0]),(int)Math.floor(p[1]));
        double r=main?6.2:5.2;int H=main?30:26,span=main?11:9;
        BlockState wall=s(Blocks.STONE_BRICKS);
        for(int side=-1;side<=1;side+=2){
            double gx=p[0]+tx*side*(span/2.0+r),gz=p[1]+tz*side*(span/2.0+r);
            int gg=c.ground((int)Math.floor(gx),(int)Math.floor(gz));
            tower(gx,gz,Math.min(gg,g)-1,g+H,r,wall,Blocks.DEEPSLATE_TILE_STAIRS,s(Blocks.DEEPSLATE_TILES),false);
            coneRoof(gx,gz,g+H+1,r);
            int bx=(int)Math.floor(gx-nx*(r+1)),bz=(int)Math.floor(gz-nz*(r+1));
            Direction out=Direction.getFacing(-nx,0,-nz);
            if(c.air(bx,g+12,bz))c.set(bx,g+12,bz,Blocks.LIGHT_BLUE_WALL_BANNER.getDefaultState().with(Properties.HORIZONTAL_FACING,out));
        }
        // the arch: a block of wall over the road between the towers, the road clear under it, the portcullis's bars at its top
        int clear=main?12:11;
        for(double u=-(span/2.0+r*0.6);u<=span/2.0+r*0.6;u+=0.5)for(double v=-3;v<=3;v+=0.5){
            int x=(int)Math.floor(p[0]+tx*u+nx*v),z=(int)Math.floor(p[1]+tz*u+nz*v);
            boolean road=Math.abs(u)<=span/2.0;
            for(int y=g+1;y<=g+H-6;y++){
                if(road&&y<=g+clear){if(y>c.ground(x,z))c.set(x,y,z,s(Blocks.AIR));continue;}
                c.set(x,y,z,y==g+clear+1&&road?s(Blocks.CHISELED_STONE_BRICKS):wall);
            }
            if(road&&Math.abs(v)<0.6)c.set(x,g+clear,z,s(Blocks.IRON_BARS));
            if(((x+z)&1)==0)c.set(x,g+H-5,z,wall);
            c.own(x,z,CODE);
        }
    }

    // ---------------------------------------------------------------- towers

    /**
     * A round tower {@code r} blocks round (cx, cz) from y0 up to y1: walls of {@code wall} with slit windows, hollow
     * inside over a floor; a cone of {@code roof} stairs (their tall side to the middle) over {@code full} with a
     * finial, or battlements instead of a cone.
     */
    void tower(double cx,double cz,int y0,int y1,double r,BlockState wall,Block roof,BlockState full,boolean cone){
        int R=(int)Math.ceil(r+2);
        for(int x=(int)Math.floor(cx-R);x<=cx+R;x++)for(int z=(int)Math.floor(cz-R);z<=cz+R;z++){
            double d=Math.hypot(x+0.5-cx,z+0.5-cz);
            if(d>r+0.5)continue;
            boolean shell=d>r-0.8;
            for(int y=y0;y<=y1;y++){
                if(y==y0)c.set(x,y,z,s(Blocks.SPRUCE_PLANKS));
                else if(!shell)c.set(x,y,z,s(Blocks.AIR));
                else{
                    double a=Math.atan2(z+0.5-cz,x+0.5-cx);
                    boolean slit=(y-y0)%5==3&&Math.abs(Math.sin(2*a))>0.92;
                    c.set(x,y,z,slit?s(Blocks.GLASS_PANE):wall);
                }
            }
            if(!cone&&shell)c.set(x,y1+1,z,((x+z)&1)==0?wall:s(Blocks.AIR));
            if(!cone&&!shell)c.set(x,y1,z,s(Blocks.SPRUCE_PLANKS));
        }
        if(!cone)return;
        int H=(int)Math.round((r+1.4)*2.1);
        for(int k=0;k<=H;k++){
            double rk=(r+1.4)*(1-k/(double)H);
            for(int x=(int)Math.floor(cx-R);x<=cx+R;x++)for(int z=(int)Math.floor(cz-R);z<=cz+R;z++){
                double dx=x+0.5-cx,dz=z+0.5-cz,d=Math.hypot(dx,dz);
                if(d>rk+0.5)continue;
                int y=y1+1+k;
                if(d>rk-0.7&&rk>0.8)c.set(x,y,z,Canvas.stairs(roof,Direction.getFacing(-dx,0,-dz),false));
                else c.set(x,y,z,full);
            }
        }
        c.set((int)Math.floor(cx),y1+2+H,(int)Math.floor(cz),Canvas.rod(Blocks.LIGHTNING_ROD,Direction.UP));
    }

    // ---------------------------------------------------------------- the great castle

    /**
     * The great castle on its plateau at the main avenue's end, facing west down it: an octagon of curtain walls
     * forty-two blocks out, sixteen high with a walk of battlements, a round tower under a blue cone at each corner;
     * the gatehouse in the west wall; a courtyard of cobbles and paths with a well; the palace in the middle, six
     * storeys of stone under a great blue roof with a round tower at each corner and two before its door; the great
     * tower rising from the palace's back over the island under its cone (its banner about y 180, under the clouds);
     * wide steps from the gate down the plateau's cliff to the avenue.
     */
    private void castle(){
        int cx=Plan.CASTLE_X,cz=Plan.CASTLE_Z,y=Plan.CASTLE_Y;
        for(int x=cx-Plan.PLATEAU_R-8;x<=cx+Plan.PLATEAU_R+8;x++)for(int z=cz-Plan.PLATEAU_R-8;z<=cz+Plan.PLATEAU_R+8;z++)
            if(Canvas.inPlan(x,z)&&Math.hypot(x-cx,z-cz)<Plan.PLATEAU_R+8)c.own(x,z,CODE);
        int R=Plan.CASTLE_R,wallH=16;
        BlockState wall=s(Blocks.STONE_BRICKS);
        // the courtyard
        for(int x=cx-R;x<=cx+R;x++)for(int z=cz-R;z<=cz+R;z++){
            if(!octagon(x+0.5-cx,z+0.5-cz,R-1.5))continue;
            if(c.ground(x,z)<y-2)continue;
            t.lots.setGround(x,z,y);
            boolean path=Math.abs(z-cz)<=3||Math.abs(x-cx+R/2)<=2;
            c.set(x,y,z,path?(((x+z)&1)==0?s(Blocks.STONE_BRICKS):s(Blocks.POLISHED_ANDESITE)):Pal.hash(x,3,z)<0.7?s(Blocks.COBBLESTONE):s(Blocks.GRAVEL));
            c.clear(x,y+1,z,x,y+30,z);
        }
        // the curtain walls: three thick, a walk of battlements on top
        for(int x=cx-R-2;x<=cx+R+2;x++)for(int z=cz-R-2;z<=cz+R+2;z++){
            double dx=x+0.5-cx,dz=z+0.5-cz;
            if(!octagon(dx,dz,R+1.5)||octagon(dx,dz,R-1.5))continue;
            int g=c.ground(x,z);
            for(int yy=Math.min(g,y)-1;yy<=y+wallH;yy++)c.set(x,yy,z,Pal.hash(x,yy,z)<0.12?s(Blocks.CRACKED_STONE_BRICKS):Pal.hash(x,yy,z)<0.2?s(Blocks.MOSSY_STONE_BRICKS):wall);
            boolean outer=!octagon(dx,dz,R+0.5);
            if(outer&&((x+z)&1)==0)c.set(x,y+wallH+1,z,wall);
        }
        // the gate in the west wall
        for(int z=cz-4;z<=cz+4;z++)for(int x=cx-R-3;x<=cx-R+3;x++){
            for(int yy=y+1;yy<=y+10;yy++)c.set(x,yy,z,s(Blocks.AIR));
            c.set(x,y,z,s(Blocks.STONE_BRICKS));
        }
        for(int z=cz-3;z<=cz+3;z++)c.set(cx-R,y+10,z,s(Blocks.IRON_BARS));
        for(int side=-1;side<=1;side+=2){
            tower(cx-R-0.5,cz+side*7.5,y-1,y+wallH+10,4.2,wall,Blocks.DEEPSLATE_TILE_STAIRS,s(Blocks.DEEPSLATE_TILES),false);
            coneRoof(cx-R-0.5,cz+side*7.5,y+wallH+11,4.2);
            c.set(cx-R-5,y+13,cz+side*7,Blocks.LIGHT_BLUE_WALL_BANNER.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.WEST));
        }
        // corner towers
        for(int k=0;k<8;k++){
            double a=Math.PI/8+k*Math.PI/4,tr=(R+0.5)/Math.cos(Math.PI/8);
            double tx=cx+Math.cos(a)*tr,tz=cz+Math.sin(a)*tr;
            int g=Math.min(y,c.ground((int)Math.floor(tx),(int)Math.floor(tz)));
            tower(tx,tz,g-1,y+wallH+12,5.2,wall,Blocks.DEEPSLATE_TILE_STAIRS,s(Blocks.DEEPSLATE_TILES),false);
            coneRoof(tx,tz,y+wallH+13,5.2);
        }
        palace(cx-4,cz,y);
        // a well in the courtyard
        int wx=cx-24,wz=cz+15;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){c.set(wx+dx,y,wz+dz,dx==0&&dz==0?s(Blocks.WATER):s(Blocks.STONE_BRICKS));c.set(wx+dx,y-1,wz+dz,s(Blocks.STONE_BRICKS));
            if(dx!=0||dz!=0)c.set(wx+dx,y+1,wz+dz,s(Blocks.STONE_BRICK_WALL));}
        for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1})for(int k=2;k<=3;k++)c.set(wx+dx,y+k,wz+dz,s(Blocks.SPRUCE_FENCE));
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)c.set(wx+dx,y+4,wz+dz,Canvas.slab(Blocks.SPRUCE_SLAB,false));
        int foot=gateSteps(cx-R-4,cz,y);
        t.places.put("castle",new int[]{cx-R+8,y+1,cz});
        Town.log("castle: walls, 8 towers, palace and the great tower, steps down to x "+foot+", "+c.placed);
    }
    private static boolean octagon(double dx,double dz,double r){
        double ax=Math.abs(dx),az=Math.abs(dz);
        return ax<=r&&az<=r&&(ax+az)<=r*1.4142*Math.cos(Math.PI/8)*1.0824;
    }
    /** A blue cone roof over a tower's top (its battlements taken off). */
    private void coneRoof(double cx,double cz,int y,double r){
        int R=(int)Math.ceil(r+2);
        for(int x=(int)Math.floor(cx-R);x<=cx+R;x++)for(int z=(int)Math.floor(cz-R);z<=cz+R;z++)if(Math.hypot(x+0.5-cx,z+0.5-cz)<=r+0.5)c.set(x,y,z,s(Blocks.AIR));
        int H=(int)Math.round((r+1.4)*2.3);
        for(int k=0;k<=H;k++){
            double rk=(r+1.4)*(1-k/(double)H);
            for(int x=(int)Math.floor(cx-R);x<=cx+R;x++)for(int z=(int)Math.floor(cz-R);z<=cz+R;z++){
                double dx=x+0.5-cx,dz=z+0.5-cz,d=Math.hypot(dx,dz);
                if(d>rk+0.5)continue;
                c.set(x,y+k,z,d>rk-0.7&&rk>0.8?Canvas.stairs(CONE_STAIRS,Direction.getFacing(-dx,0,-dz),false):CONE);
            }
        }
        c.set((int)Math.floor(cx),y+H+1,(int)Math.floor(cz),Blocks.LIGHT_BLUE_BANNER.getDefaultState());
    }
    /**
     * The palace: x0 at its front (west), across z cz-21..cz+21, six storeys of stone with dark bands, tall windows,
     * a great blue hip roof; a round tower at each corner rising past the roof; two before the door; the great tower
     * behind, its cone high over the island.
     */
    private void palace(int x0,int cz,int y){
        int x1=x0+32,z0=cz-21,z1=cz+21,stories=6,top=y+4*stories;
        var f=new Frame(c,x0,z0,x1,z1,Direction.WEST,y);
        Kit.shell(f,0,0,f.W-1,f.D-1,stories,s(Blocks.STONE_BRICKS),s(Blocks.DEEPSLATE_BRICKS),s(Blocks.POLISHED_DEEPSLATE),s(Blocks.SPRUCE_PLANKS),s(Blocks.DEEPSLATE_BRICKS));
        for(int st=0;st<stories;st++)for(int k=2;k<f.W-2;k+=3){
            f.set(k,4*st+2,0,s(Blocks.GLASS_PANE));f.set(k,4*st+3,0,s(Blocks.GLASS_PANE));
            f.set(k,4*st+2,f.D-1,s(Blocks.GLASS_PANE));f.set(k,4*st+3,f.D-1,s(Blocks.GLASS_PANE));
        }
        for(int st=0;st<stories;st++)for(int k=2;k<f.D-2;k+=3){
            f.set(0,4*st+2,k,s(Blocks.GLASS_PANE));f.set(0,4*st+3,k,s(Blocks.GLASS_PANE));
            f.set(f.W-1,4*st+2,k,s(Blocks.GLASS_PANE));f.set(f.W-1,4*st+3,k,s(Blocks.GLASS_PANE));
        }
        // the great door and its steps
        for(int k=f.W/2-2;k<=f.W/2+2;k++)for(int yy=1;yy<=6;yy++)f.set(k,yy,0,s(Blocks.AIR));
        for(int k=f.W/2-3;k<=f.W/2+3;k++){f.set(k,7,0,s(Blocks.CHISELED_STONE_BRICKS));f.set(k,0,-1,Canvas.stairs(Blocks.STONE_BRICK_STAIRS,Direction.SOUTH,false));}
        for(int k=1;k<=3;k++)f.hangingLantern(f.W/2,4*k+3,f.D/2,false);
        Kit.roof(f,0,0,f.W-1,f.D-1,4*stories,Kit.HIP,true,Kit.NAVY,s(Blocks.STONE_BRICKS),s(Blocks.SPRUCE_PLANKS));
        BlockState wall=s(Blocks.STONE_BRICKS);
        // corner towers past the roof
        for(int dx:new int[]{x0,x1})for(int dz:new int[]{z0,z1}){
            tower(dx+0.5,dz+0.5,y,top+12,3.8,wall,Blocks.DEEPSLATE_TILE_STAIRS,s(Blocks.DEEPSLATE_TILES),false);
            coneRoof(dx+0.5,dz+0.5,top+13,3.8);
        }
        // two towers before the door
        for(int side=-1;side<=1;side+=2){
            tower(x0-3.5,cz+side*8.5,y,top+5,3.2,wall,Blocks.DEEPSLATE_TILE_STAIRS,s(Blocks.DEEPSLATE_TILES),false);
            coneRoof(x0-3.5,cz+side*8.5,top+6,3.2);
            c.set(x0-7,y+12,cz+side*8,Blocks.LIGHT_BLUE_WALL_BANNER.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.WEST));
        }
        // the great tower behind
        tower(x1-9.5,cz+0.5,y,top+36,8.0,wall,Blocks.DEEPSLATE_TILE_STAIRS,s(Blocks.DEEPSLATE_TILES),false);
        coneRoof(x1-9.5,cz+0.5,top+37,8.0);
    }
    /** Wide steps from the castle's gate down the plateau's cliff westward to the avenue's end. Returns the foot's x. */
    private int gateSteps(int x,int cz,int y){
        int x0=x;
        for(;x>x0-60;x--){
            int g=Integer.MAX_VALUE;for(int z=cz-4;z<=cz+4;z++)g=Math.min(g,c.ground(x,z));
            boolean down=g<y;
            if(down)y--;
            for(int z=cz-4;z<=cz+4;z++){
                for(int yy=Math.min(c.ground(x,z),y)-2;yy<y;yy++)c.set(x,yy,z,s(Blocks.STONE));
                c.set(x,y,z,down?Canvas.stairs(Blocks.STONE_BRICK_STAIRS,Direction.EAST,false):s(Blocks.STONE_BRICKS));
                c.clear(x,y+1,z,x,y+8,z);c.ground(x,z,y);
            }
            for(int z:new int[]{cz-5,cz+5}){int top=Math.max(y,c.ground(x,z));for(int yy=Math.min(y,c.ground(x,z))-1;yy<=top;yy++)c.set(x,yy,z,t.terrain.masonry(x,yy,z));c.set(x,top+1,z,s(Blocks.STONE_BRICK_WALL));if((x&7)==0)c.set(x,top+2,z,s(Blocks.LANTERN));c.clear(x,top+3,z,x,top+8,z);c.ground(x,z,top);}
            if(t.roads.on(x-1,cz)&&!down)break;
            int next=Integer.MAX_VALUE;for(int z=cz-4;z<=cz+4;z++)next=Math.min(next,c.ground(x-1,z));
            if(next>=y&&!down&&g<=y&&x<x0-12)break;
        }
        return x;
    }
    // ---------------------------------------------------------------- the cathedral

    /**
     * The cathedral inside the loop, its front to the rift (south): a nave of pale stone fourteen high with tall
     * windows of sky-blue glass under a blue roof, lower aisles either side under lean-to roofs, twin towers at the front
     * with spires, a rose window over the great door, an apse behind; pews, an altar and hanging lights inside; its
     * square in front paved in two greys round a fountain.
     */
    private void cathedral(){
        int cx=Plan.CATHEDRAL_X,cz=Plan.CATHEDRAL_Z;
        int nx0=cx-6,nx1=cx+6,nz0=cz-20,nz1=cz+16;
        int y=c.ground(cx,nz1+4);
        var site=new Lot(cx-13,nz0-8,cx+13,nz1+2,y,Direction.SOUTH,"cathedral");
        t.lots.level(site);c.claim(site.x0,site.z0,site.x1,site.z1,CODE);
        BlockState wall=s(Blocks.STONE_BRICKS),pale=s(Blocks.POLISHED_DIORITE),dark=s(Blocks.DEEPSLATE_TILES);
        // floor
        for(int x=cx-12;x<=cx+12;x++)for(int z=nz0-7;z<=nz1+1;z++)c.set(x,y,z,((x+z)&1)==0?s(Blocks.POLISHED_ANDESITE):s(Blocks.POLISHED_DIORITE));
        // the aisles' walls (8 high) and the nave's (14 high) with its clerestory
        box(cx-11,nz0,cx+11,nz1,y,8,wall,pale);
        box(nx0,nz0,nx1,nz1,y,14,wall,pale);
        for(int x=nx0+1;x<nx1;x++)for(int z=nz0+1;z<nz1;z++)for(int k=1;k<=13;k++)c.set(x,y+k,z,s(Blocks.AIR));
        for(int x=cx-10;x<=cx+10;x++)for(int z=nz0+1;z<nz1;z++)if(x<nx0||x>nx1)for(int k=1;k<=7;k++)c.set(x,y+k,z,s(Blocks.AIR));
        // arcades between nave and aisles: pillars every 4 with arches
        for(int z=nz0+1;z<nz1;z++){
            boolean pillar=(z-nz0)%4==0;
            for(int x:new int[]{nx0,nx1}){
                for(int k=1;k<=6;k++)c.set(x,y+k,z,pillar?pale:s(Blocks.AIR));
                if(!pillar)c.set(x,y+6,z,Canvas.slab(Blocks.STONE_BRICK_SLAB,true));
            }
        }
        // tall windows: aisles (y+2..y+6) and clerestory (y+9..y+13), sky-blue and white glass
        for(int z=nz0+2;z<nz1-1;z++){
            if((z-nz0)%4==0)continue;
            for(int x:new int[]{cx-11,cx+11})for(int k=2;k<=6;k++)c.set(x,y+k,z,k==6?s(Blocks.WHITE_STAINED_GLASS):s(Blocks.LIGHT_BLUE_STAINED_GLASS));
            for(int x:new int[]{nx0,nx1})for(int k=9;k<=13;k++)c.set(x,y+k,z,k==13?s(Blocks.WHITE_STAINED_GLASS):s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        }
        // buttresses along the aisles
        for(int z=nz0;z<=nz1;z+=4)for(int x:new int[]{cx-12,cx+12}){for(int k=1;k<=7;k++)c.set(x,y+k,z,wall);c.set(x,y+8,z,Canvas.slab(Blocks.STONE_BRICK_SLAB,false));}
        // roofs: the nave's gable along z, the aisles' lean-tos
        var nave=new Frame(c,nx0,nz0,nx1,nz1,Direction.SOUTH,y);
        Kit.roof(nave,0,0,nave.W-1,nave.D-1,14,Kit.GABLE,false,Kit.NAVY,wall,s(Blocks.SPRUCE_PLANKS));
        var west=new Frame(c,cx-11,nz0,nx0-1,nz1,Direction.EAST,y);
        Kit.roof(west,0,0,west.W-1,west.D-1,8,Kit.SHED,false,Kit.SLATE,wall,s(Blocks.SPRUCE_PLANKS));
        var east=new Frame(c,nx1+1,nz0,cx+11,nz1,Direction.WEST,y);
        Kit.roof(east,0,0,east.W-1,east.D-1,8,Kit.SHED,false,Kit.SLATE,wall,s(Blocks.SPRUCE_PLANKS));
        // the apse behind: a half round
        for(int x=nx0;x<=nx1;x++)for(int z=nz0-7;z<nz0;z++){
            double d=Math.hypot(x+0.5-(cx+0.5),z+0.5-nz0);
            if(d>6.6)continue;
            boolean shell=d>5.6;
            for(int k=1;k<=12;k++)c.set(x,y+k,z,shell?(k>=4&&k<=10&&((x+z)&1)==0?s(Blocks.LIGHT_BLUE_STAINED_GLASS):wall):s(Blocks.AIR));
            int rk=(int)Math.round((6.6-d)*0.9);
            for(int k=0;k<=rk;k++)c.set(x,y+13+k,z,k==rk?TB.slab("blue_shingles_slab",false):CONE);
        }
        for(int x=nx0+1;x<nx1;x++)c.set(x,y+1,nz0,s(Blocks.AIR));
        // the front: twin towers with spires, the great door, the rose window
        for(int side=-1;side<=1;side+=2){
            int tx0=side<0?cx-11:cx+6,tx1=side<0?cx-6:cx+11;
            for(int x=tx0;x<=tx1;x++)for(int z=nz1-4;z<=nz1+1;z++){
                boolean edge=x==tx0||x==tx1||z==nz1-4||z==nz1+1;
                for(int k=1;k<=26;k++)c.set(x,y+k,z,edge?((k%8==6&&(x==tx0+2||x==tx1-2||z==nz1-2))?s(Blocks.LIGHT_BLUE_STAINED_GLASS):wall):s(Blocks.AIR));
            }
            double sx=(tx0+tx1)/2.0+0.5,sz=nz1-1.5+0.5;
            for(int k=0;k<=16;k++){
                double rk=3.4*(1-k/16.0);
                for(int x=tx0;x<=tx1;x++)for(int z=nz1-4;z<=nz1+1;z++){
                    double d=Math.max(Math.abs(x+0.5-sx),Math.abs(z+0.5-sz));
                    if(d<=rk)c.set(x,y+27+k,z,d>rk-1?dark:s(Blocks.AIR));
                }
            }
            c.set((int)Math.floor(sx),y+44,(int)Math.floor(sz),Canvas.rod(Blocks.LIGHTNING_ROD,Direction.UP));
        }
        for(int x=nx0;x<=nx1;x++)for(int k=1;k<=18;k++)c.set(x,y+k,nz1,k>14&&Math.abs(x-cx)>(k-14)*2?s(Blocks.AIR):wall);
        for(int x=cx-2;x<=cx+2;x++)for(int k=1;k<=7;k++){
            boolean arch=k==7&&Math.abs(x-cx)==2;
            c.set(x,y+k,nz1,arch?Canvas.stairs(Blocks.STONE_BRICK_STAIRS,x<cx?Direction.EAST:Direction.WEST,true):s(Blocks.AIR));
        }
        c.door(cx-1,y+1,nz1,Blocks.DARK_OAK_DOOR,Direction.SOUTH,false);c.door(cx+1,y+1,nz1,Blocks.DARK_OAK_DOOR,Direction.SOUTH,true);
        for(int x=cx-3;x<=cx+3;x++)for(int k=9;k<=15;k++){
            double d=Math.hypot(x-cx,k-12);
            if(d<=3.4)c.set(x,y+k,nz1,d<1?s(Blocks.WHITE_STAINED_GLASS):((int)Math.round(Math.atan2(k-12,x-cx)*8/Math.PI)&1)==0?s(Blocks.LIGHT_BLUE_STAINED_GLASS):s(Blocks.BLUE_STAINED_GLASS));
        }
        // inside: pews, the altar, hanging lights
        for(int z=nz0+4;z<nz1-3;z+=2)for(int x=nx0+1;x<nx1;x++)if(Math.abs(x-cx)>1)c.set(x,y+1,z,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.SOUTH,false));
        for(int x=cx-2;x<=cx+2;x++)c.set(x,y+1,nz0+1,s(Blocks.CHISELED_QUARTZ_BLOCK));
        c.set(cx,y+2,nz0+1,s(Blocks.GOLD_BLOCK));
        for(int x:new int[]{cx-2,cx+2})c.set(x,y+2,nz0+1,Blocks.CANDLE.getDefaultState().with(Properties.CANDLES,3).with(Properties.LIT,true));
        for(int z=nz0+3;z<nz1-2;z+=6){c.set(cx,y+13,z,Blocks.CHAIN.getDefaultState());c.set(cx,y+12,z,Blocks.CHAIN.getDefaultState());c.hangingLantern(cx,y+11,z,false);}
        square(cx,nz1+2,y);
        t.places.put("cathedral",new int[]{cx,y+1,nz1+6});
        Town.log("cathedral at "+cx+","+cz+" y "+y+", "+c.placed);
    }
    /** Walls of a box (x0..x1, z0..z1) h high on y, its corners in the trim stone. */
    private void box(int x0,int z0,int x1,int z1,int y,int h,BlockState wall,BlockState trim){
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            if(x!=x0&&x!=x1&&z!=z0&&z!=z1)continue;
            boolean corner=(x==x0||x==x1)&&(z==z0||z==z1);
            for(int k=1;k<=h;k++)c.set(x,y+k,z,corner?trim:wall);
        }
    }
    /** The cathedral's square: from its front to the loop road, paved in two greys, a fountain in its middle, benches and lamps round it. */
    private void square(int cx,int z0,int y){
        int z1=z0;
        while(z1<z0+40&&!t.roads.near(cx,z1+1,1))z1++;
        int x0=cx-16,x1=cx+16;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            if(t.roads.on(x,z)||c.owner(x,z)!=0&&c.owner(x,z)!=CODE)continue;
            boolean ring=(Math.abs(x-cx)+Math.abs(z-(z0+z1)/2))%6<3;
            if(c.ground(x,z)!=y)t.lots.setGround(x,z,y);
            c.set(x,y,z,ring?s(Blocks.POLISHED_ANDESITE):s(Blocks.STONE_BRICKS));
            c.clear(x,y+1,z,x,y+6,z);c.own(x,z,CODE);
        }
        int fz=(z0+z1)/2;
        for(int x=cx-4;x<=cx+4;x++)for(int z=fz-4;z<=fz+4;z++){
            double d=Math.hypot(x-cx,z-fz);
            if(d<=4.4){c.set(x,y,z,d>3.5?s(Blocks.STONE_BRICKS):s(Blocks.WATER));if(d>3.5)c.set(x,y+1,z,Canvas.slab(Blocks.STONE_BRICK_SLAB,false));c.set(x,y-1,z,s(Blocks.STONE_BRICKS));}
        }
        c.set(cx,y+1,fz,s(Blocks.STONE_BRICK_WALL));c.set(cx,y+2,fz,s(Blocks.STONE_BRICK_WALL));c.set(cx,y+3,fz,s(Blocks.SEA_LANTERN));c.set(cx,y+4,fz,Canvas.slab(Blocks.STONE_BRICK_SLAB,false));
        for(int dx:new int[]{-12,12})for(int dz:new int[]{-4,4}){int x=cx+dx,z=fz+dz;if(z>=z0&&z<=z1)lamp(x,z,Direction.NORTH);}
        for(int dx:new int[]{-8,8}){int z=fz;c.set(cx+dx,y+1,z,Canvas.stairs(Blocks.DARK_OAK_STAIRS,dx<0?Direction.WEST:Direction.EAST,false));c.set(cx+dx,y+1,z+1,Canvas.stairs(Blocks.DARK_OAK_STAIRS,dx<0?Direction.WEST:Direction.EAST,false));}
        t.places.put("capital_square",new int[]{cx,y+1,fz+6});
    }

    // ---------------------------------------------------------------- houses

    /**
     * A house of the capital on its plot: stone below, plaster with dark timbers above, two or three floors under a
     * steep roof of blue, slate or green copper, a chimney, windows with white frames, a door with lanterns; a round
     * turret at a front corner of some. Returns whether it got a turret.
     */
    private boolean house(Lot l){
        Frame f=Frame.of(c,l);
        int W=f.W,D=f.D;
        int hw=Math.min(W-2,9+rnd.nextInt(4)),hd=Math.min(D-4,8+rnd.nextInt(4));
        int bx0=(W-hw)/2,bx1=bx0+hw-1,bz0=2,bz1=bz0+hd-1;
        int stories=2+rnd.nextInt(2);
        BlockState stone=Pal.pick(rnd,new BlockState[]{s(Blocks.STONE_BRICKS),s(Blocks.STONE_BRICKS),s(Blocks.POLISHED_ANDESITE),s(Blocks.COBBLESTONE)});
        BlockState plaster=Pal.pick(rnd,new BlockState[]{TB.STUCCO_WHITE,TB.STUCCO_CREAM,TB.STUCCO_WHITE,s(Blocks.SMOOTH_SANDSTONE),TB.STUCCO_GRAY});
        BlockState timber=Canvas.axis(Blocks.STRIPPED_DARK_OAK_LOG,Direction.Axis.Y),beam=Canvas.axis(Blocks.STRIPPED_DARK_OAK_LOG,Direction.Axis.X);
        Kit.Roof roof=ROOFS[rnd.nextInt(ROOFS.length)];
        // the yard: cobbles in front, grass behind
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){f.clear(x,1,z,x,12,z);f.set(x,0,z,z<bz0?s(Blocks.COBBLESTONE):s(Blocks.GRASS_BLOCK));}
        Kit.shell(f,bx0,bz0,bx1,bz1,stories,plaster,timber,beam,s(Blocks.OAK_PLANKS),stone);
        // the ground floor in stone, timbers framing the floors above
        Houses.perimeter(f,bx0,bz0,bx1,bz1,(x,z,corner)->{
            for(int y=1;y<=3;y++)f.set(x,y,z,stone);
            for(int s=1;s<stories;s++){
                boolean post=corner||((x-bx0)%3==0&&(z==bz0||z==bz1))||((z-bz0)%3==0&&(x==bx0||x==bx1));
                if(post)for(int y=4*s+1;y<=4*s+3;y++)f.set(x,y,z,timber);
            }
        });
        int door=bx0+hw/2;
        for(int s=0;s<stories;s++){
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'f',s,TB.WIN_WHITE,TB.WIN_WHITE_CURTAIN,s==0?door:-99,true);
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'l',s,TB.WIN_WHITE,TB.WIN_WHITE_CURTAIN,-99,true);
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'r',s,TB.WIN_WHITE,TB.WIN_WHITE_CURTAIN,-99,true);
            Kit.windows(f,rnd,bx0,bz0,bx1,bz1,'b',s,TB.WIN_WHITE,TB.WIN_WHITE_CURTAIN,-99,true);
        }
        f.door(door,1,bz0,Pal.pick(rnd,Blocks.DARK_OAK_DOOR,Blocks.SPRUCE_DOOR,Blocks.OAK_DOOR),Direction.NORTH,false);
        for(int dx:new int[]{-1,1})f.set(door+dx,3,bz0-1,Blocks.LANTERN.getDefaultState());
        for(int dx=-1;dx<=1;dx++)f.set(door+dx,4,bz0-1,Canvas.stairs(Blocks.DARK_OAK_STAIRS,Direction.SOUTH,true));
        int type=rnd.nextInt(3)==0?Kit.HIP:Kit.GABLE;
        Kit.roof(f,bx0,bz0,bx1,bz1,4*stories,type,rnd.nextBoolean(),roof,plaster,s(Blocks.OAK_PLANKS));
        // a chimney through the roof, smoke from it
        int chx=bx1-1,chz=bz1-1,top=4*stories+1;
        while(top<4*stories+14&&!f.air(chx,top,chz))top++;
        for(int y=4*stories+1;y<=top+1;y++)f.set(chx,y,chz,s(Blocks.BRICKS));
        f.set(chx,top+2,chz,Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT,true).with(CampfireBlock.SIGNAL_FIRE,false));
        for(int s=0;s<stories;s++)if(rnd.nextInt(3)>0)Kit.roomLight(f,(bx0+bx1)/2,4*s,(bz0+bz1)/2,true);
        // window boxes of flowers under the front's upper windows
        for(int x=bx0+1;x<bx1;x++)if(TB.isWindow(f.get(x,6,bz0))&&f.air(x,5,bz0-1))f.set(x,5,bz0-1,Canvas.leaves(rnd.nextBoolean()?Blocks.FLOWERING_AZALEA_LEAVES:Blocks.AZALEA_LEAVES));
        boolean turret=rnd.nextInt(3)==0&&hw>=9;
        if(turret){
            int tx=rnd.nextBoolean()?bx0:bx1;
            int wx=f.wx(tx,bz0),wz=f.wz(tx,bz0);
            tower(wx+0.5,wz+0.5,f.y0+1,f.y0+4*stories+2,2.3,plaster,Blocks.DEEPSLATE_TILE_STAIRS,s(Blocks.DEEPSLATE_TILES),false);
            coneRoof(wx+0.5,wz+0.5,f.y0+4*stories+3,2.3);
        }
        // a low hedge and a gate along the front
        for(int x=0;x<W;x++)if(Math.abs(x-door)>1&&f.air(x,1,0))f.set(x,1,0,rnd.nextInt(4)==0?s(Blocks.SPRUCE_FENCE):TB.HEDGE);
        return turret;
    }

    /** Lamps along a road: lanterns on black posts at the sidewalk's outer edge, a sky-blue banner on every other. */
    private void lamps(double[][] line,int side,int half){
        int n=0;
        for(int k=0;k<line.length;k+=36){
            double[] p=line[k];double nx=-p[3]*side,nz=p[2]*side;
            int x=(int)Math.floor(p[0]+nx*(half-0.6)),z=(int)Math.floor(p[1]+nz*(half-0.6));
            if(Plan.zone(x,z)!=Plan.CAPITAL||!t.roads.on(x,z)||t.roads.map[Canvas.idx(x,z)]!=Roads.WALK)continue;
            Direction road=Math.abs(nx)>=Math.abs(nz)?(nx>0?Direction.WEST:Direction.EAST):(nz>0?Direction.NORTH:Direction.SOUTH);
            if(lamp(x,z,(n&1)==0?road:null))n++;
        }
    }
    private boolean lamp(int x,int z,Direction banner){
        int g=c.ground(x,z);
        for(int y=g+1;y<=g+5;y++)if(!c.air(x,y,z))return false;
        for(int y=g+1;y<=g+4;y++)c.set(x,y,z,s(Blocks.POLISHED_BLACKSTONE_WALL));
        c.set(x,g+5,z,s(Blocks.LANTERN));
        c.set(x,g+6,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
        if(banner!=null&&c.air(x+banner.getOffsetX(),g+3,z+banner.getOffsetZ()))
            c.set(x+banner.getOffsetX(),g+3,z+banner.getOffsetZ(),Blocks.LIGHT_BLUE_WALL_BANNER.getDefaultState().with(Properties.HORIZONTAL_FACING,banner));
        return true;
    }
}
