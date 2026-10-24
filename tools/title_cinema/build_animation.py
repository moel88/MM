"""Bake a continuous rigid-joint performance; exactly shared by runtime and preview."""
from pathlib import Path
import json,math,struct,os
import numpy as np
from scipy.spatial.transform import Rotation
from scipy.optimize import least_squares
HERE=Path(__file__).resolve().parent;RES=HERE.parents[1]/'src/main/resources/assets/celestialfall/cinema'
meta=json.loads((RES/'scene.json').read_text());bones=meta['bones'];names=[b['name'] for b in bones];idx={n:i for i,n in enumerate(names)}
piv=np.array([b['pivot'] for b in bones])/16;parents=[idx.get(b['parent'],-1) for b in bones]
def skin(rotations,translation=(0,0,0)):
 out=[]
 for i,b in enumerate(bones):
  r=Rotation.from_euler('xyz',rotations.get(b['name'],[0,0,0]),degrees=True).as_matrix();m=np.eye(4);m[:3,:3]=r;m[:3,3]=piv[i]-r@piv[i]
  if parents[i]>=0:m=out[parents[i]]@m
  else:m[:3,3]+=translation
  out.append(m)
 return np.array(out)
# Only the vanilla shoulder joints move: arms and legs stay single rigid cuboids.
# Fit the ends of the original arms to the prop, with no elbows, wrists or fingers.
raw=(RES/'bomi.mesh').read_bytes();verts=np.frombuffer(raw[8:],'<f4').reshape(-1,14)
carry={};contacts={}
for side,target in [('right',[-.16,1.04,.50]),('left',[.16,.90,.41])]:
 name=side+'_arm';i=idx[name];base=verts[verts[:,12]==i][:36,:3]
 tip=np.unique(base[np.isclose(base[:,1],base[:,1].min())],axis=0).mean(0)
 def objective(q):
  m=skin({name:[q[0],0,q[1]]});hand=m[i]@np.r_[tip,1]
  return np.r_[(hand[:3]-target)*10,q*.00001]
 q=least_squares(objective,[-65,(-1 if side=='left' else 1)*14],bounds=([-115,-45],[-10,45]),max_nfev=200).x
 carry[name]=[float(q[0]),0,float(q[1])]
 contacts[side]=(skin(carry)[i]@np.r_[tip,1])[:3].tolist()
print('Native straight-arm carry pose',carry,'hand ends',contacts)
def smooth(a,b,x):
 t=np.clip((x-a)/(b-a),0,1);return float(t*t*t*(t*(t*6-15)+10))
INTRO=7.;LOOP=12.;FPS=30;TURN_START=3.8

def pose(t):
 intro=t<INTRO;u=t if intro else (t-INTRO)%LOOP
 turn=smooth(TURN_START,6.25,t) if intro else 1.
 travel=min(t,4.15) if intro else 4.15
 # Integrate a constant walking speed with short cosine ease-in/out.
 speed=2.35/3.70
 if travel<.25:dist=speed*(travel/2-.25/(2*math.pi)*math.sin(math.pi*travel/.25))
 elif travel<3.50:dist=speed*(travel-.125)
 else:
  q=travel-3.50;dist=speed*(3.375+q/2+.65/(2*math.pi)*math.sin(math.pi*q/.65))
 walk=(1-smooth(3.35,4.2,t)) if intro else 0.
 phase=math.tau*(t-.12)/1.1
 r={k:list(v) for k,v in carry.items()}
 r['root']=[0,180+125*turn,0]
 look=(smooth(3.0,4.3,u)-smooth(7.0,8.4,u)) if not intro else 0.
 breathe=math.sin(math.tau*u/4) if not intro else math.sin(t*1.7)*(1-smooth(6,7,t))
 r['body']=[.15*breathe,0,0]
 r['head']=[2+22*look,15*turn,3*turn+.25*breathe]
 # Minecraft-style shoulder/hip swing; never deform or subdivide a limb.
 for side,offset in [('left',0),('right',math.pi)]:
  r[side+'_leg']=[21*math.cos(phase+offset)*walk,0,0]
 # Plant the lowest native sole on the path throughout each rigid-leg swing.
 m=skin(r);lowest=0.
 for side in ['left','right']:
  i=idx[side+'_leg'];base=verts[verts[:,12]==i][:36,:3]
  foot=np.c_[base,np.ones(len(base))]@m[i].T;lowest=min(lowest,float(foot[:,1].min()))
 bob=-lowest+.004*breathe
 m=skin(r,[.43,bob,-dist])
 # Shoulder-height dolly, small and deliberate, never orbits wildly.
 eye=np.array([.15,1.52-.05*turn,4.3-dist-1.35*turn]);target=np.array([-.37,1.15+.13*turn,-dist-.02])
 fov=40.-3.*turn
 # Reserved facial control remains zero: the original skin is never redrawn.
 bl=0.
 return m,np.r_[eye,target,fov,bl,dist]
frames=[]
for i in range(round((INTRO+LOOP)*FPS)+1):
 t=i/FPS
 # Keep the loop endpoint exactly equal to its beginning, including breath phases.
 if i==round((INTRO+LOOP)*FPS):t=INTRO
 m,extras=pose(t);frames.append(np.r_[m[:,:3,:].reshape(-1),extras])
arr=np.asarray(frames,dtype='<f4')
with (RES/'performance.anim').open('wb') as f:f.write(struct.pack('<4sIIIff',b'BMA1',len(arr),len(bones),FPS,INTRO,LOOP)+arr.tobytes());f.flush();os.fsync(f.fileno())
data={'fps':FPS,'intro_seconds':INTRO,'loop_seconds':LOOP,'frames':len(arr),'stride_floats':arr.shape[1],
'events':[{'time':t,'sound':'cinema_step_'+str(i+1),'volume':[.48,.46,.50,.47,.49,.46,.42,.34][i]} for i,t in enumerate([.12,.67,1.22,1.77,2.32,2.87,3.42,3.97])]+[{'time':4.1,'sound':'cinema_cloth','volume':.32},{'time':5.2,'sound':'cinema_chime','volume':.15}],
'loop_events':[{'time':3.7,'sound':'cinema_sand','volume':.26},{'time':8.3,'sound':'cinema_chime','volume':.15}],
'music':{'sound':'cinema_title_satellites','start_seconds':TURN_START,'gain':.68,'loop':True,'category':'music'},'final_facing':'screen_left','ambience_gain':.14,
'carry_pose':carry,'hand_contacts':contacts,'model':'native Minecraft slim; six rigid body parts','clock':'bounded monotonic visible-frame time; no reset on resize; no audio catch-up burst'}
(RES/'performance.json').write_text(json.dumps(data,indent=2))
print(arr.shape)
