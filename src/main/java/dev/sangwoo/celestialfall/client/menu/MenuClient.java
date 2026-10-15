package dev.sangwoo.celestialfall.client.menu;
import dev.sangwoo.celestialfall.client.*;
import dev.sangwoo.celestialfall.menu.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;
public final class MenuClient {
    public static MenuProgress progress=new MenuProgress(0,-1);
    public static String notice="";
    public static boolean ready,pending;
    private static KeyBinding key;
    private static int serial,ticks,waiting;
    private static boolean consumed;
    private MenuClient(){}
    public static boolean visible(){var mc=MinecraftClient.getInstance();return MapClient.enabled()&&mc.player.isAlive()&&!mc.player.isSpectator()&&!mc.options.hudHidden&&!CinematicDirector.isActive();}
    public static boolean available(){return visible()&&!CombatClient.actionLocked();}
    public static void initialize(){
        MenuSettings.load();key=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.botand.menu",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_M,"category.botand"));
        HudRenderCallback.EVENT.register((c,d)->{var mc=MinecraftClient.getInstance();if(visible()&&mc.currentScreen==null){double x=mc.mouse.getX()*c.getScaledWindowWidth()/mc.getWindow().getWidth(),y=mc.mouse.getY()*c.getScaledWindowHeight()/mc.getWindow().getHeight();MenuPainter.draw(c,MenuArt.dock(c.getScaledWindowWidth(),c.getScaledWindowHeight(),false,mc.mouse.isCursorLocked()?-1:x,mc.mouse.isCursorLocked()?-1:y),new MenuLayout.View(0,0,1));}});
        ClientTickEvents.END_CLIENT_TICK.register(mc->{ticks++;while(key.wasPressed())if(mc.currentScreen instanceof BotandMenuScreen s)s.close();else if(mc.currentScreen==null&&available())open();if(pending&&ticks-waiting>100){pending=false;notice="응답이 늦어지고 있어요. 잠시 후 다시 눌러 주세요.";changed();}});
        ClientPlayNetworking.registerGlobalReceiver(MenuServer.STATE,(mc,h,b,r)->{var dim=b.readIdentifier();int id=b.readInt();var p=new MenuProgress(b.readUnsignedByte(),b.readByte());String message=b.readString(160);mc.execute(()->{if(!MapClient.enabled()||!dim.equals(mc.world.getRegistryKey().getValue())||id!=serial)return;progress=p;ready=true;pending=false;if(!message.isEmpty())notice=message;changed();});});
        ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->reset());
    }
    private static void changed(){var mc=MinecraftClient.getInstance();if(mc.currentScreen instanceof BotandMenuScreen s)s.updated();}
    public static void reset(){progress=new MenuProgress(0,-1);ready=pending=consumed=false;notice="";serial++;var mc=MinecraftClient.getInstance();if(mc.currentScreen instanceof BotandMenuScreen)mc.setScreen(null);}
    public static void request(int action,int choice){if(!available()||pending)return;if(!ClientPlayNetworking.canSend(MenuServer.REQUEST)){notice="서버에도 보탠드 2.10.0이 필요해요.";changed();return;}var b=PacketByteBufs.create();b.writeInt(++serial);b.writeByte(action);b.writeByte(choice);pending=true;waiting=ticks;ClientPlayNetworking.send(MenuServer.REQUEST,b);changed();}
    public static void click(){click(MenuSettings.volume());}
    public static void click(double volume){if(volume>0)MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(),1,(float)(.35*volume)));}
    public static void open(){if(!available())return;KeyBinding.unpressAll();MinecraftClient.getInstance().setScreen(new BotandMenuScreen());click();request(0,-1);}
    /** Consumes both halves of a HUD click so it cannot become a punch or a panel click. */
    public static boolean mouse(long window,int button,int action){var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle()||button!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return false;if(action==GLFW.GLFW_RELEASE&&consumed){consumed=false;return true;}if(action!=GLFW.GLFW_PRESS||mc.currentScreen!=null||!visible()||mc.mouse.isCursorLocked())return false;double x=mc.mouse.getX()*mc.getWindow().getScaledWidth()/mc.getWindow().getWidth(),y=mc.mouse.getY()*mc.getWindow().getScaledHeight()/mc.getWindow().getHeight();var root=MenuLayout.dock(mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight(),false).get(0).rect();if(!root.contains(x,y))return false;consumed=true;if(available())open();return true;}
}
