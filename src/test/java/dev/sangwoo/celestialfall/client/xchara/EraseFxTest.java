package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X Chara's delete slash once it is thrown, as drawn (2.133.0, the user's: "일단 공중에 떠올라서 참격을 세로로 날리면 바닥에
 * 맞은 부분만 삭제되게 … 한 번 날리고 그냥 바닥에 박히고 삭제되게 그리고 삭제된 부분은 한덩어리로 글리치", "참격이 맞은 땅은
 * 덩어리째 떠오르다가 글리치 폭발되게 그리고 폭발되고 10초 이따가 땅이 다시 돌아오게"; "내려찍는 면 그대로"): the slash is a
 * crescent upright in the plane it is thrown in, its point leading, flying from the knife to the ground in the server's
 * ticks and standing in it; the cube is its ground's blocks, its outside only, rising as the server's does (a tick behind,
 * as the bodies on it are drawn), glitching as one piece more and more toward its burst; where it was shows a void from as
 * it clears the ground until the ground is swept back at EraseRules.BACK.
 */
class EraseFxTest {
    static EraseClient.Strike strike(Vec3d from,Vec3d to,int fly){
        UUID id=new UUID(11,12),owner=new UUID(13,14);
        float yaw=(float)Math.toDegrees(Math.atan2(-(to.x-from.x),to.z-from.z));
        var cast=new StandClient.Cast(id,owner,new Identifier("minecraft","overworld"),EraseRules.SKILL,0,1000,1000,0,to,yaw,List.of());
        return new EraseClient.Strike(id,owner,from,to,fly,EraseRules.corner(to.x),EraseRules.corner(to.z),cast);
    }
    @Test void itStandsUprightOnItsHeadingTurnedALittleHoweverSteeplyItFlies(){
        var s=strike(new Vec3d(0,70,0),new Vec3d(0,64,12),5);
        assertEquals(1,s.way.length(),1e-9);assertEquals(1,s.upright.length(),1e-9);assertEquals(1,s.side.length(),1e-9);assertEquals(1,s.head.length(),1e-9);
        assertTrue(s.way.y<0&&s.way.z>0,"it flies down and out the way it is thrown");
        assertEquals(0,s.head.y,1e-9,"its heading level");assertTrue(s.head.z>.99,"the way it is thrown");
        assertEquals(0,s.head.dotProduct(s.upright),1e-9,"square to its heading");assertEquals(0,s.head.dotProduct(s.side),1e-9);assertEquals(0,s.upright.dotProduct(s.side),1e-9);
        double turn=Math.toDegrees(Math.acos(s.upright.y));
        assertEquals(EraseFx.ROLL,turn,.5,"upright (the plane the knife came down in), turned about its heading so a little of its face shows behind it");
        // as steep a drop as it gets: still upright, and neither horn swept back toward its user (behind where the knife let it go)
        var steep=strike(new Vec3d(0,70,0),new Vec3d(0,64,2),3);
        assertEquals(EraseFx.ROLL,Math.toDegrees(Math.acos(steep.upright.y)),.5);
        var cr=EraseFx.crescent(steep,steep.from,1);
        for(int i=0;i<EraseFx.N;i++)for(int k=0;k<2;k++)assertTrue(cr[k][i].z>steep.from.z-EraseFx.BOW-EraseFx.THICK,"no horn swept back past its bow at "+i);
    }
    @Test void itFliesFromTheKnifeToTheGroundInTheServersTicksAndStandsInIt(){
        var s=strike(new Vec3d(2,70,1),new Vec3d(5,64,15),5);
        assertEquals(s.from,EraseFx.tip(s,0));
        assertEquals(0,EraseFx.tip(s,s.fly).distanceTo(s.to),1e-9,"its point on the ground as the server strikes");
        Vec3d last=EraseFx.tip(s,0);double lastStep=0;
        for(float a=0;a<=s.fly;a+=.25f){
            Vec3d p=EraseFx.tip(s,a);double step=p.distanceTo(last);
            assertTrue(step>=lastStep-1e-6,"a little faster as it goes");lastStep=step;last=p;
        }
        Vec3d stuck=EraseFx.tip(s,s.fly+10);
        assertEquals(EraseFx.PLUNGE,stuck.distanceTo(s.to),1e-6,"driven into the ground and standing there");
        assertTrue(stuck.y<s.to.y);
        // its point leads: the crescent's middle of its cutting edge is its point, its horns behind and to its upright
        var cr=EraseFx.crescent(s,stuck,1);
        assertEquals(0,cr[1][EraseFx.N/2].distanceTo(stuck),1e-9,"its cutting edge's middle at its point");
        for(int i=0;i<EraseFx.N;i++)assertTrue(cr[1][i].subtract(cr[0][i]).dotProduct(s.head)>=-1e-9,"its cutting edge leading all along");
        Vec3d hornA=cr[1][0],hornB=cr[1][EraseFx.N-1];
        assertEquals(2*EraseFx.HALF,hornA.distanceTo(hornB),1e-6);
        assertTrue(hornB.y>s.to.y+3,"standing up out of the ground: its upper horn high over it");
        assertEquals(0,hornB.subtract(hornA).dotProduct(s.side),1e-6,"flat in its plane");
    }
    @Test void itIsEatenWithinTheMomentItStandsAndGoneIfCutShort(){
        var s=strike(new Vec3d(0,70,0),new Vec3d(0,64,12),5);
        assertEquals(1,EraseFx.kept(s,100),1e-9);
        s.cut=20;assertEquals(1,EraseFx.kept(s,19.9f),1e-6);assertEquals(0,EraseFx.kept(s,20+EraseFx.CUT_FADE),1e-6);
        assertTrue(EraseFx.EAT1<EraseFx.RISE0+.2f,"eaten about as the cube rises");
    }
    @Test void theCubeRisesAsTheServersATickBehindAndGlitchesMoreTowardItsBurst(){
        var s=strike(new Vec3d(0,70,0),new Vec3d(.4,64,12.3),5);
        assertEquals(64-EraseRules.SIDE,EraseFx.base(s),"its lowest blocks ten under its ground's top");
        assertEquals(-(s.fly+1),EraseFx.cubeTicks(s,0),1e-6);
        assertEquals(EraseFx.cubeTicks(s,s.fly+EraseRules.RISE_FROM+1),EraseRules.RISE_FROM,1e-6,"a tick behind its clock");
        assertNull(EraseFx.corner(s,EraseRules.RISE_FROM-1,Vec3d.ZERO),"not drawn until it rises");
        Vec3d c=EraseFx.corner(s,EraseRules.RISE_FROM+60,Vec3d.ZERO);
        assertEquals(EraseFx.base(s)+EraseRules.lift(EraseRules.RISE_FROM+60),c.y,.01);
        assertEquals(s.x0,c.x,1e-9);assertEquals(s.z0,c.z,1e-9);
        assertNull(EraseFx.corner(s,EraseRules.BURST,Vec3d.ZERO),"none once it has burst");
        // how hard it glitches: a little as it rises, a trace as it waits, all of it at its burst
        float rising=EraseFx.tension(EraseRules.RISE_FROM+40),waiting=EraseFx.tension(EraseRules.RISE_FROM+EraseRules.RISE_TICKS+60),late=EraseFx.tension(EraseRules.BURST-1);
        assertTrue(rising>waiting&&late>.9f,rising+" "+waiting+" "+late);
        float last=0;for(float t=EraseRules.BURST-100;t<EraseRules.BURST;t++){float k=EraseFx.tension(t);assertTrue(k>=last-1e-6,"more and more toward its burst");last=k;}
        // its slices slip a little, and only a few layers at once
        int moved=0,steps=0;
        for(float sec=8;sec<10;sec+=1/12f){steps++;boolean any=false;
            for(int y=0;y<EraseRules.SIDE;y++){Vec3d d=EraseFx.slice(y,sec,s.seed,.06f);assertTrue(d.length()<=.7,"a little aside");if(d.lengthSquared()>0)any=true;}
            if(any)moved++;}
        assertTrue(moved<steps/3,"now and then only, as it waits: "+moved+" of "+steps);
    }
    @Test void itsFacesAreItsOutsideOnly(){
        var s=strike(new Vec3d(0,70,0),new Vec3d(0,64,12),5);
        var f=EraseFx.faces(s,null);
        int S=EraseRules.SIDE;assertEquals(6*S*S,f.n,"six faces of ten by ten");
        for(int i=0;i<f.n;i++){
            int x=f.x[i],y=f.y[i],z=f.z[i];
            assertTrue(x==0||x==S-1||y==0||y==S-1||z==0||z==S-1,"on its outside");
            switch(f.dir[i]){case 0->assertEquals(S-1,y);case 1->assertEquals(0,y);case 2->assertEquals(0,z);case 3->assertEquals(S-1,z);case 4->assertEquals(0,x);default->assertEquals(S-1,x);}
            assertNull(f.state[i],"no world: a void");
        }
    }
    @Test void whereItsGroundHasNoBlockItTakesItsColumnsNearest(){
        int S=EraseRules.SIDE;String[] g=new String[S*S*S];
        // a column of stone under dirt under grass with a cave in it; under the world's floor; and one with nothing at all
        for(int y=0;y<S;y++)g[(2*S+y)*S+3]=y==S-1?"grass":y>=S-4?"dirt":"stone";
        g[(2*S+4)*S+3]=null;g[(2*S+5)*S+3]=null;
        assertEquals("stone",EraseFx.nearest(g,2,4,3),"a cave filled from under it");
        assertEquals("dirt",EraseFx.nearest(g,2,5,3),"nearer the dirt over it than the stone under it");
        for(int y=0;y<4;y++)g[(5*S+y)*S+5]=null;for(int y=4;y<S;y++)g[(5*S+y)*S+5]=y==S-1?"grass":"dirt";
        assertEquals("dirt",EraseFx.nearest(g,5,0,5),"under the world's floor: the deepest it has");
        assertNull(EraseFx.nearest(g,7,7,7),"nothing in its column: grass over dirt over stone (EraseFx.filled)");
        assertEquals("grass",EraseFx.nearest(g,2,S-1,3),"its own where it has one");
    }
    @Test void theVoidShowsFromAsItClearsTheGroundUntilTheGroundIsSweptBack(){
        float clear=-1;
        for(float t=EraseRules.RISE_FROM;t<EraseRules.BURST;t++){if(EraseFx.voidShows(t/20f,t)){clear=t;break;}}
        assertTrue(clear>=EraseRules.RISE_FROM&&clear<EraseRules.RISE_FROM+20,"as it begins to rise (under it, out of sight until it clears)");
        assertTrue(EraseFx.voidShows((EraseRules.BURST+40)/20f,EraseRules.BURST+40),"after its burst");
        assertFalse(EraseFx.voidShows(EraseRules.BACK/20f,EraseRules.BACK),"the ground back");
        assertEquals(0,EraseFx.swept((EraseRules.BACK-20)/20f),1e-9);
        assertEquals(1,EraseFx.swept(EraseRules.BACK/20f-.05f),1e-6,"swept over by the time the ground is back");
        float last=0;for(float sec=EraseRules.BACK/20f-1;sec<EraseRules.BACK/20f;sec+=.01f){float k=EraseFx.swept(sec);assertTrue(k>=last-1e-6);last=k;}
    }
    @Test void itsSilhouettesComeAndGoWithinTheHalfSecond(){
        assertEquals(EraseClient.STRUCK,EraseClient.silhouette(0));
        assertEquals(-1,EraseClient.silhouette(EraseClient.VANISH),"themselves again at half a second");
        assertEquals(-1,EraseClient.silhouette(-.01f));
        int shown=0;for(int k=0;k<30;k++)if(EraseClient.silhouette(k/60f)>=0)shown++;
        assertTrue(shown>=5&&shown<=12,"glitching: shown a few frames, gone the rest: "+shown);
    }
}
