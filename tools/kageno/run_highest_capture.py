"""Record the real H cutscene, using the isolated RemakeCapture verification mod.

First run: gradlew build writeCombatClasspath downloadAssets; create_remake_world.py.
Usage: python tools/kageno/run_highest_capture.py OUTPUT_DIRECTORY
Optional environment: BOTAND_GRADLE, BOTAND_XVFB, BOTAND_XKBCOMP,
BOTAND_CAPTURE_ONLINE=1 for the first launch on a new build environment.
BOTAND_REVIEW_ATOMIC=1 records only the G pose instead of H.
The verification mod is never added to the production JAR.
"""
from pathlib import Path
import json, os, signal, socket, subprocess, sys, time, zipfile, urllib.parse

ROOT = Path(__file__).resolve().parents[2]
proof = Path(sys.argv[1]).resolve()
proof.mkdir(parents=True, exist_ok=False)
fixture = proof / 'fixture'
fixture.mkdir()
env = os.environ.copy()
env.update(LIBGL_ALWAYS_SOFTWARE='true', ALSOFT_DRIVERS='null')
gradle_network = ['-Djava.net.preferIPv4Stack=true', '--offline']
if env.get('BOTAND_CAPTURE_ONLINE') == '1':
    gradle_network = ['-Djava.net.preferIPv4Stack=true']
    proxy = urllib.parse.urlparse(env.get('HTTPS_PROXY', ''))
    if proxy.hostname and proxy.port:
        gradle_network += [f'-D{scheme}.proxyHost={proxy.hostname}' for scheme in ['http','https']]
        gradle_network += [f'-D{scheme}.proxyPort={proxy.port}' for scheme in ['http','https']]
    env['JAVA_TOOL_OPTIONS'] = ' '.join(gradle_network)
capture_width = int(env.get('BOTAND_CAPTURE_WIDTH', '1280'))
capture_height = int(env.get('BOTAND_CAPTURE_HEIGHT', '720'))
assert 320 <= capture_width <= 1920 and 180 <= capture_height <= 1080
atomic_only = env.get('BOTAND_REVIEW_ATOMIC') == '1'
atomic_far = env.get('BOTAND_REVIEW_ATOMIC_FAR') == '1'
cp = (ROOT / 'build/test-classpath.txt').read_text().strip()
sources = ['RemakeCapture', 'RemakeCaptureRenderMixin', 'HighestDirectionProofMixin',
           'HighestRenderedDirectionMixin', 'HighestFixedCameraMixin']
subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-proc:none',
                '-encoding', 'UTF-8', '-cp', cp, '-d', str(fixture),
                *[str(ROOT / 'tools/kageno' / (s + '.java')) for s in sources]], check=True)
jar = fixture / 'capture.jar'
with zipfile.ZipFile(jar, 'w') as z:
    z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1,
        'id': 'botand_remake_capture', 'version': '1.0.0', 'environment': 'client',
        'entrypoints': {'client': ['dev.sangwoo.botandverify.RemakeCapture']},
        'mixins': ['botand-remake-capture.mixins.json']}))
    z.writestr('botand-remake-capture.mixins.json', json.dumps({'required': True,
        'package': 'dev.sangwoo.botandverify.mixin', 'compatibilityLevel': 'JAVA_17',
        'client': sources[1:]}))
    for p in fixture.rglob('*.class'):
        z.write(p, str(p.relative_to(fixture)))
init = fixture / 'capture.gradle'
init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { '
    'jvmArgs "-Xmx3G", "-Djava.net.preferIPv4Stack=true", '
    '"-Dbotand.review.videoOnly=true", "-Dbotand.review.highest=true", '
    + ('"-Dbotand.review.atomic=true", ' if atomic_only else '') +
    ('"-Dbotand.review.atomicFar=true", ' if atomic_far else '') +
    '"-Dbotand.review.fixed=false", "-Dbotand.remake.proof=' + str(proof) + '", '
    '"-Dfabric.addMods=' + str(jar) + '"; args "--username", "BotandRemakeQA", '
    '"--uuid", "1ed53dfdc16b36d48f8e17ad842911da", "--width", "' + str(capture_width) + '", "--height", "' + str(capture_height) + '" } }')
display = next(i for i in range(151, 180) if not Path(f'/tmp/.X{i}-lock').exists())
env['DISPLAY'] = f'127.0.0.1:{display}'
xkb_link = Path('/usr/bin/xkbcomp')
made_link = False
if not xkb_link.exists() and not xkb_link.is_symlink() and env.get('BOTAND_XKBCOMP'):
    xkb_link.symlink_to(env['BOTAND_XKBCOMP'])
    made_link = True
x = recorder = None
try:
    with (proof/'xvfb.log').open('w') as xl, (proof/'native.log').open('w') as log, (proof/'recording.log').open('w') as vl:
        x = subprocess.Popen([env.get('BOTAND_XVFB', 'Xvfb'), f':{display}',
            '-nolisten', 'unix', '-nolisten', 'local', '-listen', 'tcp', '-ac',
            '-screen', '0', f'{capture_width}x{capture_height}x24'], env=env, stdout=xl, stderr=subprocess.STDOUT)
        for attempt in range(60):
            try:
                with socket.create_connection(('127.0.0.1', 6000+display), timeout=.2):
                    break
            except OSError:
                time.sleep(.1)
        assert x.poll() is None, 'Virtual display failed: ' + (proof/'xvfb.log').read_text()
        (proof/'video-start.txt').write_text(str(time.time()))
        recorder = subprocess.Popen(['ffmpeg', '-v', 'warning', '-f', 'x11grab',
            '-draw_mouse', '0', '-framerate', '30', '-video_size', f'{capture_width}x{capture_height}',
            '-i', env['DISPLAY']+'.0', '-an', '-c:v', 'libx264', '-threads', '2',
            '-preset', 'ultrafast', '-crf', '20', '-pix_fmt', 'yuv420p', '-g', '30',
            '-movflags', '+frag_keyframe+empty_moov+default_base_moof', '-flush_packets', '1', '-y',
            str(proof/'native-session.mp4')], env=env, stdout=vl, stderr=subprocess.STDOUT)
        result = subprocess.run(['bash', env.get('BOTAND_GRADLE', './gradlew'),
            *gradle_network, '--no-daemon', '--max-workers=1', 'runClient', '-x',
            'downloadAssets', '-I', str(init)], cwd=ROOT, env=env,
            stdout=log, stderr=subprocess.STDOUT, timeout=440)
        assert result.returncode == 0, 'Client failed: inspect ' + str(proof/'native.log')
        assert not (proof/'error.txt').exists(), (proof/'error.txt').read_text()
        assert (proof/'result.txt').exists(), 'Capture did not produce a result'
finally:
    if recorder is not None and recorder.poll() is None:
        recorder.send_signal(signal.SIGINT)
        recorder.wait(timeout=30)
    if x is not None and x.poll() is None:
        x.terminate()
        x.wait(timeout=10)
    if made_link:
        xkb_link.unlink()
assert recorder.returncode in (0, 255), 'Recorder failed: inspect ' + str(proof/'recording.log')
with (proof/'native-session.mp4').open('rb') as f:
    os.fsync(f.fileno())
info = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries',
    'format=duration', '-of', 'json', str(proof/'native-session.mp4')]))
assert float(info['format']['duration']) > 6
for p in proof.iterdir():
    if p.is_file():
        with p.open('rb') as f:
            os.fsync(f.fileno())
print(json.dumps({'proof': str(proof), 'video': info, 'checks': (proof/'result.txt').read_text()}))
