package dev.sangwoo.celestialfall.combat;

/** Render-rate-independent orbit math, shared by the client and the preview exporter. */
public final class OrbitCameraState {
    public static final double DEFAULT_DISTANCE=5.5, MAX_DISTANCE=14, MIN_THIRD_DISTANCE=1.35;
    public double targetDistance=DEFAULT_DISTANCE, distance=DEFAULT_DISTANCE, shoulder, collisionDistance=DEFAULT_DISTANCE;
    public float yaw, pitch=14;
    public boolean shiftLock, run;
    public void reset(float heading) {yaw=heading;pitch=14;targetDistance=distance=collisionDistance=DEFAULT_DISTANCE;shoulder=0;shiftLock=run=false;}
    public void rotate(double horizontal,double vertical) {yaw=wrap(yaw+(float)horizontal);pitch=(float)clamp(pitch+vertical,-75,80);}
    public void scroll(double amount) {
        if(!Double.isFinite(amount))return;
        double direction=Math.signum(amount),remaining=Math.min(24,Math.abs(amount));
        while(remaining>0) {
            double step=Math.min(1,remaining);
            if(targetDistance==0&&direction<0)targetDistance=MIN_THIRD_DISTANCE;
            else targetDistance=clamp(targetDistance-direction*(.55+targetDistance*.14)*step,0,MAX_DISTANCE);
            if(targetDistance<MIN_THIRD_DISTANCE)targetDistance=direction>0?0:MIN_THIRD_DISTANCE;
            remaining-=step;
        }
    }
    public void step(double seconds) {
        double dt=clamp(seconds,0,.1),a=1-Math.exp(-18*dt);
        distance+=(targetDistance-distance)*a;
        if(Math.abs(distance-targetDistance)<.005)distance=targetDistance;
        shoulder+=((shiftLock&&distance>.7?.65:0)-shoulder)*(1-Math.exp(-14*dt));
    }
    public boolean firstPerson(){return distance<.7;}
    public boolean captured(boolean rightDrag){return shiftLock||firstPerson()||rightDrag;}
    /** Obstacles pull in immediately; open space eases out. */
    public double clip(double available,double seconds) {
        available=clamp(available,0,distance);
        if(available<collisionDistance)collisionDistance=available;
        else collisionDistance+=(available-collisionDistance)*(1-Math.exp(-10*clamp(seconds,0,.1)));
        return collisionDistance;
    }
    public record Offset(double x,double y,double z) {}
    public Offset offset(double length,double lateral) {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        return new Offset(Math.sin(y)*Math.cos(p)*length-Math.cos(y)*lateral,
            Math.sin(p)*length, -Math.cos(y)*Math.cos(p)*length-Math.sin(y)*lateral);
    }
    /** Input expressed in player-local coordinates while retaining the camera-relative world vector. */
    public static float[] movement(float forward,float side,float cameraYaw,float playerYaw) {
        double angle=Math.toRadians(cameraYaw-playerYaw);
        return new float[]{(float)(Math.cos(angle)*forward+Math.sin(angle)*side),
            (float)(Math.cos(angle)*side-Math.sin(angle)*forward)};
    }
    public static float movementYaw(float cameraYaw,float forward,float side){return wrap(cameraYaw-(float)Math.toDegrees(Math.atan2(side,forward)));}
    public static float turn(float from,float to,float maximum){return from+(float)clamp(wrap(to-from),-maximum,maximum);}
    public static float wrap(float angle){angle%=360;if(angle>=180)angle-=360;if(angle< -180)angle+=360;return angle;}
    public static double clamp(double n,double low,double high){return Math.max(low,Math.min(high,n));}
}
