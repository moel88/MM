"""Multiplayer smoke test of the release jar in a production Fabric setup (no dev mappings):
a dedicated server plus two clients ("Caster" plays every Kageno key, "Watcher" stands in front),
each client under its own Xvfb display. Everything comes from the offline Gradle/Loom caches. The clients play their
sound to OpenAL Soft's null device (2.54.0: at full volume, so each Star Platinum shout is seen to start).
usage: python3 tools/kageno/mp/run_mp.py OUT_DIR [MOD_JAR]
MP_ONLY=beatdown runs only Star Platinum's beatdown's own script (2.64.0, MpClient.BEATDOWN_SCRIPT), MP_ONLY=timestop
only its time stop's (2.67.0, MpClient.TIMESTOP_SCRIPT), MP_ONLY=stagger only the basic attack's stagger and the time
stop's 20 blocks (2.68.0, MpClient.STAGGER_SCRIPT), MP_ONLY=boss only Kujo Jotaro's (2.69.0, MpClient.BOSS_SCRIPT; with
MP_RESTART=1 the server is started once more afterwards on the same world to see a boss in play come back), MP_ONLY=arrow
only the stand arrow's (2.70.0, MpClient.ARROW_SCRIPT), MP_ONLY=call only Star Platinum's call (Q out and back, its
sounds) and its time stop for the awakened (2.72.0, MpClient.CALL_SCRIPT), MP_ONLY=cooldowns only the long cooldowns
(2.73.0, MpClient.COOLDOWNS_SCRIPT: the Atomic, the time stop and the beatdown begun again in full as the caster leaves
and joins again, and since 2.74.0 as it comes back to life after any death (its reset, /kill, killed by the watcher) and
as the stand arrow gives it a stand; since 2.74.1 only those of the stand it uses, and also as the gacha gives it Kageno),
MP_ONLY=swap only another stand's cooldowns gone (2.74.2, MpClient.SWAP_SCRIPT: Star Platinum's skills used, a Rokakaka
eaten and the stand given back at once; Kageno's E, a Rokakaka again), MP_ONLY=isosang only Lee So-sang's basic attack (2.77.0, MpClient.ISOSANG_SCRIPT: the four hits at full speed, a broken
combo and its rest, the four against a guard), MP_ONLY=isosange its E (2.80.0, MpClient.ISOSANGE_SCRIPT), MP_ONLY=isosangh its H (2.82.0, MpClient.ISOSANGH_SCRIPT: the sword formation with the watcher in it, the attack and E refused
and R used up on the sword (2.82.3: the inner ring's sword ahead, the watcher put before it first), H again refused; the caster held up some 39 s with nothing under it), MP_ONLY=isosangr
its R (2.81.0, MpClient.ISOSANGR_SCRIPT: the giant sword's sweep at the watcher close and 8 blocks off in the crosshair,
again at once (refused), a press of the attack kept through it, R kept through a hit, through a guard; 2.81.1: R right in
front whatever the crosshair is on, R held at the watcher 25 blocks off in the crosshair), MP_ONLY=isosangz its Z (2.93.0, MpClient.ISOSANGZ_SCRIPT: three moves across H's swords within ten seconds, a fourth refused,
one after the cooldown and its window run out, R from that sword, Z after the window refused), MP_ONLY=isosangdodge its H's
dodge (2.94.0, MpClient.ISOSANGDODGE_SCRIPT: the caster struck three times by the watcher in H, the first kept from it and
the caster moved onto a sword at random, the others landing), MP_ONLY=isosangvolley its H basic attack (2.95.0,
MpClient.ISOSANGVOLLEY_SCRIPT: on the sword, six swords of 2 at the watcher 20 blocks off in the crosshair, again too soon
refused, again 3 s on, 2.95.2: the watcher moved on out of the reach (the formation and 3 blocks more) and not picked,
the swords at the reach's edge, then onto the ground with no one in the crosshair), MP_ONLY=isosangt its T (2.96.0,
MpClient.ISOSANGT_SCRIPT: on the sword, a dash through the watcher 20 blocks off in the crosshair, six swords of 2 and three
slashes of 4 on it, a flight on to the formation's sword farthest from it; 2.96.4: again 3 ticks in refused, 9 ticks in kept
and gone 14 ticks in from the far sword, a third so, each landing all ten blows; then onto the ground with no one in the
crosshair), MP_ONLY=isosangy its Y (2.97.0, MpClient.ISOSANGY_SCRIPT: on the sword, six swords on the watcher 20 blocks
off in the crosshair as it walks, their ring following it until set, thirteen blows of 40 in all, the watcher caged
(incapacitated, 80% slower) from the first to 1.55 s; again under way and again too soon refused), MP_ONLY=isosanghh its
H again (2.101.0, MpClient.ISOSANGHH_SCRIPT: H with the watcher out of the formation, then H once more on the sword with
the watcher 11/3 blocks off in its band: the formation over at once, its swords pulled from where they stood, the caster up her leap and down where she
stood (as drawn and as the server holds her; 2.101.1: round her lap of the sky on her sword, drawn level against its
plan on each client, and its 28 swords), five blows of 12, the recording each client plays, her H ready again only
once its swords are gone), MP_ONLY=xcharaq X Chara's Q, the hack (2.115.0, MpClient.XCHARAQ_SCRIPT: on, off, a double tap heard once, the combo
with it on, off by itself with another stand and when slain, on again on the new body), MP_ONLY=xcharacut its E with the hack off, the three cuts (2.129.0, MpClient.XCHARACUT_SCRIPT: the restore with the hack on; cut where they stand with the watcher right ahead, 8 a cut and the third throwing; refused through its 12 s; a way forward first with the watcher further off, drawn by both clients; taken on a guard; all the way with no one near; stopped short when the caster is struck before the first cut), MP_ONLY=xcharaslam its G with the hack off, the slam (2.128.0, MpClient.XCHARASLAM_SCRIPT: no slam with the hack on; the rise drawn by both
clients while the caster stands where they began, where it comes down told by the caster's screen, the landing 68 ticks in, 50 to whoever is within
3.5 blocks through a guard too and 9 ticks unable to act, 60 s from its end; struck in the crouch it stops short, in the air nothing harms them; aimed
out of the circle it comes down on its edge), MP_ONLY=xcharay its Y with the hack on, the pull (2.127.0, MpClient.XCHARAY_SCRIPT: not heard with the hack off, pulled 7 ticks in to 1.5 blocks before the caster from 8, 10, 3 and 1 blocks, refused in its cooldown, blocked by a guard, called off when struck), MP_ONLY=xcharat its T with the hack on, the weakening, and the restore back on E (2.126.0, MpClient.XCHARAT_SCRIPT: blows compared before, during and after it, refused in its cooldown, blocked by a guard, called off when struck, anew on one weakened, over with their death), MP_ONLY=xcharae its E with the hack on and the hack's G (2.119.0, MpClient.XCHARAE_SCRIPT; 2.125.0: the cleave on H and, at its end, the double, R with the hack on: E and G not heard with the hack off, 65 healed
as the press lands, refused in its cooldown, stopped short when struck before the press, G blocked by a guard with the hack on; 2.124.0: then R with the hack off, the jab: 13 off the walking watcher 14 ticks in, held 160 ticks without moving, its X on its face on both clients, refused in its cooldown, landing through a guard, stopped short when struck before its blow), MP_ONLY=xcharaplain its plain swings (2.117.0,
MpClient.XCHARAPLAIN_SCRIPT: the hack off: 6, 6, 6, 10, thrown by the lunge only; its guard up; the hack turned on and
off between the hits of a combo: the next hit is the other kind's), MP_ONLY=xcharam1 X Chara's basic attack (2.114.0, MpClient.XCHARAM1_SCRIPT: the whole combo into
the watcher (6, 3 + 3, 2 + 2 + 2, 4 + 6; thrown by the last blow only), its guard up, the combo broken and its rest, a hit
stopped short as the caster is struck, the watcher 6 blocks ahead reached by the thrusts' knives of light only),
MP_ONLY=isosanggauge its H gauge (2.113.0, MpClient.ISOSANGGAUGE_SCRIPT: H refused with it
empty, filled one a foe struck by the combo and the empowered combo, H with the watcher in the formation (70% slower for
3 s from its first blow, 50% while in it, the slow gone out of it), T three times (a fifth each: the formation's end
sooner, told to the watcher's client too), the formation ending as it runs dry; H again emptying it at once; since
2.113.0 every check that uses H fills it first, GAUGE), MP_ONLY=isosangg its G (2.83.0,
MpClient.ISOSANGG_SCRIPT: G with the watcher 30 blocks ahead (2.87.1: 21 past where its sword strikes, in the blast's
62 x 47 blocks), struck 50 as its sword strikes; the caster's cutscene in its
own space, the watcher's world left as it is with the landing drawn; G and the attack refused while it holds, G refused
once it is over), MP_ONLY=fresh only the long cooldowns begun in
full whatever they were (2.74.3, MpClient.FRESH_SCRIPT: joining again with Atomic never used, back to life with Star
Platinum before and after its awakening, the stand arrow and the gacha giving a stand; another stand's forgotten; the
caster's client drops the world's time packets for 30 ticks after joining again, mixin/MpLateTimeMixin, so its cooldown
list must wait for the server's clock).
Results: OUT_DIR/{server.txt, caster/log.txt, watcher/log.txt, caster/*.png, watcher/*.png, logs/}.
"""
from pathlib import Path
import glob, json, os, shutil, subprocess, sys, time, zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
out = Path(sys.argv[1]).resolve()
props = dict(l.split('=', 1) for l in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
mod = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else ROOT / f"build/libs/botand-{props['mod_version']}.jar"
M2 = Path.home() / '.gradle/caches/modules-2/files-2.1'
LOOM = Path.home() / '.gradle/caches/fabric-loom'
MC = props['minecraft_version']
env = os.environ.copy(); env['JAVA_TOOL_OPTIONS'] = ''

def one(pattern):
    found = sorted(glob.glob(str(M2 / pattern)))
    if not found: sys.exit(f'missing from the Gradle cache: {pattern}')
    return found[-1]

shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
work = out / 'work'; work.mkdir()

# 1. The bot mod, compiled against the dev (named) classpath and remapped to intermediary like a release jar.
cp_file = ROOT / 'build/test-classpath.txt'
if not cp_file.exists():
    subprocess.run(['./gradlew', '--offline', '-q', 'writeCombatClasspath'], cwd=ROOT, check=True)
dev_cp = cp_file.read_text().strip()
subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', dev_cp, '-d', str(work / 'classes'),
                str(HERE / 'MpServer.java'), str(HERE / 'MpClient.java'), str(HERE / 'mixin/MpLateTimeMixin.java')], check=True, env=env)
named = work / 'botand-mp-named.jar'
with zipfile.ZipFile(named, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_mp', 'version': '1.0.0', 'environment': '*',
        'entrypoints': {'main': ['dev.sangwoo.botandmp.MpServer'], 'client': ['dev.sangwoo.botandmp.MpClient']},
        'mixins': ['botand_mp.mixins.json'], 'depends': {'celestialfall': '*'}}))
    # (2.74.3: the world's time held back for a moment after joining again, MP_ONLY=fresh; intermediary names, no refmap)
    z.writestr('botand_mp.mixins.json', json.dumps({'required': True, 'package': 'dev.sangwoo.botandmp.mixin', 'compatibilityLevel': 'JAVA_17',
        'client': ['MpLateTimeMixin'], 'injectors': {'defaultRequire': 1}}))
    for p in (work / 'classes').rglob('*.class'):
        z.write(p, str(p.relative_to(work / 'classes')))
asm = [one(f'org.ow2.asm/{a}/9.*/*/{a}-9.*[0-9].jar') for a in ('asm', 'asm-analysis', 'asm-commons', 'asm-tree', 'asm-util')]
remapper = one('net.fabricmc/tiny-remapper/*/*/tiny-remapper-*[0-9].jar')
yarn = next(LOOM.glob(f'{MC}/net.fabricmc.yarn.*/mappings.tiny'))
remap_cp = [e for e in dev_cp.split(':') if e.endswith('.jar') or e.endswith('/classes/java/main')]
bot = work / 'botand-mp.jar'
subprocess.run(['java', '-cp', ':'.join([remapper] + asm), 'net.fabricmc.tinyremapper.Main', str(named), str(bot), str(yarn), 'named', 'intermediary'] + remap_cp,
               check=True, env=env, stdout=subprocess.DEVNULL)

# 2. Production launch classpath: Minecraft's libraries, the loader with its mixin/asm, and intermediary mappings.
inter = work / 'intermediary.jar'
with zipfile.ZipFile(inter, 'w') as z:
    z.write(LOOM / MC / 'intermediary-v2.tiny', 'mappings/mappings.tiny')
info = json.loads((LOOM / MC / 'minecraft-info.json').read_text())
libs = []
for lib in info['libraries']:
    art = lib.get('downloads', {}).get('artifact')
    if not art: continue
    group, name, version = lib['name'].split(':')[:3]
    classifier = lib['name'].split(':')[3] if lib['name'].count(':') > 2 else None
    if classifier and classifier not in ('natives-linux', 'linux-x86_64'): continue
    jar = f'{name}-{version}' + (f'-{classifier}' if classifier else '') + '.jar'
    found = glob.glob(str(M2 / group / name / version / '*' / jar))
    if found: libs.append(found[0])
    elif 'macos' not in lib['name'] and 'windows' not in lib['name']: print('library not cached:', lib['name'])
loader = [one(f"net.fabricmc/fabric-loader/{props['loader_version']}/*/fabric-loader-{props['loader_version']}.jar"),
          one('net.fabricmc/sponge-mixin/*/*/sponge-mixin-*[0-9].jar')] + asm
cp = ':'.join(loader + [str(inter)] + libs)
fabric_api = one(f"net.fabricmc.fabric-api/fabric-api/{props['fabric_version']}/*/fabric-api-{props['fabric_version']}.jar")
# The pinned library jars the mod requires (Player Animator, GeckoLib, Immersive Cinematics, Veil), exactly as released.
deps = [ROOT / 'libs' / d['filename'] for d in json.loads((ROOT / 'DEPENDENCIES.lock.json').read_text())]
for jar in deps:
    if not jar.exists(): sys.exit(f'missing pinned library: {jar}')

def game_dir(name):
    d = out / name; (d / 'mods').mkdir(parents=True)
    for jar in (mod, fabric_api, bot, *deps): shutil.copy(jar, d / 'mods')
    return d

server = game_dir('server')
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('\n'.join([
    'online-mode=false', 'enforce-secure-profile=false', 'level-type=minecraft\\:flat', 'generate-structures=false', 'spawn-protection=0',
    'pvp=true', 'difficulty=easy', 'spawn-monsters=false', 'spawn-animals=false', 'spawn-npcs=false', 'view-distance=5', 'simulation-distance=5',
    'server-port=25565', 'max-tick-time=-1', 'sync-chunk-writes=false', 'motd=botand mp test']) + '\n')
logs = out / 'logs'; logs.mkdir()
procs, displays = [], []
try:
    srv_log = (logs / 'server.out').open('w')
    only = [f"-Dbotand.mp.only={os.environ['MP_ONLY']}"] if os.environ.get('MP_ONLY') else []
    srv = subprocess.Popen(['java', '-Xmx2G', '-Dbotand.mp.server=true', f'-Dbotand.mp.out={out}', *only,
                            f'-Dfabric.gameJarPath={LOOM / MC / "minecraft-extracted_server.jar"}', '-cp', cp,
                            'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui'],
                           cwd=server, env=env, stdin=subprocess.PIPE, stdout=srv_log, stderr=subprocess.STDOUT, text=True)
    procs.append(srv)
    start = time.time()
    while 'Done (' not in (logs / 'server.out').read_text(errors='replace'):
        if srv.poll() is not None or time.time() - start > 600: sys.exit('server did not start, see ' + str(logs / 'server.out'))
        time.sleep(2)
    print('server up after', round(time.time() - start), 's', flush=True)
    clients = []
    for role, name in (('watcher', 'Watcher'), ('caster', 'Caster')):
        d = game_dir(role)
        (d / 'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\njoinedFirstServer:true\nskipMultiplayerWarning:true\n'
                                       'soundCategory_master:1.0\nnarrator:0\ntutorialStep:none\nrenderDistance:5\nmaxFps:30\nlang:ko_kr\n')
        display = next(i for i in range(151, 199) if not Path(f'/tmp/.X{i}-lock').exists() and i not in displays)
        displays.append(display)
        procs.append(subprocess.Popen(['Xvfb', f':{display}', '-screen', '0', '854x480x24', '-nolisten', 'tcp'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        time.sleep(2)
        cenv = env.copy(); cenv.update({'DISPLAY': f':{display}', 'LIBGL_ALWAYS_SOFTWARE': 'true', 'GALLIUM_DRIVER': 'llvmpipe', 'ALSOFT_DRIVERS': 'null'})
        c = subprocess.Popen(['java', '-Xmx2G', f'-Dbotand.mp.role={role}', f'-Dbotand.mp.out={out}', *only,
                              f'-Dfabric.gameJarPath={LOOM / MC / "minecraft-client.jar"}', '-cp', cp,
                              'net.fabricmc.loader.impl.launch.knot.KnotClient', '--username', name, '--version', MC, '--accessToken', '0',
                              '--gameDir', str(d), '--assetsDir', str(LOOM / 'assets'), '--assetIndex', f"{MC}-{info['assetIndex']['id']}",
                              '--width', '854', '--height', '480'],
                             cwd=d, env=cenv, stdout=(logs / f'{role}.out').open('w'), stderr=subprocess.STDOUT)
        procs.append(c); clients.append((role, c))
        time.sleep(20)
    deadline = time.time() + 2700
    while any(c.poll() is None for _, c in clients) and time.time() < deadline:
        time.sleep(5)
    print(json.dumps({role: c.poll() for role, c in clients}), flush=True)
    srv.stdin.write('stop\n'); srv.stdin.flush()
    srv.wait(timeout=120)
    # MP_RESTART=1 (2.69.0): the server started once more on the same world, nobody joining, and stopped: a boss in play
    # as it stopped comes back where it was (server.txt gets its line again), and nothing fails on the way
    if os.environ.get('MP_RESTART'):
        srv_log2 = (logs / 'server-restart.out').open('w')
        srv = subprocess.Popen(['java', '-Xmx2G', '-Dbotand.mp.server=true', f'-Dbotand.mp.out={out}', *only,
                                f'-Dfabric.gameJarPath={LOOM / MC / "minecraft-extracted_server.jar"}', '-cp', cp,
                                'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui'],
                               cwd=server, env=env, stdin=subprocess.PIPE, stdout=srv_log2, stderr=subprocess.STDOUT, text=True)
        procs.append(srv)
        start = time.time()
        while 'Done (' not in (logs / 'server-restart.out').read_text(errors='replace'):
            if srv.poll() is not None or time.time() - start > 600: sys.exit('server did not start again, see ' + str(logs / 'server-restart.out'))
            time.sleep(2)
        print('server up again after', round(time.time() - start), 's', flush=True)
        time.sleep(15)
        srv.stdin.write('stop\n'); srv.stdin.flush()
        srv.wait(timeout=120)
finally:
    for p in procs:
        if p.poll() is None: p.terminate()
    for p in procs:
        try: p.wait(timeout=20)
        except Exception: p.kill()
    for name in ('server', 'watcher', 'caster'):
        d = out / name
        for f in [d / 'logs/latest.log'] + list((d / 'crash-reports').glob('*.txt')):
            if f.exists(): shutil.copy(f, logs / f'{name}-{f.name}')
