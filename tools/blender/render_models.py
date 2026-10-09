"""Own procedural palette. Blender 4.5: blender -b -P tools/blender/render_models.py.
Coordinates: pointy hex R=.93, ground z=0, screen-up = world +Y.
"""
import bpy, math, random, json, time
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/design/tiles-3d-models'
OUT.mkdir(parents=True, exist_ok=True)
R = .93
ORTHO = 2.65
timings = []

def material(name, color):
    m = bpy.data.materials.new(name); m.diffuse_color = (*color, 1)
    m.use_nodes = True
    p = m.node_tree.nodes.get('Principled BSDF')
    p.inputs['Base Color'].default_value = (*color, 1)
    p.inputs['Roughness'].default_value = .86
    return m

def mesh(name, vertices, faces, mat):
    data = bpy.data.meshes.new(name); data.from_pydata(vertices, [], faces); data.update()
    obj = bpy.data.objects.new(name, data); bpy.context.collection.objects.link(obj)
    obj.data.materials.append(mat)
    return obj

def cone(name, pos, radius, height, mat, n=8, top=0):
    bpy.ops.mesh.primitive_cone_add(vertices=n, radius1=radius, radius2=top, depth=height, location=(pos[0],pos[1],pos[2]+height/2))
    o=bpy.context.object; o.name=name; o.data.materials.append(mat)
    return o

def pebble(pos, scale, mat, smooth=False):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2 if smooth else 1, radius=1, location=pos)
    o=bpy.context.object; o.scale=scale; o.data.materials.append(mat)
    if smooth:
        for p in o.data.polygons: p.use_smooth=True
    return o

def setup():
    bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
    s=bpy.context.scene; s.render.engine='CYCLES'; s.cycles.samples=32
    s.cycles.use_denoising=True; s.render.resolution_x=s.render.resolution_y=512
    s.render.resolution_percentage=100; s.render.film_transparent=True
    s.render.image_settings.file_format='PNG'; s.render.image_settings.color_mode='RGBA'
    s.world.color=(.32,.36,.42)
    s.world.use_nodes=True; bg=s.world.node_tree.nodes.get('Background')
    bg.inputs['Color'].default_value=(.66,.75,.88,1); bg.inputs['Strength'].default_value=.65
    s.view_settings.view_transform='AgX'
    bpy.ops.object.light_add(type='AREA', location=(-3,4,7))
    bpy.context.object.data.energy=450; bpy.context.object.data.shape='DISK'; bpy.context.object.data.size=4
    bpy.ops.object.light_add(type='SUN', location=(-3,4,7))
    sun=bpy.context.object; sun.data.energy=1.5; sun.data.angle=.15
    sun.rotation_euler=(Vector((0,0,0))-sun.location).to_track_quat('-Z','Y').to_euler()
    bpy.ops.object.camera_add(); cam=bpy.context.object
    cam.data.type='ORTHO'; cam.data.ortho_scale=ORTHO; s.camera=cam
    return s,cam

def pigment(mat, amount=.18, frequency=12):
    nodes=mat.node_tree.nodes; links=mat.node_tree.links
    noise=nodes.new('ShaderNodeTexNoise'); noise.inputs['Scale'].default_value=frequency
    noise.inputs['Detail'].default_value=3
    ramp=nodes.new('ShaderNodeValToRGB'); c=mat.diffuse_color[:3]
    ramp.color_ramp.elements[0].color=(*(v*(1-amount) for v in c),1)
    ramp.color_ramp.elements[1].color=(*(min(1,v*(1+amount)) for v in c),1)
    links.new(noise.outputs['Fac'],ramp.inputs[0]); links.new(ramp.outputs['Color'],nodes.get('Principled BSDF').inputs['Base Color'])

def base(resource,style):
    dirt=material('earth edge',(.22,.15,.075))
    grass=material('moss ground',(.20,.29,.065) if resource=='timber' else (.29,.32,.22))
    if style==2: pigment(grass,.30,18); pigment(dirt,.18,9)
    verts=[]
    for z in [-.22,0]:
        verts.extend([(R*math.cos(math.pi/2+i*math.pi/3),R*math.sin(math.pi/2+i*math.pi/3),z) for i in range(6)])
    o=mesh('thick hex',verts,[tuple(reversed(range(6))),tuple(range(6,12))]+[(i,(i+1)%6,(i+1)%6+6,i+6) for i in range(6)],dirt)
    o.data.materials.append(grass); o.data.polygons[1].material_index=1
    mod=o.modifiers.new('tiny worn bevel','BEVEL'); mod.width=.018; mod.segments=2
    o.modifiers.new('weighted edge normals','WEIGHTED_NORMAL')

def tree(x,y,h,style,rng,mats):
    bark,greens=mats
    cone('visible trunk',(x,y,0),.025,h*.6,bark,7,top=.018)
    for j in range(3):
        z=h*(.22+j*.19); radius=h*(.32-j*.055)
        color=greens[(j+int(x*9))%len(greens)]
        if style==1:
            o=cone('pine canopy',(x,y,z),radius,h*(.49-j*.07),color,7,top=.008)
        else:
            # Integrated scalloped boughs: actual ring geometry, not stuck-on balls.
            n=24; verts=[]; faces=[]; hh=h*(.49-j*.07)
            for ring,(rr,zz) in enumerate([(1,0),(.92,.13),(.68,.42),(.38,.72),(.015,1)]):
                for k in range(n):
                    a=k*math.tau/n
                    scallop=1+.10*math.cos(a*8+j)+rng.uniform(-.025,.025)
                    verts.append((x+radius*rr*math.cos(a)*scallop,y+radius*rr*math.sin(a)*scallop,z+hh*zz+.013*math.cos(a*8)*rr))
            for ring in range(4):
                for k in range(n):
                    a=ring*n+k;b=ring*n+(k+1)%n
                    faces.append((a,b,b+n,a+n))
            faces.append(tuple(reversed(range(n))))
            o=mesh('sculpted pine boughs',verts,faces,color)
        o.rotation_euler.z=rng.random()*6
        if style==2:
            # Geometry uses absolute coordinates; do not rotate it around world origin.
            o.rotation_euler.z=0
            for p in o.data.polygons: p.use_smooth=True

def mountain(x,y,r,h,style,rng,rocks):
    n=7 if style==1 else 11; vs=[]
    for ring in range(3):
        for i in range(n):
            a=i*math.tau/n; rr=r*(1-ring*.32)*rng.uniform(.85,1.12)
            vs.append((x+rr*math.cos(a)+ring*.035,y+rr*math.sin(a),ring*h*.29+rng.uniform(0,.13 if style==2 else .08)*ring))
    vs.append((x+.09,y+(.11 if style==2 else .015),h)); faces=[]
    for ring in range(2):
        for i in range(n):
            a=ring*n+i;b=ring*n+(i+1)%n;c=b+n;d=a+n
            faces.extend([(a,b,c),(a,c,d)])
    faces.extend([(2*n+i,2*n+(i+1)%n,3*n) for i in range(n)])
    o=mesh('modelled crag',vs,faces,rocks[0])
    for m in rocks[1:]: o.data.materials.append(m)
    for p in o.data.polygons:
        z=sum(vs[v][2] for v in p.vertices)/len(p.vertices)
        p.material_index= len(rocks)-1 if z>h*.77 else (rng.randrange(len(rocks)-1) if style==1 else (1 if p.normal.x<0 else 2))

def build(resource,style):
    rng=random.Random(832 if resource=='timber' else 194)
    base(resource,style)
    bark=material('warm bark',(.24,.105,.035))
    greens=[material('pine '+str(i),c) for i,c in enumerate([(.055,.17,.045),(.10,.26,.055),(.23,.36,.075),(.34,.43,.12)])]
    rocks=[material('rock '+str(i),c) for i,c in enumerate([(.28,.32,.34),(.40,.44,.44),(.53,.56,.55),(.62,.64,.60),(.80,.82,.75)])]
    if style==2:
        for m in greens: pigment(m,.20,24)
        for m in rocks: pigment(m,.16,16)
    if resource=='timber':
        points=[(-.48,.24),(-.23,.51),(.06,.64),(.38,.38),(.55,.10),(-.57,-.10),(-.35,-.35),(.32,-.36),(.08,-.55),(-.12,.19),(.23,.15)]
        for x,y in points: tree(x,y,rng.uniform(.48,.78),style,rng,(bark,greens))
        # Real stacked logs, with pale circular cut ends.
        end=material('cut log ends',(.69,.47,.22))
        for j in range(3):
            o=cone('felled log',(-.19+j*.07,-.30,.04),.034,.30,bark,10,top=.034)
            o.rotation_euler.x=math.pi/2
            o=cone('end grain',(-.19+j*.07,-.45,.19),.032,.005,end,10,top=.032)
            o.rotation_euler.x=math.pi/2
    else:
        for x,y,r,h in [(-.27,.30,.38,1.03),(.24,.22,.34,.76),(.43,-.21,.26,.49),(-.45,-.22,.27,.43)]:
            mountain(x,y,r,h,style,rng,rocks)
        if style==2:
            for x,y,r,h in [(-.43,.46,.15,.52),(.03,.37,.15,.48),(.50,-.02,.12,.30),(-.33,-.42,.14,.23)]:
                mountain(x,y,r,h,style,rng,rocks)
        for x,y in [(-.58,.16),(.51,.29),(-.55,-.40),(.09,-.57)]:
            tree(x,y,.32,style,rng,(bark,greens))
    for i in range(28 if style==2 else 10):
        x=rng.uniform(-.65,.65);y=rng.uniform(-.6,.6)
        if abs(x)*.577+abs(y)>.87 or x*x+y*y<.08: continue
        pebble((x,y,.015),(.035,.045,.025),rng.choice(rocks[:3]),style==2)

def main():
    for style in [1,2]:
        for resource in ['timber','stone']:
            s,cam=setup(); build(resource,style)
            for name,tilt in [('flat',0),('mid',.48),('max',.96)]:
                cam.location=(0,-8*math.sin(tilt),8*math.cos(tilt))
                cam.rotation_euler=(Vector((0,0,0))-cam.location).to_track_quat('-Z','Y').to_euler()
                path=OUT/f'{resource}_V{style}_{name}.png'; s.render.filepath=str(path)
                start=time.monotonic(); bpy.ops.render.render(write_still=True)
                timings.append(dict(file=path.name,seconds=round(time.monotonic()-start,2),tilt=tilt))
                print('PALETTE_RENDER',timings[-1],flush=True)
    (OUT/'render-manifest.json').write_text(json.dumps(dict(blender=bpy.app.version_string,ortho_scale=ORTHO,radius=R,anchor=[256,256],renders=timings),indent=2))

if __name__=='__main__': main()
