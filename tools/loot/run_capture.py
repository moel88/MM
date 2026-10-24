from pathlib import Path
import subprocess,zipfile,json,shutil,os,time
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT.parent;out=ROOT/'build/loot-client-capture';out.mkdir(parents=True,exist_ok=True)
cp=(ROOT/'build/test-classpath.txt').read_text().strip()
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(out),str(ROOT/'tools/loot/LootCapture.java')],check=True)
jar=out/'capture.jar'
with zipfile.ZipFile(jar,'w') as z:
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'botand_loot_capture','version':'1.0.0','environment':'client','entrypoints':{'client':['dev.sangwoo.botandverify.LootCapture']}}))
 for p in out.rglob('*.class'):z.write(p,str(p.relative_to(out)))
world_name='Botand_Loot_QA_'+str(time.time_ns());world=ROOT/'run/saves'/world_name
if world.exists():shutil.rmtree(world)
with zipfile.ZipFile(WORK/'deliverables/Botand-Azure-Garden-1.20.1-v1.3.zip') as old:
 for name in old.namelist():
  prefix='Botand_Azure_Garden_1.20.1/'
  if name.startswith(prefix) and not name.endswith('/'):
   dest=world/name[len(prefix):];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(old.read(name))
for file in world.rglob('*'):
 if file.is_file():
  with file.open('rb') as f:os.fsync(f.fileno())
print('Fresh world:',world_name,flush=True)
proof=ROOT/'build/loot-verification'/str(time.time_ns());proof.mkdir(parents=True)
init=out/'capture.gradle';init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx4G", "-Dbotand.loot.proof='+str(proof)+'", "-Dbotand.loot.world='+world_name+'", "-Dfabric.addMods='+str(jar)+'"; args "--username", "BotandLootQA", "--uuid", "1ed53dfdc16b36d48f8e17ad842911da", "--width", "1600", "--height", "900" } }')
E=os.environ.copy();E.pop('GRADLE_OPTS',None);E['LD_LIBRARY_PATH']=str(WORK/'title-runtime/root/usr/lib/x86_64-linux-gnu');E['LIBGL_ALWAYS_SOFTWARE']='true';E['ALSOFT_DRIVERS']='null'
E['GRADLE_USER_HOME']=str(WORK/'gradle-cache')
from urllib.parse import urlparse
proxy=urlparse(E.get('HTTPS_PROXY',''))
if proxy.hostname:
 E['GRADLE_OPTS']=f'-Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port} -Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port}'
display=next(i for i in range(103,150) if not Path(f'/tmp/.X{i}-lock').exists());E['DISPLAY']=f'127.0.0.1:{display}'
(ROOT/'build/loot-proof-path.txt').write_text(str(proof))
for name in ['client-result.txt','client-error.txt','server-result.txt']:
 p=proof/name
 if p.exists():p.unlink()
# Loom 1.1 names this index with the game version prefix when assets are cached manually.
assets=WORK/'gradle-cache/caches/fabric-loom/assets/indexes'
if (assets/'5.json').exists() and not (assets/'1.20.1-5.json').exists():shutil.copyfile(assets/'5.json',assets/'1.20.1-5.json')
started=time.time()
with (out/'xvfb.log').open('w') as log:
 x=subprocess.Popen([str(WORK/'title-runtime/root/usr/bin/Xvfb'),f':{display}','-nolisten','unix','-nolisten','local','-listen','tcp','-ac','-screen','0','1600x900x24'],env=E,stdout=log,stderr=subprocess.STDOUT)
 try:
  time.sleep(.6)
  with (WORK/'loot-native.log').open('w') as log:
   p=subprocess.run([str(WORK/'build-tools/gradle-8.6/bin/gradle'),'--no-daemon','runClient','-x','downloadAssets','-I',str(init)],cwd=ROOT,env=E,stdout=log,stderr=subprocess.STDOUT,timeout=960)
  error=proof/'client-error.txt';result=proof/'client-result.txt'
  assert p.returncode==0 and result.exists() and result.stat().st_mtime>=started and not (error.exists() and error.stat().st_mtime>=started),error.read_text() if error.exists() and error.stat().st_mtime>=started else 'Missing fresh loot evidence'
  print(result.read_text());print((proof/'server-result.txt').read_text());
  init.write_text(init.read_text().replace('jvmArgs \"-Xmx4G\",', 'jvmArgs \"-Xmx4G\", \"-Dbotand.loot.resume=true\",'))
  with (WORK/'loot-restart.log').open('w') as log:
   resumed=subprocess.run([str(WORK/'build-tools/gradle-8.6/bin/gradle'),'--no-daemon','runClient','-x','downloadAssets','-I',str(init)],cwd=ROOT,env=E,stdout=log,stderr=subprocess.STDOUT,timeout=480)
  assert resumed.returncode==0 and (proof/'restart-result.txt').exists() and not (proof/'restart-error.txt').exists(), (proof/'restart-error.txt').read_text() if (proof/'restart-error.txt').exists() else 'Restart proof missing'
  print((proof/'restart-result.txt').read_text());(ROOT/'build/loot-test-world.txt').write_text(str(world))
 finally:x.terminate();x.wait(timeout=10)
