package dev.sangwoo.celestialfall.client.title;
import java.util.*;

/** Bounded, frame-rate independent state. A touch never changes a button's hit box. */
public final class TitleMotion {
    private double elapsed,exit=-1,mouseX,mouseY;
    private final Map<String,Double> hovers=new HashMap<>();
    public void reset(){elapsed=0;exit=-1;mouseX=mouseY=0;hovers.clear();}
    public static double ease(double t){t=Math.max(0,Math.min(1,t));return 1-Math.pow(1-t,3);}
    public void step(double delta,boolean animated,double x,double y,Set<String> hovered){
        double dt=Math.max(0,Math.min(.05,Double.isFinite(delta)?delta:0));elapsed+=dt;
        if(exit>=0)exit+=dt;
        double f=animated?1-Math.exp(-dt*14):1;
        mouseX+=(Math.max(-1,Math.min(1,x))-mouseX)*f;mouseY+=(Math.max(-1,Math.min(1,y))-mouseY)*f;
        for(String id:List.of("play","servers","credits","back","cancel","confirm_leave","confirm_servers"))
            hovers.compute(id,(k,v)->(v==null?0:v)+((hovered.contains(id)?1:0)-(v==null?0:v))*f);
    }
    public double time(boolean animated){return animated?elapsed:0;}
    public double intro(boolean animated){return animated?ease(elapsed/.55):1;}
    public double hover(String id){return hovers.getOrDefault(id,0.0);}
    public double mx(boolean animated){return animated?mouseX:0;}
    public double my(boolean animated){return animated?mouseY:0;}
    public boolean startExit(){if(exit>=0)return false;exit=0;return true;}
    public boolean leaving(){return exit>=0;}
    public boolean exitReady(boolean animated){return exit>=0&&(!animated||exit>=.16);}
    public double exitAlpha(boolean animated){return exit<0?0:animated?ease(exit/.16):1;}
}
