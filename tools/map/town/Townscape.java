package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Puts the town together, in the order the pieces depend on each other, and writes the map's definition. */
final class Townscape {
    final Canvas c;final Terrain terrain;final Layout layout;final Random rnd;
    final Roads roads;final Hill hill;final Nature nature;final Houses houses;
    /** Places the mod uses: merchants (seller, buyer) and the field supply spots. */
    final List<int[]> merchants=new ArrayList<>();final List<int[]> loot=new ArrayList<>();
    final Map<String,int[]> places=new LinkedHashMap<>();
    Townscape(Canvas c,Terrain terrain,Layout layout,Random rnd){
        this.c=c;this.terrain=terrain;this.layout=layout;this.rnd=rnd;
        roads=new Roads(this);hill=new Hill(this);nature=new Nature(this);houses=new Houses(this);
    }

    void build(){
        roads.plan();hill.plan(roads);
        roads.lay();Town.log("streets laid: "+c.placed);
        hill.build();Town.log("stairway and ramps: "+c.placed);
        assignUses();
        Map<String,Integer> counts=new TreeMap<>();
        for(Lot l:layout.lots){
            if(l.use.isEmpty())continue;
            houses.build(l);counts.merge(l.use,1,Integer::sum);
        }
        Town.log("plots built: "+counts+", "+c.placed);
        nature.woods();Town.log("woods: "+c.placed);
    }

    /** What each plot becomes, by where it lies. */
    void assignUses(){
        var r=new Random(rnd.nextLong());
        for(Lot l:layout.lots){
            String[] pool=switch(l.zone){
                case "row","main","station-street"->new String[]{"old","old","old","modern","modern","modern","modern","modern3","apartment","apartment","parking","field","garden","vacant"};
                case "terrace"->new String[]{"old","old","old","old","modern","modern","modern","modern3","apartment","field","field","garden","vacant","parking"};
                case "terrace-back"->new String[]{"old","old","modern","modern","field","field","field","garden","vacant"};
                case "strip"->new String[]{"garden","field"};
                default->null;
            };
            if(pool==null)continue;
            String use=pool[r.nextInt(pool.length)];
            if(use.equals("apartment")&&l.frontage()<13)use="modern";
            l.use=use;
        }
    }

    /** Where players appear: the station square, looking down the main street to the bridge. */
    int[] spawn(){return new int[]{0,Plan.COAST+1,Plan.ST_S[0]+6,0};}

    void definition(Path pack)throws Exception{
        Files.createDirectories(pack.resolve("data/botand"));
    }

    /** A top-down picture of the finished model in the colours of an in-game map (a check for the builder). */
    void preview(Path out)throws Exception{
        var img=new BufferedImage(Plan.W,Plan.D,BufferedImage.TYPE_INT_RGB);
        int[] last=new int[Plan.W];
        for(int z=Plan.Z0;z<Plan.Z0+Plan.D;z++)for(int x=Plan.X0;x<Plan.X0+Plan.W;x++){
            int top=c.top(x,z,Canvas.YMAX);
            BlockState s=c.get(x,top,z);
            int rgb=s.getMapColor(null,null).color;
            int prev=z>Plan.Z0?last[x-Plan.X0]:top;last[x-Plan.X0]=top;
            double k=top>prev?1.12:top<prev?0.82:1.0;
            if(!s.getFluidState().isEmpty()){int d=top-c.ground(x,z);k=Math.max(0.55,1.0-d*0.04);}
            int r=(int)Math.min(255,((rgb>>16)&255)*k),g=(int)Math.min(255,((rgb>>8)&255)*k),b=(int)Math.min(255,(rgb&255)*k);
            img.setRGB(x-Plan.X0,z-Plan.Z0,(r<<16)|(g<<8)|b);
        }
        ImageIO.write(img,"png",out.toFile());
        Town.log("preview written to "+out);
    }
}
