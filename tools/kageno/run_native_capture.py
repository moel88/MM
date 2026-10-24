"""Capture the real client and integrated server, together with an uncut X11 recording.
The display, recorder and JVM share one process namespace. Configure executables via
BOTAND_GRADLE and BOTAND_XVFB; no generated gameplay frames are used.
"""
from pathlib import Path
import os, subprocess, time, sys, signal, socket, json

ROOT=Path(__file__).resolve().parents[2]
mode=sys.argv[2] if len(sys.argv)>2 else 'full'
args=[sys.executable,str(ROOT/'tools/kageno/prepare_capture.py'),sys.argv[1]]
if mode!='full':args.append(mode)
subprocess.run(args,check=True,cwd=ROOT)
proof=Path((ROOT/('build/kageno-atomic-proof-path.txt' if mode=='atomic' else 'build/kageno-proof-path.txt')).read_text().strip())
env=os.environ.copy();env['LIBGL_ALWAYS_SOFTWARE']='true';env['ALSOFT_DRIVERS']='null';env.pop('GRADLE_OPTS',None)
display=next(i for i in range(151,180) if not Path(f'/tmp/.X{i}-lock').exists())
env['DISPLAY']=f'127.0.0.1:{display}'
with (proof/'xvfb.log').open('w') as xl,(proof/'native.log').open('w') as log,(proof/'recording.log').open('w') as vl:
 x=subprocess.Popen([env.get('BOTAND_XVFB','Xvfb'),f':{display}','-nolisten','unix','-nolisten','local','-listen','tcp','-ac','-screen','0','1280x720x24'],env=env,stdout=xl,stderr=subprocess.STDOUT)
 recorder=None
 try:
  for attempt in range(60):
   try:
    with socket.create_connection(('127.0.0.1',6000+display),timeout=.2):break
   except OSError:time.sleep(.1)
  assert x.poll() is None,'Virtual display failed'
  start=time.time();(proof/'video-start.txt').write_text(str(start))
  recorder=subprocess.Popen(['ffmpeg','-v','warning','-f','x11grab','-draw_mouse','0','-framerate','30','-video_size','1280x720','-i',env['DISPLAY']+'.0','-an','-c:v','libx264','-threads','2','-preset','ultrafast','-crf','19','-pix_fmt','yuv420p','-y',str(proof/'native-session.mp4')],env=env,stdout=vl,stderr=subprocess.STDOUT)
  result=subprocess.run([env.get('BOTAND_GRADLE','./gradlew'),*([] if env.get('BOTAND_CAPTURE_ONLINE')=='1' else ['--offline']),'--no-daemon','--max-workers=1','runClient','-x','downloadAssets','-I','build/kageno-capture/capture.gradle'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=440)
  error=proof/'client-error.txt';report=proof/('atomic-result.txt' if mode=='atomic' else 'h-review-result.txt' if mode=='highest' else 'client-result.txt')
  assert result.returncode==0 and report.is_file() and not error.exists(),error.read_text() if error.exists() else f'Inspect {proof}/native.log'
  print(json.dumps({'proof':str(proof),'checks':report.read_text().count('PASS '),'mode':mode}),flush=True)
 finally:
  if recorder is not None and recorder.poll() is None:
   recorder.send_signal(signal.SIGINT);recorder.wait(timeout=20)
  x.terminate();x.wait(timeout=10)
