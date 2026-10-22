"""Builds Botand's hourglass island (map v3.0) into a fresh world on a real Minecraft 1.20.1 dedicated server and packages it.

usage: python3 tools/map/town/run_town.py OUT_DIR [SEED]
Results: OUT_DIR/server/Botand_Hourglass_1.20.1 (the world), OUT_DIR/Botand-Hourglass-1.20.1-vX.zip (for the saves folder),
OUT_DIR/town_preview.png (a top-down picture in map colours), OUT_DIR/build.log.
The old city's pieces of the first map come from tools/map/town/pieces/azure_city.txt.gz (lift_azure.py).

The builder (Town.java and friends) is compiled against the dev (named) classpath and remapped to intermediary like a
release mod; the server runs from the offline Gradle/Loom caches with Fabric API and the Botand jar (build/libs, or
TOWN_MOD) with its pinned libraries, since the town uses the mod's own town blocks.
The world starts as a flat ocean (the old Azure Garden map's layers); the builder raises the town out of it.
"""
from pathlib import Path
import glob, json, os, shutil, subprocess, sys, time, zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
out = Path(sys.argv[1]).resolve()
seed = sys.argv[2] if len(sys.argv) > 2 else '20261020'
props = dict(l.split('=', 1) for l in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in l and not l.startswith('#'))
M2 = Path.home() / '.gradle/caches/modules-2/files-2.1'
LOOM = Path.home() / '.gradle/caches/fabric-loom'
MC = props['minecraft_version']
WORLD = 'Botand_Hourglass_1.20.1'
VERSION = os.environ.get('TOWN_VERSION', '3.0')
env = os.environ.copy(); env['JAVA_TOOL_OPTIONS'] = ''

def one(pattern):
    found = sorted(glob.glob(str(M2 / pattern)))
    if not found: sys.exit(f'missing from the Gradle cache: {pattern}')
    return found[-1]

shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
work = out / 'work'; work.mkdir()

# 1. The builder, compiled against the named classpath and remapped to intermediary.
cp_file = ROOT / 'build/test-classpath.txt'
if not cp_file.exists():
    subprocess.run(['./gradlew', '--offline', '-q', 'writeCombatClasspath'], cwd=ROOT, check=True)
dev_cp = cp_file.read_text().strip()
sources = sorted(str(p) for p in HERE.glob('*.java'))
if subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-nowarn', '-cp', dev_cp, '-d', str(work / 'classes')] + sources, env=env).returncode:
    sys.exit('the builder does not compile')
named = work / 'botand-town-named.jar'
with zipfile.ZipFile(named, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_town', 'version': '1.0.0', 'environment': '*',
        'entrypoints': {'main': ['dev.sangwoo.botandtown.Town']}, 'depends': {'celestialfall': '*'}}))
    for p in (work / 'classes').rglob('*.class'):
        z.write(p, str(p.relative_to(work / 'classes')))
asm = [one(f'org.ow2.asm/{a}/9.*/*/{a}-9.*[0-9].jar') for a in ('asm', 'asm-analysis', 'asm-commons', 'asm-tree', 'asm-util')]
remapper = one('net.fabricmc/tiny-remapper/*/*/tiny-remapper-*[0-9].jar')
yarn = next(LOOM.glob(f'{MC}/net.fabricmc.yarn.*/mappings.tiny'))
remap_cp = [e for e in dev_cp.split(':') if e.endswith('.jar') or e.endswith('/classes/java/main')]
bot = work / 'botand-town.jar'
subprocess.run(['java', '-cp', ':'.join([remapper] + asm), 'net.fabricmc.tinyremapper.Main', str(named), str(bot), str(yarn), 'named', 'intermediary'] + remap_cp,
               check=True, env=env, stdout=subprocess.DEVNULL)

# 2. The production server classpath (as tools/kageno/mp/run_mp.py).
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

# the town is built of Botand's own town blocks too, so the server runs the mod (and the libraries it needs) as released
mod = Path(os.environ['TOWN_MOD']) if os.environ.get('TOWN_MOD') else ROOT / f"build/libs/botand-{props['mod_version']}.jar"
if not mod.exists(): sys.exit(f'build the mod first (bash gradlew build --offline): {mod}')
deps = [ROOT / 'libs' / d['filename'] for d in json.loads((ROOT / 'DEPENDENCIES.lock.json').read_text())]
server = out / 'server'; (server / 'mods').mkdir(parents=True)
for jar in (fabric_api, bot, mod, *deps): shutil.copy(jar, server / 'mods')
(server / 'eula.txt').write_text('eula=true\n')
# a flat ocean (water to y 62 over a sand and gravel floor at 50), as the Azure Garden map
layers = [{'block': 'minecraft:bedrock', 'height': 1}, {'block': 'minecraft:stone', 'height': 110}, {'block': 'minecraft:gravel', 'height': 2},
          {'block': 'minecraft:sand', 'height': 2}, {'block': 'minecraft:water', 'height': 12}]
gen = json.dumps({'layers': layers, 'biome': 'minecraft:ocean', 'lakes': False, 'features': False, 'structure_overrides': []}, separators=(',', ':'))
(server / 'server.properties').write_text('\n'.join([
    'online-mode=false', 'level-type=minecraft\\:flat', 'generator-settings=' + gen.replace(':', '\\:'), f'level-name={WORLD}', f'level-seed={seed}',
    'generate-structures=false', 'spawn-protection=0', 'gamemode=survival', 'difficulty=normal', 'spawn-monsters=false', 'spawn-animals=false',
    'spawn-npcs=false', 'view-distance=8', 'simulation-distance=6', 'server-port=25599', 'max-tick-time=-1', 'sync-chunk-writes=false',
    'motd=botand town builder']) + '\n')
# the map's datapack goes in before the world is created, so it is enabled from the start
pack = server / WORLD / 'datapacks' / 'botand_hourglass'
(pack / 'data' / 'botand').mkdir(parents=True)
(pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15, 'description': 'Botand: 모래시계 섬 (보탠드 전용 맵)'}}, ensure_ascii=False, indent=2))

log = (out / 'server.out').open('w')
start = time.time()
srv = subprocess.Popen(['java', '-Xmx6G', '-Xms2G', '-Dbotand.town.build=true', f'-Dbotand.town.seed={seed}', f'-Dbotand.town.pieces={HERE / "pieces" / "azure_city.txt.gz"}',
                        f'-Dfabric.gameJarPath={LOOM / MC / "minecraft-extracted_server.jar"}', '-cp', cp,
                        'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui'],
                       cwd=server, env=env, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
try:
    srv.wait(timeout=int(os.environ.get('TOWN_TIMEOUT', '5400')))
except subprocess.TimeoutExpired:
    srv.kill(); sys.exit('builder timed out, see ' + str(out / 'server.out'))
text = (out / 'server.out').read_text(errors='replace')
lines = [l for l in text.splitlines() if '[town]' in l]
(out / 'build.log').write_text('\n'.join(lines) + '\n')
print('\n'.join(lines[-30:]))
if (server / 'town_error.txt').exists() or not any('[town] saved' in l for l in lines):
    sys.exit('build failed after %d s, see %s' % (time.time() - start, out / 'server.out'))
for name in ('town_preview.png', 'town_lots.png', 'town_owner.png', 'town_build.log'):
    if (server / WORLD / name).exists(): shutil.move(str(server / WORLD / name), out / name)
for junk in ('session.lock',):
    (server / WORLD / junk).unlink(missing_ok=True)
# the name players see in the world list, adventure mode so the town is not broken by hand in single player
sys.path.insert(0, str(HERE))
import level_dat
level_dat.patch(server / WORLD, os.environ.get('TOWN_NAME', 'BOTAND | 보탠드 모래시계 섬'), 2)
# the world folder zipped for the saves folder (or a server's world folder)
pack = out / f'Botand-Hourglass-1.20.1-v{VERSION}.zip'
with zipfile.ZipFile(pack, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in sorted((server / WORLD).rglob('*')):
        if f.is_file(): z.write(f, str(f.relative_to(server)))
print(json.dumps({'seconds': round(time.time() - start), 'world': str(server / WORLD), 'zip': str(pack), 'mb': round(pack.stat().st_size / 1e6, 1)}))
