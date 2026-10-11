"""Final V3 composition pass: larger readable forms, batched botanical detail.

Runs before the existing number/neighbor guard. All geometry is authored here;
the painted images are references only. No historical V2 assets are modified.
"""
import math
import random
import bpy
from mathutils import Vector
from render_density import Batch, inside
import token_space as token
import v3_art as art


def enrich(name, v):
    rng = random.Random(111026)
    fn = v.zfn(name)
    wood = v.mat('V3 story sunlit oak', (.49, .245, .067), .18)
    iron = v.mat('V3 story weathered iron', (.13, .17, .18), .12)
    cream = v.mat('V3 story split sapwood', (.91, .59, .23), .14)
    additions = {}

    if name == 'timber':
        # Broad folded sprays give each branch a readable light/shadow plane.
        mats = [v.mat('V3 story conifer needles '+str(i), c, .15) for i,c in enumerate([
            (.025,.22,.035), (.105,.40,.019), (.40,.65,.038)])]
        fans = [Batch('V3 story overlapping evergreen fans '+str(i), m) for i,m in enumerate(mats)]
        for obj in list(bpy.context.scene.objects):
            if not obj.name.startswith('V3 pine exposed trunk'):continue
            pts = [obj.matrix_world @ p.co for p in obj.data.vertices]
            h = max(p.z for p in pts)
            x = sum(p.x for p in pts[:7])/7
            y = sum(p.y for p in pts[:7])/7
            for level in range(7):
                t = .18+level*.105
                r = h*.34*(1-t)**.85
                for j in range(8):
                    a = j*math.tau/8+level*.72
                    # Upper-left tips get a yellow-green plane like fresh growth.
                    b = fans[2 if math.cos(a)<-.3 and level%2==0 else (j+level)%2]
                    art.leaf(b,(x+r*.12*math.cos(a),y+r*.12*math.sin(a),h*t),
                             (x+r*math.cos(a),y+r*math.sin(a),h*(t+.10)),h*.058,h*.033)
        for b in fans:b.finish(False)
        # Upper bark removed along each real log axis; legible from overhead.
        bark = Batch('V3 story peeled trunk ribbons', cream)
        bpy.context.view_layer.update()
        for obj in list(bpy.context.scene.objects):
            if not obj.name.startswith('V3 felled trunk'):continue
            pts = [obj.matrix_world @ p.co for p in obj.data.vertices]
            center = sum(pts,Vector())/len(pts)
            axis = (obj.matrix_world.to_3x3() @ Vector((0,0,1))).normalized()
            side = Vector((-axis.y,axis.x,0)).normalized()
            for k in range(6):
                a = center+axis*(-.105+k*.034)+Vector((0,0,.027))
                b = a+axis*.037
                w = .006+rng.random()*.004
                bark.add([tuple(a-side*w),tuple(b-side*w*.65),tuple(b+side*w*.7),tuple(a+side*w)],[(0,1,2,3)])
        bark.finish(False)
        # A low hauling sled on the lane, with a bound load and towing traces.
        x,y=-.29,.15
        for xx in [x-.063,x+.063]:
            v.pole('V3 story logging sled runner',(xx,y-.11,.018),(xx,y+.11,.018),.012,wood,6)
            v.pole('V3 story sled turned nose',(xx,y+.11,.018),(xx,y+.15,.052),.010,wood,6)
        for yy in [y-.08,y,y+.08]:v.pole('V3 story sled crossbeam',(x-.082,yy,.04),(x+.082,yy,.04),.011,wood,5)
        for j in range(3):
            xx=x+(j-1)*.038
            v.pole('V3 story sled cut poles',(xx,y-.11,.069),(xx,y+.12,.069),.018,wood,9)
            v.pole('V3 story sled bright log ends',(xx,y-.112,.069),(xx,y-.117,.069),.017,cream,10)
        rope=v.mat('V3 story hemp bindings',(.66,.44,.18),.12)
        for yy in [y-.05,y+.065]:v.pole('V3 story sled load binding',(x-.066,yy,.075),(x+.066,yy,.075),.004,rope,5)
        additions['logging_sled']=1

    elif name == 'stone':
        # Thin slabs expose major mineral planes in addition to fine erosion.
        rock=[v.mat('V3 story split mineral faces '+str(i),c,.16) for i,c in enumerate([
            (.74,.78,.75),(.47,.56,.61),(.29,.36,.40),(.86,.83,.68)])]
        count=0
        for i in range(65):
            x=rng.uniform(-.65,.04);y=rng.uniform(-.54,.68)
            if not inside(x,y,.86) or token.distance(x,y)<.43:continue
            z=fn(x,y)
            if z<.22 or z>.92:continue
            h=rng.uniform(.10,.20)
            if z+h>token.front_cap(x,y):continue
            art.crag('V3 story upright split quarry slab',x,y,z-.055,
                     rng.uniform(.032,.056),rng.uniform(.023,.039),h,rock,900+i)
            count+=1
        # A visible block hangs from the existing crane; a diagonal dark cable
        # and bright stone face separate the machine from the mountain behind it.
        x,y=-.26,-.14
        z=fn(-.50,-.22)+.30
        v.cube('V3 story suspended dressed stone',(x,y,z),(.082,.075,.082),rock[0],.007)
        for xx in [x-.035,x+.035]:
            v.pole('V3 story hoist block sling',(xx,y,z-.02),(x,y,z+.12),.0035,iron,5)
        v.pole('V3 story hoist load cable',(x,y,z+.12),(x,y,fn(-.50,-.22)+.52),.003,iron,5)
        additions['large_mineral_slabs']=count

    elif name == 'fleece':
        # A rail gate above the dry-stone wall and a little straw feeder.
        x,y=-.31,.68;z=fn(x,y)
        for xx in [x-.09,x+.09]:v.pole('V3 story pasture gate post',(xx,y,fn(xx,y)),(xx,y,z+.24),.011,wood,6)
        for dz in [.10,.19]:v.pole('V3 story pasture gate rail',(x-.09,y,z+dz),(x+.09,y,z+dz),.008,wood,5)
        v.pole('V3 story pasture gate diagonal',(x-.09,y,z+.09),(x+.09,y,z+.21),.007,wood,5)
        x,y=-.65,-.03;z=fn(x,y)
        for xx in [x-.047,x+.047]:
            for yy in [y-.046,y+.046]:v.pole('V3 story feeder legs',(xx,yy,z),(xx,yy,z+.10),.006,wood,5)
        for yy in [y-.049,y+.049]:
            v.pole('V3 story feeder rim',(x-.063,yy,z+.10),(x+.063,yy,z+.10),.007,wood,5)
            for j in range(6):v.pole('V3 story hayrack slats',(x-.055+j*.022,yy,z+.10),(x-.044+j*.018,y,z+.025),.0038,wood,4)
        straw=Batch('V3 story fresh feeder hay',cream)
        for i in range(50):
            xx=x+rng.uniform(-.05,.05);yy=y+rng.uniform(-.032,.032)
            straw.rod((xx,yy,z+.042),(xx+rng.uniform(-.018,.018),yy,z+rng.uniform(.09,.12)),.0025,4)
        straw.finish(False)
        additions['pasture_gate_and_hayrack']=2

    elif name == 'grain':
        # Raise the four existing stooks as whole assemblies so their belts and
        # flared bases rise above the crop. All components stay in their sites.
        centres=[(-.17,-.42),(.14,-.66),(-.60,.25),(-.18,.62)]
        prefixes=('V3 sheaf','V3 full sculpted stook')
        for obj in list(bpy.context.scene.objects):
            if obj.type!='MESH':continue
            material_names=[m.name for m in obj.data.materials if m]
            if not obj.name.startswith(prefixes) and 'V3 wheat sheaf rope' not in material_names:continue
            for vertex in obj.data.vertices:
                p=obj.matrix_world@vertex.co
                x,y=min(centres,key=lambda c:(p.x-c[0])**2+(p.y-c[1])**2)
                p.x=x+(p.x-x)*1.14;p.y=y+(p.y-y)*1.14
                p.z=fn(x,y)+(p.z-fn(x,y))*1.12
                vertex.co=obj.matrix_world.inverted()@p
        additions['enlarged_tied_sheaves']=4

    elif name == 'clay':
        # Make the drying yard a readable group of red bricks on pale boards.
        brick=[v.mat('V3 story drying terracotta '+str(i),c,.12) for i,c in enumerate([
            (.80,.17,.028),(.98,.29,.057),(.54,.075,.014)])]
        x,y=-.32,-.61;z=fn(x,y)
        for j in range(4):v.cube('V3 story drying pallet boards',(x,y+(j-1.5)*.035,z+.016),(.23,.032,.017),wood,.002)
        for row in range(3):
            for col in range(4):
                v.cube('V3 story hand moulded drying brick',(x+(col-1.5)*.050,y+(row-1)*.036,z+.040),(.045,.030,.026),brick[(row+col)%3],.003)
        # Clay pillars cut into the outer bench, not loose stones in the pit.
        count=0
        for i in range(30):
            a=i*math.tau/30
            xx=math.copysign(abs(math.cos(a))**(2/3.6),math.cos(a))
            yy=math.copysign(abs(math.sin(a))**(2/3.6),math.sin(a))
            x=-.12+.59*.79*xx;y=-.13+.56*.79*yy
            if token.distance(x,y)<.37:continue
            art.crag('V3 story squared clay bench fracture',x,y,fn(x,y)-.048,.024,.025,.077,brick,1200+i)
            count+=1
        additions['terrace_fractures']=count

    elif name == 'desert':
        # Sandstone caps catch warm light; rusty ledges remain visible below.
        rock=[v.mat('V3 story desert capstone '+str(i),c,.12) for i,c in enumerate([
            (.93,.64,.31),(.65,.32,.085),(.78,.45,.16)])]
        for i,(x,y,s) in enumerate([(-.56,.37,.17),(-.51,-.31,.14),(-.24,-.61,.15)]):
            art.crag('V3 story sandstone crown ledge',x+.014,y-.013,
                     fn(x,y)+s*1.25,s*.61,s*.47,s*.25,rock,1300+i)
        additions['sandstone_crowns']=3

    # Deliberate broad-leaf shrubs between the existing micro-detail. Their
    # folded surfaces form light and shadow clusters even at 25% sprite scale.
    anchors={
        'timber':[(-.68,.18),(-.50,.61),(-.41,-.66),(.01,-.78),(.65,-.20)],
        'stone':[(-.66,-.34),(-.46,-.58),(-.12,-.78)],
        'fleece':[(-.69,.15),(-.38,.64),(-.67,-.40),(.45,-.51),(.57,-.30)],
        'grain':[(-.65,.42),(-.40,.67),(-.58,-.55),(.05,-.80)],
        'clay':[(-.69,.20),(-.51,.68),(-.44,-.64),(.02,-.80)],
        'desert':[(-.66,-.16),(-.38,.48),(-.11,-.72),(.64,-.30),(-.20,.65)]}[name]
    leaves=[Batch('V3 story broad leaf islands '+str(i),v.mat('V3 story leafy pigment '+str(i),c,.12)) for i,c in enumerate([
        (.045,.25,.016),(.18,.45,.024),(.47,.64,.045)])]
    blossom=Batch('V3 story island buttercups',v.mat('V3 story yellow flower petals',(1,.69,.007),.08))
    for ax,ay in anchors:
        for j in range(16):
            x=ax+rng.uniform(-.047,.047);y=ay+rng.uniform(-.038,.038)
            z=fn(x,y);h=rng.uniform(.036,.075)
            if name=='desert':h*=.8
            if not inside(x,y,.89) or token.distance(x,y)<.39:continue
            for k in range(5):
                a=k*math.tau/5+j*.9
                art.leaf(leaves[(j+k)%3],(x,y,z),
                         (x+h*.72*math.cos(a),y+h*.72*math.sin(a),z+h),.014,.008)
            if j%3==0:
                for k in range(5):
                    a=k*math.tau/5
                    art.leaf(blossom,(x,y,z+h),(x+.017*math.cos(a),y+.017*math.sin(a),z+h+.003),.007,.002)
    for b in leaves+[blossom]:b.finish(False)
    additions['broad_leaf_islands']=len(anchors)
    return additions


def apron(name,v):
    """Ground-level leaf/stubble/shard patterns outside the existing clearing.

    Heights stay below the inherited .008 ground tolerance. These form a low
    texture carpet in the plate sightline; tall story props remain elsewhere.
    """
    if name=='desert':return
    rng=random.Random(511026)
    colors={
        'timber':[(.045,.19,.011),(.13,.32,.014),(.33,.49,.025)],
        'fleece':[(.080,.28,.010),(.23,.43,.013),(.42,.57,.025)],
        'grain':[(.47,.21,.017),(.89,.50,.037),(1,.72,.15)],
        'clay':[(.51,.068,.012),(.78,.17,.025),(.94,.29,.058)],
        'stone':[(.29,.34,.34),(.51,.55,.49),(.71,.70,.58)]}[name]
    batches=[Batch('V3 story low apron patterns '+str(i),v.mat('V3 story apron pigment '+str(i),c,.10)) for i,c in enumerate(colors)]
    z=.0019 if name=='timber' else .005
    for i in range(3200):
        x=rng.uniform(-.78,.78);y=rng.uniform(-.87,.87)
        margin=.40 if name=='grain' else .38
        if not inside(x,y,.910) or token.distance(x,y)<margin or token.front_cap(x,y)>.004:continue
        # Cluster broad motifs and leave soil visible between their islands.
        if math.sin(x*34+math.sin(y*12))*math.cos(y*29)<-.2:continue
        b=batches[i%3]
        if name in ('timber','fleece'):
            for k in range(4):
                a=k*math.tau/4+i*.72;l=rng.uniform(.016,.028)
                art.leaf(b,(x,y,z),(x+l*math.cos(a),y+l*math.sin(a),z+.0005),l*.34,.0004)
        elif name=='grain':
            for k in range(3):
                a=rng.uniform(-.3,.3)+.7;l=rng.uniform(.025,.045)
                art.leaf(b,(x+k*.005,y,z),(x+l*math.cos(a)+k*.005,y+l*math.sin(a),z+.0005),.0018,.0002)
        else:
            a=rng.random()*math.tau;l=rng.uniform(.010,.026)
            art.leaf(b,(x,y,z),(x+l*math.cos(a),y+l*math.sin(a),z+.0005),l*.4,.0003)
    for b in batches:b.finish(False)
