"""Render Kageno skills in the real client under Xvfb and save every frame.
usage: python3 tools/kageno/shots/run_shots.py OUT_DIR [SKILLS] [WIDTHxHEIGHT]
"""
from pathlib import Path
import json, os, shutil, subprocess, sys, zipfile, time, socket

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
out = Path(sys.argv[1]).resolve()
skills = sys.argv[2] if len(sys.argv) > 2 else 'F,M1,E,R,T,RUSH,Y,Q,H,G'
size = sys.argv[3] if len(sys.argv) > 3 else '1280x720'
slow = sys.argv[4] if len(sys.argv) > 4 else '6'
w, h = size.split('x')
build = ROOT / 'build/kageno-shots'
shutil.rmtree(build, ignore_errors=True); build.mkdir(parents=True)
cp_file = ROOT / 'build/test-classpath.txt'
if not cp_file.exists():
    subprocess.run(['./gradlew', '--offline', '-q', 'writeCombatClasspath'], cwd=ROOT, check=True)
cp = cp_file.read_text().strip()
subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(build / 'classes'),
                str(HERE / 'SkillShots.java'), str(HERE / 'SkillShotsMixins.java'), str(HERE / 'SkillShotsServerMixin.java')], check=True)
jar = build / 'shots.jar'
with zipfile.ZipFile(jar, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_shots', 'version': '1.0.0', 'environment': 'client',
        'entrypoints': {'client': ['dev.sangwoo.botandshots.SkillShots']}, 'mixins': ['botand-shots.mixins.json']}))
    z.writestr('botand-shots.mixins.json', json.dumps({'required': True, 'package': 'dev.sangwoo.botandshots.mixin', 'compatibilityLevel': 'JAVA_17', 'client': ['SkillShotsMixins'], 'mixins': ['SkillShotsServerMixin'], 'injectors': {'defaultRequire': 1}}))
    for p in (build / 'classes').rglob('*.class'):
        z.write(p, str(p.relative_to(build / 'classes')))
shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
init = build / 'shots.gradle'
init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx3G", '
                f'"-Dbotand.shots.out={out}", "-Dbotand.shots.slow={slow}", "-Dbotand.shots.list={skills}", "-Dfabric.addMods={jar}"; '
                f'args "--username", "ShotsQA", "--width", "{w}", "--height", "{h}" }} }}')
env = os.environ.copy(); env['LIBGL_ALWAYS_SOFTWARE'] = 'true'; env['ALSOFT_DRIVERS'] = 'null'; env['GALLIUM_DRIVER'] = 'llvmpipe'
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
