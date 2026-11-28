package dev.sangwoo.celestialfall.client.stand;

import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Gold Experience's body in its skin "고퀄리티" (2.156.0, GoldSkin.HQ; its own body from 2.155.0 to 2.155.1): the model
 * the user sent (their .glb's Gold Experience, tools/stand/import_gold_experience_hq.py), as triangles, each vertex carried by up to four of the GeckoLib model's bones. GeckoLib
 * draws only boxes; its model here is the skeleton, which the stances turn as they always have, and every frame each
 * vertex goes where its bones take it, as much with each as its weight (linear blend skinning, as the model's own skin
 * moves it). The file holds every vertex where it is in the rest pose, in the model's frame as GeckoLib bakes it
 * (blocks, the right side at +x, the front at -z), so a bone's matrix as GeckoLib draws its cubes takes it straight to
 * the screen.
 */
final class GoldExperienceMesh {
    /** What a triangle belongs to: the body, or an arm (all a rush's afterimage of an arm shows, GoldRush). */
    static final int BODY=0,LEFT_ARM=1,RIGHT_ARM=2;
    final String[] bones;
    /** Per vertex: where it is at rest (x, y, z), its normal, its place on the texture (u, v). */
    final float[] pos,normal,uv;
    /** Per vertex, four each: the bones it goes with (-1 for none) and how much. */
    final int[] bone;
    final float[] weight;
    /** Three vertices a triangle, and what each belongs to. */
    final int[] tri;
    final byte[] part;
    private final Map<String,Integer> index=new HashMap<>();
    private float[] px,py,pz,nx,ny,nz;
    private static GoldExperienceMesh loaded;

    static GoldExperienceMesh get(){
        if(loaded==null){
            try(var in=GoldExperienceMesh.class.getResourceAsStream("/assets/celestialfall/"+GoldSkin.HQ.mesh)){
                loaded=read(Objects.requireNonNull(in,"Gold Experience's body"));
            }catch(Exception ex){throw new IllegalStateException("Gold Experience's body could not be read",ex);}
        }
        return loaded;
    }

    static GoldExperienceMesh read(InputStream raw)throws java.io.IOException{
        var in=new DataInputStream(new BufferedInputStream(raw));
        byte[] magic=new byte[4];in.readFully(magic);
        if(magic[0]!='G'||magic[1]!='E'||magic[2]!='M'||magic[3]!='1')throw new java.io.IOException("not a Gold Experience body");
        String[] bones=new String[in.readInt()];
        for(int i=0;i<bones.length;i++)bones[i]=in.readUTF();
        int n=in.readInt();
        float[] pos=new float[n*3],normal=new float[n*3],uv=new float[n*2],weight=new float[n*4];int[] bone=new int[n*4];
        for(int i=0;i<n;i++){
            for(int k=0;k<3;k++)pos[i*3+k]=in.readFloat();
            for(int k=0;k<3;k++)normal[i*3+k]=in.readFloat();
            uv[i*2]=in.readFloat();uv[i*2+1]=in.readFloat();
            for(int k=0;k<4;k++){int b=in.readUnsignedByte();bone[i*4+k]=b==255?-1:b;}
            for(int k=0;k<4;k++)weight[i*4+k]=in.readFloat();
        }
        int[] tri=new int[in.readInt()*3];
        for(int i=0;i<tri.length;i++)tri[i]=in.readInt();
        return new GoldExperienceMesh(bones,pos,normal,uv,bone,weight,tri);
    }

    GoldExperienceMesh(String[] bones,float[] pos,float[] normal,float[] uv,int[] bone,float[] weight,int[] tri){
        this.bones=bones;this.pos=pos;this.normal=normal;this.uv=uv;this.bone=bone;this.weight=weight;this.tri=tri;
        for(int i=0;i<bones.length;i++)index.put(bones[i],i);
        int[] side=new int[bones.length];
        for(int i=0;i<bones.length;i++)side[i]=arm(bones[i]);
        part=new byte[tri.length/3];
        for(int t=0;t<part.length;t++){
            // the side that carries most of its three corners
            float[] share=new float[3];
            for(int c=0;c<3;c++){int v=tri[t*3+c];for(int k=0;k<4;k++){int b=bone[v*4+k];if(b>=0)share[side[b]]+=weight[v*4+k];}}
            part[t]=(byte)(share[LEFT_ARM]>share[BODY]&&share[LEFT_ARM]>=share[RIGHT_ARM]?LEFT_ARM:share[RIGHT_ARM]>share[BODY]?RIGHT_ARM:BODY);
        }
        int count=pos.length/3;px=new float[count];py=new float[count];pz=new float[count];nx=new float[count];ny=new float[count];nz=new float[count];
    }

    /** The arm a bone is part of, from the shoulder down to the fingertips, or the body. */
    static int arm(String bone){
        boolean left=bone.startsWith("left_"),right=bone.startsWith("right_");
        if(!left&&!right)return BODY;
        String rest=bone.substring(left?5:6);
        boolean arm=rest.equals("arm")||rest.equals("forearm")||rest.equals("hand")||rest.equals("fingers")
            ||rest.startsWith("index_")||rest.startsWith("middle_")||rest.startsWith("ring_")||rest.startsWith("pinky_");
        return !arm?BODY:left?LEFT_ARM:RIGHT_ARM;
    }

    /** A bone's place in the file's list, or -1. */
    int bone(String name){Integer i=index.get(name);return i==null?-1:i;}
    int vertices(){return pos.length/3;}

    /**
     * Moves every vertex by its bones' matrices (each the place of that bone as GeckoLib draws its cubes: model to
     * screen) and its normal by their normal matrices; a bone not drawn this frame leaves the vertex where its other
     * bones take it.
     */
    void skin(Matrix4f[] poses,Matrix3f[] normals,boolean[] seen){
        int n=vertices();
        for(int v=0;v<n;v++){
            float x=pos[v*3],y=pos[v*3+1],z=pos[v*3+2],a=normal[v*3],b=normal[v*3+1],c=normal[v*3+2];
            float ox=0,oy=0,oz=0,oa=0,ob=0,oc=0,sum=0;
            for(int k=0;k<4;k++){
                int bi=bone[v*4+k];float w=weight[v*4+k];
                if(bi<0||w<=0||!seen[bi])continue;
                Matrix4f m=poses[bi];Matrix3f q=normals[bi];
                ox+=w*(m.m00()*x+m.m10()*y+m.m20()*z+m.m30());
                oy+=w*(m.m01()*x+m.m11()*y+m.m21()*z+m.m31());
                oz+=w*(m.m02()*x+m.m12()*y+m.m22()*z+m.m32());
                oa+=w*(q.m00()*a+q.m10()*b+q.m20()*c);
                ob+=w*(q.m01()*a+q.m11()*b+q.m21()*c);
                oc+=w*(q.m02()*a+q.m12()*b+q.m22()*c);
                sum+=w;
            }
            if(sum>0&&sum<.9999f){float s=1/sum;ox*=s;oy*=s;oz*=s;}
            float len=(float)Math.sqrt(oa*oa+ob*ob+oc*oc);if(len>1e-6f){oa/=len;ob/=len;oc/=len;}
            px[v]=ox;py[v]=oy;pz[v]=oz;nx[v]=oa;ny[v]=ob;nz[v]=oc;
        }
    }
    float x(int v){return px[v];}
    float y(int v){return py[v];}
    float z(int v){return pz[v];}

    /**
     * Draws the body as it was last skinned, each triangle as a quad (its last corner twice), in the colour given; only
     * the arms if {@code armsOnly}, each as solid as {@code showL} and {@code showR}.
     */
    void draw(VertexConsumer out,int light,int overlay,float red,float green,float blue,float alpha,boolean armsOnly,float showL,float showR){
        for(int t=0;t<part.length;t++){
            int p=part[t];
            if(armsOnly&&p==BODY)continue;
            float a=alpha*(!armsOnly?1:p==LEFT_ARM?showL:showR);
            if(a<=.004f)continue;
            for(int c=0;c<4;c++){
                int v=tri[t*3+Math.min(c,2)];
                out.vertex(px[v],py[v],pz[v],red,green,blue,a,uv[v*2],uv[v*2+1],overlay,light,nx[v],ny[v],nz[v]);
            }
        }
    }
}
