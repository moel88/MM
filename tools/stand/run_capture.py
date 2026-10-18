from pathlib import Path
import subprocess,zipfile,json,os,time,sys
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/stand-capture';OUT.mkdir(parents=True,exist_ok=True)
cp=(ROOT/'build/test-classpath.txt').read_text().strip()
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(OUT),str(ROOT/'tools/stand/StandCapture.java')],check=True)
jar=OUT/'capture.jar'
with zipfile.ZipFile(jar,'w') as z:
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'botand_stand_capture','version':'1.0.0','environment':'client','entrypoints':{'client':['dev.sangwoo.botandverify.StandCapture']}}))
 for p in OUT.rglob('*.class'):z.write(p,str(p.relative_to(OUT)))
name='Botand_Stand_QA_'+str(time.time_ns());world=ROOT/'run/saves'/name
with zipfile.ZipFile(sys.argv[1]) as z:
 for n in z.namelist():
  prefix='Botand_Azure_Garden_1.20.1/'
  if n.startswith(prefix) and not n.endswith('/'):
   p=world/n[len(prefix):];p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
proof=ROOT/'build/stand-verification'/str(time.time_ns());proof.mkdir(parents=True)
(ROOT/'build/stand-proof-path.txt').write_text(str(proof))
init=OUT/'capture.gradle'
init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx3G", "-Dbotand.stand.proof='+str(proof)+'", "-Dbotand.stand.world='+name+'", "-Dfabric.addMods='+str(jar)+'"; args "--username", "BotandStandQA", "--uuid", "1ed53dfdc16b36d48f8e17ad842911da", "--width", "1280", "--height", "720" } }')
# Flush generated fixture files before launching a separate JVM.
for fixture in [jar,init]+[p for p in world.rglob('*') if p.is_file()]:
 with fixture.open('rb') as f:os.fsync(f.fileno())
env=os.environ.copy();env['LIBGL_ALWAYS_SOFTWARE']='true';env['ALSOFT_DRIVERS']='null'
display=next(i for i in range(151,180) if not Path(f'/tmp/.X{i}-lock').exists());env['DISPLAY']=f'127.0.0.1:{display}'
with (OUT/'xvfb.log').open('w') as log:
 x=subprocess.Popen([env.get('BOTAND_XVFB','Xvfb'),f':{display}','-nolisten','unix','-nolisten','local','-listen','tcp','-ac','-screen','0','1280x720x24'],env=env,stdout=log,stderr=subprocess.STDOUT)
 try:
  time.sleep(.6)
  for resume in [False,True]:
   if resume:init.write_text(init.read_text().replace('jvmArgs "-Xmx3G",','jvmArgs "-Xmx3G", "-Dbotand.stand.resume=true",'))
   with init.open('rb') as f:os.fsync(f.fileno())
   with (OUT/('restart.log' if resume else 'client.log')).open('w') as log:
    p=subprocess.run([env.get('BOTAND_GRADLE','./gradlew'),'--offline','--no-daemon','runClient','-x','downloadAssets','-I',str(init)],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=420)
   result=proof/('restart-result.txt' if resume else 'client-result.txt');error=proof/('restart-error.txt' if resume else 'client-error.txt')
   assert p.returncode==0 and result.exists() and not error.exists(),error.read_text() if error.exists() else f'No result; inspect {OUT}'
   print(result.read_text(),flush=True)
 finally:x.terminate();x.wait(timeout=10)
print(proof)
