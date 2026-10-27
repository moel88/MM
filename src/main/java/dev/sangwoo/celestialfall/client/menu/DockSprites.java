package dev.sangwoo.celestialfall.client.menu;

/** Unmodified user artwork. Atlas rectangles select individual interactive buttons. */
public final class DockSprites {
    public static final int WIDTH=1223, HEIGHT=1286;
    public record Region(int u,int v,int width,int height){}
    public static Region region(String id){return switch(id){
        case "rebirth" -> new Region(130,12,328,268);
        case "bag" -> new Region(157,280,300,236);
        case "achievements" -> new Region(156,516,301,236);
        case "quests" -> new Region(156,752,301,239);
        case "menu" -> new Region(155,991,314,271);
        case "draw" -> new Region(469,991,293,271);
        case "settings" -> new Region(763,991,306,271);
        default -> throw new IllegalArgumentException("Unknown dock button: "+id);
    };}
    public static void add(UiCanvas c,String id,MenuLayout.Rect box){
        var r=region(id);double scale=Math.min(box.w()/r.width(),box.h()/r.height());
        double w=r.width()*scale,h=r.height()*scale;
        c.sprites.add(new UiCanvas.Sprite(box.x()+(box.w()-w)/2,box.y()+(box.h()-h)/2,w,h,r.u(),r.v(),r.width(),r.height()));
    }
    private DockSprites(){}
}
