package dev.sangwoo.celestialfall.client.title;

import dev.sangwoo.celestialfall.client.menu.MenuClient;
import dev.sangwoo.celestialfall.client.MapClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** A minimal welcome screen inside the current Botand map. Play resumes that same map. */
public final class BotandTitleScreen extends Screen {
    private TitleLayout layout;
    private final TitleMotion motion=new TitleMotion();
    private final TitleCinema cinema=new TitleCinema();
    private final List<ActionButton> buttons=new ArrayList<>();
    private String modal="",selected="play";
    private Runnable destination;
    private long lastFrame;
    public BotandTitleScreen(){super(Text.literal("BOMI STAND · 시작 화면"));}
    @Override protected void init(){layout=TitleLayout.of(width,height);motion.reset();destination=null;lastFrame=Util.getMeasuringTimeMs();rebuild();}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public boolean shouldPause(){return false;}
    @Override public void removed(){cinema.close();TitleMapCamera.clear();if(client!=null&&client.world!=null)client.worldRenderer.scheduleTerrainUpdate();super.removed();}
    @Override public void close(){if(!modal.isEmpty()){modal="";rebuild();}}
    private void rebuild(){
        clearChildren();buttons.clear();
        for(var action:modal.isEmpty()?layout.actions():layout.modal(modal)){
            var button=new ActionButton(action);
            button.active=!action.id().equals("servers")||client.isMultiplayerEnabled();
            addDrawableChild(button);buttons.add(button);
        }
        selected=buttons.get(0).action.id();setInitialFocus(buttons.get(0));
    }
    public boolean liveCinema(){return cinema.available();}
    public String modal(){return modal;}
    public TitleLayout layout(){return layout;}
    private void go(Runnable target){if(motion.startExit()){destination=target;for(var b:buttons)b.active=false;}}
    private void action(String id){
        if(destination!=null)return;MenuClient.click();
        switch(id){
            case "play"->go(()->{if(MapClient.enabled()){KeyBinding.unpressAll();client.setScreen(null);}});
            case "servers"->{if(client.isMultiplayerEnabled()){modal="servers";rebuild();}}
            case "credits"->{modal="credits";rebuild();}
            case "confirm_leave"->go(()->TitlePortal.leaveMap(client,false));
            case "confirm_servers"->{if(client.isMultiplayerEnabled())go(()->TitlePortal.leaveMap(client,true));}
            case "back","cancel"->{modal="";rebuild();}
        }
    }
    @Override public void tick(){if(destination!=null&&motion.exitReady(TitlePreferences.motion())){var target=destination;destination=null;target.run();}}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(destination!=null)return true;
        if(key==GLFW.GLFW_KEY_ESCAPE){if(!modal.isEmpty())close();else{modal="leave";rebuild();}return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseClicked(double x,double y,int button){return destination!=null||super.mouseClicked(x,y,button);}
    @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
        long now=Util.getMeasuringTimeMs();double dt=(now-lastFrame)/1000.0;lastFrame=now;
        boolean animated=TitlePreferences.motion();
        cinema.advance(dt,animated);cinema.render(draw,width,height);
        super.render(draw,mouseX,mouseY,delta);
        // Prefer the pointer over a retained keyboard focus; only one menu item is highlighted.
        String focused=null,hovered=null;
        for(var b:buttons)if(b.active){if(b.isFocused())focused=b.action.id();if(b.isHovered())hovered=b.action.id();}
        if(hovered!=null)selected=hovered;else if(focused!=null)selected=focused;
        motion.step(dt,animated,0,0,Set.of(selected));
        var state=new TitleArt.State(client.getSession().getUsername(),client.isMultiplayerEnabled(),animated,modal,"",motion);
        TitlePainter.draw(draw,TitleArt.scene(layout,width,height,state),layout);
    }
    private final class ActionButton extends PressableWidget {
        final TitleLayout.Action action;
        ActionButton(TitleLayout.Action action){super((int)Math.round(layout.x(action.rect().x())),(int)Math.round(layout.y(action.rect().y())),Math.max(1,(int)Math.round(action.rect().w()*layout.scale())),Math.max(1,(int)Math.round(action.rect().h()*layout.scale())),Text.literal(action.label()));this.action=action;}
        @Override public void onPress(){BotandTitleScreen.this.action(action.id());}
        @Override public void renderButton(DrawContext context,int x,int y,float delta){}
        @Override public void playDownSound(SoundManager manager){}
        @Override protected void appendClickableNarrations(NarrationMessageBuilder b){appendDefaultNarrations(b);}
    }
}
