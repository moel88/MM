package dev.sangwoo.celestialfall.client.loot;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import java.util.*;
/** Authored game geometry, not an image overlay: cardboard flaps, walnut staves, curved brass lid. */
final class LootModel {
    record Face(float[] v,float nx,float ny,float nz,int color){}
    static final int CARD=0xb39367,TAPE=0xd0bb94,EDGE=0xd8c5a3;
    static final int WOOD=0x352720,GOLD=0xc9a743,BRIGHT=0xf0d37a,DARK_GOLD=0x93702b;
    static final Mesh BOX=boxBody(),CHEST=chestBody(),LID=chestLid();
    static final Mesh FRONT=flap(1.1,.41,true),SIDE=flap(.565,.84,false);
    static final class Mesh {
        final List<Face> faces=new ArrayList<>();
        Mesh face(int color,double nx,double ny,double nz,double... v){float[] a=new float[v.length];for(int i=0;i<v.length;i++)a[i]=(float)v[i];faces.add(new Face(a,(float)nx,(float)ny,(float)nz,color));return this;}
        Mesh box(double x,double y,double z,double w,double h,double d,int c){
            double X=x+w,Y=y+h,Z=z+d;
            face(c,0,1,0,x,Y,z,x,Y,Z,X,Y,Z,X,Y,z);
            face(shade(c,.70),0,-1,0,x,y,Z,x,y,z,X,y,z,X,y,Z);
            face(shade(c,.88),0,0,-1,X,y,z,x,y,z,x,Y,z,X,Y,z);
            face(shade(c,.78),0,0,1,x,y,Z,X,y,Z,X,Y,Z,x,Y,Z);
            face(shade(c,.82),-1,0,0,x,y,z,x,y,Z,x,Y,Z,x,Y,z);
            face(shade(c,.94),1,0,0,X,y,Z,X,y,z,X,Y,z,X,Y,Z);return this;
        }
        void draw(MatrixStack m,VertexConsumer out,int light,float alpha){
            var pos=m.peek().getPositionMatrix();var norm=m.peek().getNormalMatrix();
            for(var f:faces)for(int i=0;i<4;i++)out.vertex(pos,f.v[i*3],f.v[i*3+1],f.v[i*3+2]).color((f.color>>16&255)/255f,(f.color>>8&255)/255f,(f.color&255)/255f,alpha).texture((i==0||i==3)?0:1,i<2?0:1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(norm,f.nx,f.ny,f.nz).next();
        }
    }
    static int shade(int c,double s){return (int)((c>>16&255)*s)<<16|(int)((c>>8&255)*s)<<8|(int)((c&255)*s);}
    private static Mesh boxBody(){
        var m=new Mesh();
        m.box(-.58,.03,-.43,1.16,.045,.86,CARD);
        m.box(-.58,.075,-.43,1.16,.665,.035,CARD);m.box(-.58,.075,.395,1.16,.665,.035,0xa8895d);
        m.box(-.58,.075,-.395,.035,.665,.79,0xa6885e);m.box(.545,.075,-.395,.035,.665,.79,CARD);
        m.box(-.54,.076,-.39,1.08,.012,.78,0x6f5439);
        for(double z:new double[]{-.433,.430})m.box(-.071,.045,z,.142,.694,.004,TAPE);
        for(double x:new double[]{-.582,.569})for(double z:new double[]{-.434,.419})m.box(x,.04,z,.013,.703,.015,EDGE);
        // Creased cardboard, tape overlaps, and a tiny printed handling symbol.
        for(double y:new double[]{.13,.59})m.box(-.578,y,-.435,1.156,.006,.004,0x9e7e54);
        m.box(.29,.17,-.438,.11,.018,.006,0x6c5135);m.box(.333,.17,-.439,.018,.09,.007,0x6c5135);
        m.box(.32,.26,-.439,.045,.018,.007,0x6c5135);m.box(-.071,.66,-.44,.142,.045,.005,0xe0ccaa);
        return m;
    }
    /** Local flap extends inward from its hinge, and is rotated away during opening. */
    private static Mesh flap(double w,double d,boolean end){
        var m=new Mesh();m.box(end?-w/2:0,0,end?0:-d/2,w,.019,d,CARD);
        if(end){m.box(-.071,.019,0,.142,.003,d,TAPE);m.box(-w/2,.02,d-.012,w,.002,.012,EDGE);}
        else {m.box(0,.019,-.068,w,.003,.136,TAPE);m.box(w-.012,.02,-d/2,.012,.002,d,EDGE);}
        return m;
    }
    private static Mesh chestBody(){
        var m=new Mesh();m.box(-.66,.035,-.44,1.32,.065,.88,WOOD);
        for(int i=0;i<4;i++){
            double y=.10+i*.112;int wood=new int[]{0x3b2b22,0x473328,0x362820,0x433027}[i];
            m.box(-.66,y,-.44,1.32,.108,.045,wood);m.box(-.66,y,.395,1.32,.108,.045,wood);
            m.box(-.66,y,-.395,.045,.108,.79,wood);m.box(.615,y,-.395,.045,.108,.79,wood);
            // Fine horizontal wood grain; no noisy per-frame random geometry.
            for(int j=0;j<3;j++){double x=-.6+j*.38+.025*(i%2);m.box(x,y+.026+j*.016,-.443,.24,.004,.003,shade(wood,1.17));}
        }
        for(double y:new double[]{.04,.515}){
            m.box(-.685,y,-.458,1.37,.045,.035,GOLD);m.box(-.685,y,.423,1.37,.045,.035,GOLD);
            m.box(-.685,y,-.423,.04,.045,.846,GOLD);m.box(.645,y,-.423,.04,.045,.846,GOLD);
            m.box(-.682,y+.038,-.46,1.364,.007,.006,BRIGHT);
        }
        for(double x:new double[]{-.55,.475}){
            m.box(x,.08,-.46,.075,.435,.025,GOLD);m.box(x,.08,.435,.075,.435,.025,GOLD);
            for(double y:new double[]{.14,.46})m.box(x+.024,y,-.468,.025,.025,.012,BRIGHT);
        }
        for(double x:new double[]{-.68,.645})for(double z:new double[]{-.455,.425})m.box(x,.075,z,.035,.44,.03,GOLD);
        m.box(-.61,.10,-.385,1.22,.02,.77,0x171617);
        // Small feet and back hinges.
        for(double x:new double[]{-.60,.47})for(double z:new double[]{-.39,.29})m.box(x,0,z,.13,.055,.1,DARK_GOLD);
        for(double x:new double[]{-.53,.45})m.box(x,.48,.432,.09,.14,.04,DARK_GOLD);
        return m;
    }
    private static Mesh chestLid(){
        var m=new Mesh();int segments=16;double r=.44,base=.56;
        for(int i=0;i<segments;i++){
            double a=i*Math.PI/segments,b=(i+1)*Math.PI/segments;
            double za=-r*Math.cos(a),zb=-r*Math.cos(b),ya=base+r*Math.sin(a),yb=base+r*Math.sin(b),mid=(a+b)/2;
            int wood=i%3==0?0x49352a:i%3==1?0x3d2c24:0x342720;
            m.face(wood,0,Math.sin(mid),-Math.cos(mid),-.66,ya,za,.66,ya,za,.66,yb,zb,-.66,yb,zb);
            // Slightly inset interior under the curved shell.
            m.face(0x241c18,0,-Math.sin(mid),Math.cos(mid),-.62,base+(r-.035)*Math.sin(a),-(r-.035)*Math.cos(a),-.62,base+(r-.035)*Math.sin(b),-(r-.035)*Math.cos(b),.62,base+(r-.035)*Math.sin(b),-(r-.035)*Math.cos(b),.62,base+(r-.035)*Math.sin(a),-(r-.035)*Math.cos(a));
            for(double x:new double[]{-.55,.475}){
                double R=r+.012;m.face(i%4==0?BRIGHT:GOLD,0,Math.sin(mid),-Math.cos(mid),x,base+R*Math.sin(a),-R*Math.cos(a),x+.075,base+R*Math.sin(a),-R*Math.cos(a),x+.075,base+R*Math.sin(b),-R*Math.cos(b),x,base+R*Math.sin(b),-R*Math.cos(b));
            }
            for(double x:new double[]{-.664,.664}){
                m.face(WOOD,Math.signum(x),0,0,x,base,0,x,ya,za,x,yb,zb,x,base,0);
                double xx=x+Math.signum(x)*.006,outer=r+.013,inner=r-.035;
                m.face(i<8?BRIGHT:GOLD,Math.signum(x),0,0,xx,base+outer*Math.sin(a),-outer*Math.cos(a),xx,base+outer*Math.sin(b),-outer*Math.cos(b),xx,base+inner*Math.sin(b),-inner*Math.cos(b),xx,base+inner*Math.sin(a),-inner*Math.cos(a));
            }
        }
        m.box(-.68,.55,-.455,1.36,.04,.035,GOLD);m.box(-.68,.55,.42,1.36,.04,.035,GOLD);
        for(double x:new double[]{-.682,.647})m.box(x,.55,-.42,.035,.04,.84,GOLD);
        // The gold shield-shaped hasp and inset keyhole move with the lid.
        m.face(BRIGHT,0,0,-1,-.09,.635,-.49,.09,.635,-.49,.07,.45,-.49,-.07,.45,-.49);
        m.face(GOLD,0,0,-1,-.07,.45,-.49,.07,.45,-.49,0,.395,-.49,0,.395,-.49);
        m.box(-.018,.49,-.496,.036,.067,.008,0x3f321c);m.box(-.028,.55,-.497,.056,.035,.009,0x3f321c);
        return m;
    }
    private LootModel(){}
}
