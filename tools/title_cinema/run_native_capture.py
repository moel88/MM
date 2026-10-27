"""Launch Xvfb and Minecraft in the same network namespace. No production assets injected."""
from pathlib import Path
import os,subprocess,time
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT.parent
display=next(i for i in range(103,150) if not Path(f'/tmp/.X{i}-lock').exists())
E=os.environ.copy();E.pop('GRADLE_OPTS',None);E['LD_LIBRARY_PATH']=str(WORK/'title-runtime/root/usr/lib/x86_64-linux-gnu');E['DISPLAY']=f'127.0.0.1:{display}';E['LIBGL_ALWAYS_SOFTWARE']='true';E['ALSOFT_DRIVERS']='null'
started=time.time();variant='native-compat' if E.get('BOTAND_CAPTURE_COMPAT')=='true' else 'native-map'
result=ROOT/'build/title-cinema'/variant/'native-client-result.txt'
error=ROOT/'build/title-cinema'/variant/'native-client-error.txt'
for file in [result,error]:
 if file.exists():file.unlink()
with (WORK/'cinema-xvfb.log').open('w') as f:
 xvfb=subprocess.Popen([str(WORK/'title-runtime/root/usr/bin/Xvfb'),f':{display}','-nolisten','unix','-nolisten','local','-listen','tcp','-ac','-screen','0','1280x720x24'],env=E,stdout=f,stderr=subprocess.STDOUT)
 try:
  time.sleep(.6)
  if xvfb.poll() is not None:raise RuntimeError("Virtual display did not start")
  with (WORK/('cinema-'+variant+'.log')).open('w') as log:
   p=subprocess.run([str(WORK/'build-tools/gradle-8.6/bin/gradle'),'--offline','--no-daemon','runClient','-x','downloadAssets','-I','tools/title_cinema/capture.gradle'],cwd=ROOT,env=E,stdout=log,stderr=subprocess.STDOUT,timeout=260)
   print('Native client exit',p.returncode)
   assert p.returncode==0 and result.exists() and result.stat().st_mtime>=started and not error.exists(), error.read_text() if error.exists() else 'Missing fresh client evidence'
   print(result.read_text())
 finally:xvfb.terminate();xvfb.wait(timeout=10)
