"""Stone range studies; run with Blender 4.5 -b -P this file.

Only writes to docs/design/tiles-3d-models/range-options.
Uses the V2 helpers without executing their render loop.
"""
import bpy, math, random, json, time, ast
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / 'tools/blender/render_models.py').read_text()
parsed = ast.parse(source)
helpers = ast.Module(body=[node for node in parsed.body if isinstance(node, (ast.Import, ast.ImportFrom, ast.FunctionDef, ast.Assign))], type_ignores=[])
exec(compile(helpers, 'v2_helpers', 'exec'))
OUT = ROOT / 'docs/design/tiles-3d-models/range-options'
OUT.mkdir(parents=True, exist_ok=True)

def field(x, y, option):
    # Continuous bedrock: every summit belongs to the same shoulder surface.
    peaks = {
        'R1': [(-.52,.12,.70),(-.08,.18,1.02),(.48,.12,.78)],
        'R2': [(-.10,.16,1.08),(.32,.17,.68),(-.39,-.10,.58),(.19,-.30,.48)],
        'R3': [(-.51,.15,.78),(-.16,.20,1.08),(.19,.14,.92),(.51,.06,.70)],
    }[option]
    width = .62 if option == 'R2' else .48
    summits = [max(0,h*(1-math.hypot(x-px,(y-py)*1.10)/width)) for px,py,h in peaks]
    # A broad continuous east-west shoulder and descending radial spurs.
    shoulder = .54 * max(0,1-abs(y-.10)/.59) * max(0,1-(abs(x)/1.03)**4)
    if option == 'R2':
        shoulder = .49 * max(0,1-math.hypot(x+.06,y-.06)/.91)
    spurs = 0
    for px,py,h in peaks:
        for angle in [-2.1,-.9,1.2]:
            dx,dy=x-px,y-py
            along=dx*math.cos(angle)+dy*math.sin(angle)
            across=abs(-dx*math.sin(angle)+dy*math.cos(angle))
            spurs=max(spurs,.32*max(0,1-across/.15)*max(0,1-along/.64) if along>0 else 0)
    z=max(max(summits),shoulder,spurs)
    # Taper bedrock to ground at the exact hex perimeter.
    edge=max(abs(x)/(.893*math.sqrt(3)/2), (abs(y)+abs(x)/math.sqrt(3))/.893)
    z*=min(1,max(0,(1-edge)/.12))
    return max(0,z)

def build_range(option):
    rng=random.Random(194)
    base('stone',2)
    rocks=[material('range rock '+str(i),c) for i,c in enumerate([(.28,.32,.34),(.40,.44,.44),(.53,.56,.55),(.62,.64,.60)])]
    for m in rocks: pigment(m,.16,16)
    snow=material('alpine snow',(.88,.91,.93)); pigment(snow,.06,22)
    n=16; verts=[]; ids={}
    for q in range(-n,n+1):
        for r in range(-n,n+1):
            if max(abs(q),abs(r),abs(q+r))>n: continue
            x=R*.96*math.sqrt(3)/2*q/n; y=R*.96*(r+q/2)/n
            if max(abs(q),abs(r),abs(q+r))<n:
                x+=rng.uniform(-.014,.014); y+=rng.uniform(-.014,.014)
            ids[q,r]=len(verts); verts.append((x,y,field(x,y,option)+.004))
    faces=[]
    for q,r in ids:
        for tri in [[(q,r),(q+1,r),(q,r+1)],[(q+1,r),(q+1,r+1),(q,r+1)]]:
            if all(p in ids for p in tri):
                faces.append(tuple(ids[p] for p in tri))
    obj=mesh(option+' connected bedrock and shoulders',verts,faces,rocks[0])
    for m in rocks[1:]+[snow]: obj.data.materials.append(m)
    obj.data.materials.append(bpy.data.materials.get('moss ground'))
    for p in obj.data.polygons:
        z=sum(verts[v][2] for v in p.vertices)/3
        p.use_smooth = z < .18
        p.material_index=5 if z<.045 else 4 if option=='R3' and z>.68+ .045*math.sin(p.center.x*25) else (1 if p.normal.x<-.15 else 2 if p.normal.x>.2 else 3)
    bark=material('warm bark',(.24,.105,.035))
    greens=[material('pine '+str(i),c) for i,c in enumerate([(.055,.17,.045),(.10,.26,.055),(.23,.36,.075),(.34,.43,.12)])]
    for m in greens: pigment(m,.20,24)
    for x,y in [(-.59,-.35),(.52,-.39),(.10,-.65)]:
        # Translate V2 tree geometry to the local foothill surface.
        before=set(bpy.context.scene.objects)
        tree(x,y,.23,2,rng,(bark,greens))
        for o in set(bpy.context.scene.objects)-before: o.location.z+=field(x,y,option)
    for i in range(65 if option=='R3' else 28):
        x=rng.uniform(-.70,.70); y=rng.uniform(-.70,.55)
        if abs(y)+abs(x)/math.sqrt(3)>.83: continue
        z=field(x,y,option)
        if z>.37: continue
        size=rng.uniform(.012,.033)
        pebble((x,y,z+.015),(size,size*.8,size*.65),rng.choice(rocks[:3]),True)

renders=[]
for option in ['R1','R2','R3']:
    s,cam=setup(); build_range(option)
    objects=[o for o in s.objects if o.type=='MESH']
    for name,degrees in [('flat',0),('mid',27.5),('max',55)]:
        tilt=math.radians(degrees)
        cam.location=(0,-8*math.sin(tilt),8*math.cos(tilt))
        cam.rotation_euler=(-cam.location).to_track_quat('-Z','Y').to_euler()
        s.render.filepath=str(OUT/f'{option}_{name}.png')
        bpy.ops.render.render(write_still=True)
        renders.append(dict(file=Path(s.render.filepath).name,degrees=degrees))
    # Nine edge-sharing pointy hexes; preserve the terrain orientation.
    for col in [-1,0,1]:
        for row in [-1,0,1]:
            if col==row==0: continue
            offset=Vector((math.sqrt(3)*R*(col+row/2),1.5*R*row,0))
            for original in objects:
                copy=original.copy(); copy.data=original.data
                bpy.context.collection.objects.link(copy); copy.location+=offset
    cam.data.ortho_scale=8.5
    s.render.filepath=str(OUT/f'{option}_patch_55.png')
    bpy.ops.render.render(write_still=True)
    renders.append(dict(file=Path(s.render.filepath).name,degrees=55,tiles=9))
(OUT/'render-manifest.json').write_text(json.dumps(dict(blender=bpy.app.version_string,radius=R,thickness=.22,single_ortho=ORTHO,patch_ortho=8.5,renders=renders),indent=2))
