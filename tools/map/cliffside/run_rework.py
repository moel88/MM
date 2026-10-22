"""Applies the cliffside rework to Botand Azure Garden v1.3 in a real Minecraft 1.20.1 server and packs map v1.4.

usage: python3 tools/map/cliffside/run_rework.py SRC_WORLD OUT_DIR

The plan (rework.py) goes into a throwaway data pack as setblock/fill and summon commands; a vanilla 1.20.1 dedicated
server (no mods, so nothing of Botand's runs on the world while it is built) loads a copy of the world, force-loads the
island, runs them, checks that no sand is left falling, removes every other entity, saves and stops. Then the data pack
goes, level.dat is the original one with only the world's name changed, and the server's own leftovers are dropped.
The game lights the changed chunks itself and connects fences, panes and stairs as it places them.
OUT_DIR gets the world folder, Botand-Azure-Cliffside-1.20.1-v1.4.zip, plan.json and server.log.
"""
import json, queue, random, re, shutil, subprocess, sys, threading, time, zipfile
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE))
from world import World
import rework, nbt

NAME = 'Botand_Azure_Cliffside_1.20.1'
LEVEL_NAME = 'BOMI STAND | Azure Garden · Cliffside'
ZIP = 'Botand-Azure-Cliffside-1.20.1-v1.4.zip'
SERVER_JAR = Path.home() / '.gradle/caches/fabric-loom/1.20.1/minecraft-server.jar'
PACK = 'cliffside_build'
PART = 20000

src = Path(sys.argv[1]).resolve()
out = Path(sys.argv[2]).resolve()
out.mkdir(parents=True, exist_ok=True)

# 1. the plan
w = World(src, 44, 200)
plan = rework.build(w)
cmds, summons = plan.commands(), plan.summons()
counts = {}
for s in plan.cells.values():
    k = s.split('[')[0]; counts[k] = counts.get(k, 0) + 1
(out / 'plan.json').write_text(json.dumps({'cells': len(plan.cells), 'refused': plan.refused, 'entities': len(summons),
                                           'commands': len(cmds), 'blocks': dict(sorted(counts.items(), key=lambda kv: -kv[1]))}, indent=1))
print(f'plan: {len(plan.cells)} cells in {len(cmds)} commands, {len(summons)} entities, {plan.refused} refused (protected)', flush=True)

# 2. a server folder holding a fresh copy of the world and the throwaway data pack
srv = ROOT / 'build/cliffside-server'
srv.mkdir(parents=True, exist_ok=True)
world = srv / NAME
shutil.rmtree(world, ignore_errors=True)
shutil.copytree(src, world)
(srv / 'eula.txt').write_text('eula=true\n')
props = {'level-name': NAME, 'online-mode': 'false', 'server-port': str(random.randint(26000, 29000)), 'enable-rcon': 'false',
         'spawn-protection': '0', 'max-tick-time': '-1', 'view-distance': '6', 'simulation-distance': '6', 'sync-chunk-writes': 'true',
         'spawn-monsters': 'false', 'spawn-animals': 'false', 'spawn-npcs': 'false', 'generate-structures': 'false',
         'function-permission-level': '4', 'enforce-secure-profile': 'false', 'max-players': '1', 'motd': 'cliffside build'}
(srv / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in props.items()))
pack = world / 'datapacks' / PACK
fn = pack / 'data' / PACK / 'functions'
fn.mkdir(parents=True)
(pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15, 'description': 'cliffside build, removed after use'}}))
parts = [cmds[i:i + PART] for i in range(0, len(cmds), PART)]
for k, part in enumerate(parts):
    (fn / f'part_{k}.mcfunction').write_text('\n'.join(part) + '\n')
(fn / 'summons.mcfunction').write_text('\n'.join(summons) + '\n')

# 3. the server
log = open(out / 'server.log', 'w')
proc = subprocess.Popen(['java', '-Xmx4G', '-jar', str(SERVER_JAR), '--nogui'], cwd=srv, stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
lines = queue.Queue()
def pump():
    for line in proc.stdout:
        log.write(line); log.flush(); lines.put(line.rstrip('\n'))
    lines.put(None)
threading.Thread(target=pump, daemon=True).start()

def expect(pattern, timeout=600):
    rx = re.compile(pattern); end = time.time() + timeout
    while time.time() < end:
        try: line = lines.get(timeout=1)
        except queue.Empty: continue
        if line is None: sys.exit('the server stopped early; see server.log')
        if rx.search(line): return line
    sys.exit(f'timed out waiting for {pattern!r}; see server.log')

def send(cmd):
    proc.stdin.write(cmd + '\n'); proc.stdin.flush()

expect(r'Done \(', 900)
bad = [l for l in (out / 'server.log').read_text().splitlines() if 'Failed to load function' in l or 'Whilst parsing' in l]
if bad:
    send('stop'); proc.wait(timeout=600)
    sys.exit('the build pack does not load:\n' + '\n'.join(bad[:6]))
send('gamerule maxCommandChainLength 1000000')
expect(r'maxCommandChainLength')
# the island: chunks x -11..10, z -12..11, force-loaded in bands under the game's 256-chunk limit
for z0 in range(-12, 12, 6):
    send(f'forceload add -176 {z0 * 16} 175 {(z0 + 6) * 16 - 1}')
    expect(r'(Marking|Marked) .* chunks? .* force loaded|is already|Chunks? .* forceloaded|force loaded')
time.sleep(25)
for k in range(len(parts)):
    send(f'function {PACK}:part_{k}')
    line = expect(rf'Executed \d+ command.*from function .{PACK}:part_{k}', 1200)
    print(line.split(']: ')[-1], flush=True)
if summons:
    send(f'function {PACK}:summons')
    print(expect(rf'from function .{PACK}:summons', 600).split(']: ')[-1], flush=True)
time.sleep(8)  # sand settles, water finds its level, before anything is checked
send('execute if entity @e[type=minecraft:falling_block]')
print('falling sand:', expect(r'Test (passed|failed)').split(']: ')[-1], flush=True)
send('kill @e[type=!minecraft:item_frame,type=!minecraft:glow_item_frame,type=!minecraft:armor_stand]')
expect(r'Killed|No entity was found')
send('forceload remove all')
expect(r'Unmarked|No chunks|forceload|removed')
send('save-all flush')
expect(r'Saved the game', 600)
send('stop')
proc.wait(timeout=600)
log.close()

# 4. the world as shipped: no build pack, the original level.dat (renamed), none of the server's own files
shutil.rmtree(world / 'datapacks' / PACK)
root = nbt.load(src / 'level.dat')
data = root[2]['Data'][1]
data['LevelName'] = (nbt.STRING, LEVEL_NAME)
nbt.save(world / 'level.dat', root)
for leftover in ('session.lock', 'level.dat_old', 'data', 'DIM-1', 'DIM1', 'playerdata', 'stats', 'advancements'):
    q = world / leftover
    if q.is_dir(): shutil.rmtree(q)
    elif q.exists(): q.unlink()
dst = out / NAME
shutil.rmtree(dst, ignore_errors=True)
shutil.copytree(world, dst)
with zipfile.ZipFile(out / ZIP, 'w', zipfile.ZIP_DEFLATED) as z:
    for f in sorted(dst.rglob('*')):
        if f.is_file(): z.write(f, f.relative_to(out))
print('world:', dst, flush=True)
print('zip:', out / ZIP, round((out / ZIP).stat().st_size / 1e6, 1), 'MB', flush=True)
