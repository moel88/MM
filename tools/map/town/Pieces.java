package dev.sangwoo.botandtown;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.util.BlockRotation;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Pieces of Botand's first map (Azure Garden v1.3) lifted block for block by tools/map/town/lift_azure.py: the arena,
 * the car park and the plain white buildings the old city of the hourglass island is made of (map v3.0). A piece's
 * y 0 is one block under its old ground; its front is the side its door faces.
 */
final class Pieces {
    record Piece(String name,int W,int H,int D,String front,int ox,int oz,BlockState[] palette,int[] data){
        /** The state at a position of the old world's box (x, z from its west and north sides). */
        BlockState at(int x,int y,int z){return palette[data[(y*D+z)*W+x]];}
        /** The piece's width and depth seen from its front. */
        int frontage(){return front.equals("south")||front.equals("north")||front.equals("none")?W:D;}
        int depth(){return front.equals("south")||front.equals("north")||front.equals("none")?D:W;}
    }
    final Map<String,Piece> all=new LinkedHashMap<>();

    Pieces(Path file)throws IOException{
        List<String> lines;
        try(var in=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)),java.nio.charset.StandardCharsets.UTF_8))){lines=in.lines().toList();}
        var blocks=Registries.BLOCK.getReadOnlyWrapper();
        for(int i=0;i<lines.size();i++){
            String l=lines.get(i);
            if(!l.startsWith("piece "))continue;
            String[] h=l.split(" ");
            int W=Integer.parseInt(h[2]),H=Integer.parseInt(h[3]),D=Integer.parseInt(h[4]);
            int n=Integer.parseInt(lines.get(i+1).split(" ")[1]);
            BlockState[] pal=new BlockState[n];
            for(int k=0;k<n;k++){
                try{pal[k]=BlockArgumentParser.block(blocks,lines.get(i+2+k),false).blockState();}
                catch(Exception e){throw new IOException("piece "+h[1]+": cannot read "+lines.get(i+2+k),e);}
            }
            int[] data=new int[W*H*D];int at=0;
            for(String tok:lines.get(i+2+n).substring(5).split(" ")){
                int star=tok.indexOf('*');
                int count=star<0?1:Integer.parseInt(tok.substring(0,star)),v=Integer.parseInt(star<0?tok:tok.substring(star+1));
                Arrays.fill(data,at,at+count,v);at+=count;
            }
            if(at!=data.length)throw new IOException("piece "+h[1]+": "+at+" blocks for "+data.length);
            all.put(h[1],new Piece(h[1],W,H,D,h[5],Integer.parseInt(h[6]),Integer.parseInt(h[7]),pal,data));
            i+=2+n;
        }
        Town.log("pieces: "+all.keySet());
    }
    Piece get(String name){var p=all.get(name);if(p==null)throw new IllegalStateException("no piece "+name);return p;}

    /**
     * Pastes a piece as it lay (its old north still north) with its north-west corner at (x0, z0) and its old ground
     * at y: every block but air, and air too from its old ground up (so it stands clear).
     */
    static void paste(Canvas c,Piece p,int x0,int z0,int y){
        for(int x=0;x<p.W;x++)for(int z=0;z<p.D;z++)for(int k=0;k<p.H;k++){
            BlockState s=p.at(x,k,z);
            if(s.isAir()&&k<=1)continue;
            c.set(x0+x,y-1+k,z0+z,s);
        }
        for(int x=0;x<p.W;x++)for(int z=0;z<p.D;z++)top(c,x0+x,z0+z,y);
    }
    /**
     * Pastes a building into a frame with its door to the frame's front: the piece is turned so its front side is the
     * frame's front (bz = 0), and the frame turns it to face its street.
     */
    static void paste(Frame f,Piece p){
        BlockRotation turn=switch(p.front){case "south"->BlockRotation.CLOCKWISE_180;case "east"->BlockRotation.COUNTERCLOCKWISE_90;case "west"->BlockRotation.CLOCKWISE_90;default->BlockRotation.NONE;};
        for(int x=0;x<p.W;x++)for(int z=0;z<p.D;z++)for(int k=0;k<p.H;k++){
            BlockState s=p.at(x,k,z);
            if(s.isAir()&&k<=1)continue;
            int bx,bz;
            switch(p.front){
                case "south"->{bx=p.W-1-x;bz=p.D-1-z;}
                case "east"->{bx=z;bz=p.W-1-x;}
                case "west"->{bx=p.D-1-z;bz=x;}
                default->{bx=x;bz=z;}
            }
            f.set(bx,k-1,bz,s.rotate(turn));
        }
        for(int bx=0;bx<p.frontage();bx++)for(int bz=0;bz<p.depth();bz++)top(f.c,f.wx(bx,bz),f.wz(bx,bz),f.y0);
    }
    /** The column's ground after pasting: its highest solid block at the piece's ground level or a little over it. */
    private static void top(Canvas c,int x,int z,int y){
        int g=y;
        for(int k=y+3;k>=y-1;k--)if(!c.air(x,k,z)&&!c.get(x,k,z).isOf(Blocks.WATER)){g=k;break;}
        c.ground(x,z,Math.max(y-1,Math.min(g,y+1)));
    }
}
