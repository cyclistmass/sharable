"""Own dense V3 dioramas inspired by Mauro's painted faces; one terrain/process.
No raster projection or downloaded meshes. Blender -b -t 2 -P ... -- --terrain timber.
V2 remains reproducible at its historical left-hand clearing.
"""
import sys,math,random,argparse,json,time,hashlib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import bpy
from mathutils import Vector,Matrix
import token_space as token
token.CX=.36
import render_density as density
import render_v2_set as v2
from render_models import setup,mesh,pebble,cone,tree,pigment
from render_density import Batch,inside
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models';TAU=math.tau
mat=v2.mat;cube=v2.cube;pole=v2.pole

def land(terrain,fn,color,name):
    density.terrain_base(terrain,color,terrain=='clay')
    return v2.field(name,fn,mat('V3 '+terrain+' rich earth',color,.35),terrain!='clay',64,192)

def meadow(x,y):return .025+.10*math.exp(-((x+.38)**2+(y-.24)**2)/.18)+.055*math.exp(-((x-.35)**2+(y+.38)**2)/.15)
def wheat_ground(x,y):return .050+.035*math.sin(x*2.4+.5)*math.cos(y*2)
def pit(x,y):
    xx=(x+.12)/.59;yy=(y+.13)/.56;a=math.atan2(yy,xx)
    d=(abs(xx)**3.6+abs(yy)**3.6)**(1/3.6)
    d*=1+.045*math.sin(7*a)+.029*math.sin(13*a+.7)
    def smooth(a,b):
        t=max(0,min(1,(d-a)/(b-a)));return t*t*(3-2*t)
    return -.145+.105*smooth(.28,.31)+.105*smooth(.54,.57)+.110*smooth(.79,.82)+.040*smooth(.99,1.12)
def dunes(x,y):
    u=.86*x+.51*y+.095*math.sin(y*4)+.035*math.sin(x*5);q=max(abs(x)/(.93*math.sqrt(3)/2),(abs(y)+abs(x)/math.sqrt(3))/.93)
    taper=1-.55*max(0,min(1,(q-.75)/.25))
    phase=(u*1.60+.32)%1
    # Broad windward slope, sharp crest and steeper leeward slip face.
    wave=(phase/.72)**1.45 if phase<.72 else ((1-phase)/.28)**.85
    return .042+(.22*wave+.040*math.exp(-((x+.4)**2+(y+.45)**2)/.10))*taper

def zfn(terrain):
    return {'timber':lambda x,y:0,'fleece':meadow,'grain':wheat_ground,'clay':pit,'desert':dunes,'stone':density.alpine_height_v3}[terrain]

def terrain_z(fn,x,y):return token.ground_height(x,y,fn(x,y))

def plants(fn,seed,count,flowers=True,dry=False):
    rng=random.Random(seed)
    colors=[(.045,.19,.014),(.13,.33,.018),(.29,.46,.04)] if not dry else [(.23,.18,.045),(.35,.28,.085),(.42,.33,.13)]
    batches=[Batch('V3 groundcover leaf mass '+str(i),mat('V3 leaf green '+str(i),c,.32)) for i,c in enumerate(colors)]
    petals=[Batch('V3 flower petals '+str(i),mat('V3 flower colour '+str(i),c,.1)) for i,c in enumerate([(.97,.63,.008),(.96,.87,.65),(.40,.055,.59),(.72,.018,.007)])]
    for i in range(count):
        if dry:
            anchors=[(-.55,.36),(-.54,-.29),(-.23,-.63),(.44,-.48),(.59,-.13),(-.02,.69),(-.64,.02)]
            ax,ay=anchors[i%len(anchors)];x=ax+rng.gauss(0,.085);y=ay+rng.gauss(0,.075)
        else:x=rng.uniform(-.79,.79);y=rng.uniform(-.91,.91)
        if not inside(x,y,.915) or token.distance(x,y)<.35:continue
        zz=fn(x,y)
        if zz<-.01:continue
        zz=terrain_z(fn,x,y);h=rng.uniform(.020,.064) if not dry else rng.uniform(.025,.08)
        if h+zz>token.front_cap(x,y):continue
        b=batches[i%3]
        for j in range(4 if dry else 6):
            a=rng.random()*TAU;b.blade(x+rng.uniform(-.012,.012),y+rng.uniform(-.012,.012),zz,h,a,.006 if dry else .009)
        if flowers and i%4==0:
            b.rod((x,y,zz),(x,y,zz+h+.01),.0015,4)
            p=petals[(i//4)%4]
            for j in range(5):
                a=j*TAU/5;p.ellipsoid((x+.007*math.cos(a),y+.007*math.sin(a),zz+h+.01),(.007,.005,.003),a,n=5,rings=3)
    for b in batches+petals:b.finish(False)

def rubble(fn,seed,count,color=(.28,.28,.19),size=.025):
    rng=random.Random(seed);b=Batch('V3 assorted pebbles and chipped ground',mat('V3 rubble mottled',color,.42))
    for i in range(count):
        x=rng.uniform(-.77,.77);y=rng.uniform(-.87,.87)
        if not inside(x,y,.91) or token.distance(x,y)<.35:continue
        s=rng.uniform(size*.28,size);zz=terrain_z(fn,x,y)
        if zz+s>token.front_cap(x,y):continue
        b.ellipsoid((x,y,zz+s*.3),(s,s*.75,s*.6),rng.random()*TAU,n=7,rings=3)
    b.finish(False)

def fence(fn,a,b,n=9,stone=False,gate=False):
    rng=random.Random(504);wood=mat('V3 aged fence timber',(.24,.10,.025),.35);wall=Batch('V3 long dry stone wall',mat('V3 lichen stone wall',(.32,.34,.22),.35))
    for i in range(n):
        t=i/(n-1);x=a[0]+(b[0]-a[0])*t;y=a[1]+(b[1]-a[1])*t;z=fn(x,y)
        if gate and .35<t<.58:continue
        if stone:
            for j in range(3):wall.ellipsoid((x+(j%2)*.018,y+rng.uniform(-.009,.009),z+.027+j*.038),(.046,.034,.027),rng.random()*.5,n=8,rings=3)
        elif i%2==0:pole('V3 fence post',(x,y,z),(x,y,z+.15),.011,wood,6)
    wall.finish(False)
    if not stone:
        for h in [.055,.115]:pole('V3 field fence rail',(a[0],a[1],fn(*a)+h),(b[0],b[1],fn(*b)+h),.008,wood,6)
    if gate:
        aa=(a[0]+(b[0]-a[0])*.35,a[1]+(b[1]-a[1])*.35);bb=(a[0]+(b[0]-a[0])*.58,a[1]+(b[1]-a[1])*.58)
        fence(fn,aa,bb,3)
        pole('V3 gate diagonal brace',(*aa,fn(*aa)+.03),(*bb,fn(*bb)+.15),.007,wood,5)

def hut(fn,x,y,w=.26):
    wood=mat('V3 logging shed planks',(.29,.12,.031),.35);roof=mat('V3 weathered roof shakes',(.20,.10,.040),.38);z=fn(x,y)
    for yy in [y-w*.37,y+w*.37]:
        for i in range(7):cube('V3 hut split boards',(x-w/2+i*w/6,yy,z+.09),(w/7,.016,.17),wood,.003)
    vs=[(x-w*.6,y-w*.48,z+.18),(x+w*.6,y-w*.48,z+.18),(x-w*.6,y,z+.28),(x+w*.6,y,z+.28),(x-w*.6,y+w*.48,z+.18),(x+w*.6,y+w*.48,z+.18)]
    mesh('V3 hut pitched shakes roof',vs,[(0,1,3,2),(2,3,5,4)],roof)
    for i in range(9):
        xx=x-w*.58+i*w*.145
        pole('V3 raised roof shake seam',(xx,y-w*.48,z+.186),(xx,y,z+.286),.004,wood,4)
    cube('V3 black hut doorway',(x,y-w*.39,z+.06),(.074,.012,.12),mat('V3 dark hut door',(.026,.015,.008),0),.003)

def logs(fn,x,y,count=5):
    bark=mat('V3 dark ridged log bark',(.17,.06,.014),.4);end=mat('V3 cut golden log ends',(.68,.36,.10),.2)
    for i in range(count):
        xx=x+(i%3)*.045;zz=fn(xx,y)+.029+(i//3)*.043
        pole('V3 camp cut log',(xx,y-.12,zz),(xx,y+.12,zz),.026,bark,10)
        pole('V3 visible log cut end',(xx,y-.121,zz),(xx,y-.126,zz),.023,end,12)
        for offset in [-.009,.007]:pole('V3 log growth rings',(xx+offset,y-.127,zz-.015),(xx+offset,y-.127,zz+.015),.0012,bark,4)

def rails(fn,a,b):
    wood=mat('V3 worn railway sleepers',(.19,.078,.022),.32);iron=mat('V3 rusty rail iron',(.21,.10,.038),.15)
    dx=b[0]-a[0];dy=b[1]-a[1];l=math.hypot(dx,dy);nx=-dy/l;ny=dx/l
    for i in range(18):
        t=i/17;x=a[0]+dx*t;y=a[1]+dy*t;z=fn(x,y)+.014
        pole('V3 rail sleeper',(x-nx*.065,y-ny*.065,z),(x+nx*.065,y+ny*.065,z),.008,wood,4)
    for s in [-1,1]:
        for i in range(17):
            t=i/17;tt=(i+1)/17;x=a[0]+dx*t+s*nx*.042;y=a[1]+dy*t+s*ny*.042;xx=a[0]+dx*tt+s*nx*.042;yy=a[1]+dy*tt+s*ny*.042
            pole('V3 narrow gauge rail',(x,y,fn(x,y)+.025),(xx,yy,fn(xx,yy)+.025),.0045,iron,5)

def crane(fn,x,y,headframe=False):
    wood=mat('V3 mine crane weathered oak',(.25,.105,.025),.32);rope=mat('V3 tarred hoist cable',(.038,.023,.009),0);iron=mat('V3 hoist wheel iron',(.16,.12,.06),.1);z=fn(x,y);h=.42 if headframe else .34
    for xx in [x-.07,x+.07]:
        for yy in [y-.05,y+.05]:pole('V3 hoist slanted leg',(xx,yy,z),(x+(xx-x)*.65,y+(yy-y)*.65,z+h),.015,wood,6)
    for zz in [.10,.25,h]:
        pole('V3 hoist crossbar',(x-.074,y-.056,z+zz),(x+.074,y-.056,z+zz),.011,wood,6)
    for s in [-1,1]:pole('V3 hoist diagonal brace',(x-.07,y+s*.056,z+.04),(x+.07,y+s*.056,z+.28),.009,wood,6)
    end=(x+.24,y+.08,z+h+.10)
    pole('V3 angled crane jib',(x-.06,y,z+h),end,.018,wood,6);pole('V3 crane jib support',(x+.06,y,z+h-.10),end,.012,wood,6)
    pole('V3 dangling cable',end,(end[0],end[1],fn(end[0],end[1])+.08),.0028,rope,5)
    bpy.ops.mesh.primitive_torus_add(major_radius=.030,minor_radius=.006,major_segments=16,minor_segments=5,location=(x,y,z+h+.025),rotation=(math.pi/2,0,0));bpy.context.object.name='V3 hoist winding wheel';bpy.context.object.data.materials.append(iron)

def timber():
    fn=lambda x,y:0;density.terrain_base('timber',(.085,.20,.018));rng=random.Random(3817)
    bark=mat('V3 forest resin bark',(.18,.064,.014),.35);greens=[mat('V3 rich forest canopy '+str(i),c,.24) for i,c in enumerate([(.015,.13,.030),(.055,.30,.028),(.19,.46,.022),(.48,.69,.065)])]
    points=[]
    for row in range(12):
        for col in range(11):
            x=-.75+col*.149+rng.uniform(-.031,.031);y=-.83+row*.15+rng.uniform(-.027,.027)
            if not inside(x,y,.91) or token.distance(x,y)<.39:continue
            # Clear a logging path and camp, not a second empty circle.
            if abs(x+.28)<.115 and -.70<y<.43:continue
            if abs(x+.51)<.14 and abs(y+.24)<.18:continue
            if abs(x+.12)<.18 and abs(y+.54)<.14:continue
            if abs(x+.10)<.22 and .46<y<.83:continue
            h=rng.uniform(.35,.66)
            h=min(h,(token.distance(x,y)-.36)/.40,max(.08,min(token.front_cap(x-.35*h,y-.35*h),token.front_cap(x+.35*h,y-.35*h))*.90))
            if h<.14:continue
            before=set(bpy.context.scene.objects);tree(x,y,h,2,rng,(bark,greens))
            bpy.context.view_layer.update()
            objects=set(bpy.context.scene.objects)-before
            if any(not token.allowed(*(obj.matrix_world@v.co),.025) for obj in objects for v in obj.data.vertices):
                for obj in objects:bpy.data.objects.remove(obj,do_unlink=True)
            else:points.append((x,y,h))
    hut(fn,-.11,.59,.26);logs(fn,-.51,-.24,6);logs(fn,-.12,-.54,5)
    cut=mat('V3 stump golden cut',(.62,.31,.065),.30)
    for x,y in [(-.24,-.15),(-.36,-.57),(.04,-.10),(-.67,.11)]:
        cone('V3 cut forest stump',(x,y,0),.050,.066,bark,12,top=.044);cone('V3 flat stump rings',(x,y,.066),.043,.003,cut,24,top=.043)
        for j in range(5):a=j*TAU/5;pole('V3 stump exposed roots',(x,y,.02),(x+.079*math.cos(a),y+.079*math.sin(a),.005),.012,bark,5)
    plants(fn,481,1700);rubble(fn,283,230,(.23,.29,.12),.045)
    return {'trees':len(points),'story':'logging camp, cut logs, stumps, dense flowering understorey'}

def fleece():
    density.pasture=meadow;v2.pasture=meadow
    land('fleece',meadow,(.12,.31,.019),'lush pasture hills')
    fence(meadow,(-.62,.35),(.03,.77),20,True,True);fence(meadow,(-.16,-.73),(.65,-.42),20,True)
    for i,(x,y,h) in enumerate([(-.45,.08,.3),(-.28,.18,-.4),(-.13,-.08,2.8),(-.41,-.31,.2),(-.24,-.45,-.7),(-.04,-.40,2.5),(.17,-.70,2.6)]):
        before=set(bpy.context.scene.objects);v2.sheep(x,y,h,.4+i*.5)
        group=set(bpy.context.scene.objects)-before
        bpy.context.view_layer.update()
        assert all(token.allowed(*(obj.matrix_world@v.co),.025) for obj in group for v in obj.data.vertices),('whole sheep must survive clearing guard',i,x,y)
    from alpacas import alpaca
    for i,(x,y,h) in enumerate([(-.18,.48,.1),(-.54,-.14,-.7),(-.05,-.59,2.4)]):
        before=set(bpy.context.scene.objects);alpaca(x,y,h,1.2+i,i,upright=i==0);anchor=Vector((x,y,token.ground_height(x,y,meadow(x,y))))
        for obj in set(bpy.context.scene.objects)-before:
            obj.location=anchor+(obj.location-anchor)*.65;obj.scale*=.65
        bpy.context.view_layer.update()
        assert all(token.allowed(*(obj.matrix_world@v.co),.025) for obj in set(bpy.context.scene.objects)-before for v in obj.data.vertices),('whole small alpaca must survive clearing guard',i)
    for x,y,h in [(-.56,-.53,.7),(-.54,.56,-.7)]:
        before=set(bpy.context.scene.objects);density.goat(x,y,h,1.4,meadow(x,y));anchor=Vector((x,y,meadow(x,y)))
        for obj in set(bpy.context.scene.objects)-before:obj.location=anchor+(obj.location-anchor)*.75;obj.scale*=.75
        bpy.context.view_layer.update()
        assert all(token.allowed(*(obj.matrix_world@v.co),.025) for obj in set(bpy.context.scene.objects)-before for v in obj.data.vertices),'whole tiny goat must survive clearing guard'
    plants(meadow,103,2300);rubble(meadow,801,180,(.26,.30,.18),.035)
    bush=Batch('V3 meadow flowering hedges',mat('V3 hedge dark leaf',(.026,.16,.007),.3));rng=random.Random(461)
    for x,y in [(-.68,-.21),(-.26,.69),(.0,.70),(.61,-.27),(.11,-.74),(-.64,.28)]:
        for i in range(10):xx=x+rng.uniform(-.05,.05);yy=y+rng.uniform(-.04,.04);bush.ellipsoid((xx,yy,meadow(xx,yy)+.040),(.045,.036,.042),n=8,rings=4)
    bush.finish();return {'sheep':7,'small_alpacas':3,'alpaca_relative_scale':.65,'teeny_goats':2}

def grain():
    from v3_art import wheat_ear
    density.field_ground=wheat_ground;ground=land('grain',wheat_ground,(.43,.22,.024),'rolling field ground');rng=random.Random(1309)
    bpy.data.objects.remove(ground,do_unlink=True)
    gold=[mat('V3 dense wheat honey '+str(i),c,.18) for i,c in enumerate([(.95,.53,.035),(1,.72,.085),(1,.86,.20)])]
    stooks=[(-.17,-.42),(.14,-.66),(-.60,.25),(-.18,.62)]
    def crop_height(x,y):
        edge=.93-abs(y)-abs(x)/math.sqrt(3);side=.93*math.sqrt(3)/2-abs(x)
        edge_mask=max(0,min(1,min(edge,side)/.045))
        circle=max(0,min(1,(token.distance(x,y)-.355)/.095))
        path=max(0,min(1,(abs(y+.36-x*.20)-.050)/.035))
        cart=max(0,min(1,max(abs(x+.48)/.15,abs(y+.34)/.14)-1))
        cap=token.front_cap(x,y)-wheat_ground(x,y)-.045
        harvested=min(max(0,min(1,(math.hypot(x-sx,y-sy)-.080)/.075)) for sx,sy in stooks)
        return max(0,min(.205+.021*math.sin(y*90+x*5),cap))*edge_mask*circle*path*cart*harvested
    skin=v2.field('rolling field ground continuous wheat canopy',lambda x,y:wheat_ground(x,y)+.001+crop_height(x,y)*.78,mat('V3 soft wheat leaf mass',(.67,.37,.025),.28),True,76,256)
    skin.data.materials.append(mat('V3 threshed field soil',(.43,.22,.024),.35))
    for poly in skin.data.polygons:
        x,y=[sum(skin.data.vertices[i].co[k] for i in poly.vertices)/len(poly.vertices) for k in range(2)]
        if crop_height(x,y)<.015:poly.material_index=1
    batches=[Batch('V3 wheat sculpted ears '+str(i),m) for i,m in enumerate(gold)];ears=0
    for row in range(36):
        for col in range(80):
            x=-.79+col*.020;y=-.88+row*.050+.040*math.sin(x*3+row*.04)
            if not inside(x,y,.915) or crop_height(x,y)<.018:continue
            for j in range(1):
                xx=x+rng.uniform(-.009,.009);yy=y+rng.uniform(-.012,.012);z=wheat_ground(xx,yy);h=crop_height(xx,yy)
                if h<.018:continue
                hh=h+rng.uniform(.030,.095);b=batches[(row+col+j)%3]
                bend=.015+.012*math.sin(xx*12+yy*9)
                b.rod((xx,yy,z),(xx+bend*.3,yy,z+hh*.6),.0018,4)
                b.rod((xx+bend*.3,yy,z+hh*.6),(xx+bend,yy,z+hh),.0015,4)
                wheat_ear(b,(xx+bend,yy,z+hh-.015),rng.uniform(-.8,.8),rng.uniform(.86,1.12))
                b.blade(xx,yy,z+hh*.36,hh*.48,rng.random()*TAU,.008)
                ears+=1
    for b in batches:b.finish()
    rope=mat('V3 wheat sheaf rope',(.20,.07,.008),.1)
    sheaf_ears=Batch('V3 full sculpted stook heads',gold[1])
    for x,y in stooks:
        z=wheat_ground(x,y);cap=min(token.front_cap(x+dx,y+dy) for dx,dy in [(-.055,.055),(.055,.055),(0,.055)])
        height=min(.35,max(.15,cap-z-.055))
        for i in range(38):
            a=i*TAU/38;rr=.066+.015*math.sin(i*2.4);hh=height+rng.uniform(-.030,.020)
            waist=(x+.025*math.cos(a),y+.025*math.sin(a),z+height*.44)
            pole('V3 sheaf splayed cut straw',(x+.067*math.cos(a),y+.067*math.sin(a),z),waist,.0045,gold[i%3],5)
            pole('V3 sheaf fanning stems',waist,(x+rr*math.cos(a),y+rr*math.sin(a),z+hh),.0035,gold[i%3],5)
            for k in range(4):
                sheaf_ears.ellipsoid((x+rr*math.cos(a),y+rr*math.sin(a),z+hh-.020+k*.013),(.009,.007,.009),a,n=6,rings=3)
        for k in range(3):
            bpy.ops.mesh.primitive_torus_add(major_radius=.028,minor_radius=.003,major_segments=20,minor_segments=4,location=(x,y,z+height*.44+(k-1)*.005));bpy.context.object.data.materials.append(rope)
    sheaf_ears.finish()
    # A loaded cart on the foreground-left harvest path.
    wood=mat('V3 cart warm oak',(.27,.105,.021),.32);x=-.48;y=-.34;z=wheat_ground(x,y)
    cube('V3 harvest cart platform',(x,y,z+.057),(.23,.15,.022),wood,.005)
    for yy in [y-.078,y+.078]:
        for i in range(2):cube('V3 harvest cart side slat',(x,yy,z+.085+i*.034),(.24,.012,.028),wood,.003)
        bpy.ops.mesh.primitive_torus_add(major_radius=.047,minor_radius=.007,major_segments=16,minor_segments=6,location=(x,yy,z+.045),rotation=(math.pi/2,0,0));bpy.context.object.data.materials.append(wood)
        for i in range(4):a=i*math.pi/4;pole('V3 cart wheel spokes',(x-.044*math.cos(a),yy,z+.045-.044*math.sin(a)),(x+.044*math.cos(a),yy,z+.045+.044*math.sin(a)),.003,wood,5)
    for xx in [x-.08,x+.08]:pole('V3 harvest cart handles',(xx,y,z+.06),(xx,y-.27,z+.02),.007,wood,5)
    load=Batch('V3 loose wheat in cart',gold[1])
    for i in range(75):load.ellipsoid((x+rng.uniform(-.09,.09),y+rng.uniform(-.06,.06),z+.12+rng.uniform(-.012,.025)),(.014,.022,.016),n=6,rings=3)
    load.finish();fence(wheat_ground,(-.57,.56),(.04,.83),11);fence(wheat_ground,(.41,-.61),(.68,-.29),7)
    plants(lambda x,y:wheat_ground(x,y)+crop_height(x,y),826,1000);rubble(wheat_ground,613,140,(.38,.25,.065),.025)
    return {'wheat_ears':ears,'curved_rows':36,'story':'full fields, harvest path, cinched fanning sheaves, fence and loaded cart'}

def clay():
    density.clay_height=pit;earth=land('clay',pit,(.78,.15,.025),'deep excavated clay terraces');wet=mat('V3 dark wet red clay',(.21,.035,.015),.35);cut=mat('V3 orange red pit faces',(.56,.075,.016),.35)
    earth.data.materials.append(wet);earth.data.materials.append(cut)
    for p in earth.data.polygons:
        z=sum(earth.data.vertices[i].co.z for i in p.vertices)/len(p.vertices)
        if z<-.045:p.material_index=1
        elif p.normal.z<.7:p.material_index=2
    brick=mat('V3 fired orange brick',(.49,.092,.017),.3);dark=mat('V3 kiln dark mortar',(.10,.029,.011),.25);x=-.10;y=.55;z=pit(x,y)
    cone('V3 round brick kiln',(x,y,z),.12,.22,dark,32,top=.095)
    # Individual staggered brick courses make the kiln legible from above too.
    for row in range(5):
        for i in range(16):
            a=(i+(row%2)*.5)*TAU/16;r=.118-row*.005
            o=cube('V3 kiln round brick courses',(x+r*math.cos(a),y+r*math.sin(a),z+.021+row*.043),(.037,.024,.038),brick,.002);o.rotation_euler.z=a+math.pi/2
    cone('V3 kiln chimney',(x,y,z+.21),.055,.31,brick,24,top=.037)
    cone('V3 dark chimney opening',(x,y,z+.519),.029,.005,dark,24,top=.029)
    cube('V3 kiln fire mouth',(x,y-.123,z+.053),(.070,.015,.083),dark,.015)
    smoke=mat('V3 soft kiln smoke',(.57,.52,.45),.12)
    # Own lightweight translucent procedural smoke sculpture, static in this round.
    ns=smoke.node_tree.nodes;ls=smoke.node_tree.links;output=ns.get('Material Output')
    for link in list(output.inputs['Surface'].links):ls.remove(link)
    volume=ns.new('ShaderNodeVolumePrincipled');volume.inputs['Color'].default_value=(.72,.70,.66,1)
    noise=ns.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=4;noise.inputs['Detail'].default_value=2
    density_node=ns.new('ShaderNodeMath');density_node.operation='MULTIPLY';density_node.inputs[1].default_value=6
    ls.new(noise.outputs['Fac'],density_node.inputs[0]);ls.new(density_node.outputs[0],volume.inputs['Density']);ls.new(volume.outputs[0],output.inputs['Volume'])
    # Fade density toward each puff's boundary instead of exposing sphere edges.
    coord=ns.new('ShaderNodeTexCoord');distance=ns.new('ShaderNodeVectorMath');distance.operation='DISTANCE';distance.inputs[1].default_value=(.5,.5,.5)
    ls.new(coord.outputs['Generated'],distance.inputs[0])
    fade=ns.new('ShaderNodeMapRange');fade.inputs['From Min'].default_value=.16;fade.inputs['From Max'].default_value=.51;fade.inputs['To Min'].default_value=1;fade.inputs['To Max'].default_value=0
    ls.new(distance.outputs['Value'],fade.inputs['Value']);multiply=ns.new('ShaderNodeMath');multiply.operation='MULTIPLY'
    ls.new(fade.outputs[0],multiply.inputs[0]);ls.new(density_node.outputs[0],multiply.inputs[1]);ls.new(multiply.outputs[0],volume.inputs['Density'])
    for i in range(7):pebble((x+.02*math.sin(i*.9),y+.014*i,z+.54+i*.039),(.025+i*.004,.026+i*.003,.047),smoke,True)
    for bx,by,rows in [(-.56,-.41,4),(-.29,-.60,5),(.05,-.62,3),(-.07,.62,3),(.64,-.19,3)]:
        for layer in range(rows):
            for j in range(6):cube('V3 large stacked brick pallets',(bx+(j%3)*.046,by+(j//3)*.030,pit(bx,by)+.013+layer*.026),(.042,.026,.023),brick,.002)
    crane(pit,-.60,-.04);rails(pit,(-.60,-.33),(.41,-.61));hut(pit,-.50,.53,.20)
    wood=mat('V3 clay drying rack',(.23,.081,.020),.3)
    for bx,by in [(-.69,.23),(.59,-.39)]:
        zz=pit(bx,by)
        for xx in [bx-.07,bx+.07]:pole('V3 drying rack legs',(xx,by,zz),(xx,by,zz+.13),.008,wood,5)
        for level in [.055,.12]:
            cube('V3 drying rack shelf',(bx,by,zz+level),(.19,.105,.012),wood,.003)
            for j in range(4):cube('V3 drying rack clay brick',(bx-.065+j*.045,by,zz+level+.015),(.039,.055,.023),brick,.002)
    rubble(pit,903,350,(.40,.070,.015),.030);plants(pit,114,900)
    return {'story':'red terraced excavation, round kiln with smoke, hoist crane, rail line, brick pallets and drying racks'}

def stone():
    import alpine_chain
    low_detail=alpine_chain.height
    def detailed_height(x,y):
        h=low_detail(x,y);u=TAU*x/alpine_chain.WIDTH+math.pi*y/alpine_chain.ROW;v=TAU*y/alpine_chain.ROW
        erosion=(.024*math.sin(9*u+5*v)*math.sin(4*u-7*v)+.012*math.sin(15*u-3*v))*min(1,h/.30)
        return max(.014,h+erosion)
    alpine_chain.height=detailed_height
    result=alpine_chain.build();original=alpine_chain.height
    for obj in list(bpy.context.scene.objects):
        if obj.name.startswith(('visible trunk','sculpted pine boughs')):bpy.data.objects.remove(obj,do_unlink=True)
    # Mirror the R3 terrain, moving its mass away from the new top-right shelf.
    reflection=Matrix.Diagonal(Vector((-1,1,1,1)))
    for obj in list(bpy.context.scene.objects):
        if obj.type=='MESH' and not obj.name.startswith('thick hex'):
            obj.matrix_world=reflection@obj.matrix_world
            # Bake reflection and correct polygon winding for lighting.
            obj.data.transform(obj.matrix_world);obj.matrix_world=Matrix.Identity(4)
            import bmesh
            bm=bmesh.new();bm.from_mesh(obj.data);bmesh.ops.reverse_faces(bm,faces=list(bm.faces));bm.to_mesh(obj.data);bm.free()
    fn=lambda x,y:original(-x,y)+.004
    density.alpine_height_v3=fn;alpine_chain.height=lambda x,y:original(-x,y)
    crane(fn,-.50,-.22,True);rails(fn,(-.58,-.57),(.07,-.65))
    rock=mat('V3 cut quarry block pale rock',(.36,.32,.20),.35)
    crag=mat('V3 angular protruding alpine crags',(.29,.21,.11),.35)
    for x,y in [(-.48,.05),(-.28,.15),(-.11,.36),(-.19,-.36),(-.59,-.12),(-.35,.44),(-.60,.35)]:
        for j in range(3):
            xx=x+.038*(j-1);yy=y+.025*math.sin(j*2)
            pebble((xx,yy,fn(xx,yy)+.016),(.066,.054,.061),crag)
    for x,y,h in [(-.61,-.26,.08),(-.42,-.43,.12),(-.26,-.61,.07),(-.12,-.56,.10),(-.66,.14,.09)]:
        for j in range(3):cube('V3 quarried stone stacks',(x+(j%2)*.070,y+(j//2)*.068,fn(x,y)+h/2),(.064,.061,h),rock,.008)
    rng=random.Random(774);bark=mat('V3 alpine pine bark',(.14,.055,.009),.2);greens=[mat('V3 alpine deep canopy '+str(i),c,.26) for i,c in enumerate([(.015,.10,.009),(.035,.20,.013),(.095,.32,.022),(.23,.38,.028)])]
    pine_count=0
    for x,y in [(-.67,-.44),(-.49,-.55),(-.30,-.66),(-.11,-.77),(-.73,-.21),(-.70,.12),(-.69,.35),(-.54,-.27),(-.41,-.49),(-.15,-.69)]:
        if fn(x,y)>.65 or not inside(x,y,.91):continue
        h=rng.uniform(.16,.25);before=set(bpy.context.scene.objects);tree(x,y,h,2,rng,(bark,greens))
        group=set(bpy.context.scene.objects)-before
        for obj in group:obj.location.z+=fn(x,y)
        bpy.context.view_layer.update()
        if any(not token.allowed(*(obj.matrix_world@v.co),.025) for obj in group for v in obj.data.vertices):
            for obj in group:bpy.data.objects.remove(obj,do_unlink=True)
        else:pine_count+=1
    plants(lambda x,y:fn(x,y) if fn(x,y)<.40 else -.10,531,1100);rubble(fn,726,650,(.25,.19,.10),.033)
    return {**result,'summits':[(-x,y,h) for x,y,h in result['summits']],
            'mirrored_R3':True,'periodic_erosion_amplitude':.036,'pines':pine_count,
            'story':'mirrored R3 Alpine chain, pine fringes, mine hoist, rails and quarried blocks'}

def desert():
    density.dune_height=dunes;land('desert',dunes,(.72,.43,.12),'solid dunes with actual fine ripples');rng=random.Random(704)
    rocks=[mat('V3 layered sandstone '+str(i),c,.35) for i,c in enumerate([(.30,.15,.035),(.46,.23,.055),(.61,.34,.105)])]
    for x,y,size in [(-.56,.37,.13),(-.51,-.31,.11),(-.24,-.61,.12),(.43,-.48,.12),(.60,-.10,.09),(-.02,.68,.11),(-.64,.04,.07)]:
        for j in range(4):
            zz=dunes(x,y)+j*size*.18
            pebble((x+.01*j,y,zz),(size*(1-.12*j),size*.72*(1-.1*j),size*.23),rocks[j%3])
        for j in range(7):xx=x+rng.uniform(-.10,.10);yy=y+rng.uniform(-.09,.09);s=rng.uniform(.013,.031);pebble((xx,yy,dunes(xx,yy)+s*.4),(s,s*.8,s*.7),rocks[j%3])
    bark=mat('V3 bleached dead tree wood',(.42,.27,.10),.32);x=-.36;y=.14;z=dunes(x,y)
    pole('V3 bare dead tree trunk',(x,y,z),(x+.018,y,z+.30),.019,bark,7)
    for a,b in [((x,y,z+.10),(x-.10,y+.02,z+.23)),((x-.10,y+.02,z+.23),(x-.13,y+.03,z+.29)),((x+.01,y,z+.19),(x+.10,y-.01,z+.25)),((x+.10,y-.01,z+.25),(x+.12,y+.01,z+.32)),((x+.01,y,z+.28),(x-.03,y-.02,z+.36))]:pole('V3 dead tree fork',a,b,.009,bark,5)
    plants(dunes,920,95,flowers=False,dry=True);rubble(dunes,682,70,(.53,.32,.11),.017)
    return {'story':'sculpted wind-ripple dunes, layered sandstone clusters, dry scrub and dead tree'}

def rich_materials(scene):
    # Saturated display transform and real geometry AO, not baked painted relief.
    scene.view_settings.view_transform='Standard';scene.view_settings.look='None';scene.view_settings.exposure=.10
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.55
    for obj in scene.objects:
        if obj.type=='LIGHT':
            obj.data.color=(1,.87,.70)
            if obj.data.type=='AREA':obj.data.energy=400;obj.data.size=3.5
            else:obj.data.energy=2.0
    used={m for obj in scene.objects if obj.type=='MESH' for m in obj.data.materials if m}
    for material in used:
        if material.name.startswith(('earth edge','moss ground')):continue
        nodes=material.node_tree.nodes;links=material.node_tree.links;p=nodes.get('Principled BSDF');inp=p.inputs['Base Color']
        source=inp.links[0].from_socket if inp.is_linked else None;ao=nodes.new('ShaderNodeAmbientOcclusion');ao.inputs['Distance'].default_value=.14;ao.inputs['Color'].default_value=(1,1,1,1)
        multiply=nodes.new('ShaderNodeMixRGB');multiply.blend_type='MULTIPLY';multiply.inputs[0].default_value=.45
        if source:links.new(source,multiply.inputs[1])
        else:multiply.inputs[1].default_value=inp.default_value
        if 'rich earth' in material.name or material.name.startswith('timber ground'):
            fine=nodes.new('ShaderNodeTexNoise');fine.inputs['Scale'].default_value=75;fine.inputs['Detail'].default_value=3
            ramp=nodes.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.32;ramp.color_ramp.elements[0].color=(.46,.50,.28,1);ramp.color_ramp.elements[1].position=.65;ramp.color_ramp.elements[1].color=(1.3,1.2,.82,1)
            detail=nodes.new('ShaderNodeMixRGB');detail.blend_type='MULTIPLY';detail.inputs[0].default_value=.40
            if source:links.new(source,detail.inputs[1])
            else:detail.inputs[1].default_value=inp.default_value
            links.new(fine.outputs['Fac'],ramp.inputs[0]);links.new(ramp.outputs[0],detail.inputs[2]);links.new(detail.outputs[0],multiply.inputs[1])
        links.new(ao.outputs['Color'],multiply.inputs[2]);links.new(multiply.outputs[0],inp)
        bump=nodes.new('ShaderNodeBump');noise=nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=90;noise.inputs['Detail'].default_value=2
        bump.inputs['Strength'].default_value=.18;bump.inputs['Distance'].default_value=.007
        links.new(noise.outputs['Fac'],bump.inputs['Height']);links.new(bump.outputs['Normal'],p.inputs['Normal'])

def validate_geometry(scene):
    bpy.context.view_layer.update()
    surfaces=('thick hex','lush pasture hills','rolling field ground','deep excavated clay terraces',
              'solid dunes with actual fine ripples','R3 seven-peak Alpine chain connected bedrock')
    fore=[];intrusions=0;checked=0
    for obj in scene.objects:
        if obj.type!='MESH' or obj.name.endswith('flat number clearing'):continue
        surface=obj.name.startswith(surfaces)
        for vertex in obj.data.vertices:
            p=obj.matrix_world@vertex.co;checked+=1
            if not surface and token.distance(p.x,p.y)<token.CLEAR_R+.0249:intrusions+=1
            if p.y<token.CY and p.z>.008:fore.append((p.x,p.y,p.z))
    counts=[]
    for i in range(8):
        t=.96*i/7;c=math.cos(t);s=math.sin(t)
        n=sum((x-token.CX)**2+(y*c+z*s-token.CY*c)**2<token.TOKEN_R**2 for x,y,z in fore)
        counts.append(n)
    assert intrusions==0 and not any(counts),(intrusions,counts)
    return {'mesh_vertices_checked':checked,'prop_vertices_inside_clearing':intrusions,
            'foreground_vertices_over_plate_at_eight_steps':counts,'plate_center':[token.CX,token.CY]}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--terrain',required=True,choices=['timber','stone','fleece','grain','clay','desert']);parser.add_argument('--validate-only',action='store_true');parser.add_argument('--views',nargs='+',choices=['flat','mid','max'],default=['flat','mid','max']);parser.add_argument('--samples',type=int,default=64);args=parser.parse_args(sys.argv[sys.argv.index('--')+1:]);name=args.terrain
    import v3_art
    v3_art.install(sys.modules[__name__])
    global ACTIVE_TERRAIN
    ACTIVE_TERRAIN=name
    import v3_finish, v3_story
    scene,cam=setup();scene.cycles.samples=args.samples;scene.render.threads_mode='FIXED';scene.render.threads=2
    density.alpine_height_v3=lambda x,y:0
    metadata=globals()[name]();v3_art.enhance(name);metadata['finish']=v3_finish.enrich(name,sys.modules[__name__]);metadata['story_pass']=v3_story.enrich(name,sys.modules[__name__]);bpy.context.view_layer.update();metadata['token_space']=token.prepare_scene(name);v3_art.ground_inlay(name);v3_story.apron(name,sys.modules[__name__]);rich_materials(scene);v3_art.materials(scene,name);v3_finish.materials(scene,name)
    folder=OUT/'v3';folder.mkdir(exist_ok=True);result=validate_geometry(scene)
    sources=['render_v3.py','v3_art.py','v3_finish.py','v3_story.py','render_density.py','render_v2_set.py','render_models.py','token_space.py','alpine_chain.py','alpacas.py']
    source_hashes={s:hashlib.sha256((Path(__file__).parent/s).read_bytes()).hexdigest() for s in sources}
    result['source_hashes']=source_hashes
    (folder/f'{name}-geometry-validation.json').write_text(json.dumps(result,indent=2));print('V3_GEOMETRY_OK',name,json.dumps(result),flush=True)
    if args.validate_only:return
    records=[]
    for angle,t in v2.angles:
        if angle not in args.views:continue
        v2.camera(cam,t);scene.render.filepath=str(OUT/f'{name}_V3_{angle}.png');start=time.monotonic();bpy.ops.render.render(write_still=True)
        records.append({'file':f'{name}_V3_{angle}.png','tilt_radians':t,'seconds':round(time.monotonic()-start,2),
                        'sha256':hashlib.sha256(Path(scene.render.filepath).read_bytes()).hexdigest()})
        print('V3_RENDER',records[-1],flush=True)
    folder=OUT/'v3';folder.mkdir(exist_ok=True)
    (folder/f'{name}-manifest.json').write_text(json.dumps({'metadata':metadata,'renders':records,'objects':len(scene.objects),'samples':args.samples,'threads':2,'source_hashes':source_hashes},indent=2))

if __name__=='__main__':main()
