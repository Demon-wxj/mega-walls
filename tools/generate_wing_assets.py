"""Reproducible, original UV feather materials for the layered skill wings.

No third-party artwork is sampled. UV: feather tip=(.5, 0), root=(.5, 1).
The RGBA feathers have a shaded solid vane plus small translucent luminous edges;
render them with an alpha material, and use wing_energy for additive accents.
"""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/megawalls/textures/effect'
PREVIEW = ROOT.parent / 'repair-20260928-wing-polish/asset-preview.png'
OUT.mkdir(parents=True, exist_ok=True)
RESAMPLE = getattr(Image, 'Resampling', Image).LANCZOS


def smooth(a, b, x):
    q = np.clip((x-a)/(b-a), 0, 1)
    return q*q*(3-2*q)


def feather(kind):
    # Author at 2x, then filter once to retain thin barbs with stable AA.
    w, h = 1024, 2048
    u, v = np.meshgrid((np.arange(w, dtype=np.float32)+.5)/w,
                       (np.arange(h, dtype=np.float32)+.5)/h)
    stem = .500 + .024*np.sin(v*math.pi) - .014*v
    x = u-stem
    ax = np.abs(x)
    t = np.clip((.972-v)/.945, 0, 1)
    leaf = np.clip((t-.100)/.9, 0, 1)
    broad = .330 if kind == 'hunter' else .355
    half = broad*np.clip(np.sin(math.pi*leaf),0,1)**.79 * (.87+.13*t)
    # Slight asymmetric vanes and wavering barb groups make individual feathers organic.
    half *= np.where(x < 0, .94, 1.03)
    barb_phase = (v+.54*ax+.17*ax*ax)*362
    barb = (.5+.5*np.cos(barb_phase*math.pi*2))**9
    groups = np.sin(v*82 + np.sin(v*19)*2)
    fringe = .006*np.sin(barb_phase*math.pi*2) + .0035*groups
    if kind == 'dread':
        fringe += .013*(.5+.5*np.sin(v*147+1.6*np.sin(v*39)))**7
        # Deliberate short tears rather than a uniformly outlined black silhouette.
        tears = np.zeros_like(v)
        for cy, depth, side in [(.22,.038,-1),(.38,.033,1),(.56,.052,-1),(.68,.028,1),(.43,.029,-1)]:
            tears += depth*np.exp(-((v-cy)/.006)**2) * (x*side > 0)
        fringe -= tears
    edge = half+fringe-ax
    body = smooth(-.0015, .0020, edge) * smooth(.095,.14,t) * (v>.019)
    # Fine vane splits run diagonally from the shaft, including semitransparent gaps.
    narrow_gaps = (.5+.5*np.cos(barb_phase*math.pi*2+.4))**28
    body *= 1 - narrow_gaps*.18*smooth(.020,.2,ax)
    across = np.clip(ax/np.maximum(half,.002),0,1)
    filament = np.exp(-(x/(.0030+.0028*v))**2)
    shaft_shadow = np.exp(-((x+.006)/.006)**2)
    stem_mask = np.exp(-(x/.0032)**2)*smooth(.014,.035,v)*(1-smooth(.968,.985,v))
    fiber = .5+.5*np.sin((v+.54*ax)*math.pi*724+np.sin(ax*113)*.24)
    highlight = np.exp(-((across-.30)/.19)**2)
    edge_glow = np.exp(-(edge/.008)**2)*smooth(.13,.17,t)*smooth(.02,.07,v)
    vein_wave = .5+.5*np.sin(v*23 + ax*28 + np.sin(v*9)*1.8)

    if kind == 'hunter':
        # Warm gold base / ivory tip, fine blue-grey shaded grooves, a readable rachis.
        light = np.clip(.55 + .24*(1-v) + .14*highlight - .13*across
                        + .085*fiber - .15*barb - .13*shaft_shadow,0,1)
        colors = np.stack([.43+.57*light,
                           .24+.72*light + .025*(1-v),
                           .075+.78*light + .075*(1-v)],axis=-1)
        gold = np.exp(-((across-.73)/.14)**2)*(.30+.7*vein_wave)*.21
        colors += np.stack([gold,gold*.28,-gold*.22],axis=-1)
        colors = colors*(1-filament[...,None]*.82)+np.array([1,.93,.69])*filament[...,None]*.82
        edge_color = np.array([1.0,.71,.24])
        alpha = np.maximum(body*.97,stem_mask*.96)
        alpha = np.maximum(alpha,edge_glow*.25)
        colors = np.where((body<.18)[...,None],edge_color,colors)
        colors = colors*(1-stem_mask[...,None]*.6)+np.array([1,.80,.37])*stem_mask[...,None]*.6
    else:
        # Indigo/violet feathers have a visible body. Cracks are sparse, hot and irregular.
        light = .19+.11*highlight+.095*fiber+.06*(1-v)-.09*barb-.035*across
        colors = np.stack([.085+light*.38,
                           .075+light*.22,
                           .17+light*.82],axis=-1)
        turbulence = np.sin(v*39+np.sin(v*13)*1.7)*.010 + np.sin(v*102)*.003
        crack = np.exp(-((ax-(.026+.16*(1-v)+turbulence))/.0032)**2)
        crack *= (.45+.55*np.sin(v*27+ax*30)**2)*body
        barb_fire = np.exp(-(np.sin((v+.54*ax)*69+np.sin(v*29)*.32)/.10)**2)
        barb_fire *= smooth(.14,.28,across)*(1-smooth(.78,1,across))*.37*body
        heat = np.clip(crack*.85+barb_fire,0,1)
        colors += heat[...,None]*np.array([.63,.20,.88])
        colors += filament[...,None]*np.array([.39,.23,.57])
        colors += edge_glow[...,None]*np.array([.53,.15,.70])
        alpha = np.maximum(body*.96,stem_mask*.90)
        alpha = np.maximum(alpha,edge_glow*.34)
        colors = np.where((body<.15)[...,None],np.array([.62,.28,.95]),colors)
        colors += stem_mask[...,None]*np.array([.32,.16,.39])
    rgba = np.dstack((np.clip(colors,0,1),np.clip(alpha,0,1)))
    img = Image.fromarray(np.uint8(np.rint(rgba*255)),'RGBA').resize((512,1024),RESAMPLE)

    # Tiny authored star flecks, deterministic positions, never a separate opaque sheet.
    rng = np.random.default_rng(23817 if kind == 'hunter' else 83910)
    flecks = Image.new('RGBA',img.size)
    draw = ImageDraw.Draw(flecks)
    pixels = img.load()
    for _ in range(260):
        px = int(rng.integers(112,405)); py = int(rng.integers(80,865))
        if pixels[px,py][3] < 160 or rng.random()>.23:
            continue
        hot = (255,244,188,205) if kind == 'hunter' else (203,183,255,215)
        radius = 1 if rng.random()<.78 else 2
        draw.line((px-radius,py,px+radius,py),fill=hot,width=1)
        draw.line((px,py-radius*2,px,py+radius*2),fill=hot,width=1)
    img = Image.alpha_composite(img,flecks)
    return img


def energy():
    size=512
    x,y=np.meshgrid((np.arange(size,dtype=np.float32)+.5)/size*2-1,
                    (np.arange(size,dtype=np.float32)+.5)/size*2-1)
    r=np.sqrt(x*x+y*y); a=np.arctan2(y,x)
    warp=r+.055*np.sin(a*5+r*17)+.025*np.sin(a*11-r*29)
    wisps=np.exp(-((warp-.36-.08*np.sin(a*3))/.075)**2)
    core=np.exp(-(r/.22)**2)
    veil=np.exp(-(r/.48)**2)*(.4+.6*(.5+.5*np.sin(a*4+r*39))**3)
    sparks=np.maximum(np.exp(-((x+.02*np.sin(y*11))/.021)**2)*np.exp(-(y/.30)**2),
                      np.exp(-((y+.01*np.sin(x*16))/.024)**2)*np.exp(-(x/.21)**2))
    alpha=np.clip(core*.65+wisps*.28+veil*.13+sparks*.36,0,1)*(1-smooth(.76,.98,r))
    rgb=np.ones((size,size,3),dtype=np.float32)
    return Image.fromarray(np.uint8(np.dstack((rgb,alpha))*255),'RGBA').resize((256,256),RESAMPLE)


def save_asset(name,img):
    path=OUT/(name+'.png')
    img.save(path,optimize=True)
    path.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}\n',encoding='utf-8')
    return path


def preview(hunter,dread,energy_image):
    PREVIEW.parent.mkdir(parents=True,exist_ok=True)
    canvas=Image.new('RGB',(1440,1040),(14,18,28))
    draw=ImageDraw.Draw(canvas)
    try:
        font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',27)
        small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
    except OSError:
        font=small=ImageFont.load_default()
    draw.text((30,20),'ORIGINAL WING MATERIALS / UV feather root at bottom',fill=(229,231,237),font=font)
    for x,img,title in [(0,hunter,'HUNTER / ivory-gold vane'),(480,dread,'DREADLORD / violet cracked vane')]:
        # A checkerboard makes it easy to inspect translucent feather edges.
        for cy in range(85,987,28):
            for cx in range(x+24,x+456,28):
                tone=(25,30,42) if (cx//28+cy//28)%2==0 else (33,38,51)
                draw.rectangle((cx,cy,min(cx+27,x+455),min(cy+27,986)),fill=tone)
        scaled=img.resize((448,896),RESAMPLE)
        canvas.paste(scaled,(x+16,88),scaled)
        draw.text((x+28,998),title,fill=(220,220,230),font=small)
    draw.text((991,100),'Neutral turbulent glow',fill=(220,220,230),font=small)
    canvas.paste(energy_image,(1056,150),energy_image)
    # Assemble mini wing fans on a dark ground to show actual solid-vane layering.
    for row,material,color,label in [(450,hunter,(249,210,123),'Layered feather fan'),(735,dread,(188,142,245),'Layered feather fan')]:
        fan=Image.new('RGBA',(470,250))
        for i in range(9):
            fh=int(225-i*13)
            f=material.resize((max(28,int(fh*.30)),fh),RESAMPLE)
            # Rotate around the root. Pillow rotation is only for this contact sheet.
            plate=Image.new('RGBA',(470,470));plate.paste(f,(235-f.width//2,235-f.height),f)
            rotated=plate.rotate(-10-i*8,resample=Image.BICUBIC,center=(235,235))
            fan=Image.alpha_composite(fan,rotated.crop((50,10,520,260)))
        canvas.paste(fan,(960,row-105),fan)
        draw.text((1000,row+160),label,fill=color,font=small)
    canvas.save(PREVIEW,optimize=True)


if __name__=='__main__':
    hunter=feather('hunter');dread=feather('dread');glow=energy()
    for name,img in [('wing_feather_hunter',hunter),('wing_feather_dread',dread),('wing_energy',glow)]:
        print(save_asset(name,img))
    preview(hunter,dread,glow)
    print(PREVIEW)
