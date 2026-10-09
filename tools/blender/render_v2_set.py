"""Full own V2 set and sheep-only animation layers. Blender 4.5, no external assets.
blender -b -t 8 -P tools/blender/render_v2_set.py -- [--terrain stone] [--skip-sheep]
"""
import sys, math, random, time, json, argparse
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import bpy
from mathutils import Vector
from render_models import OUT, R, ORTHO, setup, material, pigment, mesh, pebble, cone, tree, build

TAU=math.tau
TERRAINS=['timber','stone','fleece','grain','clay','desert']
angles=[('flat',0),('mid',.48),('max',.96)]
records=[]

def mat(name,c,noise=.15):
    m=material(name,c)
    if noise: pigment(m,noise,18)
    return m

def cube(name,pos,size,m,bevel=0):
    bpy.ops.mesh.primitive_cube_add(size=1,location=pos)
    o=bpy.context.object; o.name=name; o.scale=size; o.data.materials.append(m)
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    if bevel:
        mod=o.modifiers.new('soft handmade edge','BEVEL'); mod.width=bevel;mod.segments=2
        o.modifiers.new('weighted normals','WEIGHTED_NORMAL')
    return o

def pole(name,a,b,r,m,n=8):
    a=Vector(a);b=Vector(b);d=b-a
    o=cone(name,((a+b)/2-Vector((0,0,d.length/2))),r,d.length,m,n,top=r*.8)
    o.rotation_euler=d.to_track_quat('Z','Y').to_euler()
    return o

def extent(a):
    c=abs(math.cos(a));s=abs(math.sin(a))
    return min(math.sqrt(3)*R/2/max(c,1e-8),R/(s+c/math.sqrt(3)))

def field(name,fn,m,smooth=False,rings=25,n=96):
    vs=[(0,0,fn(0,0))]
    for ring in range(1,rings+1):
        for i in range(n):
            a=math.pi/2+i*TAU/n;r=extent(a)*ring/rings
            x=r*math.cos(a);y=r*math.sin(a)
            vs.append((x,y,fn(x,y)))
    fs=[(0,1+i,1+(i+1)%n) for i in range(n)]
    for ring in range(rings-1):
        for i in range(n):
            a=1+ring*n+i;b=1+ring*n+(i+1)%n;c=b+n;d=a+n
            fs.extend([(a,d,c),(a,c,b)])
    # Close the elevated terrain perimeter down to the common base, avoiding floating sheets.
    edge=1+(rings-1)*n;bottom=len(vs)
    vs.extend([(vs[edge+i][0],vs[edge+i][1],0) for i in range(n)])
    fs.extend([(edge+i,bottom+i,bottom+(i+1)%n,edge+(i+1)%n) for i in range(n)])
    o=mesh(name,vs,fs,m)
    # Correct winding: radial convention is counterclockwise but rings go outwards.
    for p in o.data.polygons: p.use_smooth=smooth
    return o

def ground(resource,color):
    # Reuse identical base geometry, replacing only its top terrain material.
    from render_models import base
    base(resource,2)
    o=bpy.data.objects['thick hex'];o.data.materials[1]=mat(resource+' ground',color,.24)

def segment(x,y,a,b,w):
    dx=b[0]-a[0];dy=b[1]-a[1]
    u=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy)))
    d=math.hypot(x-a[0]-u*dx,y-a[1]-u*dy)
    return (a[2]+u*(b[2]-a[2]))*math.exp(-2.8*(d/w)**1.45)

RIDGE=[(-.805,0,.13),(-.49,.13,.56),(-.25,.25,1.00),(.06,.14,.56),(.29,.16,.85),(.56,.03,.40),(.805,0,.13)]
SPURS=[([(-.25,.25,.80),(-.42,-.18,.39),(-.48,-.55,.13),(-.40,-.69,.035)],.24),
       ([(.29,.16,.68),(.35,-.22,.32),(.58,-.52,.10)],.22),
       ([(-.25,.25,.70),(-.26,.60,.30),(0,.91,.05)],.19),
       ([(.29,.16,.60),(.47,.49,.28),(.43,.68,.04)],.18),
       ([(.06,.14,.45),(-.02,-.35,.22),(.08,-.74,.045)],.23)]

def range_height(x,y):
    ridges=[segment(x,y,a,b,.34) for a,b in zip(RIDGE,RIDGE[1:])]
    for points,w in SPURS:
        ridges.extend(segment(x,y,a,b,w) for a,b in zip(points,points[1:]))
    h=max(ridges)
    # Low connected foothill bed, plus restrained erosional texture.
    bed=.055+.065*math.exp(-((x/.75)**2+(y/.8)**2))
    detail=.010*math.sin(x*43+y*17)*math.sin(y*37-x*9)*min(1,h/.2)
    return max(bed,h)+detail

def stone():
    ground('stone',(.30,.33,.24))
    rock=mat('weathered range rock',(.43,.47,.47),.22)
    o=field('single connected ridge saddle foothill mesh',range_height,rock,False,32,96)
    pale=mat('exposed high crags',(.66,.69,.66),.13)
    shadow=mat('slate fissure faces',(.32,.37,.37),.12)
    o.data.materials.append(pale);o.data.materials.append(shadow)
    for p in o.data.polygons:
        z=sum(o.data.vertices[v].co.z for v in p.vertices)/len(p.vertices)
        if z>.78: p.material_index=1
        elif p.normal.x>.55 and z>.25: p.material_index=2
    rng=random.Random(438);scree=mat('scree slope',(.43,.44,.40),.25)
    o.data.materials.append(scree)
    # Scree is material on the connected surface, not a second raised plane.
    for a,b,width in [((-.35,-.02),(-.54,-.43),.15),((.38,.03),(.62,-.28),.13)]:
        for p in o.data.polygons:
            x=sum(o.data.vertices[v].co.x for v in p.vertices)/len(p.vertices)
            y=sum(o.data.vertices[v].co.y for v in p.vertices)/len(p.vertices)
            u=(y-a[1])/(b[1]-a[1])
            if 0<u<1 and abs(x-a[0]-u*(b[0]-a[0]))<width*(.3+.7*u)*(.84+.16*math.sin(y*61)):
                p.material_index=3
        for j in range(28):
            u=rng.random();x=a[0]+u*(b[0]-a[0])+rng.uniform(-width,width)*u;y=a[1]+u*(b[1]-a[1])
            hit,loc,_,_=o.ray_cast(Vector((x,y,3)),Vector((0,0,-1)))
            pebble((x,y,(loc.z if hit else range_height(x,y))+.008),(.012,.015,.009),scree)
    greens=[mat('range pine',(.12,.25,.06))];bark=mat('range bark',(.22,.11,.04))
    for x,y in [(-.62,-.35),(.63,.27),(-.64,.34)]:
        before=set(bpy.context.scene.objects);tree(x,y,.23,2,rng,(bark,greens))
        for obj in set(bpy.context.scene.objects)-before: obj.location.z+=range_height(x,y)

def pasture(x,y):
    return .024+.10*math.exp(-((x+.40)**2+(y-.23)**2)/.17)+.065*math.exp(-((x-.4)**2+(y+.17)**2)/.18)

def sheep(x,y,heading=0,phase=0):
    start=set(bpy.context.scene.objects)
    wool=mat('cream wool',(.84,.80,.65),.07);dark=mat('sheep face legs',(.19,.17,.12),0)
    # Model around origin, then rotate and move the complete assembly.
    pebble((0,0,.104),(.103,.063,.063),wool,True)
    for i in range(9):
        a=i*TAU/9
        pebble((.052*math.cos(a),.045*math.sin(a),.124+.018*(i%2)),(.047,.039,.036),wool,True)
    graze=.5+.5*math.cos(phase)
    head=pebble((.109,0,.098-.033*graze),(.035,.031,.044),dark,True)
    for side in [-1,1]:
        pebble((.105,side*.039,.106-.02*graze),(.027,.010,.014),dark,True)
    for xx in [-.055,.054]:
        for yy in [-.038,.038]:
            pole('sheep leg',(xx,yy,.025),(xx+.006*math.sin(phase+xx*10),yy,.094),.009,dark)
    pebble((-.103,0,.095),(.022,.018,.021),wool,True)
    eye=mat('tiny sheep eye',(.025,.018,.01),0)
    for side in [-1,1]: pebble((.12,side*.028,.107-.03*graze),(.004,.004,.004),eye,True)
    z=pasture(x,y)
    for o in set(bpy.context.scene.objects)-start:
        loc=o.location.copy();o.location=(x+loc.x*math.cos(heading)-loc.y*math.sin(heading),y+loc.x*math.sin(heading)+loc.y*math.cos(heading),z+loc.z)
        o.rotation_euler.z+=heading

def fleece():
    ground('fleece',(.27,.40,.105));grass=mat('rolling pasture',(.28,.43,.12),.24)
    field('rolling grassy hills',pasture,grass,True)
    wall=mat('pasture stone wall',(.40,.40,.32),.13)
    for i in range(12):
        x=-.59+i*.075;y=.43+.10*math.sin(i/11*math.pi);z=pasture(x,y)
        for j in range(2):
            stone=pebble((x+(.024 if j else 0),y,z+.03+j*.044),(.047,.033,.031),wall)
            stone.rotation_euler.z=.15*math.sin(i*7+j)
    for x,y,h in [(-.38,.05,.1),(.33,.23,-.7),(-.35,-.37,.4)]: sheep(x,y,h,1.3)
    # One extra sheep is exclusively an animation layer, never baked twice.
    for i in range(28):
        a=i*2.4;x=.61*math.cos(a);y=.60*math.sin(a)
        if abs(x)*.577+abs(y)>.86: continue
        for j in range(2):
            pole('pasture grass tuft',(x,y,pasture(x,y)),(x+(j-.5)*.012,y+.01,pasture(x,y)+.045),.003,grass,5)

def wheat(x,y,h,m,rng):
    pole('wheat stem',(x,y,.015),(x+.006,y,h),.003,m,5)
    for j in range(3):
        for side in [-1,1]:
            o=pebble((x+side*.008,y,h-.022*j),(.012,.007,.020),m,True)
            o.rotation_euler.y=side*.40

def grain():
    ground('grain',(.43,.31,.085));soil=mat('field soil',(.28,.18,.07),.23)
    golds=[mat('wheat gold '+str(i),c,.10) for i,c in enumerate([(.60,.32,.035),(.78,.46,.08),(.91,.62,.16)])]
    rng=random.Random(129)
    for row in range(9):
        y=-.45+row*.12
        for col in range(14):
            x=-.61+col*.091
            if abs(x)*.577+abs(y)>.80 or (y<-.24 and x>.26):continue
            for j in range(3):
                wheat(x+(j-1)*.025,y+rng.uniform(-.018,.018),rng.uniform(.20,.28),golds[(row+col+j)%3],rng)
        # Raised soil furrow makes spacing readable even top-down.
        pole('row furrow',(-.62,y-.052,.001),(.62,y-.052,.001),.006,soil)
    rope=mat('sheaf ties',(.30,.17,.055),0)
    for x,y in [(.45,-.39),(.32,-.53)]:
        for j in range(10):
            a=j*TAU/10;r=.033
            pole('bundled wheat sheaf',(x+r*math.cos(a),y+r*math.sin(a),.018),(x+r*.7*math.cos(a),y+r*.7*math.sin(a),.24),.009,golds[j%3],6)
        cone('sheaf binding',(x,y,.105),.034,.022,rope,12,top=.034)
    wood=mat('cart oak',(.32,.17,.065),.15)
    cube('small field cart bed',(.48,-.22,.104),(.21,.15,.028),wood,.008)
    for yy in [-.30,-.14]:
        cube('cart side',(.48,yy,.147),(.21,.018,.07),wood,.004)
        bpy.ops.mesh.primitive_torus_add(major_radius=.048,minor_radius=.009,major_segments=16,minor_segments=6,location=(.48,yy,.059),rotation=(math.pi/2,0,0))
        bpy.context.object.data.materials.append(wood)
    for xx in [.38,.58]: pole('cart handle',(xx,-.24,.10),(xx,-.48,.06),.008,wood)

def clay_height(x,y):
    # Two terraced excavations, with exposed horizontal rims and broad earthen shoulders.
    h=.07+.09*math.exp(-((x+.30)**2+(y-.24)**2)/.20)
    for cx,cy,rx,ry in [(-.29,-.19,.38,.29),(.21,.33,.27,.25)]:
        d=math.sqrt(((x-cx)/rx)**2+((y-cy)/ry)**2)
        def rise(a,b):
            u=max(0,min(1,(d-a)/(b-a)));return u*u*(3-2*u)
        if d<1.08: h=.018+.037*rise(.43,.49)+.040*rise(.70,.76)+.045*rise(.99,1.07)
        elif d<1.17: h=max(h,.14)
    return h

def clay():
    ground('clay',(.48,.22,.09));earth=mat('terraced clay earth',(.54,.24,.105),.24)
    field('stepped clay pits',clay_height,earth,True,70,192)
    brick=mat('fired bricks',(.49,.15,.065),.20);mortar=mat('brick mortar',(.25,.15,.07),.12)
    # Kiln occupies the front-right bank; its chimney is also the smoke anchor.
    x=.43;y=-.16;z=clay_height(x,y)
    cube('kiln core',(x,y,z+.15),(.29,.25,.29),mortar,.018)
    for row in range(5):
        for j in range(4):
            xx=x-.108+j*.071+(row%2)*.012
            cube('kiln facade brick',(xx,y-.132,z+.029+row*.054),(.064,.018,.047),brick,.003)
    dark=mat('kiln opening',(.045,.024,.008),0)
    cube('dark kiln fire mouth',(x,y-.145,z+.081),(.097,.009,.10),dark,.024)
    ember=mat('small kiln embers',(.70,.19,.025),0)
    cube('ember bank',(x,y-.153,z+.046),(.075,.007,.016),ember,.004)
    cube('kiln cap',(x,y,z+.305),(.32,.28,.035),brick,.009)
    for row in range(6):
        cube('chimney brick course',(x+.055,y+.035,z+.34+row*.045),(.088,.087,.039),brick,.004)
    cube('chimney dark mouth',(x+.055,y+.035,z+.61),(.056,.056,.004),dark)
    for layer in range(3):
        for a in range(4):
            for b in range(2):
                xx=-.05+a*.069;yy=-.60+b*.04
                cube('stacked ready bricks',(xx,yy,clay_height(xx,yy)+.02+layer*.032),(.062,.034,.026),brick,.003)
    pole('pit work plank',(-.60,-.34,.15),(-.22,-.33,.095),.018,mat('pit plank',(.29,.15,.055)),6)

def dune_height(x,y):
    # Diagonal wind ridges, sharp leeward break and smooth windward shoulders.
    u=x*.85+y*.52
    wave=.5+.5*math.sin(9.8*u+.7*math.sin(y*3))
    q=max(abs(x)/(math.sqrt(3)*R/2),(abs(y)+abs(x)/math.sqrt(3))/R)
    edge=max(0,min(1,(q-.65)/.35));edge=edge*edge*(3-2*edge)
    return .045+.26*wave**2*(.80+.20*math.cos(y*2))*(1-.85*edge)

def desert():
    ground('desert',(.63,.43,.18));sand=mat('warm dune sand',(.70,.44,.18),.13)
    field('wind shaped dune ridges',dune_height,sand,True,32,128)
    rock=mat('desert rocks',(.40,.29,.18),.20);rng=random.Random(752)
    for x,y,s in [(-.57,.16,.10),(-.49,.25,.07),(.56,-.36,.08),(.35,.48,.09),(.43,.54,.06)]:
        pebble((x,y,dune_height(x,y)+s*.3),(s,s*.8,s*.75),rock)
    bark=mat('dead sun bleached tree',(.32,.22,.12),.15)
    x=.39;y=-.21;z=dune_height(x,y)
    pole('dead tree trunk',(x,y,z),(x-.04,y,z+.38),.025,bark)
    for a,b in [((x-.02,y,z+.18),(x-.16,y+.04,z+.28)),((x-.16,y+.04,z+.28),(x-.19,y+.04,z+.35)),((x-.04,y,z+.27),(x+.08,y-.035,z+.35)),((x-.04,y,z+.32),(x-.10,y,z+.44))]:
        pole('dead forked limb',a,b,.012,bark)
    for x,y in [(-.36,-.42),(.02,.61),(.57,.05)]:
        z=dune_height(x,y)
        for j in range(5):
            a=j*TAU/5
            pole('dry scrub twig',(x,y,z),(x+.05*math.cos(a),y+.05*math.sin(a),z+.052),.004,bark,5)

def camera(cam,tilt):
    cam.location=(0,-8*math.sin(tilt),8*math.cos(tilt))
    cam.rotation_euler=(Vector((0,0,0))-cam.location).to_track_quat('-Z','Y').to_euler()

def render(s,path,kind,**extra):
    s.render.filepath=str(path);start=time.monotonic();bpy.ops.render.render(write_still=True)
    r=dict(file=str(path.relative_to(OUT)),seconds=round(time.monotonic()-start,2),kind=kind,**extra)
    records.append(r);print('V2_RENDER',r,flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--terrain',choices=TERRAINS);parser.add_argument('--skip-sheep',action='store_true')
    parser.add_argument('--sheep-only',action='store_true')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    for resource in ([] if args.sheep_only else ([args.terrain] if args.terrain else TERRAINS)):
        if resource=='timber':
            # Mauro asked to keep Timber. Retain its already approved pixel assets.
            assert all((OUT/f'timber_V2_{name}.png').exists() for name,_ in angles)
            continue
        s,cam=setup();globals()[resource]()
        for name,tilt in angles:
            camera(cam,tilt);render(s,OUT/f'{resource}_V2_{name}.png','tile',terrain=resource,tilt=tilt)
    if not args.skip_sheep:
        folder=OUT/'animation-layers'/'fleece';folder.mkdir(parents=True,exist_ok=True)
        for i in range(16):
            s,cam=setup();s.render.resolution_x=s.render.resolution_y=512
            s.cycles.samples=24;camera(cam,.48)
            phase=TAU*i/16
            sheep(.19+.07*math.sin(phase),-.34+.025*math.cos(phase),-.25+.15*math.sin(phase),phase)
            render(s,folder/f'sheep_mid_{i:02d}.png','sheep-layer',frame=i,phase=phase)
        # Blender's bundled Python has no Pillow: crop the 512px raw RGBA buffer directly.
        # Stable 128px crop keeps all path phases and grazing poses at source tile detail.
        for i in range(16):
            path=folder/f'sheep_mid_{i:02d}.png';src=bpy.data.images.load(str(path),check_existing=False)
            px=list(src.pixels[:]);crop=bpy.data.images.new('sheep cropped layer',width=128,height=128,alpha=True)
            data=[]
            # Image buffers run bottom-to-top; top-left pixel crop is (232,232).
            for yy in range(152,280):
                data.extend(px[(yy*512+232)*4:(yy*512+360)*4])
            crop.pixels[:]=data;crop.filepath_raw=str(path);crop.file_format='PNG';crop.save()
            bpy.data.images.remove(src);bpy.data.images.remove(crop)
    manifest=OUT/'render-manifest-v2.json'
    prior=json.loads(manifest.read_text()) if manifest.exists() else {}
    replaced={r['file'] for r in records}
    old=[r for r in prior.get('renders',[]) if r['file'] not in replaced]
    manifest.write_text(json.dumps(dict(blender=bpy.app.version_string,ortho_scale=ORTHO,radius=R,anchor=[256,256],period_seconds=8,sheep_frames=16,sheep_layer_size=128,sheep_crop_origin=[232,232],renders=old+records),indent=2))

if __name__=='__main__':main()
