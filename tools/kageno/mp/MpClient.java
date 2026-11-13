package dev.sangwoo.botandmp;

import dev.sangwoo.celestialfall.client.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Multiplayer smoke test, client half (only with -Dbotand.mp.role=caster|watcher). The caster joins
 * the dedicated server and plays every Kageno key through the real keyboard path on a fixed script;
 * the watcher stands in front of it. Both log what their client sees and save a frame every 4 ticks.
 */
public final class MpClient implements ClientModInitializer {
    private static final String ROLE=System.getProperty("botand.mp.role");
    private static final Path OUT=Path.of(System.getProperty("botand.mp.out","mp"));
    private static final String ADDRESS=System.getProperty("botand.mp.address","127.0.0.1:25565");
    /** "beatdown" (2.64.0, MP_ONLY=beatdown): only the beatdown's script, BEATDOWN_SCRIPT (the check that new rules and packets call for). */
    static final String ONLY=System.getProperty("botand.mp.only","");
    private static final String[] SKILLS={"M1","E","R","T","T+","Y","Q","H","G","P"};
    /** Script tick, then a key: a bare name taps it, "+"/"-" hold and release. */
    /**
     * Since 2.38.2: E is walked and aimed (the camera turns 50 degrees right as it starts, W held through its thrusts), R
     * is walked away from the watcher through it and after it ends (its slowdown must end with it), T held's camera
     * turns 39 degrees right after the first slash, and "DUMMY" runs /botand dummy atomic 30 (an Atomic with no player
     * caster, 30 blocks ahead). Since 2.43.0 the caster dashes (C) after the lone M1 at 2420, whose combo then breaks: the
     * log shows both in the cooldown list. Since 2.45.0 the caster sends the player list away with Tab and back, opens
     * the pause menu (Esc), asks for a reset (R) and confirms it (Enter): the server kills it and both clients' own logs
     * show the death message in chat; then it respawns. Both log the player list's names and whether it is away.
     * Since 2.51.0 the caster then takes Star Platinum as its stand ("/botand progress stand @s star_platinum"), brings it
     * out with Q, walks away with it for two seconds and sends it back with Q: both log how they see each player's stand
     * (out or not as the server tracks it, coming out, at rest or going back, and whether this client drew it).
     * Since 2.54.0 the caster holds the attack with the stand out (3315 to 3378), so Star Platinum punches the watcher
     * standing in front through a whole combo, the fourth throwing it: both log each punch they hear (who, which,
     * whether it landed and on whom, its age on their clock then), when its shout starts on their clock (it should start
     * a tick in, StandAttack.LEAD) and the cooldown list's rest. Since 2.56.0 only the hook and the finisher shout: the
     * jab and the cross are logged as shouting nothing, and the hook's shout as fading when the finisher's comes.
     * Since 2.57.0 the caster first holds E with the stand out (3315 to 3405), so Star Platinum rushes the watcher in
     * front through all its forty blows, walking forward through it (3330 to 3380, 90% slower; the speed window is
     * logged); both log the rush they hear (its start, its voice starting on their clock, every tenth blow and whom it
     * struck, how loud its blows sound, its end), the watcher how many cries of pain it heard from itself, and the
     * cooldown list. The punches follow (3440 to 3503). Since 2.59.0 the caster then throws the heavy punch (R) twice at
     * the watcher, brought back 2.5 blocks in front of it each time ("HOME": /execute ... tp): first unguarded, then
     * with the watcher holding its guard up (F, from when it hears that second heavy punch start): both log each heavy
     * punch they hear, when its voice and its blow start on their clock, how loud its blow sounds once it lands, and
     * whose guard it broke; the watcher logs its own guard (up, broken), the server how far it flew. Since 2.60.0 the
     * second, breaking the watcher's guard, does not throw it but incapacitates it for 2 s: the watcher tries the attack
     * 5 ticks after it lands (refused). Then the watcher takes Kageno ("KAGENO", for skills to try), the caster calls
     * the watcher 4.5 blocks in front of it ("FAR") and uses the
     * Star Finger (T) on it: both log the Star Finger they hear, when its sound starts on their clock and whom it struck;
     * the watcher, struck, tries the attack, the dash (C) and E (all refused: incapacitated), walks forward for 25 ticks
     * (95% slower; its speed is logged) and tries the attack again once the 3 s are over (it swings); it logs its own
     * incapacitation and speed as its client knows them, the server as it keeps them. Since 2.61.0 the caster then turns
     * back (a tap of its guard turns its body to the camera, so the watcher it calls is where it then inhales), calls the
     * watcher 2.5 blocks in front of it again and inhales, holding Y down since 2.62.0 (3922 to 4012): the watcher is
     * caught, drawn in and held in front of the stand while the caster walks on (3942 to 3972), and, held, tries the
     * attack, the dash (C) and E and to walk away (all of it refused: held and incapacitated, 95% slower); Y let up, the
     * inhale ends at once and the watcher, let go, tries the attack 3 ticks after (refused: half a second more) and 14
     * ticks after (it swings). Then the caster inhales again (4060 to 4130) with the watcher holding its guard up (F,
     * for 40 ticks from when it hears that inhale start): the guard holds it off, and once the guard comes down the
     * inhale, still sucking, catches it; and once more, Y let up two ticks after it went down (4150): over at once,
     * before it sucks. Both log each inhale they hear (its start, its sound starting on their clock, whom it caught,
     * the guards seen holding it off, its end), the watcher whether it is held and where it is from the caster, the
     * server the same as it keeps them and every blow on the watcher (none).
     */
    private static final String[] SCRIPT={
        "5 M1","18 M1","31 M1","44 M1",
        "90 F+","130 F-",
        "170 E","172 TURN50","174 W+","214 W-",
        "270 R","271 TURN180","272 W+","362 W-",
        "370 T","510 T+","527 TURN39","530 T-",
        "650 Y","770 Q","830 H","980 G",
        "1480 P","1700 P",
        "1760 P","1900 E",
        "2000 P","2420 M1",
        "2445 C",
        "2460 DUMMY",
        "3070 TAB","3100 TAB","3120 ESC","3135 R","3150 ENTER","3185 RESPAWN",
        "3230 STAND","3270 Q","3315 E+","3330 W+","3380 W-","3405 E-","3440 M1+","3503 M1-",
        "3560 HOME","3575 R","3650 HOME","3665 R","3730 KAGENO","3740 FAR","3755 T","3850 TURN180","3852 W+","3892 W-",
        "3900 TURN0","3902 F+","3906 F-","3912 HOME","3922 Y+","3942 W+","3972 W-","4012 Y-","4045 HOME","4060 Y+","4130 Y-",
        "4145 HOME","4150 Y+","4152 Y-","4175 Q","4225 END"};
    /**
     * Star Platinum's beatdown (2.64.0), on its own (MP_ONLY=beatdown): the caster takes Star Platinum, brings it out and
     * calls the watcher 2.5 blocks in front of it ("HOME"), at full health ("HEAL"), and uses G on it: the whole
     * cutscene (both log the beatdown they hear, its cutscene's sound starting, the camera and the bars, where the one
     * struck is shown; the server the watcher held, its health through it and where it is put down). Then G against the
     * watcher's guard ("GUARD+": the watcher holds F while the file OUT/guard is there): the guard breaks, no cutscene.
     * Then G on nobody (the watcher 8 blocks away, "AWAY"). Then G on the watcher brought down to a quarter of its
     * health ("HURT": /damage, 150 of 200): its blows leave it at one health and the fall kills it as it comes down;
     * the watcher respawns.
     */
    /**
     * A skill with a cutscene glows first (2.109.0, MP_ONLY=glow, CutsceneGlow): its key glows its caster half a second
     * (both clients log the glow they draw and how long; the caster its own lock and speed) and only then is it cast;
     * a blow as it glows cuts it short and its cooldown runs from then. Each skill twice: first with the watcher asked to
     * strike as it glows ("JAB+": the watcher's attack as it sees the glow begin: cut short, its cooldown on), then with
     * the cooldowns off ("NOCDON") and no strike (cast as the glow ends): Star Platinum's beatdown (G, the watcher 2.5
     * blocks in front, its whole cutscene), Kageno's H (the watcher in it), Lee So-sang's G (the watcher 30 blocks off).
     * The server logs each glow's start and end, cast or cut, the blows on it, its speed and the cooldown after.
     */
    private static final String[] GLOW_SCRIPT={
        "5 NOCDOFF","8 STAND","40 Q","90 HOME","95 HEAL","100 JAB+","110 G","130 JAB-","160 G",
        "200 NOCDON","210 HOME","215 HEAL","230 G",
        "560 NOCDOFF","570 STANDK","600 HOME","605 HEAL","610 JAB+","620 H","640 JAB-","660 H",
        "700 NOCDON","710 HOME","715 HEAL","730 H",
        "900 NOCDOFF","910 ISOSANG","940 HOME","945 HEAL","950 JAB+","960 G","980 JAB-","1000 G",
        "1040 NOCDON","1050 DIST30","1055 HEAL","1070 G",
        "1260 NOCDOFF","1300 END"};
    /**
     * Plain Minecraft for one player (2.110.0, MP_ONLY=vanilla, VanillaMode): the caster takes Star Platinum, brings it out
     * and calls the watcher 2.5 blocks in front of it; the watcher is put in its vanilla mode ("WVANILLA": /execute as
     * Watcher run botand vanilla) and the caster throws a whole combo at it, a heavy punch and (awakened) a time stop:
     * none may reach it. The watcher's vanilla mode off again, a combo lands. Both log the map as their client has it,
     * their game mode, their inventory key and view, and the other's vanilla mode and body (CombatClient.animate); the
     * server each player's vanilla mode, game mode and health, every blow on the watcher and whether the time stop holds it.
     */
    private static final String[] VANILLA_SCRIPT={
        "5 STAND","40 Q","90 HOME","95 HEAL","100 WVANILLA",
        "140 HOME","150 M1+","190 M1-","220 R","260 AWAKEN","280 H",
        "420 WVANILLA","440 HOME","445 HEAL","460 M1+","500 M1-",
        "540 Q","580 END"};
    private static final String[] BEATDOWN_SCRIPT={
        "5 STAND","40 Q","90 HOME","100 HEAL","110 G",
        "400 HOME","405 HEAL","410 GUARD+","430 G","455 GUARD-",
        "520 AWAY","535 G",
        "600 HOME","605 HEAL","612 HURT","625 G",
        "935 Q","980 END"};
    /**
     * Star Platinum's time stop (2.67.0), on its own (MP_ONLY=timestop): the caster takes Star Platinum, brings it out,
     * calls the watcher 2.5 blocks in front of it ("HOME") at full health ("HEAL") and stops time (H): while time is
     * stopped it throws a whole combo at the watcher (the fourth throws) and a heavy punch; the watcher, stopped, tries
     * the attack, the dash, to walk and its guard (all held), and logs whether its screen is held still and every sound
     * that played meanwhile (only the time stop's own may). Time moves again: both log the harm that came to whom, the
     * server the watcher's health (one blow of all of it) and how far it flew. Then H again (refused: its cooldown runs).
     */
    private static final String[] TIMESTOP_SCRIPT={
        "5 STAND","20 AWAKEN","40 Q","90 HOME","100 HEAL","110 H",
        "165 M1+","205 M1-","215 R",
        "330 HOME","340 H",
        "400 Q","440 END"};
    /**
     * The basic attack's stagger and the time stop's 20 blocks (2.68.0), on their own (MP_ONLY=stagger): the caster
     * (Kageno at first) calls the watcher 2.5 blocks in front of it and holds its attack down: M1's four hits at full
     * speed (the watcher, struck, mashes its own attack every 3 ticks: refused while it is staggered, the whole combo);
     * then three taps 20 ticks apart (each 12 ticks later than it could be: the watcher, free between them, strikes back
     * and staggers the caster, cutting its M1 short); then Star Platinum out, its punches at full speed while the
     * watcher walks into them ("WALK+": 90% slower while staggered); then its bare fists (Star Platinum in) at full
     * speed. Then H with the watcher 25 blocks away (it walks on: not stopped) and again 15 blocks away (stopped: its
     * walk held), and a third time 2.5 blocks away, throwing Star Platinum's punches at the stopped watcher (their
     * staggers kept until time moves again, StarPlatinumTimeStop.deferStagger). Both log whether they are staggered as
     * their clients know it, the watcher its tries, its strikes and how fast it walked while staggered and while free;
     * the server both players' staggers, strikes, blows and speed.
     */
    private static final String[] STAGGER_SCRIPT={
        "5 HOME","10 HEAL","30 M1+","70 M1-",
        "130 HOME","135 HEAL","140 M1","160 M1","180 M1",
        "240 STAND","270 Q","310 HOME","315 HEAL","318 WALK+","320 M1+","365 M1-","372 WALK-",
        "420 Q","460 HOME","465 HEAL","470 M1+","520 M1-",
        "550 AWAKEN","560 Q","600 DIST25","610 H","655 WALK+","690 WALK-",
        "780 DIST15","790 H","835 WALK+","870 WALK-",
        "950 HOME","960 H","1005 M1+","1040 M1-",
        "1150 Q","1190 END"};
    /**
     * Kujo Jotaro, the boss (2.69.0), on his own (MP_ONLY=boss): the server brings him in 8 blocks behind the caster's
     * mark before either client joins (both must be shown him as they come in). The caster takes Star Platinum and
     * brings it out; the watcher is brought down to 80 of its 200 ("WLOW"); a new Jotaro is called 3 blocks to the
     * caster's west ("BOSSHERE": the first is taken off the clients), and the caster strikes him once ("HITBOSS"): he
     * turns on the watcher first (the least health), the caster kept whole. At 420 he is brought down to 2300 health
     * ("BOSSDMG2700"): under 2500 his time stop is his to call (both players stopped in it, the watcher's screen held).
     * At 700 the caster fells him ("BOSSKILL": /damage by the caster): 5000 won to the caster and six boxes in a ring
     * where he fell; the watcher opens one ("OPENBOX", the server claims it for it, and once only). Then another
     * Jotaro, struck and brought down to 3000 ("BOSSDMG2000"), and both players go 40 blocks away ("FLEE"): with nobody
     * within his 30 blocks he lets his anger go, walks back and is whole again in 5 s; both come back ("RETURN") and
     * the script ends with him there (the server stops with him in play; MP_RESTART=1 starts it again to see him back).
     * Both log what their client knows of him (in its list, his body, his skin, his outfit, the boss bar, his anger, his
     * stand, his health in tenths), their screen held by a time stop, the boxes they see, and the player list (he is
     * not in it); the server logs him (whom he goes after, what he does, each blow on him), both players' health and
     * money and the boxes.
     */
    private static final String[] BOSS_SCRIPT={
        "5 STAND","40 Q","60 WLOW","80 BOSSHERE","100 HITBOSS",
        "420 BOSSDMG2700",
        "700 BOSSKILL","740 OPENBOX",
        "790 BOSSHERE","810 HITBOSS","825 BOSSDMG2000","835 FLEE","1010 RETURN",
        "1080 END"};
    /**
     * The stand arrow (2.70.0; ten seconds since 2.71.0), on its own (MP_ONLY=arrow): the caster starts with no stand and two arrows in its main
     * hand; the server is told what the next arrow will do (the file OUT/rig, MpServer: Star Platinum, then the death).
     * The first stab (R) awakens Star Platinum, the caster trying to walk meanwhile (W, held by it); the arrow used again
     * with a stand ("USE": the item's use sent as R sends it, since a Star Platinum user's R is its heavy punch) is
     * refused (nothing starts, the arrow kept); the caster's stand is taken off again and the second stab kills it,
     * then it respawns. Both log every stab they hear (when, what it chose and when they heard it, cut short), the
     * caster's body as they draw it (the stab's pose on and off, how far down it went, lying where it fell as it dies),
     * the stab's sounds as they start and each player's stand; the server logs the caster held, its stand, the arrows in
     * its hand, its death and any move while held (there should be none).
     */
    private static final String[] ARROW_SCRIPT={
        "5 NOSTAND","10 ARROWS","20 RIGSTAR","30 R","60 W+","100 W-",
        "270 USE","300 NOSTAND","320 RIGDIE","330 R","400 W+","440 W-",
        "620 RESPAWN","700 END"};
    /**
     * Star Platinum called out and back, and its time stop for the awakened (2.72.0), on their own (MP_ONLY=call): the
     * caster takes Star Platinum and brings it out (Q) and back (Q) and out again: both log the call sound they play
     * for it (the call coming out, the sound of it going back) and how far into its coming out or going back it started
     * on their clock (it should start as it does). Its stand is then taken off it ("NOSTAND": it goes back by itself:
     * no sound) and given back and brought out (Q). Then H, not awakened: nothing at all (the server hears nothing);
     * awakened by the command ("AWAKEN": /botand progress awaken @s) H stops time, the watcher 2.5 blocks in front of
     * it stopped; the awakening taken back ("UNAWAKEN"), H is nothing again. Both log whether this client's player is
     * awakened, the time stop they hear; the server the caster's awakening and its time stop as it keeps them.
     */
    private static final String[] CALL_SCRIPT={
        "5 STAND","40 Q","100 Q","140 Q","180 NOSTAND","200 STAND","230 Q",
        "250 HOME","260 H","300 AWAKEN","330 H",
        "520 UNAWAKEN","550 H","600 Q","650 END"};
    /**
     * The long cooldowns (2.73.0), on their own (MP_ONLY=cooldowns; the server keeps the caster's cooldowns and, as it
     * comes back, the stand it had): the caster, Kageno, uses its Atomic (G) and waits it out (27.6 s), so its ten
     * minutes run; it leaves the server ("LEAVE": the pause menu's 나가기, TitlePortal.leaveMap) and joins again 5 s later:
     * the ten minutes begin again in full. It takes Star Platinum, is awakened, brings it out and stops time (H) with
     * the watcher 2.5 blocks in front of it (the two and a half minutes run once time moves again), then uses its
     * beatdown on nobody (the watcher 8 blocks away: its two minutes run once the opener is over). It resets its
     * character (Esc, R, Enter) and respawns 4 s later; it dies another way ("KILL": /kill) and respawns 4 s later; the
     * watcher kills it ("SLAIN": /damage by the watcher) and it respawns 4 s later: since 2.74.0 they begin again in
     * full as it comes back to life each time (they run on while it is dead; the Atomic sword's cooldown is on the new
     * body), since 2.74.1 only those of the stand it uses (Star Platinum's time stop and beatdown: Kageno's Atomic runs
     * on). It loses its stand ("NOSTAND") and the stand arrow gives it Star Platinum (R with the arrow in hand, the
     * server told to awaken it): its two in full again as the arrow chooses it, Atomic running on. It loses its stand
     * again, its next gacha pull is made an SSR ("PITY": the operator's count of 99 pulls since one) and it pulls once
     * ("PULL", as the gacha screen's 1회 asks; 2.74.1): Kageno, and Atomic in full again, the other two running on. It
     * leaves and joins once more, Kageno: Atomic in full again, the other two running on. Both log the long cooldowns
     * their client is told (LongCooldownsClient: each one's ready tick, the seconds from now, its length) and the
     * cooldown list (each entry's length and what is left of it); the server logs the caster's as kept with the world
     * and as each skill keeps its own, its deaths and its pull.
     */
    private static final String[] COOLDOWNS_SCRIPT={
        "5 G","600 LEAVE",
        "640 STAND","670 AWAKEN","680 Q","720 HOME","730 H",
        "900 AWAY","915 G",
        "990 ESC","1005 R","1020 ENTER","1100 RESPAWN",
        "1220 KILL","1300 RESPAWN",
        "1420 SLAIN","1500 RESPAWN",
        "1560 NOSTAND","1570 ARROWS","1580 RIGSTAR","1600 R",
        "1840 NOSTAND","1850 PITY","1870 PULL","1900 PULLED",
        "1960 LEAVE","2020 END"};
    /**
     * Another stand's cooldowns gone (2.74.2, user request: "걍 다른 스탠드를 끼면 다른 스탠드 쿨타임은 사라진다"), on their
     * own (MP_ONLY=swap; the server keeps the caster's cooldowns): the caster takes Star Platinum, is awakened and brings
     * it out, uses its beatdown on nobody (the watcher 8 blocks away) and stops time with the watcher 2.5 blocks in front
     * (two long cooldowns), then its heavy punch, its Star Finger, a short rush and a short inhale (four short ones). It
     * eats a Rokakaka (in its main hand, the left click's ask and the confirmation's 사용하고 초기화): its stand is none,
     * every box leaves the list and the server forgets the short ones. Star Platinum is given back by the command at once
     * (it begins nothing again): the two long ones are in the list again as they were, the short ones are not, and its
     * Star Finger, rush and inhale are used straight away (each would still have been cooling). It takes Kageno by the
     * command: Star Platinum's boxes leave the list; it uses Kageno's E, eats a Rokakaka again (its box leaves), and is
     * given Star Platinum back once more: only the two long ones. Both log the cooldown list (each entry's length and what
     * is left of it) and this client's stand; the server logs the caster's stand and its cooldowns, the long ones as kept
     * with the world and the short ones as Star Platinum's punches, rush and inhale and Kageno keep them.
     */
    private static final String[] SWAP_SCRIPT={
        "5 STAND","20 AWAKEN","40 Q","60 AWAY","70 G","110 HOME","120 H",
        "280 R","320 T","355 E+","375 E-","385 Y+","400 Y-",
        "420 FRUIT","430 EAT","450 CONFIRM",
        "490 STAND","500 Q","520 HOME","530 T","570 E+","585 E-","600 Y+","615 Y-",
        "640 STANDK","660 E","760 FRUIT","770 EAT","790 CONFIRM",
        "820 STAND","880 END"};
    /**
     * The long cooldowns begun in full whatever they were (2.74.3, user request: "그냥 단순하게 스탠드를 새로 얻거나
     * 죽으면 쿨타임 다시 처음부터 돌게하면 되는데"), on their own (MP_ONLY=fresh; the server keeps the caster's cooldowns and,
     * as it comes back, the stand it had). The caster, Kageno, loses its stand (its Atomic is forgotten at once) and is
     * given Kageno back by the command (which begins nothing: Atomic is ready), then leaves and joins again: Atomic, never
     * used, ten minutes from then. Star Platinum by the command (Atomic forgotten, nothing begun); /kill and back to life:
     * the beatdown in full, no time stop (not awakened); awakened by the command (still no time stop), /kill and back:
     * both in full. Not awakened any more (the time stop forgotten), no stand (the beatdown forgotten), and the stand
     * arrow gives it Star Platinum: the beatdown in full, no time stop. No stand again, its next gacha pull made an SSR:
     * Kageno, Atomic in full, nothing of Star Platinum's. Joining again, the caster's client drops the world's time packets
     * for 30 ticks (as if the server's time came late, MpLateTimeMixin): told Atomic's ten minutes as it joins, its
     * cooldown list must wait for the server's clock (CooldownHud.timed) and then show them, not put them 80 s ahead.
     * Both log the long cooldowns their client is told and the cooldown list (the caster also each change from joining
     * again until 100 ticks into the script: trace); the server logs the caster's as kept with the world and as each
     * skill keeps its own, its deaths, its stand and its awakening.
     */
    /**
     * Back to life by itself (2.107.0, MP_ONLY=death, user request: "죽으면 리스폰 화면이 뜨지 않고 ... 그 자리에서 5초 동안
     * 화면만 움직일수 있게 하다가 자동 부활"): the caster, Kageno, with the watcher 2.5 blocks in front of it, is killed
     * (/kill) and at once asks to come back (as an older client's respawn button: the server must hold it); dead, it
     * turns its camera with the right button held and the mouse moved ("DRAG": Minecraft's own mouse handlers), zooms out
     * with the wheel ("WHEELOUT") and tries the hotbar's 2, the attack, E and walking (none may go through); the server
     * brings it back five seconds after it died. Killed again, it leaves the server dead and joins again five seconds
     * later: the server brings it back five seconds after it is back. Both log every screen they are shown (no death
     * screen may come), the caster its health, camera and slot as they change, its own body in its world (gone a second
     * on, as Minecraft's: 2.107.1) and the watcher (still there through the five seconds: the world goes on for it); the
     * watcher the caster's body (falling dead where it fell, gone a second on in Minecraft's puff of smoke (2.107.1), then
     * back alive at the spawn); the server each death, where the body is and whether it is still in the world each
     * second, and each coming back.
     */
    private static final String[] DEATH_SCRIPT={
        "20 HOME","40 KILL","42 RESPAWN",
        "50 DRAG","80 WHEELOUT",
        "90 2","95 M1","100 E","105 W+","125 W-",
        "200 KILL","215 LEAVE",
        "560 END"};
    /** (2.107.0, MP_ONLY=death) The script tick the caster's right-button drag began (-1 for none), and its camera's way then. */
    private int dragAt=-1;private float dragFrom;
    private static final String[] FRESH_SCRIPT={
        "10 NOSTAND","30 STANDK","50 LEAVE",
        "70 STAND","90 KILL","170 RESPAWN",
        "200 AWAKEN","220 KILL","300 RESPAWN",
        "320 UNAWAKEN","330 NOSTAND","340 ARROWS","350 RIGSTAR","370 R",
        "620 NOSTAND","630 PITY","650 PULL","680 PULLED","720 END"};
    /**
     * Star Platinum's users resist another's stopped time for two seconds (2.75.0, user request: "스타플래티나는 시간정지
     * 면역이 2초 있게 만들어줘"), on their own (MP_ONLY=resist; nobody is healed). The caster takes Star Platinum, is
     * awakened and brings it out; the watcher is given Star Platinum and the awakening too ("WSTAR", "WAWAKEN") and
     * brings its own out (Q, as it sees it has it), and is called 2.5 blocks in front of the caster. The caster stops
     * time (H): the watcher, not stopped, tries its own time stop (H), the inhale (Y) and the beatdown (G) (all refused
     * while it resists), jabs the caster (it lands at once: the caster moves in its own stopped time) and walks back (S
     * held from 16 ticks in to 64): it walks on until its two seconds are up and stands stopped from there, its screen
     * held, until time moves. The watcher logs each tick of it how far it moved, whether its screen is held and whether
     * its client holds it; both log whom the time stop held when (the watcher appears in it two seconds in); the server
     * the watcher resisting and then stopped (and where), its own skills refused, and both players' health through it;
     * and on its own clock (a slow client lags far behind it) asks the watcher's own time stop, inhale and beatdown
     * straight while it resists (refused), and strikes it through the game's own blow path while it resists (landing at
     * once) and once it stands stopped (kept until time moves).
     */
    private static final String[] RESIST_SCRIPT={
        "5 STAND","20 AWAKEN","40 Q","60 WSTAR","65 WAWAKEN","90 HOME","100 HEAL","110 H",
        "330 Q","360 END"};
    /**
     * Lee So-sang's basic attack (2.77.0; 2.77.2 at the pace of the user's videos of each hit: the fourth starts 51 ticks
     * into the combo and throws its slash flying 14 ticks later, so the attack is held that long), on its own
     * (MP_ONLY=isosang): the caster takes Lee So-sang ("ISOSANG"), calls the watcher 2.5 blocks in front of it at full
     * health and holds its attack down: the four hits at full speed (the last can take no guard). Then a hit, and the next
     * 40 ticks later: the combo has broken and the attack rests (that press refused), and a press after the rest starts the
     * first hit again. Then the four hits with the watcher 8 blocks away ("AWAY": the first three strike no one) and
     * brought back in front of the caster, its guard up ("GUARD+"), as the fourth comes: its thrown slash goes through the
     * guard, reaching it a tick into its flight. Both log every hit they see (its number, the tick
     * it began on, its age as they heard of it), whom its blows struck and its sounds as they start (at what age of the
     * hit), the watcher its own staggers and every move of its body (a blow throws no one); the server the caster's hits
     * and blows, the watcher's health, its staggers and any move of it.
     */
    private static final String[] ISOSANG_SCRIPT={
        "5 ISOSANG","40 HOME","45 HEAL","60 M1+","124 M1-",
        "190 HOME","195 HEAL","200 M1","240 M1","272 M1",
        "330 AWAY","335 HEAL","338 GUARD+","350 M1+","402 HOME","414 M1-","440 GUARD-",
        "500 END"};
    /**
     * Lee So-sang's E (2.80.0, MP_ONLY=isosange): the four hits at full speed, then E at once after the fourth (its rest
     * is gone: the next press starts the empowered first then, not 1.2 s after the fourth's motion), the empowered combo
     * at full speed against the watcher 2.5 blocks in front at full health (three swords thrown, the second and third
     * half as hard again, the step back and the giant sword's thrust, which throws the watcher), E again (refused: 15 s
     * from the thrust's end), and a press once the rest is over (a plain first: the empowerment is spent). Both log the
     * hits they see, the swords thrown and where they stopped, whom each struck and the sounds; the caster where it stands
     * (the step back); the server the hits, the blows, the watcher's health and throw, and the caster's E.
     */
    private static final String[] ISOSANGE_SCRIPT={
        "5 ISOSANG","40 HOME","45 HEAL","60 M1+","128 M1-",
        "130 E","132 HOME","133 HEAL","134 M1+","205 M1-",
        "215 E","230 HOME","235 HEAL","242 M1","280 END"};
    /**
     * Lee So-sang's R (2.81.0, MP_ONLY=isosangr): R at the watcher 2.5 blocks in front at full health (the sweep stands
     * where the caster does, strikes it 20, throws no one, staggers no one), R again at once (refused: 20 s from its end);
     * once it is ready, the watcher 8 blocks ahead in the crosshair: the sweep stands out toward it and strikes it; the
     * attack pressed a little before R lets go (kept, the first hit as it does); then R pressed a little before a hit lets
     * go (kept, R as it does) at the watcher with its guard up (no guard takes it). Both log the swings they see (where R's
     * sweep stands, whom it struck, its sound); the server the caster's R (where it stands, when it is ready again) and
     * hits, the watcher's health and any move of it.
     */
    private static final String[] ISOSANGR_SCRIPT={
        "5 ISOSANG","40 HOME","45 HEAL","60 R","100 R",
        "500 DIST8","505 HEAL","520 R","536 M1","538 M1-",
        "940 HOME","945 HEAL","950 GUARD+","962 M1","964 M1-","966 R","1010 GUARD-",
        // (2.81.1) R held: the watcher 25 blocks ahead in the crosshair (the camera all but level, so its crosshair reaches
        // them), R held down 12 ticks: the sword sent to them once it has been held 8
        "1395 PITCH2","1400 DIST25","1405 HEAL","1420 R+","1432 R-","1500 END"};
    /**
     * Lee So-sang's H (2.82.0, MP_ONLY=isosangh): H with the watcher 2.5 blocks in front at full health (its swords strike
     * it 30 as they come down and 5 every 5 s after, no guard, no throw); the attack and E while it is under way (refused:
     * only R may be used up there; since 2.95.0 the attack is 회풍만검 once its swords stand, 1.35 s in: the second press,
     * 3 s in, sends six swords of 2 at the watcher if the crosshair is near it, MP_ONLY=isosangvolley), R from the sword (it strikes the watcher 20 from the formation's ground; the watcher
     * put 3 blocks before the caster first: 2.82.3, the caster leapt across past it; 2.82.4, the caster stands where it
     * leapt, on the formation's outer ring, its middle 8.7 blocks ahead), H again (refused: under way), and H once it is
     * over (refused: 60 s from its end). The
     * caster stands on the sword some 39 s with nothing under it: the server must not take it for flying. Both log the
     * formation they see (its middle, the caster's height over it as drawn and as it is, and 2.82.3 how far off the plan's
     * place level the caster is and is drawn), its sounds, and the caster's H; the server the caster's formation, its
     * height, how far off where it holds the caster (every other tick as they leap), and its strikes, and the watcher's health.
     */
    private static final String[] ISOSANGH_SCRIPT={
        "5 ISOSANG","40 HOME","45 HEAL","50 GAUGE","60 H","75 M1","100 E","120 M1","130 DIST3","140 R","200 H",
        "930 H","980 END"};
    /**
     * Lee So-sang's G (2.83.0, MP_ONLY=isosangg): G with the watcher 30 blocks ahead at full health (2.87.1: 21 past where
     * its sword strikes, in the blast's 62 × 47 blocks but out of the 20 blocks round it before): the watcher is struck 50
     * as it strikes (4.85 s), no guard, no throw; G again while it
     * is under way (refused), the attack while it holds the caster (refused), and G once it is over (refused: 5 minutes).
     * The caster's camera is in its cutscene, in its own space (the world hidden), until 4.83 s; the watcher's never is, nor
     * does its world hide: it sees the caster's hand, and where it struck, the landing's fire and dust. Both log what they
     * see of it (its recording, where it strikes, their space and camera every half second, the landing drawn) and its
     * sound; the server the caster's G (where it strikes, when it is ready again), the caster held where it stood and
     * unharmed, and the watcher's health.
     */
    private static final String[] ISOSANGG_SCRIPT={
        "5 ISOSANG","40 DIST30","45 HEAL","60 G","75 G","90 M1","300 G","360 END"};
    /**
     * Lee So-sang's Z (2.93.0, MP_ONLY=isosangz): H with the watcher 2.5 blocks in front, then Z three times within ten
     * seconds of the first, each onto a sword the caster's camera turns to ("ZAIMdeg": of its formation, the one nearest
     * deg degrees clockwise of where the camera looks, 3.5 to 10 blocks off), a fourth at once (refused: its twenty
     * seconds' cooldown runs from the third), one once that is over and none after it (its cooldown runs from its
     * window's end), R from the sword it stands on then (the sweep stands there), and Z after the window (refused). Both
     * log each flight they hear of (from which sword to which), the caster every tick through it and after (hidden or not,
     * how far off the plan it is and is drawn), and the caster's Z as their client has it; the server the caster's Z as it
     * changes and every tick of each flight how far off where the formation holds them the caster is.
     */
    private static final String[] ISOSANGZ_SCRIPT={
        "5 ISOSANG","40 HOME","45 HEAL","50 GAUGE","60 H",
        "100 ZAIM30","104 Z","140 ZAIM-40","144 Z","180 ZAIM35","184 Z","210 ZAIM-30","214 Z",
        "600 ZAIM30","604 Z","700 R","810 ZAIM-30","814 Z","950 END"};
    /**
     * Lee So-sang's H's dodge (2.94.0, MP_ONLY=isosangdodge): H with the watcher 10 blocks behind the caster (out of the
     * formation: its swords do not strike it), then the caster struck three times by the watcher ("HITME": /damage Caster 4
     * minecraft:player_attack by Watcher) once the swords stand (2 s in) and 1.5 s apart: the first does not reach it (its
     * health as it was) and it is moved onto a sword of the formation at random (as Z moves it: both hear the flight and
     * its sound; its own Z as it was), the second and the third do (4 each). Both log each flight they hear and the caster
     * through it (as for Z), the server the caster's health, its dodge (used or not) and the sword it stands on as they
     * change, and every tick through each move how far off where the formation holds it the caster is.
     */
    private static final String[] ISOSANGDODGE_SCRIPT={
        "5 ISOSANG","40 DIST-10","45 HEAL","50 GAUGE","60 H","100 HITME","130 HITME","160 HITME","260 END"};
    /**
     * Lee So-sang's H basic attack, 회풍만검 (2.95.0, MP_ONLY=isosangvolley): H with the watcher 20 blocks ahead (out of the
     * formation: its swords do not strike it), then on the sword the caster's camera onto the watcher ("VAIM") and the
     * attack ("VM1", its crosshair's pick sent with it): six swords of 2 come down on the watcher (200 to 188, no throw);
     * again 1.6 s later (refused: once every 3 s), again once that is over (188 to 176); (2.95.2) then the watcher moved
     * on to 23 blocks ahead ("DIST23": 17.4 from the formation's middle, out of the reach of the formation and 3 blocks
     * more; as that volley's last sword comes down, which follows it out there all the same, within VOLLEY_LEASH, the
     * server's "watcher moved" lines then being the move and its fall) and the camera onto it again: it is not picked,
     * the swords come down at the reach's edge on the ground before it, no one struck (176 kept); then the camera onto the ground 50 degrees to the left 10 blocks off ("VGROUND-50")
     * and the attack: the swords come down there, no one struck. Both log each volley they hear (on whom, where it comes
     * down as drawn here against the watcher's chest, how many swords are drawn as they fan out, fly and strike) and its
     * sound; the server each volley, its blows and the watcher's health.
     */
    /**
     * Lee So-sang's T (2.96.0, MP_ONLY=isosangt): H with the watcher 20 blocks ahead (out of the formation: its swords do
     * not strike it, within T's reach), then on the sword the caster's camera onto the watcher ("VAIM") and T where it
     * looks ("VT", its crosshair's pick sent with it): the caster dashes at the watcher and cuts it (6), six swords of 2
     * and three slashes of 4 come down on it (200 to 170, no throw), and the caster lands just in front of it at its side
     * (2.96.2; 2.96.0 to 2.96.1 3 blocks beyond it) and flies on onto a sword on the other side of the formation from the
     * one it began on (2.96.1); 2.96.4 (no cooldown for now, again as she stands on the far sword, a press a moment before
     * kept for then): T again 3 ticks in (refused: it holds her), 9 ticks in (kept: it goes 14 ticks in, from the far
     * sword, the first's last swords and slashes still coming down), and so a third (200 to 170 to 140 to 110); then the
     * camera onto the ground 50 degrees to the left 10 blocks off ("VGROUND-50") and T: the caster dashes there, the
     * blows come down there, no one struck (170 kept). Both log each T they hear (on whom, where she lands against the
     * watcher, the swords she flies from and to, how late) and, every tick through it, the caster hidden or not, how far
     * off the plan it is and is drawn; the server each T, its blows, the watcher's health and how far off where the
     * formation holds them the caster is through it.
     */
    private static final String[] ISOSANGT_SCRIPT={
        "5 ISOSANG","40 DIST20","45 HEAL","50 GAUGE","60 H","100 VAIM","104 VT","107 VT","112 VAIM","113 VT","126 VAIM","127 VT","440 VGROUND-50","446 VT","530 END"};
    /**
     * Lee So-sang's Y (2.97.0, MP_ONLY=isosangy): H with the watcher 20 blocks ahead (out of the formation: its swords do
     * not strike it, within Y's reach), then on the sword the caster's camera onto the watcher ("VAIM") and Y where it
     * looks ("VY", its crosshair's pick sent with it), the watcher asked to walk to its left from 3 ticks in ("WALK+"): the six
     * swords show over it, their ring following it as it walks until the server sets it (9 ticks in), and come down on it
     * two at a time: thirteen blows, 200 to 160, no throw; caged from the first (0.55 s) to 1.55 s: incapacitated, its walk
     * 80% slower (its steps a fifth as long), free again after. Y again 3 ticks in (refused: under way) and at 3 s (refused:
     * its 20 s cooldown). Both log each Y they hear (on whom, its ring against the watcher's feet, how late) and, every
     * tick through it, the ring as drawn against the watcher, the caster's height drawn and planned (her leap) and the
     * swords drawn; the watcher its own steps and whether it is held; the server each Y, its blows, the watcher's health,
     * whether it is caged and its steps.
     */
    private static final String[] ISOSANGY_SCRIPT={
        "5 ISOSANG","40 DIST20","45 HEAL","50 GAUGE","60 H","100 VAIM","104 VY","107 VY","107 WALK+","150 WALK-","160 VAIM","164 VY","240 END"};
    /**
     * Lee So-sang's H again (2.101.0, MP_ONLY=isosanghh): H with the watcher out of the formation ("WPOS15.5/8.5": 15.5
     * blocks ahead of the caster and 8.5 to its right, 13 from the formation's middle: its first 30 does not strike it),
     * then the watcher put well inside the band ahead of her ("WPOS11/3": the band turns with her toward the crosshair as
     * she presses, by some degrees, and the first check's watcher at its far corner was left just out of it), H again as
     * every sword of the formation stands (40 ticks in): the formation is
     * over, its swords the finale's, five blows of 12 on the watcher as they come down (200 to 140), no throw; H again 3
     * ticks in (refused: it holds her) and 140 ticks in (refused: H's cooldown). Both log the finale they hear (its
     * recording, where she stands, the formation it takes), every few ticks the formation's swords still standing as drawn
     * and the finale's, how high the caster is drawn and really is, whether her hand is empty and (the caster) whether her
     * cutscene is on; the server its start, its blows, the watcher's health, the formation over, H's ready tick, and how
     * high over where she stands the caster is.
     */
    private static final String[] ISOSANGHH_SCRIPT={
        "5 ISOSANG","40 HOME","45 HEAL","50 GAUGE","60 H","62 WPOS15.5/8.5","98 WPOS11/3","100 H","103 H","240 H","300 END"};
    /**
     * Lee So-sang's H gauge (2.113.0, MP_ONLY=isosanggauge): H with an empty gauge (refused), the caster's combo on the
     * watcher 2.5 blocks in front (each hit fills one: four), E and the empowered combo (four more: full), H (it lays the
     * formation with the watcher in it: its first blow slows the watcher 70% for 3 s, then 50% while it stands in it),
     * the watcher sent out of it (the slow lifts), T three times (each takes a fifth: the formation's end moves sooner,
     * told to the watcher's client), the formation running dry (its swords flare and it ends then, the watcher's client
     * too); then the cooldowns off, the gauge full (/botand gauge), H and H again (the gauge empty at once). Both log the
     * caster's gauge as their HUD has it, the formation's end as told, and their own slow; the server the caster's gauge,
     * the formation's dry and flare ticks, and the watcher's slow.
     */
    private static final String[] ISOSANGGAUGE_SCRIPT={
        "5 ISOSANG","40 HOME","45 HEAL","60 H","70 M1+","140 M1-","142 E","150 HOME","152 HEAL","155 M1+","232 M1-",
        "240 HOME","245 HEAL","260 H","380 DIST20","400 VAIM","404 VT","420 VAIM","424 VT","440 VAIM","444 VT",
        "700 NOCOOL","705 GAUGE","710 HOME","715 H","760 H","900 END"};
    private static final String[] ISOSANGVOLLEY_SCRIPT={
        "5 ISOSANG","40 DIST20","45 HEAL","50 GAUGE","60 H","100 VAIM","104 VM1","134 VAIM","136 VM1","170 VAIM","174 VM1",
        "200 DIST23","236 VAIM","240 VM1","296 VGROUND-50","302 VM1","380 END"};
    /**
     * X Chara's G, the sword dance (2.112.0, MP_ONLY=xchara): the caster takes X Chara and its cooldown is on; the watcher
     * 5 blocks in front of it. G against the watcher's guard ("GUARD+"): blocked as the "!!!" shows, its 3 minutes' cooldown
     * on; G again (refused: its cooldown). The cooldowns off ("NOCDON"), G and the watcher's guard raised as the "!!!"
     * shows: blocked then. G and the caster struck as the "!!!" shows ("HITME"): called off. G again on the watcher at full health: the whole of it, the strange space on both clients, its
     * 40 blows (200 to 140), the watcher sent flying. Both log every sword dance they hear of (its moments as they change,
     * whom it marks), the "!!!" they draw, whether their camera is in its strange space and where, whether they are held;
     * the server its start, its strange space, its end, what it dealt, its ready tick, the watcher's health and flight.
     */
    private static final String[] XCHARA_SCRIPT={
        "5 XCHARA","8 NOCDOFF","40 DIST5","45 HEAL","50 GUARD+","62 G","80 GUARD-","90 G",
        "110 NOCDON","120 G","126 GUARD+","150 GUARD-","170 G","176 HITME","200 HEAL","205 DIST5","210 G","470 END"};
    /**
     * X Chara's basic attack (2.114.0, MP_ONLY=xcharam1; since 2.118.0 the four swings are the same with the hack on or
     * off, and this script, which leaves the hack off, swings the plain four: MP_ONLY=xcharaplain is the fuller check): the
     * caster takes X Chara, its cooldowns on. The whole combo into the watcher 3 blocks ahead (the attack held: 6, 6, 6,
     * 10; no blow but the lunge moves it, the lunge throws it). Again with the watcher's guard up (the first three kept
     * off, the lunge through it). One hit and nothing more: the combo breaks and the attack rests (a press in the rest
     * refused, one after it the first hit again). The caster struck in the middle of a hit: it stops short and rests.
     * The watcher 6 blocks ahead: nothing reaches it (until 2.118.0 the hack's thrusts threw knives of light that far).
     * Last the watcher's guard raised only once the second hit is in (18 ticks after the press): the chop kept off, the
     * lunge through its guard (it takes none). The server leaves the watcher where the script puts it and where the
     * lunge throws it (MpServer.restore). Both clients log every swing as they hear it (which hit, how late), its blow's
     * struck as told, a swing stopped short, the sound they start and the caster's hit count; the server each hit, its
     * blow, the rest, the watcher's health, guard and moves.
     */
    private static final String[] XCHARAM1_SCRIPT={
        "5 XCHARA","8 NOCDOFF",
        "40 DIST3","45 HEAL","60 M1+","104 M1-",
        "170 DIST3","175 HEAL","180 GUARD+","195 M1+","239 M1-","250 GUARD-",
        "320 DIST3","325 HEAL","330 M1","370 M1","400 M1",
        "470 DIST3","480 M1","482 HITME","488 M1","530 M1",
        "600 DIST6","605 HEAL","610 M1+","654 M1-",
        "720 DIST3","725 HEAL","740 M1+","758 GUARD+","784 M1-","800 GUARD-",
        "880 END"};
    /**
     * X Chara's Q, the hack (2.115.0, MP_ONLY=xcharaq): the caster takes X Chara. Q: on. Q: off. Q twice within 8 ticks:
     * on once (the second is not heard). The whole combo into the watcher 3 blocks ahead with it on (hits 1 to 4: 6, 6, 6,
     * 10, the same swings as with it off, 2.118.0). Another stand: off by itself; X Chara again: still off until Q. Slain with it on: off,
     * and the new body comes back without it; Q on the new body: on. Both clients log each player's hack as they see it
     * change (and whether the panel is drawn in their other hand) and the sounds they start for it; the server the
     * caster's hack as it changes (with why it may or may not be on), and the combo as for xcharam1.
     */
    private static final String[] XCHARAQ_SCRIPT={
        "5 XCHARA","8 NOCDOFF","40 DIST3","45 HEAL",
        "60 Q","100 Q",
        "140 Q","143 Q",
        "175 HEAL","180 M1+","224 M1-",
        "300 STAND","330 XCHARA","360 Q",
        "400 SLAIN","560 Q",
        "620 END"};
    /**
     * X Chara's swings and the hack between hits (2.117.0, MP_ONLY=xcharaplain; 2.118.0: the same four swings with the
     * hack on or off, one sound for all): the caster takes X Chara, its hack off as it comes. The plain four into the
     * watcher 3 blocks ahead (the attack held: hits 5 to 8, one blow each: 6, 6, 6, 10; no blow but the lunge moves it,
     * the lunge throws it). Again with the watcher's guard up (the three cuts kept off, the lunge through it). The
     * attack held and Q struck once the second swing has begun: hits 5 and 6, then 3 and 4 (the hack on: the same chop
     * and lunge, 6 and 10, told as the kind the clients draw a purple cut for). The hack on now: held again and Q struck
     * once the second has begun: hits 1 and 2, then 7 and 8. Last one swing and nothing more with the hack off: it
     * breaks and rests. Both clients log every swing as they hear it (which hit: 1 to 4 begun with the hack on, 5 to 8
     * plain), its blow's struck, the sound they start (xchara_slash for every swing, two ticks before its blow) and
     * each player's hack as it changes; the server each hit, its blow, the rest, the watcher's health, guard and moves,
     * and the caster's hack as it changes.
     */
    private static final String[] XCHARAPLAIN_SCRIPT={
        "5 XCHARA","8 NOCDOFF",
        "40 DIST3","45 HEAL","60 M1+","104 M1-",
        "170 DIST3","175 HEAL","180 GUARD+","195 M1+","239 M1-","250 GUARD-",
        "320 DIST3","325 HEAL","330 M1+","344 Q","374 M1-",
        "440 DIST3","445 HEAL","450 M1+","464 Q","494 M1-",
        "560 DIST3","565 HEAL","570 M1","610 M1","640 M1",
        "700 END"};
    /**
     * (2.121.0: the restore is Y's, and R the cleave. The script now: R, Y and G with the hack off, none heard. Q. The
     * caster hurt, Y: 65 healed 8 ticks in; Y again refused; E: nothing. The watcher walking ("WALK+"), R: the blow 24
     * ticks in takes 18 off it and it cannot move or act for 20 ticks (the server logs how far it moved through it). R
     * again: refused (12 s from its end). After that, R on the watcher's guard: struck all the same. After that, R and
     * the caster struck before the blow ("HITME"): stopped short, its cooldown from then; R again refused. Last G on the
     * watcher's guard.)
     * (2.124.0: R with the hack off is the jab, so it is no longer pressed with the hack off at the start; after the G, Q
     * turns the hack off and, the watcher walking, R: the jab's blow 14 ticks in takes 13 off it and it cannot move or act
     * for 160 ticks, its X on its face on both clients for as long, the caster's knife lit by it until the blow. R again:
     * refused (45 s from its end). The cooldowns off, R on the watcher's guard: struck all the same. R and the caster
     * struck before the blow ("HITME"): stopped short.)
     * (2.125.0: the cleave is H's with the hack on, so every R above with the hack on is an H now. After the jab's part, Q
     * turns the hack on again and R is the double: on the watcher 5 blocks ahead, the "!!!", seized 17 ticks in, in the
     * wall 40 ticks in (20 off it, put at the wall's foot some 8 blocks behind where it stood, unable to move or act for
     * 30 ticks), over 70 ticks in; R again while it runs and after: refused (20 s from its end). The cooldowns off: R on
     * the watcher's guard (blocked at once); R and the guard raised as the "!!!" shows (blocked then); R and the caster
     * struck as the "!!!" shows ("HITME": called off); R once more and the watcher's guard raised only after it is seized
     * (too late: into the wall all the same). Both clients log the double as they hear of it, and whether it holds them.)
     * X Chara's E with the hack on, the restore, and the hack's G (2.119.0, MP_ONLY=xcharae): the caster takes X Chara,
     * its cooldowns on, its hack off as it comes. E, then G on the watcher 3 blocks ahead, with the hack off: neither is
     * heard. Q: the hack on. The caster hurt 120 ("CHURT": /damage Caster 120), then E: the press, 65 healed 8 ticks in.
     * E again at once: refused (its cooldown, 20 s from its end). Once it is over (440 ticks on) E and the caster struck
     * by the watcher before the press lands ("HITME"): stopped short, no heal, the cooldown from then. Last G with the
     * hack on and the watcher's guard up: the "!!!" and blocked (G is heard with the hack on). Both clients log the
     * caster's E as they hear of it (started, healed how much, stopped short; its sound), the hack as it changes and the
     * sword dance; the server the caster's health at each change, its E (when it began, whether it healed, when it is
     * ready again), its hack and its G.
     */
    private static final String[] XCHARAE_SCRIPT={
        "5 XCHARA","8 NOCDOFF","40 DIST3","45 HEAL",
        "75 E","90 G",
        "110 Q","130 CHURT","150 E","175 E","185 Y",
        "210 H","226 WALK+","300 WALK-","310 DIST3","320 H",
        "500 HEAL","505 GUARD+","515 H","600 GUARD-",
        "800 DIST3","810 H","815 HITME","840 H",
        "880 GUARD+","890 G","930 GUARD-",
        "960 Q","975 DIST3","980 HEAL","990 WALK+","1000 R","1030 R","1200 WALK-",
        "1210 NOCDON","1215 DIST3","1220 GUARD+","1230 R","1260 GUARD-",
        "1430 DIST3","1440 R","1443 HITME","1470 NOCDOFF",
        "1480 Q","1490 DIST5","1495 HEAL","1500 R","1530 R","1580 R",
        "1600 NOCDON","1605 DIST5","1610 HEAL","1615 GUARD+","1625 R","1650 GUARD-",
        "1670 DIST5","1680 R","1686 GUARD+","1710 GUARD-",
        "1730 DIST5","1740 R","1745 HITME",
        "1780 DIST5","1785 HEAL","1790 R","1812 GUARD+","1840 GUARD-",
        "1920 NOCDOFF","1940 END"};
    /**
     * X Chara's T with the hack on, the weakening, and the restore back on E (2.126.0, MP_ONLY=xcharat): the caster
     * takes X Chara, its cooldowns on. T with the hack off: not heard. Q. The caster struck by the watcher ("HITME":
     * 4) and one swing at the watcher 3 blocks ahead (6), to compare. T on the watcher: the "!!!", weakened 15 ticks
     * in for 400. T again at once: refused (50 s from when it took). The caster struck by the weakened watcher (3: three
     * quarters) and one swing at it (7.5: a quarter more). The caster hurt 120, then E: 65 healed (the restore is E's
     * again), Y: nothing. Once the weakening is over (400 ticks on), struck and swung again: 4 and 6 again. The
     * cooldowns off: T on the watcher's guard (blocked at once, nobody weakened); T and the caster struck as the "!!!"
     * shows (called off); T, and T again once it took (weakened anew: the full 400 again, not more); the watcher slain
     * while weakened ("WLOW" twice: each 120 a quarter more), its weakening over with it, its new body not weakened.
     * Both clients log whom they hold weakened and the casts as they hear of them, and the warning sound.
     */
    private static final String[] XCHARAT_SCRIPT={
        "5 XCHARA","8 NOCDOFF","40 DIST3","45 HEAL",
        "60 T","80 Q",
        "100 HITME","120 M1",
        "200 DIST3","205 HEAL","210 T","240 T",
        "260 HITME","300 M1",
        "350 CHURT","360 E","400 Y",
        "640 DIST3","650 HITME","670 M1",
        "740 NOCDON","745 DIST3","750 GUARD+","760 T","790 GUARD-",
        "810 DIST3","820 T","824 HITME",
        "860 DIST3","870 T","900 T",
        "960 WLOW","975 WLOW",
        "1120 DIST3","1130 HITME",
        "1160 NOCDOFF","1180 END"};
    /**
     * X Chara's Y with the hack on, the pull (2.127.0, MP_ONLY=xcharay): the caster takes X Chara, its cooldowns on.
     * The watcher 8 blocks ahead; Y with the hack off: not heard. Q. Y on the watcher: the "!!!" at once, pulled 7
     * ticks in to 1.5 blocks before the caster, no harm, nothing holding it after, facing as it faced. Y again at
     * once: refused (20 s from the pull). The cooldowns off: Y on the watcher's guard (blocked, not moved); Y and the
     * caster struck as the "!!!" shows ("HITME": called off, not moved); Y from 10 blocks, from 3 and from 1 (put at
     * 1.5 each time). Both clients log the casts as they hear of them, where the one pulled is and is drawn there, and
     * the two sounds.
     */
    private static final String[] XCHARAY_SCRIPT={
        "5 XCHARA","8 NOCDOFF","40 DIST8","45 HEAL",
        "60 Y","80 Q",
        "110 Y","130 Y",
        "200 NOCDON","205 DIST8","210 GUARD+","225 Y","255 GUARD-",
        "270 DIST8","285 Y","286 HITME",
        "320 DIST10","335 Y",
        "370 DIST3","385 Y",
        "420 DIST1","435 Y",
        "470 NOCDOFF","490 END"};
    /**
     * X Chara's G with the hack off, the slam (2.128.0, MP_ONLY=xcharaslam): the caster takes X Chara, its cooldowns
     * on, their camera looking steeply down (so the middle of the screen, from up there, points at the ground a few
     * blocks ahead). Q, and G with the hack on and no one near: no slam. Q again. G with the watcher 6 blocks ahead:
     * the knife lit, the rise (drawn by both clients, the caster really where they stood), where it comes down told by
     * the caster's screen, the landing there (the caster really there from then), the watcher struck for 50 and unable
     * to act 9 ticks. G at once: refused (60 s from its end). The cooldowns off: G on the watcher's guard (struck all
     * the same); G and the caster struck in the crouch ("HITME": stopped short); G and the caster struck in the air
     * (no harm, it goes on); G looking out level (it comes down at the circle's edge). Both clients log the slam as
     * they hear of it, how far from where the caster is they draw them, and its sounds.
     */
    private static final String[] XCHARASLAM_SCRIPT={
        "5 XCHARA","8 NOCDOFF","30 PITCH55","40 DIST30","45 HEAL",
        "60 Q","80 G","100 Q",
        "120 DIST6","130 HEAL","140 G",
        "250 G",
        "280 NOCDON","285 DIST6","290 HEAL","295 GUARD+","310 G","400 GUARD-",
        "420 DIST2","425 HEAL","440 G","450 HITME",
        "500 DIST2","505 HEAL","520 G","550 HITME","565 HITME",
        "640 DIST30","645 PITCH8","660 G",
        "780 NOCDOFF","800 END"};
    private static final String[] STEPS=ONLY.equals("xcharaslam")?XCHARASLAM_SCRIPT:ONLY.equals("xcharay")?XCHARAY_SCRIPT:ONLY.equals("xcharat")?XCHARAT_SCRIPT:ONLY.equals("xcharae")?XCHARAE_SCRIPT:ONLY.equals("xcharaplain")?XCHARAPLAIN_SCRIPT:ONLY.equals("xcharaq")?XCHARAQ_SCRIPT:ONLY.equals("xcharam1")?XCHARAM1_SCRIPT:ONLY.equals("isosanggauge")?ISOSANGGAUGE_SCRIPT:ONLY.equals("xchara")?XCHARA_SCRIPT:ONLY.equals("vanilla")?VANILLA_SCRIPT:ONLY.equals("glow")?GLOW_SCRIPT:ONLY.equals("death")?DEATH_SCRIPT:ONLY.equals("isosanghh")?ISOSANGHH_SCRIPT:ONLY.equals("isosangy")?ISOSANGY_SCRIPT:ONLY.equals("isosangt")?ISOSANGT_SCRIPT:ONLY.equals("isosangvolley")?ISOSANGVOLLEY_SCRIPT:ONLY.equals("isosangdodge")?ISOSANGDODGE_SCRIPT:ONLY.equals("isosangz")?ISOSANGZ_SCRIPT:ONLY.equals("isosangg")?ISOSANGG_SCRIPT:ONLY.equals("isosangh")?ISOSANGH_SCRIPT:ONLY.equals("isosangr")?ISOSANGR_SCRIPT:ONLY.equals("isosange")?ISOSANGE_SCRIPT:ONLY.equals("isosang")?ISOSANG_SCRIPT:ONLY.equals("resist")?RESIST_SCRIPT:ONLY.equals("fresh")?FRESH_SCRIPT:ONLY.equals("swap")?SWAP_SCRIPT:ONLY.equals("cooldowns")?COOLDOWNS_SCRIPT:ONLY.equals("call")?CALL_SCRIPT:ONLY.equals("beatdown")?BEATDOWN_SCRIPT:ONLY.equals("timestop")?TIMESTOP_SCRIPT:ONLY.equals("stagger")?STAGGER_SCRIPT:ONLY.equals("boss")?BOSS_SCRIPT:ONLY.equals("arrow")?ARROW_SCRIPT:SCRIPT;
    /** Walking speed windows (script ticks, inclusive) and the lens's FOV windows (script ticks) logged by the caster. */
    private static final int[][] SPEED={{282,312},{330,360},{3340,3375}},LENS={{126,142},{312,340},{3320,3400}};
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mp-writer");t.setDaemon(true);return t;});
    private final Map<String,Object> flags=new HashMap<>();
    private final Map<UUID,long[]> casts=new HashMap<>();private net.minecraft.util.math.Vec3d lastPos;
    private MinecraftClient mc;private int phase,age,frames;private boolean caster;private final Set<Integer> held=new HashSet<>();
    private float quakePeak,overwhelmPeak,lightPeak,wallPeak;private net.minecraft.util.math.Vec3d walkLast;private long walkStart=-1;
    private final List<Double> steps=new ArrayList<>();
    private net.minecraft.util.math.Vec3d speedLast;private final List<Double> speedSteps=new ArrayList<>();private float lensMin=9,lensMax=0;

    @Override public void onInitializeClient(){
        if(ROLE==null)return;caster=ROLE.equals("caster");
        ClientTickEvents.END_CLIENT_TICK.register(client->{mc=client;try{tick();}catch(Throwable t){fail(t);}});
    }
    private Path dir(){return OUT.resolve(ROLE);}
    private void note(String line){
        String entry=(mc.world==null?-1:mc.world.getTime())+" ["+age+"] "+line+"\n";
        try{Files.createDirectories(dir());Files.writeString(dir().resolve("log.txt"),entry,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}
    }
    private void fail(Throwable t){
        var w=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(w));note("ERROR "+w);
    }
    private void flag(String name,Object value){if(!Objects.equals(flags.put(name,value),value))note(name+" = "+value);}
    private void key(int key,boolean down){mc.keyboard.onKey(mc.getWindow().getHandle(),key,0,down?GLFW_PRESS:GLFW_RELEASE,0);if(down)held.add(key);else held.remove(key);}
    /**
     * (2.107.0) One of Minecraft's own mouse handlers, by its name here (dev) or its intermediary one (the bot mod runs
     * remapped, with no refmap: names in strings are not remapped, as MpLateTimeMixin's are not).
     */
    private static java.lang.reflect.Method mouse(String named,String intermediary,Class<?>... types)throws ReflectiveOperationException{
        java.lang.reflect.Method m;
        try{m=net.minecraft.client.Mouse.class.getDeclaredMethod(named,types);}catch(NoSuchMethodException e){m=net.minecraft.client.Mouse.class.getDeclaredMethod(intermediary,types);}
        m.setAccessible(true);return m;
    }
    /** (2.107.0) A mouse button through Minecraft's own mouse handler (the right one turns the map's camera while held). */
    private void mouseButton(int button,boolean down){
        try{mouse("onMouseButton","method_1601",long.class,int.class,int.class,int.class).invoke(mc.mouse,mc.getWindow().getHandle(),button,down?GLFW_PRESS:GLFW_RELEASE,0);}catch(ReflectiveOperationException e){fail(e);}
    }
    /**
     * (2.107.0, MP_ONLY=death) Each tick: the caster's own death as its client has it (any screen it is shown, its
     * health, whether it is dead, its slot: each change), its drag moving the mouse 30 pixels a tick through Minecraft's
     * own cursor handler for 20 ticks and letting go (the camera's way before and after); the watcher's view of the
     * caster's body (there or not, alive or lying dead, where: each change).
     */
    private void deathWatch(){
        if(caster){
            flag("my health",String.format(Locale.ROOT,"%.1f (%s)",mc.player.getHealth(),mc.player.isAlive()?"alive":"dead"));
            flag("my slot",mc.player.getInventory().selectedSlot);
            flag("death view",DeathView.active());
            // (2.107.1) its own body in its own world (gone a second on, as Minecraft's), and the watcher (still told of)
            flag("my body",mc.world.getEntityById(mc.player.getId())==mc.player?"in my world":"gone ("+mc.player.getRemovalReason()+", death time "+mc.player.deathTime+")");
            var w=mc.world.getPlayers().stream().filter(q->q!=mc.player).findFirst().orElse(null);
            flag("the watcher",w==null?"not seen":String.format(Locale.ROOT,"seen at %.0f %.0f %.0f",w.getX(),w.getY(),w.getZ()));
            if(dragAt>=0&&age>dragAt&&age<=dragAt+20){
                try{mouse("onCursorPos","method_1600",long.class,double.class,double.class).invoke(mc.mouse,mc.getWindow().getHandle(),mc.mouse.getX()+30,mc.mouse.getY());}catch(ReflectiveOperationException e){fail(e);}
            }
            if(dragAt>=0&&age==dragAt+21){mouseButton(GLFW_MOUSE_BUTTON_RIGHT,false);
                note(String.format(Locale.ROOT,"right button up: the camera turned %.1f -> %.1f (body yaw %.1f, %s)",dragFrom,BotandCamera.STATE.yaw,mc.player.getYaw(),mc.player.isAlive()?"alive":"dead"));dragAt=-1;}
            return;
        }
        var c=mc.world.getPlayers().stream().filter(q->q!=mc.player).findFirst().orElse(null);
        flag("caster's body",c==null?"none":String.format(Locale.ROOT,"%s at %.0f %.0f %.0f",c.isAlive()?"alive":"lying dead",c.getX(),c.getY(),c.getZ()));
        // (2.107.1) the death time it was last seen at, dead, before it went (Minecraft's dead go at 20)
        if(c!=null&&!c.isAlive())bodyLast=c.deathTime;
        else if(c==null&&bodyLast>=0){note("the caster's body went after death time "+bodyLast);bodyLast=-1;}
        else if(c!=null)bodyLast=-1;
    }
    /** (2.107.1, MP_ONLY=death) The death time the watcher last saw the caster's dead body at (-1: none seen dead). */
    private int bodyLast=-1;
    private void attack(boolean down){var k=InputUtil.Type.MOUSE.createFromCode(GLFW_MOUSE_BUTTON_LEFT);KeyBinding.setKeyPressed(k,down);if(down)KeyBinding.onKeyPressed(k);}
    private String name(UUID id){var p=mc.world.getPlayerByUuid(id);return p==null?id.toString().substring(0,8):p.getGameProfile().getName();}
    /** The watcher looks a little up at G's pillar from outside; otherwise both look slightly down at each other. */
    /** (2.93.0) The sword of the caster's own formation nearest {@code deg} degrees clockwise of where the camera looks, 3.5 to 10 blocks off, not the one it stands on (-1 for none). */
    private int pickSword(float deg){
        var mids=dev.sangwoo.celestialfall.client.isosang.SwordStep.middles();if(mids==null)return -1;int on=dev.sangwoo.celestialfall.client.isosang.SwordStep.perchNow(),best=-1;double bestD=1e9;
        float want=BotandCamera.STATE.yaw+deg;var at=mc.player.getPos();
        for(int k=0;k<mids.length;k++){if(k==on)continue;double dx=mids[k].x-at.x,dz=mids[k].z-at.z,far=Math.hypot(dx,dz);if(far<3.5||far>10)continue;
            double off=Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx,dz))-want));if(off<bestD){bestD=off;best=k;}}
        return best;
    }
    /** (2.93.0) The camera's yaw and pitch for its middle to fall on {@code target} (the shoulder camera's offset and H's pull taken in). */
    private float[] aimAt(net.minecraft.util.math.Vec3d target){
        float yaw=BotandCamera.STATE.yaw,pitch=BotandCamera.STATE.pitch,keepYaw=yaw,keepPitch=pitch;
        var eye=mc.player.getPos().add(0,mc.player.getStandingEyeHeight()+.8*Math.max(0,dev.sangwoo.celestialfall.client.isosang.IsosangClient.lift(mc.player.getUuid(),1)),0);
        double pull=dev.sangwoo.celestialfall.client.isosang.IsosangClient.cameraPull(1);
        for(int i=0;i<8;i++){
            BotandCamera.STATE.yaw=yaw;BotandCamera.STATE.pitch=pitch;var o=BotandCamera.STATE.offset(BotandCamera.STATE.distance,BotandCamera.shoulder(1));
            var off=new net.minecraft.util.math.Vec3d(o.x(),o.y(),o.z());var cam=eye.add(off).add(off.lengthSquared()>1e-6?off.normalize().multiply(pull):net.minecraft.util.math.Vec3d.ZERO);
            var d=target.subtract(cam);yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
        }
        BotandCamera.STATE.yaw=keepYaw;BotandCamera.STATE.pitch=keepPitch;
        return new float[]{yaw,pitch};
    }
    // (2.107.0, MP_ONLY=death) dead, the caster's camera is its own mouse's (it turns it): never set back
    private void face(){if(mc.player==null||ONLY.equals("death")&&caster&&!mc.player.isAlive())return;BotandCamera.STATE.yaw=caster?0:180;BotandCamera.STATE.pitch=!caster&&!AtomicClient.CASTS.isEmpty()?-8:caster?casterPitch:12;}
    /** The caster's camera pitch as it presses a key ("PITCHn", 2.81.1: all but level to reach a foe 25 blocks off with its crosshair). */
    private float casterPitch=12;

    private void tick()throws Exception{
        age++;
        switch(phase){
            case 0->{if(mc.currentScreen!=null&&mc.getOverlay()==null&&age>40){
                var o=mc.options;o.getViewDistance().setValue(5);o.getMaxFps().setValue(30);o.getGuiScale().setValue(2);o.pauseOnLostFocus=false;
                mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                note("connecting "+ADDRESS);ConnectScreen.connect(mc.currentScreen,mc,ServerAddress.parse(ADDRESS),new ServerInfo("mp",ADDRESS,false),false);phase=1;age=0;}}
            case 1->{
                if(age>2400){note("ERROR never ready");phase=3;age=0;return;}
                if(mc.world==null||mc.player==null)return;
                if(mc.currentScreen!=null){flag("screen",mc.currentScreen.getClass().getName());mc.setScreen(null);}
                boolean ready=mc.world.getPlayers().size()>=2&&age>100&&(!caster||ONLY.equals("arrow")||StandClient.visible(mc.player));
                if(ready){note("ready players="+mc.world.getPlayers().size()+" kageno="+StandClient.visible(mc.player));face();phase=2;age=0;}}
            case 2->{
                mc.onWindowFocusChanged(true);
                if(mc.currentScreen!=null){flag("screen",mc.currentScreen.getClass().getName());if(!interfaceStep())mc.setScreen(null);}
                observe();
                if(traceUntil>0&&age<=traceUntil)trace();
                if(ONLY.equals("boss"))bossWatch();
                if(ONLY.equals("arrow"))arrowWatch();
                if(ONLY.equals("isosanggauge")||ONLY.equals("isosang")||ONLY.equals("isosange")||ONLY.equals("isosangr")||ONLY.equals("isosangh")||ONLY.equals("isosangg")||ONLY.equals("isosangz")||ONLY.equals("isosangdodge")||ONLY.equals("isosangvolley")||ONLY.equals("isosangt")||ONLY.equals("isosangy")||ONLY.equals("isosanghh"))isosangWatch();
                if(ONLY.equals("isosangvolley"))volleyWatch();
                if(ONLY.equals("isosangt"))tWatch();
                if(ONLY.equals("isosangy"))yWatch();
                if(ONLY.equals("isosanghh"))finaleWatch();
                if(ONLY.equals("isosangz")||ONLY.equals("isosangdodge"))zWatch();
                if(ONLY.equals("death"))deathWatch();
                if(ONLY.equals("xchara"))xcharaWatch();
                if(ONLY.equals("xcharam1"))knifeWatch();
                if(ONLY.equals("xcharaq")||ONLY.equals("xcharaplain")){knifeWatch();hackWatch();}
                if(ONLY.equals("xcharae")){hackWatch();healWatch();xcharaWatch();knifeWatch();cloneWatch();}
                if(ONLY.equals("xcharat")){hackWatch();healWatch();knifeWatch();weakenWatch();}
                if(ONLY.equals("xcharay")){hackWatch();pullWatch();}
                if(ONLY.equals("xcharaslam")){hackWatch();slamWatch();}
                if(age%4==0)shot();
                if(caster){
                    if(age%40==0&&held.isEmpty())face();
                    measure();
                    if(ONLY.equals("glow"))glowWatch();
                    if(ONLY.equals("vanilla"))vanillaWatch();
                    for(String step:STEPS){String[] s=step.split(" ");int at=Integer.parseInt(s[0]);if(age==at)act(s[1],true);if(age==at+1&&!s[1].endsWith("+")&&!s[1].endsWith("-"))act(s[1],false);}
                }else{
                    if((age%20==0||!AtomicClient.CASTS.isEmpty()&&age%5==0)&&StandClient.cinematic()==null)face();
                    walk();
                    if(age%20==0&&Files.exists(OUT.resolve("done"))){note("caster done");phase=3;age=0;}
                    if(guardUntil>0&&age>=guardUntil){key(GLFW_KEY_F,false);note("release F (guard)");guardUntil=0;}
                    // 2.64.0: the guard the caster's script asks for (the file OUT/guard), and a respawn once dead
                    boolean guard=Files.exists(OUT.resolve("guard"));
                    if(guard!=guardAsked){guardAsked=guard;key(GLFW_KEY_F,guard);note((guard?"press":"release")+" F (guard, asked by the caster)");}
                    if(!mc.player.isAlive()){if(++deadFor==40){note("dead: respawn requested");mc.player.requestRespawn();}}else deadFor=0;
                    if(ONLY.equals("stagger"))staggerWatch();
                    if(ONLY.equals("resist"))resistWatch();
                    if(ONLY.equals("glow"))glowWatch();
                    if(ONLY.equals("vanilla"))vanillaWatch();
                    if(ONLY.equals("isosanggauge")||ONLY.equals("isosang")||ONLY.equals("isosange")||ONLY.equals("isosangr")||ONLY.equals("isosangh")||ONLY.equals("isosangg")||ONLY.equals("isosangz")||ONLY.equals("isosangdodge")||ONLY.equals("isosangvolley")||ONLY.equals("isosangt")||ONLY.equals("isosangy")||ONLY.equals("isosanghh"))watcherStill();
                    if(age>(ONLY.equals("xcharaslam")?1000:ONLY.equals("xcharay")?700:ONLY.equals("xcharat")?1500:ONLY.equals("xcharae")?2400:ONLY.equals("xcharaplain")?1000:ONLY.equals("xcharaq")?900:ONLY.equals("xcharam1")?1200:ONLY.equals("isosanggauge")?1000:ONLY.equals("xchara")?760:ONLY.equals("vanilla")?800:ONLY.equals("glow")?1500:ONLY.equals("isosangg")?700:ONLY.equals("isosangdodge")||ONLY.equals("isosangvolley")||ONLY.equals("isosangt")||ONLY.equals("isosangy")||ONLY.equals("isosanghh")?600:ONLY.equals("isosangh")||ONLY.equals("isosangz")?1300:ONLY.equals("isosangr")?1800:ONLY.equals("isosang")||ONLY.equals("isosange")?900:ONLY.equals("beatdown")?1600:ONLY.equals("timestop")||ONLY.equals("resist")?1000:ONLY.equals("stagger")?1400:ONLY.equals("boss")?2600:ONLY.equals("arrow")?1100:ONLY.equals("call")?900:ONLY.equals("cooldowns")?3400:ONLY.equals("swap")?1300:ONLY.equals("fresh")?1700:ONLY.equals("death")?1200:4400)){note("ERROR watcher timeout");phase=3;age=0;}
                }
                if(phase==2&&(mc.world==null||mc.player==null)){note("ERROR lost connection");phase=3;age=0;}
            }
            case 3->{if(age==20){writer.shutdown();writer.awaitTermination(60,TimeUnit.SECONDS);note("frames "+frames);mc.scheduleStop();}}
            // 2.73.0 (LEAVE): away from the server, then joining it again
            case 4->{
                if(mc.world!=null)return;
                if(age==1)note("left the server ("+(mc.currentScreen==null?"no screen":mc.currentScreen.getClass().getSimpleName())+")");
                if(age>=100&&mc.currentScreen!=null){note("joining again");ConnectScreen.connect(mc.currentScreen,mc,ServerAddress.parse(ADDRESS),new ServerInfo("mp",ADDRESS,false),false);phase=5;age=0;}
            }
            case 5->{
                if(age>2400){note("ERROR never back");phase=3;age=0;return;}
                if(mc.world!=null&&late&&++lateFor>30){late=false;note("the world's time let through again ("+dropped.get()+" time packets dropped since joining)");}
                if(mc.world!=null)trace();
                if(mc.world==null||mc.player==null)return;
                if(mc.currentScreen!=null){flag("screen",mc.currentScreen.getClass().getName());mc.setScreen(null);}
                // (2.107.1) the other one seen: one back dead has its own body gone from its world a second on (as Minecraft's)
                if(mc.world.getPlayers().stream().anyMatch(q->q!=mc.player)&&age>60){note("back in the game, the script at "+resumeAt+(mc.player.isAlive()?"":" (dead)"));flags.clear();face();age=resumeAt;phase=2;traceUntil=resumeAt+100;}
            }
        }
    }
    /**
     * During G the watcher walks forward for 30 ticks outside the column (cast age 280) and again inside it (325; the
     * server moves it in at 320 and out at 360) on the real movement path, and logs its walking speed: the median
     * distance per tick on the ground once it is up to speed, leaving out the ticks after a hit (knockback).
     */
    private void walk(){
        var cast=AtomicClient.CASTS.values().stream().findFirst().orElse(null);if(cast==null||mc.player==null)return;
        long a=mc.world.getTime()-cast.start;var p=mc.player.getPos();
        if(walkStart<0&&(a==280||a==325)){walkStart=a;walkLast=p;steps.clear();key(GLFW_KEY_W,true);return;}
        if(walkStart<0)return;
        if(a-walkStart>=6&&mc.player.isOnGround()&&mc.player.hurtTime==0)steps.add(Math.hypot(p.x-walkLast.x,p.z-walkLast.z));
        walkLast=p;
        if(a>=walkStart+30){
            key(GLFW_KEY_W,false);steps.sort(null);double median=steps.isEmpty()?0:steps.get(steps.size()/2);
            note(String.format(Locale.ROOT,"walking %.2f blocks/s on the ground at cast age %d (median of %d ticks, %s)",median*20,a,steps.size(),AtomicClient.held(mc.player)?"held":"free"));
            walkStart=-1;
        }
    }
    /** The caster's walking speed in each SPEED window (median per tick on the ground) and the lens's FOV multiplier range in each LENS window. */
    private void measure(){
        var p=mc.player.getPos();
        for(int[] w:SPEED){
            if(age==w[0]){speedLast=p;speedSteps.clear();}
            else if(age>w[0]&&age<=w[1]){if(mc.player.isOnGround())speedSteps.add(Math.hypot(p.x-speedLast.x,p.z-speedLast.z));speedLast=p;}
            if(age==w[1]){var sorted=new ArrayList<>(speedSteps);sorted.sort(null);double median=sorted.isEmpty()?0:sorted.get(sorted.size()/2);
                note(String.format(Locale.ROOT,"walking %.2f blocks/s from script tick %d to %d (median of %d ticks)",median*20,w[0],w[1],sorted.size()));}
        }
        for(int[] w:LENS){
            if(age>=w[0]&&age<=w[1]){float f=mc.player.getFovMultiplier();lensMin=Math.min(lensMin,f);lensMax=Math.max(lensMax,f);}
            if(age==w[1]){note(String.format(Locale.ROOT,"lens FOV multiplier %.3f to %.3f from script tick %d to %d",lensMin,lensMax,w[0],w[1]));lensMin=9;lensMax=0;}
        }
    }
    /** The 2.45.0 steps keep the pause menu and the death screen up (the rest of the script closes every screen). */
    private boolean interfaceStep(){return caster&&(age>=3060||ONLY.equals("cooldowns")||ONLY.equals("fresh"))&&(mc.currentScreen instanceof dev.sangwoo.celestialfall.client.ui.BotandPauseScreen||mc.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen)
        // (2.74.2: the Rokakaka's confirmation stays up for its 사용하고 초기화)
        ||caster&&ONLY.equals("swap")&&mc.currentScreen instanceof dev.sangwoo.celestialfall.client.commerce.CommerceScreen;}
    private void act(String step,boolean down){
        if(step.equals("END")){if(down){note("script end");try{Files.writeString(OUT.resolve("done"),"done");}catch(Exception ignored){}phase=3;age=0;}return;}
        if(step.startsWith("TURN")){if(down){BotandCamera.STATE.yaw=(caster?0:180)+Float.parseFloat(step.substring(4));note("camera turned to "+BotandCamera.STATE.yaw);}return;}
        if(step.equals("RESPAWN")){if(down){note("respawn requested (alive="+mc.player.isAlive()+")");mc.player.requestRespawn();}return;}
        // 2.73.0: leaving the server as the pause menu's 나가기 does (on the next frame, not in the middle of this tick),
        // and dying another way than the reset
        if(step.equals("LEAVE")){if(down){note("leave the server");resumeAt=age;phase=4;age=0;if(ONLY.equals("fresh")){late=true;lateFor=0;dropped.set(0);}mc.send(()->dev.sangwoo.celestialfall.client.title.TitlePortal.leaveMap(mc,true));}return;}
        if(step.equals("KILL")){if(down)command("kill @s");return;}
        // (2.109.0, MP_ONLY=glow) the cooldowns off or on for the caster; the watcher asked to strike as the caster's glow begins (the file OUT/jab)
        // (2.110.0, MP_ONLY=vanilla) the watcher's vanilla mode on or off, as it would type it (the caster's operator rights)
        if(step.equals("WVANILLA")){if(down)command("execute as Watcher run botand vanilla");return;}
        if(step.equals("NOCDON")||step.equals("NOCDOFF")){if(down)command("botand nocooldown "+(step.equals("NOCDON")?"on":"off"));return;}
        if(step.equals("JAB+")||step.equals("JAB-")){if(down){try{if(step.equals("JAB+"))Files.writeString(OUT.resolve("jab"),"jab");else Files.deleteIfExists(OUT.resolve("jab"));}catch(Exception e){note("ERROR "+e);}note(step.equals("JAB+")?"the watcher asked to strike as the glow begins":"the watcher asked to strike no more");}return;}
        if(step.equals("SLAIN")){if(down)command("damage @s 1000 minecraft:player_attack by Watcher");return;}
        if(step.equals("DUMMY")){if(down){note("command botand dummy atomic 30");mc.player.networkHandler.sendChatCommand("botand dummy atomic 30");}return;}
        if(step.equals("STAND")){if(down){note("command botand progress stand @s star_platinum");mc.player.networkHandler.sendChatCommand("botand progress stand @s star_platinum");}return;}
        // 2.74.2: the caster takes Kageno; a Rokakaka in its main hand, eaten as a left click with it asks and as the
        // confirmation's 사용하고 초기화 is pressed
        if(step.equals("STANDK")){if(down)command("botand progress stand @s kageno");return;}
        // 2.77.0: the caster takes Lee So-sang (MP_ONLY=isosang)
        if(step.equals("ISOSANG")){if(down)command("botand progress stand @s isosang");return;}
        // (2.113.0) the caster's H gauge full (H needs it), and the caster's cooldowns off
        if(step.equals("GAUGE")){if(down)command("botand gauge @s");return;}
        if(step.equals("NOCOOL")){if(down)command("botand nocooldown on @s");return;}
        // 2.112.0: the caster takes X Chara (MP_ONLY=xchara)
        if(step.equals("XCHARA")){if(down)command("botand progress stand @s x_chara");return;}
        if(step.startsWith("PITCH")){if(down){casterPitch=Float.parseFloat(step.substring(5));note("caster's camera pitch "+casterPitch);}return;}
        // 2.93.0: Lee So-sang's Z: the camera onto a sword of the caster's formation ("ZAIMdeg"), and Z pressed where it looks
        if(step.startsWith("ZAIM")){if(down){var mids=dev.sangwoo.celestialfall.client.isosang.SwordStep.middles();int k=pickSword(Float.parseFloat(step.substring(4)));
            if(k<0||mids==null)note("ERROR no sword to aim at");
            else{float[] a=aimAt(mids[k]);BotandCamera.STATE.yaw=a[0];BotandCamera.STATE.pitch=a[1];
                note(String.format(Locale.ROOT,"aim at sword %d (on %d), %.1f blocks off (camera yaw %.1f pitch %.1f)",k,dev.sangwoo.celestialfall.client.isosang.SwordStep.perchNow(),Math.hypot(mids[k].x-mc.player.getX(),mids[k].z-mc.player.getZ()),a[0],a[1]));}}return;}
        // (2.95.0) Lee So-sang's H basic attack: the camera onto the watcher's chest ("VAIM") or onto the ground "VGROUNDdeg"
        // (deg degrees clockwise of where the camera looks, 10 blocks from the caster), and the attack where it looks ("VM1":
        // not turned back to the script's way first, as other keys are)
        if(step.equals("VAIM")||step.startsWith("VGROUND")){if(down){
            net.minecraft.util.math.Vec3d to;
            if(step.equals("VAIM")){var w=mc.world.getPlayers().stream().filter(q->q!=mc.player).findFirst().orElse(null);if(w==null){note("ERROR no watcher to aim at");return;}to=w.getPos().add(0,w.getHeight()*.55,0);}
            else{var f=net.minecraft.util.math.Vec3d.fromPolar(0,BotandCamera.STATE.yaw+Float.parseFloat(step.substring(7)));var at=mc.player.getPos().add(f.multiply(10));
                to=new net.minecraft.util.math.Vec3d(at.x,mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,(int)Math.floor(at.x),(int)Math.floor(at.z))+.05,at.z);}
            float[] a=aimAt(to);BotandCamera.STATE.yaw=a[0];BotandCamera.STATE.pitch=a[1];
            note(String.format(Locale.ROOT,"camera onto %s (%.2f %.2f %.2f), yaw %.1f pitch %.1f",step.equals("VAIM")?"the watcher":"the ground",to.x,to.y,to.z,a[0],a[1]));}return;}
        if(step.equals("VM1")){if(down){var aim=dev.sangwoo.celestialfall.client.isosang.IsosangClient.aimNow(mc);
                note(aim==null?"press the attack: no volley aim (off the sword)":String.format(Locale.ROOT,"press the attack: its crosshair picks %s at %.2f %.2f %.2f",aim.target()<0?"no one":mc.world.getEntityById(aim.target())==mc.player?"me":mc.world.getEntityById(aim.target()) instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():"entity "+aim.target(),aim.point().x,aim.point().y,aim.point().z));
                attack(true);}else attack(false);return;}
        // (2.96.0) Lee So-sang's T where the camera looks ("VT": not turned back to the script's way first), its crosshair's pick logged
        if(step.equals("VT")){if(down){var aim=dev.sangwoo.celestialfall.client.isosang.IsosangClient.lungeAimNow(mc);
                note(aim==null?"press T: no aim (off the sword)":String.format(Locale.ROOT,"press T: its crosshair picks %s at %.2f %.2f %.2f",aim.target()<0?"no one":mc.world.getEntityById(aim.target())==mc.player?"me":mc.world.getEntityById(aim.target()) instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():"entity "+aim.target(),aim.point().x,aim.point().y,aim.point().z));
                key(GLFW_KEY_T,true);}else key(GLFW_KEY_T,false);return;}
        // (2.97.0) Lee So-sang's Y where the camera looks ("VY"), its crosshair's pick logged
        if(step.equals("VY")){if(down){var aim=dev.sangwoo.celestialfall.client.isosang.IsosangClient.cageAimNow(mc);
                note(aim==null?"press Y: no aim (off the sword)":String.format(Locale.ROOT,"press Y: its crosshair picks %s at %.2f %.2f %.2f",aim.target()<0?"no one":mc.world.getEntityById(aim.target())==mc.player?"me":mc.world.getEntityById(aim.target()) instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():"entity "+aim.target(),aim.point().x,aim.point().y,aim.point().z));
                key(GLFW_KEY_Y,true);}else key(GLFW_KEY_Y,false);return;}
        if(step.equals("Z")){if(down){note(String.format(Locale.ROOT,"press Z: it aims at sword %d (on %d, Z left %d)",dev.sangwoo.celestialfall.client.isosang.SwordStep.aimedForFilm(),dev.sangwoo.celestialfall.client.isosang.SwordStep.perchNow(),
                dev.sangwoo.celestialfall.client.isosang.IsosangClient.zState(mc.player.getUuid()).left(mc.world.getTime())));key(GLFW_KEY_Z,true);}else key(GLFW_KEY_Z,false);return;}
        if(step.equals("FRUIT")){if(down)command("item replace entity @s weapon.mainhand with celestialfall:rokakaka");return;}
        if(step.equals("EAT")){if(down){note("left click with "+mc.player.getMainHandStack().getItem()+" (the Rokakaka's ask)");dev.sangwoo.celestialfall.client.commerce.CommerceClient.clickFruit();}return;}
        if(step.equals("CONFIRM")){if(down){
            if(!(mc.currentScreen instanceof dev.sangwoo.celestialfall.client.commerce.CommerceScreen cs)||!cs.confirmation){note("ERROR no Rokakaka confirmation up ("+(mc.currentScreen==null?"no screen":mc.currentScreen.getClass().getSimpleName())+")");return;}
            for(var c:cs.children())if(c instanceof net.minecraft.client.gui.widget.PressableWidget b&&b.getMessage().getString().equals("사용하고 초기화")){note("press 사용하고 초기화 (active "+b.active+")");b.onPress();return;}
            note("ERROR no 사용하고 초기화 on the confirmation");}return;}
        // 2.59.0: the watcher back 2.5 blocks in front of the caster, facing it, for the heavy punch
        // 2.60.0: the watcher takes Kageno (its skills, refused while the Star Finger has it incapacitated)
        if(step.equals("KAGENO")){if(down){String c="botand progress stand Watcher kageno";note("command "+c);mc.player.networkHandler.sendChatCommand(c);}return;}
        // 2.75.0: the watcher given Star Platinum and the awakening (MP_ONLY=resist)
        if(step.equals("WSTAR")){if(down)command("botand progress stand Watcher star_platinum");return;}
        if(step.equals("WAWAKEN")){if(down)command("botand progress awaken Watcher");return;}
        // (2.68.0: "DISTn", n blocks in front: the time stop's 20 blocks)
        if(step.equals("HOME")||step.equals("FAR")||step.equals("AWAY")||step.startsWith("DIST")){if(down){String c="execute as Caster at @s rotated ~ 0 run tp Watcher ^ ^ ^"+(step.equals("FAR")?"4.5":step.equals("AWAY")?"8":step.startsWith("DIST")?step.substring(4):"2.5")+" facing entity Caster feet";note("command "+c);mc.player.networkHandler.sendChatCommand(c);}return;}
        // (2.101.0) "WPOSf/r": the watcher f blocks ahead of the caster and r to its right, facing it
        if(step.startsWith("WPOS")){if(down){String[] v=step.substring(4).split("/");String c="execute as Caster at @s rotated ~ 0 run tp Watcher ^"+(-Double.parseDouble(v[1]))+" ^ ^"+v[0]+" facing entity Caster feet";note("command "+c);mc.player.networkHandler.sendChatCommand(c);}return;}
        // 2.68.0: the watcher's walk asked for (the file OUT/walk), as its guard is
        if(step.startsWith("WALK")){if(down){try{if(step.endsWith("+"))Files.writeString(OUT.resolve("walk"),"walk");else Files.deleteIfExists(OUT.resolve("walk"));}catch(Exception e){fail(e);}note("watcher's walk "+(step.endsWith("+")?"asked for":"stopped"));}return;}
        // 2.64.0: the watcher at full health, or brought down to a quarter of it; its guard asked for
        // (2.94.0) the caster struck by the watcher (a blow from someone: Lee So-sang's H's dodge keeps the first from it)
        if(step.equals("CHURT")){if(down)command("damage Caster 120 minecraft:generic");return;}
        if(step.equals("HITME")){if(down)command("damage Caster 4 minecraft:player_attack by Watcher");return;}
        if(step.equals("HEAL")||step.equals("HURT")){if(down){String c=step.equals("HEAL")?"effect give Watcher minecraft:instant_health 1 20 true":"damage Watcher 150 minecraft:generic";note("command "+c);mc.player.networkHandler.sendChatCommand(c);}return;}
        // 2.70.0: the stand arrow's steps (ARROW_SCRIPT)
        if(step.equals("NOSTAND")){if(down)command("botand progress stand @s none");return;}
        // 2.72.0: the awakening (Star Platinum's time stop is the awakened's), by the operator's command
        if(step.equals("AWAKEN")||step.equals("UNAWAKEN")){if(down)command("botand progress awaken @s"+(step.equals("AWAKEN")?"":" off"));return;}
        if(step.equals("ARROWS")){if(down)command("item replace entity @s weapon.mainhand with celestialfall:stand_arrow 2");return;}
        if(step.equals("USE")){if(down){note("use the item in the main hand ("+mc.player.getMainHandStack().getItem()+")");mc.interactionManager.interactItem(mc.player,net.minecraft.util.Hand.MAIN_HAND);}return;}
        // 2.74.1: the gacha, its next pull made an SSR by the operator's count, asked as the screen's 1회 does
        if(step.equals("PITY")){if(down)command("botand gacha set @s 99 0");return;}
        if(step.equals("PULL")){if(down){note("gacha: one pull asked");dev.sangwoo.celestialfall.client.gacha.GachaClient.request(1);}return;}
        if(step.equals("PULLED")){if(down)note("gacha answered: \""+dev.sangwoo.celestialfall.client.gacha.GachaClient.notice+"\", own stand "+stand(mc.player));return;}
        if(step.equals("RIGSTAR")||step.equals("RIGDIE")){if(down){try{Files.writeString(OUT.resolve("rig"),step.equals("RIGDIE")?"death":"star_platinum");}catch(Exception e){fail(e);}note("next arrow asked to "+(step.equals("RIGDIE")?"kill":"awaken Star Platinum"));}return;}
        // 2.69.0: the boss's steps (BOSS_SCRIPT)
        if(step.equals("WLOW")){if(down)command("damage Watcher 120 minecraft:generic");return;}
        if(step.equals("BOSSHERE")){if(down)command("execute at Caster rotated 90 0 positioned ^ ^ ^3 rotated 270 0 run botand boss jotaro spawn here");return;}
        if(step.equals("HITBOSS")){
            // the camera (and so the punch) on him, then the attack for a tick
            if(down){var b=boss();if(b==null){note("ERROR no boss to strike");return;}
                var v=b.getPos().add(0,1,0).subtract(mc.player.getEyePos());BotandCamera.STATE.yaw=(float)Math.toDegrees(Math.atan2(-v.x,v.z));BotandCamera.STATE.pitch=(float)-Math.toDegrees(Math.atan2(v.y,v.horizontalLength()));
                note(String.format(Locale.ROOT,"strike the boss %.1f blocks away (camera yaw %.0f)",b.distanceTo(mc.player),BotandCamera.STATE.yaw));attack(true);}
            else attack(false);
            return;}
        if(step.startsWith("BOSSDMG")){if(down){var b=boss();if(b==null)note("ERROR no boss");else command("damage "+b.getUuid()+" "+step.substring(7)+" minecraft:generic");}return;}
        if(step.equals("BOSSKILL")){if(down){var b=boss();if(b==null)note("ERROR no boss");else command("damage "+b.getUuid()+" 9999 minecraft:out_of_world by Caster");}return;}
        if(step.equals("OPENBOX")){if(down){try{Files.writeString(OUT.resolve("openbox"),"open");}catch(Exception e){fail(e);}note("the watcher is to open a box");}return;}
        if(step.equals("FLEE")){if(down){command("execute at Caster run tp Watcher ~42 ~ ~2");command("execute at Caster run tp Caster ~40 ~ ~");}return;}
        if(step.equals("RETURN")){if(down){var b=boss();if(b==null)note("ERROR no boss");else{command("execute at "+b.getUuid()+" run tp Caster ~ ~ ~6");command("execute at "+b.getUuid()+" run tp Watcher ~2 ~ ~7");}}return;}
        if(step.startsWith("GUARD")){if(down){try{if(step.endsWith("+"))Files.writeString(OUT.resolve("guard"),"guard");else Files.deleteIfExists(OUT.resolve("guard"));}catch(Exception e){fail(e);}note("watcher's guard "+(step.endsWith("+")?"asked for":"let down"));}return;}
        // (2.107.0, MP_ONLY=death) the right button held and the mouse moved 30 pixels a tick for 20 ticks, then let go
        // (deathWatch moves it), and the wheel turned down twice, all through Minecraft's own mouse handlers
        if(step.equals("DRAG")){if(down){dragAt=age;dragFrom=BotandCamera.STATE.yaw;mouseButton(GLFW_MOUSE_BUTTON_RIGHT,true);
            note(String.format(Locale.ROOT,"right button down (cursor %s), camera yaw %.1f",mc.mouse.isCursorLocked()?"captured":"free",dragFrom));}return;}
        if(step.equals("WHEELOUT")){if(down){double zoom=BotandCamera.STATE.targetDistance;
            try{var m=mouse("onMouseScroll","method_1598",long.class,double.class,double.class);for(int i=0;i<2;i++)m.invoke(mc.mouse,mc.getWindow().getHandle(),0d,-1d);}catch(ReflectiveOperationException e){fail(e);}
            note(String.format(Locale.ROOT,"wheel down twice: the camera's zoom %.2f -> %.2f, slot %d",zoom,BotandCamera.STATE.targetDistance,mc.player.getInventory().selectedSlot));}return;}
        String k=step.replaceAll("[+-]$","");boolean press=step.endsWith("+")||!step.endsWith("-")&&down;
        if(down&&press&&!k.equals("W"))face();
        if(down||!step.endsWith("+"))note((press?"press ":"release ")+k);
        switch(k){
            case "M1"->attack(press);
            case "F"->key(GLFW_KEY_F,press);
            case "E"->key(GLFW_KEY_E,press);case "R"->key(GLFW_KEY_R,press);case "T"->key(GLFW_KEY_T,press);case "Y"->key(GLFW_KEY_Y,press);
            case "Q"->key(GLFW_KEY_Q,press);case "G"->key(GLFW_KEY_G,press);case "H"->key(GLFW_KEY_H,press);case "P"->key(GLFW_KEY_P,press);
            case "W"->key(GLFW_KEY_W,press);case "C"->key(GLFW_KEY_C,press);
            case "TAB"->key(GLFW_KEY_TAB,press);case "ESC"->key(GLFW_KEY_ESCAPE,press);case "ENTER"->key(GLFW_KEY_ENTER,press);
            case "2"->key(GLFW_KEY_2,press);
            default->note("ERROR unknown step "+step);
        }
    }
    private void observe(){
        long now=mc.world.getTime();
        for(var c:StandClient.CASTS.values())if(!casts.containsKey(c.id())){
            casts.put(c.id(),new long[]{c.skill(),c.start()});
            note("cast "+(c.skill()<SKILLS.length?SKILLS[c.skill()]:c.skill())+" combo="+c.combo()+" by "+name(c.caster())+" age="+String.format(Locale.ROOT,"%.1f",c.age(0))+" targets="+c.targets().size());
        }
        for(var it=casts.entrySet().iterator();it.hasNext();){var e=it.next();if(!StandClient.CASTS.containsKey(e.getKey())){note("end "+SKILLS[(int)e.getValue()[0]]+" age~"+(now-e.getValue()[1]));it.remove();}}
        flag("cinematic",StandClient.cinematic()!=null);flag("mono",StandClient.monochrome());flag("frozen",StandClient.frozen(mc.player));flag("locked",StandClient.locked());
        // 2.42.0: the cooldown list (top first) and whether the danger banner is up for this player
        flag("cooldowns",CooldownHud.shown());
        flag("danger_banner",AtomicClient.CASTS.values().stream().anyMatch(c->c.caster.equals(mc.player.getUuid())&&c.age(0)<dev.sangwoo.celestialfall.AtomicTimeline.END));
        // G seen from here: the strongest camera quake and Veil screen treatment while it lasts, logged when it ends.
        if(!AtomicClient.CASTS.isEmpty()){
            var s=dev.sangwoo.celestialfall.client.AtomicImmersion.at(mc.gameRenderer.getCamera().getPos(),0);
            quakePeak=Math.max(quakePeak,Math.max(Math.abs(s.yaw()),Math.abs(s.pitch())));
            overwhelmPeak=Math.max(overwhelmPeak,dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm.strength());
            lightPeak=Math.max(lightPeak,dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm.radiance());
            // The wall shake this camera gets (degrees, with the shake setting) from the nearest cast.
            for(var c:AtomicClient.CASTS.values()){
                var cam=mc.gameRenderer.getCamera().getPos();float sec=c.age(0)/20f;
                double r=dev.sangwoo.celestialfall.client.atomic.AtomicPillarShape.radius(dev.sangwoo.celestialfall.client.atomic.AtomicStage.visual(sec),c.radius);
                if(!dev.sangwoo.celestialfall.client.atomic.AtomicPillar.interior(c,cam,sec))
                    wallPeak=Math.max(wallPeak,dev.sangwoo.celestialfall.client.atomic.AtomicStage.wallShake(sec,dev.sangwoo.celestialfall.client.AtomicImmersion.wallDistance(cam,c.origin,0),r,c.radius)
                        *(float)dev.sangwoo.celestialfall.CelestialFall.CONFIG.screenShakeStrength);
            }
            flag("veil_overwhelm_active",dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm.active());
        }else if(quakePeak>0||overwhelmPeak>0||lightPeak>0){
            note(String.format(Locale.ROOT,"G seen: quake peak %.2f deg, wall shake amplitude peak %.2f deg, overwhelm peak %.2f, light peak %.2f, veil frames %d",quakePeak,wallPeak,overwhelmPeak,lightPeak,
                dev.sangwoo.celestialfall.client.integration.AtomicOverwhelm.renderedFrames()));
            quakePeak=0;overwhelmPeak=0;lightPeak=0;wallPeak=0;
        }
        flag("atomic",AtomicClient.CASTS.size());flag("overdrive",StandClient.OVERDRIVE.size());flag("hits",dev.sangwoo.celestialfall.client.HitCounterHud.count);
        flag("health",Math.round(mc.player.getHealth()));
        // 2.45.0: the player list (its names, and whether Tab sent it away), the pause menu and its reset question, deaths and chat
        flag("player_list",mc.player.networkHandler.getListedPlayerListEntries().stream().map(e->e.getProfile().getName()).sorted().toList());
        flag("player_list_away",dev.sangwoo.celestialfall.client.ui.PlayerPanel.hidden());
        flag("pause_menu",mc.currentScreen instanceof dev.sangwoo.celestialfall.client.ui.BotandPauseScreen);
        flag("reset_question",mc.currentScreen instanceof dev.sangwoo.celestialfall.client.ui.BotandPauseScreen s&&s.asking());
        flag("alive",mc.player.isAlive());
        // the death message itself is in each client's own log as a [CHAT] line (logs/ROLE-latest.log)
        // G's hold on this player: its speed against its base, and that the slowdown is not taken for an optical zoom.
        var speed=mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        flag("atomic_hold",AtomicClient.held(mc.player)?String.format(Locale.ROOT,"speed x%.2f fov x%.2f",speed.getValue()/speed.getBaseValue(),mc.player.getFovMultiplier()):"off");
        if(lastPos==null||lastPos.squaredDistanceTo(mc.player.getPos())>1){lastPos=mc.player.getPos();note(String.format(Locale.ROOT,"selfpos %.2f %.2f %.2f",lastPos.x,lastPos.y,lastPos.z));}
        var other=mc.world.getPlayers().stream().filter(p->p!=mc.player).findFirst().orElse(null);
        if(other!=null){flag("other_marked",StandClient.shadowMarked(other));flag("other_outlined",StandClient.shadowOutlined(other));}
        // 2.51.0: each player's Star Platinum as this client sees it, and every half second while one is drawn how it
        // follows its user (how far it leans into the walk and how fast it goes)
        flag("own_stand",stand(mc.player));if(other!=null)flag("other_stand",stand(other));
        // 2.54.0: Star Platinum's punches heard here, whether each landed and on whom, and when its shout started
        punches();
        // 2.57.0: its rush heard here, and this player's own cries of pain (only the first blow on them cries out)
        rushes();flag("hurt_cries",hurtCries);
        // 2.61.0: its inhale heard here; held by it, the watcher tries everything and to walk away
        inhales();
        // 2.64.0: its beatdown heard here: its cutscene, its camera and bars here, where its one struck is shown
        beats();
        // 2.67.0: its time stop heard here; stopped, the watcher tries everything and its screen is held
        stops();
        // 2.72.0: each player's stand's call sound playing here, and whether this client's player is awakened
        calls();flag("awakened",dev.sangwoo.celestialfall.client.ProgressClient.awakened);
        // 2.73.0: the long cooldowns this client is told, and the cooldown list's entries
        if(ONLY.equals("cooldowns")||ONLY.equals("swap")||ONLY.equals("fresh"))longCooldowns();
        // 2.74.2: the stand this client is told the player has (whose cooldowns its list shows)
        flag("profile_stand",dev.sangwoo.celestialfall.client.ProgressClient.profile.stand());
        boolean fast=mc.player.getVelocity().horizontalLength()>.6;
        if(fast&&!Boolean.TRUE.equals(flags.get("thrown")))note(String.format(Locale.ROOT,"thrown at %.2f blocks a tick",mc.player.getVelocity().horizontalLength()));
        flag("thrown",fast);
        if(age%10==0)for(var p:mc.world.getPlayers()){var look=dev.sangwoo.celestialfall.client.stand.StandPresence.shown(p.getUuid());
            if(look!=null)note(String.format(Locale.ROOT,"stand of %s drawn: out %.2f, solid %.2f, glow %.2f, aura %.2f on %d bodies, lean %.1f deg, speed %.2f blocks/s (%s)",p.getGameProfile().getName(),look.out,look.alpha,look.glow,
                dev.sangwoo.celestialfall.client.stand.StandPresence.aura(p.getUuid()),dev.sangwoo.celestialfall.client.stand.StandAuraLayer.drawnLast(),look.lean,look.speed,dev.sangwoo.celestialfall.client.stand.StandPresence.describe(p.getUuid())));}
        try{var f=dev.sangwoo.celestialfall.client.kageno.HitFeedback.class.getDeclaredField("NUMBERS");f.setAccessible(true);flag("damage_numbers",((Map<?,?>)f.get(null)).size());}catch(ReflectiveOperationException e){flag("damage_numbers","n/a");}
    }
    private int resumeAt;private String longLogged="";private int traceUntil;private String traced="";
    /**
     * 2.74.3 (MP_ONLY=fresh): from leaving until 30 ticks after the world is back, the world's time packets are dropped
     * (MpLateTimeMixin), as if the server's time came late; how many were.
     */
    private static volatile boolean late;private static final java.util.concurrent.atomic.AtomicInteger dropped=new java.util.concurrent.atomic.AtomicInteger();private int lateFor;
    public static boolean lateTime(){if(late)dropped.incrementAndGet();return late;}
    /**
     * 2.74.3: from joining again until 100 ticks into the resumed script, whenever any changes: the world's time (each
     * line's first number), whether the server has set it yet (CooldownHud.timed: the list reads nothing before), the map
     * on or off here, the long cooldowns told (Atomic's ready tick and length), the stand this client is told, the
     * Atomic sword's cooldown share, and the cooldown list's entries and the ones waiting to come in.
     */
    private void trace(){
        try{
            var listField=CooldownHud.class.getDeclaredField("LIST");listField.setAccessible(true);var list=listField.get(null);
            var waitingField=list.getClass().getDeclaredField("waiting");waitingField.setAccessible(true);
            var clock=CooldownHud.class.getDeclaredMethod("clock",float.class);clock.setAccessible(true);double t=(double)clock.invoke(null,0f);
            // (a jar from before 2.74.3 has no such field: the list then reads at once)
            String timed;try{var timedField=CooldownHud.class.getDeclaredField("timed");timedField.setAccessible(true);timed=timedField.get(null)==mc.world?"set":"not set yet";}catch(NoSuchFieldException e){timed="not waited for (no CooldownHud.timed)";}
            var entries=new ArrayList<String>();
            for(var e:((CooldownList)list).entries())entries.add(String.format(Locale.ROOT,"%s %.0f..%.0f%s",e.name,e.start-t,e.end-t,e.leaving()?" leaving":""));
            String line=String.format(Locale.ROOT,"clock %s, map=%s known=%s atomic=%d len=%d stand=%s sword=%.4f list=%s waiting=%s",timed,
                dev.sangwoo.celestialfall.client.MapClient.enabled(),dev.sangwoo.celestialfall.client.LongCooldownsClient.known(),
                dev.sangwoo.celestialfall.client.LongCooldownsClient.ready(dev.sangwoo.celestialfall.LongCooldowns.Skill.ATOMIC),
                dev.sangwoo.celestialfall.client.LongCooldownsClient.length(dev.sangwoo.celestialfall.LongCooldowns.Skill.ATOMIC),
                dev.sangwoo.celestialfall.client.ProgressClient.profile.stand(),
                mc.player==null?-1:mc.player.getItemCooldownManager().getCooldownProgress(dev.sangwoo.celestialfall.CelestialFall.ATOMIC_SWORD,0),entries,waitingField.get(list));
            if(!line.equals(traced)){traced=line;note("trace (list clock "+String.format(Locale.ROOT,"%.0f",t)+"): "+line);}
        }catch(ReflectiveOperationException e){note("ERROR trace "+e);}
    }
    /**
     * The long cooldowns as this client is told them (2.73.0): whenever they change, each one's ready tick, the seconds
     * from now and its length; and whenever the cooldown list changes, each entry with its length and what is left.
     */
    private void longCooldowns(){
        var c=dev.sangwoo.celestialfall.client.LongCooldownsClient.class;long now=mc.world.getTime();
        var line=new StringBuilder();
        for(var skill:dev.sangwoo.celestialfall.LongCooldowns.Skill.values()){
            long ready=dev.sangwoo.celestialfall.client.LongCooldownsClient.ready(skill);int length=dev.sangwoo.celestialfall.client.LongCooldownsClient.length(skill);
            line.append(String.format(Locale.ROOT," %s ready %d (%s) of %.0f s;",skill.name().toLowerCase(Locale.ROOT),ready,ready>now?String.format(Locale.ROOT,"%.1f s from now",(ready-now)/20.):"none",length/20.));
        }
        String key=dev.sangwoo.celestialfall.client.LongCooldownsClient.known()+line.toString().replaceAll("\\([0-9.]+ s from now\\)","");
        if(!key.equals(longLogged)){longLogged=key;note("long cooldowns told (known "+dev.sangwoo.celestialfall.client.LongCooldownsClient.known()+"):"+line);}
        try{
            var listField=CooldownHud.class.getDeclaredField("LIST");listField.setAccessible(true);var list=(CooldownList)listField.get(null);
            var clock=CooldownHud.class.getDeclaredMethod("clock",float.class);clock.setAccessible(true);double t=(double)clock.invoke(null,0f);
            var shown=new ArrayList<String>();
            for(var e:list.entries())if(!e.leaving())shown.add(String.format(Locale.ROOT,"%s %.0f s, %.1f s left",e.name,(e.end-e.start)/20,(e.end-t)/20));
            String names=list.entries().stream().filter(e->!e.leaving()).map(e->e.name+" "+Math.round((e.end-e.start)/20)).toList().toString();
            if(!names.equals(flags.get("list_lengths"))){flags.put("list_lengths",names);note("cooldown list "+shown);}
        }catch(ReflectiveOperationException e){flag("list_lengths","n/a "+e);}
        var sword=mc.player.getItemCooldownManager();
        flag("atomic_sword_cooling",sword.isCoolingDown(dev.sangwoo.celestialfall.CelestialFall.ATOMIC_SWORD));
    }
    private final Map<String,int[]> heard=new HashMap<>(),rushHeard=new HashMap<>(),inhaleHeard=new HashMap<>();private int hurtCries,inhalesHeard;private boolean listening;
    private final Map<UUID,net.minecraft.client.sound.SoundInstance> callsHeard=new HashMap<>();
    /**
     * Each player's stand's call sound as it starts here (2.72.0, StandCall): which (the call as it comes out, the
     * sound of it going as it goes back), how loud, and how far into its coming out or going back the stand was on this
     * client's clock then (it starts as the stand does: 0.00 s), whether the server has it out and called.
     */
    private void calls(){
        for(var p:mc.world.getPlayers()){
            var s=dev.sangwoo.celestialfall.client.stand.StandCall.playing(p.getUuid());
            if(s==null||callsHeard.get(p.getUuid())==s)continue;
            callsHeard.put(p.getUuid(),s);
            var st=dev.sangwoo.celestialfall.client.stand.StandPresence.state(p.getUuid());var h=(dev.sangwoo.celestialfall.stand.StandHolder)p;
            note(String.format(Locale.ROOT,"call sound %s for %s: volume %.2f, its stand %s %.2f s on this client's clock, server: out %s, called %s",s.getId().getPath(),p.getGameProfile().getName(),
                s.getVolume(),st==null?"none":st[1]>0?"coming out":"going back",st==null?-1f:st[0],h.botand$isStandOut(),h.botand$standCalled()));
        }
    }
    /**
     * Each player's inhale heard here (2.61.0; held down since 2.62.0): as it starts, its sound starting (its tick on its
     * clock), each catch (whom), each guard seen holding it off, its end. The watcher holds its guard up for 40 ticks
     * against the second it hears; held, it tries the attack, the dash and E, and walks (W) for 25 ticks, and logs where
     * it is from the caster; let go, it tries the attack 3 ticks after (incapacitated half a second more) and 14 after.
     */
    private void inhales(){
        for(var p:mc.world.getPlayers()){
            var i=dev.sangwoo.celestialfall.client.stand.StandInhale.of(p.getUuid());if(i==null)continue;
            String key=p.getUuid()+"/"+i.start;int[] st=inhaleHeard.get(key);String who=p.getGameProfile().getName()+"'s inhale";
            if(st==null){inhaleHeard.put(key,st=new int[5]);inhalesHeard++;note(String.format(Locale.ROOT,"%s heard (server tick %d, its tick here %d)",who,i.start,i.ticksIn()));
                if(!caster&&inhalesHeard==2){key(GLFW_KEY_F,true);guardUntil=age+40;note("press F (guard) against inhale "+inhalesHeard);}}
            if(st[0]==0&&i.sound()!=null&&mc.getSoundManager().isPlaying(i.sound())){st[0]=1;note(String.format(Locale.ROOT,"%s: its sound started at its tick %d here",who,i.ticksIn()));}
            for(;st[1]<i.caught.size();st[1]++){
                var c=i.caught.get(st[1]);var e=mc.world.getEntityById(c.id);
                note(String.format(Locale.ROOT,"%s caught %s at %.2f s on its clock, heard at its tick %d here",who,e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(c.id),c.at,i.ticksIn()));
                if(!caster&&c.id==mc.player.getId()){tries.put(age+3,"M1");tries.put(age+10,"C");tries.put(age+17,"E");tries.put(age+20,"W+");tries.put(age+45,"W-");heldSince=age;st[3]=1;}
            }
            int seen=(int)i.blocks.stream().filter(b->b.at()*20>lastBlock(key)).count();
            if(seen>0){var last=i.blocks.get(i.blocks.size()-1);blockSeen.put(key,last.at()*20);
                var e=mc.world.getEntityById(last.id());note(String.format(Locale.ROOT,"%s held off by %s's guard at %.2f s on its clock",who,e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(last.id()),last.at()));}
            if(st[2]==0&&!i.on()){st[2]=1;note(String.format(Locale.ROOT,"%s ended at %.2f s on its clock here",who,i.ends()));
                // let go: incapacitated half a second more (the attack refused), then free (it swings)
                if(!caster&&st[3]==1){tries.put(age+3,"M1");tries.put(age+14,"M1");heldSince=-1;}}
        }
        flag("held_by_inhale",dev.sangwoo.celestialfall.client.stand.StandInhale.localHeld());
        // held: where this player is from the caster (ahead along the way it faces, to its right), every 10 ticks
        if(!caster&&heldSince>=0&&(age-heldSince)%10==0&&age-heldSince<=120){
            var c=mc.world.getPlayers().stream().filter(q->q!=mc.player).findFirst().orElse(null);
            if(c!=null){var d=mc.player.getPos().subtract(c.getPos());var f=net.minecraft.util.math.Vec3d.fromPolar(0,c.getHeadYaw());
                note(String.format(Locale.ROOT,"%d ticks after the catch: %.2f blocks ahead of the caster, %.2f to its right, %.2f up; held %s",age-heldSince,d.x*f.x+d.z*f.z,-d.x*f.z+d.z*f.x,d.y,dev.sangwoo.celestialfall.client.stand.StandInhale.localHeld()));}
        }
    }
    private int heldSince=-1;private final Map<String,Float> blockSeen=new HashMap<>();
    /** The clock (ticks) of the last guard seen holding this inhale off (-1 if none yet). */
    private float lastBlock(String key){return blockSeen.getOrDefault(key,-1f);}
    /** Each player's rush heard here (2.57.0): as it starts, its voice starting, every tenth blow (whom it struck, how loud its blows sound) and its end. */
    private void rushes(){
        if(!listening&&mc.getSoundManager()!=null){listening=true;mc.getSoundManager().registerListener((sound,set)->{if(sound.getId().getPath().equals("entity.player.hurt"))hurtCries++;});}
        for(var p:mc.world.getPlayers()){
            var r=dev.sangwoo.celestialfall.client.stand.StandRush.of(p.getUuid());if(r==null)continue;
            String key=p.getUuid()+"/"+r.start;int[] st=rushHeard.get(key);String who=p.getGameProfile().getName()+"'s rush";
            if(st==null){rushHeard.put(key,st=new int[3]);note(String.format(Locale.ROOT,"%s heard (server tick %d, its tick here %d)",who,r.start,r.ticksIn()));}
            if(st[0]==0&&r.voice()!=null&&mc.getSoundManager().isPlaying(r.voice())){st[0]=1;note(String.format(Locale.ROOT,"%s: its voice started at its tick %d here",who,r.ticksIn()));}
            if(r.blows>st[1]){
                var all=r.blowsHeard();var last=all.isEmpty()?null:all.get(all.size()-1);
                if(last!=null&&(r.blows==1||r.blows%10==0)){var names=new ArrayList<String>();
                    for(int id:last.ids()){var e=mc.world.getEntityById(id);names.add(e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(id));}
                    note(String.format(Locale.ROOT,"%s: blow %d heard at its tick %d here, struck %s, its blows sounding at %.2f",who,r.blows,r.ticksIn(),names,r.striking()));}
                st[1]=r.blows;
            }
            if(st[2]==0&&!r.on()){st[2]=1;note(String.format(Locale.ROOT,"%s ended at %.2f s on its clock here, %d blows heard",who,r.ends(),r.blows));}
        }
    }
    /** Each punch of every player heard here, as it is heard, as it lands, and as its shout starts (its tick on the punch's clock). */
    private void punches(){
        for(var p:mc.world.getPlayers())for(var seen:dev.sangwoo.celestialfall.client.stand.StandAttack.punches(p.getUuid())){
            String key=p.getUuid()+"/"+seen.start;int[] state=heard.get(key);
            String who=p.getGameProfile().getName()+"'s punch "+seen.punch.combo();
            if(state==null){heard.put(key,state=new int[5]);note(String.format(Locale.ROOT,"%s heard (server tick %d, its tick here %d)",who,seen.start,seen.ticksIn()));}
            if(state[0]==0&&seen.landed){state[0]=1;var names=new ArrayList<String>();
                for(int id:seen.struck){var e=mc.world.getEntityById(id);names.add(e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(id));}
                note(String.format(Locale.ROOT,"%s landed on %s at its tick %d here",who,names,seen.ticksIn()));}
            if(state[1]==0&&seen.voice()!=null&&mc.getSoundManager().isPlaying(seen.voice())){state[1]=1;note(String.format(Locale.ROOT,"%s: its shout started at its tick %d here",who,seen.ticksIn()));}
            // 2.56.0: the jab and the cross shout nothing; the hook's shout fades as the finisher's comes
            // (the heavy punch and the Star Finger are no punches of the combo: they have sounds of their own, logged below)
            if(state[3]==0&&seen.ticksIn()>=4&&!dev.sangwoo.celestialfall.stand.StarPlatinumRules.skill(seen.punch.combo())&&!dev.sangwoo.celestialfall.stand.StarPlatinumRules.shouts(seen.punch.combo())){state[3]=1;note(who+(seen.voice()==null?": no shout (by the rule)":": a shout, against the rule"));}
            if(state[4]==0&&seen.voice() instanceof dev.sangwoo.celestialfall.client.stand.StandAttack.Shout shout&&shout.fading()){state[4]=1;note(String.format(Locale.ROOT,"%s: its shout fading out at its tick %d here",who,seen.ticksIn()));}
            if(state[2]==0&&!Float.isNaN(seen.punch.stopped())){state[2]=1;note(who+" stopped short");}
            // 2.59.0: the heavy punch: its voice and its blow starting here, the blow's loudness once it lands, whose
            // guard it broke; the watcher holds its guard up against the second it hears
            if(seen.punch.combo()==dev.sangwoo.celestialfall.stand.StarPlatinumRules.HEAVY){
                int[] h=heavyHeard.computeIfAbsent(key,k->new int[4]);
                if(h[0]==0){h[0]=1;heavies++;if(!caster&&heavies==2){key(GLFW_KEY_F,true);guardUntil=age+40;note("press F (guard) against heavy punch "+heavies);}}
                if(h[1]==0&&seen.heavyVoice()!=null&&mc.getSoundManager().isPlaying(seen.heavyVoice())){h[1]=1;note(String.format(Locale.ROOT,"%s: its voice started at its tick %d here",who,seen.ticksIn()));}
                if(h[2]==0&&seen.heavyBlow()!=null&&mc.getSoundManager().isPlaying(seen.heavyBlow())){h[2]=1;note(String.format(Locale.ROOT,"%s: its blow started at its tick %d here",who,seen.ticksIn()));}
                if(h[3]==0&&seen.landed&&seen.ticksIn()>=dev.sangwoo.celestialfall.stand.StarPlatinumRules.CONTACT[seen.punch.combo()]+2){h[3]=1;var broke=new ArrayList<String>();
                    for(int id:seen.broke){var e=mc.world.getEntityById(id);broke.add(e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(id));}
                    note(String.format(Locale.ROOT,"%s: its blow sounding at %.2f at its tick %d here, broke the guard of %s",who,seen.heavyBlow()==null?-1:seen.heavyBlow().loudness(),seen.ticksIn(),broke));
                    // 2.60.0: its guard broken, the watcher is incapacitated: its attack is refused
                    if(!caster&&seen.broke.length>0)tries.put(age+3,"M1");}
            }
            // 2.60.0: the Star Finger: its sound starting here and whom it struck; the watcher, struck, tries everything
            if(seen.punch.combo()==dev.sangwoo.celestialfall.stand.StarPlatinumRules.FINGER){
                int[] h=fingerHeard.computeIfAbsent(key,k->new int[2]);
                if(h[0]==0&&seen.voice()!=null&&mc.getSoundManager().isPlaying(seen.voice())){h[0]=1;note(String.format(Locale.ROOT,"%s: its sound started at its tick %d here",who,seen.ticksIn()));}
                if(h[1]==0&&seen.landed){h[1]=1;
                    boolean me=false;for(int id:seen.struck)me|=id==mc.player.getId();
                    if(!caster&&me){tries.put(age+3,"M1");tries.put(age+10,"C");tries.put(age+17,"E");tries.put(age+20,"W+");tries.put(age+45,"W-");tries.put(age+68,"M1");
                        fingerWalk=new int[]{age+20,age+45};}}
            }
        }
        // this player's own guard as its client knows it (2.59.0: broken by the heavy punch)
        var mine=CombatClient.snapshot(mc.player.getUuid());flag("guard",mine.broken(mc.world.getTime())?"broken":mine.guard()?"up":"down");
        // 2.68.0: staggered by a basic attack's blow as its client knows it (until when), and whether its client lets it act
        flag("staggered",mine.staggered(mc.world.getTime())?"until "+mine.staggerUntil():"no");flag("action_locked",CombatClient.actionLocked());
        // 2.60.0: incapacitated as its client knows it, and its speed against its base
        var speed=mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        flag("incapacitated",mine.disabled(mc.world.getTime())||mine.broken(mc.world.getTime()));
        flag("speed",String.format(Locale.ROOT,"x%.2f",speed.getValue()/speed.getBaseValue()));
        // the watcher's tries while incapacitated (and once it is over), and how fast it walked
        if(!caster){
            String t=tries.remove(age);
            if(t!=null){note("try "+t+(dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView()?" (stopped in time)":mine.locked(mc.world.getTime())?" (incapacitated)":" (free)"));
                switch(t){case "M1"->{attack(true);release=age+1;}case "C"->{key(GLFW_KEY_C,true);releaseKey=GLFW_KEY_C;release=age+1;}case "E"->{key(GLFW_KEY_E,true);releaseKey=GLFW_KEY_E;release=age+1;}
                    // (2.75.0: its own Q, H, Y and G tapped, and walking back)
                    case "Q"->{key(GLFW_KEY_Q,true);releaseKey=GLFW_KEY_Q;release=age+1;}case "H"->{key(GLFW_KEY_H,true);releaseKey=GLFW_KEY_H;release=age+1;}
                    case "Y"->{key(GLFW_KEY_Y,true);releaseKey=GLFW_KEY_Y;release=age+1;}case "G"->{key(GLFW_KEY_G,true);releaseKey=GLFW_KEY_G;release=age+1;}
                    case "S+"->key(GLFW_KEY_S,true);case "S-"->key(GLFW_KEY_S,false);
                    case "W+"->key(GLFW_KEY_W,true);case "W-"->key(GLFW_KEY_W,false);case "F+"->key(GLFW_KEY_F,true);case "F-"->key(GLFW_KEY_F,false);default->{}}
                if(stoppedAt!=null&&(t.equals("W-")||t.equals("F-")))note(String.format(Locale.ROOT,"%.2f blocks from where time stopped it (its guard %s)",mc.player.getPos().distanceTo(stoppedAt),mine.guard()?"up":"down"));}
            if(age==release){attack(false);if(releaseKey!=0)key(releaseKey,false);releaseKey=0;}
            if(fingerWalk!=null){
                var p=mc.player.getPos();
                if(age==fingerWalk[0]){walkFrom=p;walkSteps.clear();}
                else if(age>fingerWalk[0]&&age<=fingerWalk[1]){if(age>fingerWalk[0]+4&&mc.player.isOnGround())walkSteps.add(Math.hypot(p.x-walkFrom.x,p.z-walkFrom.z));walkFrom=p;}
                if(age==fingerWalk[1]){var sorted=new ArrayList<>(walkSteps);sorted.sort(null);double median=sorted.isEmpty()?0:sorted.get(sorted.size()/2);
                    note(String.format(Locale.ROOT,"walking %.2f blocks/s while struck by the Star Finger (median of %d ticks)",median*20,sorted.size()));fingerWalk=null;}
            }
            boolean dashing=mine.dashStart()>mc.world.getTime()-8&&mine.dashStart()<=mc.world.getTime();
            flag("dashing",dashing);
        }
    }
    private final Map<String,int[]> heavyHeard=new HashMap<>(),fingerHeard=new HashMap<>();private int heavies,guardUntil;
    /** The vanilla mode's check (2.110.0): what this client last logged of itself and of the other. */
    private String vanillaSelf="",vanillaOther="";
    /**
     * The vanilla mode's check (2.110.0, MP_ONLY=vanilla): this client's map, its player's game mode, vanilla mode, inventory
     * key and view; the other player's vanilla mode as heard here and whether its body is drawn as Botand's (the rig).
     */
    private void vanillaWatch(){
        var gm=mc.interactionManager==null?null:mc.interactionManager.getCurrentGameMode();
        String self=String.format(Locale.ROOT,"map %s, game mode %s, vanilla %s, inventory key %s, view %s",dev.sangwoo.celestialfall.client.MapClient.enabled(),gm,
            dev.sangwoo.celestialfall.map.VanillaMode.on(mc.player),mc.options.inventoryKey.getBoundKeyTranslationKey(),mc.options.getPerspective());
        if(!self.equals(vanillaSelf)){vanillaSelf=self;note("self: "+self);}
        net.minecraft.client.network.AbstractClientPlayerEntity other=null;for(var p:mc.world.getPlayers())if(p!=mc.player)other=p;
        if(other!=null){String o=String.format(Locale.ROOT,"vanilla %s, drawn with the rig %s, stand out %s",dev.sangwoo.celestialfall.map.VanillaMode.on(other),CombatClient.animate(other),((dev.sangwoo.celestialfall.stand.StandHolder)other).botand$isStandOut());
            if(!o.equals(vanillaOther)){vanillaOther=o;note("the other ("+other.getGameProfile().getName()+"): "+o);}}
    }
    /** The glow's check (2.109.0): the other's glow as drawn here (its strength last tick, when it began, how many), this one's own. */
    private float glowSeen;private int glowBegan,glowCount;private boolean ownGlow;
    /**
     * The glow's check (2.109.0, MP_ONLY=glow): the other player's glow as this client draws it (CutsceneFlash: when it
     * begins and how long it shows), the watcher striking as it begins if the caster asks (the file OUT/jab); and this
     * one's own glow: its client's lock, its speed against its base, its own flash.
     */
    private void glowWatch(){
        net.minecraft.entity.player.PlayerEntity other=null;for(var p:mc.world.getPlayers())if(p!=mc.player)other=p;
        float a=other==null?0:dev.sangwoo.celestialfall.client.CutsceneFlash.strength(other,0);
        if(a>0&&glowSeen==0){glowCount++;glowBegan=age;note(String.format(Locale.ROOT,"the other's glow %d begins here (strength %.2f)",glowCount,a));
            if(!caster&&Files.exists(OUT.resolve("jab"))){tries.put(age+1,"M1");note("strike asked: the attack at "+(age+1));}}
        if(a==0&&glowSeen>0)note(String.format(Locale.ROOT,"the other's glow %d gone after %d ticks",glowCount,age-glowBegan));
        glowSeen=a;
        boolean mine=dev.sangwoo.celestialfall.client.CutsceneFlash.localGlowing();
        if(mine!=ownGlow){ownGlow=mine;var speed=mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
            note(String.format(Locale.ROOT,"its own glow %s (action locked %s, speed x%.2f, its flash %.2f)",mine?"begins":"ends",CombatClient.actionLocked(),speed.getValue()/speed.getBaseValue(),dev.sangwoo.celestialfall.client.CutsceneFlash.local(0)));}
    }
    /** The stagger's check (2.68.0): until when the watcher mashes its attack, its last health and strike, its walk. */
    private int mashUntil,mashRelease=-1,lastHurt;private long lastStrike=-1000;private boolean walking;
    private net.minecraft.util.math.Vec3d walkAt;private final List<Double> walkStaggered=new ArrayList<>(),walkFree=new ArrayList<>();
    /**
     * The watcher in the stagger's check (2.68.0): struck, it mashes its own attack every 3 ticks for 36 ticks (each try
     * logged as its client sees it: staggered or free; a free one starts a strike, which strikes the caster back); it
     * logs each strike of its own its client starts; while the caster asks it to walk (the file OUT/walk) it holds W and
     * measures its speed on the ground, staggered and free apart.
     */
    private void command(String c){note("command "+c);mc.player.networkHandler.sendChatCommand(c);}
    /** The boss's body as this client has it (2.69.0), if any. */
    private net.minecraft.entity.player.PlayerEntity boss(){
        for(var info:dev.sangwoo.celestialfall.client.boss.BossClient.all()){var e=mc.world.getPlayerByUuid(info.id());if(e!=null)return e;}
        return null;
    }
    /**
     * Kujo Jotaro (2.69.0) as this client knows him: in its list of bosses and whether it has his body, the skin it draws
     * him with, his outfit, the boss bar showing, his anger, his stand out, his health in tenths, whether this client's
     * screen is held by a time stop, and the boxes within 32 blocks (and how many of them opened).
     */
    private void bossWatch(){
        var list=dev.sangwoo.celestialfall.client.boss.BossClient.all();
        flag("boss_known",list.stream().map(i->i.title()+(mc.world.getPlayerByUuid(i.id())!=null?" (body)":" (no body)")).toList());
        var b=boss();
        if(b instanceof net.minecraft.client.network.AbstractClientPlayerEntity c){
            flag("boss_skin",c.getSkinTexture().toString());
            flag("boss_outfit",dev.sangwoo.celestialfall.client.boss.BossOutfit.model("jotaro")!=null);
            flag("boss_angry",list.stream().anyMatch(i->i.id().equals(c.getUuid())&&i.angry()));
            flag("boss_stand_out",((dev.sangwoo.celestialfall.stand.StandHolder)c).botand$isStandOut());
            flag("boss_health_tenths",Math.round(c.getHealth()/c.getMaxHealth()*10));
            flag("boss_alive",c.isAlive());
        }else flag("boss_skin","-");
        flag("boss_bar",dev.sangwoo.celestialfall.client.boss.BossBar.shown()>.5f);
        flag("screen_held",dev.sangwoo.celestialfall.client.stand.StandTimeStop.stopped(mc.player));
        var boxes=mc.world.getEntitiesByClass(dev.sangwoo.celestialfall.loot.FieldLootEntity.class,mc.player.getBoundingBox().expand(32),e->e.loose());
        flag("boxes_seen",boxes.size()+" ("+boxes.stream().filter(e->e.opened()).count()+" opened)");
    }
    private final Set<UUID> swingsSeen=new HashSet<>();private final Map<UUID,Integer> swingStruck=new HashMap<>();private boolean swingListening;
    private net.minecraft.util.math.Vec3d stillAt;private boolean stillStaggered;
    /**
     * Lee So-sang's basic attack as this client knows it (2.77.0): every hit seen (whose, its number, the tick it began
     * on and its age as this client heard of it), whom its blows struck as they are told, and its sounds as they start
     * (at what age of the newest hit).
     */
    private final java.util.Set<String> swordsSeen=new java.util.HashSet<>();private String eSeen="";
    /** (2.113.0) The gauge, the formations' planned ends and the slow last noted. */
    private String gaugeSeen="",slowSeen="";private final java.util.Set<String> planSeen=new java.util.HashSet<>();private final java.util.Map<java.util.UUID,Float> formAge=new java.util.HashMap<>();
    private void isosangWatch(){
        if(!swingListening&&mc.getSoundManager()!=null){swingListening=true;mc.getSoundManager().registerListener((sound,set)->{
            String id=sound.getId().getPath();if(!id.startsWith("isosang_m1")&&!id.equals("isosang_r")&&!id.startsWith("isosang_h")&&!id.startsWith("isosang_g")&&!id.equals("isosang_z")&&!id.equals("isosang_volley")&&!id.equals("isosang_t")&&!id.equals("isosang_y"))return;
            var newest=dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values().stream().reduce((a,b)->b).orElse(null);
            note(String.format(Locale.ROOT,"sound %s at hit %s's age %.2f ticks (volume %.2f)",id,newest==null?"-":String.valueOf(newest.combo()),newest==null?-1:newest.age(0),sound.getVolume()));});}
        long now=mc.world.getTime();
        for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(swingsSeen.add(c.id())){
                note(String.format(Locale.ROOT,"%s's hit %d seen (began on world tick %d, its age %.0f ticks as heard)",name(c.caster()),c.combo(),now-(long)Math.floor(c.age(0)),c.age(0)));
                // (2.81.0) R: where its sweep stands, against where its caster is
                var sw=dev.sangwoo.celestialfall.client.isosang.IsosangClient.sweep(c.id());var who=mc.world.getPlayerByUuid(c.caster());
                if(sw!=null&&who!=null){var f=net.minecraft.util.math.Vec3d.fromPolar(0,sw.yaw());var d=sw.at().subtract(who.getPos());
                    note(String.format(Locale.ROOT,"%s's R stands %.2f ahead (%.2f to the side, %.2f up) of it, facing %.1f, aimed at %s",name(c.caster()),d.dotProduct(f),d.dotProduct(new net.minecraft.util.math.Vec3d(-f.z,0,f.x)),d.y,sw.yaw(),
                        sw.target()<0?"no one":mc.world.getEntityById(sw.target())==mc.player?"me":String.valueOf(sw.target())));}
            }
            int n=c.targets().size();Integer was=swingStruck.put(c.id(),n);
            if(was==null?n>0:n>was)note(String.format(Locale.ROOT,"%s's hit %d struck %d (at its age %.0f ticks)",name(c.caster()),c.combo(),n,c.age(0)));
            // (2.80.0) the empowered first's swords: each as it is thrown and where it stopped
            var swords=dev.sangwoo.celestialfall.client.isosang.IsosangClient.swords(c.id());
            if(swords!=null)for(int i=0;i<swords.length;i++){
                var w=swords[i];if(w==null)continue;String key=c.id()+"/"+i+"/"+w.landed();
                if(swordsSeen.add(key))note(String.format(Locale.ROOT,"%s's sword %d %s: from %.2f %.2f %.2f way %.2f %.2f %.2f, %s %.2f blocks (thrown %.0f ticks in; heard at its age %.0f)",
                    name(c.caster()),i,w.landed()?"stopped":"thrown",w.from().x,w.from().y,w.from().z,w.dir().x,w.dir().y,w.dir().z,w.landed()?"flew":"may fly",w.end(),w.thrown(),c.age(0)));
            }
        }
        // (2.113.0) own H gauge as the HUD has it (every second while it drains), each formation's planned end as told and
        // when it is gone here, and own slow (the combat's speed modifier)
        {
            String g=dev.sangwoo.celestialfall.client.isosang.IsosangGaugeHud.units()+"/"+dev.sangwoo.celestialfall.client.isosang.IsosangGaugeHud.onH();
            if(!g.equals(gaugeSeen)){gaugeSeen=g;note(String.format(Locale.ROOT,"own H gauge: %d/%d%s, shown %.3f (world tick %d)",dev.sangwoo.celestialfall.client.isosang.IsosangGaugeHud.units(),dev.sangwoo.celestialfall.isosang.IsosangRules.GAUGE_FULL,
                dev.sangwoo.celestialfall.client.isosang.IsosangGaugeHud.onH()?" on H":"",dev.sangwoo.celestialfall.client.isosang.IsosangGaugeHud.fillNow(),mc.world.getTime()));}
            if(dev.sangwoo.celestialfall.client.isosang.IsosangGaugeHud.onH()&&mc.world.getTime()%20==0)note(String.format(Locale.ROOT,"own H gauge on H: %.3f left (world tick %d)",dev.sangwoo.celestialfall.client.isosang.IsosangGaugeHud.fillNow(),mc.world.getTime()));
            var seenNow=new java.util.HashSet<java.util.UUID>();
            for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
                if(c.combo()!=dev.sangwoo.celestialfall.isosang.IsosangRules.FORMATION)continue;
                seenNow.add(c.id());formAge.put(c.id(),c.age(0)/20f);
                float plan=dev.sangwoo.celestialfall.client.isosang.IsosangClient.plannedEnd(c.id());
                if(planSeen.add(c.id()+"/"+plan))note(String.format(Locale.ROOT,"%s's formation: its swords flare %.2f s in (as told; its age %.2f s)",name(c.caster()),plan,c.age(0)/20));
                if(c.age(0)/20>=plan&&planSeen.add(c.id()+"/flared"))note(String.format(Locale.ROOT,"%s's formation flares here at its age %.2f s (planned %.2f s)",name(c.caster()),c.age(0)/20,plan));
            }
            for(var e:new java.util.ArrayList<>(formAge.entrySet()))if(!seenNow.contains(e.getKey())){note(String.format(Locale.ROOT,"a formation is gone here (last at its age %.2f s)",e.getValue()));formAge.remove(e.getKey());}
            var sp=mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);var mod=sp==null?null:sp.getModifier(dev.sangwoo.celestialfall.combat.CombatManager.SLOW);
            String w=mod==null?"none":String.format(Locale.ROOT,"%.2f",mod.getValue());
            if(!w.equals(slowSeen)){slowSeen=w;note("own slow: "+w+" (world tick "+mc.world.getTime()+")");}
        }
        var self=mc.player;boolean charged=dev.sangwoo.celestialfall.client.isosang.IsosangClient.charged(self.getUuid());long eReady=dev.sangwoo.celestialfall.client.isosang.IsosangClient.eReady(self.getUuid());
        String e=charged+"/"+eReady;if(!e.equals(eSeen)){eSeen=e;note("own E: "+(charged?"empowered":"not empowered")+", ready again at "+eReady+" (world tick "+now+")");}
        long rReady=dev.sangwoo.celestialfall.client.isosang.IsosangClient.rReady(self.getUuid());
        if(rReady!=rSeen){rSeen=rReady;note("own R: ready again at "+rReady+" (world tick "+now+")");}
        // (2.82.0) H's formation: its middle as told, and every second the caster's height over it, as it is and as drawn
        // (2.83.0) G: its recording and where it strikes as told, then every half second this client's space (the world
        // hidden for the caster's cutscene only) and camera, and whether its landing is drawn here
        long gReady=dev.sangwoo.celestialfall.client.isosang.IsosangClient.gReady(self.getUuid());
        if(gReady!=gSeen){gSeen=gReady;note("own G: ready again at "+gReady+" (world tick "+now+")");}
        for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(c.combo()!=dev.sangwoo.celestialfall.isosang.IsosangRules.ULTIMATE)continue;
            var u=dev.sangwoo.celestialfall.client.isosang.IsosangClient.ultimate(c.id());var who=mc.world.getPlayerByUuid(c.caster());
            if(u!=null&&who!=null&&ultSeen.add(c.id()+"/told")){var f=net.minecraft.util.math.Vec3d.fromPolar(0,c.yaw());var d=u.strike().subtract(c.origin());
                note(String.format(Locale.ROOT,"%s's G: recording %d, it strikes %.2f ahead (%.2f to the side, %.2f up) of where it stood",name(c.caster()),u.variant()+1,d.dotProduct(f),d.dotProduct(new net.minecraft.util.math.Vec3d(-f.z,0,f.x)),d.y));}
            int half=(int)Math.floor(c.age(0)/10);
            if(ultSeen.add(c.id()+"/"+half))note(String.format(Locale.ROOT,"%s's G at %.1f s: here the space is %d (-1 the world), the camera %s, its landing %s",name(c.caster()),c.age(0)/20,
                dev.sangwoo.celestialfall.client.isosang.GStage.space(0),dev.sangwoo.celestialfall.client.CinematicDirector.isActive()?"in its cutscene":"the game's",
                dev.sangwoo.celestialfall.client.meteor.MeteorFx.blasting(c.id())?"drawn":"not drawn"));
        }
        long hReady=dev.sangwoo.celestialfall.client.isosang.IsosangClient.hReady(self.getUuid());
        if(hReady!=hSeen){hSeen=hReady;note("own H: ready again at "+hReady+" (world tick "+now+")");}
        for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(c.combo()!=dev.sangwoo.celestialfall.isosang.IsosangRules.FORMATION)continue;
            var form=dev.sangwoo.celestialfall.client.isosang.IsosangClient.formation(c.id());var who=mc.world.getPlayerByUuid(c.caster());if(form==null||who==null)continue;
            int sec=(int)Math.floor(c.age(0)/20);float end=dev.sangwoo.celestialfall.client.isosang.IsosangClient.formationEnd(c.id());
            // (2.82.3: every other tick as the caster leaps across, 1.1 to 2.1 s; else every second)
            int tick=(int)Math.floor(c.age(0));boolean leap=tick>=22&&tick<=42;
            String key=c.id()+"/"+(leap?"t"+(tick/2):sec)+"/"+(Float.isNaN(end)?"":"end");
            if(formationSeen.add(key)){
                // (as the body is drawn at the frame's start: where it is between ticks, getLerpedPos, moved as the plan has it)
                var at=who.getLerpedPos(0);var d=at.subtract(form.centre());
                // (2.82.3) and where the plan has them level (2.82.4: where they stood, on the formation's outer ring, its
                // middle ahead): how far off it they are and are drawn (drawn where they are: no lag to make up level)
                var plan=dev.sangwoo.celestialfall.client.isosang.IsosangClient.spot(form);
                var drawn=at;
                note(String.format(Locale.ROOT,"%s's formation at its age %.2f s: the caster %.2f over its middle (%.2f off it level; %.2f off the plan, drawn %.2f off it, the plan %.2f from the middle), drawn %.2f over it (planned %.2f)%s",name(c.caster()),c.age(0)/20,d.y,Math.hypot(d.x,d.z),
                    Math.hypot(at.x-plan.x,at.z-plan.z),Math.hypot(drawn.x-plan.x,drawn.z-plan.z),Math.hypot(plan.x-form.centre().x,plan.z-form.centre().z),
                    d.y+dev.sangwoo.celestialfall.client.isosang.IsosangClient.lift(c.caster(),0),Math.max(0,dev.sangwoo.celestialfall.isosang.IsosangRules.planned(c.age(0)/20.0)),
                    Float.isNaN(end)?"":String.format(Locale.ROOT," (ended early at %.2f s)",end)));
            }
        }
    }
    /** (2.95.0) H basic attack volleys seen (by swing and what of it was logged), and the own one's ready tick last logged. */
    private final java.util.Set<String> volleySeen=new java.util.HashSet<>();private long vReadySeen=-1;
    /**
     * H's basic attack as this client sees it (2.95.0): each volley as it is heard (whose, on whom: me or another, how
     * late), where its swords come down as drawn here against the watcher's chest as drawn here, how many of its swords
     * are drawn as they fan out (6 ticks in), fly (14) and strike (22), and gone (30); the own one's ready tick as told.
     */
    private void volleyWatch(){
        for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(c.combo()!=dev.sangwoo.celestialfall.isosang.IsosangRules.VOLLEY)continue;
            var v=dev.sangwoo.celestialfall.client.isosang.IsosangClient.volley(c.id());if(v==null)continue;
            if(volleySeen.add(c.id()+"/heard")){var e=mc.world.getEntityById(v.target());
                note(String.format(Locale.ROOT,"%s's volley heard %.1f ticks into it: on %s, at %.2f %.2f %.2f",name(c.caster()),c.age(0),v.target()<0?"no one":e==mc.player?"me":e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():"entity "+v.target(),v.point().x,v.point().y,v.point().z));}
            int age=(int)Math.floor(c.age(0));
            for(int at:new int[]{6,14,22,30})if(age>=at&&volleySeen.add(c.id()+"/"+at)){
                var to=dev.sangwoo.celestialfall.client.isosang.IsosangClient.volleyAt(c.id(),0);var w=v.target()>=0?mc.world.getEntityById(v.target()):null;
                note(String.format(Locale.ROOT,"%s's volley +%d: %d swords drawn, coming down %s",name(c.caster()),age,dev.sangwoo.celestialfall.client.isosang.IsosangClient.volleyDrawn(c.id(),0),
                    to==null?"nowhere":w==null?String.format(Locale.ROOT,"at %.2f %.2f %.2f",to.x,to.y,to.z):String.format(Locale.ROOT,"%.2f off its foe's chest as drawn here",to.distanceTo(w.getLerpedPos(0).add(0,w.getHeight()*.55,0)))));
            }
        }
        long vr=dev.sangwoo.celestialfall.client.isosang.IsosangClient.vReady(mc.player.getUuid());
        if(vr!=vReadySeen){vReadySeen=vr;note("own volley: ready again at "+vr+" (world tick "+mc.world.getTime()+")");}
    }
    /** (2.96.0) T's seen (by swing and its tick), and the own T's readiness last logged. */
    private final java.util.Set<String> tSeen=new java.util.HashSet<>();private long tReadySeen=-1;
    /**
     * T as this client sees it (2.96.0): each as it is heard (on whom, where she lands against the watcher's feet as drawn
     * here, the swords she flies from and to, how late), every tick of it the caster hidden or not, how far off the plan
     * (the formation's, IsosangClient.spot) it is and is drawn level and how high it is drawn over the plan's height, how
     * many swords are drawn; the own T's readiness as the server told it.
     */
    private void tWatch(){
        for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(c.combo()!=dev.sangwoo.celestialfall.isosang.IsosangRules.LUNGE)continue;
            var run=dev.sangwoo.celestialfall.client.isosang.IsosangClient.lunge(c.id());var v=dev.sangwoo.celestialfall.client.isosang.IsosangClient.volley(c.id());if(run==null||v==null)continue;
            var who=mc.world.getPlayerByUuid(c.caster());
            if(tSeen.add(c.id()+"/heard")){var e=mc.world.getEntityById(v.target());var w=mc.world.getPlayers().stream().filter(q->q!=who).findFirst().orElse(null);
                note(String.format(Locale.ROOT,"%s's T heard %.1f ticks into it: on %s, at %.2f %.2f %.2f; lands at %.2f %.2f %.2f (%s), from sword %d to sword %d",name(c.caster()),c.age(0),
                    v.target()<0?"no one":e==mc.player?"me":e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():"entity "+v.target(),v.point().x,v.point().y,v.point().z,
                    run.past().x,run.past().y,run.past().z,w==null?"no watcher":String.format(Locale.ROOT,"%.2f from the watcher's feet",run.past().distanceTo(w.getLerpedPos(0))),run.from(),run.to()));}
            int age=(int)Math.floor(c.age(0));
            if(who!=null&&age>=0&&age<=dev.sangwoo.celestialfall.isosang.IsosangRules.LUNGE_LENGTH&&tSeen.add(c.id()+"/"+age)){
                var formSwing=dev.sangwoo.celestialfall.client.isosang.IsosangClient.formationOf(c.caster());
                var form=formSwing==null?null:dev.sangwoo.celestialfall.client.isosang.IsosangClient.formation(formSwing.id());
                if(form!=null){
                    double fa=formSwing.age(0);var plan=dev.sangwoo.celestialfall.client.isosang.IsosangClient.spot(formSwing.id(),form,fa);var at=who.getLerpedPos(0);
                    var drawn=at.add(dev.sangwoo.celestialfall.client.isosang.IsosangClient.shift(c.caster(),0));
                    double high=at.y+dev.sangwoo.celestialfall.client.isosang.IsosangClient.lift(c.caster(),0)-form.centre().y,planned=dev.sangwoo.celestialfall.client.isosang.IsosangClient.raised(formSwing.id(),fa/20.0);
                    note(String.format(Locale.ROOT,"%s's T +%d: hidden %s, %.2f off the plan level (drawn %.2f), drawn %.2f over its ground (planned %.2f), %d swords drawn",name(c.caster()),age,
                        dev.sangwoo.celestialfall.client.isosang.SwordStep.hides(who),Math.hypot(at.x-plan.x,at.z-plan.z),Math.hypot(drawn.x-plan.x,drawn.z-plan.z),high,planned,
                        dev.sangwoo.celestialfall.client.isosang.IsosangClient.volleyDrawn(c.id(),0)));
                }
            }
        }
        long tr=dev.sangwoo.celestialfall.client.isosang.IsosangClient.tReady(mc.player.getUuid());
        if(tr!=tReadySeen){tReadySeen=tr;note("own T: ready again at "+tr+" (world tick "+mc.world.getTime()+")");}
    }
    /** (2.97.0) Y's ticks seen (by swing and its tick), and the own Y's readiness last logged. */
    private final java.util.Set<String> ySeen=new java.util.HashSet<>();private long yReadySeen=-1;private boolean heldSeen;
    /** (2.112.0) X Chara's G as this client has it: its moments last logged, whether its camera was in its strange space, whether it was held, the marks logged. */
    private String xState="";private boolean xCamera,xHeld;private final java.util.Set<String> xSeen=new java.util.HashSet<>();
    /** (2.114.0) X Chara's basic attack as this client has it: the swing last logged, the sounds logged so far, the hit count last logged. */
    private String knifeState="";private int knifeHeard,knifeHits=-1;private final java.util.Set<String> knifeSeen=new java.util.HashSet<>();
    /**
     * X Chara's basic attack as this client sees it (2.114.0, MP_ONLY=xcharam1): every swing as it is heard (whose, which
     * hit, how many ticks into it, whether it stopped short), each blow's struck as the server told it, every sound this
     * client started, its own hit count as its HUD has it, and for the watcher whether a blow holds it (its action locked).
     */
    private void knifeWatch(){
        for(var p:mc.world.getPlayers()){
            String desc=dev.sangwoo.celestialfall.client.xchara.KnifeClient.describe(p,0);if(desc==null)continue;
            String[] v=desc.split(",");var cast=dev.sangwoo.celestialfall.client.xchara.KnifeClient.SWINGS.values().stream().filter(c->c.caster().equals(p.getUuid())).reduce((a,b)->b).orElse(null);
            if(cast==null)continue;
            String key=cast.id()+":"+v[2];
            if(!key.equals(knifeState)){knifeState=key;note(String.format(Locale.ROOT,"%s's hit %s (began %d): heard %s ticks into it, stopped short %s, the body driven %s",name(p.getUuid()),v[0],cast.start(),v[1],v[2],
                dev.sangwoo.celestialfall.client.xchara.KnifeClient.animated(p.getUuid())!=null));}
            for(int i=0;i<dev.sangwoo.celestialfall.xchara.XCharaCombo.blows(cast.combo());i++){
                boolean me=cast.targets().stream().anyMatch(t->t.entity()==mc.player.getId());
                if(cast.age(0)>=dev.sangwoo.celestialfall.xchara.XCharaCombo.blowAt(cast.combo(),i)+3&&knifeSeen.add(cast.id()+"/"+i))
                    note(String.format(Locale.ROOT,"  its blow %d: %d struck so far by the server's word (%s)",i,cast.targets().size(),me?"me among them":"not me"));
            }
        }
        var heard=dev.sangwoo.celestialfall.client.xchara.KnifeClient.HEARD;
        if(heard.size()<knifeHeard)knifeHeard=0;
        for(;knifeHeard<heard.size();knifeHeard++)note("  sound: "+heard.get(knifeHeard));
        int hits=dev.sangwoo.celestialfall.client.HitCounterHud.count;
        if(hits!=knifeHits){knifeHits=hits;note("my hit count "+hits);}
        boolean locked=dev.sangwoo.celestialfall.client.CombatClient.actionLocked();
        flag("action_locked",locked);
        // (2.124.0) the jab: whose face its X is on here, and whether it lights a knife here with the hack off
        String marks=dev.sangwoo.celestialfall.client.xchara.CorruptionJab.describe(0);var on=new StringBuilder();
        if(!marks.isEmpty())for(String k:marks.split(" ")){
            int id=Integer.parseInt(k.substring(0,k.indexOf(':')));
            if(dev.sangwoo.celestialfall.client.xchara.CorruptionJab.marked(id))on.append(id==mc.player.getId()?"me":mc.world.getEntityById(id) instanceof net.minecraft.entity.player.PlayerEntity q?name(q.getUuid()):"entity "+id).append(' ');
        }
        if(!on.toString().equals(jabMarks)){jabMarks=on.toString();note("the jab's X here: "+(jabMarks.isEmpty()?"on no one":"on "+jabMarks.trim()+" ("+marks+")"));}
        for(var p:mc.world.getPlayers()){
            boolean lit=dev.sangwoo.celestialfall.client.xchara.CorruptionJab.knife(p.getUuid(),0)!=null;
            if(lit!=jabLit.contains(p.getUuid())){
                if(lit)jabLit.add(p.getUuid());else jabLit.remove(p.getUuid());
                note(String.format(Locale.ROOT,"%s's knife lit by the jab here: %s (their hack %s)",name(p.getUuid()),lit,dev.sangwoo.celestialfall.client.xchara.HackMode.on(p)?"on":"off"));
            }
        }
    }
    private String jabMarks="";private final java.util.Set<UUID> jabLit=new java.util.HashSet<>();
    private final Map<UUID,String> cloneState=new HashMap<>(),cloneSounds=new HashMap<>();private boolean cloneHeld;
    /**
     * (2.125.0) X Chara's double as this client sees it: each player's as it changes here (seized, in the wall, ended and
     * how), whether the double is drawn here, where the one seized is drawn against where this client has them, and
     * whether it holds this client's player where they stand.
     */
    private String weakenState="";private final Map<UUID,String> weakenSounds=new HashMap<>();private boolean weakenHeld;
    /** (2.126.0, MP_ONLY=xcharat) X Chara's weakening as this client has it: whom it holds weakened and the casts it heard of as they change, the warning sound begun, whether it holds this player, and its cooldown here. */
    private void weakenWatch(){
        String desc=dev.sangwoo.celestialfall.client.xchara.WeakenClient.describe(0);
        String key=desc.replaceAll(":[0-9.]+","").replaceAll("@[0-9.]+","");
        if(!key.equals(weakenState)){
            weakenState=key;
            var b=new StringBuilder();
            for(String part:desc.split(" ")){if(part.isEmpty()||part.startsWith("cast"))continue;int id=Integer.parseInt(part.substring(0,part.indexOf(':')));var e=mc.world.getEntityById(id);
                b.append(' ').append(e==null?"entity "+id+" (not here)":e==mc.player?"me":e.getName().getString()).append(part.substring(part.indexOf(':'))).append("s");}
            note("weakened here:"+(b.length()==0?" nobody":b)+" ["+desc+"]; a blow of mine at the other is x"+factor());
        }
        for(var p:mc.world.getPlayers()){
            String sounds=dev.sangwoo.celestialfall.client.xchara.WeakenClient.sounds(p.getUuid());if(sounds==null||sounds.equals(weakenSounds.get(p.getUuid())))continue;
            weakenSounds.put(p.getUuid(),sounds);
            note("  "+name(p.getUuid())+"'s weakening's warning sound here: "+(sounds.equals("1")?"begun":sounds.equals("0")?"asked for":"none"));
        }
        boolean held=dev.sangwoo.celestialfall.client.xchara.WeakenClient.holds(mc.player);
        if(held!=weakenHeld){weakenHeld=held;note("the weakening holds me here: "+held);}
        long ready=dev.sangwoo.celestialfall.client.xchara.WeakenClient.ready(mc.player.getUuid());
        Object was=flags.get("weaken_ready");if(was==null||(Long)was!=ready){flags.put("weaken_ready",ready);if(ready>Long.MIN_VALUE)note("my weakening ready again at "+ready+" here (now "+mc.world.getTime()+": "+String.format(Locale.ROOT,"%.1f",(ready-mc.world.getTime())/20.0)+" s)");}
    }
    private final Map<UUID,String> slamState=new HashMap<>();private int slamHeard;
    /**
     * (2.128.0, MP_ONLY=xcharaslam) X Chara's slam as this client has it: each player's slam as it changes (its clock,
     * whether it stopped short, how high it rises, where it comes down once that is heard and how far into it), and at
     * a few ticks of it where that player is here and how far from there this client draws them, and whether their
     * knife is lit; its sounds as they are started; and its cooldown here.
     */
    private void slamWatch(){
        for(var p:mc.world.getPlayers()){
            var c=dev.sangwoo.celestialfall.client.xchara.KnifeClient.slamOf(p.getUuid());
            boolean lit=dev.sangwoo.celestialfall.client.xchara.HackMode.knifeLook(p.getUuid(),0)!=null;
            if(c==null){if(slamState.remove(p.getUuid())!=null)note(name(p.getUuid())+"'s slam gone here; their knife lit "+lit);continue;}
            var info=dev.sangwoo.celestialfall.client.xchara.KnifeClient.slam(c.id());int age=(int)Math.floor(c.age(0));
            boolean stopped=dev.sangwoo.celestialfall.client.xchara.KnifeClient.until(c.id())<1e6f;
            String key=c.id()+"/"+stopped+"/"+(info!=null&&info.land!=null)+"/"+(age==2||age==12||age==25||age==40||age==60||age==64||age==66||age==68||age==70||age==74||age==80||age==90?age:-1)+"/"+lit;
            if(key.equals(slamState.get(p.getUuid())))continue;
            slamState.put(p.getUuid(),key);
            var off=dev.sangwoo.celestialfall.client.xchara.OverwriteSlam.offset(p.getUuid(),0);
            note(String.format(Locale.ROOT,"%s's slam here: %d ticks in%s, rise %s, comes down %s; they are at (%.2f, %.2f, %.2f) here (%.2f, %.2f, %.2f from where it began), drawn %s from there; knife lit %s",name(p.getUuid()),age,stopped?" (stopped short)":"",
                info==null?"?":String.format(Locale.ROOT,"%.2f",info.rise),info==null||info.land==null?"not heard":String.format(Locale.ROOT,"(%.2f, %.2f, %.2f) from where it began, heard %.0f ticks in",info.land.x-c.origin().x,info.land.y-c.origin().y,info.land.z-c.origin().z,info.heard),
                p.getX(),p.getY(),p.getZ(),p.getX()-c.origin().x,p.getY()-c.origin().y,p.getZ()-c.origin().z,off==null?"nowhere else":String.format(Locale.ROOT,"(%.2f, %.2f, %.2f)",off.x,off.y,off.z),lit));
        }
        var heard=dev.sangwoo.celestialfall.client.xchara.KnifeClient.HEARD;
        if(heard.size()<slamHeard)slamHeard=0;
        for(;slamHeard<heard.size();slamHeard++)if(heard.get(slamHeard).contains("slam"))note("  slam sound: "+heard.get(slamHeard));
        long ready=dev.sangwoo.celestialfall.client.xchara.KnifeClient.slamReady(mc.player.getUuid());
        Object was=flags.get("slam_ready");if(was==null||(Long)was!=ready){flags.put("slam_ready",ready);if(ready>0)note("my slam ready again at "+ready+" here (now "+mc.world.getTime()+": "+String.format(Locale.ROOT,"%.1f",(ready-mc.world.getTime())/20.0)+" s)");}
    }
    private String pullState="";private final Map<UUID,String> pullSounds=new HashMap<>();private boolean pullHeld;
    /**
     * (2.127.0, MP_ONLY=xcharay) X Chara's pull as this client has it: the casts it heard of as they change (and then
     * where the other player is here, how far from me, and how far from there they are drawn), its two sounds begun,
     * whether it holds this player, and its cooldown here.
     */
    private void pullWatch(){
        String desc=dev.sangwoo.celestialfall.client.xchara.PullClient.describe(0);
        String key=desc.replaceAll("@[0-9.]+,(-?[0-9]+),-?[0-9.]+,","@,$1,");
        if(!key.equals(pullState)){
            pullState=key;
            var b=new StringBuilder();
            for(var p:mc.world.getPlayers()){if(p==mc.player)continue;var off=dev.sangwoo.celestialfall.client.xchara.PullClient.offset(p,0);
                b.append(String.format(Locale.ROOT,"; %s at (%.2f, %.2f, %.2f) here, %.2f from me, drawn %.2f from there, yaw %.0f",p.getName().getString(),p.getX(),p.getY(),p.getZ(),Math.hypot(p.getX()-mc.player.getX(),p.getZ()-mc.player.getZ()),off==null?0:off.length(),p.getYaw()));}
            note("pull here: ["+desc+"]; I am at "+String.format(Locale.ROOT,"(%.2f, %.2f, %.2f), yaw %.0f",mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYaw())+b);
        }
        for(var p:mc.world.getPlayers()){
            String sounds=dev.sangwoo.celestialfall.client.xchara.PullClient.sounds(p.getUuid());if(sounds==null||sounds.equals(pullSounds.get(p.getUuid())))continue;
            pullSounds.put(p.getUuid(),sounds);
            note("  "+name(p.getUuid())+"'s pull's sounds here (warning,burst; 1 begun, 0 asked for): "+sounds);
        }
        boolean held=dev.sangwoo.celestialfall.client.xchara.PullClient.holds(mc.player);
        if(held!=pullHeld){pullHeld=held;note("the pull holds me here: "+held);}
        long ready=dev.sangwoo.celestialfall.client.xchara.PullClient.ready(mc.player.getUuid());
        Object was=flags.get("pull_ready");if(was==null||(Long)was!=ready){flags.put("pull_ready",ready);if(ready>Long.MIN_VALUE)note("my pull ready again at "+ready+" here (now "+mc.world.getTime()+": "+String.format(Locale.ROOT,"%.1f",(ready-mc.world.getTime())/20.0)+" s)");}
    }
    private String factor(){
        for(var p:mc.world.getPlayers())if(p!=mc.player)return String.format(Locale.ROOT,"%.4f",dev.sangwoo.celestialfall.client.xchara.WeakenClient.factor(mc.player,p));
        return "?";
    }
    private void cloneWatch(){
        for(var p:mc.world.getPlayers()){
            String desc=dev.sangwoo.celestialfall.client.xchara.CloneClient.describe(p,0);if(desc==null)continue;
            String key=desc.substring(desc.indexOf(',')+1);
            if(key.equals(cloneState.get(p.getUuid())))continue;
            cloneState.put(p.getUuid(),key);
            var d=dev.sangwoo.celestialfall.client.xchara.CloneClient.of(p.getUuid());
            var e=mc.world.getEntityById(d.target);var off=e==null?null:dev.sangwoo.celestialfall.client.xchara.CloneClient.offset(e,0);
            note(String.format(Locale.ROOT,"%s's double here (seized,in the wall,ended,how) %s, %s ticks into it; the one marked %s, drawn %s from where this client has them; the wall's foot %.2f blocks along",
                name(p.getUuid()),key,desc.substring(0,desc.indexOf(',')),e==null?"not here":e==mc.player?"me":e.getName().getString(),off==null?"where they are":String.format(Locale.ROOT,"%.2f up %.2f off",off.y,Math.hypot(off.x,off.z)),
                d.wall.subtract(d.targetFrom).horizontalLength()));
        }
        for(var p:mc.world.getPlayers()){
            String sounds=dev.sangwoo.celestialfall.client.xchara.CloneClient.sounds(p.getUuid());if(sounds==null||sounds.equals(cloneSounds.get(p.getUuid())))continue;
            cloneSounds.put(p.getUuid(),sounds);
            String desc=dev.sangwoo.celestialfall.client.xchara.CloneClient.describe(p,0);
            note(String.format(Locale.ROOT,"  %s's double's sounds begun here (coming,throw,wall) %s, %s ticks into it",name(p.getUuid()),sounds,desc.substring(0,desc.indexOf(','))));
        }
        boolean held=dev.sangwoo.celestialfall.client.xchara.CloneClient.holds(mc.player);
        if(held!=cloneHeld){cloneHeld=held;note("the double holds me here: "+held);}
    }
    /** (2.115.0) X Chara's hack as this client has it: each player's state last logged, the sounds logged so far. */
    private final Map<UUID,String> hackState=new HashMap<>();private int hackHeard;
    /**
     * X Chara's hack as this client sees it (2.115.0, MP_ONLY=xcharaq): each player's as it changes here (on or off, and
     * whether the panel is drawn in their other hand), and every sound this client started for it.
     */
    private void hackWatch(){
        for(var p:mc.world.getPlayers()){
            String desc=dev.sangwoo.celestialfall.client.xchara.HackMode.describe(p,0);
            boolean panel=dev.sangwoo.celestialfall.client.xchara.HackMode.panel(p)!=null,lit=dev.sangwoo.celestialfall.client.xchara.HackMode.look(p.getUuid(),0)!=null;
            String key=(desc==null?"none":desc.substring(0,desc.indexOf(',')))+"/"+panel+"/"+lit;
            if(!key.equals(hackState.get(p.getUuid()))){
                hackState.put(p.getUuid(),key);
                note(String.format(Locale.ROOT,"%s's hack here: %s (its look drawn %s, the panel in their other hand %s, their body %d)",name(p.getUuid()),desc==null?"not known":desc.startsWith("1")?"on":"off",lit,panel,p.getId()));
            }
        }
        var heard=dev.sangwoo.celestialfall.client.xchara.HackMode.HEARD;
        if(heard.size()<hackHeard)hackHeard=0;
        for(;hackHeard<heard.size();hackHeard++)note("  hack sound: "+heard.get(hackHeard));
    }
    private int healHeard;private float healHealth=-1;
    /** X Chara's E as this client hears of it (2.119.0, MP_ONLY=xcharae): every line HealClient logged, and the caster's health here at each change. */
    private void healWatch(){
        var heard=dev.sangwoo.celestialfall.client.xchara.HealClient.HEARD;
        if(heard.size()<healHeard)healHeard=0;
        for(;healHeard<heard.size();healHeard++)note("  E here: "+heard.get(healHeard));
        for(var p:mc.world.getPlayers())if(name(p.getUuid()).equals("Caster")&&Math.abs(p.getHealth()-healHealth)>.01f){
            note(String.format(Locale.ROOT,"the caster's health here %.1f -> %.1f",healHealth,p.getHealth()));healHealth=p.getHealth();
        }
    }
    private void xcharaWatch(){
        for(var p:mc.world.getPlayers()){
            var d=dev.sangwoo.celestialfall.client.xchara.XCharaClient.of(p.getUuid());if(d==null)continue;
            String desc=dev.sangwoo.celestialfall.client.xchara.XCharaClient.describe(p,0);if(desc==null)continue;
            int comma=desc.indexOf(',');String key=d.cast.start()+":"+desc.substring(comma+1);
            boolean me=d.targetId.equals(mc.player.getUuid());
            if(!key.equals(xState)){xState=key;note(String.format(Locale.ROOT,"%s's G (began %d): age %s, space/ended/how %s; it marks %s",name(p.getUuid()),d.cast.start(),desc.substring(0,comma),desc.substring(comma+1),me?"me":"the other"));}
            float t=d.age(0);int tick=(int)Math.floor(t);
            // the "!!!" as this client draws it, every other tick while it shows (and the tick it goes)
            if(tick>=0&&tick<=dev.sangwoo.celestialfall.xchara.XCharaRules.WARN+8&&xSeen.add(d.cast.start()+"/mark/"+tick)){
                float shown=dev.sangwoo.celestialfall.client.xchara.SwordDance.markShown(d,t);
                if(tick%2==0||shown<=0&&xSeen.add(d.cast.start()+"/markgone"))note(String.format(Locale.ROOT,"  its \"!!!\" at %d: %.2f",tick,shown));
            }
        }
        var cine=dev.sangwoo.celestialfall.client.xchara.XCharaClient.cinematic();
        boolean cam=cine!=null;
        if(cam!=xCamera){xCamera=cam;note(cam?String.format(Locale.ROOT,"my camera in its strange space (its age %.1f)",cine.age(0)):"my camera back");}
        if(cine!=null){
            int tick=(int)Math.floor(cine.age(0));var camera=mc.gameRenderer.getCamera().getPos();
            if((tick==dev.sangwoo.celestialfall.xchara.XCharaRules.WARN+2||tick==dev.sangwoo.celestialfall.xchara.XCharaRules.WARN+10||tick==dev.sangwoo.celestialfall.xchara.XCharaRules.SHOT_C+2||tick==dev.sangwoo.celestialfall.xchara.XCharaRules.VANISH+2)&&xSeen.add(cine.cast.start()+"/cam/"+tick))
                note(String.format(Locale.ROOT,"  my camera at %d: %.2f from where the caster stands, %.2f over its feet; the world hidden %s",tick,Math.hypot(camera.x-cine.from.x,camera.z-cine.from.z),camera.y-cine.from.y,dev.sangwoo.celestialfall.client.xchara.XCharaClient.spaceHidden()));
        }
        boolean held=dev.sangwoo.celestialfall.client.xchara.XCharaClient.holds(mc.player);
        if(held!=xHeld){xHeld=held;note(held?"held by a sword dance":"free of the sword dance");}
    }
    /**
     * Y as this client sees it (2.97.0): each one as it is heard (on whom, its ring against the watcher's feet, how late),
     * and every tick through it the ring as drawn against the watcher's feet, the caster's height drawn over its formation's
     * ground and planned (her leap), how many swords are drawn and, for the watcher, whether it is held (its action locked)
     * as it changes; the own Y as the server told it.
     */
    private void yWatch(){
        for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(c.combo()!=dev.sangwoo.celestialfall.isosang.IsosangRules.CAGE)continue;
            var v=dev.sangwoo.celestialfall.client.isosang.IsosangClient.volley(c.id());var cage=dev.sangwoo.celestialfall.client.isosang.IsosangClient.cage(c.id());if(v==null||cage==null)continue;
            var who=mc.world.getPlayerByUuid(c.caster());var w=mc.world.getPlayers().stream().filter(q->q!=who).findFirst().orElse(null);
            if(ySeen.add(c.id()+"/heard")){var e=mc.world.getEntityById(v.target());
                note(String.format(Locale.ROOT,"%s's Y heard %.1f ticks into it: on %s, its ring at %.2f %.2f %.2f (%s)",name(c.caster()),c.age(0),
                    v.target()<0?"no one":e==mc.player?"me":e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():"entity "+v.target(),cage.at().x,cage.at().y,cage.at().z,
                    w==null?"no watcher":String.format(Locale.ROOT,"%.2f from the watcher's feet",Math.hypot(cage.at().x-w.getX(),cage.at().z-w.getZ()))));}
            int age=(int)Math.floor(c.age(0));
            if(who!=null&&age>=0&&age<=dev.sangwoo.celestialfall.isosang.IsosangRules.CAGE_LENGTH+2&&ySeen.add(c.id()+"/"+age)){
                var ring=dev.sangwoo.celestialfall.client.isosang.IsosangClient.cageRingNow(c.id());
                var formSwing=dev.sangwoo.celestialfall.client.isosang.IsosangClient.formationOf(c.caster());
                var form=formSwing==null?null:dev.sangwoo.celestialfall.client.isosang.IsosangClient.formation(formSwing.id());
                double high=Double.NaN,planned=Double.NaN;
                if(form!=null){high=who.getLerpedPos(0).y+dev.sangwoo.celestialfall.client.isosang.IsosangClient.lift(c.caster(),0)-form.centre().y;planned=dev.sangwoo.celestialfall.client.isosang.IsosangClient.raised(formSwing.id(),formSwing.age(0)/20.0);}
                note(String.format(Locale.ROOT,"%s's Y +%d: ring %s, set %s; the caster drawn %.2f over its ground (planned %.2f); %d swords drawn",name(c.caster()),age,
                    ring==null||w==null?"none":String.format(Locale.ROOT,"%.2f from the watcher's feet",Math.hypot(ring.x-w.getLerpedPos(0).x,ring.z-w.getLerpedPos(0).z)),cage.planted(),high,planned,
                    dev.sangwoo.celestialfall.client.isosang.IsosangClient.cageSwordsDrawn(c.id(),0)));
            }
        }
        if(!caster){boolean held=CombatClient.actionLocked();if(held!=heldSeen){heldSeen=held;note(held?"held (its actions locked)":"free (its actions unlocked)");}
            // the watcher walks sideways (to its left: neither into the formation nor out of Y's reach) while the caster asks
            // ("WALK+"): its ring follows it until set, and caged its steps are a fifth as long
            boolean walk=Files.exists(OUT.resolve("walk"));
            if(walk!=walking){walking=walk;key(GLFW_KEY_A,walk);walkAt=mc.player.getPos();note((walk?"press":"release")+" A (walk to its left, asked by the caster)");}
            else if(walking){var p=mc.player.getPos();note(String.format(Locale.ROOT,"walked %.3f blocks%s",Math.hypot(p.x-walkAt.x,p.z-walkAt.z),held?" (held)":""));walkAt=p;}}
        long yr=dev.sangwoo.celestialfall.client.isosang.IsosangClient.yReady(mc.player.getUuid());
        if(yr!=yReadySeen){yReadySeen=yr;note("own Y: ready again at "+yr+" (world tick "+mc.world.getTime()+")");}
    }
    /** (2.101.0) H again's swings seen (and each tick of them logged), and the own H's ready tick last logged. */
    private final java.util.Set<String> finaleSeen=new java.util.HashSet<>();private long hhReadySeen=-1;
    /**
     * H again as this client sees it (2.101.0): as it is heard (its recording, where she stands against where the caster
     * is, the formation it takes, how late); every 4 ticks through it the formation's swords still drawn by the formation
     * and the finale's swords drawn, how high the caster is drawn and really is over where she stands, (2.101.1) how far
     * from where she stands she is drawn round her lap against its plan, whether her hand is empty, and (the caster)
     * whether her cutscene is on and her camera; the own H's ready tick as the server told it.
     */
    private void finaleWatch(){
        for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(c.combo()!=dev.sangwoo.celestialfall.isosang.IsosangRules.FINALE)continue;
            var r=dev.sangwoo.celestialfall.client.isosang.IsosangClient.finale(c.id());if(r==null)continue;
            var who=mc.world.getPlayerByUuid(c.caster());
            if(finaleSeen.add(c.id()+"/heard"))note(String.format(Locale.ROOT,"%s's H again heard %.1f ticks into it: recording %d, she stands %s, it takes formation %s (%s)",name(c.caster()),c.age(0),r.variant()+1,
                who==null?"(no caster)":String.format(Locale.ROOT,"%.2f from the caster",Math.hypot(r.spot().x-who.getX(),r.spot().z-who.getZ())),r.form().toString().substring(0,8),
                dev.sangwoo.celestialfall.client.isosang.IsosangClient.takenBy(r.form())==c?"taken":"not taken"));
            int age=(int)Math.floor(c.age(0));
            if(who!=null&&age>=0&&age<=dev.sangwoo.celestialfall.isosang.IsosangRules.FINALE_LENGTH+4&&age%4==0&&finaleSeen.add(c.id()+"/"+age)){
                double real=who.getLerpedPos(0).y-r.spot().y,drawn=real+dev.sangwoo.celestialfall.client.isosang.IsosangClient.lift(c.caster(),0);
                // (2.101.1) and level: where her body is drawn round her lap against where it plans her
                var level=who.getLerpedPos(0).add(dev.sangwoo.celestialfall.client.isosang.IsosangClient.shift(c.caster(),0));
                var plan=dev.sangwoo.celestialfall.client.isosang.IsosangClient.finaleOver(c,c.age(0)/20.0);
                note(String.format(Locale.ROOT,"%s's H again +%d: formation swords standing %d, its own swords drawn %d; the caster %.2f up (drawn %.2f, planned %.2f), %.2f off where she stands, drawn %.2f off it (planned %.2f, %.2f apart), hand %s%s",name(c.caster()),age,
                    dev.sangwoo.celestialfall.client.isosang.IsosangClient.finaleFormationStanding(r.form()),dev.sangwoo.celestialfall.client.isosang.IsosangClient.finaleSwordsDrawn(c.id()),real,drawn,
                    dev.sangwoo.celestialfall.isosang.IsosangRules.finaleDrawn(c.age(0)/20.0,dev.sangwoo.celestialfall.client.isosang.IsosangClient.finalePommel(c.id())),
                    Math.hypot(who.getX()-r.spot().x,who.getZ()-r.spot().z),Math.hypot(level.x-r.spot().x,level.z-r.spot().z),Math.hypot(plan.x-r.spot().x,plan.z-r.spot().z),Math.hypot(level.x-plan.x,level.z-plan.z),
                    dev.sangwoo.celestialfall.client.isosang.IsosangCinema.emptyHanded(who)?"empty":"holding her sword",
                    caster?String.format(Locale.ROOT,", cutscene %s",dev.sangwoo.celestialfall.client.isosang.IsosangCinema.active()):""));
            }
        }
        long hr=dev.sangwoo.celestialfall.client.isosang.IsosangClient.hReady(mc.player.getUuid());
        if(hr!=hhReadySeen){hhReadySeen=hr;note("own H: ready again at "+hr+" (world tick "+mc.world.getTime()+")");}
    }
    /** (2.93.0) Z's flights seen (by swing and its tick), and the own Z last logged. */
    private final java.util.Set<String> zFlightSeen=new java.util.HashSet<>();private dev.sangwoo.celestialfall.isosang.IsosangRules.ZState zStateSeen;
    /**
     * Z as this client sees it (2.93.0): each flight as it is heard (from which sword to which, how late), and every tick
     * for 14 ticks from its start the caster hidden or not, how far off the formation's plan it is and is drawn (level:
     * where it is between ticks, and that moved as the client draws it, IsosangClient.shift) and how high it is drawn over
     * the plan's height; the own Z as the server told it.
     */
    private void zWatch(){
        for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(c.combo()!=dev.sangwoo.celestialfall.isosang.IsosangRules.FORMATION)continue;
            var form=dev.sangwoo.celestialfall.client.isosang.IsosangClient.formation(c.id());var who=mc.world.getPlayerByUuid(c.caster());if(form==null||who==null)continue;
            for(var fl:dev.sangwoo.celestialfall.client.isosang.IsosangClient.flights(c.id())){
                double since=c.age(0)-(fl.moved()-c.start());String key=c.id()+"/z"+fl.moved();
                if(zFlightSeen.add(key))note(String.format(Locale.ROOT,"%s's Z heard: from sword %d to sword %d, %.1f ticks into its flight here",name(c.caster()),fl.from(),fl.to(),since));
                if(since>=0&&since<=14&&zFlightSeen.add(key+"/"+(int)Math.floor(since))){
                    var at=who.getLerpedPos(0);var plan=dev.sangwoo.celestialfall.client.isosang.IsosangClient.spot(c.id(),form,c.age(0));var drawn=at.add(dev.sangwoo.celestialfall.client.isosang.IsosangClient.shift(c.caster(),0));
                    double high=at.y+dev.sangwoo.celestialfall.client.isosang.IsosangClient.lift(c.caster(),0)-form.centre().y,planned=dev.sangwoo.celestialfall.client.isosang.IsosangClient.raised(c.id(),c.age(0)/20.0);
                    note(String.format(Locale.ROOT,"%s's Z %d->%d +%.0f: hidden %s, %.2f off the plan level (drawn %.2f), drawn %.2f over its ground (planned %.2f)",name(c.caster()),fl.from(),fl.to(),Math.floor(since),
                        dev.sangwoo.celestialfall.client.isosang.SwordStep.hides(who),Math.hypot(at.x-plan.x,at.z-plan.z),Math.hypot(drawn.x-plan.x,drawn.z-plan.z),high,planned));
                }
            }
        }
        var zs=dev.sangwoo.celestialfall.client.isosang.IsosangClient.zState(mc.player.getUuid());
        if(!zs.equals(zStateSeen)){zStateSeen=zs;note(String.format(Locale.ROOT,"own Z: first %d, used %d, ready again at %d (world tick %d)",zs.first(),zs.uses(),zs.ready(),mc.world.getTime()));}
    }
    /** The watcher (2.77.0): its staggers as its client knows them, and every move of its body (a blow throws no one). */
    private long rSeen=-1,hSeen=-1,gSeen=-1;private final java.util.Set<String> formationSeen=new java.util.HashSet<>(),ultSeen=new java.util.HashSet<>();
    private void watcherStill(){
        long now=mc.world.getTime();var mine=CombatClient.snapshot(mc.player.getUuid());
        boolean st=mine.staggered(now);if(st!=stillStaggered){stillStaggered=st;note(st?"staggered (until "+mine.staggerUntil()+")":"free of its stagger");}
        var p=mc.player.getPos();
        if(stillAt!=null&&p.distanceTo(stillAt)>.01)note(String.format(Locale.ROOT,"moved %.3f blocks (velocity %.3f %.3f %.3f)",p.distanceTo(stillAt),mc.player.getVelocity().x,mc.player.getVelocity().y,mc.player.getVelocity().z));
        stillAt=p;
    }
    private void staggerWatch(){
        long now=mc.world.getTime();var mine=CombatClient.snapshot(mc.player.getUuid());
        // struck: its hurt time comes back up (its health may be healed back before this client hears of it)
        int hurt=mc.player.hurtTime;
        if(hurt>lastHurt){note(String.format(Locale.ROOT,"struck (health %.1f)",mc.player.getHealth()));if(mashUntil<age)note("mashing its attack");mashUntil=age+36;}
        lastHurt=hurt;
        if(age<mashUntil&&age%3==0){note("try M1 ("+(mine.staggered(now)?"staggered":"free")+(CombatClient.actionLocked()?", its client holds it":", its client lets it")+")");attack(true);mashRelease=age+1;}
        if(age==mashRelease)attack(false);
        if(mine.attack()>0&&mine.attackStart()!=lastStrike){lastStrike=mine.attackStart();note("its own strike "+mine.attack()+" started at "+lastStrike);}
        boolean walk=Files.exists(OUT.resolve("walk"));
        if(walk!=walking){walking=walk;key(GLFW_KEY_W,walk);note((walk?"press":"release")+" W (walk, asked by the caster)");
            if(walk){walkAt=mc.player.getPos();walkStaggered.clear();walkFree.clear();}
            else{var a=new ArrayList<>(walkStaggered);var b=new ArrayList<>(walkFree);a.sort(null);b.sort(null);
                note(String.format(Locale.ROOT,"walked %.2f blocks/s while staggered (median of %d ticks), %.2f blocks/s while free (median of %d ticks)%s",
                    a.isEmpty()?0:a.get(a.size()/2)*20,a.size(),b.isEmpty()?0:b.get(b.size()/2)*20,b.size(),dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView()?" (stopped in time)":""));}}
        if(walking){var p=mc.player.getPos();if(mc.player.isOnGround())(mine.staggered(now)?walkStaggered:walkFree).add(Math.hypot(p.x-walkAt.x,p.z-walkAt.z));walkAt=p;}
    }
    /**
     * The stand arrow as this client knows it (2.70.0): every stab heard (when, what it chose and when this client heard
     * it, cut short), the caster's body as drawn here (the stab's pose on and off, at what age; how far down it went;
     * lying where it fell as it dies), and the stab's sounds as they start (at what age of the stab).
     */
    private void arrowWatch(){
        if(!arrowListening&&mc.getSoundManager()!=null){arrowListening=true;mc.getSoundManager().registerListener((sound,set)->{
            // (2.71.0: the ground's own sounds it plays for the knees and the fall, and any cry: none at the arrow's death)
            String id=sound.getId().getPath();if(!id.startsWith("stand_arrow")&&!id.endsWith(".fall")&&!id.endsWith(".step")&&!id.contains("player.death")&&!id.contains("player.hurt"))return;
            var s=dev.sangwoo.celestialfall.client.stand.StandArrowClient.all().values().stream().findFirst().orElse(null);
            note(String.format(Locale.ROOT,"sound %s at stab age %.2f (volume %.2f)",id,s==null?-1:s.age(0),sound.getVolume()));});}
        for(var s:dev.sangwoo.celestialfall.client.stand.StandArrowClient.all().values()){
            String key=s.user+"@"+s.start;
            var seen=arrowSeen.get(key);
            if(seen==null){arrowSeen.put(key,seen=new String[]{"","",""});note(String.format(Locale.ROOT,"stab heard: %s from tick %d, heard at its age %.2f",name(s.user),s.start,s.age(0)));}
            String fate=s.cutShort()?"cut short at "+String.format(Locale.ROOT,"%.2f",s.cut):s.fate.name()+(Float.isNaN(s.known)?"":String.format(Locale.ROOT," (%s, heard at %.2f)",s.outcome,s.known));
            if(!fate.equals(seen[0])){seen[0]=fate;note("stab of "+name(s.user)+": "+fate);}
            var body=mc.world.getPlayerByUuid(s.user);
            var f=body==null?null:dev.sangwoo.celestialfall.client.stand.StandArrowClient.frame(body,0);
            String pose=f==null?"off":"on";
            if(!pose.equals(seen[1])){seen[1]=pose;note(String.format(Locale.ROOT,"stab pose of %s %s at its age %.2f",name(s.user),pose,s.age(0)));}
            if(f!=null&&f.fallen()>arrowFallen+.2f){arrowFallen=f.fallen();note(String.format(Locale.ROOT,"%s fallen %.2f at its age %.2f",name(s.user),f.fallen(),s.age(0)));}
            String lying=body!=null&&dev.sangwoo.celestialfall.client.stand.StandArrowClient.lying(body)?"lying (death time "+body.deathTime+")":"";
            if(!lying.isEmpty()&&seen[2].isEmpty()){seen[2]=lying;note(name(s.user)+" "+lying+" at its age "+String.format(Locale.ROOT,"%.2f",s.age(0)));}
        }
        if(dev.sangwoo.celestialfall.client.stand.StandArrowClient.all().isEmpty())arrowFallen=0;
    }
    private boolean arrowListening;private float arrowFallen;private final Map<String,String[]> arrowSeen=new HashMap<>();
    private boolean guardAsked;private int deadFor;private final Map<Object,int[]> beatHeard=new IdentityHashMap<>();
    /** Per beatdown here: its one struck's hurt time last seen, how often their body flashed red and when it last did (its clock). */
    private final Map<Object,float[]> beatFlash=new IdentityHashMap<>();
    /**
     * Each player's beatdown heard here (2.64.0): as it starts (its age here, whom it struck, its damage, where they come
     * down), its cutscene's sound starting (its tick on its clock here: StarPlatinumRules.BEAT_SOUND), where its one
     * struck is shown twice a second (ahead of its user along the blow and up, and where they really are), and its end
     * with how often the one struck's body flashed red here (a blow's hurt time) and when last (its clock: every blow
     * to the last, even those that find them at one health, StarPlatinumBeatdown.blow); and whether this client's
     * camera is the cutscene's, its black bars, the sky-blue glow of this player's body, and (2.65.0) whether this client
     * drew a body glowing sky blue.
     */
    private void beats(){
        for(var p:mc.world.getPlayers()){
            var b=dev.sangwoo.celestialfall.client.stand.StandBeatdown.of(p.getUuid());if(b==null)continue;
            int[] st=beatHeard.computeIfAbsent(b,k->new int[3]);String who=p.getGameProfile().getName()+"'s beatdown";float t=b.age(0);
            var v=mc.world.getEntityById(b.victim);
            if(st[0]==0){st[0]=1;note(String.format(Locale.ROOT,"%s heard at %.2f s on its clock here, on %s, its damage %.1f, they come down %.2f blocks ahead of its user",who,t,
                v instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(b.victim),b.damage,b.land.subtract(b.from).dotProduct(b.f)));}
            if(st[1]==0&&b.voice()!=null&&mc.getSoundManager().isPlaying(b.voice())){st[1]=1;note(String.format(Locale.ROOT,"%s: its cutscene's sound started at its tick %.0f here",who,t*20));}
            if(v!=null&&age%10==0&&!b.ended()){var off=dev.sangwoo.celestialfall.client.stand.StandBeatdown.offset(v,0);
                if(off!=null){var d=v.getPos().add(off).subtract(b.from);note(String.format(Locale.ROOT,"%s at %.2f s: its one struck shown %.2f blocks ahead, %.2f up (really %.2f ahead)",who,t,d.dotProduct(b.f),d.y,v.getPos().subtract(b.from).dotProduct(b.f)));}}
            float[] fl=beatFlash.computeIfAbsent(b,k->new float[]{0,0,Float.NaN});
            if(v instanceof net.minecraft.entity.LivingEntity l){if(l.hurtTime>fl[0]){fl[1]++;fl[2]=t;}fl[0]=l.hurtTime;}
            if(st[2]==0&&b.ended()){st[2]=1;note(String.format(Locale.ROOT,"%s %s at %.2f s on its clock here; its one struck flashed red %.0f times here, last at %.2f s",who,b.wasCut()?"cut short":"over",t,fl[1],fl[2]));}
        }
        flag("beat_camera",dev.sangwoo.celestialfall.client.stand.StandBeatdown.cinematic()!=null);
        float bars=dev.sangwoo.celestialfall.client.stand.StandBeatdown.bars(0);
        flag("beat_bars",bars<=0?"none":bars>=dev.sangwoo.celestialfall.client.stand.StandBeatdown.BAR-1e-4f?"full":"sliding");
        flag("flash",dev.sangwoo.celestialfall.client.CutsceneFlash.local(0)>0);
        // 2.65.0: whether this client drew any body glowing sky blue last frame (its own, or the other player's)
        flag("glowing",dev.sangwoo.celestialfall.client.stand.StandAuraLayer.glowedLast()>0);
    }
    private final Map<Object,int[]> stopHeard=new IdentityHashMap<>();private final Set<String> stillSounds=new LinkedHashSet<>();private boolean stillListening;private int stillTicks;
    private net.minecraft.util.math.Vec3d stoppedAt;
    /**
     * Each player's time stop heard here (2.67.0): as it is called (its tick here), its call's sound starting (its tick
     * on its clock: StarPlatinumRules.TS_SOUND), time stopping (whom it stopped), its end's sound starting, time moving
     * again (the harm that came to each); whether this client draws the other player held still; for the one it
     * stopped, whether its screen is held still and every sound that played meanwhile (only the time stop's own may).
     * Stopped, the watcher tries the attack, the dash, walking and its guard, and logs how far it moved.
     */
    private void stops(){
        if(!stillListening&&mc.getSoundManager()!=null){stillListening=true;
            mc.getSoundManager().registerListener((sound,set)->{if(dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView())stillSounds.add(sound.getId().toString());});}
        for(var p:mc.world.getPlayers()){
            var s=dev.sangwoo.celestialfall.client.stand.StandTimeStop.of(p.getUuid());if(s==null)continue;
            int[] st=stopHeard.computeIfAbsent(s,k->new int[6]);String who=p.getGameProfile().getName()+"'s time stop";
            if(st[0]==0){st[0]=1;note(String.format(Locale.ROOT,"%s heard (server tick %d, its tick here %d)",who,s.start(),s.ticksHere()));}
            if(st[1]==0&&s.voiceSound()!=null&&mc.getSoundManager().isPlaying(s.voiceSound())){st[1]=1;note(String.format(Locale.ROOT,"%s: its call's sound started at its tick %d here",who,s.ticksHere()));}
            if(st[2]==0&&s.timeStopped()){st[2]=1;var names=new ArrayList<String>();
                for(int id:s.whom()){var e=mc.world.getEntityById(id);names.add(e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(id));}
                note(String.format(Locale.ROOT,"%s: time stopped at its tick %d here, stopping %s",who,s.ticksHere(),names));
                if(!caster&&s.whom().contains(mc.player.getId())){tries.put(age+5,"M1");tries.put(age+12,"C");tries.put(age+20,"W+");tries.put(age+40,"W-");tries.put(age+50,"F+");tries.put(age+60,"F-");stoppedAt=mc.player.getPos();}
                // 2.75.0 (MP_ONLY=resist): not stopped, a Star Platinum user resisting it: its own skills tried, a jab at the
                // caster, then a walk back through its two seconds and on
                else if(!caster&&ONLY.equals("resist")&&p!=mc.player){
                    tries.put(age+2,"H");tries.put(age+4,"Y");tries.put(age+6,"G");tries.put(age+8,"M1");tries.put(age+16,"S+");tries.put(age+64,"S-");
                    resistFrom=age;resistAt=mc.player.getPos();resistStart=mc.player.getPos();note(String.format(Locale.ROOT,"not stopped by it: resisting (at %.2f %.2f)",resistAt.x,resistAt.z));}}
            // (2.75.0) whom it holds grown after time stopped: Star Platinum's users stopped as their two seconds ran out (LATE)
            if(st[2]==1&&st[5]==0)st[5]=s.whom().size()+1;
            if(st[2]==1&&Float.isNaN(s.endedAt())&&s.whom().size()+1!=st[5]){var names=new ArrayList<String>();
                for(int id:s.whom()){var e=mc.world.getEntityById(id);names.add(e instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(id));}
                note(String.format(Locale.ROOT,"%s: now holds %s, at its tick %d here",who,names,s.ticksHere()));st[5]=s.whom().size()+1;}
            if(st[3]==0&&s.endSound()!=null&&mc.getSoundManager().isPlaying(s.endSound())){st[3]=1;note(String.format(Locale.ROOT,"%s: its end's sound started at its tick %d here",who,s.ticksHere()));}
            if(st[4]==0&&!Float.isNaN(s.endedAt())){st[4]=1;var owed=new ArrayList<String>();
                for(var e:s.owedHarm().entrySet()){var x=mc.world.getEntityById(e.getKey());owed.add(String.format(Locale.ROOT,"%s %.1f",x instanceof net.minecraft.entity.player.PlayerEntity q?q.getGameProfile().getName():String.valueOf(e.getKey()),e.getValue()));}
                note(String.format(Locale.ROOT,"%s %s at %.2f s on its clock here (its tick %d); the harm owed: %s",who,s.wasCut()?"cut short":"over, time moves again",s.endedAt(),s.ticksHere(),owed));stoppedAt=null;}
        }
        var other=mc.world.getPlayers().stream().filter(q->q!=mc.player).findFirst().orElse(null);
        if(other!=null){
            boolean held=dev.sangwoo.celestialfall.client.stand.StandTimeStop.stopped(other);
            flag("other_held_still",held);
            // (2.67.0) a blow on one stopped holds nothing besides: no flinch is drawn on it until time moves again
            if(held){otherHeldTicks++;if(dev.sangwoo.celestialfall.client.HitStunClient.reaction(other)!=null)otherFlinched++;}
            else if(otherHeldTicks>0){note("while the other player stood stopped ("+otherHeldTicks+" ticks) it was drawn flinching on "+otherFlinched);otherHeldTicks=0;otherFlinched=0;}
        }
        boolean view=dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView();
        flag("screen_held_still",view);
        if(view){stillTicks++;if(dev.sangwoo.celestialfall.client.HitStunClient.localStunned())stillStunned++;}
        else if(stillTicks>0){note("while its screen was held ("+stillTicks+" ticks) the sounds that played: "+stillSounds+"; held by a blow (its hit-stun notice) on "+stillStunned);stillTicks=0;stillStunned=0;stillSounds.clear();}
    }
    private int otherHeldTicks,otherFlinched,stillStunned;
    /** 2.75.0 (MP_ONLY=resist): when the watcher heard time stop without stopping it, where it was then and last tick, whether it brought its stand out. */
    private int resistFrom=-1;private net.minecraft.util.math.Vec3d resistAt,resistStart;private boolean resistQ;
    /**
     * The watcher in the resisting check (2.75.0): brings its Star Platinum out once it has it; from hearing time stop
     * without being stopped until 75 ticks on, each tick it moved (how far, how far from where time stopped), and each
     * tick its screen held or its client holding it changed.
     */
    private void resistWatch(){
        if(!resistQ&&dev.sangwoo.celestialfall.stand.StandManager.starPlatinum(mc.player)&&!((dev.sangwoo.celestialfall.stand.StandHolder)mc.player).botand$isStandOut()&&age>20){resistQ=true;tries.put(age+2,"Q");note("bring my own Star Platinum out");}
        if(resistFrom<0||resistAt==null)return;
        int in=age-resistFrom;
        if(in>75){note(String.format(Locale.ROOT,"resisting check over: %.2f blocks from where time stopped",mc.player.getPos().distanceTo(resistStart)));resistFrom=-1;return;}
        var p=mc.player.getPos();double step=p.distanceTo(resistAt);resistAt=p;
        String state=String.format(Locale.ROOT,"screen %s, %s",dev.sangwoo.celestialfall.client.stand.StandTimeStop.stillView()?"held":"moving",StandClient.frozen(mc.player)?"held by its client":"free");
        if(step>1e-4||!state.equals(resistState))note(String.format(Locale.ROOT,"resisting %d ticks in: moved %.3f blocks (%.2f from where time stopped), %s",in,step,p.distanceTo(resistStart),state));
        resistState=state;
    }
    private String resistState="";
    private final Map<Integer,String> tries=new HashMap<>();private int release=-1,releaseKey;private int[] fingerWalk;
    private net.minecraft.util.math.Vec3d walkFrom;private final List<Double> walkSteps=new ArrayList<>();
    /** A player's stand as this client sees it: its id, out or in as the server tracks it, and the stand drawn here. */
    private String stand(net.minecraft.entity.player.PlayerEntity p){
        var h=(dev.sangwoo.celestialfall.stand.StandHolder)p;var st=dev.sangwoo.celestialfall.client.stand.StandPresence.state(p.getUuid());
        String shown=st==null?"none":st[1]==0?"going back":st[0]<dev.sangwoo.celestialfall.client.stand.StandTimeline.SETTLED?"coming out":"at rest";
        return h.botand$getStand()+(h.botand$isStandOut()?" out":" in")+", here "+shown+(dev.sangwoo.celestialfall.client.stand.StandPresence.shown(p.getUuid())!=null?" (drawn)":"");
    }
    private void shot(){
        var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());frames++;
        Path p=dir().resolve(String.format(Locale.ROOT,"%06d.png",mc.world.getTime()));
        writer.submit(()->{try(image){Files.createDirectories(p.getParent());image.writeTo(p);}catch(Exception e){throw new RuntimeException(e);}});
    }
}
