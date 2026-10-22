package dev.sangwoo.botandtown;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * The cyber city in the lower glass's east half (map v3.0), rebuilt from the user's five photos (2.49.0: '사이버 구역만
 * 만들고 사진만 보내줘'). Its middle is a sunken plaza (the first photo): terraces stepping down with orange-edged
 * treads, a pavilion, pits rimmed with light, teal trees, and round it eight monolithic towers of dark stone inlaid with
 * salmon geometry, white balconies and red bands. Round that the ring (the third photo, CyberRing): a deck forty-two
 * blocks up on huge pillars with a road, a monorail and its train, and under it the undercity market (the second
 * photo). Two avenues cross the city to the plaza, lined beyond the ring with arcades (the fourth photo, CyberTowers);
 * streets run round, and the towers beyond (the fifth photo) wear their storeys in bands of coloured light, some round,
 * with tanks, helipads and beams of light on their roofs. Over the plaza a hologram of an hourglass, over the ring's
 * south side a koi of light; a promenade along the sea wall; lamps, cars, crosswalks striped yellow, teal trees. The
 * photos' signs, faces and the studio's logo are not used: the signs here are Botand's own, in Hangul. Nothing stands
 * higher than y 189 (the clouds are at 192). The day and night as everywhere else (the user's choice).
 */
final class Cyber {
    private final Townscape t;private final Canvas c;private final Random rnd;
    final CyberTowers towers;final CyberRing ring;
    static final int CODE=3,BAND_CODE=13,PLAZA_CODE=14;
    /** The city's ground, and its middle: the plaza's and the ring's centre. */
    static final int Y=Plan.BASE,MID_X=235,MID_Z=300;
    /** The plaza: half its side and its floor's level; the terraces round it are ten blocks wide. */
    static final int PLAZA=34,PLAZA_Y=Y-5;
    /** The ring: the radius of its centre line, half its deck's width, its deck's top. */
    static final int RING=104,RING_HALF=7,DECK=Y+42;
    /** No roof higher than ROOF_Y and no mast higher than MAST_Y: the towers stay under the clouds (y 192). */
    static final int ROOF_Y=186,MAST_Y=189;
    /** The avenues (to the plaza) and the streets (round the ring). */
    static final int AVENUE_HALF=12,STREET_HALF=7;
    static final int[] STREET_Z={MID_Z-128,MID_Z+128},STREET_X={MID_X-130,MID_X+130};
    static final BlockState PAVING=TB.of("cyber_paving"),WET=TB.of("wet_asphalt");
    int monoliths,arcades,banded,round,trees,lamps,cars,crossings;
    Cyber(Townscape t){
        this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());
        towers=new CyberTowers(t,new Random(rnd.nextLong()));ring=new CyberRing(t,new Random(rnd.nextLong()),towers);
    }
    private static BlockState s(Block b){return b.getDefaultState();}
    static double r(double x,double z){return Math.hypot(x+0.5-MID_X,z+0.5-MID_Z);}

    // ---------------------------------------------------------------- roads (planned with the island's, Roads.plan)

    /** The cyber city's roads: the avenues from the city's edges to the plaza, the streets round the ring. */
    static void plan(Roads r){
        line(r,AVENUE_HALF,Plan.ROAD_HALF-1,MID_Z,MID_X-PLAZA-1,MID_Z);
        line(r,AVENUE_HALF,MID_X+PLAZA+1,MID_Z,Plan.WIDE,MID_Z);
        line(r,AVENUE_HALF,MID_X,0,MID_X,MID_Z-PLAZA-1);
        line(r,AVENUE_HALF,MID_X,MID_Z+PLAZA+1,MID_X,Plan.END);
        for(int z:STREET_Z)line(r,STREET_HALF,Plan.ROAD_HALF-1,z,Plan.WIDE,z);
        for(int x:STREET_X)line(r,STREET_HALF,x,0,x,Plan.END);
    }
    /** A straight road kept where it runs over the city's own land, in stretches at least thirty blocks long. */
    private static void line(Roads r,int half,double x0,double z0,double x1,double z1){
        double len=Math.hypot(x1-x0,z1-z0),dx=(x1-x0)/len,dz=(z1-z0)/len,start=-1;
        for(double s=0;s<=len+1;s++){
            double x=x0+dx*s,z=z0+dz*s;
            boolean ok=s<=len&&roadOk(x,z,half,dz==0);
            if(ok&&start<0)start=s;
            if(!ok&&start>=0){
                double end=s-1;
                if(end-start>=30)r.add(Roads.straight(x0+dx*start,z0+dz*start,x0+dx*end,z0+dz*end),half,Plan.CYBER,Roads.WET);
                start=-1;
            }
        }
    }
    private static boolean roadOk(double x,double z,int half,boolean eastWest){
        if(!Canvas.inPlan((int)Math.floor(x),(int)Math.floor(z)))return false;
        boolean axis=eastWest&&Math.abs(x)<=Plan.ROAD_HALF+1;
        if(Plan.zone(x,z)!=Plan.CYBER&&!axis)return false;
        if(Plan.shore(x,z)>-(half+16))return false;
        return Math.hypot(x,z)>=Plan.riftEdge(x,z)+half+10;
    }

    // ---------------------------------------------------------------- the city

    void build(){
        ground();
        plaza();
        monoliths();
        ring.deck();ring.pillars();ring.market();ring.lanterns();ring.train(Math.PI*0.92);
        Town.log("cyber: ring with "+ring.pillars+" pillars, "+ring.shophouses+" shophouses, "+ring.stalls+" stalls, "+ring.lanterns+" lanterns, "+c.placed);
        arcades();
        outer();
        ring.moss();
        ring.hourglass(MID_X,DECK+8,MID_Z);
        ring.koi(MID_X-40,DECK+14,MID_Z+RING+4,MID_X+40,DECK+14,MID_Z+RING-4,26);
        furnish();
        promenade();
        t.places.put("cyber",new int[]{MID_X,PLAZA_Y+1,MID_Z+20});
        t.places.put("cyber_market",new int[]{MID_X-RING,Y+1,MID_Z+20});
        t.places.put("cyber_ring",new int[]{MID_X,DECK+1,MID_Z+RING-5});
        Town.log("cyber: "+monoliths+" monoliths, "+arcades+" arcades, "+banded+" banded towers, "+round+" round towers, "+towers.beacons+" beams, "+towers.signs+" signs, "
            +trees+" trees, "+lamps+" lamps, "+cars+" cars, "+crossings+" crossings, "+c.placed);
    }

    /** Whether a rectangle is the city's own open land: its zone, dry, off the shore and the rift, off the roads and every claim. */
    boolean land(int x0,int z0,int x1,int z1,int margin,int road){
        for(int x=x0-margin;x<=x1+margin;x++)for(int z=z0-margin;z<=z1+margin;z++){
            if(!Canvas.inPlan(x,z)||Plan.zone(x,z)!=Plan.CYBER||Plan.shore(x,z)>-8)return false;
            if(Math.hypot(x,z)<Plan.riftEdge(x,z)+12)return false;
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.pale[i]||c.owner[i]!=0)return false;
        }
        for(int x=x0-road;x<=x1+road;x++)for(int z=z0-road;z<=z1+road;z++)if(t.roads.on(x,z))return false;
        return true;
    }

    /** The city's ground: dark granite paving over all of it, a lighter promenade along the sea wall. */
    private void ground(){
        for(int x=0;x<Plan.X0+Plan.W;x++)for(int z=0;z<Plan.Z0+Plan.D;z++){
            if(Plan.zone(x,z)!=Plan.CYBER)continue;
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.pale[i]||t.roads.on(x,z))continue;
            int g=c.ground(x,z);
            c.set(x,g,z,Plan.shore(x,z)>-7?((x+z)%4==0?s(Blocks.SMOOTH_STONE):s(Blocks.LIGHT_GRAY_CONCRETE)):PAVING);
            c.clear(x,g+1,z,x,g+4,z);
        }
    }

    // ---------------------------------------------------------------- the plaza (the first photo)

    /**
     * The sunken plaza: five terraces stepping down from the street all round, each tread's inner half trimmed in
     * orange; wide stairs where the avenues come in; a floor of polished dark stone in squares lined with orange, light
     * set in it; the pavilion in its middle (a raised floor, four thick columns, a roof with an orange fascia and an
     * opening in its middle ringed with light, lanterns under it); pits rimmed in orange with a ring of light round
     * their dark floors, teal trees in some; benches.
     */
    private void plaza(){
        int x0=MID_X-PLAZA,x1=MID_X+PLAZA,z0=MID_Z-PLAZA,z1=MID_Z+PLAZA;
        c.claim(x0-2,z0-2,x1+2,z1+2,PLAZA_CODE);
        BlockState tile=s(Blocks.DEEPSLATE_TILES),orange=s(Blocks.ORANGE_TERRACOTTA),floor=s(Blocks.POLISHED_DEEPSLATE),line=s(Blocks.GRAY_CONCRETE);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            int dx=x-MID_X,dz=z-MID_Z,e=Math.min(Math.min(x-x0,x1-x),Math.min(z-z0,z1-z));
            int step=e/2,y=e>=10?PLAZA_Y:Y-step;
            for(int yy=PLAZA_Y-3;yy<y;yy++)c.set(x,yy,z,s(Blocks.STONE));
            c.clear(x,y+1,z,x,Y+4,z);
            c.ground(x,z,y);
            // the stairs where an avenue comes in: the inner half of each tread a stair up to the next
            boolean stairs=e<10&&(Math.abs(dz)<=AVENUE_HALF&&(x-x0==e||x1-x==e)||Math.abs(dx)<=AVENUE_HALF&&(z-z0==e||z1-z==e));
            if(e<10){
                if(stairs&&e%2==1){
                    Direction up=x-x0==e?Direction.WEST:x1-x==e?Direction.EAST:z-z0==e?Direction.NORTH:Direction.SOUTH;
                    c.set(x,y,z,Canvas.stairs(Blocks.DEEPSLATE_TILE_STAIRS,up,false));
                }else c.set(x,y,z,e%2==1?orange:tile);
                continue;
            }
            int d=Math.max(Math.abs(dx),Math.abs(dz));
            BlockState b=floor;
            if(dx==0||dz==0)b=line;
            if(d%8==0)b=orange;
            if(d==20&&(dx+dz)%4==0)b=s(Blocks.SEA_LANTERN);
            c.set(x,y,z,b);
        }
        pavilion();
        // pits rimmed with light, teal trees in every other one
        for(int k=0;k<8;k++){
            double a=Math.PI/8+k*Math.PI/4;
            int px=MID_X+(int)Math.round(Math.cos(a)*17),pz=MID_Z+(int)Math.round(Math.sin(a)*17);
            pit(px,pz,k%2==0);
        }
        // teal trees at the floor's corners, benches along the cross paths
        for(int sx=-1;sx<=1;sx+=2)for(int sz=-1;sz<=1;sz+=2)tealTree(MID_X+sx*21,PLAZA_Y+1,MID_Z+sz*21,7+rnd.nextInt(3));
        for(int k=-1;k<=1;k+=2)for(int dir=0;dir<4;dir++){
            int off=k*13;
            int bx=MID_X+(dir<2?off:(dir==2?-3:3)),bz=MID_Z+(dir<2?(dir==0?-3:3):off);
            Direction face=dir==0?Direction.SOUTH:dir==1?Direction.NORTH:dir==2?Direction.EAST:Direction.WEST;
            for(int j=-1;j<=1;j++){int x=bx+(dir<2?j:0),z=bz+(dir<2?0:j);if(c.air(x,PLAZA_Y+1,z))c.set(x,PLAZA_Y+1,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_STAIRS,face,false));}
        }
    }
    private void pavilion(){
        int y=PLAZA_Y,cx=MID_X,cz=MID_Z;
        BlockState stone=s(Blocks.POLISHED_BLACKSTONE_BRICKS),orange=s(Blocks.ORANGE_TERRACOTTA),col=s(Blocks.POLISHED_BLACKSTONE);
        for(int x=cx-10;x<=cx+10;x++)for(int z=cz-10;z<=cz+10;z++){
            int d=Math.max(Math.abs(x-cx),Math.abs(z-cz));
            if(d<=8)c.set(x,y+1,z,d==8?orange:stone);
            if(d==8&&(Math.abs(x-cx)<=2||Math.abs(z-cz)<=2))c.set(x,y+1,z,Canvas.stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
                Math.abs(x-cx)>Math.abs(z-cz)?(x>cx?Direction.WEST:Direction.EAST):(z>cz?Direction.NORTH:Direction.SOUTH),false));
            // the roof: two thick, an orange fascia, open in its middle with a ring of light under the opening
            if(d<=10&&d>2){c.set(x,y+10,z,d==10?orange:(d==3?s(Blocks.SEA_LANTERN):stone));c.set(x,y+11,z,d==10?orange:stone);}
            if(d==10&&((x+z)&1)==0)c.set(x,y+12,z,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false));
        }
        for(int sx=-1;sx<=1;sx+=2)for(int sz=-1;sz<=1;sz+=2)
            for(int x=cx+sx*6;x!=cx+sx*9;x+=sx)for(int z=cz+sz*6;z!=cz+sz*9;z+=sz)for(int yy=y+2;yy<=y+9;yy++)c.set(x,yy,z,yy==y+9?orange:col);
        for(int k=0;k<4;k++)c.hangingLantern(cx+(k%2==0?-4:4),y+9,cz+(k<2?-4:4),false);
        // the hologram's projector: a ring of cyan light round the opening's rim, rods of light standing on it
        for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++)if(Math.max(Math.abs(x-cx),Math.abs(z-cz))==2){c.set(x,y+11,z,CyberTowers.CYAN);c.set(x,y+12,z,Canvas.rod(Blocks.END_ROD,Direction.UP));}
    }
    /** A pit in the plaza's floor: an orange rim a step up, a ring of light round its dark floor two down, a teal tree in it or water. */
    private void pit(int cx,int cz,boolean tree){
        for(int x=cx-6;x<=cx+6;x++)for(int z=cz-6;z<=cz+6;z++){
            double d=octagon(x-cx,z-cz);
            if(d>5.5)continue;
            if(d>4.5){c.set(x,PLAZA_Y+1,z,s(Blocks.ORANGE_TERRACOTTA));continue;}
            if(d>3.6){c.set(x,PLAZA_Y,z,CyberTowers.WHITE);continue;}
            c.set(x,PLAZA_Y,z,s(Blocks.AIR));c.set(x,PLAZA_Y-1,z,s(Blocks.AIR));
            c.set(x,PLAZA_Y-2,z,tree?s(Blocks.MOSS_BLOCK):s(Blocks.BLACK_CONCRETE));
            if(!tree&&d<2.6)c.set(x,PLAZA_Y-1,z,s(Blocks.WATER));
        }
        if(tree)tealTree(cx,PLAZA_Y-1,cz,8+rnd.nextInt(3));
    }
    private static double octagon(double dx,double dz){double ax=Math.abs(dx),az=Math.abs(dz);return Math.max(Math.max(ax,az),(ax+az)/1.3);}

    /** A teal tree (the first photo): a dark trunk and three tiers of teal leaves narrowing up it, light glowing under the lowest. */
    void tealTree(int x,int y,int z,int h){
        for(int k=0;k<h;k++)c.set(x,y+k,z,Canvas.axis(Blocks.DARK_OAK_LOG,Direction.Axis.Y));
        int[][] tiers={{h-6,3},{h-3,2},{h,1}};
        for(int[] tr:tiers){
            int ty=y+tr[0],rr=tr[1];
            for(int dx=-rr-1;dx<=rr+1;dx++)for(int dz=-rr-1;dz<=rr+1;dz++){
                double d=Math.hypot(dx,dz);
                if(d>rr+0.6)continue;
                if(c.air(x+dx,ty,z+dz))c.set(x+dx,ty,z+dz,s(Blocks.WARPED_WART_BLOCK));
                if(d<rr-0.4&&c.air(x+dx,ty+1,z+dz))c.set(x+dx,ty+1,z+dz,s(Blocks.WARPED_WART_BLOCK));
            }
        }
        c.set(x,y+h+1,z,s(Blocks.WARPED_WART_BLOCK));
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)==3&&c.air(x+dx,y+h-7,z+dz))c.set(x+dx,y+h-7,z+dz,TB.of("world_tree_leaves"));
        trees++;
    }

    // ---------------------------------------------------------------- the monoliths round the plaza (the first photo)

    private void monoliths(){
        for(int k=0;k<8;k++){
            double a=Math.PI/8+k*Math.PI/4;
            int cx=MID_X+(int)Math.round(Math.cos(a)*72),cz=MID_Z+(int)Math.round(Math.sin(a)*72);
            int x0=cx-13,z0=cz-13,x1=cx+13,z1=cz+13;
            if(!land(x0,z0,x1,z1,0,2))continue;
            towers.monolith(x0,z0,x1,z1,Y,104+rnd.nextInt(13));
            c.claim(x0-2,z0-2,x1+2,z1+2,CODE);
            monoliths++;
        }
    }

    // ---------------------------------------------------------------- arcades along the avenues (the fourth photo)

    /** Arcade blocks along both sides of each avenue and street beyond the ring, their fronts a step off the road's walk. */
    private void arcades(){
        for(int i=0;i<t.roads.lines.size();i++){
            if(t.roads.zones.get(i)!=Plan.CYBER)continue;
            double[][] line=t.roads.lines.get(i);int half=t.roads.halfs.get(i);
            for(int side=-1;side<=1;side+=2){
                int k=0;
                while(k<line.length){
                    double[] p=line[k];
                    double nx=-p[3]*side,nz=p[2]*side;
                    int w=16+rnd.nextInt(10),d=16+rnd.nextInt(6);
                    double fx=p[0]+nx*(half+2),fz=p[1]+nz*(half+2);
                    Direction front=Math.abs(nx)>=Math.abs(nz)?(nx>0?Direction.WEST:Direction.EAST):(nz>0?Direction.NORTH:Direction.SOUTH);
                    int cx=(int)Math.round(fx),cz=(int)Math.round(fz),x0,z0,x1,z1;
                    switch(front){
                        case NORTH->{x0=cx;x1=cx+w-1;z0=cz;z1=cz+d-1;}
                        case SOUTH->{x0=cx;x1=cx+w-1;z1=cz;z0=cz-d+1;}
                        case WEST->{z0=cz;z1=cz+w-1;x0=cx;x1=cx+d-1;}
                        default->{z0=cz;z1=cz+w-1;x1=cx;x0=cx-d+1;}
                    }
                    if(r((x0+x1)/2.0,(z0+z1)/2.0)>RING+RING_HALF+18&&land(x0,z0,x1,z1,0,1)&&land(x0+1,z0+1,x1-1,z1-1,1,1)){
                        towers.arcade(new Frame(c,x0,z0,x1,z1,front,Y),half==AVENUE_HALF?28+rnd.nextInt(30):20+rnd.nextInt(18));
                        c.claim(x0-1,z0-1,x1+1,z1+1,CODE);
                        arcades++;
                        k+=(w+2+rnd.nextInt(3))*2;
                    }else k+=6;
                }
            }
        }
    }

    // ---------------------------------------------------------------- the towers beyond the ring (the fifth photo)

    /**
     * The rest of the city's land beyond the ring filled with towers, the nearest to the ring first: banded towers of
     * various sizes, one in four round, a gap of six or more between them; taller toward the ring.
     */
    private void outer(){
        List<int[]> spots=new ArrayList<>();
        for(int x=0;x<Plan.X0+Plan.W;x+=4)for(int z=0;z<Plan.Z0+Plan.D;z+=4)
            if(Plan.zone(x,z)==Plan.CYBER&&r(x,z)>RING+RING_HALF+10)spots.add(new int[]{x,z,rnd.nextInt(24)});
        spots.sort(Comparator.comparingDouble(p->r(p[0],p[1])+p[2]));
        for(int[] p:spots){
            double dist=r(p[0],p[1])-RING;
            double tall=Math.exp(-Math.pow(dist/150,2));
            if(rnd.nextDouble()<0.15){
                double rr=7+rnd.nextInt(4);int R=(int)Math.ceil(rr);
                if(!land(p[0]-R,p[1]-R,p[0]+R,p[1]+R,2,2))continue;
                towers.roundTower(p[0]+0.5,p[1]+0.5,rr,Y,(int)Math.round(46+60*tall*(0.6+0.4*rnd.nextDouble())+rnd.nextInt(10)));
                c.claim(p[0]-R-2,p[1]-R-2,p[0]+R+2,p[1]+R+2,CODE);
                round++;
                continue;
            }
            // the biggest plot that fits here, down to a small block
            for(int size:new int[]{28,24,20,16,12}){
                int w=size+rnd.nextInt(5)-2,d=size+rnd.nextInt(5)-2;
                int x0=p[0]-w/2,z0=p[1]-d/2,x1=x0+w-1,z1=z0+d-1;
                if(!land(x0,z0,x1,z1,2,2))continue;
                int h=(int)Math.round(34+72*tall*(0.55+0.45*rnd.nextDouble())+rnd.nextInt(10));
                if(size<=12)h=Math.min(h,24+rnd.nextInt(16));
                towers.bandTower(x0,z0,x1,z1,Y,h);
                c.claim(x0-2,z0-2,x1+2,z1+2,CODE);
                banded++;
                break;
            }
        }
    }

    // ---------------------------------------------------------------- the streets' furniture

    /**
     * Lamps along both walks of every road with a neon band on their posts, cars along the kerbs, crosswalks striped
     * yellow where roads cross (the fifth photo), teal trees in the avenues' walks between the lamps.
     */
    private void furnish(){
        for(int i=0;i<t.roads.lines.size();i++){
            if(t.roads.zones.get(i)!=Plan.CYBER)continue;
            double[][] line=t.roads.lines.get(i);int half=t.roads.halfs.get(i);
            for(int side=-1;side<=1;side+=2){
                for(int k=0;k<line.length;k+=24){
                    double[] p=line[k];double nx=-p[3]*side,nz=p[2]*side;
                    int x=(int)Math.floor(p[0]+nx*(half-1.2)),z=(int)Math.floor(p[1]+nz*(half-1.2));
                    if(!onWalk(x,z))continue;
                    Direction road=Direction.getFacing(-nx,0,-nz);
                    if((k/24)%2==0){if(lamp(x,z,road))lamps++;}
                    else if(half==AVENUE_HALF&&c.air(x,Y+1,z)&&r(x,z)>RING+RING_HALF+2){c.set(x,Y,z,s(Blocks.MOSS_BLOCK));tealTree(x,Y+1,z,7+rnd.nextInt(2));}
                    if(rnd.nextDouble()<0.35){
                        int cx=(int)Math.floor(p[0]+nx*(half-4.5)),cz=(int)Math.floor(p[1]+nz*(half-4.5));
                        if(car(cx,cz,Math.abs(p[2])>Math.abs(p[3])?Direction.Axis.X:Direction.Axis.Z))cars++;
                    }
                }
            }
        }
        crossings();
    }
    private boolean onWalk(int x,int z){return Plan.zone(x,z)==Plan.CYBER&&t.roads.on(x,z)&&t.roads.map[Canvas.idx(x,z)]==Roads.WALK;}
    private boolean lamp(int x,int z,Direction road){
        int g=c.ground(x,z);
        for(int yy=g+1;yy<=g+9;yy++)if(!c.air(x,yy,z))return false;
        for(int yy=g+1;yy<=g+8;yy++)c.set(x,yy,z,s(Blocks.POLISHED_BLACKSTONE_WALL));
        c.set(x,g+9,z,CyberTowers.PANEL);
        for(int k=1;k<=2;k++){int ax=x+road.getOffsetX()*k,az=z+road.getOffsetZ()*k;if(c.air(ax,g+9,az))c.set(ax,g+9,az,Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,true));}
        int lx=x+road.getOffsetX()*2,lz=z+road.getOffsetZ()*2;
        if(c.air(lx,g+8,lz))c.set(lx,g+8,lz,Canvas.rod(Blocks.END_ROD,Direction.DOWN));
        c.set(x,g+4,z,CyberTowers.NEON[rnd.nextInt(CyberTowers.NEON.length)]);
        return true;
    }
    /** A car at the kerb along the given axis: a body of painted concrete, dark glass over it, lights at its ends. */
    private boolean car(int x,int z,Direction.Axis along){
        int lx=along==Direction.Axis.X?2:1,lz=along==Direction.Axis.X?1:2;
        int g=c.ground(x,z);
        for(int dx=-lx;dx<=lx;dx++)for(int dz=-lz;dz<=lz;dz++){
            if(!t.roads.on(x+dx,z+dz)||t.roads.map[Canvas.idx(x+dx,z+dz)]==Roads.WALK||!c.air(x+dx,g+1,z+dz)||c.ground(x+dx,z+dz)!=g)return false;
        }
        BlockState body=Pal.pick(rnd,new BlockState[]{s(Blocks.BLACK_CONCRETE),s(Blocks.WHITE_CONCRETE),s(Blocks.MAGENTA_CONCRETE),s(Blocks.GRAY_CONCRETE),s(Blocks.CYAN_CONCRETE),s(Blocks.YELLOW_CONCRETE)});
        for(int dx=-lx;dx<=lx;dx++)for(int dz=-lz;dz<=lz;dz++){
            boolean end=along==Direction.Axis.X?Math.abs(dx)==lx:Math.abs(dz)==lz;
            boolean front=along==Direction.Axis.X?dx==lx:dz==lz;
            c.set(x+dx,g+1,z+dz,end?(front?s(Blocks.SEA_LANTERN):s(Blocks.REDSTONE_BLOCK)):body);
            c.set(x+dx,g+2,z+dz,end?Canvas.slab(Blocks.POLISHED_BLACKSTONE_SLAB,false):s(Blocks.BLACK_STAINED_GLASS));
        }
        return true;
    }
    /** Yellow zebra stripes across each road just outside where it crosses another (the fifth photo's crossings). */
    private void crossings(){
        int[] xs={MID_X,STREET_X[0],STREET_X[1]},zs={MID_Z,STREET_Z[0],STREET_Z[1]};
        for(int x:xs)for(int z:zs){
            if(!Canvas.inPlan(x,z)||!t.roads.on(x,z)||t.roads.map[Canvas.idx(x,z)]!=Roads.JUNCTION)continue;
            int hx=x==MID_X?AVENUE_HALF:STREET_HALF,hz=z==MID_Z?AVENUE_HALF:STREET_HALF;
            // the four arms: across the east-west road west and east of the junction, across the north-south road north and south of it
            for(int side=-1;side<=1;side+=2){
                for(int k=1;k<=4;k++){
                    int ax=x+side*(hx+k);
                    for(int dz=-hz+3;dz<=hz-3;dz++)if((dz&1)==0)stripe(ax,z+dz);
                    int az=z+side*(hz+k);
                    for(int dx=-hx+3;dx<=hx-3;dx++)if((dx&1)==0)stripe(x+dx,az);
                }
            }
            crossings++;
        }
    }
    private void stripe(int x,int z){
        if(!Canvas.inPlan(x,z)||!t.roads.on(x,z))return;
        byte m=t.roads.map[Canvas.idx(x,z)];
        if(m==Roads.WALK||m==Roads.JUNCTION)return;
        int g=c.ground(x,z);
        if(c.get(x,g,z).isOf(WET.getBlock()))c.set(x,g,z,s(Blocks.YELLOW_CONCRETE));
    }

    /** The promenade along the sea wall: a rail at its edge with posts, lights on some posts. */
    private void promenade(){
        int n=0;
        for(int x=0;x<Plan.X0+Plan.W;x++)for(int z=0;z<Plan.Z0+Plan.D;z++){
            if(Plan.zone(x,z)!=Plan.CYBER)continue;
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND||t.roads.on(x,z)||c.owner[i]!=0)continue;
            boolean edge=false;
            for(var d:Direction.Type.HORIZONTAL){int nx=x+d.getOffsetX(),nz=z+d.getOffsetZ();if(!Canvas.inPlan(nx,nz)||t.terrain.kind[Canvas.idx(nx,nz)]==Terrain.SEA)edge=true;}
            if(!edge)continue;
            int g=c.ground(x,z);
            if(!c.air(x,g+1,z))continue;
            boolean post=Math.floorMod(x*7+z*13,9)==0;
            c.set(x,g+1,z,post?s(Blocks.POLISHED_BLACKSTONE_WALL):s(Blocks.IRON_BARS));
            if(post&&Math.floorMod(x*3+z*5,4)==0){c.set(x,g+2,z,s(Blocks.POLISHED_BLACKSTONE_WALL));c.set(x,g+3,z,CyberTowers.CYAN);n++;}
        }
        Town.log("cyber: promenade rail with "+n+" lights");
    }
}
