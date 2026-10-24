from pathlib import Path
import subprocess,zipfile,json,os,time,sys
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/kageno-capture';OUT.mkdir(parents=True,exist_ok=True)
import shutil
shutil.rmtree(OUT/'dev',ignore_errors=True)
cp=(ROOT/'build/test-classpath.txt').read_text().strip()
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(OUT),str(ROOT/'tools/kageno/KagenoCapture.java'),str(ROOT/'tools/kageno/KagenoCaptureRenderMixin.java')],check=True)
jar=OUT/'capture.jar'
with zipfile.ZipFile(jar,'w') as z:
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'botand_kageno_capture','version':'1.0.0','environment':'client','entrypoints':{'client':['dev.sangwoo.botandverify.KagenoCapture']},'mixins':['botand-kageno-capture.mixins.json']}))
 z.writestr('botand-kageno-capture.mixins.json',json.dumps({'required':True,'package':'dev.sangwoo.botandverify.mixin','compatibilityLevel':'JAVA_17','client':['KagenoCaptureRenderMixin']}))
 for p in OUT.rglob('*.class'):z.write(p,str(p.relative_to(OUT)))
name='Botand_Kageno_QA_'+str(time.time_ns());world=ROOT/'run/saves'/name
with zipfile.ZipFile(sys.argv[1]) as z:
 for n in z.namelist():
  prefix='Botand_Azure_Garden_1.20.1/'
  if n.startswith(prefix) and not n.endswith('/'):
   p=world/n[len(prefix):];p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
proof=ROOT/'build/kageno-verification'/str(time.time_ns());proof.mkdir(parents=True)
(ROOT/('build/kageno-atomic-proof-path.txt' if len(sys.argv)>2 and sys.argv[2]=='atomic' else 'build/kageno-proof-path.txt')).write_text(str(proof))
init=OUT/'capture.gradle'
init.write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Xmx3G", "-Dbotand.kageno.proof='+str(proof)+'", "-Dbotand.kageno.world='+name+'", "-Dfabric.addMods='+str(jar)+'"; args "--username", "BotandKagenoQA", "--uuid", "1ed53dfdc16b36d48f8e17ad842911da", "--width", "1280", "--height", "720" } }')
if len(sys.argv)>2:
 mode='highest' if sys.argv[2]=='highest' else 'atomic'
 init.write_text(init.read_text().replace('jvmArgs "-Xmx3G",',f'jvmArgs "-Xmx3G", "-Dbotand.kageno.{mode}=true",'))
for fixture in [jar,init]+[p for p in world.rglob('*') if p.is_file()]:
 with fixture.open('rb') as f:os.fsync(f.fileno())
print(proof)
