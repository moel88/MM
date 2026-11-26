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
    /** Blocks: nearer than this along the crosshair's line, the body looks at the line that far out ({@link #sight}). */
    public static final double SIGHT_NEAR=8;
    /**
     * The point the player looks at so that their own sight (from {@code eye}) meets what the crosshair is on (2.142.1): the
     * camera at {@code cam} looks along unit {@code look} and its centre line meets something at {@code hit}; the point is
     * {@code hit}, or, nearer than {@code near} past where that line is abreast of the eye, the line's point that far out.
     * So a skill the server aims by the player's sight lands on the crosshair (the shift lock's camera stands .65 of a
     * block to their right), and the body is never turned far from the camera's way by ground at its feet: its heading by
     * at most atan(shoulder/near), however steeply the camera looks down ({@code near} is blocks along the ground). In the first person, and with the lock off, the line goes through the eye: the camera's own way.
     */
    public static double[] sight(double[] cam,double[] look,double[] eye,double[] hit,double near){
        double abreast=Math.max(0,(eye[0]-cam[0])*look[0]+(eye[1]-cam[1])*look[1]+(eye[2]-cam[2])*look[2]);
        double far=(hit[0]-cam[0])*look[0]+(hit[1]-cam[1])*look[1]+(hit[2]-cam[2])*look[2];
        // near is measured along the ground: looking steeply down the line is followed further, so the body's heading
        // (not only its line of sight) stays within atan(shoulder/near) of the camera's
        double flat=Math.max(.05,Math.hypot(look[0],look[2]));
        double out=Math.max(far,abreast+near/flat);
        return new double[]{cam[0]+look[0]*out,cam[1]+look[1]*out,cam[2]+look[2]*out};
    }
    public static float movementYaw(float cameraYaw,float forward,float side){return wrap(cameraYaw-(float)Math.toDegrees(Math.atan2(side,forward)));}
    public static float turn(float from,float to,float maximum){return from+(float)clamp(wrap(to-from),-maximum,maximum);}
    public static float wrap(float angle){angle%=360;if(angle>=180)angle-=360;if(angle< -180)angle+=360;return angle;}
    public static double clamp(double n,double low,double high){return Math.max(low,Math.min(high,n));}
}
