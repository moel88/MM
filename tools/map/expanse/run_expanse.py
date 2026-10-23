"""Builds Botand Azure Garden v1.5 'Expanse' into a fresh world on a real Minecraft 1.20.1 dedicated server and packages it.

usage: python3 tools/map/expanse/run_expanse.py OUT_DIR
Results: OUT_DIR/server/Botand_Azure_Expanse_1.20.1 (the world), OUT_DIR/Botand-Azure-Expanse-1.20.1-v1.5.zip (for the
saves folder), OUT_DIR/plan/preview.png (the plan from above in block colours, not a game screen), OUT_DIR/build.log.

plan.py decides every block (Azure Garden's island 2.5 times over, grass inside, the grey stone wall round it); the
builder (Expanse.java) is compiled against the dev (named) classpath and remapped to intermediary like a release mod and
writes the plan into the world as the town builder does (tools/map/town). The server runs from the offline Gradle/Loom
caches with Fabric API only: the map is all vanilla blocks. The world starts as Azure Garden's flat ocean.
"""
from pathlib import Path
import glob, json, os, shutil, subprocess, sys, time, zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
out = Path(sys.argv[1]).resolve()
props = dict(l.split('=', 1) for l in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
M2 = Path.home() / '.gradle/caches/modules-2/files-2.1'
LOOM = Path.home() / '.gradle/caches/fabric-loom'
MC = props['minecraft_version']
WORLD = 'Botand_Azure_Expanse_1.20.1'
VERSION = '1.5'
NAME = 'BOMI STAND | Azure Garden · Expanse'
env = os.environ.copy(); env['JAVA_TOOL_OPTIONS'] = ''

def one(pattern):
    found = sorted(glob.glob(str(M2 / pattern)))
    if not found: sys.exit(f'missing from the Gradle cache: {pattern}')
    return found[-1]

shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
work = out / 'work'; work.mkdir()

# 0. The plan.
sys.path.insert(0, str(HERE))
import plan as planner
plan = planner.main(out / 'plan')

# 1. The builder, compiled against the named classpath and remapped to intermediary.
cp_file = ROOT / 'build/test-classpath.txt'
if not cp_file.exists():
    subprocess.run(['./gradlew', '--offline', '-q', 'writeCombatClasspath'], cwd=ROOT, check=True)
dev_cp = cp_file.read_text().strip()
if subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-nowarn', '-cp', dev_cp, '-d', str(work / 'classes'),
                   str(HERE / 'Expanse.java')], env=env).returncode:
    sys.exit('the builder does not compile')
named = work / 'botand-expanse-named.jar'
with zipfile.ZipFile(named, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_expanse', 'version': '1.0.0', 'environment': '*',
        'entrypoints': {'main': ['dev.sangwoo.botandexpanse.Expanse']}}))
    for p in (work / 'classes').rglob('*.class'):
        z.write(p, str(p.relative_to(work / 'classes')))
asm = [one(f'org.ow2.asm/{a}/9.*/*/{a}-9.*[0-9].jar') for a in ('asm', 'asm-analysis', 'asm-commons', 'asm-tree', 'asm-util')]
remapper = one('net.fabricmc/tiny-remapper/*/*/tiny-remapper-*[0-9].jar')
yarn = next(LOOM.glob(f'{MC}/net.fabricmc.yarn.*/mappings.tiny'))
remap_cp = [e for e in dev_cp.split(':') if e.endswith('.jar') or e.endswith('/classes/java/main')]
bot = work / 'botand-expanse.jar'
subprocess.run(['java', '-cp', ':'.join([remapper] + asm), 'net.fabricmc.tinyremapper.Main', str(named), str(bot), str(yarn), 'named', 'intermediary'] + remap_cp,
               check=True, env=env, stdout=subprocess.DEVNULL)

# 2. The production server classpath (as tools/map/town/run_town.py).
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
loader = [one(f"net.fabricmc/fabric-loader/{props['loader_version']}/*/fabric-loader-{props['loader_version']}.jar"),
          one('net.fabricmc/sponge-mixin/*/*/sponge-mixin-*[0-9].jar')] + asm
cp = ':'.join(loader + [str(inter)] + libs)
fabric_api = one(f"net.fabricmc.fabric-api/fabric-api/{props['fabric_version']}/*/fabric-api-{props['fabric_version']}.jar")

server = out / 'server'; (server / 'mods').mkdir(parents=True)
for jar in (fabric_api, bot): shutil.copy(jar, server / 'mods')
(server / 'eula.txt').write_text('eula=true\n')
# Azure Garden's flat ocean: water 49..62 over sand at 47..48, gravel 43..46 and stone; deep ocean beyond the map
layers = [{'block': 'minecraft:bedrock', 'height': 1}, {'block': 'minecraft:stone', 'height': 106}, {'block': 'minecraft:gravel', 'height': 4},
          {'block': 'minecraft:sand', 'height': 2}, {'block': 'minecraft:water', 'height': 14}]
gen = json.dumps({'layers': layers, 'biome': 'minecraft:deep_ocean', 'lakes': False, 'features': False, 'structure_overrides': []}, separators=(',', ':'))
(server / 'server.properties').write_text('\n'.join([
    'online-mode=false', 'level-type=minecraft\\:flat', 'generator-settings=' + gen.replace(':', '\\:'), f'level-name={WORLD}', 'level-seed=16591012',
    'generate-structures=false', 'spawn-protection=0', 'gamemode=creative', 'difficulty=normal', 'spawn-monsters=false', 'spawn-animals=false',
    'spawn-npcs=false', 'view-distance=8', 'simulation-distance=6', 'server-port=25598', 'max-tick-time=-1', 'sync-chunk-writes=false',
    'motd=botand expanse builder']) + '\n')
# the map's data pack (its definition and teleports) goes in before the world is created, so it is enabled from the start
pack = server / WORLD / 'datapacks' / 'botand_expanse'
shutil.copytree(out / 'plan' / 'datapack', pack)

log = (out / 'server.out').open('w')
start = time.time()
srv = subprocess.Popen(['java', '-Xmx6G', '-Xms2G', f"-Dbotand.expanse.plan={out / 'plan'}",
                        f'-Dfabric.gameJarPath={LOOM / MC / "minecraft-extracted_server.jar"}', '-cp', cp,
                        'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui'],
                       cwd=server, env=env, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
try:
    srv.wait(timeout=int(os.environ.get('EXPANSE_TIMEOUT', '3600')))
except subprocess.TimeoutExpired:
    srv.kill(); sys.exit('builder timed out, see ' + str(out / 'server.out'))
text = (out / 'server.out').read_text(errors='replace')
lines = [l for l in text.splitlines() if '[expanse]' in l]
(out / 'build.log').write_text('\n'.join(lines) + '\n')
print('\n'.join(lines[-30:]))
if (server / 'expanse_error.txt').exists() or not any('[expanse] saved' in l for l in lines):
    sys.exit('build failed after %d s, see %s' % (time.time() - start, out / 'server.out'))
(server / WORLD / 'session.lock').unlink(missing_ok=True)
# past the plan's own region files the world holds only open sea the server generated round it, which the game
# generates again the same way when a player comes near: left out, so the world is the plan's four region files
keep = {(rx, rz) for rx in range(plan['x0'] >> 9, ((plan['x0'] + plan['w'] - 1) >> 9) + 1)
        for rz in range(plan['z0'] >> 9, ((plan['z0'] + plan['d'] - 1) >> 9) + 1)}
for folder in ('region', 'entities', 'poi'):
    for f in (server / WORLD / folder).glob('r.*.mca'):
        _, rx, rz, _ = f.name.split('.')
        if (int(rx), int(rz)) not in keep: f.unlink()
# the name players see in the world list; creative, as Azure Garden
sys.path.insert(0, str(HERE.parent / 'town'))
import level_dat
level_dat.patch(server / WORLD, NAME, 1)
# the world folder zipped for the saves folder (or a server's world folder)
zpath = out / f'Botand-Azure-Expanse-1.20.1-v{VERSION}.zip'
with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in sorted((server / WORLD).rglob('*')):
        if f.is_file(): z.write(f, str(f.relative_to(server)))
print(json.dumps({'seconds': round(time.time() - start), 'world': str(server / WORLD), 'zip': str(zpath), 'mb': round(zpath.stat().st_size / 1e6, 1),
                  'stats': plan['stats']}))
