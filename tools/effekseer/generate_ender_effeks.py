"""Author original Enderman rift motes as readable Effekseer 1.80.7 projects.

Run with Python 3 + Pillow, then run export_ender_effeks.ps1. Coordinates are in
Minecraft blocks, local XY is the portal plane, and origin is portal center.
No stock/sample effects or textures are copied. Effekseer time is 60 frames/sec.
"""
from pathlib import Path
from math import sin, cos, pi, exp, sqrt, copysign
import random
import xml.etree.ElementTree as ET
from xml.dom import minidom
from PIL import Image

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "authored"
OUT.mkdir(exist_ok=True)
(OUT / "textures").mkdir(exist_ok=True)

def element(parent, name, value=None):
    node = ET.SubElement(parent, name)
    if value is not None:
        node.text = str(value)
    return node

def randval(parent, name, value, spread=0):
    node = element(parent, name)
    element(node, "Center", round(value, 7))
    element(node, "Min", round(value-spread, 7))
    element(node, "Max", round(value+spread, 7))
    return node

def vector(parent, name, values, randomize=False):
    node = element(parent, name)
    for axis, value in zip("XYZ", values):
        if randomize:
            randval(node, axis, value)
        else:
            element(node, axis, round(value, 7))
    return node

def color(parent, name, values):
    node = element(parent, name)
    for channel, value in zip("RGBA", values):
        element(node, channel, value)

def render_common(node, texture, fade_in=3, fade_out=10):
    common = element(node, "RendererCommonValues")
    element(common, "Material", 0)  # unlit; independent of shader packs
    element(common, "ColorTexture", texture)
    element(common, "AlphaBlend", 2) # additive
    element(common, "ZTest", "True")
    element(common, "ZWrite", "False")
    element(common, "FadeInType", 1)
    element(element(common, "FadeIn"), "Frame", fade_in)
    element(common, "FadeOutType", 1)
    element(element(common, "FadeOut"), "Frame", fade_out)

def common(node, life, delay, count=1, interval=1, parent_type=2):
    c = element(node, "CommonValues")
    element(element(c, "MaxGeneration"), "Value", count)
    element(c, "LocationEffectType", parent_type)
    randval(c, "Life", life)
    generation = element(c, "Generation")
    randval(generation, "GenerationTime", interval)
    randval(generation, "GenerationTimeOffset", delay)
    return c

def texture(name, streak=False):
    size=64
    im=Image.new("RGBA", (size,size))
    px=im.load()
    for y in range(size):
        for x in range(size):
            u=(x+.5-size/2)/(size/2)
            v=(y+.5-size/2)/(size/2)
            if streak:
                glow=exp(-u*u*20)*(1-abs(v))**1.6
                core=exp(-u*u*160)*(1-abs(v))**0.7
                a=min(1, .24*glow+.86*core)
            else:
                r=sqrt(u*u+v*v)
                glow=exp(-r*r*10)*.32
                core=exp(-r*r*140)
                star=(exp(-u*u*350)*exp(-abs(v)*6)+exp(-v*v*350)*exp(-abs(u)*6))*.5
                a=min(1,glow+core+star)*max(0,1-r)**.2
            px[x,y]=(255,255,255,round(a*255))
    im.save(OUT / "textures" / name)

texture("rift_star.png")
texture("rift_filament.png", True)

def rift_outline(angle, ripple=0):
    """Pointed horizontal tear, matching the 4.2 x 2.44 block main opening."""
    c,s=cos(angle),sin(angle)
    # This intentionally is not an ellipse: the exponent pulls in the shoulders.
    uneven=1+.027*sin(7*angle+.8)+.019*cos(13*angle-.3)+ripple
    return (2.10*copysign(abs(c)**2.10,c)*uneven,
            1.22*copysign(abs(s)**2.10,s)*uneven)

def beam_node(children, name, a, b, width, shade, delay, life=12):
    """A double-sided fixed sprite follows a local XY electric line segment.

    Specifying the four vertices directly avoids camera-facing billboard strips
    turning into a round halo. The emitter's world yaw still rotates the plane.
    """
    dx,dy=b[0]-a[0],b[1]-a[1]
    length=max(.0001,sqrt(dx*dx+dy*dy))
    nx,ny=-dy/length*width*.5,dx/length*width*.5
    n=element(children,"Node")
    element(n,"Name",name)
    common(n,life,delay,3,17)
    render_common(n,"textures/rift_filament.png",1,4)
    d=element(n,"DrawingValues")
    element(d,"Type",2)
    color(element(d,"ColorAll"),"Fixed",shade)
    s=element(d,"Sprite")
    element(s,"Billboard",2)
    element(s,"Position",1)
    for key,point in (("LL",(a[0]+nx,a[1]+ny)),("LR",(a[0]-nx,a[1]-ny)),
                      ("UL",(b[0]+nx,b[1]+ny)),("UR",(b[0]-nx,b[1]-ny))):
        v=element(s,"Position_Fixed_"+key)
        element(v,"X",round(point[0],7)); element(v,"Y",round(point[1],7))
    element(n,"Children")

def write_effect(name, arrival):
    rng=random.Random(20260928)
    project=ET.Element("EffekseerProject")
    root=element(project,"Root")
    element(root,"Name","Root")
    children=element(root,"Children")
    palette=[(178,115,255,225),(130,170,255,230),(145,215,255,220),(240,240,255,245)]
    # Staggered slivers curl from / towards the pointed horizontal tear.
    # Finite generation: maximum ending time 72 frames = 24 game ticks.
    for i in range(40):
        angle=2*pi*i/40 + rng.uniform(-.04,.04)
        x,y=rift_outline(angle)
        z=rng.uniform(-.09,.09)
        life=24+rng.randrange(0,7)
        delay=(i*7)%36
        count=2 if delay<22 else 1
        start_factor=.81 if arrival else 1.13
        direction=1 if arrival else -1
        # A tangential velocity gives the orbit a curl, without world scans.
        velocity=(direction*x*.012 - sin(angle)*.014,
                  direction*y*.012 + cos(angle)*.019,
                  direction*.004 + rng.uniform(-.002,.002))
        n=element(children,"Node")
        element(n,"Name",f"{'Outward' if arrival else 'Inward'} star {i:02d}")
        common(n,life,delay,count,15)
        loc=element(n,"LocationValues")
        element(loc,"Type",1)
        pva=element(loc,"PVA")
        vector(pva,"Location",(x*start_factor,y*start_factor,z),True)
        vector(pva,"Velocity",velocity,True)
        vector(pva,"Acceleration",(-sin(angle)*.00016,cos(angle)*.00020,0),True)
        scale=element(n,"ScalingValues")
        element(scale,"Type",4)
        easing=element(scale,"SingleEasing")
        randval(easing,"Start",rng.uniform(.09,.17))
        randval(easing,"End",.015)
        render_common(n,"textures/rift_star.png",2,12)
        drawing=element(n,"DrawingValues")
        element(drawing,"Type",2)
        sprite=element(drawing,"Sprite")
        element(sprite,"Billboard",0)
        color(element(drawing,"ColorAll"),"Fixed",palette[i%len(palette)])
        trail_parent=element(n,"Children")
        if i%4==0:
            # Short, local-space-independent vertex ribbons trail each bright mote.
            trail=element(trail_parent,"Node")
            element(trail,"Name","Fine luminous filament")
            tc=common(trail,9,0,life//3,3,1)
            element(tc,"ScaleEffectType",3)
            element(tc,"RotationEffectType",3)
            render_common(trail,"textures/rift_filament.png",1,7)
            td=element(trail,"DrawingValues")
            element(td,"Type",3)
            ribbon=element(td,"Ribbon")
            color(ribbon,"ColorAll_Fixed",(160,165,255,195))
            element(ribbon,"Position",1)
            element(ribbon,"Position_Fixed_L",-.018)
            element(ribbon,"Position_Fixed_R",.018)
            element(ribbon,"SplineDivision",3)
            element(trail,"Children")
    # Small point stars remain along the opening and flicker out with its closure.
    for i in range(20):
        angle=2*pi*i/20+.17
        n=element(children,"Node")
        element(n,"Name",f"Rim scintillation {i:02d}")
        common(n,14,8+(i*3)%45)
        loc=element(n,"LocationValues")
        x,y=rift_outline(angle)
        vector(element(loc,"Fixed"),"Location",(x,y,.04))
        scale=element(n,"ScalingValues")
        vector(element(scale,"Fixed"),"Scale",(.13,.13,.13))
        render_common(n,"textures/rift_star.png",3,8)
        d=element(n,"DrawingValues")
        element(d,"Type",2)
        s=element(d,"Sprite")
        color(element(d,"ColorAll"),"Fixed",(225,225,255,235))
        element(n,"Children")
    # Three interwoven, jagged blue-white arc strands. Short phases travel along
    # the edge; each strand has a distinct local bend and phase, no closed ellipse.
    arc_shades=[(218,239,255,250),(125,172,255,235),(174,167,255,220)]
    for strand in range(3):
        points=[]
        count=84
        for i in range(count):
            angle=2*pi*i/count
            wave=.036*sin(angle*17+strand*2.2)+.024*cos(angle*29-strand)
            wave+=rng.uniform(-.023,.023)
            x,y=rift_outline(angle,wave)
            # A few uneven sideward branches follow the reference's torn seam.
            x+=.023*sin(i*2.37+strand); y+=.018*cos(i*1.87-strand)
            points.append((x,y))
        for i,a in enumerate(points):
            b=points[(i+1)%count]
            phase=3+((i*2+strand*5)%16)
            width=.095 if strand==0 else .075
            beam_node(children,f"Jagged electric seam {strand}-{i:02d}",a,b,
                      width,arc_shades[strand],phase,12)
    element(project,"ToolVersion","1.80.7")
    element(project,"Version",3)
    element(project,"StartFrame",0)
    element(project,"EndFrame",72)
    element(project,"IsLoop","False")
    path=OUT/(name+".efkproj")
    path.write_bytes(minidom.parseString(ET.tostring(project)).toprettyxml(indent="  ", encoding="utf-8"))
    (OUT/(name+".efkefc.mcmeta")).write_text('{"preload": true}\n',encoding="utf-8")
    print(path)

write_effect("ender_departure",False)
write_effect("ender_arrival",True)
