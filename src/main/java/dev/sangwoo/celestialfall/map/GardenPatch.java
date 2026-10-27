package dev.sangwoo.celestialfall.map;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Immutable, bounded before/after patch; no Minecraft/client dependency in the decoder. */
public final class GardenPatch {
    public final String[] palette;
    public final int count;
    private final short[] cells;
    private GardenPatch(String[] p,int n,short[] c){palette=p;count=n;cells=c;}
    public int x(int i){return cells[i*5];}public int y(int i){return cells[i*5+1];}public int z(int i){return cells[i*5+2];}
    public int before(int i){return cells[i*5+3]&65535;}public int after(int i){return cells[i*5+4]&65535;}
    public static GardenPatch read(InputStream source)throws IOException{
        if(source==null)throw new IOException("Missing Azure Garden patch");
        try(var in=new DataInputStream(new GZIPInputStream(source))){
            if(in.readInt()!=0x42475031)throw new IOException("Invalid garden patch magic");
            int length=in.readInt();if(length<2||length>100_000)throw new IOException("Invalid patch header size");
            byte[] raw=in.readNBytes(length);if(raw.length!=length)throw new EOFException("Truncated garden header");
            var header=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();
            int count=header.get("cells").getAsInt();var list=header.getAsJsonArray("palette");
            if(count<1||count>2_000_000||list.size()<1||list.size()>4096)throw new IOException("Invalid patch limits");
            String[] palette=new String[list.size()];for(int i=0;i<palette.length;i++)palette[i]=list.get(i).getAsString();
            short[] cells=new short[count*5];
            for(int i=0;i<count;i++){
                for(int k=0;k<5;k++)cells[i*5+k]=in.readShort();
                int x=cells[i*5],y=cells[i*5+1],z=cells[i*5+2];
                if(x< -176||x>175||z< -192||z>191||y<63||y>150||(cells[i*5+3]&65535)>=palette.length||(cells[i*5+4]&65535)>=palette.length)throw new IOException("Garden cell out of bounds");
            }
            if(in.read()!=-1)throw new IOException("Trailing garden data");return new GardenPatch(palette,count,cells);
        }catch(RuntimeException e){throw new IOException("Invalid garden metadata",e);}
    }
}
