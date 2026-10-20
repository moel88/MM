"""Films a built map in the real client under Xvfb (software rendering) and saves the frames.

usage: python3 tools/map/shots/run_map_shots.py OUT_DIR WORLD_DIR "SHOT,SHOT,..." [WIDTHxHEIGHT] [SLOW]
A shot is NAME;time=T;at=x/y/z/yaw/pitch[;fov=F] (a still, OUT_DIR/NAME.png) or
NAME;time=T;path=x/y/z/yaw/pitch|...;secs=S[;fov=F] (a flight, OUT_DIR/NAME/00000.png... at 60 frames a second of its
own clock). The world is copied into the dev client's saves and opened there with the mod loaded (MapShots.java);
SLOW slows the game's clock for flights so water and leaves move at their own pace in the 60 fps film.
MAP_MODS (jar paths joined by ':') adds mods to the client, such as Sodium and Iris; MAP_SHADER names a shader pack
zip for Iris to use (copied into the dev client's shaderpacks for the run and its Iris settings put back afterwards),
MAP_SHADER_OPTIONS the pack's options ("SUN_ANGLE=-40;...").
"""
from pathlib import Path
import json, os, shutil, subprocess, sys, time, zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
out = Path(sys.argv[1]).resolve()
world = Path(sys.argv[2]).resolve()
shots = sys.argv[3]
size = sys.argv[4] if len(sys.argv) > 4 else '1280x720'
slow = sys.argv[5] if len(sys.argv) > 5 else '20'
w, h = size.split('x')
build = ROOT / 'build/map-shots'
shutil.rmtree(build, ignore_errors=True); build.mkdir(parents=True)
cp = (ROOT / 'build/test-classpath.txt').read_text().strip()
env0 = os.environ.copy(); env0['JAVA_TOOL_OPTIONS'] = ''
if subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-nowarn', '-cp', cp, '-d', str(build / 'classes'),
                   str(HERE / 'MapShots.java'), str(HERE / 'MapShotsMixin.java')], env=env0).returncode:
    sys.exit('the filming tool does not compile')
jar = build / 'map-shots.jar'
with zipfile.ZipFile(jar, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_map_shots', 'version': '1.0.0', 'environment': 'client',
        'entrypoints': {'client': ['dev.sangwoo.botandmap.MapShots']}, 'mixins': ['botand-map-shots.mixins.json']}))
    z.writestr('botand-map-shots.mixins.json', json.dumps({'required': True, 'package': 'dev.sangwoo.botandmap.mixin', 'compatibilityLevel': 'JAVA_17',
        'client': ['MapShotsMixin'], 'injectors': {'defaultRequire': 1}}))
    for p in (build / 'classes').rglob('*.class'):
        z.write(p, str(p.relative_to(build / 'classes')))
shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
# the world goes into the dev client's saves under its own folder name, replacing an older copy
saves = ROOT / 'run/saves'; saves.mkdir(parents=True, exist_ok=True)
target = saves / ('MapShots_' + world.name)
shutil.rmtree(target, ignore_errors=True); shutil.copytree(world, target)
(target / 'session.lock').unlink(missing_ok=True)
mods = ':'.join([str(jar)] + [m for m in os.environ.get('MAP_MODS', '').split(':') if m])
# a shader pack for Iris: copied in for this run, Iris's own settings kept aside and put back afterwards
shader = os.environ.get('MAP_SHADER')
iris_props = ROOT / 'run/config/iris.properties'
iris_saved = iris_props.read_text() if iris_props.exists() else None
packs = ROOT / 'run/shaderpacks'
if shader:
    packs.mkdir(parents=True, exist_ok=True)
    shutil.copy(shader, packs / Path(shader).name)
    iris_props.parent.mkdir(parents=True, exist_ok=True)
    iris_props.write_text(f'enableShaders=true\nshaderPack={Path(shader).name}\ndisableUpdateMessage=true\n')
    # the pack's own options ("SUN_ANGLE=-40;..."), kept by Iris in a file beside the pack
    options = os.environ.get('MAP_SHADER_OPTIONS', '')
    (packs / (Path(shader).name + '.txt')).write_text(''.join(o.strip() + '\n' for o in options.split(';') if o.strip()))
init = build / 'map.gradle'
init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx6G", "-Xms2G", '
                f'"-Dbotand.map.out={out}", "-Dbotand.map.world={target.name}", "-Dbotand.map.list={shots}", "-Dbotand.map.slow={slow}", '
                f'"-Dbotand.map.view={os.environ.get("MAP_VIEW", "12")}", "-Dfabric.addMods={mods}"; '
                f'args "--username", "MapQA", "--width", "{w}", "--height", "{h}" }} }}')
env = os.environ.copy(); env['LIBGL_ALWAYS_SOFTWARE'] = 'true'; env['ALSOFT_DRIVERS'] = 'null'; env['GALLIUM_DRIVER'] = 'llvmpipe'
display = next(i for i in range(151, 190) if not Path(f'/tmp/.X{i}-lock').exists())
env['DISPLAY'] = f':{display}'
x = subprocess.Popen(['Xvfb', f':{display}', '-screen', '0', f'{w}x{h}x24', '-nolisten', 'tcp'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    time.sleep(2)
    start = time.time()
    with (out / 'client.log').open('w') as log:
        r = subprocess.run(['./gradlew', '--no-daemon', '--max-workers=2', 'runClient', '-I', str(init)], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                           timeout=int(os.environ.get('MAP_TIMEOUT', '7200')))
    print(json.dumps({'exit': r.returncode, 'seconds': round(time.time() - start),
                      'error': (out / 'error.txt').read_text() if (out / 'error.txt').exists() else None,
                      'log': (out / 'log.txt').read_text() if (out / 'log.txt').exists() else None}))
finally:
    x.terminate(); x.wait(timeout=10)
    if shader:
        (packs / Path(shader).name).unlink(missing_ok=True)
        (packs / (Path(shader).name + '.txt')).unlink(missing_ok=True)
        if iris_saved is None: iris_props.unlink(missing_ok=True)
        else: iris_props.write_text(iris_saved)
    shutil.rmtree(target, ignore_errors=True)
