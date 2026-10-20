package dev.sangwoo.botandtown;

import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Puts the town together, in the order the pieces depend on each other, and writes the map's definition. */
final class Townscape {
    final Canvas c;final Terrain terrain;final Random rnd;
    final Roads roads;final Hill hill;
    /** Places the mod uses: merchants (seller, buyer) and the field supply spots. */
    final List<int[]> merchants=new ArrayList<>();final List<int[]> loot=new ArrayList<>();
    final Map<String,int[]> places=new LinkedHashMap<>();
    Townscape(Canvas c,Terrain terrain,Random rnd){
        this.c=c;this.terrain=terrain;this.rnd=rnd;
        roads=new Roads(this);hill=new Hill(this);
    }

    void build(){
        roads.plan();hill.plan(roads);
        roads.lay();Town.log("streets laid: "+c.placed);
        hill.build();Town.log("stairway and ramps: "+c.placed);
    }

    /** Where players appear: the station square, looking down the main street to the sea. */
    int[] spawn(){return new int[]{0,Plan.GROUND+1,Plan.S1_Z1+8,0};}

    void definition(Path pack)throws Exception{
        preview(pack.getParent().getParent().resolve("town_preview.png"));
    }

    /** A top-down picture of the finished town in the colours of an in-game map (a check for the builder). */
    void preview(Path out)throws Exception{
        var img=new BufferedImage(Plan.W,Plan.D,BufferedImage.TYPE_INT_RGB);
        var pos=new BlockPos.Mutable();
        int[] last=new int[Plan.W];
        for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++)for(int x=Plan.X0;x<Plan.X0+Plan.W;x++){
            int top=c.w.getTopY(Heightmap.Type.WORLD_SURFACE,x,z)-1;
            pos.set(x,top,z);var s=c.w.getBlockState(pos);
            MapColor mc=s.getMapColor(c.w,pos);int rgb=mc==null?0:mc.color;
            int prev=z>Plan.Z0?last[x-Plan.X0]:top;last[x-Plan.X0]=top;
            double k=top>prev?1.12:top<prev?0.82:1.0;
            if(!s.getFluidState().isEmpty())k=0.75+0.25*Math.max(0,Math.min(1,(top-50)/12.0));
            int r=(int)Math.min(255,((rgb>>16)&255)*k),g=(int)Math.min(255,((rgb>>8)&255)*k),b=(int)Math.min(255,(rgb&255)*k);
            img.setRGB(x-Plan.X0,z-Plan.Z0,(r<<16)|(g<<8)|b);
        }
        ImageIO.write(img,"png",out.toFile());
        Town.log("preview written to "+out);
    }
}
