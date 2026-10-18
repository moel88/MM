package dev.sangwoo.celestialfall.client.title;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Small, versioned asset reader shared by the renderer and asset integrity tests. */
public final class CinemaAssets {
    public record Mesh(float[] vertices){public int count(){return vertices.length/14;}}
    public static final class Performance {
        public final int count,bones,fps,stride;public final float intro,loop;private final float[] frames;
        Performance(ByteBuffer b)throws IOException{
            count=b.getInt();bones=b.getInt();fps=b.getInt();intro=b.getFloat();loop=b.getFloat();stride=bones*12+9;
            if(count<2||count>10000||bones<1||bones>128||fps!=30||intro!=7||loop!=12||b.remaining()!=(long)count*stride*4)throw new IOException("Invalid cinema animation header");
            frames=new float[count*stride];b.asFloatBuffer().get(frames);finite(frames);
        }
        public void sample(double time,float[] out){
            if(out.length!=stride)throw new IllegalArgumentException("Pose stride");
            double t=Math.max(0,time);if(t>=intro)t=intro+(t-intro)%loop;
            double frame=t*fps;int i=Math.min(count-2,(int)frame);float f=(float)(frame-i);
            for(int k=0;k<stride;k++)out[k]=frames[i*stride+k]*(1-f)+frames[(i+1)*stride+k]*f;
        }
    }
    private static ByteBuffer resource(String name,int max)throws IOException{
        try(var in=CinemaAssets.class.getResourceAsStream("/assets/celestialfall/cinema/"+name)){
            if(in==null)throw new IOException("Missing cinema asset "+name);
            byte[] bytes=in.readNBytes(max+1);if(bytes.length>max)throw new IOException("Oversize cinema asset "+name);
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }
    }
    public static Mesh mesh(String name)throws IOException{
        ByteBuffer b=resource(name+".mesh",24_000_000);
        if(b.remaining()<8||b.getInt()!=0x31534d42)throw new IOException("Invalid cinema mesh");
        int n=b.getInt();if(n<0||n>400_000||n%3!=0||b.remaining()!=(long)n*56)throw new IOException("Invalid cinema vertices");
        float[] v=new float[n*14];b.asFloatBuffer().get(v);finite(v);return new Mesh(v);
    }
    public static Performance performance()throws IOException{
        ByteBuffer b=resource("performance.anim",8_000_000);
        if(b.remaining()<24||b.getInt()!=0x31414d42)throw new IOException("Invalid cinema animation");return new Performance(b);
    }
    private static void finite(float[] values)throws IOException{for(float f:values)if(!Float.isFinite(f))throw new IOException("Nonfinite cinema asset");}
    private CinemaAssets(){}
}
