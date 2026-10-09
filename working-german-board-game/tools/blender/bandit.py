"""Original bandit concepts. Blender 4.5: blender -b -t 8 -P tools/blender/bandit.py."""
import bpy, math
from pathlib import Path
from mathutils import Vector
OUT=Path(__file__).resolve().parents[2]/'docs/design/bandit'
OUT.mkdir(parents=True,exist_ok=True)
def mat(name,c,emission=0):
    m=bpy.data.materials.new(name); m.diffuse_color=(*c,1); m.use_nodes=True
    p=m.node_tree.nodes.get('Principled BSDF'); p.inputs['Base Color'].default_value=(*c,1); p.inputs['Roughness'].default_value=.78
    p.inputs['Emission Color'].default_value=(*c,1); p.inputs['Emission Strength'].default_value=emission
    return m
def cone(name,pos,r1,r2,h,m):
    bpy.ops.mesh.primitive_cone_add(vertices=16,radius1=r1,radius2=r2,depth=h,location=pos)
    o=bpy.context.object; o.name=name; o.data.materials.append(m)
    b=o.modifiers.new('Soft edges','BEVEL'); b.width=.025; b.segments=2
    o.modifiers.new('Normals','WEIGHTED_NORMAL'); return o
def sphere(name,pos,scale,m):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=20,ring_count=12,location=pos)
    o=bpy.context.object; o.name=name;o.scale=scale;o.data.materials.append(m)
    for p in o.data.polygons:p.use_smooth=True
    return o
def ring(name,pos,major,minor,m,rotation=None):
    bpy.ops.mesh.primitive_torus_add(major_segments=48,minor_segments=8,location=pos,major_radius=major,minor_radius=minor)
    o=bpy.context.object;o.name=name;o.data.materials.append(m)
    if rotation:o.rotation_euler=rotation
    return o
def light(name,pos,power,color,size):
    d=bpy.data.lights.new(name,'AREA');d.energy=power;d.color=color;d.shape='DISK';d.size=size
    o=bpy.data.objects.new(name,d);bpy.context.collection.objects.link(o);o.location=pos;o.rotation_euler=(Vector((0,0,.7))-o.location).to_track_quat('-Z','Y').to_euler()
for option in ['B1','B2','B3']:
    bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
    s=bpy.context.scene;s.render.engine='CYCLES';s.cycles.samples=48;s.cycles.use_denoising=True
    s.render.resolution_x=s.render.resolution_y=512;s.render.resolution_percentage=100;s.render.film_transparent=True
    s.world.color=(.18,.18,.18);s.view_settings.view_transform='AgX'
    dark=mat('Midnight charcoal',(.025,.032,.046));fold=mat('Cloak folds',(.055,.067,.09));black=mat('Face in shadow',(.006,.007,.01))
    pale=mat('Pale linen piping',(.78,.83,.86));gold=mat('Aged brass',(.32,.17,.045));warm=mat('Lantern amber',(1,.48,.075),3)
    cone('Flared cloak',(0,0,.65),.40,.21,1.17,dark)
    for x in [-.23,0,.23]:
        o=cone('Broad cloth fold',(x,-.16,.59),.09,.035,1.0,fold);o.rotation_euler[1]=-.15*x
    sphere('Shoulders',(0,0,1.13),(.30,.22,.21),dark)
    sphere('Hood',(0,-.01,1.48),(.255,.22,.31),dark)
    sphere('Deep hood opening',(0,-.208,1.48),(.177,.035,.213),black)
    lip=ring('Readable hood edge',(0,-.228,1.49),.205,.022,pale,(math.pi/2,0,0));lip.scale=(.86,1.1,1)
    # Pale left seam catches the upper-left key, preserving a large dark silhouette.
    seam=cone('Light-facing cloak edge',(-.30,-.12,.67),.027,.018,1.02,pale);seam.rotation_euler[1]=.15
    cone('Weighted hem',(0,0,.085),.41,.40,.085,dark)
    if option=='B2':
        sphere('Lantern arm',(.31,-.06,.94),(.16,.16,.13),dark)
        cone('Lantern glow core',(.48,-.16,.70),.12,.12,.29,warm)
        cone('Lantern roof',(.48,-.16,.89),.17,.065,.12,gold)
        cone('Lantern foot',(.48,-.16,.53),.15,.15,.045,gold)
        for x,y in [(-1,-1),(1,-1),(-1,1),(1,1)]:cone('Lantern frame',(.48+x*.093,-.16+y*.093,.70),.012,.012,.30,gold)
        ring('Handle',(.48,-.16,1.0),.08,.012,gold,(math.pi/2,0,0))
        light('Warm lantern bounce',(.48,-.4,.65),30,(1,.39,.065),.35)
    if option=='B3':
        cone('Obsidian shadow disc',(0,0,.035),.49,.49,.055,black)
        ring('Ivory luminous base',(0,0,.07),.455,.033,mat('Ivory glow',(.93,.78,.40),2))
    light('Upper left softbox',(-3,-4,6),450,(.86,.92,1),3)
    light('Pale back rim',(-2,2,3),350,(.80,.88,1),2)
    light('Front fill',(3,-4,2),65,(.75,.82,1),3)
    bpy.ops.object.camera_add(location=(0,-6,4.2));cam=bpy.context.object
    cam.rotation_euler=(Vector((0,0,.88))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.type='ORTHO';cam.data.ortho_scale=2.3;s.camera=cam
    s.render.image_settings.file_format='PNG';s.render.image_settings.color_mode='RGBA';s.render.filepath=str(OUT/f'{option}-render.png');bpy.ops.render.render(write_still=True)
