"""Own R3 Alpine-chain evolution: seven summits, periodic shared shoulders.

Pure height functions can be checked without Blender. build() uses the existing V2
base, trees, light/camera helpers. Historical R1/R2/R3 studies remain untouched.
"""
import math,random,hashlib,json

R=.93;WIDTH=math.sqrt(3)*R;ROW=1.5*R
# First four are the selected R3 study. Three new summits have distinct heights.
PEAKS=[(-.51,.15,.78),(-.16,.20,1.08),(.19,.14,.92),(.51,.06,.70),
       (-.47,-.22,.65),(.02,-.31,.74),(.35,.39,.79)]
LINKS=[(0,1),(1,2),(2,3),(0,4),(4,5),(5,2),(2,6),(6,3)]
OFFSETS=[(WIDTH*(c+r/2),ROW*r) for r in range(-2,3) for c in range(-2,3)]

def ridge(x,y,a,b):
    dx=b[0]-a[0];dy=b[1]-a[1];den=dx*dx+dy*dy
    u=((x-a[0])*dx+(y-a[1])*dy)/den
    if not 0<=u<=1:return 0
    distance=math.hypot(x-a[0]-u*dx,y-a[1]-u*dy)
    saddle=(a[2]*(1-u)+b[2]*u)*.76*(1-.24*math.sin(math.pi*u)**2)
    return saddle*max(0,1-distance/.17)

def local_height(x,y):
    # Repeated shoulder exits stay at the same heights on either side of a shared edge.
    z=0
    for px,py,h in PEAKS:
        z=max(z,h*max(0,1-math.hypot(x-px,(y-py)*1.10)/.48))
    for a,b in LINKS:z=max(z,ridge(x,y,PEAKS[a],PEAKS[b]))
    return z

def height(x,y):
    # Periodic R3 east-west shoulder + lower diagonal foothill connection.
    # Summits near an edge are completed by the adjacent instance, not faded to zero.
    row_distance=abs((y-.10+ROW/2)%ROW-ROW/2)
    shoulder=.54*max(0,1-row_distance/.59)
    diagonal=(y-math.sqrt(3)*x-.10+ROW)%(2*ROW)-ROW
    spur=.26*max(0,1-abs(diagonal)/.46)
    z=max(shoulder,spur,.018)
    for ox,oy in OFFSETS:
        xx=x-ox;yy=y-oy
        if abs(xx)>1.25 or abs(yy)>1.0:continue
        z=max(z,local_height(xx,yy))
    return z

def snowy(x,y,z):
    for ox,oy in OFFSETS:
        for px,py,h in PEAKS:
            d=math.hypot(x-ox-px,(y-oy-py)*1.1)
            if d<.48*.23 and z>h*.76+.012*math.sin(38*x+19*y) and z>.54:return True
    return False

def continuity():
    # Equivalent world positions on both sides of horizontal and diagonal shared edges.
    errors=[]
    for i in range(33):
        y=-R/2+R*i/32
        errors.append(abs(height(WIDTH/2,y)-height(-WIDTH/2,y)))
        x=WIDTH/2*i/32;y=R-x/math.sqrt(3)
        errors.append(abs(height(x,y)-height(x-WIDTH/2,y-ROW)))
        x=-WIDTH/2*i/32;y=R+x/math.sqrt(3)
        errors.append(abs(height(x,y)-height(x+WIDTH/2,y-ROW)))
    assert max(errors)<1e-9,max(errors)
    return {'shared_edge_samples':99,'max_height_error':max(errors),
            'physical_lattice_width':WIDTH,'physical_lattice_row':ROW}

def build():
    import bpy
    from render_models import base,mesh,pebble,tree,material,pigment
    rng=random.Random(194);base('stone',2)
    def mat(name,c,noise=.20):
        m=material(name,c);pigment(m,noise,18);return m
    rocks=[mat('R3 warm dark bedrock',(.23,.15,.085),.27),
           mat('R3 warm cleft shading',(.145,.095,.053)),
           mat('R3 warm scree',(.30,.205,.125),.22),
           mat('R3 warm lit crags',(.28,.195,.11))]
    snow=mat('R3 alpine snow caps',(.89,.92,.88),.06)
    meadow=mat('R3 pine foothill earth',(.18,.23,.085),.22)
    # The skirt closes cut shoulders against the original thick hex, with no extra base band.
    n=32;verts=[];ids={};faces=[]
    for q in range(-n,n+1):
        for r in range(-n,n+1):
            edge=max(abs(q),abs(r),abs(q+r))
            if edge>n:continue
            x=WIDTH/2*q/n;y=R*(r+q/2)/n
            if edge<n:x+=rng.uniform(-.009,.009);y+=rng.uniform(-.009,.009)
            ids[q,r]=len(verts);verts.append((x,y,height(x,y)+.004))
    # Some valid boundary triangles have an anchor outside the hex. Iterate the square,
    # then validate all three vertices; iterating only ids leaves a sawtooth mesh gap.
    for q,r in ((q,r) for q in range(-n,n+1) for r in range(-n,n+1)):
        for tri in [[(q,r),(q+1,r),(q,r+1)],[(q+1,r),(q+1,r+1),(q,r+1)]]:
            if all(p in ids for p in tri):faces.append(tuple(ids[p] for p in tri))
    assert len(faces)==6*n*n,'Full hex tessellation must include every boundary triangle'
    boundary=[i for (q,r),i in ids.items() if max(abs(q),abs(r),abs(q+r))==n]
    boundary.sort(key=lambda i:math.atan2(verts[i][1],verts[i][0]))
    floor={i:len(verts)+j for j,i in enumerate(boundary)}
    verts.extend((verts[i][0],verts[i][1],0) for i in boundary)
    top_faces=len(faces)
    for j,i in enumerate(boundary):
        k=boundary[(j+1)%len(boundary)];faces.append((i,floor[i],floor[k],k))
    o=mesh('R3 seven-peak Alpine chain connected bedrock',verts,faces,rocks[0])
    for m in rocks[1:]+[snow,meadow]:o.data.materials.append(m)
    for p in o.data.polygons:
        x,y,z=[sum(verts[v][k] for v in p.vertices)/len(p.vertices) for k in range(3)]
        p.use_smooth=z<.16
        if p.index>=top_faces:p.material_index=1
        elif snowy(x,y,z):p.material_index=4
        elif z<.14:p.material_index=5
        elif p.normal.x>.34 and p.normal.z<.78:p.material_index=1
        elif z<.37:p.material_index=2
        else:p.material_index=3 if p.normal.x<-.15 else 0
    bark=mat('R3 pine bark',(.24,.105,.035))
    greens=[mat('R3 pine canopy '+str(i),c) for i,c in enumerate([(.055,.17,.045),(.10,.26,.055),(.23,.36,.075),(.34,.43,.12)])]
    for x,y,h in [(-.59,-.35,.23),(.52,-.39,.23),(.10,-.65,.23),(-.66,-.48,.16),(.61,-.52,.18)]:
        before=set(bpy.context.scene.objects);tree(x,y,h,2,rng,(bark,greens))
        for obj in set(bpy.context.scene.objects)-before:obj.location.z+=height(x,y)+.004
    stones=0
    for i in range(115):
        x=rng.uniform(-.74,.74);y=rng.uniform(-.80,.69)
        if abs(y)+abs(x)/math.sqrt(3)>.87:continue
        z=height(x,y)
        if z>.51:continue
        size=rng.uniform(.009,.028)
        pebble((x,y,z+.012),(size,size*.8,size*.65),rocks[2],True);stones+=1
    return {'selected_study':'R3 Alpine chain','original_summits':4,'added_summits':3,
            'summits':PEAKS,'scree_stones':stones,'pines':5,'mesh_vertices':len(verts),
            'ridge_vertices_sha256':hashlib.sha256(json.dumps(verts).encode()).hexdigest(),
            'continuity':continuity()}
