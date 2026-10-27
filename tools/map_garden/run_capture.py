from pathlib import Path
import subprocess,zipfile,json,shutil,os,time
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT.parent;out=ROOT/'build/garden-client-capture';out.mkdir(parents=True,exist_ok=True)
cp=(ROOT/'build/test-classpath.txt').read_text().strip()
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(out),str(ROOT/'tools/map_garden/GardenClientCapture.java')],check=True)
jar=out/'capture.jar'
with zipfile.ZipFile(jar,'w') as z:
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'botand_garden_capture','version':'1.0.0','environment':'client','entrypoints':{'client':['dev.sangwoo.botandverify.GardenClientCapture']}}))
 for p in out.rglob('*.class'):z.write(p,str(p.relative_to(out)))
world_name='Botand_Garden_QA_'+str(time.time_ns());world=ROOT/'run/saves'/world_name
if world.exists():shutil.rmtree(world)
shutil.copytree(WORK/'map_work/base/Cliffside_City_Third_1.20.1',world)
for file in world.rglob('*'):
 if file.is_file():
  with file.open('rb') as f:os.fsync(f.fileno())
print('Fresh world:',world_name,flush=True)
init=out/'capture.gradle';init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx4G", "-Dbotand.garden.world='+world_name+'", "-Dfabric.addMods='+str(jar)+'"; args "--width", "1600", "--height", "900" } }')
E=os.environ.copy();E.pop('GRADLE_OPTS',None);E['LD_LIBRARY_PATH']=str(WORK/'title-runtime/root/usr/lib/x86_64-linux-gnu');E['LIBGL_ALWAYS_SOFTWARE']='true';E['ALSOFT_DRIVERS']='null'
display=next(i for i in range(103,150) if not Path(f'/tmp/.X{i}-lock').exists());E['DISPLAY']=f'127.0.0.1:{display}'
proof=ROOT/'build/map-garden/verification/native';proof.mkdir(exist_ok=True)
for name in ['client-result.txt','client-error.txt','server-result.txt']:
 p=proof/name
 if p.exists():p.unlink()
started=time.time()
with (out/'xvfb.log').open('w') as log:
 x=subprocess.Popen([str(WORK/'title-runtime/root/usr/bin/Xvfb'),f':{display}','-nolisten','unix','-nolisten','local','-listen','tcp','-ac','-screen','0','1600x900x24'],env=E,stdout=log,stderr=subprocess.STDOUT)
 try:
  time.sleep(.6)
  with (WORK/'garden-native.log').open('w') as log:
   p=subprocess.run([str(WORK/'build-tools/gradle-8.6/bin/gradle'),'--offline','--no-daemon','runClient','-x','downloadAssets','-I',str(init)],cwd=ROOT,env=E,stdout=log,stderr=subprocess.STDOUT,timeout=960)
  error=proof/'client-error.txt';result=proof/'client-result.txt'
  assert p.returncode==0 and result.exists() and result.stat().st_mtime>=started and not (error.exists() and error.stat().st_mtime>=started),error.read_text() if error.exists() and error.stat().st_mtime>=started else 'Missing fresh garden evidence'
  print(result.read_text());print((proof/'server-result.txt').read_text())
 finally:x.terminate();x.wait(timeout=10)
