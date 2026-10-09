"""Shared number-plate reservation, in lattice units (nominal hex side 1)."""
import math
CX, CY, TOKEN_R, CLEAR_R = -.36, .30, .30, .345
MAX_TILT = math.radians(55)

def distance(x,y): return math.hypot(x-CX,y-CY)

def front_cap(x,y):
    limit=float('inf');radius=CLEAR_R+.025
    # Also protect the two next rear neighbours at the game's nominal spacing.
    # Their upright plates can be covered by a tall model on this foreground tile.
    for ox,oy in [(0,0),(math.sqrt(3)/2,1.5),(-math.sqrt(3)/2,1.5)]:
        dx=x-(CX+ox);cy=CY+oy
        if y>=cy or abs(dx)>=radius:continue
        limit=min(limit,max(0,((cy-y)*math.cos(MAX_TILT)-math.sqrt(radius*radius-dx*dx))/math.sin(MAX_TILT)))
    return limit

def ground_height(x,y,z):
    d=distance(x,y)
    t=max(0,min(1,(d-(CLEAR_R+.025))/(.20 if z>.4 else .085)));t=t*t*(3-2*t)
    return min(.003+(z-.003)*t,max(.003,front_cap(x,y)))

def allowed(x,y,z,pad=0):
    return distance(x,y)>CLEAR_R+pad and z<=front_cap(x,y)+1e-5

def stone_ground_height(x,y,z):
    # Both instances use the same lowered shoulder on a shared boundary.
    r=.93;w=math.sqrt(3)*r;row=1.5*r;own=ground_height(x,y,z);result=own
    for ox,oy in [(w,0),(-w,0),(w/2,row),(-w/2,row),(w/2,-row),(-w/2,-row)]:
        xx=x-ox;yy=y-oy
        q=max(abs(xx)/(w/2),(abs(yy)+abs(xx)/math.sqrt(3))/r)
        t=max(0,min(1,(1.12-q)/.12));t=t*t*(3-2*t)
        if t:result=min(result,own*(1-t)+ground_height(xx,yy,z)*t)
    return result

def screen_mask(size=512,tilt=.48):
    from PIL import Image,ImageDraw
    pix=size/2.65;r=(CLEAR_R+.025)*pix
    mask=Image.new('L',(size,size),255)
    for ox,oy in [(0,0),(math.sqrt(3)/2,1.5),(-math.sqrt(3)/2,1.5)]:
        x=size/2+(CX+ox)*pix;y=size/2-(CY+oy)*math.cos(tilt)*pix
        ImageDraw.Draw(mask).ellipse((x-r,y-r,x+r,y+r),fill=0)
    return mask

def prepare_scene(terrain):
    """Flatten terrain, prune complete small components; never leave severed props."""
    import bpy
    from mathutils import Vector
    from render_models import mesh
    import render_v2_set as v2
    surfaces=('lush pasture hills','rolling field ground','deep excavated clay terraces',
              'solid dunes with actual fine ripples','R3 seven-peak Alpine chain connected bedrock')
    removed=0;surface_count=0
    for o in list(bpy.context.scene.objects):
        if o.type!='MESH' or o.name.startswith('thick hex'):continue
        if o.name.startswith(surfaces):
            for v in o.data.vertices:
                p=o.matrix_world@v.co
                if p.z>=-.0001 or terrain=='clay':
                    p.z=(stone_ground_height if terrain=='stone' else ground_height)(p.x,p.y,p.z);v.co=o.matrix_world.inverted()@p
            o.data.update()
            if terrain=='stone':
                for poly in o.data.polygons:
                    if poly.material_index==4 and sum(o.data.vertices[i].co.z for i in poly.vertices)/len(poly.vertices)<.54:poly.material_index=3
            surface_count+=1;continue
        world=[o.matrix_world@v.co for v in o.data.vertices]
        # Keep ground-attached props planted when a slope becomes a clearing.
        # Batched flowers/stones are independent connected components.
        if not o.name.startswith('soft curved wheat canopy row'):
            import render_density as density
            from alpine_chain import height as alpine_height
            ground_fn={'timber':lambda x,y:0,'stone':lambda x,y:alpine_height(x,y)+.004,
                       'fleece':density.pasture,'grain':density.field_ground,
                       'clay':density.clay_height,'desert':density.dune_height}[terrain]
            parents=list(range(len(world)))
            def find(i):
                while parents[i]!=i:parents[i]=parents[parents[i]];i=parents[i]
                return i
            for edge in o.data.edges:
                a,b=edge.vertices;parents[find(a)]=find(b)
            groups={}
            for i in range(len(world)):groups.setdefault(find(i),[]).append(i)
            for indices in groups.values():
                x=sum(world[i].x for i in indices)/len(indices);y=sum(world[i].y for i in indices)/len(indices)
                old=ground_fn(x,y);new=(stone_ground_height if terrain=='stone' else ground_height)(x,y,old)
                delta=new-old
                if abs(delta)>1e-6:
                    for i in indices:
                        world[i].z+=delta;o.data.vertices[i].co=o.matrix_world.inverted()@world[i]
            o.data.update()
        bad={i for i,p in enumerate(world) if not allowed(p.x,p.y,p.z,.025)}
        if not bad:continue
        if o.name.startswith('soft curved wheat canopy row'):
            # Keep individual crop strips on both sides of the cut circle.
            keep=[p for p in o.data.polygons if not any(i in bad for i in p.vertices)
                  and allowed(*tuple(sum(world[i][k] for i in p.vertices)/len(p.vertices) for k in range(3)),.035)]
        else:
            parent=list(range(len(world)))
            def root(i):
                while parent[i]!=i:parent[i]=parent[parent[i]];i=parent[i]
                return i
            for edge in o.data.edges:
                a,b=edge.vertices;parent[root(a)]=root(b)
            banned={root(i) for i in bad}
            keep=[p for p in o.data.polygons if root(p.vertices[0]) not in banned]
        if not keep:bpy.data.objects.remove(o,do_unlink=True);removed+=1;continue
        fs=[tuple(p.vertices) for p in keep];mi=[p.material_index for p in keep];sm=[p.use_smooth for p in keep]
        vs=[tuple(v.co) for v in o.data.vertices]
        # Remove unused vertices so validation sees only surviving geometry.
        used=sorted({i for f in fs for i in f});ids={old:new for new,old in enumerate(used)}
        o.data.clear_geometry();o.data.from_pydata([vs[i] for i in used],[],[tuple(ids[i] for i in f) for f in fs]);o.data.update()
        for p,m,s in zip(o.data.polygons,mi,sm):p.material_index=m;p.use_smooth=s
        removed+=1
    colors={'timber':(.23,.34,.085),'stone':(.34,.27,.18),'fleece':(.28,.37,.12),
            'grain':(.43,.30,.10),'clay':(.44,.165,.105),'desert':(.65,.43,.19)}
    clearing_z=.0002 if terrain=='timber' else .0032
    m=v2.mat(terrain+' natural number clearing',colors[terrain],.12)
    vs=[(CX,CY,clearing_z)]+[(CX+CLEAR_R*math.cos(i*math.tau/128),CY+CLEAR_R*math.sin(i*math.tau/128),clearing_z) for i in range(128)]
    mesh(terrain+' flat number clearing',vs,[(0,1+i,1+(i+1)%128) for i in range(128)],m)
    return {'token_center':(CX,CY),'token_radius':TOKEN_R,'clearing_radius':CLEAR_R,
            'upright_plate':True,'foreground_max_tilt_degrees':55,'pruned_objects':removed,'flattened_surfaces':surface_count}
