"""V3 continuation: readable scene masses and a single painted-pigment shader.

All detail is procedural model geometry/shading, never projected reference art.
The shared base, camera, number clearing and eight-step guard remain unchanged.
"""
import math
import random
import bpy
from mathutils import Vector
from render_density import Batch, inside
import token_space as token
import v3_art as art


def enrich(name, v):
    rng=random.Random(20261010)
    fn=v.zfn(name)
    wood=v.mat('V3 finish honey exposed timber',(.48,.245,.067),.18)
    pale=v.mat('V3 finish fresh split sapwood',(.78,.48,.17),.16)
    green=v.mat('V3 finish sunlight leaf tips',(.32,.52,.016),.16)
    added=0
    if name=='timber':
        # Larger foliage fans fill the spaces within existing crowns; all the
        # original branch/trunk detail survives underneath the new growth.
        crowns=Batch('V3 finish dense sunlit branch fans',green)
        dark=Batch('V3 finish dense emerald branch fans',v.mat('V3 finish emerald needles',(.025,.235,.042),.20))
        trunks=[o for o in bpy.context.scene.objects if o.name.startswith('V3 pine exposed trunk')]
        for o in trunks:
            vs=[o.matrix_world@p.co for p in o.data.vertices]
            h=max(p.z for p in vs);x=sum(p.x for p in vs[:7])/7;y=sum(p.y for p in vs[:7])/7
            for level in range(7):
                t=.18+level*.11;r=h*.30*(1-t)**.82
                for j in range(9):
                    a=j*math.tau/9+level*.79
                    b=crowns if (j+level)%4==0 else dark
                    art.leaf(b,(x+r*.22*math.cos(a),y+r*.22*math.sin(a),h*t),
                             (x+r*math.cos(a),y+r*math.sin(a),h*(t+.08)),h*.047,h*.022)
        crowns.finish(False);dark.finish(False)
        # Expose broad strips of sapwood on top of the felled trunks. These
        # remain legible in the overhead sprite where endgrain is foreshortened.
        for o in list(bpy.context.scene.objects):
            if not o.name.startswith('V3 felled trunk'):continue
            bpy.context.view_layer.update()
            a=o.matrix_world@Vector((0,0,-.09));b=o.matrix_world@Vector((0,0,.09))
            a.z+=.024;b.z+=.024
            v.pole('V3 finish peeled log scar',a,b,.006,pale,5);added+=1
        # Pale granite anchors, amid the dark conifers, echo the reference.
        rock=[v.mat('V3 finish forest granite '+str(i),c,.18) for i,c in enumerate([(.35,.40,.39),(.55,.58,.49),(.22,.28,.26)])]
        for i,(x,y) in enumerate([(-.70,.20),(-.48,.54),(-.30,-.69),(.02,-.80)]):
            art.crag('V3 finish mossy forest boulder',x,y,0,.054,.045,.075,rock,90+i)
        # A small split-wood rack, with drying fuel under a cover.
        x=-.10;y=.77
        for xx in [x-.09,x+.09]:v.pole('V3 finish firewood rack post',(xx,y,0),(xx,y,.12),.008,wood,5)
        for k in range(6):v.pole('V3 finish split fuel logs',(x-.085,y-.035,.023+k*.014),(x+.085,y-.035,.023+k*.014),.010,pale,5)
    elif name=='stone':
        # Long angular ribs grow from the mountainside. They give the smooth
        # connecting slopes geological structure at sprite and phone scales.
        rock=[v.mat('V3 finish quarry limestone '+str(i),c,.16) for i,c in enumerate([(.55,.60,.64),(.78,.78,.66),(.26,.33,.39),(.64,.68,.65)])]
        for i in range(92):
            x=rng.uniform(-.76,.10);y=rng.uniform(-.65,.76)
            if not inside(x,y,.91) or token.distance(x,y)<.41:continue
            z=fn(x,y)
            if z<.22:continue
            sx=rng.uniform(.018,.042);sy=rng.uniform(.024,.045);h=rng.uniform(.07,.18)
            if z+h>token.front_cap(x,y):continue
            art.crag('V3 finish jointed limestone ribs',x,y,z-.028,sx,sy,h,rock,800+i);added+=1
        # Tall headframe cross bracing, visible pulley and pale working deck.
        x=-.50;y=-.22;z=fn(x,y)
        for yy in [y-.065,y+.065]:
            v.pole('V3 finish mine upper X brace',(x-.07,yy,z+.22),(x+.07,yy,z+.42),.008,wood,5)
            v.pole('V3 finish mine lower X brace',(x+.07,yy,z+.22),(x-.07,yy,z+.42),.008,wood,5)
        for xx in [x-.09,x+.09]:
            v.pole('V3 finish mine deck railing posts',(xx,y-.095,z+.28),(xx,y-.095,z+.37),.007,pale,5)
        v.pole('V3 finish mine deck railing',(x-.10,y-.095,z+.37),(x+.10,y-.095,z+.37),.006,pale,5)
    elif name=='fleece':
        # White and buttercup clusters make deliberate flower beds; do not cover
        # the animals with more random grass. Their silhouettes need clear ground.
        white=Batch('V3 finish oxeye daisy drifts',v.mat('V3 finish daisy ivory',(.95,.91,.69),.06))
        gold=Batch('V3 finish buttercup drifts',v.mat('V3 finish buttercup gold',(.95,.57,.006),.06))
        for ax,ay in [(-.67,.02),(-.52,.67),(-.33,-.64),(.07,-.78),(.62,-.33)]:
            for j in range(24):
                x=ax+rng.uniform(-.065,.065);y=ay+rng.uniform(-.06,.06)
                if not inside(x,y,.91) or token.distance(x,y)<.36:continue
                z=fn(x,y)+rng.uniform(.025,.052);b=white if j%3 else gold
                for k in range(6):
                    a=k*math.tau/6
                    art.leaf(b,(x,y,z),(x+.016*math.cos(a),y+.016*math.sin(a),z+.003),.0055,.002)
                gold.ellipsoid((x,y,z+.004),(.005,.005,.003),n=5,rings=3);added+=1
        white.finish(False);gold.finish(False)
    elif name=='grain':
        # Low stone boundary, bright poppies and clearly stacked cart contents.
        wall=[v.mat('V3 finish field boundary limestone '+str(i),c,.15) for i,c in enumerate([(.50,.45,.27),(.64,.58,.34),(.36,.33,.20)])]
        for i in range(16):
            x=-.67+i*.039;y=.52+i*.018
            if inside(x,y,.91):art.crag('V3 finish field boundary stones',x,y,fn(x,y),.022,.022,.06,wall,i)
        red=Batch('V3 finish harvest poppy groups',v.mat('V3 finish scarlet poppies',(.76,.025,.004),.08))
        for ax,ay in [(-.66,-.17),(-.32,-.59),(-.42,.53),(.04,-.78)]:
            for j in range(12):
                x=ax+rng.uniform(-.047,.047);y=ay+rng.uniform(-.026,.026);z=fn(x,y)+.055
                for k in range(4):
                    a=k*math.tau/4;red.ellipsoid((x+.008*math.cos(a),y+.008*math.sin(a),z),(.011,.009,.004),a,n=6,rings=3)
        red.finish(False)
    elif name=='clay':
        # Remove the round loose rock caps masking the excavated steps. Model
        # their vertical cuts with narrow slabs rooted in the actual terraces.
        for o in list(bpy.context.scene.objects):
            if o.name.startswith('V3 broken clay terrace cut'):bpy.data.objects.remove(o,do_unlink=True)
        cuts=[v.mat('V3 finish clay cut '+str(i),c,.18) for i,c in enumerate([(.69,.105,.018),(.91,.245,.047),(.46,.045,.012)])]
        for i in range(126):
            a=i*2.399963;r=[.31,.57,.82][i%3]
            xx=math.copysign(abs(math.cos(a))**(2/3.6),math.cos(a));yy=math.copysign(abs(math.sin(a))**(2/3.6),math.sin(a))
            aa=math.atan2(yy,xx);r/=1+.045*math.sin(7*aa)+.029*math.sin(13*aa+.7)
            x=-.12+.59*r*xx;y=-.13+.56*r*yy
            if token.distance(x,y)<.36:continue
            # Thin upright strata, mostly buried in the cut, not boulders on top.
            art.crag('V3 finish clay vertical exposed joint',x,y,fn(x,y)-.062,.015,.014,.066,cuts,310+i);added+=1
        # Ladder into the pit, stacked moulded bricks drying on a plank bed.
        for xx in [-.48,-.40]:v.pole('V3 finish pit ladder stringer',(xx,-.33,fn(xx,-.33)+.005),(xx,-.17,fn(xx,-.17)+.032),.0055,wood,5)
        for j in range(8):
            yy=-.33+j*.022;zz=fn(-.44,yy)+.018
            v.pole('V3 finish pit ladder rungs',(-.48,yy,zz),(-.40,yy,zz),.005,pale,5)
        # Wet wheel tracks follow the working yard without filling the clearing.
        dark=v.mat('V3 finish damp red wheel ruts',(.25,.038,.012),.14)
        tracks=Batch('V3 finish clay wagon wheel tracks',dark)
        for side in [-1,1]:
            for i in range(25):
                x=-.48+i*.036;y=-.64+.17*(x+.48)+side*.037
                if not inside(x,y,.91):continue
                z=fn(x,y)+.002
                tracks.add([(x,y-.004,z),(x+.032,y+.001,z),(x+.032,y+.009,z),(x,y+.004,z)],[(0,1,2,3)])
        tracks.finish(False)
    elif name=='desert':
        # Satellite outcrops build asymmetric formations, not evenly sized stumps.
        rocks=[v.mat('V3 finish weathered desert strata '+str(i),c,.16) for i,c in enumerate([(.62,.29,.079),(.87,.52,.21),(.37,.15,.035)])]
        for i,(x,y,s) in enumerate([(-.61,.38,.07),(-.57,-.30,.07),(-.22,-.65,.065),(-.08,.71,.045),(.49,-.48,.047)]):
            art.crag('V3 finish eroded sandstone flank',x,y,fn(x,y)-.015,s,s*.76,s*1.3,rocks,100+i);added+=1
        # A pale animal skull and its partly buried rib cage tell a small story.
        bone=v.mat('V3 finish sunbleached bone',(.81,.73,.51),.12)
        eye=v.mat('V3 finish skull sockets',(.10,.065,.025),.08)
        x=-.38;y=-.49;z=fn(x,y)
        v.pebble((x,y,z+.018),(.032,.021,.020),bone,True)
        v.pebble((x+.023,y,z+.011),(.022,.012,.010),bone,True)
        for side in [-1,1]:
            v.pebble((x+.008,y+side*.017,z+.024),(.007,.004,.006),eye,True)
            v.pole('V3 finish skull swept horns',(x-.012,y+side*.010,z+.032),(x-.036,y+side*.029,z+.036),.004,bone,5)
    return {'readable_story_details':added,'material_pass':'single pigment, warm key, cool fill, local AO'}


def materials(scene,terrain):
    """Replace accumulated multiplicative shading with a coherent pigment graph.

    Retain authored material colours, emission and smoke. Avoid per-object noise
    stretching, muddy repeated AO and high-frequency sand colour speckling.
    """
    scene.view_settings.exposure=0
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.55
    for o in scene.objects:
        if o.type!='LIGHT':continue
        o.data.color=(1,.93,.78)
        if o.data.type=='SUN':o.data.energy=2.35;o.data.angle=.08
        else:o.data.energy=330;o.data.size=2.0
    ground={'timber':(.075,.255,.010),'fleece':(.15,.39,.009),'grain':(.82,.43,.035),
            'clay':(.76,.125,.022),'desert':(.84,.53,.19),'stone':(.43,.43,.34)}
    mineral={'R3 warm dark bedrock':(.40,.47,.52),'R3 warm cleft shading':(.21,.28,.34),
             'R3 warm scree':(.51,.52,.46),'R3 warm lit crags':(.69,.72,.68),
             'R3 pine foothill earth':(.18,.28,.029),'V3 cut quarry block pale rock':(.71,.71,.59),
             'V3 angular protruding alpine crags':(.52,.57,.55),
             'V3 dark wet red clay':(.37,.055,.018),'V3 orange red pit faces':(.65,.083,.014),
             'V3 soft wheat leaf mass':(.68,.31,.014),'V3 sheaf warm full centres':(1,.69,.105),
             'V3 deeply furrowed log bark':(.28,.115,.034),'V3 golden exposed end grain':(.84,.51,.18),
             'V3 sheep warm ivory fleece':(.92,.86,.66),'V3 sheep charcoal face and hooves':(.038,.029,.019),
             'V3 warm worn working timber':(.49,.265,.077),
             'V3 rusty rail iron':(.31,.34,.31),
             'V3 wall weathered stone 0':(.39,.43,.36),
             'V3 wall weathered stone 1':(.58,.61,.49),
             'V3 wall weathered stone 2':(.28,.33,.27),
             'V3 wall weathered stone 3':(.68,.65,.48),
             'V3 fired orange brick':(.73,.13,.022)}
    used={m for o in scene.objects if o.type=='MESH' for m in o.data.materials if m}
    for m in used:
        label=m.name;low=label.lower()
        if label.startswith('earth edge') or 'smoke' in low or 'living coals' in low:continue
        c=mineral.get(label,tuple(m.diffuse_color[:3]))
        clearing='natural number clearing' in low
        soil=clearing or any(k in low for k in ['rich earth','timber ground','moss ground','threshed field soil'])
        rock=any(k in low for k in ['rock','crag','cleft','scree','sandstone','limestone','granite','mineral','strata'])
        botanical=any(k in low for k in ['canopy','needles','foliage','leaf','leaves','fern','clover','scrub'])
        wheat=any(k in low for k in ['wheat honey','stook','sheaf','wheat leaf','sculpted stook'])
        wood=any(k in low for k in ['timber','wood','cedar','plank','bark','oak']) and not soil
        if soil:c=ground[terrain]
        if label.startswith('V3 rich forest canopy'):
            c=[(.018,.17,.035),(.050,.32,.026),(.23,.49,.022),(.49,.67,.075)][int(label.split()[-1])%4]
        if label.startswith('V3 dense wheat honey'):
            c=[(.93,.42,.008),(1,.65,.035),(1,.84,.17)][int(label.split()[-1])%3]
        if 'sedimentary sandstone' in low:
            c=[(.53,.235,.060),(.69,.37,.115),(.88,.58,.265),(.60,.28,.073)][int(label.split()[-1])%4]
        ns=m.node_tree.nodes;ls=m.node_tree.links;ns.clear()
        p=ns.new('ShaderNodeBsdfPrincipled');out=ns.new('ShaderNodeOutputMaterial');ls.new(p.outputs[0],out.inputs['Surface'])
        p.inputs['Roughness'].default_value=.82
        if botanical or wheat:p.inputs['Subsurface Weight'].default_value=.045
        if 'water' in low:p.inputs['Roughness'].default_value=.20
        pos=ns.new('ShaderNodeNewGeometry');mapping=ns.new('ShaderNodeVectorMath');mapping.operation='MULTIPLY'
        mapping.inputs[1].default_value=(1,1,1) if not wood else (3,3,.3)
        ls.new(pos.outputs['Position'],mapping.inputs[0])
        noise=ns.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=9 if soil else 24;noise.inputs['Detail'].default_value=2.5
        ls.new(mapping.outputs[0],noise.inputs['Vector'])
        ramp=ns.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.22;ramp.color_ramp.elements[1].position=.80
        lo,hi=(.68,1.25) if soil else (.77,1.16)
        if terrain=='desert' and soil:lo,hi=.97,1.025
        if clearing:lo,hi=(.86,1.04) if terrain!='desert' else (.97,1.025)
        ramp.color_ramp.elements[0].color=(*(q*lo for q in c),1)
        ramp.color_ramp.elements[1].color=(*(min(1,q*hi) for q in c),1)
        ls.new(noise.outputs['Fac'],ramp.inputs[0]);source=ramp.outputs[0]
        if rock:
            wave=ns.new('ShaderNodeTexWave');wave.wave_type='BANDS';wave.bands_direction='Z'
            wave.inputs['Scale'].default_value=6 if terrain=='desert' else 4;wave.inputs['Distortion'].default_value=2.7
            ls.new(pos.outputs['Position'],wave.inputs['Vector'])
            bands=ns.new('ShaderNodeValToRGB');bands.color_ramp.elements[0].position=.28;bands.color_ramp.elements[0].color=(.59,.55,.46,1)
            bands.color_ramp.elements[1].position=.43;bands.color_ramp.elements[1].color=(1,1,1,1)
            ls.new(wave.outputs['Fac'],bands.inputs[0]);mix=ns.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=.16 if terrain=='stone' else .32
            ls.new(source,mix.inputs[1]);ls.new(bands.outputs[0],mix.inputs[2]);source=mix.outputs[0]
        ao=ns.new('ShaderNodeAmbientOcclusion');ao.inputs['Distance'].default_value=.045 if botanical or wheat else .11
        mix=ns.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=.22 if botanical or wheat else .34
        ls.new(source,mix.inputs[1]);ls.new(ao.outputs['Color'],mix.inputs[2]);ls.new(mix.outputs[0],p.inputs['Base Color'])
        fine=ns.new('ShaderNodeTexNoise');fine.inputs['Scale'].default_value=120 if soil else 72;fine.inputs['Detail'].default_value=2
        ls.new(mapping.outputs[0],fine.inputs['Vector']);bump=ns.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.19;bump.inputs['Distance'].default_value=.0035
        ls.new(fine.outputs['Fac'],bump.inputs['Height']);ls.new(bump.outputs[0],p.inputs['Normal'])
        if (terrain=='stone' and rock) or (terrain=='clay' and not clearing and ('rich earth' in low or 'pit faces' in low or 'clay cut' in low)):
            # Angular vertical joints break up the smooth reservation shoulder.
            # They are world-scale mineral relief, independent of camera/view.
            stretch=ns.new('ShaderNodeVectorMath');stretch.operation='MULTIPLY';stretch.inputs[1].default_value=(1,1,.34)
            ls.new(pos.outputs['Position'],stretch.inputs[0])
            joints=ns.new('ShaderNodeTexVoronoi');joints.feature='DISTANCE_TO_EDGE';joints.inputs['Scale'].default_value=17 if terrain=='stone' else 25
            ls.new(stretch.outputs[0],joints.inputs['Vector'])
            cracks=ns.new('ShaderNodeValToRGB');cracks.color_ramp.elements[0].position=.013;cracks.color_ramp.elements[0].color=(.30,.34,.35,1) if terrain=='stone' else (.40,.22,.12,1)
            cracks.color_ramp.elements[1].position=.080;cracks.color_ramp.elements[1].color=(1,1,1,1)
            ls.new(joints.outputs['Distance'],cracks.inputs[0])
            old=p.inputs['Base Color'].links[0].from_socket
            tint=ns.new('ShaderNodeMixRGB');tint.blend_type='MULTIPLY';tint.inputs[0].default_value=.38 if terrain=='stone' else .24
            ls.new(old,tint.inputs[1]);ls.new(cracks.outputs[0],tint.inputs[2]);ls.new(tint.outputs[0],p.inputs['Base Color'])
            relief=ns.new('ShaderNodeBump');relief.inputs['Strength'].default_value=.42;relief.inputs['Distance'].default_value=.014 if terrain=='stone' else .009
            ls.new(cracks.outputs[0],relief.inputs['Height']);ls.new(bump.outputs[0],relief.inputs['Normal']);ls.new(relief.outputs[0],p.inputs['Normal'])
        if terrain=='desert' and soil:
            wave=ns.new('ShaderNodeTexWave');wave.wave_type='BANDS';wave.bands_direction='DIAGONAL'
            wave.inputs['Scale'].default_value=48;wave.inputs['Distortion'].default_value=4;wave.inputs['Detail Scale'].default_value=.8
            ls.new(pos.outputs['Position'],wave.inputs['Vector']);ls.new(wave.outputs['Fac'],bump.inputs['Height'])
            bump.inputs['Strength'].default_value=.16;bump.inputs['Distance'].default_value=.002
