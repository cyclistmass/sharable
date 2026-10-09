"""Own stylised alpacas: long fluffy necks, upright ears; no downloaded models."""
import math,random

def alpaca(x,y,heading=0,phase=0,color=0,upright=False):
    import bpy
    from render_density import Batch,mat,pasture
    from token_space import ground_height
    colors=[(.80,.75,.60),(.92,.89,.78),(.55,.34,.17),(.28,.13,.055)]
    fleece=mat('alpaca fleece '+str(color),colors[color%4],.10)
    face=mat('alpaca face '+str(color),tuple(c*.78 for c in colors[color%4]),.08)
    dark=mat('alpaca eyes and nostrils',(.025,.017,.012),0)
    wool=Batch('alpaca fluffy body and long neck',fleece);skin=Batch('alpaca face legs upright ears',face);eyes=Batch('alpaca small eyes',dark)
    # Legs and a slimmer, taller silhouette than the tiny horned goats.
    for xx in [-.060,.054]:
        for yy in [-.030,.030]:
            skin.rod((xx,yy,.010),(xx+.004*math.sin(phase+xx*9),yy,.125),.012,7)
    wool.ellipsoid((0,0,.151),(.092,.050,.068),n=12,rings=6)
    rng=random.Random(610+color)
    for j in range(18):
        a=j*math.tau/18;xx=rng.uniform(-.068,.064)
        wool.ellipsoid((xx,.039*math.cos(a),.151+.050*math.sin(a)),(.024,.020,.024),n=8,rings=4)
    # Three linked neck masses bend down for grazing, while the long neck stays visible.
    graze=.5+.5*math.cos(phase)
    if upright:graze=.08+.06*math.sin(phase)
    hx=.078+.095*graze;hz=.334-.232*graze;turn=.20*math.sin(phase)
    neck=[(.049,0,.170),(.062+.027*graze,0,.246-.060*graze),(hx,0,hz)]
    for a,b in zip(neck,neck[1:]):wool.rod(a,b,.028,10)
    for p in neck[1:]:wool.ellipsoid(p,(.033,.029,.041),n=10,rings=5)
    def head(xx,yy,zz):
        return (hx+xx*math.cos(turn)-yy*math.sin(turn),xx*math.sin(turn)+yy*math.cos(turn),hz+zz)
    skin.ellipsoid(head(.012,0,.012),(.039,.025,.029),turn,n=12,rings=5)
    skin.ellipsoid(head(.041,0,.001),(.026,.019,.018),turn,n=10,rings=4)
    wool.ellipsoid(head(-.002,0,.035),(.031,.027,.025),turn,n=10,rings=5)
    for side in [-1,1]:
        # Straight, narrow ears distinguish alpacas from floppy-eared sheep and horned goats.
        skin.rod(head(-.007,side*.019,.031),head(-.011,side*.026,.090),.010,7)
        wool.ellipsoid(head(-.009,side*.021,.048),(.012,.010,.025),turn,n=8,rings=4)
        eyes.ellipsoid(head(.026,side*.025,.023),(.004,.003,.004),n=8,rings=4)
        eyes.ellipsoid(head(.059,side*.010,.006),(.002,.002,.002),n=6,rings=3)
    wool.rod((-.078,0,.168),(-.107,0,.184),.014,8)
    z=ground_height(x,y,pasture(x,y))
    for batch in [wool,skin,eyes]:
        obj=batch.finish();obj.location=(x,y,z);obj.rotation_euler.z=heading
