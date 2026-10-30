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
     * Lee So-sang's basic attack (2.77.0), on its own (MP_ONLY=isosang): the caster takes Lee So-sang ("ISOSANG"), calls
     * the watcher 2.5 blocks in front of it at full health and holds its attack down: the four hits at full speed (the
     * last can take no guard). Then a hit, and the next 40 ticks later: the combo has broken and the attack rests (that
     * press refused), and a press after the rest starts the first hit again. Then the four hits with the watcher 8 blocks
     * away ("AWAY": the first three strike no one) and brought back in front of the caster, its guard up ("GUARD+"),
     * as the fourth comes: the fourth goes through the guard. Both log every hit they see (its number, the tick
     * it began on, its age as they heard of it), whom its blows struck and its sounds as they start (at what age of the
     * hit), the watcher its own staggers and every move of its body (a blow throws no one); the server the caster's hits
     * and blows, the watcher's health, its staggers and any move of it.
     */
    private static final String[] ISOSANG_SCRIPT={
        "5 ISOSANG","40 HOME","45 HEAL","60 M1+","100 M1-",
        "180 HOME","185 HEAL","190 M1","230 M1","262 M1",
        "330 AWAY","335 HEAL","338 GUARD+","350 M1+","378 HOME","390 M1-","405 GUARD-",
        "470 END"};
    private static final String[] STEPS=ONLY.equals("isosang")?ISOSANG_SCRIPT:ONLY.equals("resist")?RESIST_SCRIPT:ONLY.equals("fresh")?FRESH_SCRIPT:ONLY.equals("swap")?SWAP_SCRIPT:ONLY.equals("cooldowns")?COOLDOWNS_SCRIPT:ONLY.equals("call")?CALL_SCRIPT:ONLY.equals("beatdown")?BEATDOWN_SCRIPT:ONLY.equals("timestop")?TIMESTOP_SCRIPT:ONLY.equals("stagger")?STAGGER_SCRIPT:ONLY.equals("boss")?BOSS_SCRIPT:ONLY.equals("arrow")?ARROW_SCRIPT:SCRIPT;
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
    private void attack(boolean down){var k=InputUtil.Type.MOUSE.createFromCode(GLFW_MOUSE_BUTTON_LEFT);KeyBinding.setKeyPressed(k,down);if(down)KeyBinding.onKeyPressed(k);}
    private String name(UUID id){var p=mc.world.getPlayerByUuid(id);return p==null?id.toString().substring(0,8):p.getGameProfile().getName();}
    /** The watcher looks a little up at G's pillar from outside; otherwise both look slightly down at each other. */
    private void face(){if(mc.player==null)return;BotandCamera.STATE.yaw=caster?0:180;BotandCamera.STATE.pitch=!caster&&!AtomicClient.CASTS.isEmpty()?-8:12;}

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
                if(ONLY.equals("isosang"))isosangWatch();
                if(age%4==0)shot();
                if(caster){
                    if(age%40==0&&held.isEmpty())face();
                    measure();
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
                    if(ONLY.equals("isosang"))watcherStill();
                    if(age>(ONLY.equals("isosang")?900:ONLY.equals("beatdown")?1600:ONLY.equals("timestop")||ONLY.equals("resist")?1000:ONLY.equals("stagger")?1400:ONLY.equals("boss")?2600:ONLY.equals("arrow")?1100:ONLY.equals("call")?900:ONLY.equals("cooldowns")?3400:ONLY.equals("swap")?1300:ONLY.equals("fresh")?1700:4400)){note("ERROR watcher timeout");phase=3;age=0;}
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
                if(mc.world.getPlayers().size()>=2&&age>60){note("back in the game, the script at "+resumeAt);flags.clear();face();age=resumeAt;phase=2;traceUntil=resumeAt+100;}
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
        if(step.equals("SLAIN")){if(down)command("damage @s 1000 minecraft:player_attack by Watcher");return;}
        if(step.equals("DUMMY")){if(down){note("command botand dummy atomic 30");mc.player.networkHandler.sendChatCommand("botand dummy atomic 30");}return;}
        if(step.equals("STAND")){if(down){note("command botand progress stand @s star_platinum");mc.player.networkHandler.sendChatCommand("botand progress stand @s star_platinum");}return;}
        // 2.74.2: the caster takes Kageno; a Rokakaka in its main hand, eaten as a left click with it asks and as the
        // confirmation's 사용하고 초기화 is pressed
        if(step.equals("STANDK")){if(down)command("botand progress stand @s kageno");return;}
        // 2.77.0: the caster takes Lee So-sang (MP_ONLY=isosang)
        if(step.equals("ISOSANG")){if(down)command("botand progress stand @s isosang");return;}
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
        // 2.68.0: the watcher's walk asked for (the file OUT/walk), as its guard is
        if(step.startsWith("WALK")){if(down){try{if(step.endsWith("+"))Files.writeString(OUT.resolve("walk"),"walk");else Files.deleteIfExists(OUT.resolve("walk"));}catch(Exception e){fail(e);}note("watcher's walk "+(step.endsWith("+")?"asked for":"stopped"));}return;}
        // 2.64.0: the watcher at full health, or brought down to a quarter of it; its guard asked for
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
        flag("atomic",AtomicClient.CASTS.size());flag("overdrive",StandClient.OVERDRIVE.size());flag("hits",StandClient.hitCount);
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
    private void isosangWatch(){
        if(!swingListening&&mc.getSoundManager()!=null){swingListening=true;mc.getSoundManager().registerListener((sound,set)->{
            String id=sound.getId().getPath();if(!id.startsWith("isosang_m1"))return;
            var newest=dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values().stream().reduce((a,b)->b).orElse(null);
            note(String.format(Locale.ROOT,"sound %s at hit %s's age %.2f ticks (volume %.2f)",id,newest==null?"-":String.valueOf(newest.combo()),newest==null?-1:newest.age(0),sound.getVolume()));});}
        long now=mc.world.getTime();
        for(var c:dev.sangwoo.celestialfall.client.isosang.IsosangClient.SWINGS.values()){
            if(swingsSeen.add(c.id()))note(String.format(Locale.ROOT,"%s's hit %d seen (began on world tick %d, its age %.0f ticks as heard)",name(c.caster()),c.combo(),now-(long)Math.floor(c.age(0)),c.age(0)));
            int n=c.targets().size();Integer was=swingStruck.put(c.id(),n);
            if(was==null?n>0:n>was)note(String.format(Locale.ROOT,"%s's hit %d struck %d (at its age %.0f ticks)",name(c.caster()),c.combo(),n,c.age(0)));
        }
    }
    /** The watcher (2.77.0): its staggers as its client knows them, and every move of its body (a blow throws no one). */
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
