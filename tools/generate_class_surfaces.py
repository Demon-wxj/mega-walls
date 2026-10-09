"""Original analytic smoke, water and healing-leaf materials; no third-party images."""
from pathlib import Path
import numpy as np
from PIL import Image

target=Path(__file__).resolve().parents[1]/'src/main/resources/assets/megawalls/textures/effect'
target.mkdir(parents=True,exist_ok=True)
n=256
u,v=np.meshgrid((np.arange(n,dtype=np.float32)+.5)/n,(np.arange(n,dtype=np.float32)+.5)/n)
x,y=u*2-1,v*2-1

def save(name,brightness,alpha):
    rgba=np.zeros((n,n,4),dtype=np.uint8)
    light=np.broadcast_to(np.clip(brightness,0,1),(n,n))
    rgba[...,:3]=(light[...,None]*255).astype(np.uint8)
    rgba[...,3]=(np.clip(alpha,0,1)*255).astype(np.uint8)
    rgba[rgba[...,3]==0,:3]=0
    Image.fromarray(rgba,'RGBA').save(target/name)

# Distorted finite cloud with transparent margins, usable as mist or warm excavation dust.
warp_x=x+.13*np.sin(y*7+x*4)+.045*np.sin(y*19-x*11)
warp_y=y+.11*np.sin(x*6-y*3)+.05*np.sin(x*15+y*12)
radius=np.sqrt(warp_x**2+warp_y**2)
grain=.56+.17*np.sin(warp_x*13+warp_y*7)+.12*np.sin(warp_x*24-warp_y*17)+.06*np.sin(x*53+y*41)
cloud=np.clip(1-radius/.92,0,1)**1.2
margin=np.clip((1-np.maximum(abs(x),abs(y)))/.15,0,1)
save('class_smoke.png',.68+.28*np.clip(grain,0,1),cloud*np.clip(grain+.16,0,1)*margin)

# A seamless horizontal wave material; vertical edges fade to allow curved curtain geometry.
flow=u+.045*np.sin(v*13+u*2*np.pi)+.015*np.sin(v*31-u*6*np.pi)
stripes=(.5+.5*np.sin(flow*12*np.pi+v*4))**5
small=(.5+.5*np.sin(flow*34*np.pi-v*9))**8
edge=np.sin(np.pi*v)**.7
save('class_water.png',.63+.30*stripes+.07*small,(.20+.36*stripes+.12*small)*edge)

# An elongated leaf of light with a central vein and alternating side veins.
half_width=.58*np.sin(np.pi*v)**.8
leaf=np.clip((half_width-abs(x))/.065,0,1)
vein=np.exp(-(x/.035)**2)
barbs=(.5+.5*np.cos(v*31+abs(x)*19))**14
tip_margin=np.minimum(np.clip(v/.04,0,1),np.clip((1-v)/.04,0,1))
save('class_leaf.png',.64+.32*vein+.12*barbs,leaf*(.46+.4*vein+.14*barbs)*tip_margin)

# Thin, broken foam crests with small bubbles. Transparent gaps keep the player readable.
foam_line=.54+.075*np.sin(u*4*np.pi)+.032*np.sin(u*12*np.pi)
foam=np.exp(-((v-foam_line)/.050)**2)
for k in range(13):
    cx=(k+.5)/13
    cy=.45+.14*np.sin(k*2.39996)
    rad=.022+.012*((k*7)%11)/10
    ring=np.sqrt((u-cx)**2+(v-cy)**2)
    foam=np.maximum(foam,np.exp(-((ring-rad)/.008)**2)*.85)
foam*=.55+.45*(.5+.5*np.sin(u*28*np.pi))
save('class_foam.png',1,foam*np.sin(np.pi*u)**.25)

# Small faceted chips are mesh-only cosmetics, never additional block entities.
polygon=(np.abs(x*.87+y*.20)+np.abs(y*.72-x*.10))
shard=np.clip((.84-polygon)/.035,0,1)
facet=.58+.19*(x<y)+.11*(x+y>.05)+.045*np.sin(x*37+y*21)
save('class_shard.png',facet,shard)
print('Generated class smoke, water, leaf, foam and shard materials (256x256 RGBA).')
