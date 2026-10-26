package dev.sangwoo.celestialfall.client.meteor;

import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.MotionPlayerModel;
import dev.sangwoo.celestialfall.client.MotionRig;
import dev.sangwoo.celestialfall.combat.MotionPose;
import dev.sangwoo.celestialfall.meteor.MeteorRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The meteor pickaxe in both hands (2.49.1, user request: "운석을 캘때 양손으로 곡괭이를 들고 캐는 모션"). While a
 * player on the Botand map holds it, both hands hold its handle, and every blow (the main hand's swing, which every
 * client sees, 6 ticks) is a chop: the torso twists right and the pickaxe goes up beside the head, hangs a moment, then
 * the torso untwists and bends, the knees give and the pickaxe comes over and down in front, faster and faster, stops
 * dead at the strike (where the server's hit lands, {@link #STRIKE_TICKS}), bounces a little and lifts back to the hold.
 * <p>
 * The pickaxe is keyed in the torso's space and both arms reach its handle (a two-bone solve, elbows down and out), so
 * the hands never leave it; the held item is then moved from the right hand onto that handle ({@link #applyGrip}).
 * First person has its own path for the camera: the same chop, framed to be seen, with both forearms on the handle.
 * Every value is a smooth function of the swing's progress (60 fps, no steps), the strike's sudden stop aside.
 */
public final class MiningPose {
    private MiningPose(){}
    private static final float RAD=(float)Math.PI/180;
    /** The swing's progress where the pickaxe lands. */
    public static final float STRIKE=.58f;
    /**
     * Ticks from the click to sending the blow, so the server's hit (its sparks and sound) comes as the pickaxe lands: on
     * screen a swing runs a tick behind, so the strike shows {@code 1+STRIKE*6} = 4.5 ticks after the click, and the hit,
     * sent at the end of the fifth tick, half a tick later (on every client: the swing and the blow both go through the
     * server; the miner's own client sees it later by the round trip).
     */
    public static final int STRIKE_TICKS=5;
    /**
     * Pixels between the two fists on the handle; model pixels to one pixel of the pickaxe's item model (vanilla holds a
     * tool at .85; held in both hands it is bigger, so the handle shows past the fists).
     */
    static final float GRIP=3.6f,SCALE=1.3f,VIEW_SCALE=.85f;
    /** A straight arm, from its shoulder: the elbow 4 px down, the fist closing on the handle 4.5 px further, in the arm's middle. */
    static final float ELBOW=4,FIST=4.5f;
    /**
     * The sprite: where the handle ends and which way it runs (item pixels), the head across it, the item's face, and
     * how far up the handle the left fist (at its end) and the right fist close.
     */
    private static final Vector3f HANDLE_END=new Vector3f(2.5f,2.25f,8),ALONG=new Vector3f(1,1,0).normalize(),ACROSS=new Vector3f(-1,1,0).normalize(),FACE=new Vector3f(0,0,1);
    static final float LEFT_FIST=1.7f;
    /** How far up the handle the right fist closes (item pixels), for a pickaxe shown at {@code scale}. */
    static float rightFist(float scale){return LEFT_FIST+GRIP/scale;}

    // ---------------------------------------------------------------- the chop

    /** The swing's keys: hold, lift, top, hang (the load), strike, bounce, recover, hold. */
    static final float[] TIMES={0,.12f,.32f,.40f,STRIKE,.66f,.82f,1};
    /** The strike lands at full speed and stops dead: the segment into it accelerates all the way. */
    private static final int IMPACT=4;
    /**
     * Third person, per key: torso pitch, yaw, roll (degrees; +yaw turns right, bringing the left shoulder forward),
     * bob (px down), shoulder lift (px), head nod (degrees), the hands' midpoint on the handle (torso px: +x the
     * character's left, +y down, -z ahead), the pickaxe's swing angle, the swing plane's turn and the head's roll
     * about the handle (degrees), and the hips' shift and lean (px).
     */
    private static final float[][] BODY={
        {6,10,0,  .5f,0,0,   .5f,5.5f,-5.3f,   40,60,0,   0,0},
        {4,22,-2, .3f,.4f,-2, -.4f,2.5f,-5.8f, 8,20,0,    -.15f,.25f},
        {-3,40,-4, .1f,1.5f,-4, -1.2f,-2.4f,-5f, -50,-35,0, -.35f,.55f},
        {-4,43,-5, 0,1.6f,-5, -1.3f,-2.6f,-4.9f, -58,-37,0, -.4f,.6f},
        {30,-4,3, 2.2f,0,7,  -.5f,4.6f,-4.4f,  110,-3,0,  .3f,-.8f},
        {27,-3,3, 2,0,6,     -.4f,4.2f,-4.6f,  98,-3,0,   .3f,-.7f},
        {14,4,1,  1,0,2,     .1f,4.6f,-5.2f,   60,30,0,   .15f,-.3f},
        {6,10,0,  .5f,0,0,   .5f,5.5f,-5.3f,   40,60,0,   0,0}};
    /**
     * First person, per key: the hands' midpoint (px from the neck; the eye is 2.16 px above it and 1 px ahead) and the
     * pickaxe's tilt across the screen (toward its left), lean into it and roll about the handle ({@link #viewTool}).
     * The hands stay about 0.6 blocks out: low on the right while holding (the head up on the right), out past the top
     * right corner at the top (the pickaxe leaning back over the shoulder, out of the picture with the fists), and low
     * in the middle at the strike, the head coming down by the crosshair. The pickaxe's face stays turned to the eye (a flat item seen edge
     * on is a line).
     */
    private static final float[][] VIEW={
        {-3f,1.6f,-10f,      -30,30,0},
        {-3.6f,-.6f,-9.6f,   -25,0,0},
        {-7.4f,-9.2f,-8.6f,  -15,-45,0},
        {-7.5f,-9.5f,-8.3f,  -13,-52,0},
        {-1.5f,3f,-11f,      35,60,0},
        {-1.5f,2.6f,-11.2f,  32,52,0},
        {-2.4f,2.2f,-10.6f,  0,40,0},
        {-3f,1.6f,-10f,      -30,30,0}};
    private static final Track THIRD=new Track(TIMES,BODY,IMPACT),FIRST=new Track(TIMES,VIEW,IMPACT);
    /** The feet stay where they stand: the right one back, the left one ahead. */
    private static final float[][] FEET={{-.6f,1.9f},{.6f,-1.8f}};
    /** Where each elbow points (torso space): down and out, a little back. */
    private static final Vector3f RIGHT_POLE=new Vector3f(-.6f,1,.35f),LEFT_POLE=new Vector3f(.6f,1,.35f);
    /**
     * First person: each shoulder sits well below its fist (the right one out to the right, the left one nearly under
     * its fist), out of the picture, so the arms come up from the bottom of the screen as the game's own arm does and
     * the left one never crosses the middle of the picture when both fists go up to the right.
     */
    private static final Vector3f VIEW_RIGHT_SHOULDER=new Vector3f(-3.5f,7.5f,1),VIEW_LEFT_SHOULDER=new Vector3f(1.5f,8,1.5f);

    /** One frame of the hold or a blow: the body (both arms on the handle), the shoulders' lift and the pickaxe (item blocks to torso blocks). */
    public record Frame(MotionPose pose,float lift,Matrix4f tool,Vector3f rightFist,Vector3f leftFist){}
    /** First person: each arm's shoulder, angles (pitch, yaw, roll) and elbow, and the pickaxe (item blocks to the neck's px/16 space). */
    public record View(Vector3f rightShoulder,float[] rightArm,Vector3f leftShoulder,float[] leftArm,Matrix4f tool){}

    /** The third-person pose at swing progress {@code swing} (0 holding, up to 1); {@code time} in ticks drives the breathing. */
    public static Frame frame(float swing,float time){
        float[] k=THIRD.at(Math.max(0,Math.min(1,swing)));
        float breath=(float)Math.sin(time*.09f);
        MotionPose p=new MotionPose();
        p.rot(MotionPose.BODY,k[0]+breath*.6f,k[1],k[2]).rot(MotionPose.HEAD,k[5],0,0);
        p.bob=k[3]+breath*.08f;p.shift=k[12];p.lean=k[13];
        for(int i=0;i<2;i++)leg(p,i,FEET[i][0]-p.shift,11.75f-p.bob,FEET[i][1]-p.lean);
        float lift=k[4];
        Vector3f middle=new Vector3f(k[6],k[7]+breath*.15f,k[8]);
        Matrix3f turn=tool(k[9],k[10],k[11]);
        Vector3f along=turn.getColumn(0,new Vector3f());
        Vector3f right=new Vector3f(along).mul(GRIP/2).add(middle),left=new Vector3f(along).mul(-GRIP/2).add(middle);
        float[] ra=reach(new Vector3f(-5,2-lift,0),right,RIGHT_POLE,-1),la=reach(new Vector3f(5,2-lift,0),left,LEFT_POLE,1);
        p.rot(MotionPose.RIGHT_ARM,ra[0],ra[1],ra[2]).rot(MotionPose.LEFT_ARM,la[0],la[1],la[2]);
        p.elbow[0]=ra[3];p.elbow[1]=la[3];
        return new Frame(p,lift,toolMatrix(middle,turn,SCALE),right,left);
    }
    /** The first-person pose at swing progress {@code swing}. */
    public static View view(float swing){
        float[] k=FIRST.at(Math.max(0,Math.min(1,swing)));
        Vector3f middle=new Vector3f(k[0],k[1],k[2]);
        Matrix3f turn=viewTool(k[3],k[4],k[5]);
        Vector3f along=turn.getColumn(0,new Vector3f());
        Vector3f right=new Vector3f(along).mul(GRIP/2).add(middle),left=new Vector3f(along).mul(-GRIP/2).add(middle);
        Vector3f rs=new Vector3f(right).add(VIEW_RIGHT_SHOULDER),ls=new Vector3f(left).add(VIEW_LEFT_SHOULDER);
        return new View(rs,reach(rs,right,RIGHT_POLE,-1),ls,reach(ls,left,LEFT_POLE,1),toolMatrix(middle,turn,VIEW_SCALE));
    }
    /**
     * The pickaxe's frame (columns: up the handle, toward the head's leading tip, its face): the swing plane turned by
     * {@code turn} about the vertical, the handle {@code swing} from straight up (ahead and over), the head rolled about it.
     */
    static Matrix3f tool(float swing,float turn,float roll){
        return new Matrix3f().rotationY(turn*RAD).rotateX(swing*RAD).rotateY(roll*RAD).mul(new Matrix3f().setColumn(0,0,-1,0).setColumn(1,0,0,-1).setColumn(2,1,0,0));
    }
    /**
     * First person's frame: the face turned to the eye, the handle up and the tip toward the screen's left, then the
     * handle tilted {@code tilt} across the screen (toward its left), leaned {@code lean} into it (back over the
     * shoulder when negative) and rolled about itself.
     */
    static Matrix3f viewTool(float tilt,float lean,float roll){
        return new Matrix3f().rotationX(lean*RAD).rotateZ(tilt*RAD).rotateY(roll*RAD).mul(new Matrix3f().setColumn(0,0,-1,0).setColumn(1,1,0,0).setColumn(2,0,0,1));
    }
    /**
     * Item blocks to torso blocks: the sprite's handle, head and face onto the frame's columns, {@code scale} model
     * pixels to an item pixel, its midpoint between the fists at {@code middle} (px).
     */
    static Matrix4f toolMatrix(Vector3f middle,Matrix3f frame,float scale){
        Matrix3f sprite=new Matrix3f().setColumn(0,ALONG).setColumn(1,ACROSS).setColumn(2,FACE);
        Matrix3f linear=new Matrix3f(frame).mul(sprite.transpose()).scale(scale);
        Vector3f grip=new Vector3f(ALONG).mul((LEFT_FIST+rightFist(scale))/2).add(HANDLE_END);
        Vector3f offset=linear.transform(new Vector3f(grip)).negate().add(middle).div(16);
        return new Matrix4f().set(linear).setTranslation(offset);
    }

    /**
     * The arm's angles (pitch, yaw, roll as a model part takes them, degrees) and elbow bend (degrees) that close its fist
     * on {@code target} from {@code shoulder}, the elbow toward {@code pole}; {@code side} is -1 for the right arm and 1
     * for the left (each fist sits 1 px off its pivot, in the arm's middle). Out of reach, the arm points at it straight.
     */
    public static float[] reach(Vector3f shoulder,Vector3f target,Vector3f pole,float side){
        Vector3f d=new Vector3f(target).sub(shoulder);float dist=d.length();
        float c=(dist*dist-side*side-ELBOW*ELBOW-FIST*FIST)/(2*ELBOW*FIST);
        float bend=-(float)Math.acos(Math.max(-1,Math.min(1,c)));
        Vector3f fist=new Vector3f(side,ELBOW+FIST*(float)Math.cos(bend),FIST*(float)Math.sin(bend)).normalize();
        Vector3f aim=new Vector3f(d).normalize();
        Vector3f elbowLocal=perpendicular(new Vector3f(0,1,0),fist,new Vector3f(0,0,1));
        Vector3f elbowWanted=perpendicular(new Vector3f(pole),aim,new Vector3f(0,1,0));
        Matrix3f local=new Matrix3f().setColumn(0,fist).setColumn(1,elbowLocal).setColumn(2,new Vector3f(fist).cross(elbowLocal));
        Matrix3f wanted=new Matrix3f().setColumn(0,aim).setColumn(1,elbowWanted).setColumn(2,new Vector3f(aim).cross(elbowWanted));
        Quaternionf q=new Quaternionf().setFromNormalized(wanted.mul(local.transpose())).normalize();
        float pitch=(float)Math.atan2(2*(q.w*q.x+q.y*q.z),1-2*(q.x*q.x+q.y*q.y));
        float yaw=(float)Math.asin(Math.max(-1,Math.min(1,2*(q.w*q.y-q.z*q.x))));
        float roll=(float)Math.atan2(2*(q.w*q.z+q.x*q.y),1-2*(q.y*q.y+q.z*q.z));
        return new float[]{pitch/RAD,yaw/RAD,roll/RAD,bend/RAD};
    }
    /** The part of {@code v} square to the unit {@code axis}, made unit ({@code fallback}'s if {@code v} runs along it). */
    private static Vector3f perpendicular(Vector3f v,Vector3f axis,Vector3f fallback){
        Vector3f p=new Vector3f(v).sub(new Vector3f(axis).mul(v.dot(axis)));
        if(p.lengthSquared()<1e-8f)p=new Vector3f(fallback).sub(new Vector3f(axis).mul(fallback.dot(axis)));
        return p.normalize();
    }
    /** Where an arm posed by {@link #reach} closes its fist (torso px): for checking the solve. */
    public static Vector3f fist(Vector3f shoulder,float[] arm,float side){
        Vector3f lower=new Vector3f(side,FIST,0).rotateX(arm[3]*RAD).add(0,ELBOW,0);
        return new Quaternionf().rotationZYX(arm[2]*RAD,arm[1]*RAD,arm[0]*RAD).transform(lower).add(shoulder);
    }
    /** Two-segment leg reaching its foot (hip px), as the combat clips plant theirs. */
    private static void leg(MotionPose p,int i,float x,float y,float z){
        float d=Math.min(11.98f,(float)Math.sqrt(x*x+y*y+z*z));
        float bend=(float)Math.acos(Math.max(0,d/12));
        p.joint[MotionPose.RIGHT_LEG+i][0]=(float)Math.toDegrees(Math.atan2(z,Math.hypot(x,y))-bend);
        p.joint[MotionPose.RIGHT_LEG+i][2]=(float)Math.toDegrees(-Math.atan2(x,y));
        p.knee[i]=(float)Math.toDegrees(2*bend);
    }

    /**
     * Keyed channels on monotone cubic curves: smooth through every key, never overshooting, at rest at the ends and
     * where a channel turns back. Into the {@code impact} key every channel speeds up all the way and leaves it at rest.
     */
    public static final class Track {
        private final float[] times;private final float[][] keys,in,out;
        /** {@code impact} below 1: no key is struck (every key smooth, as the others). */
        public Track(float[] times,float[][] keys,int impact){
            this.times=times;this.keys=keys;int n=times.length,c=keys[0].length;
            in=new float[n][c];out=new float[n][c];
            for(int j=0;j<c;j++)for(int i=1;i+1<n;i++){
                float h1=times[i]-times[i-1],h2=times[i+1]-times[i];
                float s1=(keys[i][j]-keys[i-1][j])/h1,s2=(keys[i+1][j]-keys[i][j])/h2;
                float m=s1*s2<=0?0:3*(h1+h2)/((2*h2+h1)/s1+(h2+2*h1)/s2);
                in[i][j]=out[i][j]=m;
            }
            // the strike: from rest, arriving at twice the segment's mean speed (an even acceleration), leaving from rest
            if(impact>=1)for(int j=0;j<c;j++){
                float h=times[impact]-times[impact-1];
                out[impact-1][j]=0;in[impact][j]=2*(keys[impact][j]-keys[impact-1][j])/h;out[impact][j]=0;
            }
        }
        public float[] at(float t){
            int n=times.length,i=1;while(i<n-1&&t>times[i])i++;
            float h=times[i]-times[i-1],u=Math.max(0,Math.min(1,(t-times[i-1])/h));
            float u2=u*u,u3=u2*u,h00=2*u3-3*u2+1,h10=u3-2*u2+u,h01=-2*u3+3*u2,h11=u3-u2;
            float[] v=new float[keys[0].length];
            for(int j=0;j<v.length;j++)v[j]=h00*keys[i-1][j]+h10*h*out[i-1][j]+h01*keys[i][j]+h11*h*in[i][j];
            return v;
        }
    }

    // ---------------------------------------------------------------- the game

    /** Whether the player holds the pickaxe in the main hand on the Botand map (it shows instead of a stand's sword). */
    public static boolean holding(AbstractClientPlayerEntity p){
        return MapClient.enabled()&&p.getMainArm()==Arm.RIGHT&&p.getMainHandStack().isOf(MeteorRegistry.PICKAXE);
    }
    /** Whether the two-handed hold shows: holding it, on the articulated rig, not using an item, not hit, dashing, punching or guarding. */
    public static boolean active(AbstractClientPlayerEntity p,float delta){
        return holding(p)&&!p.isUsingItem()&&CombatClient.animate(p)&&"base".equals(CombatClient.state(p,delta))&&!CombatClient.snapshot(p.getUuid()).guard();
    }
    /** The blow's progress: the main hand's swing (0 when it is not swinging). */
    public static float swing(AbstractClientPlayerEntity p,float delta){
        return p.preferredHand==Hand.OFF_HAND?0:p.getHandSwingProgress(delta);
    }
    /** Poses the model for this frame; the feet walk with the player's steps and plant for the blows when still. */
    public static void apply(BipedEntityModel<?> model,AbstractClientPlayerEntity p,float delta,float headYaw,float headPitch){
        delta=MathHelper.clamp(delta,0,1);
        var f=frame(swing(p,delta),p.age+delta);var pose=f.pose();
        float walk=Math.max(CombatClient.moving(p.getUuid(),delta),p.isOnGround()?0:1);
        if(walk>0){
            var steps=CombatClient.pose(p,delta);
            for(int i=MotionPose.RIGHT_LEG;i<=MotionPose.LEFT_LEG;i++)for(int j=0;j<3;j++)pose.joint[i][j]=MathHelper.lerp(walk,pose.joint[i][j],steps.joint[i][j]);
            for(int i=0;i<2;i++)pose.knee[i]=MathHelper.lerp(walk,pose.knee[i],steps.knee[i]);
            pose.bob=MathHelper.lerp(walk,pose.bob,steps.bob);pose.shift=MathHelper.lerp(walk,pose.shift,steps.shift);pose.lean=MathHelper.lerp(walk,pose.lean,steps.lean);
        }
        MotionRig.apply(model,pose,headYaw,headPitch);
        if(f.lift()!=0){
            // the shoulders rise along the torso, not straight up the screen
            float[] b=pose.joint[MotionPose.BODY];
            Vector3f up=new Quaternionf().rotationZYX(b[2]*RAD,b[1]*RAD,b[0]*RAD).transform(new Vector3f(0,-f.lift(),0));
            for(var arm:new net.minecraft.client.model.ModelPart[]{model.rightArm,model.leftArm}){arm.pivotX+=up.x;arm.pivotY+=up.y;arm.pivotZ+=up.z;}
        }
    }
    /** Whether a crossfade should run: the hold changes look when the feet leave or touch the ground. */
    public static Object source(AbstractClientPlayerEntity p){return p.isOnGround()?"mining":"mining-air";}

    /**
     * Third person: moves the held pickaxe (set up by the held-item layer on the right hand) onto the posed handle. The
     * move is taken from the designed pose, so the pickaxe stays in the right fist while the body crossfades.
     */
    public static void applyGrip(MatrixStack matrices,AbstractClientPlayerEntity p,float delta){
        var f=frame(swing(p,delta),p.age+delta);var pose=f.pose();
        float[] arm=pose.joint[MotionPose.RIGHT_ARM];
        Matrix4f held=new Matrix4f().translation(-5/16f,(2-f.lift())/16f,0)
            .rotate(new Quaternionf().rotationZYX(arm[2]*RAD,arm[1]*RAD,arm[0]*RAD))
            .translate(0,4/16f,0).rotateX(pose.elbow[0]*RAD).translate(0,-4/16f,0)
            .rotateX(-90*RAD).rotateY(180*RAD).translate(1/16f,2/16f,-10/16f);
        var mc=MinecraftClient.getInstance();
        var t=mc.getItemRenderer().getModel(p.getMainHandStack(),p.getWorld(),p,p.getId()).getTransformation().getTransformation(ModelTransformationMode.THIRD_PERSON_RIGHT_HAND);
        Matrix4f display=new Matrix4f().translation(t.translation).rotate(new Quaternionf().rotationXYZ(t.rotation.x*RAD,t.rotation.y*RAD,t.rotation.z*RAD)).scale(t.scale);
        transform(matrices,held.invert().mul(f.tool()).translate(.5f,.5f,.5f).mul(display.invert()));
    }
    /** Multiplies {@code m} by a move, a turn and an even scale (so the normals only turn). */
    private static void transform(MatrixStack m,Matrix4f a){
        Vector3f move=a.getTranslation(new Vector3f());float scale=a.getScale(new Vector3f()).x;
        m.translate(move.x,move.y,move.z);m.multiply(a.getUnnormalizedRotation(new Quaternionf()).normalize());m.scale(scale,scale,scale);
    }

    private static MotionPlayerModel normal,slim;
    /**
     * First person: both forearms on the handle (the upper arms, close to the eye, would fill the picture as the fists
     * go up, so only the forearms are drawn, as many games show them) and the pickaxe, drawn from the eye.
     */
    public static void renderFirstPerson(AbstractClientPlayerEntity p,float delta,MatrixStack m,VertexConsumerProvider vertices,int light){
        var v=view(swing(p,delta));
        m.push();
        // the neck's model pixels to the view: 2.16 px under the eye and 1 px behind it, turned to face the screen
        m.translate(0,-.135,.06);m.scale(-1,-1,1);
        if(!p.isInvisible()){
            boolean thin="slim".equals(p.getModel());
            if(thin&&slim==null)slim=new MotionPlayerModel(true);
            if(!thin&&normal==null)normal=new MotionPlayerModel(false);
            var model=thin?slim:normal;
            MotionRig.reset(model);
            pose(model.rightArm,v.rightShoulder(),v.rightArm());pose(model.leftArm,v.leftShoulder(),v.leftArm());
            model.overlays();
            var skin=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(p.getSkinTexture()));
            forearm(model.rightArm,m,skin,light);forearm(model.leftArm,m,skin,light);
            if(p.isPartVisible(PlayerModelPart.RIGHT_SLEEVE))forearm(model.rightSleeve,m,skin,light);
            if(p.isPartVisible(PlayerModelPart.LEFT_SLEEVE))forearm(model.leftSleeve,m,skin,light);
        }
        transform(m,v.tool());
        m.translate(.5f,.5f,.5f);
        MinecraftClient.getInstance().getItemRenderer().renderItem(p,p.getMainHandStack(),ModelTransformationMode.NONE,false,m,vertices,p.getWorld(),light,OverlayTexture.DEFAULT_UV,p.getId());
        m.pop();
    }
    /** An arm's lower segment only, where the posed arm puts it. */
    private static void forearm(net.minecraft.client.model.ModelPart arm,MatrixStack m,net.minecraft.client.render.VertexConsumer skin,int light){
        m.push();arm.rotate(m);arm.getChild("elbow").render(m,skin,light,OverlayTexture.DEFAULT_UV);m.pop();
    }
    private static void pose(net.minecraft.client.model.ModelPart arm,Vector3f shoulder,float[] a){
        arm.pivotX=shoulder.x;arm.pivotY=shoulder.y;arm.pivotZ=shoulder.z;
        arm.pitch=a[0]*RAD;arm.yaw=a[1]*RAD;arm.roll=a[2]*RAD;arm.getChild("elbow").pitch=a[3]*RAD;
    }
}
