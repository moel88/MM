package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.StarPlatinumLook;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
import dev.sangwoo.celestialfall.stand.StarPlatinumStance;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Star Platinum's punches (2.54.0): smooth at 60 fps through a mashed combo, landing on their contacts, the stand coming round and going back. */
class StandPunchTest {
    static final float FRAME=1/60f;
    /** A mashed combo: each punch as soon as it may (seconds). */
    static final float[] STARTS={0,StarPlatinumRules.STEP[1]/20f,(StarPlatinumRules.STEP[1]+StarPlatinumRules.STEP[2])/20f,(StarPlatinumRules.STEP[1]+StarPlatinumRules.STEP[2]+StarPlatinumRules.STEP[3])/20f};
    private List<String> bones()throws Exception{
        try(var in=getClass().getResourceAsStream("/assets/celestialfall/geo/entity/star_platinum.geo.json")){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    static StandPunch.Punch punch(int k){return new StandPunch.Punch(k,0,Float.NaN,Float.NaN);}
    /** How far (degrees) a bone turns from one pose to another: the angle between the two whole turns, its own turn in the model included. */
    static float turn(String bone,float[] a,float[] b){
        var rest=StarPlatinumPoses.rests().getOrDefault(bone,new float[6]);
        double[] qa=StarPlatinumPoses.quat(rest[0]+a[0],rest[1]+a[1],rest[2]+a[2],new double[4]),qb=StarPlatinumPoses.quat(rest[0]+b[0],rest[1]+b[1],rest[2]+b[2],new double[4]);
        double dot=Math.abs(qa[0]*qb[0]+qa[1]*qb[1]+qa[2]*qb[2]+qa[3]*qb[3]);
        return (float)Math.toDegrees(2*Math.acos(Math.min(1,dot)));
    }
    /** The shape at {@code t} seconds into a combo whose punches start at {@code starts}, as the client takes the newest and the one before. */
    static StandPunch.Shape at(float t,float[] starts,StandPunch.Shape into){
        int n=-1;for(int i=0;i<starts.length;i++)if(t>=starts[i])n=i;
        if(n<0)return null;
        return StandPunch.at(punch(n+1),t-starts[n],n>0?punch(n):null,n>0?t-starts[n-1]:0,into);
    }
    /** The look drawn: at rest, taken over by the punches as far as the stand has come round. */
    static void look(StandPunch.Shape s,StarPlatinumLook look){
        look.only(StarPlatinumStance.IDLE);if(s==null)return;
        for(int i=0;i<look.weights.length;i++)look.weights[i]=look.weights[i]*(1-s.engage)+s.weights[i]*s.engage;
        look.wind=.35f*s.engage;look.gust=s.gust;
    }

    @Test void aMashedComboMovesWithoutAJumpAt60fps()throws Exception{
        var poses=new StarPlatinumPoses();var names=bones();var look=new StarPlatinumLook();var shape=new StandPunch.Shape();
        float[][] last=new float[names.size()][];float[] p=new float[6];float fastest=0;String where="";
        float lastEngage=0,lastYaw=0;
        for(int f=0;f<=(int)(5.5f/FRAME);f++){
            float t=f*FRAME;var s=at(t,STARTS,shape);look(s,look);
            float sum=0;for(float w:look.weights)sum+=w;assertEquals(1,sum,1e-4,"weights at "+t);
            if(s!=null){
                assertTrue(Math.abs(s.engage-lastEngage)<.25f,"comes round in a jump at "+t);
                lastEngage=s.engage;
            }
            for(int b=0;b<names.size();b++){
                poses.pose(names.get(b),look,t,p);
                if(last[b]!=null){
                    float d=turn(names.get(b),last[b],p);
                    if(d>fastest){fastest=d;where=names.get(b)+" at "+t;}
                    // a punch is fast (an arm swung through 60-100 degrees in a tenth of a second, at its fastest as it
                    // lands, which the afterimages smear), but nothing ever jumps
                    assertTrue(d<30,names.get(b)+" turns "+d+" degrees in a frame at "+t);
                }
                last[b]=p.clone();
            }
        }
        assertTrue(fastest>12,"the punches are fast: "+fastest+" ("+where+")");
    }
    @Test void everyPunchLandsOnItsContactAndHoldsStillThere(){
        var shape=new StandPunch.Shape();
        for(int k=1;k<=4;k++){
            float hit=StandPunch.HIT[k];
            assertEquals(StarPlatinumRules.CONTACT[k]/20f,hit,1e-6);
            StandPunch.at(punch(k),hit,null,0,shape);
            assertEquals(1,shape.weights[StandPunch.STRIKES[k]],1e-5,"punch "+k+" thrown at its contact");
            // the hit-stop: still for its frames
            var held=StandPunch.at(punch(k),hit+StandPunch.FREEZE[k]*.9f,null,0,new StandPunch.Shape());
            for(int i=0;i<shape.weights.length;i++)assertEquals(shape.weights[i],held.weights[i],1e-5,"punch "+k+" still through its hit-stop");
            // still loading a moment before: the strike is quickest as it lands
            StandPunch.at(punch(k),hit-.05f,null,0,shape);
            assertTrue(shape.weights[StandPunch.STRIKES[k]]<.6f,"punch "+k+" lands at the end of its strike: "+shape.weights[StandPunch.STRIKES[k]]);
            // and back in the guard before the next may start
            if(k<4){StandPunch.at(punch(k),StarPlatinumRules.STEP[k]/20f,null,0,shape);assertEquals(1,shape.weights[StarPlatinumStance.GUARD],1e-4,"punch "+k+" back in its guard");}
        }
    }
    @Test void itComesRoundToTheFrontAndGoesBackOnceThePunchesStop(){
        var shape=new StandPunch.Shape();
        assertEquals(0,at(0,STARTS,shape).engage,1e-6,"at its user's shoulder as the first punch starts");
        assertEquals(1,at(StandPunch.RISE,STARTS,shape).engage,1e-6,"in front before the first lands");
        for(float t=StandPunch.RISE;t<STARTS[3]+1.2f;t+=.05f)assertEquals(1,at(t,STARTS,shape).engage,1e-6,"in front through the combo at "+t);
        float end=STARTS[3]+StandPunch.length(punch(4));
        assertEquals(0,at(end,STARTS,shape).engage,1e-4,"back at its shoulder once it is over");
        // a punch stopped short (its user struck) goes back to the guard and the stand goes back soon after
        var stopped=punch(2).stop(.2f);
        StandPunch.at(stopped,.2f+.25f,null,0,shape);assertEquals(1,shape.weights[StarPlatinumStance.GUARD],1e-4);
        assertEquals(0,StandPunch.at(stopped,StandPunch.length(stopped)-.2f,null,0,shape).engage,1e-3);
        assertEquals(0,StandPunch.ghost(stopped,StandPunch.HIT[2]),1e-6,"no afterimages for a punch that never landed");
    }
    @Test void itStandsWhereItsFistReachesTheTarget(){
        // nothing in its way: a little ahead of its user
        assertEquals(StandPunch.AHEAD,StandPunch.ahead(punch(1)),1e-6);
        for(int k=1;k<=4;k++){
            var p=new StandPunch.Punch(k,0,2.6f,Float.NaN);
            float[] fist=StandPunch.fist(k);
            // standing to the right of the line, the fist (with the lunge) reaches the target's middle less its
            // half width, sinking a little into it
            float ahead=StandPunch.ahead(p),reach=fist[0]+StandPunch.LUNGE[k];
            double toMiddle=Math.hypot(2.6f+StandPunch.HALF-ahead,StandPunch.SIDE);
            assertEquals(toMiddle,reach+StandPunch.HALF-StandPunch.BITE,1e-4,"punch "+k);
            assertTrue(fist[0]>.7f&&fist[2]>1.2f&&fist[2]<1.9f,"punch "+k+" reaches ahead at head height: "+java.util.Arrays.toString(fist));
        }
        // too near or too far: it stays within reach of its user
        assertEquals(StandPunch.AHEAD_MIN,StandPunch.ahead(new StandPunch.Punch(1,0,.2f,Float.NaN)),1e-6);
        assertEquals(StandPunch.AHEAD_MAX,StandPunch.ahead(new StandPunch.Punch(1,0,9,Float.NaN)),1e-6);
    }
}
