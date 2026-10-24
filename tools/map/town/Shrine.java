package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.Attachment;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * ぼたんど神社 on the summit of the great mountain at the old town's far corner (map v3.0, the user's request: '일본 옛
 * 신사는 진짜 거대한 산 위에 놔두자 산을 랜드마크로'), facing the town to the south: a gravel precinct on the summit with
 * a stone-paved approach between stone lanterns, a great red torii at its edge, the purification basin under its little
 * roof, the worship hall on a raised floor under a green copper roof with crossed finials, its straw rope and paper
 * streamers, the offering box and the bell, a great sacred tree bound with rope. Snow lies round the summit; cedars
 * cover the mountain's flanks below the rock. Stone steps come all the way down the mountain's south face to the town,
 * a vermilion torii over them every twenty rows, between low walls of old masonry, and a path runs from their foot to
 * the nearest street.
 */
final class Shrine {
    private final Townscape t;private final Canvas c;private final Random rnd;
    static final int Y=Plan.SHRINE_Y,X=Plan.SHRINE_X,Z=Plan.SHRINE_Z,HALF=12;
    static final int CODE=9;
    Shrine(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}
    private static BlockState s(net.minecraft.block.Block b){return b.getDefaultState();}

    void build(){
        // the mountain above its foot is the shrine's: no houses up it
        int R=(int)(Plan.MOUNT_R*0.66);
        for(int x=X-R;x<=X+R;x++)for(int z=Z-R;z<=Z+R;z++)if(Math.hypot(x-X,z-Z)<R&&Canvas.inPlan(x,z)&&c.owner(x,z)==0)c.own(x,z,CODE);
        for(int x=X-HALF;x<=X+HALF;x++)for(int z=Z-HALF;z<=Z+HALF;z++){
            boolean path=Math.abs(x-X)<=2&&z>Z-1;
            c.set(x,Y,z,path?(((x+z)&1)==0?s(Blocks.STONE_BRICKS):s(Blocks.POLISHED_ANDESITE)):s(Blocks.GRAVEL));
            c.clear(x,Y+1,z,x,Y+14,z);c.ground(x,z,Y);
        }
        int foot=stairway();
        torii(X,Z+HALF-1);
        for(int z=Z+HALF-4;z>=Z+1;z-=4)for(int side=-1;side<=1;side+=2)lantern(X+side*4,z);
        basin(X-9,Z+6);
        hall(X-9,Z-HALF+1,X+9,Z-HALF+11);
        sacredTree(X+8,Z+5);
        // cedars on the mountain's wooded flanks, thinning out where the rock and snow begin; none on the stairway
        int cedars=0;
        for(int i=0;i<3000&&cedars<300;i++){
            double a=rnd.nextDouble()*2*Math.PI,r=Plan.MOUNT_SUMMIT+8+Math.sqrt(rnd.nextDouble())*(Plan.MOUNT_R*1.05-Plan.MOUNT_SUMMIT);
            int x=X+(int)Math.round(Math.cos(a)*r),z=Z+(int)Math.round(Math.sin(a)*r);
            if(Math.abs(x-X)<=9&&z>Z)continue;
            if(!Canvas.inPlan(x,z)||Plan.shore(x,z)>-4||t.roads.near(x,z,4))continue;
            int g=c.ground(x,z);
            int thin=Plan.mountLevel(0.39),bare=Plan.mountLevel(0.56);
            if(g>bare||g>thin&&rnd.nextDouble()<(g-thin)/(double)(bare-thin))continue;
            if(!c.get(x,g,z).isOf(Blocks.GRASS_BLOCK)||!c.air(x,g+1,z))continue;
            if(Pal.smooth(x,z,23,61)<0.3)continue;
            t.nature.cedar(x,g+1,z,12+rnd.nextInt(10));cedars++;
        }
        t.places.put("shrine",new int[]{X,Y+1,Z+HALF-3});
        t.places.put("mountain_foot",new int[]{X,c.ground(X,foot+2)+1,foot+2});
        Town.log("shrine: on the mountain's summit (y "+Y+"), steps down to z "+foot+", "+gates+" torii on them, "+cedars+" cedars, "+c.placed);
    }
    int gates;

    /**
     * The stone steps down the mountain's south face from the precinct's edge to the town: five wide, each row at the
     * mountain's own height there (a step where it falls, a landing where it levels, never more than one step a row,
     * built up with stone where the face is steeper and cut through the ridges), low walls of old masonry either side
     * with a stone rail and a lantern now and then, and a vermilion torii over them every twenty rows or so (as on a
     * holy mountain's approach). Two stone lanterns at the foot, then a gravel path to the nearest street. Returns the
     * foot's z.
     */
    private int stairway(){
        int x0=X-2,x1=X+2,y=Y,z=Z+HALF+1,lastGate=z;
        for(;z<Z+Plan.MOUNT_R+80;z++){
            int g=Integer.MAX_VALUE,next=Integer.MAX_VALUE;
            for(int x=x0;x<=x1;x++){g=Math.min(g,c.ground(x,z));next=Math.min(next,c.ground(x,z+1));}
            boolean down=g<y;
            if(down)y--;
            step(x0,x1,z,y,down);
            // a gate on a landing every twenty rows or so, or on a step where the face is too steep for landings
            if(z-lastGate>=20&&(!down||z-lastGate>=24)){stairGate(X,y,z);lastGate=z;gates++;}
            else if((z-Z)%9==0)for(int x:new int[]{x0-1,x1+1}){int top=c.ground(x,z);if(c.air(x,top+2,z))c.set(x,top+2,z,s(Blocks.LANTERN));}
            // the foot: down from the heights to the town's level, where the ground no longer falls
            if(z>Z+Plan.MOUNT_SUMMIT+30&&next>=y&&y<=90)break;
        }
        int foot=z;
        for(int side=-1;side<=1;side+=2){
            int lx=X+side*4,lz=foot+1,g=c.ground(lx,lz);
            c.set(lx,g+1,lz,s(Blocks.STONE_BRICKS));c.set(lx,g+2,lz,s(Blocks.STONE_BRICK_WALL));c.set(lx,g+3,lz,s(Blocks.STONE_BRICK_WALL));
            c.set(lx,g+4,lz,s(Blocks.LANTERN));c.set(lx,g+5,lz,Canvas.slab(Blocks.STONE_BRICK_SLAB,false));
        }
        c.wallSign(X+5,c.ground(X+5,foot+1)+1,foot+1,Blocks.DARK_OAK_WALL_SIGN,Direction.SOUTH,DyeColor.WHITE,false,"ぼたんど神社","参道","BOTAND SHRINE");
        // the path from the foot to the nearest street: south, then east along it if none is met
        int pz=foot+2,px=X;
        for(int k=0;k<220&&!t.roads.near(px,pz,2);k++){
            for(int d=-1;d<=1;d++){
                int qx=px+(k<40?d:0),qz=pz+(k<40?0:d);
                int g=c.ground(qx,qz);
                if(!c.air(qx,g+1,qz))c.clear(qx,g+1,qz,qx,g+3,qz);
                c.set(qx,g,qz,d==0&&((qx+qz)&1)==0?s(Blocks.SMOOTH_STONE):s(Blocks.GRAVEL));
            }
            if(k<40)pz++;else px++;
        }
        return foot;
    }
    /** A vermilion torii over the stairway at row z, standing on its walls' level y. */
    private void stairGate(int x,int y,int z){
        int h=7;
        for(int side=-1;side<=1;side+=2){
            int px=x+side*4,g=Math.min(c.ground(px,z),y);
            for(int yy=g;yy<=y;yy++)c.set(px,yy,z,s(Blocks.STONE_BRICKS));
            for(int yy=y+1;yy<=y+h;yy++)c.set(px,yy,z,s(Blocks.RED_CONCRETE));
            c.set(px,y+1,z,s(Blocks.BLACK_CONCRETE));
        }
        for(int dx=-4;dx<=4;dx++)c.set(x+dx,y+h-1,z,s(Blocks.RED_CONCRETE));
        for(int dx=-5;dx<=5;dx++)c.set(x+dx,y+h+1,z,s(Blocks.BLACK_CONCRETE));
        for(int dx=-4;dx<=4;dx++)c.set(x+dx,y+h,z,s(Blocks.RED_CONCRETE));
        c.set(x-6,y+h+1,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,Direction.EAST,true));
        c.set(x+6,y+h+1,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,Direction.WEST,true));
    }
    /** One row of the stairway at z: a step (or landing) at y, the earth under it made up, the side walls with their rail. */
    private void step(int x0,int x1,int z,int y,boolean stair){
        for(int x=x0;x<=x1;x++){
            int g=c.ground(x,z);
            for(int yy=Math.min(g,y-1)-2;yy<y;yy++)c.set(x,yy,z,s(Blocks.STONE));
            c.set(x,y,z,stair?Canvas.stairs(Blocks.STONE_BRICK_STAIRS,Direction.NORTH,false):s(Blocks.STONE_BRICKS));
            c.clear(x,y+1,z,x,y+8,z);c.ground(x,z,y);
        }
        for(int x:new int[]{x0-1,x1+1}){
            int top=Math.max(y,c.ground(x,z));
            for(int yy=Math.min(y,c.ground(x,z))-1;yy<=top;yy++)c.set(x,yy,z,t.terrain.masonry(x,yy,z));
            c.set(x,top+1,z,s(Blocks.ANDESITE_WALL));
            c.clear(x,top+2,z,x,top+8,z);
            c.ground(x,z,top);
        }
    }

    /** The great torii: vermilion pillars and tie beam, the black lintel with upturned ends, the name tablet. */
    void torii(int x,int z){
        int h=8;
        for(int side=-1;side<=1;side+=2){
            for(int y=Y+1;y<=Y+h;y++)c.set(x+side*3,y,z,s(Blocks.RED_CONCRETE));
            c.set(x+side*3,Y+1,z,s(Blocks.BLACK_CONCRETE));
        }
        for(int dx=-4;dx<=4;dx++)c.set(x+dx,Y+h-2,z,s(Blocks.RED_CONCRETE));
        for(int dx=-4;dx<=4;dx++)c.set(x+dx,Y+h,z,s(Blocks.RED_CONCRETE));
        for(int dx=-5;dx<=5;dx++)c.set(x+dx,Y+h+1,z,s(Blocks.BLACK_CONCRETE));
        c.set(x-6,Y+h+1,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,Direction.EAST,true));
        c.set(x+6,Y+h+1,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,Direction.WEST,true));
        c.set(x-6,Y+h+2,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));c.set(x+6,Y+h+2,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
        c.set(x,Y+h-1,z,s(Blocks.BLACK_CONCRETE));
        c.wallSign(x,Y+h-1,z+1,Blocks.DARK_OAK_WALL_SIGN,Direction.SOUTH,DyeColor.YELLOW,true,"","ぼたんど","神社");
    }
    /** 石灯籠: a stone lantern. */
    void lantern(int x,int z){
        c.set(x,Y+1,z,s(Blocks.STONE_BRICKS));c.set(x,Y+2,z,s(Blocks.STONE_BRICK_WALL));c.set(x,Y+3,z,s(Blocks.STONE_BRICK_WALL));
        c.set(x,Y+4,z,s(Blocks.LANTERN));c.set(x,Y+5,z,Canvas.slab(Blocks.STONE_BRICK_SLAB,false));
    }
    /** 手水舎: a stone basin of water under a small roof on four posts. */
    private void basin(int x,int z){
        c.set(x,Y+1,z,Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3,3));c.set(x+1,Y+1,z,Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3,3));
        for(int dx:new int[]{-1,2})for(int dz:new int[]{-1,1})for(int y=Y+1;y<=Y+3;y++)c.set(x+dx,y,z+dz,s(Blocks.DARK_OAK_FENCE));
        var f=new Frame(c,x-1,z-1,x+2,z+1,Direction.SOUTH,Y);
        Kit.roof(f,0,0,f.W-1,f.D-1,4,Kit.GABLE,true,Kit.COPPER,s(Blocks.AIR),s(Blocks.AIR));
    }

    /** 拝殿: the worship hall. */
    private void hall(int x0,int z0,int x1,int z1){
        var f=new Frame(c,x0,z0,x1,z1,Direction.SOUTH,Y+1);
        int W=f.W,D=f.D;
        BlockState post=Canvas.axis(Blocks.STRIPPED_DARK_OAK_LOG,Direction.Axis.Y),wall=s(Blocks.SPRUCE_PLANKS),white=s(Blocks.CALCITE);
        // a raised floor on a stone base, steps up at the front
        for(int x=-1;x<=W;x++)for(int z=-1;z<=D;z++)f.set(x,-1,z,s(Blocks.STONE_BRICKS));
        for(int x=-1;x<=W;x++)for(int z=-1;z<=D;z++)f.set(x,0,z,x<0||x>=W||z<0||z>=D?Canvas.slab(Blocks.SPRUCE_SLAB,true):s(Blocks.SPRUCE_PLANKS));
        for(int x=W/2-2;x<=W/2+2;x++)f.set(x,0,-2,Canvas.stairs(Blocks.SPRUCE_STAIRS,Direction.SOUTH,false));
        for(int x=0;x<W;x++)for(int z=0;z<D;z++){
            boolean edge=x==0||x==W-1||z==0||z==D-1;
            boolean p=(x%3==0||x==W-1)&&(z==0||z==D-1)||(z%3==0||z==D-1)&&(x==0||x==W-1);
            for(int y=1;y<=5;y++)f.set(x,y,z,!edge?s(Blocks.AIR):p?post:y>=4?white:wall);
        }
        // the front stands open between its posts
        for(int x=1;x<W-1;x++)if(x%3!=0)for(int y=1;y<=4;y++)f.set(x,y,0,s(Blocks.AIR));
        // the straw rope across the front with paper streamers, the bell, the offering box
        for(int x=1;x<W-1;x++)f.set(x,5,-1,Canvas.axis(Blocks.HAY_BLOCK,Direction.Axis.X));
        for(int x=2;x<W-1;x+=3)f.set(x,4,-1,s(Blocks.WHITE_WALL_BANNER).with(Properties.HORIZONTAL_FACING,Direction.NORTH));
        f.set(W/2,4,0,s(Blocks.BELL).with(Properties.ATTACHMENT,Attachment.CEILING).with(Properties.HORIZONTAL_FACING,Direction.NORTH));
        f.set(W/2,3,0,s(Blocks.RED_WOOL));
        f.set(W/2,1,-1,s(Blocks.BARREL));
        // inside: a dim sanctuary, lanterns, the mirror
        f.set(W/2,1,D-2,s(Blocks.GOLD_BLOCK));f.set(W/2,2,D-2,s(Blocks.GLASS));
        f.hangingLantern(2,5,D/2,false);f.hangingLantern(W-3,5,D/2,false);
        for(int x=1;x<W-1;x++)for(int z=1;z<D-1;z++)f.set(x,6,z,s(Blocks.SPRUCE_PLANKS));
        // the roof: green copper with a wide overhang, crossed finials at the gables, logs along the ridge
        Kit.roof(f,-1,-1,W,D,6,Kit.GABLE,true,Kit.COPPER,white,s(Blocks.SPRUCE_PLANKS));
        int ridge=6+(D+3)/4+1;
        for(int x:new int[]{-2,W+1})for(int k=0;k<3;k++){
            f.set(x,ridge+k,D/2-k,s(Blocks.DARK_OAK_FENCE));f.set(x,ridge+k,D/2+k,s(Blocks.DARK_OAK_FENCE));
        }
        for(int x=1;x<W-1;x+=3)f.set(x,ridge,D/2,Canvas.axis(Blocks.DARK_OAK_LOG,Direction.Axis.Z));
    }
    /** 御神木: a huge old camphor bound with a straw rope. */
    private void sacredTree(int x,int z){
        int g=c.ground(x,z);
        for(int y=g+1;y<=g+9;y++)for(int dx=0;dx<=1;dx++)for(int dz=0;dz<=1;dz++)c.set(x+dx,y,z+dz,Canvas.axis(Blocks.DARK_OAK_LOG,Direction.Axis.Y));
        for(int dx=-1;dx<=2;dx++)for(int dz=-1;dz<=2;dz++)if((dx==-1||dx==2||dz==-1||dz==2)&&!(Math.abs(dx-0.5)>1&&Math.abs(dz-0.5)>1))c.set(x+dx,g+3,z+dz,Canvas.axis(Blocks.HAY_BLOCK,Direction.Axis.Y));
        t.nature.blob(x+1,g+12,z+1,7,4.5,7,TB.CANOPY);
        t.nature.blob(x-2,g+14,z+3,4,3,4,TB.CANOPY);
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2+0.4;
            for(int k=1;k<=4;k++)c.set(x+(int)Math.round(Math.cos(a)*k),g+9+k/2,z+(int)Math.round(Math.sin(a)*k),Canvas.axis(Blocks.DARK_OAK_LOG,Math.abs(Math.cos(a))>0.7?Direction.Axis.X:Direction.Axis.Z));
        }
    }
}
