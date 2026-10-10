"""Second V3 pass: own modelled botanical/architectural detail, no image textures.

Kept separate from the historical V2 generators. Small repeated forms are merged
by material to keep Blender memory bounded. Only the V3 driver installs overrides.
"""
import math, random
import bpy
from mathutils import Vector,Matrix
from render_density import Batch, inside
from render_models import mesh, cone, pebble
import token_space as token

TAU=math.tau
V=None

def leaf(b,a,c,width,fold=.004):
    a=Vector(a);c=Vector(c);d=c-a;side=Vector((-d.y,d.x,0)).normalized()*width
    mid=a+d*.55
    b.add([tuple(a),tuple(mid+side),tuple(c),tuple(mid-side),tuple(mid+Vector((0,0,fold)))],[(4,1,0),(4,2,1),(4,3,2),(4,0,3)])

def pine(x,y,h,style,rng,mats):
    """Seven open branch whorls, individual needle sprays, bright new growth."""
    bark,greens=mats
    trunk=Batch('V3 pine exposed trunk and branch skeleton',bark)
    trunk.rod((x,y,0),(x,y,h*.97),h*.025,7)
    bs=[Batch('V3 layered conifer sprays '+str(i),m) for i,m in enumerate(greens)]
    for level in range(8):
        t=.14+level*.10;r=h*.285*(1-t)**.83
        n=7 if level<5 else 5
        for j in range(n):
            a=j*TAU/n+level*1.13+rng.uniform(-.13,.13)
            rr=r*rng.uniform(.85,1.13);z=h*t
            tip=(x+rr*math.cos(a),y+rr*math.sin(a),z+h*.035)
            trunk.rod((x,y,z+h*.022),tip,h*.004,4)
            for k in range(5):
                u=.25+k*.15;cx=x+rr*u*math.cos(a);cy=y+rr*u*math.sin(a)
                length=rr*(.47-.28*u);zz=z+h*.04*u
                for sign in [-1,1]:
                    aa=a+sign*.78
                    dest=(cx+length*math.cos(aa),cy+length*math.sin(aa),zz+h*.035)
                    b=bs[(level+j+k//2)%max(1,len(bs)-1)]
                    leaf(b,(cx,cy,zz),dest,h*.035*(1-u*.48),h*.015)
                    # Small pointed needles silhouette each fan, rather than cones.
                    for needle in range(2):
                        nn=.35+needle*.30;xx=cx+(dest[0]-cx)*nn;yy=cy+(dest[1]-cy)*nn
                        leaf(bs[-1] if (k+j)%7==0 else b,(xx,yy,zz+h*.02*nn),
                             (xx+h*.060*math.cos(aa+.45*sign),yy+h*.060*math.sin(aa+.45*sign),zz+h*.045),h*.010,h*.005)
            leaf(bs[-1],(tip[0],tip[1],tip[2]-.012),(tip[0]+h*.03*math.cos(a),tip[1]+h*.03*math.sin(a),tip[2]+h*.045),h*.018)
    for i in range(5):
        a=i*TAU/5;leaf(bs[-1],(x,y,h*.79),(x+h*.035*math.cos(a),y+h*.035*math.sin(a),h),h*.013)
    trunk.finish(False)
    for b in bs:b.finish(False)

def plants(fn,seed,count,flowers=True,dry=False):
    rng=random.Random(seed)
    colors=[(.024,.16,.008),(.075,.31,.010),(.23,.48,.022),(.48,.65,.055)] if not dry else [(.08,.17,.026),(.20,.29,.040),(.38,.38,.065),(.60,.48,.12)]
    bs=[Batch('V3 botanical leaf clusters '+str(i),V.mat('V3 botanical foliage '+str(i),c,.26)) for i,c in enumerate(colors)]
    petals=[Batch('V3 clustered wildflowers '+str(i),V.mat('V3 wildflower pigment '+str(i),c,.12)) for i,c in enumerate([(.98,.88,.61),(.95,.53,.005),(.26,.035,.49),(.65,.014,.006)])]
    centers=Batch('V3 flower golden hearts',V.mat('V3 flower pollen',(.72,.32,.008),.1))
    anchors=[(-.61,.33),(-.59,-.31),(-.26,-.66),(.44,-.53),(.64,-.17),(-.05,.75),(-.72,.03)]
    for i in range(int(count*1.15)):
        if dry or i%3:
            ax,ay=anchors[i%len(anchors)];x=ax+rng.gauss(0,.045 if dry else .10);y=ay+rng.gauss(0,.040 if dry else .09)
        else:x=rng.uniform(-.80,.80);y=rng.uniform(-.92,.92)
        if not inside(x,y,.919) or token.distance(x,y)<.347:continue
        if V.ACTIVE_TERRAIN=='timber' and abs(x+.28+.025*math.sin(y*9))<.047 and -.70<y<.46:continue
        if V.ACTIVE_TERRAIN=='grain' and inside(x,y,.78) and abs(y+.36-x*.20)>.10:continue
        z=fn(x,y)
        if z<-.01:continue
        cap=token.front_cap(x,y)-token.ground_height(x,y,z)-.005
        h=min(rng.uniform(.026,.076) if not dry else rng.uniform(.025,.055),cap)
        if V.ACTIVE_TERRAIN=='clay':h*=.65
        if h<.006:continue
        # Source-ground coordinates; shared prepare_scene rebases each component.
        b=bs[i%4]
        for j in range(5 if dry else 7):
            a=rng.random()*TAU;hh=h*rng.uniform(.5,1.1)
            leaf(b,(x,y,z),(x+hh*.65*math.cos(a),y+hh*.65*math.sin(a),z+hh),hh*.16,hh*.10)
        if not flowers or i%5:continue
        a=rng.random()*TAU;typ=(i//5)%4
        if typ==2 and h>.035:
            # Lupin: a tall tapered flowering spike, not a dot.
            h*=1.7
            if h>cap:continue
            b.rod((x,y,z),(x,y,z+h),.002,4)
            for k in range(6):
                for j in range(3):
                    a=j*TAU/3+k*.7;rr=.013*(1-k*.11)
                    petals[2].ellipsoid((x+rr*.5*math.cos(a),y+rr*.5*math.sin(a),z+h*.46+k*h*.085),(rr,rr*.65,.005),a,n=5,rings=3)
        else:
            b.rod((x,y,z),(x,y,z+h),.0018,4)
            r=.012 if typ==0 else .010
            for j in range(7 if typ==0 else 5):
                a=j*TAU/(7 if typ==0 else 5)
                leaf(petals[typ],(x,y,z+h),(x+r*math.cos(a),y+r*math.sin(a),z+h+.003),r*.40,.002)
            centers.ellipsoid((x,y,z+h+.002),(.004,.004,.003),n=5,rings=3)
    for b in bs+petals+[centers]:b.finish(False)

def hut(fn,x,y,w=.26):
    z=fn(x,y);rng=random.Random(19)
    boards=[V.mat('V3 shed silvered oak '+str(i),c,.35) for i,c in enumerate([(.24,.12,.043),(.37,.22,.085),(.16,.09,.039)])]
    roof=[V.mat('V3 split cedar shakes '+str(i),c,.35) for i,c in enumerate([(.32,.20,.085),(.46,.31,.13),(.24,.14,.058)])]
    for xx in [x-w*.47,x+w*.47]:
        for yy in [y-w*.35,y+w*.35]:V.pole('V3 shed corner posts',(xx,yy,z),(xx,yy,z+w*.78),.009,boards[0],5)
    for yy in [y-w*.37,y+w*.37]:
        for i in range(9):
            if yy<y and 3<=i<=5:continue
            V.cube('V3 shed separate wall planks',(x-w*.48+i*w*.12,yy,z+w*.33),(w*.112,.014,w*.66),boards[i%3],.001)
    for xx in [x-w*.49,x+w*.49]:
        for j in range(7):V.cube('V3 shed side plank',(xx,y-w*.36+j*w*.12,z+w*.33),(.012,w*.11,w*.66),boards[j%3],.001)
    for side in [-1,1]:
        for row in range(4):
            u=row/4;yy=y+side*w*.47*(1-u);zz=z+w*(.68+.35*u)
            for col in range(8):
                xx=x-w*.57+col*w*.15+(row%2)*w*.025
                verts=[(xx,yy,zz),(xx+w*.143,yy,zz),(xx+w*.143,yy-side*w*.15,zz+w*.112),(xx,yy-side*w*.15,zz+w*.112)]
                mesh('V3 overlapping cedar roof shake',verts,[(0,1,2,3)],roof[rng.randrange(3)])
    V.pole('V3 shed ridge beam',(x-w*.64,y,z+w*1.04),(x+w*.64,y,z+w*1.04),.009,boards[0],5)
    for yy in [y-w*.39,y+w*.39]:
        V.pole('V3 shed diagonal brace',(x-w*.44,yy,z+.02),(x+w*.44,yy,z+w*.62),.007,boards[0],4)
    V.cube('V3 shed dark doorway',(x,y-w*.38,z+w*.23),(w*.31,.008,w*.46),V.mat('V3 shed door recess',(.012,.008,.004),0),.001)

def logs(fn,x,y,count=5):
    before=set(bpy.context.scene.objects)
    rng=random.Random(293);bark=V.mat('V3 deeply furrowed log bark',(.115,.038,.012),.45);end=V.mat('V3 golden exposed end grain',(.64,.34,.10),.30);ring=V.mat('V3 endgrain dark lines',(.24,.10,.027),.25)
    ridges=Batch('V3 log bark ridges and endgrain rings',ring)
    for i in range(count):
        xx=x+(i%3)*.047;zz=fn(xx,y)+.030+(i//3)*.046;r=.027
        V.pole('V3 felled trunk',(xx,y-.12,zz),(xx+.009,y+.13,zz+.005),r,bark,12)
        V.pole('V3 pale circular saw cut',(xx,y-.121,zz),(xx,y-.126,zz),r*.93,end,16)
        for a in range(9):
            t=a*TAU/9;ridges.rod((xx+r*.96*math.cos(t),y-.115,zz+r*.96*math.sin(t)),(xx+.007+r*.91*math.cos(t),y+.12,zz+.004+r*.91*math.sin(t)),.0018,3)
        for rr in [.008,.015,.021]:
            for j in range(16):
                a=j*TAU/16;b=(j+1)*TAU/16
                ridges.rod((xx+rr*math.cos(a),y-.127,zz+rr*math.sin(a)),(xx+rr*math.cos(b),y-.127,zz+rr*math.sin(b)),.0007,3)
    ridges.finish(False)
    if V.ACTIVE_TERRAIN=='timber':
        pivot=Vector((x+.047,y,0));angle=-.75 if x>-.3 else .65
        transform=Matrix.Translation(pivot)@Matrix.Rotation(angle,4,'Z')@Matrix.Translation(-pivot)
        for o in set(bpy.context.scene.objects)-before:o.matrix_world=transform@o.matrix_world

def sheep(x,y,heading=0,phase=0):
    rng=random.Random(round((x+y)*9000));z=V.meadow(x,y)
    wool=V.mat('V3 sheep warm ivory fleece',(.80,.75,.57),.12);shadow=V.mat('V3 sheep fleece shaded roots',(.54,.47,.31),.20)
    black=V.mat('V3 sheep charcoal face and hooves',(.024,.020,.014),.18);skin=V.mat('V3 sheep ear velvet',(.23,.16,.085),.15)
    body=Batch('V3 sheep coherent body and fine wool locks',wool);dark=Batch('V3 sheep face legs ears',black);inner=Batch('V3 sheep ear interiors',skin)
    def transform(p):return (x+p[0]*math.cos(heading)-p[1]*math.sin(heading),y+p[0]*math.sin(heading)+p[1]*math.cos(heading),z+p[2])
    def oval(b,c,s,angle=0,n=8,rings=4):b.ellipsoid(transform(c),s,heading+angle,n,rings)
    oval(body,(0,0,.101),(.097,.053,.052),n=12,rings=6)
    # Seventy small locks follow an actual body surface, instead of nine large balls.
    for j in range(7):
        u=-.90+j*.29+rng.uniform(-.055,.055)
        for k in range(11):
            a=k*TAU/11+j*.43+rng.uniform(-.19,.19);rr=math.sqrt(max(0,1-u*u));xx=.089*u+rng.uniform(-.003,.003)
            yy=.050*rr*math.cos(a);zz=.103+.050*rr*math.sin(a)
            if zz<.080:continue
            scale=rng.uniform(.75,1.20)
            oval(body,(xx,yy,zz),(.014*scale,.010*scale,.011*scale),angle=rng.random(),n=7,rings=4)
    grazing=phase<1.8;hz=.071 if grazing else .125
    oval(dark,(.090,0,hz),(.030,.019,.034),n=10,rings=5)
    oval(dark,(.106,0,hz-.014),(.021,.017,.023),n=8,rings=4)
    for side in [-1,1]:
        oval(dark,(.083,side*.032,hz+.010),(.023,.010,.008),angle=side*.3)
        oval(inner,(.086,side*.032,hz+.014),(.015,.006,.002),angle=side*.3)
        for xx in [-.055,.049]:dark.rod(transform((xx,side*.031,.063)),transform((xx+.008,side*.035,.009)),.008,5)
        # Light eye glint distinguishes head from hooves at phone scale.
        oval(body,(.106,side*.017,hz+.007),(.0032,.0025,.0032),n=5,rings=3)
    oval(body,(-.091,0,.111),(.027,.015,.017),angle=.2)
    for b in [body,dark,inner]:b.finish()

def stone_wall(fn,a,b,n,gate):
    rng=random.Random(914)
    ms=[V.mat('V3 wall weathered stone '+str(i),c,.35) for i,c in enumerate([(.28,.30,.23),(.42,.44,.33),(.21,.24,.18),(.51,.49,.35)])]
    bs=[Batch('V3 wall irregular fitted stones '+str(i),m) for i,m in enumerate(ms)]
    dx=b[0]-a[0];dy=b[1]-a[1];angle=math.atan2(dy,dx)
    for row in range(3):
        for i in range(n):
            t=(i+(row%2)*.45)/(n-1)
            if t>1 or (gate and .35<t<.58):continue
            x=a[0]+dx*t;y=a[1]+dy*t;z=fn(x,y)
            bs[rng.randrange(4)].ellipsoid((x,y,z+.022+row*.033),(.035+rng.random()*.009,.029,.022),angle+rng.uniform(-.22,.22),n=7,rings=3)
    for i in range(n):
        t=i/(n-1)
        if gate and .35<t<.58:continue
        x=a[0]+dx*t;y=a[1]+dy*t
        bs[i%4].ellipsoid((x,y,fn(x,y)+.116),(.016,.031,.029),angle+rng.uniform(-.12,.12),n=6,rings=3)
    for bb in bs:bb.finish(False)
    if gate:
        aa=(a[0]+dx*.35,a[1]+dy*.35);bb=(a[0]+dx*.58,a[1]+dy*.58)
        V.original_fence(fn,aa,bb,3,False,True)

def fence(fn,a,b,n=9,stone=False,gate=False):
    if stone:return stone_wall(fn,a,b,n,gate)
    if V.ACTIVE_TERRAIN=='grain':
        wood=V.mat('V3 harvest fence sunlit oak',(.38,.20,.057),.3)
        for i in range(0,n,2):
            t=i/(n-1);x=a[0]+(b[0]-a[0])*t;y=a[1]+(b[1]-a[1])*t
            V.pole('V3 tall harvest fence post',(x,y,fn(x,y)),(x,y,fn(x,y)+.31),.014,wood,6)
        for h in [.13,.26]:V.pole('V3 tall harvest fence rail',(*a,fn(*a)+h),(*b,fn(*b)+h),.011,wood,6)
        return
    V.original_fence(fn,a,b,n,stone,gate)
    iron=V.mat('V3 fence iron nails',(.075,.045,.014),.1)
    for i in range(0,n,2):
        t=i/(n-1);x=a[0]+(b[0]-a[0])*t;y=a[1]+(b[1]-a[1])*t
        for h in [.06,.12]:pebble((x,y-.010,fn(x,y)+h),(.0035,.003,.0035),iron)

def crag(name,x,y,z,sx,sy,h,materials,seed):
    """Jointed rock with ledges, leaning faces and discontinuous sediment bands."""
    rng=random.Random(seed);n=9;vs=[];fs=[]
    az=rng.random()*TAU
    corners=[(math.cos(az+j*TAU/n)*rng.uniform(.80,1.13),math.sin(az+j*TAU/n)*rng.uniform(.85,1.1)) for j in range(n)]
    for ring in range(6):
        t=ring/5;rr=[1,1.03,.84,.90,.69,.57][ring]
        for j,(xx,yy) in enumerate(corners):
            vs.append((x+sx*(rr*xx+t*.12),y+sy*(rr*yy-t*.10),z+h*(t+rng.uniform(-.030,.030))))
    for ring in range(5):
        for j in range(n):fs.append((ring*n+j,ring*n+(j+1)%n,(ring+1)*n+(j+1)%n,(ring+1)*n+j))
    vs.append((x+sx*.12,y-sy*.10,z+h*1.04))
    fs.extend((5*n+j,5*n+(j+1)%n,len(vs)-1) for j in range(n))
    o=mesh(name,vs,fs,materials[0])
    for m in materials[1:]:o.data.materials.append(m)
    for p in o.data.polygons:
        p.material_index=(p.index//n+rng.randrange(2))%len(materials)
    return o

def wheel(x,y,z,r,m,iron):
    for rr,material in [(r,m),(r+.003,iron)]:
        bpy.ops.mesh.primitive_torus_add(major_radius=rr,minor_radius=.0035,major_segments=20,minor_segments=5,location=(x,y,z),rotation=(math.pi/2,0,0));bpy.context.object.data.materials.append(material)
    for k in range(8):
        a=k*TAU/8;V.pole('V3 eight-spoke wheel',(x,y,z),(x+r*math.cos(a),y,z+r*math.sin(a)),.0035,m,5)
    V.pole('V3 cart axle hub',(x,y-.011,z),(x,y+.011,z),.009,m,8)

def mine_cart(fn,x,y):
    wood=V.mat('V3 mine cart planks',(.27,.145,.047),.3);iron=V.mat('V3 cart iron straps',(.075,.057,.033),.1);z=fn(x,y)
    for yy in [y-.058,y+.058]:
        for xx in [x-.045,x+.045]:wheel(xx,yy,z+.031,.028,wood,iron)
        for k in range(3):V.cube('V3 mine cart separate side board',(x,yy,z+.067+k*.023),(.15,.013,.021),wood,.001)
        for xx in [x-.053,x+.053]:V.cube('V3 mine cart iron binding',(xx,yy-.008,z+.086),(.009,.004,.075),iron,.001)
    V.cube('V3 loaded mine wagon bed',(x,y,z+.057),(.15,.13,.012),wood,.002)
    stone=V.mat('V3 wagon quarried rubble',(.37,.34,.26),.25)
    for k in range(7):pebble((x+(k%3-1)*.037,y+(k//3-1)*.028,z+.12),(.023,.022,.021),stone)

def enhance(name):
    fn=V.zfn(name);rng=random.Random(714)
    wood=V.mat('V3 warm worn working timber',(.30,.145,.036),.34);iron=V.mat('V3 forged tools and bindings',(.072,.078,.067),.20)
    if name=='timber':
        # Visible radial rings on stump tops, an axe, offcuts and ferns.
        grain=Batch('V3 stump growth rings and splits',V.mat('V3 stump ring dark wood',(.20,.078,.017),.22))
        for x,y in [(-.24,-.15),(-.36,-.57),(.04,-.10),(-.67,.11)]:
            for r in [.014,.024,.035]:
                for j in range(24):
                    a=j*TAU/24;b=(j+1)*TAU/24
                    grain.rod((x+r*math.cos(a),y+r*math.sin(a),.070),(x+r*math.cos(b),y+r*math.sin(b),.070),.0008,3)
        grain.finish(False)
        V.pole('V3 axe wooden handle',(-.23,-.15,.083),(-.19,-.15,.20),.0045,wood,5)
        V.cube('V3 axe iron head',(-.232,-.15,.09),(.04,.012,.024),iron,.002)
        for i in range(26):
            x=-.45+rng.random()*.43;y=-.48+rng.random()*.35
            V.pole('V3 logging wood chips',(x,y,.007),(x+.020,y+.009,.010),.0035,wood,4)
        # Fern fronds mark the forest path.
        fern=Batch('V3 fern pinnate fronds',V.mat('V3 fern new green',(.13,.36,.021),.2))
        for x,y in [(-.38,.05),(-.58,-.11),(-.38,-.44),(-.03,-.69),(-.66,.26)]:
            for j in range(5):
                a=j*TAU/5
                for k in range(7):
                    t=(k+1)/8;xx=x+.095*t*math.cos(a);yy=y+.095*t*math.sin(a);z=.09*math.sin(t*math.pi*.75)
                    for s in [-1,1]:leaf(fern,(xx,yy,z),(xx+.028*(1-t)*math.cos(a+s*1.1),yy+.028*(1-t)*math.sin(a+s*1.1),z+.006),.007*(1-t)+.002)
        fern.finish(False)
    elif name=='fleece':
        # Patches of clover at the cropped edge, and a small water trough by the wall.
        clover=Batch('V3 pasture clover patches',V.mat('V3 clover leaf',(.067,.29,.014),.23))
        for i in range(330):
            x=rng.uniform(-.75,.74);y=rng.uniform(-.84,.84)
            if not inside(x,y,.91) or token.distance(x,y)<.35:continue
            z=fn(x,y)+.006
            for k in range(3):
                a=k*TAU/3;clover.ellipsoid((x+.009*math.cos(a),y+.009*math.sin(a),z),(.011,.007,.0025),a,n=6,rings=3)
        clover.finish(False)
        x=-.38;y=.47;z=fn(x,y);stone=V.mat('V3 pasture trough stone',(.26,.28,.20),.2)
        V.cube('V3 trough bottom',(x,y,z+.014),(.12,.065,.022),stone,.007)
        for yy in [y-.031,y+.031]:V.cube('V3 trough raised rim',(x,yy,z+.04),(.13,.012,.045),stone,.004)
        water=V.mat('V3 trough dark water',(.028,.065,.043),.05);water.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value=.19
        V.cube('V3 trough water',(x,y,z+.038),(.10,.045,.004),water,.001)
    elif name=='grain':
        # Loose harvested straw laid flat across the paths; no tall apron clutter.
        straw=Batch('V3 loose harvested straw',V.mat('V3 dry straw path scatter',(.66,.38,.046),.22))
        for i in range(650):
            x=rng.uniform(-.77,.77);y=rng.uniform(-.87,.87)
            if not inside(x,y,.91) or token.distance(x,y)<.35:continue
            if abs(y+.36-x*.20)>.085 and token.front_cap(x,y)>.12:continue
            z=fn(x,y)+.003;a=rng.random()*TAU;l=rng.uniform(.012,.048)
            straw.rod((x,y,z),(x+l*math.cos(a),y+l*math.sin(a),z+.001),.0014,3)
        straw.finish(False)
        for yy in [-.34-.081,-.34+.081]:
            for xx in [-.48-.09,-.48+.09]:V.cube('V3 harvest cart corner iron straps',(xx,yy,fn(-.48,-.34)+.107),(.009,.006,.095),iron,.001)
        for x,y in [(-.58,-.28),(-.37,-.36)]:
            V.pole('V3 cart wooden end stake',(x,y,fn(x,y)+.05),(x,y,fn(x,y)+.17),.008,wood,5)
    elif name=='clay':
        x=-.10;y=.55;z=fn(x,y)
        # Chimney masonry and iron bands; a supported platform around the kiln.
        bricks=[V.mat('V3 chimney fired brick '+str(i),c,.25) for i,c in enumerate([(.49,.11,.025),(.66,.19,.042),(.36,.060,.015)])]
        masonry=Batch('V3 chimney individual brick joints',bricks[0])
        for row in range(10):
            r=.056-row*.0018
            for j in range(12):
                a=(j+(row%2)*.5)*TAU/12;aa=a+.45*TAU/12;ab=a-.45*TAU/12;zz=z+.215+row*.029
                masonry.add([(x+r*math.cos(ab),y+r*math.sin(ab),zz),(x+r*math.cos(aa),y+r*math.sin(aa),zz),(x+(r-.0015)*math.cos(aa),y+(r-.0015)*math.sin(aa),zz+.025),(x+(r-.0015)*math.cos(ab),y+(r-.0015)*math.sin(ab),zz+.025)],[(0,1,2,3)])
        masonry.finish(False)
        for dz in [.27,.43]:
            bpy.ops.mesh.primitive_torus_add(major_radius=.054-(dz-.27)*.07,minor_radius=.003,major_segments=28,minor_segments=4,location=(x,y,z+dz));bpy.context.object.data.materials.append(iron)
        for xx in [x-.145,x+.145]:
            for yy in [y-.12,y+.12]:V.pole('V3 kiln working platform posts',(xx,yy,fn(xx,yy)),(xx,yy,z+.16),.009,wood,5)
            V.cube('V3 kiln narrow working deck',(xx,y,z+.075),(.065,.26,.012),wood,.001)
            V.pole('V3 kiln platform handrail',(xx,y-.12,z+.16),(xx,y+.12,z+.16),.006,wood,5)
        # Vertical quarry joints follow the actual irregular terrace boundaries.
        cuts=[V.mat('V3 exposed clay strata '+str(i),c,.4) for i,c in enumerate([(.47,.069,.012),(.67,.13,.023),(.84,.23,.045)])]
        for i in range(85):
            a=i*2.39996;r=[.31,.57,.82][i%3]
            xx=math.copysign(abs(math.cos(a))**(2/3.6),math.cos(a));yy=math.copysign(abs(math.sin(a))**(2/3.6),math.sin(a))
            aa=math.atan2(yy,xx);r/=1+.045*math.sin(7*aa)+.029*math.sin(13*aa+.7)
            x=-.12+.59*r*xx;y=-.13+.56*r*yy
            if token.distance(x,y)<.36:continue
            crag('V3 broken clay terrace cut',x,y,fn(x,y)-.035,.033,.027,.063,cuts,i)
        mine_cart(fn,-.31,-.54)
        # Hanging hoist block and bucket beside the crane.
        V.pole('V3 crane hanging hook',(-.36,.04,fn(-.60,-.04)+.35),(-.36,.04,fn(-.36,.04)+.065),.0025,iron,5)
        V.cube('V3 suspended clay hoist bucket',(-.36,.04,fn(-.36,.04)+.045),(.075,.070,.070),wood,.004)
    elif name=='stone':
        # Resolve erosion on the broad peak faces as real relief, leaving the
        # periodic R3 height function and the original solid skirt in place.
        import bmesh
        terrain=bpy.data.objects.get('R3 seven-peak Alpine chain connected bedrock')
        bm=bmesh.new();bm.from_mesh(terrain.data);bm.normal_update()
        top_edges={e for face in bm.faces if face.normal.z>.005 for e in face.edges}
        bmesh.ops.subdivide_edges(bm,edges=list(top_edges),cuts=1,use_grid_fill=True)
        bm.normal_update();top_vertices={v for face in bm.faces if face.normal.z>.005 for v in face.verts}
        for vertex in top_vertices:vertex.co.z=fn(vertex.co.x,vertex.co.y)
        bm.normal_update();bm.to_mesh(terrain.data);bm.free();terrain.data.update()
        rock=[V.mat('V3 alpine layered rock '+str(i),c,.30) for i,c in enumerate([(.32,.35,.36),(.57,.58,.52),(.19,.23,.25),(.71,.68,.54)])]
        # Exposed buttresses follow the mountain, leaving the selected R3 ridge intact.
        for i in range(105):
            x=rng.uniform(-.75,.12);y=rng.uniform(-.73,.75)
            if not inside(x,y,.91) or token.distance(x,y)<.39:continue
            z=fn(x,y)
            if z<.18:continue
            s=rng.uniform(.024,.068);h=rng.uniform(.04,.12)
            if z+h>token.front_cap(x,y):continue
            crag('V3 fractured alpine buttress',x,y,z-.035,s,s*.7,h,rock,i+200)
        mine_cart(fn,-.35,-.61)
        # Working deck and ladder make the mine a building, rather than loose rods.
        x=-.50;y=-.22;z=fn(x,y)
        for k in range(7):V.cube('V3 mine deck individual planks',(x-.095+k*.032,y,z+.28),(.029,.20,.012),wood,.001)
        for xx in [x-.06,x+.06]:V.pole('V3 mine ladder stringer',(xx,y-.13,z+.018),(xx,y-.08,z+.35),.006,wood,5)
        for k in range(9):V.pole('V3 mine ladder rungs',(x-.06,y-.13+k*.006,z+.04+k*.035),(x+.06,y-.13+k*.006,z+.04+k*.035),.0045,wood,5)
        # Replace the surviving low smooth pine objects with the sharper botanical model.
    elif name=='desert':
        # Remove pancake stacks; replace with ledged, jointed outcrops at the same sites.
        for o in list(bpy.context.scene.objects):
            if o.type=='MESH' and any(m and m.name.startswith('V3 layered sandstone ') for m in o.data.materials):bpy.data.objects.remove(o,do_unlink=True)
        rocks=[V.mat('V3 sedimentary sandstone '+str(i),c,.28) for i,c in enumerate([(.37,.17,.042),(.54,.29,.076),(.75,.43,.14),(.43,.22,.058)])]
        for k,(x,y,s) in enumerate([(-.56,.37,.13),(-.51,-.31,.11),(-.24,-.61,.12),(.43,-.48,.12),(.60,-.10,.09),(-.02,.68,.11),(-.64,.04,.07)]):
            crag('V3 sandstone layered outcrop',x,y,fn(x,y)-.014,s,s*.75,s*1.45,rocks,k)
            for j in range(4):
                xx=x+rng.uniform(-.11,.11);yy=y+rng.uniform(-.09,.09)
                if not inside(xx,yy,.91):continue
                crag('V3 sandstone outcrop fallen block',xx,yy,fn(xx,yy)-.005,.035,.025,rng.uniform(.025,.07),rocks,k*5+j)
        # Much finer twig hierarchy on the bleached tree.
        dead=V.mat('V3 deadwood sunbleached tips',(.58,.39,.16),.25);x=-.36;y=.14;z=fn(x,y)
        for j in range(9):
            a=j*2.399;start=(x+.035*math.cos(a),y+.015*math.sin(a),z+.16+j*.017)
            end=(x+.12*math.cos(a),y+.075*math.sin(a),z+.25+j*.012)
            V.pole('V3 dead tree fine branching',start,end,.0045,dead,5)
            for s in [-1,1]:V.pole('V3 dead tree forked twig',end,(end[0]+.031*math.cos(a+s*.6),end[1]+.031*math.sin(a+s*.6),end[2]+.042),.002,dead,4)
    story_details(name,fn,rng,wood,iron)


def story_details(name,fn,rng,wood,iron):
    """Readable secondary scenes: silhouettes first, tiny surface detail second."""
    if name=='timber':
        path=Batch('V3 winding logging footpath',V.mat('V3 trampled ochre forest path',(.37,.22,.065),.35))
        for i in range(66):
            y=-.72+i*.018;yy=y+.019;x=-.28-.025*math.sin(y*9);xx=-.28-.025*math.sin(yy*9)
            w=.043+.008*math.sin(i*1.8)
            path.add([(x-w,y,.0015),(x+w,y,.0015),(xx+w,yy,.0015),(xx-w,yy,.0015)],[(0,1,2,3)])
        path.finish(False)
        # Cross-cut saw resting on a pair of trestles beside the camp.
        x=-.15;y=.33
        for yy in [y-.072,y+.072]:
            for side in [-1,1]:V.pole('V3 sawbuck crossed legs',(x+side*.048,yy,0),(x-side*.025,yy,.12),.007,wood,5)
        V.pole('V3 sawbuck fresh trunk',(x,y-.12,.104),(x,y+.12,.104),.025,wood,10)
        blade=V.mat('V3 crosscut saw steel',(.39,.43,.41),.12)
        V.cube('V3 crosscut saw blade',(x,y,.145),(.20,.009,.023),blade,.001)
        for xx in [x-.11,x+.11]:V.pole('V3 saw two handles',(xx,y,.12),(xx,y,.185),.006,wood,5)
        mushroom=V.mat('V3 forest mushroom russet',(.54,.10,.023),.15)
        for x,y in [(-.41,-.02),(-.44,-.04),(-.02,-.72),(-.63,.29),(-.64,.33)]:
            V.pole('V3 mushroom cream stem',(x,y,0),(x,y,.025),.004,wood,5)
            pebble((x,y,.029),(.017,.014,.008),mushroom,True)
    elif name=='fleece':
        # Taller blue flower islands provide colour masses that survive downsampling.
        foliage=Batch('V3 pasture flowering island leaves',V.mat('V3 island fresh leaves',(.15,.39,.016),.2))
        blooms=Batch('V3 pasture blue delphinium spikes',V.mat('V3 delphinium blue violet',(.20,.08,.80),.12))
        for x,y in [(-.64,.17),(-.58,.40),(-.34,.63),(-.03,-.72),(-.63,-.42)]:
            for j in range(7):
                xx=x+rng.uniform(-.035,.035);yy=y+rng.uniform(-.027,.027);z=fn(xx,yy);h=rng.uniform(.09,.17)
                foliage.rod((xx,yy,z),(xx,yy,z+h),.002,4)
                for k in range(5):
                    for side in [-1,1]:
                        blooms.ellipsoid((xx+side*.005,yy,z+h*.43+k*h*.12),(.008*(1-k*.1),.008,.007),n=6,rings=3)
                for k in range(4):
                    a=k*TAU/4;leaf(foliage,(xx,yy,z),(xx+.035*math.cos(a),yy+.035*math.sin(a),z+.045),.010)
        foliage.finish(False);blooms.finish()
    elif name=='grain':
        # A sickle and a rake read as a harvest in progress alongside the laden cart.
        x=-.34;y=-.49;z=fn(x,y)
        V.pole('V3 harvest rake long handle',(x,y,z+.012),(x+.12,y-.15,z+.016),.004,wood,5)
        V.pole('V3 harvest rake crossbar',(x-.05,y-.025,z+.015),(x+.035,y+.040,z+.015),.005,wood,5)
        for k in range(7):
            xx=x-.045+k*.013;yy=y-.021+k*.010
            V.pole('V3 harvest rake wooden teeth',(xx,yy,z+.009),(xx+.02,yy-.023,z+.009),.0025,wood,4)
        # Additional central stems prevent sheaves reading as hollow flowerpots at flat.
        gold=V.mat('V3 sheaf warm full centres',(.90,.53,.042),.22);heads=Batch('V3 sheaf filled centre ears',gold)
        for x,y in [(-.17,-.42),(.14,-.66),(-.60,.25),(-.18,.62)]:
            z=fn(x,y);cap=min(token.front_cap(x+dx,y+dy) for dx,dy in [(-.055,.055),(.055,.055),(0,.055)])
            height=min(.35,max(.15,cap-z-.055))
            for i in range(22):
                a=i*2.4;r=.035*math.sqrt((i+1)/22);xx=x+r*math.cos(a);yy=y+r*math.sin(a);hh=height+rng.uniform(-.018,.02)
                heads.rod((x,y,z+height*.43),(xx,yy,z+hh),.003,4)
                for k in range(4):heads.ellipsoid((xx,yy,z+hh-.02+k*.011),(.008,.006,.008),n=5,rings=3)
        heads.finish(False)
    elif name=='clay':
        # Light in the stoke hole, ash, shovel and split fuel under the kiln deck.
        fire=V.mat('V3 kiln living coals',(.95,.18,.012),.05)
        p=fire.node_tree.nodes['Principled BSDF'];p.inputs['Emission Color'].default_value=(1,.12,.003,1);p.inputs['Emission Strength'].default_value=1.5
        x=-.10;y=.55;z=fn(x,y)
        V.cube('V3 orange fire within kiln mouth',(x,y-.132,z+.035),(.047,.004,.022),fire,.004)
        logs(fn,-.30,.48,3)
        V.pole('V3 kiln shovel handle',(-.24,.38,fn(-.24,.38)),(-.18,.40,z+.24),.005,wood,5)
        V.cube('V3 shovel spade',(-.241,.38,fn(-.24,.38)+.008),(.040,.028,.010),iron,.005)
        # Brick stacks get light upper edges and dark joints instead of uniform blocks.
        bright=V.mat('V3 brick dry upper faces',(.70,.20,.044),.25)
        for o in list(bpy.context.scene.objects):
            if o.name.startswith('V3 large stacked brick pallets'):
                o.data.materials.append(bright)
                for p in o.data.polygons:
                    if p.normal.z>.5:p.material_index=len(o.data.materials)-1
    elif name=='stone':
        # A dark adit framed by timbers separates the working mine from the cliffs.
        x=-.54;y=-.40;z=fn(x,y)
        recess=V.mat('V3 adit deep shadow',(.015,.025,.027),0)
        V.cube('V3 mine adit opening',(x,y,z+.065),(.13,.030,.13),recess,.012)
        for xx in [x-.074,x+.074]:V.pole('V3 adit timber uprights',(xx,y-.02,z),(xx,y-.02,z+.15),.014,wood,6)
        V.pole('V3 adit lintel',(x-.095,y-.02,z+.15),(x+.095,y-.02,z+.15),.018,wood,6)
        # Spare pit props and an ore bin complete the railway-side work area.
        logs(fn,-.14,-.67,3)
    elif name=='desert':
        # Rounded thorn scrub around outcrops, preserving broad clean slip faces.
        shrub=Batch('V3 desert rounded thorn scrub',V.mat('V3 desert olive scrub',(.23,.30,.026),.28))
        gold=Batch('V3 desert dry shrub tips',V.mat('V3 desert sunlit scrub tips',(.54,.46,.078),.2))
        for x,y in [(-.62,.36),(-.58,-.35),(-.18,-.66),(.48,-.48),(-.07,.70),(-.69,.04)]:
            for j in range(42):
                a=j*2.399;r=.058*math.sqrt((j+1)/42);xx=x+r*math.cos(a);yy=y+r*math.sin(a)
                if not inside(xx,yy,.91):continue
                z=fn(xx,yy);h=.065*(1-.45*r/.058)
                for k in range(3):
                    aa=a+k*2.1;leaf(shrub,(xx,yy,z),(xx+.023*math.cos(aa),yy+.023*math.sin(aa),z+h),.008)
                gold.ellipsoid((xx,yy,z+h),(.005,.005,.003),n=5,rings=3)
        shrub.finish(False);gold.finish(False)

def materials(scene,terrain):
    """World-scale pigment, dry brushing and fine relief; never painted-face projection."""
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.48
    scene.view_settings.exposure=.06
    for o in scene.objects:
        if o.type=='LIGHT':
            o.data.color=(1,.94,.83)
            if o.data.type=='AREA':
                o.data.energy=380;o.data.size=2.3
                o.rotation_euler=(Vector((0,0,0))-o.location).to_track_quat('-Z','Y').to_euler()
            else:o.data.energy=2.15;o.data.angle=.10
    ground_colors={'timber':(.060,.18,.015),'fleece':(.085,.24,.012),'grain':(.55,.29,.028),'clay':(.67,.12,.019),'stone':(.32,.27,.18),'desert':(.76,.45,.13)}
    clearing_colors={'timber':(.16,.23,.045),'fleece':(.18,.27,.043),'grain':(.50,.30,.057),'clay':(.48,.13,.050),'stone':(.35,.31,.22),'desert':(.69,.44,.17)}
    used={m for o in scene.objects if o.type=='MESH' for m in o.data.materials if m}
    for m in used:
        if m.name.startswith('earth edge'):continue
        ns=m.node_tree.nodes;ls=m.node_tree.links;p=ns.get('Principled BSDF')
        if not p:continue
        # Bright mineral faces, with warm sun and cooler joints; retain the R3 mesh.
        mineral={'R3 warm dark bedrock':(.36,.38,.36),'R3 warm cleft shading':(.17,.20,.22),
                 'R3 warm scree':(.44,.43,.35),'R3 warm lit crags':(.60,.59,.50),
                 'V3 cut quarry block pale rock':(.56,.55,.47),
                 'V3 angular protruding alpine crags':(.40,.43,.41)}
        if m.name in mineral:
            c=mineral[m.name];m.diffuse_color=(*c,1)
            for node in ns:
                if node.bl_idname=='ShaderNodeValToRGB':
                    node.color_ramp.elements[0].color=(*(q*.78 for q in c),1)
                    node.color_ramp.elements[-1].color=(*(min(1,q*1.2) for q in c),1)
        # Delicate foliage cannot carry the same heavy occlusion as masonry.
        botanical=any(s in m.name.lower() for s in ['canopy','foliage','fern','clover','leaf','wheat honey'])
        if botanical:
            for node in ns:
                if node.bl_idname=='ShaderNodeAmbientOcclusion':node.inputs['Distance'].default_value=.055
            p.inputs['Subsurface Weight'].default_value=.035
            p.inputs['Roughness'].default_value=.72
        clearing='natural number clearing' in m.name
        ground=clearing or any(s in m.name for s in ['rich earth','timber ground','moss ground','threshed field soil'])
        rock=any(s in m.name.lower() for s in ['rock','crag','cleft','sandstone','scree'])
        wood=any(s in m.name.lower() for s in ['wood','timber','plank','bark','cedar','oak']) and not ground
        source=p.inputs['Base Color'].links[0].from_socket if p.inputs['Base Color'].is_linked else None
        position=ns.new('ShaderNodeNewGeometry')
        noise=ns.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=(7 if terrain=='desert' else 23) if ground else 35;noise.inputs['Detail'].default_value=4;noise.inputs['Roughness'].default_value=.72
        ls.new(position.outputs['Position'],noise.inputs['Vector'])
        ramp=ns.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.25;ramp.color_ramp.elements[1].position=.75
        if ground:
            c=clearing_colors[terrain] if clearing else ground_colors[terrain]
            lo,hi=(.91,1.08) if terrain=='desert' else (.55,1.35)
            ramp.color_ramp.elements[0].color=(*(q*lo for q in c),1);ramp.color_ramp.elements[1].color=(*(min(1,q*hi) for q in c),1)
            ls.new(noise.outputs['Fac'],ramp.inputs[0]);ls.new(ramp.outputs['Color'],p.inputs['Base Color'])
            ao=ns.new('ShaderNodeAmbientOcclusion');ao.inputs['Distance'].default_value=.13
            mix=ns.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=.52;ls.new(ramp.outputs[0],mix.inputs[1]);ls.new(ao.outputs['Color'],mix.inputs[2]);ls.new(mix.outputs[0],p.inputs['Base Color'])
        elif rock or wood:
            ramp.color_ramp.elements[0].color=(.48,.45,.38,1);ramp.color_ramp.elements[1].color=(1.25,1.20,1.05,1)
            ls.new(noise.outputs['Fac'],ramp.inputs[0]);mix=ns.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=.48
            if source:ls.new(source,mix.inputs[1])
            else:mix.inputs[1].default_value=p.inputs['Base Color'].default_value
            ls.new(ramp.outputs[0],mix.inputs[2]);ls.new(mix.outputs[0],p.inputs['Base Color'])
        # World coordinates keep texture scale consistent across small and large meshes.
        mapping=ns.new('ShaderNodeVectorMath');mapping.operation='MULTIPLY'
        mapping.inputs[1].default_value=(1,1,1)
        if wood:mapping.inputs[1].default_value=(2.5,2.5,.18)
        elif rock:mapping.inputs[1].default_value=(.7,.7,3.0)
        elif ground and terrain in ('timber','fleece'):mapping.inputs[1].default_value=(1.4,1.4,.2)
        ls.new(position.outputs['Position'],mapping.inputs[0])
        fine=ns.new('ShaderNodeTexNoise');fine.inputs['Scale'].default_value=160 if ground else 65;fine.inputs['Detail'].default_value=2.5
        ls.new(mapping.outputs[0],fine.inputs['Vector'])
        bump=ns.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.32 if not clearing else .14;bump.inputs['Distance'].default_value=.006 if ground else .010
        ls.new(fine.outputs['Fac'],bump.inputs['Height']);ls.new(bump.outputs['Normal'],p.inputs['Normal'])
        if terrain=='desert' and ground and not clearing:
            wave=ns.new('ShaderNodeTexWave');wave.wave_type='BANDS';wave.bands_direction='DIAGONAL';wave.inputs['Scale'].default_value=65;wave.inputs['Distortion'].default_value=3.5;wave.inputs['Detail Scale'].default_value=1.1
            ls.new(position.outputs['Position'],wave.inputs['Vector']);ls.new(wave.outputs['Color'],bump.inputs['Height']);bump.inputs['Distance'].default_value=.005;bump.inputs['Strength'].default_value=.35
        elif terrain in ('stone','desert') and rock and not clearing:
            # Continuous sediment bands and joints wrap across modelled facets.
            # The painted reference is never used as a projected texture.
            source=p.inputs['Base Color'].links[0].from_socket
            strata=ns.new('ShaderNodeTexWave');strata.wave_type='BANDS';strata.bands_direction='Z'
            strata.inputs['Scale'].default_value=3 if terrain=='stone' else 8
            strata.inputs['Distortion'].default_value=3;strata.inputs['Detail Scale'].default_value=1.5
            ls.new(position.outputs['Position'],strata.inputs['Vector'])
            strata_ramp=ns.new('ShaderNodeValToRGB');strata_ramp.color_ramp.elements[0].position=.28;strata_ramp.color_ramp.elements[0].color=(.49,.45,.37,1)
            strata_ramp.color_ramp.elements[1].position=.54;strata_ramp.color_ramp.elements[1].color=(1.17,1.12,1.02,1)
            ls.new(strata.outputs['Fac'],strata_ramp.inputs[0])
            strata_mix=ns.new('ShaderNodeMixRGB');strata_mix.blend_type='MULTIPLY';strata_mix.inputs[0].default_value=.30 if terrain=='stone' else .55
            ls.new(source,strata_mix.inputs[1]);ls.new(strata_ramp.outputs[0],strata_mix.inputs[2]);ls.new(strata_mix.outputs[0],p.inputs['Base Color'])
            if terrain=='stone':
                stretch=ns.new('ShaderNodeVectorMath');stretch.operation='MULTIPLY';stretch.inputs[1].default_value=(1,1,.42)
                ls.new(position.outputs['Position'],stretch.inputs[0])
                joints=ns.new('ShaderNodeTexVoronoi');joints.feature='DISTANCE_TO_EDGE';joints.inputs['Scale'].default_value=15
                ls.new(stretch.outputs[0],joints.inputs['Vector'])
                joints_ramp=ns.new('ShaderNodeValToRGB');joints_ramp.color_ramp.elements[0].position=.012;joints_ramp.color_ramp.elements[0].color=(.23,.27,.30,1)
                joints_ramp.color_ramp.elements[1].position=.055;joints_ramp.color_ramp.elements[1].color=(1,1,1,1)
                ls.new(joints.outputs['Distance'],joints_ramp.inputs[0])
                joints_mix=ns.new('ShaderNodeMixRGB');joints_mix.blend_type='MULTIPLY';joints_mix.inputs[0].default_value=.46
                ls.new(strata_mix.outputs[0],joints_mix.inputs[1]);ls.new(joints_ramp.outputs[0],joints_mix.inputs[2]);ls.new(joints_mix.outputs[0],p.inputs['Base Color'])
                ls.new(joints_ramp.outputs[0],bump.inputs['Height']);bump.inputs['Distance'].default_value=.012;bump.inputs['Strength'].default_value=.38
            else:
                ls.new(strata.outputs['Fac'],bump.inputs['Height']);bump.inputs['Distance'].default_value=.007;bump.inputs['Strength'].default_value=.40

def ground_inlay(terrain):
    """Flat surface markings in the sightline apron: no raised foreground objects.

    The height guard conservatively clamps ground to .003. These thin, coplanar
    botanical/soil markings are at .0036, below the geometry proof's .008 tolerance.
    They stay outside the clearing; real upright plate proofs check the result.
    """
    rng=random.Random(9124)
    palettes={'timber':[(.050,.12,.009),(.13,.25,.016),(.29,.38,.041),(.26,.12,.025)],
              'fleece':[(.055,.17,.007),(.15,.33,.014),(.36,.46,.047),(.46,.48,.18)],
              'grain':[(.69,.41,.046),(.88,.60,.10),(.33,.18,.023),(.57,.34,.04)],
              'clay':[(.38,.065,.015),(.70,.19,.035),(.52,.11,.022),(.84,.29,.065)],
              'stone':[(.24,.22,.16),(.43,.38,.27),(.15,.17,.10),(.37,.31,.19)],
              'desert':[(.56,.31,.085),(.79,.52,.20),(.70,.41,.12),(.88,.59,.24)]}
    bs=[Batch('V3 ground inlay '+str(i),V.mat('V3 flat ground markings '+str(i),c,.16)) for i,c in enumerate(palettes[terrain])]
    z=.0007 if terrain=='timber' else .0037
    for i in range(2200):
        x=rng.uniform(-.78,.78);y=rng.uniform(-.88,.88)
        if not inside(x,y,.917) or token.distance(x,y)<.353 or token.front_cap(x,y)>.008:continue
        a=rng.random()*TAU;l=rng.uniform(.008,.026);w=l*.16
        if terrain in ('clay','stone'):w=l*.60
        if terrain=='desert':l*=1.8;w*=.4;a=-.6+math.sin(y*10)*.18
        # Flat diamonds/needles, no overlap into the number circle.
        vs=[(x-l*.5*math.cos(a),y-l*.5*math.sin(a),z),(x-w*math.sin(a),y+w*math.cos(a),z),(x+l*.5*math.cos(a),y+l*.5*math.sin(a),z),(x+w*math.sin(a),y-w*math.cos(a),z)]
        if any(token.distance(xx,yy)<.335 for xx,yy,zz in vs):continue
        bs[i%4].add(vs,[(0,1,2,3)])
    for b in bs:b.finish(False)

def install(v):
    global V;V=v
    v.tree=pine;v.plants=plants;v.hut=hut;v.logs=logs;v.original_fence=v.fence;v.fence=fence
    v.v2.sheep=sheep
