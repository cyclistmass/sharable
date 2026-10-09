"""One original angry skeleton corsair. Own meshes, no external models.
Run one batch/process: --batch portrait | tiles. Transparent 512px sprites.
"""
import sys,math,random,argparse,json,time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import bpy
from mathutils import Vector,Matrix
from render_models import setup,mesh,pebble,cone
from render_v2_set import mat,cube,pole,camera
OUT=Path(__file__).resolve().parents[2]/'docs/design/bandit/v1'

def model():
    rng=random.Random(905);bone=mat('warm ivory pirate bones',(.83,.77,.60),.10)
    dark=mat('deep skull sockets',(.028,.018,.015),0);coat=mat('ragged mulberry pirate coat',(.13,.035,.045),.16)
    lapel=mat('aged teal lapels',(.035,.105,.115),.14);hair=mat('wild charcoal afro',(.035,.025,.019),.12)
    straw=mat('weathered ochre woven straw',(.57,.37,.12),.16);strawlit=mat('straw frayed fibres',(.76,.56,.23),.10)
    brass=mat('tarnished brass buckle and hilt',(.55,.33,.08),.10);steel=mat('cutlass pale steel',(.54,.61,.61),.04)
    steel.node_tree.nodes.get('Principled BSDF').inputs['Metallic'].default_value=.65
    boots=mat('worn pirate boots',(.065,.043,.030),.10)
    # Squat, wide character proportions keep the skull readable at board size.
    for side in [-1,1]:
        pebble((side*.105,-.06,.075),(.095,.16,.075),boots,True)
        pole('shin bone',(side*.105,0,.10),(side*.10,0,.42),.035,bone,10)
    vs=[];n=18
    for ring,(z,r,ry) in enumerate([(.30,.25,.145),(.68,.22,.14),(.92,.24,.13),(.99,.15,.10)]):
        for i in range(n):
            a=i*math.tau/n;zz=z+(.045*math.sin(i*4.1) if ring==0 else 0)
            vs.append((r*math.cos(a),ry*math.sin(a),zz))
    fs=[]
    for ring in range(3):
        for i in range(n):fs.append((ring*n+i,ring*n+(i+1)%n,(ring+1)*n+(i+1)%n,(ring+1)*n+i))
    mesh('tattered open pirate coat',vs,fs,coat)
    for side in [-1,1]:
        mesh('wide weathered lapel',[(side*.03,-.148,.67),(side*.18,-.12,.92),(side*.07,-.13,.97)],[(0,1,2)],lapel)
    # Exposed ribs, belt and mismatched shoulder armour; no formal suit or waistcoat.
    for z in [.70,.76,.82,.88]:pole('exposed rib',( -.075,-.154,z),(.075,-.154,z+.014),.013,bone,8)
    cube('pirate leather belt',(0,-.14,.60),(.38,.027,.07),boots,.008)
    cube('brass belt buckle',(0,-.17,.60),(.08,.02,.07),brass,.006)
    pole('neck bone',(0,0,.94),(0,0,1.08),.040,bone,10)
    # Oversized skull, cheeks and a visibly angry toothy grin facing camera (-Y).
    before_head=set(bpy.context.scene.objects)
    pebble((0,-.018,1.24),(.185,.14,.19),bone,True)
    for side in [-1,1]:
        pebble((side*.135,-.087,1.17),(.063,.067,.067),bone,True)
        socket=pebble((side*.075,-.144,1.264),(.057,.025,.047),dark,True)
        socket.rotation_euler.y=side*-.24
        # Heavy inward-sloping brow, deliberately low over the inner eye corners.
        pole('angry diagonal bone brow',(side*.025,-.169,1.288),(side*.137,-.142,1.323),.025,bone,10)
    mesh('triangular skull nose',[(-.026,-.162,1.205),(.026,-.162,1.205),(0,-.171,1.248)],[(0,1,2)],dark)
    pebble((0,-.12,1.112),(.135,.065,.055),bone,True)
    pebble((0,-.172,1.142),(.118,.014,.041),dark,True)
    for i in range(7):
        xx=(i-3)*.028;zz=1.143+.017*(abs(i-3)/3)
        cube('upper grinning tooth',(xx,-.189,zz+.017),(.023,.017,.031),bone,.003)
        cube('lower grinning tooth',(xx,-.188,zz-.015),(.022,.017,.023),bone,.003)
    pivot=Vector((0,-.018,1.24))
    pose=Matrix.Translation(pivot)@Matrix.Rotation(math.radians(-30),4,'X')@Matrix.Translation(-pivot)
    for obj in set(bpy.context.scene.objects)-before_head:obj.matrix_world=pose@obj.matrix_world
    # Dense wild afro below a broad, battered straw brim, asymmetrical curls.
    pebble((0,.07,1.26),(.265,.18,.16),hair,True)
    for i in range(48):
        a=i*math.tau/48;r=.26+rng.uniform(-.025,.04);z=1.255+rng.uniform(-.08,.09)
        if math.sin(a)<-.65:continue
        size=rng.uniform(.058,.086)
        pebble((r*math.cos(a),r*.74*math.sin(a)+.035,z),(size,size*.85,size*1.15),hair,True)
    def hat_y(a,r):return r*(.30 if math.sin(a)<0 else .72)*math.sin(a)+.13
    n=72;vs=[]
    for radius in [.17,.29,.46]:
        for i in range(n):
            a=i*math.tau/n;z=1.395+.036*math.sin(a*2+.5)*(radius/.46)+.015*math.sin(7*a)
            vs.append((radius*math.cos(a),hat_y(a,radius),z))
    fs=[]
    for ring in range(2):
        for i in range(n):fs.append((ring*n+i,ring*n+(i+1)%n,(ring+1)*n+(i+1)%n,(ring+1)*n+i))
    brim=mesh('broad frayed straw brim',vs,fs,straw);solid=brim.modifiers.new('straw brim thickness','SOLIDIFY');solid.thickness=.022
    cone('low rounded straw crown',(0,.13,1.39),.205,.14,straw,32,top=.15)
    cone('dark weathered hatband',(0,.13,1.415),.206,.038,coat,32,top=.194)
    for i in range(56):
        a=i*math.tau/56;rr=.46+rng.uniform(.01,.05)
        pole('ragged loose straw fibre',(.43*math.cos(a),hat_y(a,.43),1.393),(rr*math.cos(a),hat_y(a,rr),1.376+rng.uniform(-.018,.020)),.003,strawlit,4)
    for radius in [.23,.30,.37,.43]:
        for i in range(36):
            a=i*math.tau/36;b=(i+1)*math.tau/36
            pole('woven straw brim ring',(radius*math.cos(a),hat_y(a,radius),1.416),(radius*math.cos(b),hat_y(b,radius),1.416),.0025,strawlit,4)
    # One arm gestures angrily; the other brandishes a curved cutlass.
    for side in [-1,1]:
        a=(side*.21,0,.91);b=(side*.32,-.02,.76);c=(side*.37,-.12,.87 if side==1 else .66)
        pole('ragged coat sleeve',a,b,.075,coat,10);pole('exposed forearm',b,c,.031,bone,10);pebble(c,(.046,.036,.04),bone,True)
    pole('cutlass leather grip',(.37,-.12,.85),(.46,-.12,.96),.025,boots,10)
    pole('cutlass brass guard',(.395,-.13,1.00),(.51,-.13,.89),.022,brass,10)
    blade=[]
    for i in range(13):
        t=i/12;xx=.46+.52*t-.22*t*t;zz=.97+.38*t+.05*t*t
        dx=.52-.44*t;dz=.38+.10*t;length=math.hypot(dx,dz);width=.055*(1-.90*t)
        for side in [-1,1]:blade.append((xx-side*dz/length*width,-.12,zz+side*dx/length*width))
    o=mesh('curved broad cutlass',blade,[(i*2,i*2+1,i*2+3,i*2+2) for i in range(12)],steel);mod=o.modifiers.new('blade thickness','SOLIDIFY');mod.thickness=.018
    # Shared ground anchor and a deliberate 35% nominal hex-width silhouette.
    for obj in [o for o in bpy.context.scene.objects if o.type=='MESH']:
        obj.location*=.46;obj.scale*=.46
    return {'own_original_design':True,'straw_hat':True,'afro':True,'cutlass':True,'model_scale':.46,'foot_anchor':[0,0,0]}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--batch',choices=['portrait','tiles'],required=True)
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:]);OUT.mkdir(parents=True,exist_ok=True)
    scene,cam=setup();scene.render.threads_mode='FIXED';scene.render.threads=2;metadata=model();records=[]
    if args.batch=='portrait':jobs=[('front',math.radians(18),0),('threequarter',math.radians(18),math.radians(-28))]
    else:jobs=[(f'tilt_{i:02d}',.96*i/7,0) for i in range(8)]+[('flat',0,0),('mid',.48,0),('max',.96,0)]
    for name,tilt,azimuth in jobs:
        if args.batch=='portrait':
            target=Vector((.04,0,.36));cam.location=target+Vector((3*math.sin(azimuth),-3*math.cos(azimuth),1.1));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=.92
        else:camera(cam,tilt)
        path=OUT/f'bandit_v1_{name}.png';scene.render.filepath=str(path);start=time.monotonic();bpy.ops.render.render(write_still=True)
        records.append({'file':path.name,'tilt_radians':tilt,'render_seconds':round(time.monotonic()-start,2)})
    (OUT/f'{args.batch}-manifest.json').write_text(json.dumps({'metadata':metadata,'renders':records},indent=2))
if __name__=='__main__':main()
