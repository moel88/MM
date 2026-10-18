from pathlib import Path
import subprocess,zipfile,json,sys
root=Path(__file__).resolve().parents[2];workspace=root.parent
classpath_file=Path(sys.argv[1]) if len(sys.argv)>1 else root/'build/test-classpath.txt'
cp=classpath_file.read_text().strip();out=root/'build/smoke-mod';out.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-proc:none','-cp',cp,'-d',str(out),str(Path(__file__).with_name('CombatSmoke.java')),str(Path(__file__).with_name('ComboSmoke.java')),str(Path(__file__).with_name('DummySmoke.java')),str(Path(__file__).with_name('DashSmoke.java')),str(Path(__file__).with_name('AtomicSmoke.java')),str(Path(__file__).with_name('ProgressSmoke.java')),str(Path(__file__).with_name('TerrainSmoke.java')),str(Path(__file__).with_name('MapGateSmoke.java')),str(Path(__file__).with_name('MenuSmoke.java'))],check=True)
jar=out/'botand-verification.jar'
with zipfile.ZipFile(jar,'w') as z:
 for path in out.rglob('*.class'):z.write(path,path.relative_to(out))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'botand_verification','version':'1.0.0','environment':'server','entrypoints':{'main':['dev.sangwoo.botandverify.CombatSmoke']},'depends':{'celestialfall':'*'}}))
(root/'tools/integration/smoke.gradle').write_text('gradle.projectsEvaluated { rootProject.tasks.named("runServer") { jvmArgs "-Dfabric.addMods='+str(jar)+'" } }\n')
print(jar)

client_out=root/'build/client-smoke';client_out.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-proc:none','-cp',cp,'-d',str(client_out),str(Path(__file__).with_name('ClientMixinSmoke.java'))],check=True)
client_jar=client_out/'botand-client-verification.jar'
with zipfile.ZipFile(client_jar,'w') as z:
 for path in client_out.rglob('*.class'):z.write(path,path.relative_to(client_out))
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'botand_client_verification','version':'1.0.0','environment':'client','entrypoints':{'preLaunch':['dev.sangwoo.botandverify.ClientMixinSmoke']},'depends':{'celestialfall':'*'}}))
(root/'tools/integration/client-smoke.gradle').write_text('gradle.projectsEvaluated { rootProject.tasks.named("runClient") { jvmArgs "-Dfabric.addMods='+str(client_jar)+'" } }'+chr(10))
