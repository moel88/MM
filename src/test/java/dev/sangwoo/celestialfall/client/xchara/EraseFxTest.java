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
 * X Chara's terrain delete once its grounds are marked, as drawn (2.133.0, the user's: "하나는 바라보는 방향에 있는 10 × 10 ×
 * 10 블록 위에 거대한 !!!가 뜨게하고 나머지는 근처 플레이어 위치에 !!!가 각각 3개 뜨게하고 공중으로 뜨게"; and before: "참격이
 * 맞은 땅은 덩어리째 떠오르다가 글리치 폭발되게 그리고 폭발되고 10초 이따가 땅이 다시 돌아오게"): a giant "!!!" over the ground
 * looked at and a "!!!" over each of the others (over the head of whom it is under) until it rises; each cube is its
 * ground's blocks, its outside only, rising as the server's does (a tick behind, as the bodies on it are drawn), glitching
 * as one piece more and more toward its burst; where it was shows a void from as it clears the ground until the ground is
 * swept back at EraseRules.BACK.
 */
class EraseFxTest {
    static EraseClient.Call call(EraseClient.Ground... grounds){
        UUID id=new UUID(11,12),owner=new UUID(13,14);
        var cast=new StandClient.Cast(id,owner,new Identifier("minecraft","overworld"),EraseRules.SKILL,0,1000,1000,0,new Vec3d(0,64,0),0,List.of());
        return new EraseClient.Call(id,owner,new Vec3d(0,64,0),cast,List.of(grounds));
    }
    static EraseClient.Ground ground(double x,double z,double top,int kind){return new EraseClient.Ground(EraseRules.corner(x),EraseRules.corner(z),top,kind,-1,1234+kind);}
    @Test void theGiantBangsIsOverTheGroundLookedAtTheOthersOverTheirOwn(){
        var look=ground(0,12,64,EraseRules.LOOK);var near=ground(15,3,64,EraseRules.PLAYER);
        assertTrue(look.looked()&&!near.looked());
        assertEquals(4,EraseFx.GIANT,1e-9,"giant: four times G's (the user's \"거대한 !!!\")");
        Vec3d a=look.bang,b=near.bang;
        assertEquals(look.middle().x,a.x,1e-9);assertEquals(look.middle().z,a.z,1e-9);
        assertEquals(64+EraseFx.GIANT_OVER,a.y,1e-9,"over its ground, higher than the others");
        assertEquals(64+EraseFx.BANG_OVER,b.y,1e-9);assertTrue(a.y>b.y);
        // shown from the mark until the ground rises, gone over the few ticks round that, with no jump
        assertEquals(0,EraseFx.marked(-1),1e-9);assertEquals(1,EraseFx.marked(0),1e-9);assertEquals(1,EraseFx.marked(EraseRules.WARN-1),1e-9);
        assertEquals(0,EraseFx.marked(EraseRules.WARN+3),1e-9,"gone as it rises");
        float last=1;for(float t=0;t<=EraseRules.WARN+4;t+=.05f){float k=EraseFx.marked(t);assertTrue(k<=last+1e-6&&last-k<.03f,"fading, no jump at "+t);last=k;}
    }
    @Test void theCubeRisesAsTheServersATickBehindAndGlitchesMoreTowardItsBurst(){
        var g=ground(.4,12.3,64,EraseRules.RANDOM);
        assertEquals(64-EraseRules.SIDE,EraseFx.base(g),"its lowest blocks ten under its ground's top");
        assertEquals(EraseRules.RISE_FROM,EraseFx.cubeTicks(EraseRules.RISE_FROM+1),1e-6,"a tick behind its clock");
        assertNull(EraseFx.corner(g,EraseRules.RISE_FROM-1,Vec3d.ZERO),"not drawn until it rises");
        Vec3d c=EraseFx.corner(g,EraseRules.RISE_FROM+60,Vec3d.ZERO);
        assertEquals(EraseFx.base(g)+EraseRules.lift(EraseRules.RISE_FROM+60),c.y,.01);
        assertEquals(g.x0,c.x,1e-9);assertEquals(g.z0,c.z,1e-9);
        assertNull(EraseFx.corner(g,EraseRules.BURST,Vec3d.ZERO),"none once it has burst");
        float rising=EraseFx.tension(EraseRules.RISE_FROM+40),waiting=EraseFx.tension(EraseRules.RISE_FROM+EraseRules.RISE_TICKS+60),late=EraseFx.tension(EraseRules.BURST-1);
        assertTrue(rising>waiting&&late>.9f,rising+" "+waiting+" "+late);
        float last=0;for(float t=EraseRules.BURST-100;t<EraseRules.BURST;t++){float k=EraseFx.tension(t);assertTrue(k>=last-1e-6,"more and more toward its burst");last=k;}
        int moved=0,steps=0;
        for(float sec=8;sec<10;sec+=1/12f){steps++;boolean any=false;
            for(int y=0;y<EraseRules.SIDE;y++){Vec3d d=EraseFx.slice(y,sec,g.seed,.06f);assertTrue(d.length()<=.7,"a little aside");if(d.lengthSquared()>0)any=true;}
            if(any)moved++;}
        assertTrue(moved<steps/3,"now and then only, as it waits: "+moved+" of "+steps);
        // drawn while it is up, not cut short
        var four=call(g);
        assertFalse(EraseFx.showsCube(four,EraseRules.RISE_FROM,EraseRules.RISE_FROM-1));assertTrue(EraseFx.showsCube(four,EraseRules.RISE_FROM+2,EraseRules.RISE_FROM+1));
        four.cut=EraseRules.RISE_FROM+10;assertFalse(EraseFx.showsCube(four,EraseRules.RISE_FROM+12,EraseRules.RISE_FROM+11),"cut short: gone");
    }
    @Test void itsFacesAreItsOutsideOnly(){
        var g=ground(0,12,64,EraseRules.LOOK);
        var f=EraseFx.faces(g,null);
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
    @Test void itShakesAsTheCubesBreakFreeAndHardestAsTheyBurst(){
        var c=call(ground(0,12,64,EraseRules.LOOK));
        float rise=EraseFx.shake(c,EraseFx.RISE0+.02f),burst=EraseFx.shake(c,EraseFx.BURST_S+.07f),before=EraseFx.shake(c,.5f);
        assertTrue(before<1e-3,"nothing through the warning");assertTrue(rise>.2f);assertTrue(burst>rise*3,"hardest as they burst");
        c.cut=EraseRules.RISE_FROM-5;assertEquals(0,EraseFx.shake(c,EraseFx.BURST_S+.07f),1e-9,"cut short: none");
    }
}
