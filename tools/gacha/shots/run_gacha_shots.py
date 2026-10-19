"""Film the gacha (2.39.0, 2.40.0) in the real client under Xvfb and save every frame, or record its sound.
usage: python3 tools/gacha/shots/run_gacha_shots.py OUT_DIR [ENTRIES] [WIDTHxHEIGHT] [SLOW]
ENTRIES is a comma list of GachaShots entries (MENU, STAND, R1, SR1, SSR1, TEN, TENR); frames go to OUT_DIR/ENTRY/ as
INDEX_TICKS.png on the entry's own clock, which tools/kageno/shots/film.py turns into 60 fps clips. SLOW runs the game
that many times slower than real time: the pull scenes render in software here, so use 30 or more (50 for TEN).
GACHA_SHOTS_AUDIO=1 (2.40.0) instead runs the same list in real time (SLOW 1), saves no frames, and has OpenAL write
everything the game plays to OUT_DIR/game.wav, with OUT_DIR/ticks.txt tying each entry's clock to the recording
(tools/gacha/shots/mux_audio.py lays it under the films). The server rolls from a fixed seed in both, so they match.
The scenes cue their sounds on the first rendered frame past each cue, and in software the 3D scenes draw only a few
frames a second, which played cues up to 0.4 s late; so the sound pass draws the 2D scenes (the same timeline and the
same cues) in a 640x366 window, where frames come fast. GACHA_SHOTS_AUDIO_3D=1 keeps the 3D scenes and the given size.
"""
from pathlib import Path
import json, os, shutil, subprocess, sys, zipfile, time
import numpy as np

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
out = Path(sys.argv[1]).resolve()
entries = sys.argv[2] if len(sys.argv) > 2 else 'MENU,STAND,R1,SR1,SSR1,TEN'
size = sys.argv[3] if len(sys.argv) > 3 else '1280x720'
audio = os.environ.get('GACHA_SHOTS_AUDIO') == '1'
slow = '1' if audio else (sys.argv[4] if len(sys.argv) > 4 else '36')
w, h = size.split('x')
fast_sound = audio and os.environ.get('GACHA_SHOTS_AUDIO_3D') != '1'
if fast_sound:
    w, h = '640', '366'
build = ROOT / 'build/gacha-shots'
shutil.rmtree(build, ignore_errors=True); build.mkdir(parents=True)
cp_file = ROOT / 'build/test-classpath.txt'
if not cp_file.exists():
    subprocess.run(['./gradlew', '--offline', '-q', 'writeCombatClasspath'], cwd=ROOT, check=True)
cp = cp_file.read_text().strip()
subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(build / 'classes'),
                str(HERE / 'GachaShots.java'), str(HERE / 'GachaShotsMixins.java'), str(HERE / 'GachaShotsServerMixin.java'),
                str(HERE / 'GachaShotsRollMixin.java'), str(HERE / 'GachaShotsSoundMixin.java'), str(HERE / 'QaRolls.java')], check=True)
# the marker tone: one second of 1 kHz, faded in and out, to find in the recording
sr = 48000
t = np.arange(int(0.4 * sr)) / sr
tone = np.sin(2 * np.pi * 1000 * t) * np.minimum(1, np.minimum(t / 0.005, (0.4 - t) / 0.005)) * 0.5
wav = build / 'marker.wav'
import wave
with wave.open(str(wav), 'wb') as f:
    f.setnchannels(1); f.setsampwidth(2); f.setframerate(sr); f.writeframes((tone * 32767).astype('<i2').tobytes())
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '6', str(build / 'marker.ogg')], check=True)
jar = build / 'gacha-shots.jar'
with zipfile.ZipFile(jar, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'botand_gacha_shots', 'version': '1.0.0', 'environment': '*',
        'entrypoints': {'client': ['dev.sangwoo.gachashots.GachaShots']}, 'mixins': ['botand-gacha-shots.mixins.json']}))
    z.writestr('botand-gacha-shots.mixins.json', json.dumps({'required': True, 'package': 'dev.sangwoo.gachashots.mixin', 'compatibilityLevel': 'JAVA_17',
        'client': ['GachaShotsMixins', 'GachaShotsSoundMixin'], 'mixins': ['GachaShotsServerMixin', 'GachaShotsRollMixin'], 'injectors': {'defaultRequire': 1}}))
    z.writestr('assets/botand_gacha_shots/sounds.json', json.dumps({'marker': {'sounds': [{'name': 'botand_gacha_shots:marker', 'preload': True}]}}))
    z.write(build / 'marker.ogg', 'assets/botand_gacha_shots/sounds/marker.ogg')
    for p in (build / 'classes').rglob('*.class'):
        z.write(p, str(p.relative_to(build / 'classes')))
shutil.rmtree(out, ignore_errors=True); out.mkdir(parents=True)
init = build / 'shots.gradle'
# GACHA_SHOTS_JVM adds JVM options, e.g. "-Dbotand.cinema.compatibility=true" films the 2D scenes shown where desktop
# OpenGL 3.3 is missing (mobile launchers).
extra = ''.join(f', "{a}"' for a in os.environ.get('GACHA_SHOTS_JVM', '').split())
# GACHA_SHOTS_EVERY sets the least time between saved frames in ticks (default 0.15; 1/3 is what 60 fps needs)
extra += f', "-Dbotand.shots.every={os.environ.get("GACHA_SHOTS_EVERY", "0.15")}"'
# GACHA_SHOTS_SCENE_ONLY=1 stops each pull at its results (a quick look at the scene)
if os.environ.get('GACHA_SHOTS_SCENE_ONLY'):
    extra += ', "-Dbotand.shots.sceneOnly=true"'
if audio:
    extra += ', "-Dbotand.shots.audio=true"'
if fast_sound:
    extra += ', "-Dbotand.cinema.compatibility=true"'
init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx6G", "-Xms2G", '
                f'"-Dbotand.shots.out={out}", "-Dbotand.shots.slow={slow}", "-Dbotand.gacha.list={entries}", "-Dfabric.addMods={jar}"{extra}; '
                f'args "--username", "ShotsQA", "--width", "{w}", "--height", "{h}" }} }}')
env = os.environ.copy(); env['LIBGL_ALWAYS_SOFTWARE'] = 'true'; env['GALLIUM_DRIVER'] = 'llvmpipe'
if audio:
    conf = build / 'alsoft.conf'
    conf.write_text(f'[general]\nfrequency = 48000\nchannels = stereo\n\n[wave]\nfile = {out / "game.wav"}\n')
    env['ALSOFT_DRIVERS'] = 'wave'; env['ALSOFT_CONF'] = str(conf)
else:
    env['ALSOFT_DRIVERS'] = 'null'
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
