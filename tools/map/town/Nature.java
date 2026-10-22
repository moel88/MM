package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import java.util.Random;

/**
 * Trees and plants: wind-bent black pines with cloud-shaped pads of needles (the coast, gardens, the pine grove),
 * cherry trees (round the park's pond, at the square, the school's gate and the shrine, not everywhere), round
 * evergreen broadleaf trees (the hill, the town's shade trees), tall cedars round the shrine, bamboo, and the grass and
 * wildflowers under them. Leaves only grow into air.
 */
final class Nature {
    private final Townscape t;private final Canvas c;private final Random rnd;
    Nature(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    private void leaf(int x,int y,int z,Block b){if(c.air(x,y,z))c.set(x,y,z,Canvas.leaves(b));}
    private void log(int x,int y,int z,Block b,Direction.Axis a){
        if(free(x,y,z))c.set(x,y,z,Canvas.axis(b,a));
    }
    /** Whether wood may grow through here: air, leaves (the game's or Botand's), plants. */
    private boolean free(int x,int y,int z){
        BlockState here=c.get(x,y,z);
        return here.isAir()||here.getBlock() instanceof LeavesBlock||here.getBlock() instanceof dev.sangwoo.celestialfall.town.TownBlocks.Foliage||here.getBlock() instanceof PlantBlock;
    }
    /** A cherry's limb (Botand's round branch, 2.48.0) where wood may grow. */
    private void limb(int x,int y,int z,Direction.Axis a){if(free(x,y,z))c.set(x,y,z,TB.branch(a));}
    /** An ellipsoid of Botand's leaves on cards (2.48.0: hedges, crowns, blossom), ragged at its edge, only into air. */
    void blob(double cx,double cy,double cz,double rx,double ry,double rz,BlockState leaves){
        for(int x=(int)Math.floor(cx-rx);x<=Math.ceil(cx+rx);x++)for(int y=(int)Math.floor(cy-ry);y<=Math.ceil(cy+ry);y++)for(int z=(int)Math.floor(cz-rz);z<=Math.ceil(cz+rz);z++){
            double dx=(x-cx)/rx,dy=(y-cy)/ry,dz=(z-cz)/rz,d=dx*dx+dy*dy+dz*dz;
            if(d>1)continue;
            if(d>0.62&&Pal.hash(x,y,z)<0.35)continue;
            if(c.air(x,y,z))c.set(x,y,z,leaves);
        }
    }
    /** An ellipsoid of leaves, ragged at its edge. */
    void blob(double cx,double cy,double cz,double rx,double ry,double rz,Block leaves,Block mix){
        for(int x=(int)Math.floor(cx-rx);x<=Math.ceil(cx+rx);x++)for(int y=(int)Math.floor(cy-ry);y<=Math.ceil(cy+ry);y++)for(int z=(int)Math.floor(cz-rz);z<=Math.ceil(cz+rz);z++){
            double dx=(x-cx)/rx,dy=(y-cy)/ry,dz=(z-cz)/rz,d=dx*dx+dy*dy+dz*dz;
            if(d>1)continue;
            if(d>0.62&&Pal.hash(x,y,z)<0.35)continue;
            leaf(x,y,z,mix!=null&&Pal.hash(z,x,y)<0.25?mix:leaves);
        }
    }

    /** クロマツ: a leaning, bending trunk with flat pads of dark needles on short branches (in the town, Botand's needles on cards). */
    void pine(int x,int y,int z,int h){pine(x,y,z,h,true);}
    private boolean townPine;
    void pine(int x,int y,int z,int h,boolean town){townPine=town;pine0(x,y,z,h);townPine=false;}
    private void pine0(int x,int y,int z,int h){
        double px=x,pz=z,lx=(rnd.nextDouble()-0.5)*0.6,lz=(rnd.nextDouble()-0.5)*0.6;
        int topY=y+h;
        for(int k=0;k<h;k++){
            int bx=(int)Math.round(px),bz=(int)Math.round(pz);
            log(bx,y+k,bz,Blocks.DARK_OAK_LOG,Direction.Axis.Y);
            if(k>h/3){px+=lx;pz+=lz;}
            int nx=(int)Math.round(px),nz=(int)Math.round(pz);
            if(nx!=bx||nz!=bz)log(nx,y+k,nz,Blocks.DARK_OAK_LOG,Direction.Axis.Y);
            // pads of needles on short branches up the upper half
            if(k>=h/2&&(k-h/2)%2==0){
                double a=rnd.nextDouble()*Math.PI*2;int len=1+rnd.nextInt(2);
                int ex=nx+(int)Math.round(Math.cos(a)*len),ez=nz+(int)Math.round(Math.sin(a)*len);
                log(ex,y+k,ez,Blocks.DARK_OAK_LOG,Math.abs(Math.cos(a))>0.7?Direction.Axis.X:Direction.Axis.Z);
                pad(ex,y+k+1,ez,1.6+rnd.nextDouble()*0.9);
            }
        }
        pad((int)Math.round(px),topY,(int)Math.round(pz),2.2+rnd.nextDouble()*0.8);
    }
    private void pad(int cx,int y,int cz,double r){
        for(int x=(int)(cx-r-1);x<=cx+r+1;x++)for(int z=(int)(cz-r-1);z<=cz+r+1;z++){
            double d=Math.hypot(x-cx,z-cz);
            if(d<=r){if(townPine){if(c.air(x,y,z))c.set(x,y,z,TB.PINE);}else leaf(x,y,z,Blocks.SPRUCE_LEAVES);}
            if(d<=r-1.1){if(townPine){if(c.air(x,y+1,z))c.set(x,y+1,z,TB.PINE);}else leaf(x,y+1,z,Blocks.SPRUCE_LEAVES);}
        }
    }

    /** サクラ: a short trunk dividing into spreading limbs under a wide, light crown of blossom; petals beneath. */
    void cherry(int x,int y,int z,int h){cherry(x,y,z,h,1);}
    /**
     * A cherry tree {@code size} times the usual: the photos' big old trees spread their limbs wider and carry a
     * deeper crown. Fallen petals lie on the grass under it (the game's pink petals) and on paving, asphalt and walls
     * round it (Botand's fallen petals, which lie on anything solid).
     */
    void cherry(int x,int y,int z,int h,double size){
        // a round trunk (Botand's thick branch, 2.48.0) dividing into thin round limbs, spreading and rising
        for(int k=0;k<h-2;k++)if(free(x,y+k,z))c.set(x,y+k,z,TB.trunk(Direction.Axis.Y));
        int top=y+h-2,reach=(int)Math.round(3*size);
        for(int i=0;i<3+(size>1.2?1:0);i++){
            double a=i*2.1+rnd.nextDouble();
            Direction.Axis along=Math.abs(Math.cos(a))>0.7?Direction.Axis.X:Direction.Axis.Z;
            for(int k=1;k<=reach;k++){
                int bx=x+(int)Math.round(Math.cos(a)*k),bz=z+(int)Math.round(Math.sin(a)*k),by=top+k/2;
                limb(bx,by,bz,along);if(k%2==0)limb(bx,by-1,bz,Direction.Axis.Y);
            }
        }
        // the crown: Botand's blossom on cards, wide and light, low enough to hold the limbs (2.48.0)
        double r=(3.6+rnd.nextDouble()*1.2)*size;
        blob(x+0.5,top+1.6*Math.sqrt(size),z+0.5,r,2.3*size,r,TB.BLOSSOM);
        blob(x+0.5+(rnd.nextDouble()-0.5)*2*size,top+2.7*size,z+0.5+(rnd.nextDouble()-0.5)*2*size,r*0.7,1.8*size,r*0.7,TB.BLOSSOM);
        if(size>1.2)for(int i=0;i<3;i++){
            double a=rnd.nextDouble()*Math.PI*2;
            blob(x+0.5+Math.cos(a)*r*0.6,top+0.9*size+rnd.nextDouble()*size,z+0.5+Math.sin(a)*r*0.6,r*0.5,1.5*size,r*0.5,TB.BLOSSOM);
        }
        // fallen petals on the ground under the crown and a little beyond it
        int n=(int)Math.round(18*size*size*1.6);
        for(int i=0;i<n;i++){
            double a=rnd.nextDouble()*Math.PI*2,d=Math.sqrt(rnd.nextDouble())*r*1.25;
            int px=x+(int)Math.round(Math.cos(a)*d),pz=z+(int)Math.round(Math.sin(a)*d);
            petals(px,c.ground(px,pz),pz);
        }
    }
    /** A few fallen petals on top of the block at y, if anything solid is there to hold them and nothing stands on it. */
    void petals(int x,int y,int z){
        if(!c.air(x,y+1,z))return;
        BlockState g=c.get(x,y,z);
        if(g.isOf(Blocks.GRASS_BLOCK))
            c.set(x,y+1,z,Blocks.PINK_PETALS.getDefaultState().with(Properties.FLOWER_AMOUNT,1+rnd.nextInt(4)).with(Properties.HORIZONTAL_FACING,Direction.fromHorizontal(rnd.nextInt(4))));
        else if(g.isOpaqueFullCube(null,null)||g.getBlock() instanceof SlabBlock&&g.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.BOTTOM)
            c.set(x,y+1,z,TB.of("fallen_petals"));
    }

    /** A broadleaf tree in the town (2.48.0): a straight trunk under a crown of Botand's leaves on cards. */
    void townTree(int x,int y,int z,int h){
        Block wood=rnd.nextInt(3)==0?Blocks.DARK_OAK_LOG:Blocks.OAK_LOG;
        for(int k=0;k<h;k++)log(x,y+k,z,wood,Direction.Axis.Y);
        double r=2.2+rnd.nextDouble()*1.2;
        blob(x+0.5,y+h-0.5,z+0.5,r,r*0.8,r,TB.CANOPY);
    }
    /** A round evergreen broadleaf tree (camphor, oak): a straight trunk under a dense dark crown. */
    void broadleaf(int x,int y,int z,int h){
        Block wood=rnd.nextInt(3)==0?Blocks.DARK_OAK_LOG:Blocks.OAK_LOG;
        for(int k=0;k<h;k++)log(x,y+k,z,wood,Direction.Axis.Y);
        double r=2.4+rnd.nextDouble()*1.3;
        Block main=rnd.nextInt(3)==0?Blocks.DARK_OAK_LEAVES:Blocks.OAK_LEAVES;
        blob(x+0.5,y+h-0.5,z+0.5,r,r*0.8,r,main,rnd.nextBoolean()?Blocks.JUNGLE_LEAVES:Blocks.DARK_OAK_LEAVES);
    }

    /** スギ: a tall straight cedar, its narrow cone of dark foliage starting a third of the way up. */
    void cedar(int x,int y,int z,int h){
        for(int k=0;k<h;k++)log(x,y+k,z,Blocks.SPRUCE_LOG,Direction.Axis.Y);
        int start=h/3;
        for(int k=start;k<=h+1;k++){
            double r=(h+1-k)/(double)(h+1-start)*2.6+0.3;
            if((k&1)==1)r*=0.75;
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(dx*dx+dz*dz<=r*r)leaf(x+dx,y+k,z+dz,Blocks.SPRUCE_LEAVES);
        }
    }

    /** A clump of bamboo, leafy at the top. */
    void bamboo(int x,int y,int z,int h){
        for(int k=0;k<h;k++){
            if(!c.air(x,y+k,z))return;
            var leaves=k>=h-2?net.minecraft.block.enums.BambooLeaves.LARGE:k>=h-4?net.minecraft.block.enums.BambooLeaves.SMALL:net.minecraft.block.enums.BambooLeaves.NONE;
            c.set(x,y+k,z,Blocks.BAMBOO.getDefaultState().with(BambooBlock.AGE,1).with(BambooBlock.LEAVES,leaves));
        }
    }

    /**
     * Woods on the natural land outside the town (map v2.0): cedars and broadleaf trees on the hill behind the town,
     * thinning to meadows here and there, black pines along the shores and on the island; grass, ferns and
     * wildflowers under them.
     */
    void woods(){
        int trees=0;
        for(int x=Plan.X0+2;x<Plan.X0+Plan.W-2;x+=3)for(int z=Plan.Z0+2;z<Plan.Z0+Plan.D-2;z+=3){
            int px=x+rnd.nextInt(3)-1,pz=z+rnd.nextInt(3)-1;
            int i=Canvas.idx(px,pz);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.plat[i]!=Terrain.NONE)continue;
            int g=c.ground(px,pz);
            if(g<=Plan.SEA+1||!c.get(px,g,pz).isOf(Blocks.GRASS_BLOCK)||!c.air(px,g+1,pz))continue;
            if(t.roads.any(px,pz)||nearBuilt(px,pz,g))continue;
            // the shrine's stairway keeps a clear strip either side, so the view down it stays open
            if(pz<Plan.TOWN_Z0&&pz>=Plan.SHRINE_Z0&&Math.abs(px)<=Plan.STAIR_X1+5)continue;
            boolean hill=pz<Plan.TOWN_Z0;
            // the hill: a meadow along its foot, then woods in groves with clearings between them
            int up=Plan.TOWN_Z0-pz;
            double p=hill?(up<7?0.03:Pal.smooth(px,pz,23,97)<0.35?0.06:0.5):0.30;
            if(rnd.nextDouble()>p)continue;
            int r=rnd.nextInt(100);
            if(!hill)pine(px,g+1,pz,5+rnd.nextInt(4),false);
            else if(r<40)cedar(px,g+1,pz,11+rnd.nextInt(7));else if(r<90)broadleaf(px,g+1,pz,5+rnd.nextInt(4));else pine(px,g+1,pz,6+rnd.nextInt(3),false);
            trees++;
        }
        Town.log("woods: "+trees+" trees");
        // grass, ferns and wildflowers over all the open natural ground, thickest in front of the little rises of a slope
        int plants=0;
        for(int x=Plan.X0+1;x<Plan.X0+Plan.W-1;x++)for(int z=Plan.Z0+1;z<Plan.Z0+Plan.D-1;z++){
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.plat[i]!=Terrain.NONE||t.roads.any(x,z))continue;
            int g=c.ground(x,z);
            if(g<=Plan.SEA+1||!c.get(x,g,z).isOf(Blocks.GRASS_BLOCK)||!c.air(x,g+1,z))continue;
            boolean rise=c.ground(x+1,z)>g||c.ground(x-1,z)>g||c.ground(x,z+1)>g||c.ground(x,z-1)>g;
            boolean shore=z>=Plan.TOWN_Z0;
            if(rnd.nextDouble()<(rise?0.6:0.2)*(shore?0.7:1)&&undergrowth(x,g+1,z,shore))plants++;
        }
        Town.log("undergrowth: "+plants+" plants");
    }
    private boolean nearBuilt(int x,int z,int g){
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            if(!Canvas.inPlan(x+dx,z+dz))continue;
            int i=Canvas.idx(x+dx,z+dz);
            if(t.terrain.plat[i]!=Terrain.NONE||t.roads.any(x+dx,z+dz))return true;
        }
        return false;
    }
    boolean undergrowth(int x,int y,int z,boolean shore){
        if(!c.air(x,y,z))return false;
        int r=rnd.nextInt(100);
        if(r<58)c.set(x,y,z,Blocks.GRASS.getDefaultState());
        else if(r<72&&c.air(x,y+1,z))c.tallPlant(x,y,z,shore||r<66?Blocks.TALL_GRASS:Blocks.LARGE_FERN);
        else if(r<86)c.set(x,y,z,shore?Blocks.GRASS.getDefaultState():Blocks.FERN.getDefaultState());
        else if(r<92)c.set(x,y,z,Pal.pick(rnd,Blocks.OXEYE_DAISY,Blocks.CORNFLOWER,Blocks.AZURE_BLUET,Blocks.DANDELION,Blocks.LILY_OF_THE_VALLEY).getDefaultState());
        else if(!shore)c.set(x,y,z,TB.HEDGE);
        else c.set(x,y,z,Blocks.GRASS.getDefaultState());
        return true;
    }
}
