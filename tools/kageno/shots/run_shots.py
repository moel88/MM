"""Render Kageno skills in the real client under Xvfb and save every frame.
usage: python3 tools/kageno/shots/run_shots.py OUT_DIR [SKILLS] [WIDTHxHEIGHT] [SLOW]
Frames go to OUT_DIR/NAME/ (a repeated skill name gets NAME.2, NAME.3, ...). SLOW (default 12) runs the game that many
times slower than real time so the software renderer saves more than 60 frames per second of the cast clock (film.py
makes 60 fps clips from them).
SKILLS is a comma list of entries "NAME;opt=value;...". Options: face, dist, pitch, camyaw, hits, faces, dfw, drt,
dummy=0, len, then=SKILL@age, view=CHUNKS, far=ex/ey/ez/tx/ty/tz[/fov] (fixed camera in blocks from the cast spot),
act=NAME:DIST:YAW:SPEED:START+... (running husks for G), track=NAME:MODE:DIST:UP:SIDE[:FOV] (camera following one) and
shake=1 (those cameras get the Atomic camera shake a player there would; G's frames logged to NAME/shake.csv),
hud=1 (the game's HUD shown: the cooldown list and the danger banner), cool=1 (real cooldowns). The entry "HUD" casts
only its then= steps, e.g. "HUD;hud=1;cool=1;len=300;then=E@5/T@80/RUSH@81". The entry "UI" films the interface on
its own clock with keys and the mouse, e.g. "UI;hud=1;len=200;then=KEY_TAB@30/KEY_ESCAPE@90/MOVE_B2@110/CLICK@130"
(SkillShots.java); SHOTS_LANG=ko_kr switches the game's language first. SHOTS_MODS (jar paths joined by ':') adds
mods such as Sodium and Iris; SHOTS_SHADER names a shader pack zip for Iris to use for the run, SHOTS_SHADER_OPTIONS
its options ("SUN_ANGLE=-40;..."). SHOTS_WORLD names a world folder to film in (the meteor and shop shots) and
SHOTS_RECORD=1 records the game's sound to OUT_DIR/game.wav (SkillShots.java).
"""
from pathlib import Path
import json, os, shutil, subprocess, sys, zipfile, time, socket

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
out = Path(sys.argv[1]).resolve()
skills = sys.argv[2] if len(sys.argv) > 2 else 'F,M1,E,R,T,RUSH,Y,Q,H,G'
size = sys.argv[3] if len(sys.argv) > 3 else '1280x720'
slow = sys.argv[4] if len(sys.argv) > 4 else '12'
w, h = size.split('x')
build = ROOT / 'build/kageno-shots'
shutil.rmtree(build, ignore_errors=True); build.mkdir(parents=True)
cp_file = ROOT / 'build/test-classpath.txt'
if not cp_file.exists():
    subprocess.run(['./gradlew', '--offline', '-q', 'writeCombatClasspath'], cwd=ROOT, check=True)
cp = cp_file.read_text().strip()
subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(build / 'classes'),
                str(HERE / 'SkillShots.java'), str(HERE / 'SkillShotsMixins.java'), str(HERE / 'SkillShotsCameraMixin.java'), str(HERE / 'SkillShotsObserverMixin.java'),
                str(HERE / 'SkillShotsServerMixin.java'), str(HERE / 'SkillShotsMeteorMixin.java')], check=True)
jar = build / 'shots.jar'
with zipfile.ZipFile(jar, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_shots', 'version': '1.0.0', 'environment': 'client',
        'entrypoints': {'client': ['dev.sangwoo.botandshots.SkillShots']}, 'mixins': ['botand-shots.mixins.json']}))
    z.writestr('botand-shots.mixins.json', json.dumps({'required': True, 'package': 'dev.sangwoo.botandshots.mixin', 'compatibilityLevel': 'JAVA_17', 'client': ['SkillShotsMixins', 'SkillShotsCameraMixin', 'SkillShotsObserverMixin'], 'mixins': ['SkillShotsServerMixin', 'SkillShotsMeteorMixin'], 'injectors': {'defaultRequire': 1}}))
    for p in (build / 'classes').rglob('*.class'):
        z.write(p, str(p.relative_to(build / 'classes')))
shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
mods = ':'.join([str(jar)] + [m for m in os.environ.get('SHOTS_MODS', '').split(':') if m])
# a shader pack for Iris: copied in for this run, Iris's own settings kept aside and put back afterwards
shader = os.environ.get('SHOTS_SHADER')
iris_props = ROOT / 'run/config/iris.properties'
iris_saved = iris_props.read_text() if iris_props.exists() else None
packs = ROOT / 'run/shaderpacks'
if shader:
    packs.mkdir(parents=True, exist_ok=True)
    shutil.copy(shader, packs / Path(shader).name)
    iris_props.parent.mkdir(parents=True, exist_ok=True)
    iris_props.write_text(f'enableShaders=true\nshaderPack={Path(shader).name}\ndisableUpdateMessage=true\n')
    # the pack's own options ("SUN_ANGLE=-40;..."), kept by Iris in a file beside the pack
    options = os.environ.get('SHOTS_SHADER_OPTIONS', '')
    (packs / (Path(shader).name + '.txt')).write_text(''.join(o.strip() + '\n' for o in options.split(';') if o.strip()))
# a real map to film in (the meteor and shop shots): copied into the dev client's saves under its own folder name
world = os.environ.get('SHOTS_WORLD')
world_name = None
if world:
    world_name = 'Shots_' + Path(world).name
    target = ROOT / 'run/saves' / world_name
    shutil.rmtree(target, ignore_errors=True); shutil.copytree(world, target)
init = build / 'shots.gradle'
# A roomy heap so a full collection does not stall a slowed shot (its pauses are logged to OUT_DIR/gc.log).
init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx6G", "-Xms2G", '
                f'"-Xlog:gc:file={out}/gc.log", '
                f'"-Dbotand.shots.out={out}", "-Dbotand.shots.slow={slow}", "-Dbotand.shots.list={skills}", "-Dfabric.addMods={mods}"'
                + (f', "-Dbotand.shots.lang={os.environ["SHOTS_LANG"]}"' if os.environ.get('SHOTS_LANG') else '')
                + (f', "-Dbotand.shots.world={world_name}"' if world_name else '')
                + (', "-Dbotand.shots.audio=true"' if os.environ.get('SHOTS_RECORD') else '') + '; '
                f'args "--username", "ShotsQA", "--width", "{w}", "--height", "{h}" }} }}')
# Sound goes nowhere unless SHOTS_AUDIO names an OpenAL Soft driver: "wave" with ALSOFT_CONF pointing at a config whose
# [wave] section names a file records what the game plays (in real time, so use SLOW 1 for timing).
env = os.environ.copy(); env['LIBGL_ALWAYS_SOFTWARE'] = 'true'; env['ALSOFT_DRIVERS'] = os.environ.get('SHOTS_AUDIO', 'null'); env['GALLIUM_DRIVER'] = 'llvmpipe'
# SHOTS_RECORD=1: everything the game plays goes to OUT_DIR/game.wav (run in real time, SLOW 1), each entry's sounds
# and ticks are logged against System.nanoTime (tools/meteor/shots/mux.py lays it under the film)
if os.environ.get('SHOTS_RECORD'):
    conf = build / 'alsoft.conf'
    conf.write_text(f'[general]\nfrequency = 48000\nchannels = stereo\n\n[wave]\nfile = {out / "game.wav"}\n')
    env['ALSOFT_DRIVERS'] = 'wave'; env['ALSOFT_CONF'] = str(conf)
display = next(i for i in range(151, 190) if not Path(f'/tmp/.X{i}-lock').exists())
env['DISPLAY'] = f':{display}'
x = subprocess.Popen(['Xvfb', f':{display}', '-screen', '0', f'{w}x{h}x24', '-nolisten', 'tcp'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    time.sleep(2)
    start = time.time()
    with (out / 'client.log').open('w') as log:
        r = subprocess.run(['./gradlew', '--no-daemon', '--max-workers=2', 'runClient', '-I', str(init)], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=5400)
    print(json.dumps({'exit': r.returncode, 'seconds': round(time.time() - start), 'error': (out / 'error.txt').read_text() if (out / 'error.txt').exists() else None,
                      'log': (out / 'log.txt').read_text() if (out / 'log.txt').exists() else None}))
finally:
    x.terminate(); x.wait(timeout=10)
    if shader:
        (packs / Path(shader).name).unlink(missing_ok=True)
        (packs / (Path(shader).name + '.txt')).unlink(missing_ok=True)
        if iris_saved is None: iris_props.unlink(missing_ok=True)
        else: iris_props.write_text(iris_saved)
