"""Original 16-frame turbulent fireball texture. Pure generated math, no outside art."""
from pathlib import Path
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parents[1]
target=root/'src/main/resources/assets/megawalls/textures/effect/skill_fireball.png'
size=256
atlas=np.zeros((size*4,size*4,4),dtype=np.uint8)
axis=(np.arange(size,dtype=np.float32)+.5-size/2)/(size/2)
x,y=np.meshgrid(axis,axis)
r=np.sqrt(x*x+y*y)
angle=np.arctan2(y,x)
for frame in range(16):
    time=frame/16*2*np.pi
    swirl=angle+.24*np.sin(r*7-time*1.7)
    edge=(.65+.075*np.sin(swirl*5+time*1.3)+.045*np.sin(swirl*11-time*2.1)
          +.023*np.sin(swirl*23+time*2.7))
    cover=np.clip((edge-r)/.09,0,1)
    corona=np.clip((edge+.16-r)/.16,0,1)**2
    vein=.5+.25*np.sin(swirl*7-r*15+time*2.2)+.18*np.sin(swirl*17+r*23-time*3.1)
    core=np.exp(-((r/.38)**2))
    hot=np.clip(.20+.78*core+.16*vein,0,1)
    tongues=corona*(.65+.35*np.clip(vein,0,1))
    red=np.clip(.85*cover+.55*tongues+.25*core,0,1)
    green=np.clip(.11*cover+.58*hot*cover+.13*tongues,0,1)
    blue=np.clip(.015*cover+.20*core*cover,0,1)
    alpha=np.clip(np.maximum(cover*.92,tongues*.6),0,1)
    rgba=np.stack([red,green,blue,alpha],axis=2)
    atlas[(frame//4)*size:(frame//4+1)*size,(frame%4)*size:(frame%4+1)*size]=np.rint(rgba*255).astype(np.uint8)
target.parent.mkdir(parents=True,exist_ok=True)
Image.fromarray(atlas,'RGBA').save(target,optimize=True)
target.with_suffix(target.suffix+'.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}\n',encoding='utf-8')
print(target)
