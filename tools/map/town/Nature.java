package dev.sangwoo.botandtown;

import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import java.util.Random;

/**
 * Trees and plants: wind-bent black pines with cloud-shaped pads of needles (the coast, gardens), cherry trees
 * (along the river and at the shrine and station, not everywhere), round evergreen broadleaf trees (the hills),
 * tall cedars round the shrine, bamboo, and the grass and wildflowers under them. Leaves only grow into air.
 */
final class Nature {
    private final Townscape t;private final Canvas c;private final Random rnd;
    Nature(Townscape t){this.t=t;c=t.c;rnd=new Random(t.rnd.nextLong());}

    private void leaf(int x,int y,int z,Block b){if(c.air(x,y,z))c.set(x,y,z,Canvas.leaves(b));}
    private void log(int x,int y,int z,Block b,Direction.Axis a){
        BlockState here=c.get(x,y,z);
        if(here.isAir()||here.getBlock() instanceof LeavesBlock||here.getBlock() instanceof PlantBlock)c.set(x,y,z,Canvas.axis(b,a));
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

    /** クロマツ: a leaning, bending trunk with flat pads of dark needles on short branches. */
    void pine(int x,int y,int z,int h){
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
            if(d<=r)leaf(x,y,z,Blocks.SPRUCE_LEAVES);
            if(d<=r-1.1)leaf(x,y+1,z,Blocks.SPRUCE_LEAVES);
        }
    }

    /** サクラ: a short trunk dividing into spreading limbs under a wide, light crown of blossom; petals beneath. */
    void cherry(int x,int y,int z,int h){
        for(int k=0;k<h-2;k++)log(x,y+k,z,Blocks.CHERRY_LOG,Direction.Axis.Y);
        int top=y+h-2;
        for(int i=0;i<3;i++){
            double a=i*2.1+rnd.nextDouble();
            for(int k=1;k<=3;k++){
                int bx=x+(int)Math.round(Math.cos(a)*k),bz=z+(int)Math.round(Math.sin(a)*k);
                log(bx,top+k/2,bz,Blocks.CHERRY_LOG,Math.abs(Math.cos(a))>0.7?Direction.Axis.X:Direction.Axis.Z);
            }
        }
        double r=3.6+rnd.nextDouble()*1.2;
        blob(x+0.5,top+2.5,z+0.5,r,2.3,r,Blocks.CHERRY_LEAVES,null);
        blob(x+0.5+(rnd.nextDouble()-0.5)*2,top+3.5,z+0.5+(rnd.nextDouble()-0.5)*2,r*0.7,1.8,r*0.7,Blocks.CHERRY_LEAVES,null);
        // fallen petals on the ground under the crown
        for(int i=0;i<18;i++){
            int px=x+(int)Math.round((rnd.nextDouble()*2-1)*r),pz=z+(int)Math.round((rnd.nextDouble()*2-1)*r);
            int g=c.ground(px,pz);
            if(c.air(px,g+1,pz)&&c.get(px,g,pz).isOf(Blocks.GRASS_BLOCK))
                c.set(px,g+1,pz,Blocks.PINK_PETALS.getDefaultState().with(Properties.FLOWER_AMOUNT,1+rnd.nextInt(4)).with(Properties.HORIZONTAL_FACING,Direction.fromHorizontal(rnd.nextInt(4))));
        }
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
     * Woods on the natural land outside the town: cedars and broadleaf trees on the ridge, broadleaf trees and
     * pines on the headlands, black pines along the shores and on the island, bamboo in hollows; grass, ferns
     * and wildflowers under them.
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
            boolean shore=pz>Terrain.shoreZ(px)-14||pz>150;
            boolean ridge=pz<-200;
            boolean valley=Math.abs(px-Plan.riverX(pz))<46&&pz>Plan.RIVER_HEAD_Z;
            double p=valley?0.07:shore?0.32:ridge?0.62:0.42;
            undergrowth(px,g+1,pz,shore);
            if(rnd.nextDouble()>p)continue;
            int r=rnd.nextInt(100);
            if(shore)pine(px,g+1,pz,5+rnd.nextInt(4));
            else if(ridge)if(r<45)cedar(px,g+1,pz,11+rnd.nextInt(7));else if(r<92)broadleaf(px,g+1,pz,5+rnd.nextInt(4));else pine(px,g+1,pz,6+rnd.nextInt(3));
            else if(r<70)broadleaf(px,g+1,pz,4+rnd.nextInt(4));else if(r<85)pine(px,g+1,pz,5+rnd.nextInt(3));else if(r<93)cedar(px,g+1,pz,9+rnd.nextInt(5));
            else for(int k=0;k<9;k++)bamboo(px+rnd.nextInt(5)-2,c.ground(px,pz)+1,pz+rnd.nextInt(5)-2,7+rnd.nextInt(6));
            trees++;
        }
        Town.log("woods: "+trees+" trees");
    }
    private boolean nearBuilt(int x,int z,int g){
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            if(!Canvas.inPlan(x+dx,z+dz))continue;
            int i=Canvas.idx(x+dx,z+dz);
            if(t.terrain.plat[i]!=Terrain.NONE||t.roads.any(x+dx,z+dz))return true;
        }
        return false;
    }
    void undergrowth(int x,int y,int z,boolean shore){
        int r=rnd.nextInt(100);
        if(!c.air(x,y,z))return;
        if(r<30)c.set(x,y,z,Blocks.GRASS.getDefaultState());
        else if(r<36)c.set(x,y,z,Blocks.FERN.getDefaultState());
        else if(r<39&&c.air(x,y+1,z))c.tallPlant(x,y,z,shore?Blocks.TALL_GRASS:Blocks.LARGE_FERN);
        else if(r<41)c.set(x,y,z,Pal.pick(rnd,Blocks.OXEYE_DAISY,Blocks.CORNFLOWER,Blocks.AZURE_BLUET,Blocks.DANDELION,Blocks.LILY_OF_THE_VALLEY).getDefaultState());
        else if(r<43&&!shore)c.set(x,y,z,Canvas.leaves(Blocks.AZALEA_LEAVES));
    }
}
