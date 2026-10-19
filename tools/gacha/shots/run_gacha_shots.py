"""Film the gacha (2.39.0) in the real client under Xvfb and save every frame.
usage: python3 tools/gacha/shots/run_gacha_shots.py OUT_DIR [ENTRIES] [WIDTHxHEIGHT] [SLOW]
ENTRIES is a comma list of GachaShots entries (MENU, STAND, R1, SR1, SSR1, TEN); frames go to OUT_DIR/ENTRY/ as
INDEX_TICKS.png on the entry's own clock, which tools/kageno/shots/film.py turns into 60 fps clips. SLOW runs the game
that many times slower than real time: the pull scenes render in software here, so use 36 or more.
"""
from pathlib import Path
import json, os, shutil, subprocess, sys, zipfile, time

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
out = Path(sys.argv[1]).resolve()
entries = sys.argv[2] if len(sys.argv) > 2 else 'MENU,STAND,R1,SR1,SSR1,TEN'
size = sys.argv[3] if len(sys.argv) > 3 else '1280x720'
slow = sys.argv[4] if len(sys.argv) > 4 else '36'
w, h = size.split('x')
build = ROOT / 'build/gacha-shots'
shutil.rmtree(build, ignore_errors=True); build.mkdir(parents=True)
cp_file = ROOT / 'build/test-classpath.txt'
if not cp_file.exists():
    subprocess.run(['./gradlew', '--offline', '-q', 'writeCombatClasspath'], cwd=ROOT, check=True)
cp = cp_file.read_text().strip()
subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(build / 'classes'),
                str(HERE / 'GachaShots.java'), str(HERE / 'GachaShotsMixins.java'), str(HERE / 'GachaShotsServerMixin.java')], check=True)
jar = build / 'gacha-shots.jar'
with zipfile.ZipFile(jar, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_gacha_shots', 'version': '1.0.0', 'environment': 'client',
        'entrypoints': {'client': ['dev.sangwoo.gachashots.GachaShots']}, 'mixins': ['botand-gacha-shots.mixins.json']}))
    z.writestr('botand-gacha-shots.mixins.json', json.dumps({'required': True, 'package': 'dev.sangwoo.gachashots.mixin', 'compatibilityLevel': 'JAVA_17', 'client': ['GachaShotsMixins'], 'mixins': ['GachaShotsServerMixin'], 'injectors': {'defaultRequire': 1}}))
    for p in (build / 'classes').rglob('*.class'):
        z.write(p, str(p.relative_to(build / 'classes')))
shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
init = build / 'shots.gradle'
# GACHA_SHOTS_JVM adds JVM options, e.g. "-Dbotand.cinema.compatibility=true" films the 2D scenes shown where desktop
# OpenGL 3.3 is missing (mobile launchers).
extra = ''.join(f', "{a}"' for a in os.environ.get('GACHA_SHOTS_JVM', '').split())
init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx6G", "-Xms2G", '
                f'"-Dbotand.shots.out={out}", "-Dbotand.shots.slow={slow}", "-Dbotand.gacha.list={entries}", "-Dfabric.addMods={jar}"{extra}; '
                f'args "--username", "ShotsQA", "--width", "{w}", "--height", "{h}" }} }}')
env = os.environ.copy(); env['LIBGL_ALWAYS_SOFTWARE'] = 'true'; env['ALSOFT_DRIVERS'] = 'null'; env['GALLIUM_DRIVER'] = 'llvmpipe'
display = next(i for i in range(151, 190) if not Path(f'/tmp/.X{i}-lock').exists())
env['DISPLAY'] = f':{display}'
x = subprocess.Popen(['Xvfb', f':{display}', '-screen', '0', f'{w}x{h}x24', '-nolisten', 'tcp'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    time.sleep(2)
    start = time.time()
    with (out / 'client.log').open('w') as log:
        r = subprocess.run(['./gradlew', '--no-daemon', '--max-workers=2', 'runClient', '-I', str(init)], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=7200)
    print(json.dumps({'exit': r.returncode, 'seconds': round(time.time() - start), 'error': (out / 'error.txt').read_text() if (out / 'error.txt').exists() else None,
                      'log': (out / 'log.txt').read_text() if (out / 'log.txt').exists() else None}))
finally:
    x.terminate(); x.wait(timeout=10)
