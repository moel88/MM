package dev.sangwoo.celestialfall.client.stand;

import com.google.gson.JsonParser;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gold Experience's tree of life as it is drawn (2.152.0): the stand down on its knee, its palm on the ground as the server
 * says, kneeling through the burst and up again; smooth at 60 fps, stopped short at any moment; the tree growing branch by
 * branch out of the one before, all grown as the server says, withering back into the ground, its jolt coming in smoothly.
 */
class GoldTreeTest {
    static final float FRAME=1/60f;
    static GoldTree.Tree tree(){return new GoldTree.Tree(0,0,0,30,new Vec3d(0,64,0));}
    private static List<String> bones()throws Exception{
        try(var in=GoldTreeTest.class.getResourceAsStream("/assets/celestialfall/geo/entity/gold_experience.geo.json")){
            var list=new ArrayList<String>();
            for(var b:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))
                list.add(b.getAsJsonObject().get("name").getAsString());
            return list;
        }
    }
    /** Runs a tree's stand frame by frame from at rest beside its user; no bone or place jumps, and it is at rest again after. */
    private void watch(GoldTree.Tree t,float until,String what)throws Exception{
        var names=bones();var poses=new GoldExperiencePoses();var look=new GoldExperienceLook();var shape=new GoldRush.Shape();
        float[] scratch=new float[NAMES.size()],q=new float[6];float[][] last=new float[names.size()][];
        float engage=0,pin=1;boolean shown=false;
        for(float a=0;a<=until;a+=FRAME){
            float delta=GoldRushTest.clock(a);
            look.only(IDLE);
            boolean on=GoldTree.pose(t,delta,look,shape,scratch);shown|=on;
            if(!on){shape.engage=0;shape.pin=0;}
            float sum=0;for(float w:look.weights){assertTrue(w>=-1e-5,what+" at "+a);sum+=w;}
            assertEquals(1,sum,1e-4,"weights at "+a+" ("+what+")");
            for(int b=0;b<names.size();b++){
                poses.blend(names.get(b),look.weights,a,q);
                if(last[b]!=null)assertTrue(GoldRushTest.turn(last[b],q)<20,names.get(b)+" turns "+GoldRushTest.turn(last[b],q)+" degrees in a frame at "+a+" ("+what+")");
                last[b]=q.clone();
            }
            assertTrue(Math.abs(shape.engage-engage)<.2,"it jumps round at "+a+" ("+what+")");
            if(on)assertTrue(Math.abs(shape.pin-pin)<.2,"it jumps off the ground at "+a+" ("+what+")");
            engage=shape.engage;if(on)pin=shape.pin;
        }
        assertTrue(shown,what);
        assertEquals(0,engage,.02,"back at its user's shoulder ("+what+")");
        assertEquals(1,look.weights[IDLE],.02,"at rest again ("+what+")");
    }
    @Test void itKneelsWithItsPalmOnTheGroundAsTheServerSaysAndGetsUpAfterTheBurst()throws Exception{
        assertEquals(GoldExperienceRules.TREE_TOUCH/20f,GoldTree.TOUCH,1e-6);assertEquals(GoldExperienceRules.TREE_BURST/20f,GoldTree.BURST,1e-6);
        var t=tree();
        assertEquals(0,GoldTree.kneel(t,0),1e-6);
        assertEquals(1,GoldTree.kneel(t,GoldTree.TOUCH),1e-5,"down with its palm on the ground as the server's touch comes");
        for(float a=GoldTree.TOUCH;a<=GoldTree.RISE_FROM;a+=.01f)assertEquals(1,GoldTree.kneel(t,a),1e-5,"kneeling through the burst, at "+a);
        assertEquals(1,GoldTree.pinned(t,GoldTree.BURST),1e-6,"where it touched the ground as the tree bursts up");
        assertEquals(0,GoldTree.kneel(t,GoldTree.RISE_FROM+GoldTree.RISE),1e-5,"up again");
        assertEquals(0,GoldTree.pinned(t,GoldTree.RISE_FROM+GoldTree.RISE),1e-5,"and back to its user");
        float[] w=new float[NAMES.size()];GoldTree.stances(t,GoldTree.TOUCH,w);assertEquals(1,w[GROUND],1e-5);assertEquals(0,w[IDLE],1e-5);
        watch(tree(),GoldTree.RISE_FROM+GoldTree.LEAVE+GoldTree.RETURN+.3f,"the whole of it");
    }
    @Test void stoppedShortAtAnyMomentItGetsUpWithoutAJump()throws Exception{
        for(float at:new float[]{0,.1f,.2f,.34f,.45f}){
            var t=tree();t.stopped=at;
            watch(t,at+GoldTree.LEAVE+GoldTree.RETURN+GoldTree.CUT+.3f,"stopped at "+at);
            assertEquals(0,GoldTree.kneel(t,at+GoldTree.CUT),1e-4,"up "+GoldTree.CUT+" s after it was stopped at "+at);
        }
    }
    @Test void itsUserIsHeldUntilTheTreeBurstsUp(){
        var t=tree();
        assertTrue(GoldTree.shows(t,0));
        // held until the burst is heard (or a moment past the server's own tick if it is not)
        t.burst=GoldTree.BURST;
        assertFalse(GoldTree.shows(t,GoldTree.RISE_FROM+GoldTree.LEAVE+GoldTree.RETURN+.01f));
    }

    // ───────────────────────────── the tree ─────────────────────────────

    @Test void theTreeGrowsBranchByBranchOutOfTheOneBeforeAndIsGrownAsTheServerSays(){
        assertEquals(GoldExperienceRules.TREE_GROWN-GoldExperienceRules.TREE_BURST,GoldTreeGrowth.GROWN,1e-6);
        assertFalse(GoldTreeGrowth.GROWERS.isEmpty());
        Map<Integer,Integer> depths=new HashMap<>();
        for(var g:GoldTreeGrowth.GROWERS){
            depths.merge(g.depth(),1,Integer::sum);
            assertEquals(0,GoldTreeGrowth.grown(0,g.depth(),g.order()),1e-6,g.bone()+" not before the burst");
            assertEquals(1,GoldTreeGrowth.grown(GoldTreeGrowth.GROWN,g.depth(),g.order()),1e-6,g.bone()+" grown by the server's TREE_GROWN");
            // never far past its length as it shoots out
            for(double a=0;a<=GoldTreeGrowth.GROWN;a+=.05)assertTrue(GoldTreeGrowth.grown(a,g.depth(),g.order())<1.12f,g.bone()+" at "+a);
        }
        for(int d=0;d<=3;d++)assertTrue(depths.getOrDefault(d,0)>0,"branches at depth "+d);
        // each depth begins only once the one it grows out of is well on its way
        for(int d=1;d<=3;d++)assertTrue(GoldTreeGrowth.FROM[d]>=GoldTreeGrowth.FROM[d-1]+.5*GoldTreeGrowth.TAKES[d-1]);
        // the trunk bursts up first and fast
        assertTrue(GoldTreeGrowth.grown(1.2,0,0)>.75f);
    }
    @Test void everyBranchGrowsAndWithersWithoutAJumpAt60fps(){
        double wither=(GoldExperienceRules.TREE_WITHER-GoldExperienceRules.TREE_BURST);
        float[] s0=new float[2],s1=new float[2];
        for(var g:GoldTreeGrowth.GROWERS){
            double last=Double.NaN;
            for(double a=0;a<=GoldExperienceRules.TREE_END-GoldExperienceRules.TREE_BURST;a+=20*FRAME){
                double s=GoldTreeGrowth.size(a,wither,g.depth(),g.order());
                // the trunk bursts up hard (most in its first frames); a branch is out within a few frames
                if(!Double.isNaN(last))assertTrue(Math.abs(s-last)<(g.depth()==0?.45:.3),g.bone()+" jumps "+(s-last)+" at "+a);
                last=s;
                GoldTreeGrowth.sway(a,g.depth(),g.order(),s0);GoldTreeGrowth.sway(a+20*FRAME,g.depth(),g.order(),s1);
                assertTrue(Math.abs(s1[0]-s0[0])<.5f&&Math.abs(s1[1]-s0[1])<.5f,g.bone()+" stirs with a jolt at "+a);
            }
            assertTrue(GoldTreeGrowth.size(wither+GoldTreeGrowth.WITHER,wither,g.depth(),g.order())<=.0011f,g.bone()+" back in the ground by the server's TREE_END");
        }
        assertEquals(0,GoldTreeGrowth.roots(wither+GoldTreeGrowth.WITHER,wither),1e-6);assertEquals(1,GoldTreeGrowth.roots(10,wither),1e-6);
        // withering: the twigs go first, the trunk last
        double mid=wither+GoldTreeGrowth.WITHER*.4;
        assertTrue(GoldTreeGrowth.left(mid,wither,3)<GoldTreeGrowth.left(mid,wither,0));
        // early: its maker gone, it withers from then
        assertTrue(GoldTreeGrowth.size(30+GoldTreeGrowth.WITHER,30,0,0)<=.0011f);
    }
    @Test void aBlowTurnedBackJoltsItWithoutAJump(){
        assertEquals(0,GoldTreeGrowth.jolt(Double.NaN,1),1e-6);assertEquals(0,GoldTreeGrowth.jolt(0,1),1e-6);
        double last=0,most=0;
        for(double s=0;s<=1.1;s+=FRAME){
            double j=GoldTreeGrowth.jolt(s,3);most=Math.max(most,Math.abs(j));
            assertTrue(Math.abs(j-last)<6,"jolts "+(j-last)+" degrees in a frame at "+s);last=j;
        }
        assertTrue(most>3,"it jolts ("+most+")");
        assertEquals(0,GoldTreeGrowth.jolt(1.1,3),1e-6);
    }
    /**
     * Its user is never hidden behind it (2.152.0, the first filming: flung three blocks on past it, they stood for its six
     * seconds behind its trunk from the camera behind them): drawn see-through where its trunk stands between a camera and
     * the body it follows, whole where it stands beyond the body, off the line, or under the line; not as it bursts up;
     * and never a jump between frames, however fast the camera swings or the body falls.
     */
    @Test void itsUserIsNeverHiddenBehindIt(){
        double grown=40;
        // the tree at the origin, its user 3 blocks on (+z), the camera 4.46 behind them, a little over their head
        double by=1.0,cy=2.7;
        assertTrue(GoldTreeGrowth.opacity(0,0,0,0,cy,3-4.46,0,by,3,grown)<=GoldTreeGrowth.SEE_MIN+1e-4,"behind the trunk from the camera: see-through");
        // the camera turned round in front of them (the tree beyond their body): all of it
        assertEquals(1,GoldTreeGrowth.opacity(0,0,0,0,cy,3+4.46,0,by,3,grown),1e-6);
        // well off to the side of the line: all of it
        assertEquals(1,GoldTreeGrowth.opacity(0,0,0,4,cy,3-4.46,4,by,3,grown),1e-6);
        // the line passing high over its forks (they are in the air above it): all of it
        assertEquals(1,GoldTreeGrowth.opacity(0,0,0,0,10.7,-2.1,0,9,2.4,grown),1e-6);
        // one standing right before its trunk with the camera behind them, the tree at their back: all of it
        assertEquals(1,GoldTreeGrowth.opacity(0,0,0,0,cy,-1-4.46,0,by,-1,grown),1e-6);
        // as it bursts up, its user just flung up over it: all of it (the burst is the thing to see)
        assertEquals(1,GoldTreeGrowth.opacity(0,0,0,0,cy+1.5,.6-4.46,0,by+1.5,.6,3),1e-6);
        // the camera swinging round them a frame at a time (a turn in two seconds), what shows eased: no jump
        float shown=Float.NaN;double most=0;
        for(int k=0;k<=240;k++){
            double a=k*Math.PI*2/120,cx=-4.6*Math.sin(a),cz=3-4.6*Math.cos(a);
            float should=GoldTreeGrowth.opacity(0,0,0,cx,cy,cz,0,by,3,grown),next=GoldTreeGrowth.eased(shown,should,1/3.0);
            if(!Float.isNaN(shown))most=Math.max(most,Math.abs(next-shown));shown=next;
        }
        assertTrue(most<.08,"a camera swinging round: at most "+most+" a frame");
        // its user flung up and on and down behind it, the camera behind them, a frame at a time (the game's leap, 3 frames a tick)
        double v=GoldExperienceRules.leapSpeed(GoldExperienceRules.TREE_RISE),y=0,z=0,f=GoldExperienceRules.treeLeap()[0];
        shown=Float.NaN;most=0;float least=1,early=1;
        for(int tick=0;tick<80;tick++){
            double y0=y,z0=z;
            if(tick==0||y>0){y=Math.max(0,y+v);v=(v-GoldExperienceRules.GRAVITY)*GoldExperienceRules.DRAG;z+=f;f*=tick==0?GoldExperienceRules.GRIP:GoldExperienceRules.AIR;}
            for(int k=0;k<3;k++){
                double u=k/3.0,up=y0+(y-y0)*u,on=z0+(z-z0)*u,age=tick+u;
                float should=GoldTreeGrowth.opacity(0,0,0,0,up+2.7,on-4.46,0,up+1,on,age),next=GoldTreeGrowth.eased(shown,should,1/3.0);
                if(!Float.isNaN(shown))most=Math.max(most,Math.abs(next-shown));shown=next;least=Math.min(least,shown);
                if(age<20)early=Math.min(early,shown);
            }
        }
        assertTrue(most<.08,"the leap: at most "+most+" a frame");
        assertEquals(1,early,1e-4,"all of it shows as it bursts up and its user goes up over it");
        assertTrue(least<GoldTreeGrowth.SEE_MIN+.02,"down behind it, they are seen through it ("+least+")");
    }
    /**
     * Its model is the pictures' tree (2.152.0, "나무는 이렇게 생겼어"): all branches, about three times a body's height and
     * nearly as wide, a few small leaves cut to their shape; every branch that grows is a bone of it, every face on its texture.
     */
    @Test void itsModelIsThePicturesBranchyTree()throws Exception{
        com.google.gson.JsonObject geo,shape;
        try(var in=getClass().getResourceAsStream("/assets/celestialfall/geo/entity/gold_tree.geo.json")){geo=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();}
        try(var in=getClass().getResourceAsStream("/assets/celestialfall/stands/gold_tree_shape.json")){shape=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}
        java.awt.image.BufferedImage tex;
        try(var in=getClass().getResourceAsStream("/assets/celestialfall/textures/entity/gold_tree.png")){tex=javax.imageio.ImageIO.read(in);}
        var d=geo.getAsJsonObject("description");assertEquals(tex.getWidth(),d.get("texture_width").getAsInt());assertEquals(tex.getHeight(),d.get("texture_height").getAsInt());
        var names=new java.util.HashSet<String>();int leaves=0,clear=0,green=0;
        for(var b:geo.getAsJsonArray("bones")){
            var o=b.getAsJsonObject();names.add(o.get("name").getAsString());
            if(!o.has("cubes"))continue;
            for(var c:o.getAsJsonArray("cubes"))for(var f:c.getAsJsonObject().getAsJsonObject("uv").entrySet()){
                var uv=f.getValue().getAsJsonObject();int x=uv.getAsJsonArray("uv").get(0).getAsInt(),y=uv.getAsJsonArray("uv").get(1).getAsInt(),w=uv.getAsJsonArray("uv_size").get(0).getAsInt(),h=uv.getAsJsonArray("uv_size").get(1).getAsInt();
                assertTrue(x>=0&&y>=0&&x+w<=tex.getWidth()&&y+h<=tex.getHeight(),"a face of "+o.get("name")+" off its texture");
                // a leaf's broad face: cut to a leaf's shape (clear round it), green in it
                if(w>=12&&h>=18&&c.getAsJsonObject().getAsJsonArray("size").get(2).getAsDouble()<.5){
                    leaves++;
                    for(int j=0;j<h;j++)for(int i=0;i<w;i++){int p=tex.getRGB(x+i,y+j);if(p>>>24==0)clear++;else if((p>>8&255)>(p>>16&255)+20)green++;}
                }
            }
        }
        for(var g:GoldTreeGrowth.GROWERS)assertTrue(names.contains(g.bone()),"the branch "+g.bone()+" is a bone");
        assertTrue(names.contains("roots"));
        double high=shape.get("height").getAsDouble()/16,across=shape.get("across").getAsDouble()/16;
        assertTrue(high>5.5&&high<7.8,"about three times a body's height: "+high+" blocks");
        assertTrue(across>2&&across<3.2,"nearly as wide: "+across+" blocks out from its middle");
        assertTrue(leaves>=30,"leaves "+leaves);assertTrue(clear>0&&green>clear/4,"leaves cut to shape: "+clear+" clear, "+green+" green");
        assertTrue(shape.getAsJsonArray("tips").size()>=16,"twigs");
        // it leans a little the way its maker faced (its front, -z) and its leader goes on up
        var lead=shape.getAsJsonArray("leader");var first=lead.get(0).getAsJsonArray();var last=lead.get(lead.size()-1).getAsJsonArray();
        assertTrue(last.get(2).getAsDouble()<first.get(2).getAsDouble()-4,"it leans forward");assertTrue(last.get(1).getAsDouble()>60);
    }
}
