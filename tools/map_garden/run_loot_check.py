"""Opens a fresh copy of the dedicated map in the real client and checks the eleven field boxes (LootCheck.java).
usage: python3 tools/map_garden/run_loot_check.py MAP_ZIP OUT_DIR
MAP_ZIP is Botand-Azure-Garden-1.20.1-v1.3.zip; the world is copied to run/saves and removed afterwards.
Results: OUT_DIR/loot-result.txt (or loot-error.txt), box-01.png ... box-11.png, box-01-restored.png, client.log.
"""
from pathlib import Path
import json, os, shutil, subprocess, sys, time, zipfile

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
map_zip, out = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
build = ROOT / 'build/loot-check'; shutil.rmtree(build, ignore_errors=True); (build / 'classes').mkdir(parents=True)
cp_file = ROOT / 'build/test-classpath.txt'
if not cp_file.exists():
    subprocess.run(['./gradlew', '--offline', '-q', 'writeCombatClasspath'], cwd=ROOT, check=True)
subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', cp_file.read_text().strip(), '-d', str(build / 'classes'), str(HERE / 'LootCheck.java')], check=True)
jar = build / 'loot-check.jar'
with zipfile.ZipFile(jar, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_loot_check', 'version': '1.0.0', 'environment': 'client',
                                              'entrypoints': {'client': ['dev.sangwoo.botandverify.LootCheck']}}))
    for p in (build / 'classes').rglob('*.class'):
        z.write(p, str(p.relative_to(build / 'classes')))
world_name = 'Botand_LootCheck_' + str(time.time_ns())
world = ROOT / 'run/saves' / world_name
prefix = 'Botand_Azure_Garden_1.20.1/'
with zipfile.ZipFile(map_zip) as zf:
    for name in zf.namelist():
        if name.startswith(prefix) and not name.endswith('/'):
            dest = world / name[len(prefix):]
            dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(zf.read(name))
init = build / 'loot.gradle'
init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx3G", '
                f'"-Dbotand.loot.proof={out}", "-Dbotand.loot.world={world_name}", "-Dfabric.addMods={jar}"; '
                'args "--username", "LootQA", "--width", "1280", "--height", "730" } }')
env = os.environ.copy(); env.update({'LIBGL_ALWAYS_SOFTWARE': 'true', 'ALSOFT_DRIVERS': 'null', 'GALLIUM_DRIVER': 'llvmpipe'})
display = next(i for i in range(151, 190) if not Path(f'/tmp/.X{i}-lock').exists())
env['DISPLAY'] = f':{display}'
x = subprocess.Popen(['Xvfb', f':{display}', '-screen', '0', '1280x730x24', '-nolisten', 'tcp'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    time.sleep(2)
    with (out / 'client.log').open('w') as log:
        r = subprocess.run(['./gradlew', '--no-daemon', '--max-workers=2', 'runClient', '-I', str(init)], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=1800)
    for name in ('loot-result.txt', 'loot-error.txt'):
        if (out / name).exists(): print(name); print((out / name).read_text())
    print(json.dumps({'exit': r.returncode}))
finally:
    x.terminate(); x.wait(timeout=10)
    shutil.rmtree(world, ignore_errors=True)
