package dev.sangwoo.celestialfall.client.title;

/** Real-time fixed shots. Terrain queries are read-only; no chunk requests or player movement. */
public final class TitleCameraTour {
    public static final long HOLD_MILLIS=10_000;
    public static final int SHOT_COUNT=4;
    public static final double FOV=70;
    public record Point(double x,double y,double z){}
    public record Shot(Point position,Point focus,float yaw,float pitch){}
    public interface Terrain {
        boolean loaded(int x,int z);
        int surface(int x,int z);
        int ceiling();
        boolean clear(Point position,Point focus);
    }
    private final Point origin;
    private final int viewDistance;
    private final Terrain terrain;
    private long started=Long.MIN_VALUE,interval=-1,retryAt=Long.MIN_VALUE;
    private Shot current;
    public TitleCameraTour(Point origin,int viewDistance,Terrain terrain){this.origin=origin;this.viewDistance=viewDistance;this.terrain=terrain;}
    public Shot sample(long now){
        if(started==Long.MIN_VALUE){
            if(now<retryAt)return null;
            current=choose(0,origin,viewDistance,terrain);retryAt=now+250;
            if(current==null)return null;
            started=now;interval=0;
        }
        long next=Math.max(0,now-started)/HOLD_MILLIS;
        if(next!=interval){
            interval=next;var candidate=choose((int)(next%SHOT_COUNT),origin,viewDistance,terrain);
            if(candidate!=null)current=candidate;
        }
        return current;
    }
    public long interval(){return interval;}
    public static Shot choose(int slot,Point origin,int viewDistance,Terrain terrain){
        slot=Math.floorMod(slot,SHOT_COUNT);
        double angle=Math.toRadians(35+slot*90);
        double radius=Math.max(12,Math.min(72,(viewDistance-1)*16));
        radius*=new double[]{1,.82,.94,.76}[slot];
        for(double shrink:new double[]{1,.75,.5,.3,.15,0}){
            double r=radius*shrink,dx=Math.cos(angle),dz=Math.sin(angle);
            double x=origin.x()+dx*r,z=origin.z()+dz*r;
            double fx=origin.x()-dx*r*.16,fz=origin.z()-dz*r*.16;
            // Check the surrounding chunks and the entire view corridor before asking for heights.
            if(!loadedAround(terrain,x,z,12)||!loadedAround(terrain,fx,fz,8)||!loadedLine(terrain,x,z,fx,fz))continue;
            int ground=Math.max(terrain.surface((int)Math.floor(x),(int)Math.floor(z)),terrain.surface((int)Math.floor(fx),(int)Math.floor(fz)));
            double fy=terrain.surface((int)Math.floor(fx),(int)Math.floor(fz))+2.5;
            Point focus=new Point(fx,Math.min(fy,terrain.ceiling()-2),fz);
            for(int lift=0;lift<3;lift++){
                double height=new double[]{22,30,18,26}[slot]+lift*10;
                Point position=new Point(x,Math.min(terrain.ceiling()-2,ground+height),z);
                if(!terrain.clear(position,focus))continue;
                return lookAt(position,focus);
            }
        }
        return null;
    }
    private static boolean loadedAround(Terrain t,double x,double z,int pad){
        for(int dx:new int[]{-pad,0,pad})for(int dz:new int[]{-pad,0,pad})
            if(!t.loaded((int)Math.floor(x+dx),(int)Math.floor(z+dz)))return false;
        return true;
    }
    private static boolean loadedLine(Terrain t,double x,double z,double fx,double fz){
        int steps=Math.max(1,(int)Math.ceil(Math.hypot(fx-x,fz-z)/8));
        for(int i=0;i<=steps;i++){double a=i/(double)steps;if(!loadedAround(t,x+(fx-x)*a,z+(fz-z)*a,2))return false;}
        return true;
    }
    public static Shot lookAt(Point p,Point f){
        double dx=f.x()-p.x(),dy=f.y()-p.y(),dz=f.z()-p.z();
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        return new Shot(p,f,yaw,pitch);
    }
}
