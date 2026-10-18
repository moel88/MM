from pathlib import Path
import os,subprocess,urllib.parse,sys
root=Path(__file__).resolve().parents[2];work=root.parent;e=os.environ.copy();e['GRADLE_USER_HOME']=str(work/'gradle-cache')
p=urllib.parse.urlparse(e.get('HTTPS_PROXY',''))
e['GRADLE_OPTS']=f'-Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port} -Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port}' if p.hostname else ''
with (work/'npc-build.log').open('w') as f:
 r=subprocess.run([str(work/'build-tools/gradle-8.6/bin/gradle'),'--no-daemon']+(sys.argv[1:] or ['build','writeCombatClasspath']),cwd=root,env=e,stdout=f,stderr=subprocess.STDOUT)
print((work/'npc-build.log').read_text()[-10000:]);sys.exit(r.returncode)
