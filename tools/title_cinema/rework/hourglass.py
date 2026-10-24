"""Aurora hourglass reusable geometry. Local centre, 0.54 m high, +Z presentation side.
The same arrays are embedded in the game and exported as a standalone multi-material GLB.
"""
def build_hourglass():
    import math, numpy as np
    verts={'frame':[],'glass':[],'sand':[],'jewels':[]}
    def poly(group,points,color,mode,smooth=None):
        p=np.array(points,float);nn=np.cross(p[1]-p[0],p[2]-p[0]);nn/=np.linalg.norm(nn)
        for i in range(1,len(p)-1):
            for j in [0,i,i+1]:
                n=nn if smooth is None else smooth[j]
                verts[group].append([*p[j],*n,.02,.02,*color,-1,mode])
    def lathe(group,profile,color,mode,n=48,c=(0,0,0),smooth=True):
        rr=[np.array([[math.sin(a)*r,y,math.cos(a)*r] for a in np.linspace(0,math.tau,n,endpoint=False)])+c for y,r in profile]
        normals=[]
        for j,(y,r) in enumerate(profile):
            y1,r1=profile[max(0,j-1)];y2,r2=profile[min(len(profile)-1,j+1)];slope=-(r2-r1)/(y2-y1) if y1!=y2 else 0
            normals.append([np.array([math.sin(a),slope,math.cos(a)])/math.sqrt(1+slope*slope) for a in np.linspace(0,math.tau,n,endpoint=False)])
        for j in range(len(rr)-1):
            for i in range(n):
                k=(i+1)%n;poly(group,[rr[j][i],rr[j][k],rr[j+1][k],rr[j+1][i]],color,mode,[normals[j][i],normals[j][k],normals[j+1][k],normals[j+1][i]] if smooth else None)
        poly(group,list(reversed(rr[0])),color,mode);poly(group,rr[-1],color,mode)
    def tube(group,path,radius,color,mode,n=8):
        pp=np.array(path);rr=[]
        for j,p in enumerate(pp):
            y=pp[min(len(pp)-1,j+1)]-pp[max(0,j-1)];y/=np.linalg.norm(y);x=np.cross(y,[0,0,1])
            if np.linalg.norm(x)<.001:x=np.cross(y,[1,0,0])
            x/=np.linalg.norm(x);z=np.cross(x,y)
            rr.append([p+radius*(math.cos(a)*x+math.sin(a)*z) for a in np.linspace(0,math.tau,n,endpoint=False)])
        for j in range(len(rr)-1):
            for i in range(n):k=(i+1)%n;poly(group,[rr[j][i],rr[j+1][i],rr[j+1][k],rr[j][k]],color,mode)
    silver=(.80,.88,.98,1);gold=(.82,.66,.36,1);navy=(.09,.18,.34,1);blue=(.18,.65,1,1)
    for s in [-1,1]:
        # Bevelled stepped crown, dark enamel band and narrow gold inlay.
        profile=sorted([(s*y,r) for y,r in [( .217,.104),(.226,.132),(.235,.143),(.246,.143),(.254,.131),(.260,.13),(.267,.115)]])
        lathe('frame',profile,silver,2)
        lathe('frame',sorted([(s*.237,.144),(s*.249,.144)]),navy,2)
        for yy in [.235,.251]:lathe('frame',sorted([(s*(yy-.0015),.145),(s*(yy+.0015),.145)]),gold,2)
        # Twelve gilt tick marks and four raised sapphire crowns.
        for k in range(24):
            a=math.tau*k/24;x=math.sin(a)*.144;z=math.cos(a)*.144
            tube('frame',[(x,s*.238,z),(x,s*.247,z)],.0018,gold,2,4)
        for k in range(4):
            a=math.pi/4+math.tau*k/4;c=(math.sin(a)*.095,s*.264,math.cos(a)*.095)
            lathe('jewels',[(-.007,.008),(0,.015),(.014,.002)],blue,4,8,c,False)
        # Rosette cap with concentric engraved geometry.
        for r in [.035,.060,.09]:lathe('frame',sorted([(s*.267,r),(s*.269,r+.002)]),gold,2,n=48)
    for k in range(4):
        a=math.pi/4+k*math.pi/2
        path=[(math.sin(a)*r,y,math.cos(a)*r) for y,r in [(-.23,.110),(-.19,.119),(-.10,.129),(0,.138),(.10,.129),(.19,.119),(.23,.110)]]
        tube('frame',path,.0065,silver,2,10)
        # Smaller gold edge line and delicate leaf curls on each main column.
        tube('frame',[(x*1.052,y,z*1.052) for x,y,z in path],.0018,gold,2,6)
        for s in [-1,1]:
            y=s*.132
            curl=[]
            for q in np.linspace(0,1,14):
                ang=a+.19*math.sin(q*math.pi);r=.125+.011*math.sin(q*math.tau)
                curl.append((math.sin(ang)*r,y+s*q*.067,math.cos(ang)*r))
            tube('frame',curl,.0022,silver,2,6)
        # Faceted central sapphire with silver bezel.
        c=np.array([math.sin(a)*.14,0,math.cos(a)*.14]);u=np.array([math.cos(a),0,-math.sin(a)]);up=np.array([0,1,0]);front=np.array([math.sin(a),0,math.cos(a)])
        rim=[c+u*.014,c+up*.026,c-u*.014,c-up*.026]
        for j in range(4):poly('jewels',[rim[j],rim[(j+1)%4],c+front*.012],blue,4)
        tube('frame',rim+[rim[0]],.0022,gold,2,6)
    # Physical bulb contour, double shell, narrow neck and thick glass foot rims.
    profile=[(-.215,.063),(-.207,.079),(-.190,.091),(-.167,.096),(-.140,.091),(-.11,.073),(-.075,.046),(-.040,.021),(-.016,.0085),(0,.0065),(.016,.0085),(.040,.021),(.075,.046),(.110,.073),(.140,.091),(.167,.096),(.19,.091),(.207,.079),(.215,.063)]
    lathe('glass',profile,(.64,.85,.97,.15),3,64)
    for s in [-1,1]:lathe('glass',sorted([(s*.208,.064),(s*.215,.067),(s*.219,.065)]),(.70,.89,1,.35),3,48)
    # Two flowing blue sand heaps and fine luminous stream. Sand is a separate material.
    lathe('sand',[(.025,.003),(.052,.021),(.075,.039),(.10,.06),(.136,.085),(.141,.084)],(.21,.63,.96,1),7,48)
    lathe('sand',[(-.210,.065),(-.198,.079),(-.180,.079),(-.168,.062),(-.146,.037),(-.128,.002)],(.36,.78,1,1),7,48)
    tube('sand',[(0,-.136,0),(0,.039,0)],.0018,(.69,.91,1,1),1,8)
    for k in range(24):
        a=math.tau*k/24;r=.04+(k%4)*.008;y=-.198+(k%5)*.007
        lathe('sand',[(-.001,.0015),(.001,.0012)],(.66,.90,1,1),1,4,(math.sin(a)*r,y,math.cos(a)*r),False)
    return {k:np.asarray(v,dtype='<f4') for k,v in verts.items()}

def export_hourglass(out):
    import json,struct,numpy as np
    from pathlib import Path
    out=Path(out);out.mkdir(parents=True,exist_ok=True);data=build_hourglass();buf=bytearray();views=[];acc=[];prims=[]
    def accessor(a,typ):
        a=np.asarray(a,dtype='<f4');v=len(views);views.append({'buffer':0,'byteOffset':len(buf),'byteLength':a.nbytes,'target':34962});buf.extend(a.tobytes());idx=len(acc);acc.append({'bufferView':v,'componentType':5126,'count':len(a),'type':typ,'min':a.min(0).tolist(),'max':a.max(0).tolist()});return idx
    materials=[]
    for k,a in data.items():
        glass=k=='glass';mi=len(materials)
        materials.append({'name':k,'pbrMetallicRoughness':{'baseColorFactor':[1,1,1,1],'metallicFactor':.78 if k=='frame' else .0,'roughnessFactor':.19 if glass else .30 if k=='frame' else .42},'alphaMode':'BLEND' if glass else 'OPAQUE','doubleSided':glass,**({'extensions':{'KHR_materials_ior':{'ior':1.46},'KHR_materials_transmission':{'transmissionFactor':.38}}} if glass else {})})
        prims.append({'attributes':{'POSITION':accessor(a[:,:3],'VEC3'),'NORMAL':accessor(a[:,3:6],'VEC3'),'COLOR_0':accessor(a[:,8:12],'VEC4')},'material':mi})
    g={'asset':{'version':'2.0','generator':'Botand Aurora hourglass 1.0'},'scene':0,'scenes':[{'nodes':[0]}],'nodes':[{'name':'Aurora_Hourglass','mesh':0}],'meshes':[{'name':'Reusable_Hourglass','primitives':prims}],'materials':materials,'extensionsUsed':['KHR_materials_ior','KHR_materials_transmission'],'buffers':[{'byteLength':len(buf)}],'bufferViews':views,'accessors':acc,'extras':{'units':'metres','height':.546,'origin':'centre','front':'+Z','groups':list(data),'source':'hourglass.py'}}
    raw=json.dumps(g,separators=(',',':')).encode();raw+=b' '*((-len(raw))%4)
    payload=struct.pack('<4sII',b'glTF',2,28+len(raw)+len(buf))+struct.pack('<I4s',len(raw),b'JSON')+raw+struct.pack('<I4s',len(buf),b'BIN\0')+buf
    import os
    with (out/'Aurora_Hourglass.glb').open('wb') as f:f.write(payload);f.flush();os.fsync(f.fileno())
    (out/'hourglass_manifest.json').write_text(json.dumps({'height_m':.546,'triangles':{k:len(v)//3 for k,v in data.items()},'reusable':True,'material_groups':list(data)},indent=2))
    # Generic Blockbench mesh project, with editable frame/glass/sand/jewel groups.
    # Pixel swatches preserve material colors without external texture dependencies.
    import uuid,io,base64
    from PIL import Image
    colors=[]
    for a in data.values():
        for c in a[:,8:12]:
            key=tuple(int(round(float(v)*255)) for v in c)
            if key not in colors:colors.append(key)
    atlas=Image.new('RGBA',(64,64));
    for i,c in enumerate(colors):atlas.putpixel((i%64,i//64),c)
    b=io.BytesIO();atlas.save(b,format='PNG');png=b.getvalue()
    def uid(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'botand/aurora-hourglass/'+s))
    elements=[];groups=[]
    for name,a in data.items():
        vs={f'v{i}':(v[:3]*16).astype(float).tolist() for i,v in enumerate(a)};faces={}
        for i in range(0,len(a),3):
            col=tuple(int(round(float(v)*255)) for v in a[i,8:12]);ix=colors.index(col);keys=[f'v{i+j}' for j in range(3)]
            faces[f'f{i//3}']={'vertices':keys,'uv':{k:[ix%64+.5,ix//64+.5] for k in keys},'texture':0}
        elements.append({'type':'mesh','name':name,'uuid':uid(name),'origin':[0,0,0],'vertices':vs,'faces':faces,'rotation':[0,0,0],'visibility':True,'export':True})
        groups.append({'name':name,'origin':[0,0,0],'uuid':uid('group/'+name),'children':[uid(name)]})
    bb={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':'Aurora Hourglass','resolution':{'width':64,'height':64},'elements':elements,'outliner':groups,'textures':[{'name':'hourglass_palette.png','id':'0','uuid':uid('palette'),'width':64,'height':64,'uv_width':64,'uv_height':64,'source':'data:image/png;base64,'+base64.b64encode(png).decode()}]}
    with (out/'Aurora_Hourglass.bbmodel').open('wb') as f:f.write(json.dumps(bb,separators=(',',':')).encode());f.flush();os.fsync(f.fileno())
    with (out/'hourglass_palette.png').open('wb') as f:f.write(png);f.flush();os.fsync(f.fileno())
if __name__=='__main__':
    from pathlib import Path
    export_hourglass(Path(__file__).parent/'output')
