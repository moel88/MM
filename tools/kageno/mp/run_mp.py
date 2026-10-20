"""Multiplayer smoke test of the release jar in a production Fabric setup (no dev mappings):
a dedicated server plus two clients ("Caster" plays every Kageno key, "Watcher" stands in front),
each client under its own Xvfb display. Everything comes from the offline Gradle/Loom caches.
usage: python3 tools/kageno/mp/run_mp.py OUT_DIR [MOD_JAR]
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
                str(HERE / 'MpServer.java'), str(HERE / 'MpClient.java')], check=True, env=env)
named = work / 'botand-mp-named.jar'
with zipfile.ZipFile(named, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_mp', 'version': '1.0.0', 'environment': '*',
        'entrypoints': {'main': ['dev.sangwoo.botandmp.MpServer'], 'client': ['dev.sangwoo.botandmp.MpClient']},
        'depends': {'celestialfall': '*'}}))
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
    srv = subprocess.Popen(['java', '-Xmx2G', '-Dbotand.mp.server=true', f'-Dbotand.mp.out={out}',
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
                                       'soundCategory_master:0.0\nnarrator:0\ntutorialStep:none\nrenderDistance:5\nmaxFps:30\nlang:ko_kr\n')
        display = next(i for i in range(151, 199) if not Path(f'/tmp/.X{i}-lock').exists() and i not in displays)
        displays.append(display)
        procs.append(subprocess.Popen(['Xvfb', f':{display}', '-screen', '0', '854x480x24', '-nolisten', 'tcp'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        time.sleep(2)
        cenv = env.copy(); cenv.update({'DISPLAY': f':{display}', 'LIBGL_ALWAYS_SOFTWARE': 'true', 'GALLIUM_DRIVER': 'llvmpipe', 'ALSOFT_DRIVERS': 'null'})
        c = subprocess.Popen(['java', '-Xmx2G', f'-Dbotand.mp.role={role}', f'-Dbotand.mp.out={out}',
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
