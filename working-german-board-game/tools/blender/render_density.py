"""Own V2 density pass. One terrain per Blender process; merged small geometry.
blender -b -t 2 -P tools/blender/render_density.py -- --terrain fleece
blender -b -t 2 -P tools/blender/render_density.py -- --animals-frame 0
The caller MUST wait for this process to exit before launching the next batch.
"""
import sys,math,random,json,time,argparse,hashlib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import bpy
from mathutils import Vector
import render_v2_set as v2
from render_models import setup, OUT, R, mesh, pebble, cone, pigment

from token_space import prepare_scene
TAU=math.tau
mat=v2.mat;cube=v2.cube;pole=v2.pole

class Batch:
    """One mesh per material instead of thousands of independent Blender objects."""
    def __init__(self,name,m):self.name=name;self.m=m;self.v=[];self.f=[]
    def add(self,vs,fs):
        offset=len(self.v);self.v.extend(vs);self.f.extend(tuple(offset+i for i in f) for f in fs)
    def ellipsoid(self,c,s,heading=0,n=8,rings=4):
        vs=[(c[0],c[1],c[2]+s[2])]
        for j in range(1,rings):
            p=math.pi*j/rings
            for i in range(n):
                a=TAU*i/n;xx=s[0]*math.sin(p)*math.cos(a);yy=s[1]*math.sin(p)*math.sin(a)
                vs.append((c[0]+xx*math.cos(heading)-yy*math.sin(heading),c[1]+xx*math.sin(heading)+yy*math.cos(heading),c[2]+s[2]*math.cos(p)))
        bottom=len(vs);vs.append((c[0],c[1],c[2]-s[2]))
        fs=[(0,1+i,1+(i+1)%n) for i in range(n)]
        for j in range(rings-2):
            for i in range(n):
                a=1+j*n+i;b=1+j*n+(i+1)%n;fs.append((a,a+n,b+n,b))
        fs.extend((bottom,1+(rings-2)*n+(i+1)%n,1+(rings-2)*n+i) for i in range(n))
        self.add(vs,fs)
    def rod(self,a,b,r,n=5):
        a=Vector(a);b=Vector(b);d=(b-a).normalized();u=d.cross(Vector((0,1,0)))
        if u.length<.1:u=d.cross(Vector((1,0,0)))
        u.normalize();w=d.cross(u);vs=[]
        for c,rr in [(a,r),(b,r*.55)]:
            for i in range(n):vs.append(tuple(c+(u*math.cos(i*TAU/n)+w*math.sin(i*TAU/n))*rr))
        fs=[tuple(reversed(range(n))),tuple(range(n,2*n))]
        fs.extend((i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n));self.add(vs,fs)
    def blade(self,x,y,z,h,a,width):
        dx=math.cos(a);dy=math.sin(a)
        self.add([(x-dy*width,y+dx*width,z),(x+dy*width,y-dx*width,z),(x+dx*h*.30,y+dy*h*.30,z+h*.62),(x+dx*h*.58,y+dy*h*.58,z+h)],[(0,1,2),(0,2,3)])
    def finish(self,smooth=True):
        if not self.v:return None
        o=mesh(self.name,self.v,self.f,self.m)
        for p in o.data.polygons:p.use_smooth=smooth
        self.v=[];self.f=[];return o

def inside(x,y,inset=.90):return abs(x)<math.sqrt(3)*inset/2 and abs(y)+abs(x)/math.sqrt(3)<inset
def terrain_base(r,color,open_top=False):
    v2.ground(r,color)
    if open_top:
        o=bpy.data.objects['thick hex'];vs=[tuple(v.co) for v in o.data.vertices]
        fs=[tuple(p.vertices) for p in o.data.polygons if p.index!=1]
        o.data.clear_geometry();o.data.from_pydata(vs,[],fs);o.data.update()

def pasture(x,y):
    return .032+.14*math.exp(-((x+.43)**2+(y-.22)**2)/.15)+.095*math.exp(-((x-.42)**2+(y+.20)**2)/.18)

def goat(x,y,heading=0,phase=0,z=None):
    before=set(bpy.context.scene.objects)
    coat=mat('small goat brown coat',(.31,.17,.075),.14);cream=mat('goat white blaze',(.72,.68,.51),.08)
    dark=mat('goat dark legs beard',(.075,.053,.025),0);horn=mat('small goat horns',(.60,.48,.27),0)
    pebble((0,0,.078),(.060,.031,.035),coat,True)
    pebble((-.008,0,.095),(.029,.032,.021),cream,True)
    g=.5+.5*math.cos(phase);headz=.100-.027*g
    pole('goat slender neck',(.037,0,.072),(.067,0,headz),.016,coat)
    pebble((.073,0,headz),(.026,.018,.023),coat,True)
    pebble((.085,-.007,headz+.006),(.013,.014,.010),cream,True)
    for side in [-1,1]:
        pebble((.06,side*.024,headz+.007),(.025,.009,.011),coat,True)
        pole('tiny swept goat horn',(.065,side*.012,headz+.018),(.051,side*.014,headz+.060),.005,horn,6)
        pole('horn tip',(.051,side*.014,headz+.060),(.035,side*.013,headz+.067),.0025,horn,5)
    # Distinct pointed beard, slim legs and upright tail, not a smaller wool sphere.
    cone('little pointed goat beard',(.087,0,headz-.037),.003,.032,dark,6,top=.009)
    for xx in [-.035,.034]:
        for yy in [-.021,.021]:pole('goat leg',(xx,yy,.012),(xx+.003*math.sin(phase+xx*8),yy,.071),.005,dark)
    pole('upright goat tail',(-.054,0,.082),(-.076,0,.103),.006,cream)
    eye=mat('goat eye',(.006,.006,.004),0)
    for side in [-1,1]:pebble((.08,side*.017,headz+.009),(.003,.003,.003),eye,True)
    z=pasture(x,y) if z is None else z
    goat_scale=1.25
    for o in set(bpy.context.scene.objects)-before:
        loc=o.location.copy()*goat_scale;o.scale*=goat_scale;o.location=(x+loc.x*math.cos(heading)-loc.y*math.sin(heading),y+loc.x*math.sin(heading)+loc.y*math.cos(heading),z+loc.z);o.rotation_euler.z+=heading

def fleece():
    terrain_base('fleece',(.17,.34,.065));grass=mat('lush rolling meadow',(.19,.36,.07),.28)
    v2.field('lush pasture hills',pasture,grass,True,32,128)
    rng=random.Random(5201)
    greens=[mat('grass leaf '+str(i),c,.14) for i,c in enumerate([(.08,.23,.035),(.16,.34,.055),(.30,.43,.10)])]
    tufts=[Batch('grass clumps '+str(i),m) for i,m in enumerate(greens)]
    for i in range(480):
        x=rng.uniform(-.73,.73);y=rng.uniform(-.77,.77)
        if not inside(x,y,.84):continue
        # Leave hoof clearings and the front wandering path unobstructed.
        if .02<x<.42 and -.49<y<-.26:continue
        for k in range(7):
            xx=x+rng.uniform(-.022,.022);yy=y+rng.uniform(-.022,.022)
            tufts[k%3].blade(xx,yy,pasture(xx,yy),rng.uniform(.034,.078),rng.random()*TAU,.007)
    for b in tufts:b.finish(False)
    wallm=mat('weathered dry wall',(.29,.28,.20),.23);wall=Batch('proper irregular dry stone wall',wallm)
    for i in range(23):
        x=-.19+i*.035;y=.72-.13*x*x
        if .02<x<.25:continue
        for j in range(3):
            xx=x+(j%2)*.019;yy=y+rng.uniform(-.012,.012)
            wall.ellipsoid((xx,yy,pasture(xx,yy)+.027+j*.041),(.043+rng.random()*.007,.034,.029),rng.uniform(-.3,.3))
    wall.finish(False)
    wood=mat('meadow gate oak',(.24,.12,.045),.17)
    for x in [.02,.25]:pole('gate post',(x,.72,pasture(x,.72)),(x,.72,pasture(x,.72)+.19),.018,wood)
    for z in [.065,.135]:pole('gate rail',(.02,.71,pasture(0,.72)+z),(.25,.71,pasture(0,.72)+z),.012,wood)
    pole('gate diagonal brace',(.02,.707,pasture(0,.72)+.065),(.25,.707,pasture(0,.72)+.135),.008,wood)
    # Six grouped alpacas; a seventh shifts and grazes in the sprite layer.
    v2.pasture=pasture
    from alpacas import alpaca
    for i,(x,y,h) in enumerate([(.13,.40,.3),(.32,.43,-.4),(.29,-.13,-.1),(.38,.13,-.7),(.54,-.09,.2),(.10,-.58,.6)]):
        alpaca(x,y,h,.3+i*.13,i%4,upright=i in (0,1))
    rock=mat('goat outcrop',(.34,.33,.25),.20)
    for x,y,sz in [(.02,.67,.11),(.12,.65,.09),(.55,.33,.10)]:pebble((x,y,pasture(x,y)+sz*.3),(sz,sz*.8,sz*.7),rock)
    goat(.02,.67,.2,1.0,pasture(.02,.67)+.068);goat(.53,.33,-1.0,1.8,pasture(.53,.33)+.06)
    bush=Batch('dense meadow bushes',greens[0])
    for x,y in [(-.66,-.27),(.61,.36),(-.57,.61),(.35,-.59),(-.06,.71),(.04,.32),(.05,-.30)]:
        if not inside(x,y,.85):continue
        for j in range(7):
            xx=x+rng.uniform(-.05,.05);yy=y+rng.uniform(-.04,.04)
            bush.ellipsoid((xx,yy,pasture(xx,yy)+.035),(.047,.04,.040))
    bush.finish()
    flowers=[Batch('wildflower '+str(i),mat('petal '+str(i),c,0)) for i,c in enumerate([(.88,.76,.48),(.61,.25,.42),(.88,.88,.74)])]
    stems=Batch('wildflower stems',greens[0])
    for i in range(135):
        x=rng.uniform(-.71,.71);y=rng.uniform(-.75,.73)
        if not inside(x,y,.85) or (.02<x<.4 and -.49<y<-.26):continue
        z=pasture(x,y)+rng.uniform(.04,.07);stems.rod((x,y,z-.05),(x,y,z),.0018)
        for j in range(4):
            a=j*TAU/4;flowers[i%3].ellipsoid((x+.006*math.cos(a),y+.006*math.sin(a),z),(.006,.005,.003))
    stems.finish();[b.finish() for b in flowers]

def field_ground(x,y):return .022+.047*math.sin(x*2.3+.4)*math.cos(y*1.7)+.033
def row_y(x,j):return -.55+j*.078+.065*math.sin(x*2.4+j*.03)

def grain():
    terrain_base('grain',(.40,.26,.065));soil=mat('gently rolling wheat soil',(.35,.23,.06),.22)
    v2.field('rolling field ground',field_ground,soil,True,28,96)
    rng=random.Random(8330)
    golds=[mat('dense wheat '+str(i),c,.17) for i,c in enumerate([(.59,.32,.035),(.75,.48,.065),(.86,.64,.16)])]
    batches=[Batch('golden ears and leaves '+str(i),m) for i,m in enumerate(golds)]
    from token_space import allowed
    ears=0
    for row in range(15):
        # Closed sculpted canopy row: a soft textured mass, not floating stick heads.
        vs=[];fs=[];previous=None
        for k in range(49):
            x=-.67+k*.027;y=row_y(x,row)
            if not inside(x,y,.83):continue
            if (x>.25 and y<.08) or (x<-.04 and y<.30) or not allowed(x,y,field_ground(x,y)+.34,.04):
                previous=None;continue
            start=len(vs)
            for side,zz in [(-1,.005),(-.52,.21),(0,.24),(.52,.21),(1,.005)]:
                yy=y+side*.036
                edge=.83-abs(yy)-abs(x)/math.sqrt(3)
                taper=min(1,max(.35,edge/.075))
                vs.append((x,yy,field_ground(x,yy)+zz*taper+.015*math.sin(x*31+row)))
            if previous is not None:
                for j in range(4):fs.append((previous+j,start+j,start+j+1,previous+j+1))
            previous=start
            for m in range(2):
                xx=x+rng.uniform(-.013,.013);yy=y+rng.uniform(-.028,.028);z=field_ground(xx,yy)
                h=rng.uniform(.245,.30);b=batches[(k+row+m)%3]
                b.rod((xx,yy,z),(xx+.012,yy+.004,z+h-.024),.0022)
                # Long continuous ear with alternating kernels and fine awns.
                b.ellipsoid((xx+.012,yy+.004,z+h),(.009,.010,.037),rng.random()*TAU,n=6,rings=4)
                for q in range(3):
                    side=-1 if q%2 else 1
                    b.ellipsoid((xx+.012+side*.006,yy+.004,z+h-.022+q*.018),(.005,.007,.010),n=6,rings=3)
                    b.rod((xx+.012+side*.006,yy+.004,z+h-.015+q*.018),(xx+side*.025,yy+.005,z+h+.025+q*.008),.0008,n=3)
                b.blade(xx,yy,z+.07,.14,rng.random()*TAU,.009);ears+=1
        o=mesh('soft curved wheat canopy row',vs,fs,golds[row%3])
        for p in o.data.polygons:p.use_smooth=True
    for b in batches:b.finish()
    rope=mat('stook binding',(.23,.12,.025),0)
    for x,y in [(.45,-.42),(.32,-.60),(.59,-.30)]:
        z=field_ground(x,y)
        for j in range(12):
            a=j*TAU/12;pole('cut wheat stook',(x+.05*math.cos(a),y+.05*math.sin(a),z),(x+.022*math.cos(a),y+.022*math.sin(a),z+.25),.009,golds[j%3],6)
        cone('stook tie',(x,y,z+.115),.034,.024,rope,12,top=.034)
    wood=mat('field cart oak',(.26,.115,.035),.17);z=field_ground(.48,-.15)
    cube('cart platform',(.47,-.16,z+.07),(.23,.17,.024),wood,.006)
    for yy in [-.247,-.073]:
        for j in range(2):cube('cart side boards',(.47,yy,z+.11+j*.038),(.23,.014,.032),wood,.003)
        bpy.ops.mesh.primitive_torus_add(major_radius=.047,minor_radius=.008,major_segments=16,minor_segments=6,location=(.47,yy,z+.045),rotation=(math.pi/2,0,0));bpy.context.object.data.materials.append(wood)
    for xx in [.39,.55]:pole('cart handles',(xx,-.17,z+.08),(xx,-.41,z+.03),.007,wood)
    for yy in [-.247,-.073]:
        for j in range(3):
            a=j*math.pi/3
            pole('cart wheel spoke',(.47-.042*math.cos(a),yy,z+.045-.042*math.sin(a)),(.47+.042*math.cos(a),yy,z+.045+.042*math.sin(a)),.0035,wood,5)
    red=Batch('field edge poppies',mat('poppy crimson',(.59,.025,.015),0));green=Batch('poppy stems',mat('poppy leaf green',(.15,.27,.045)))
    for i in range(42):
        x=-.66+i*.032;y=-.68+.045*math.sin(i*.45)
        if not inside(x,y,.85):continue
        zz=field_ground(x,y);green.rod((x,y,zz),(x,y,zz+.085),.002)
        for j in range(4):red.ellipsoid((x+.007*math.cos(j*TAU/4),y+.007*math.sin(j*TAU/4),zz+.085),(.008,.007,.004))
    red.finish();green.finish();return {'ears':ears,'curved_crop_rows':15}

def clay_height(x,y):
    d=math.sqrt(((x+.27)/.47)**2+((y+.19)/.40)**2)
    def smooth(a,b):
        t=max(0,min(1,(d-a)/(b-a)));return t*t*(3-2*t)
    # Real excavation: lowest wet floor -.115, below the original base top z=0.
    if d<1.13:return -.115+.080*smooth(.34,.40)+.075*smooth(.60,.67)+.075*smooth(.90,.97)
    return .115+.025*math.exp(-((x+.45)**2+(y-.46)**2)/.18)

def clay():
    terrain_base('clay',(.49,.145,.080),True)
    earth=mat('terrace cut clay',(.49,.145,.078),.25);wet=mat('wet dark clay bottom',(.20,.052,.030),.20)
    wet.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=.33
    cut=mat('fresh cut terrace walls',(.35,.080,.045),.22)
    o=v2.field('deep excavated clay terraces',clay_height,earth,True,76,192);o.data.materials.append(wet);o.data.materials.append(cut)
    for p in o.data.polygons:
        z=sum(o.data.vertices[v].co.z for v in p.vertices)/len(p.vertices)
        if z<-.045:p.material_index=1
        elif p.normal.z<.65:p.material_index=2
    water=mat('wet clay puddle',(.15,.058,.037),.06);water.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=.20
    pebble((-.27,-.19,-.112),(.12,.073,.002),water,True)
    brick=mat('hand fired kiln bricks',(.39,.105,.035),.23);mortar=mat('dark mortar',(.15,.070,.020),.13);wood=mat('yard oak',(.22,.11,.034),.20)
    x=.46;y=-.08;z=clay_height(x,y)
    cube('kiln structure',(x,y,z+.15),(.29,.25,.29),mortar,.014)
    for row in range(5):
        for col in range(4):cube('kiln brick face',(x-.108+col*.071+(row%2)*.009,y-.132,z+.027+row*.054),(.064,.018,.047),brick,.003)
    black=mat('deep kiln mouth',(.025,.014,.005),0)
    cube('fire arch',(x,y-.145,z+.080),(.100,.009,.105),black,.026)
    cube('kiln top',(x,y,z+.305),(.32,.28,.034),brick,.007)
    for j in range(6):cube('chimney course',(x+.055,y+.035,z+.34+j*.045),(.087,.086,.039),brick,.003)
    cube('chimney mouth',(x+.055,y+.035,z+.611),(.055,.055,.005),black)
    for layer in range(5):
        for a in range(5):
            for b in range(2):cube('stack of drying bricks',(.05+a*.065,-.64+b*.041,clay_height(.05+a*.065,-.64+b*.041)+.017+layer*.030),(.059,.035,.025),brick,.002)
    # Two slatted drying rack shelves carrying spaced unfired bricks.
    for yy in [.65,.76]:
        for xx in [-.10,.18]:pole('drying rack uprights',(xx,yy,clay_height(xx,yy)),(xx,yy,.35),.009,wood)
        for level in [.21,.32]:
            for k in range(6):cube('rack wood slat',(-.10+k*.050,yy,level),(.066,.12,.012),wood,.002)
            for k in range(5):cube('unfired drying brick',(-.085+k*.050,yy,level+.020),(.064,.045,.027),earth,.003)
    # Small board shed on the rear bank, with pitched roof and visible planks.
    sx=.30;sy=.49;sz=clay_height(sx,sy)
    for yy in [sy-.12,sy+.12]:
        for i in range(6):cube('shed upright boards',(sx-.14+i*.055,yy,sz+.11),(.049,.016,.21),wood,.002)
    for i in range(4):cube('shed side boards',(sx-.17,sy-.09+i*.06,sz+.11),(.018,.052,.21),wood,.002)
    roof=mat('shed roof aged wood',(.20,.12,.065),.24)
    vs=[(sx-.19,sy-.15,sz+.22),(sx+.19,sy-.15,sz+.22),(sx-.19,sy,sz+.31),(sx+.19,sy,sz+.31),(sx-.19,sy+.15,sz+.22),(sx+.19,sy+.15,sz+.22)]
    mesh('shed pitched roof',vs,[(0,1,3,2),(2,3,5,4)],roof)
    for i in range(8):pole('roof slat',(sx-.175+i*.05,sy-.15,sz+.223),(sx-.175+i*.05,sy,sz+.314),.005,roof,4)
    pole('pit descent plank',(-.53,-.28,.05),(-.25,-.24,-.09),.022,wood,6)
    rubble=Batch('clay yard rubble',mat('yard rubble',(.34,.20,.095),.20));rng=random.Random(7041)
    for i in range(80):
        xx=rng.uniform(-.73,.73);yy=rng.uniform(-.72,.72)
        if inside(xx,yy,.85) and clay_height(xx,yy)>.06:rubble.ellipsoid((xx,yy,clay_height(xx,yy)+.008),(.014,.019,.010),rng.random()*TAU)
    rubble.finish(False)
    for bx,by in [(.04,.30),(.08,-.47)]:
        for layer in range(3):
            for k in range(3):cube('extra clay yard brick stack',(bx+k*.055,by,clay_height(bx,by)+.014+layer*.028),(.049,.033,.025),brick,.002)

def dune_height(x,y):
    u=x*.83+y*.56;wave=.5+.5*math.sin(10.6*u+.90*math.sin(y*2.7))
    q=max(abs(x)/(math.sqrt(3)*R/2),(abs(y)+abs(x)/math.sqrt(3))/R)
    t=max(0,min(1,(q-.67)/.33));t=t*t*(3-2*t)
    ridge=.055+.38*wave**2*(1-.80*t)
    ripple=.007*math.sin(x*61+y*37+2*math.sin(y*7))*(.4+.6*wave)*(1-.5*t)
    return ridge+ripple+.055*math.exp(-((x-.08)**2+(y+.56)**2)/.045)

def desert():
    terrain_base('desert',(.54,.32,.11));sand=mat('dune ochre sand',(.64,.40,.15),.15)
    v2.field('solid dunes with actual fine ripples',dune_height,sand,True,62,192)
    rockm=mat('warm desert outcrops',(.30,.20,.12),.22);rng=random.Random(6704)
    for cx,cy,size in [(.30,.61,.14),(.46,.39,.15),(.51,-.40,.13),(-.68,-.31,.055),(.09,-.64,.075)]:
        for j in range(4):
            x=cx+rng.uniform(-.07,.07);y=cy+rng.uniform(-.06,.06);s=size*rng.uniform(.5,1.0)
            o=pebble((x,y,dune_height(x,y)+s*.40),(s,s*.7,s*.85),rockm);o.rotation_euler.z=rng.random()*TAU
    small=Batch('scattered desert stones',rockm)
    for i in range(80):
        x=rng.uniform(-.71,.71);y=rng.uniform(-.71,.71)
        if inside(x,y,.85):small.ellipsoid((x,y,dune_height(x,y)+.008),(.02,.017,.018),rng.random()*TAU)
    small.finish(False)
    bark=mat('bleached dead wood',(.57,.45,.28),.20);x=.35;y=-.15;z=dune_height(x,y)
    pole('bleached dead tree trunk',(x,y,z),(x-.04,y,z+.37),.024,bark)
    for a,b in [((x-.02,y,z+.16),(x-.15,y+.04,z+.27)),((x-.15,y+.04,z+.27),(x-.20,y+.03,z+.34)),((x-.04,y,z+.28),(x+.08,y-.03,z+.34)),((x-.04,y,z+.32),(x-.10,y,z+.43))]:pole('dead tree fork',a,b,.011,bark)
    scrub=Batch('desert dry scrub and grasses',mat('dry scrub ochre',(.34,.24,.105),.16))
    for i in range(30):
        x=rng.uniform(-.69,.69);y=rng.uniform(-.73,.73)
        if not inside(x,y,.84) or (abs(x)<.18 and abs(y)<.3):continue
        for j in range(7):
            a=j*TAU/7;zz=dune_height(x,y);scrub.rod((x,y,zz),(x+.04*math.cos(a),y+.04*math.sin(a),zz+.07),.002)
            scrub.blade(x,y,zz,.065,a,.006)
    scrub.finish(False)
    bone=mat('bleached skull bone',(.82,.76,.57),.10);black=mat('skull sockets',(.12,.085,.035),0)
    x=.22;y=-.66;z=dune_height(x,y)
    pebble((x,y,z+.027),(.065,.038,.032),bone,True);pebble((x+.055,y,z+.019),(.042,.020,.016),bone,True)
    for side in [-1,1]:pebble((x+.018,y+side*.029,z+.030),(.015,.004,.012),black,True)

def timber():
    from render_models import base,tree,material,pigment
    from token_space import front_cap
    rng=random.Random(832);base('timber',2)
    bark=mat('V2 forest bark',(.24,.105,.035),.22)
    greens=[mat('V2 forest pine '+str(i),c,.20) for i,c in enumerate([(.055,.17,.045),(.10,.26,.055),(.23,.36,.075),(.34,.43,.12)])]
    for x,y,h in [(.03,.70,.50),(.24,.60,.59),(.48,.40,.66),(.62,.08,.57),(.41,.02,.60),(.45,-.28,.53),(.20,-.53,.40),(.57,-.48,.34),(.20,.05,.45),(.00,.65,.38),(.08,.33,.26),(-.71,-.24,.16)]:
        h=min(h,max(.18,min(front_cap(x-.352*h,y),front_cap(x,y),front_cap(x+.352*h,y))*.93))
        tree(x,y,h,2,rng,(bark,greens))
    end=mat('pale cut log ends',(.61,.39,.15),.1)
    for j in range(3):
        x=.23+j*.07
        pole('forest stacked log',(x,-.34,.10+j*.022),(x,-.56,.10+j*.022),.035,bark,10)
        pebble((x,-.57,.10+j*.022),(.032,.009,.032),end,True)
    rock=mat('forest scattered moss stones',(.27,.30,.16),.2)
    for i in range(32):
        x=rng.uniform(-.65,.65);y=rng.uniform(-.68,.68)
        if inside(x,y,.85):pebble((x,y,.015),(.025,.02,.025),rock,True)


def stone():
    from alpine_chain import build
    return build()

def animals(phase):
    v2.pasture=pasture
    from alpacas import alpaca
    alpaca(.16+.040*math.sin(phase),-.37+.015*math.cos(phase),-.2+.10*math.sin(phase),phase,1)
    goat(.42+.010*math.sin(phase),.08+.006*math.cos(phase),-.5,phase+.9)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--terrain',choices=['timber','stone','fleece','grain','clay','desert']);parser.add_argument('--animals-frame',type=int,choices=range(16));parser.add_argument('--stone-patch',action='store_true')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    assert sum([bool(args.terrain),args.animals_frame is not None,args.stone_patch])==1,'Exactly one batch per process'
    s,cam=setup();s.cycles.samples=32;s.render.threads_mode='FIXED';s.render.threads=2
    timings=[];metadata={}
    if args.stone_patch:
        from alpine_chain import WIDTH,ROW
        metadata=stone();metadata['token_space']=prepare_scene('stone');objects=[o for o in s.objects if o.type=='MESH']
        for row in range(-1,2):
            for col in range(-1,2):
                if col==row==0:continue
                offset=Vector((WIDTH*(col+.5*(row%2)),ROW*row,0))
                for original in objects:
                    copy=original.copy();copy.data=original.data;bpy.context.collection.objects.link(copy);copy.location+=offset
        folder=OUT/'stone-alpine';folder.mkdir(exist_ok=True)
        jobs=[(folder/'stone_R3_chain_patch_max.png',.96,1536)]
    elif args.terrain:
        metadata=globals()[args.terrain]() or {}
        metadata['token_space']=prepare_scene(args.terrain)
        jobs=[(OUT/f'{args.terrain}_V2_{name}.png',tilt,512) for name,tilt in v2.angles]
        if args.terrain=='stone':jobs.append((OUT/'stone_V2_max_mock1024.png',.96,1024))
    else:
        phase=TAU*args.animals_frame/16;animals(phase);s.cycles.samples=24
        folder=OUT/'animation-layers/fleece/raw';folder.mkdir(parents=True,exist_ok=True)
        jobs=[(folder/f'animals_mid_{args.animals_frame:02d}_raw.png',.48,512)]
    for path,tilt,res in jobs:
        v2.camera(cam,tilt);s.render.resolution_x=s.render.resolution_y=res;s.render.filepath=str(path)
        if args.stone_patch:
            target=Vector((.35,0,.12));cam.location+=target;cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler()
            cam.data.ortho_scale=6.8;s.render.resolution_y=1024
        start=time.monotonic();bpy.ops.render.render(write_still=True)
        timings.append({'file':str(path.relative_to(OUT)).replace('\\','/'),'seconds':round(time.monotonic()-start,2),'tilt':tilt,'resolution':res})
        print('DENSITY_RENDER',timings[-1],flush=True)
    folder=OUT/'density-manifests';folder.mkdir(exist_ok=True)
    name='stone_patch' if args.stone_patch else args.terrain or f'animals_{args.animals_frame:02d}'
    (folder/f'{name}.json').write_text(json.dumps({'blender':bpy.app.version_string,'threads':2,'metadata':metadata,'scene_objects':len(bpy.context.scene.objects),'renders':timings},indent=2))

if __name__=='__main__':main()
