"""Original real-time VFX textures: Cartesian turbulence, neutral ribbons and runes.

No screenshots or third-party textures are sampled. The 16-frame atlases loop exactly.
"""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

target=Path(__file__).resolve().parents[1]/'src/main/resources/assets/megawalls/textures/effect'
target.mkdir(parents=True,exist_ok=True)

def save(name,im):
    im.save(target/name,optimize=True)
    (target/(name+'.mcmeta')).write_text('{"texture":{"blur":true,"clamp":true}}\n',encoding='utf-8')

x=np.linspace(-1,1,256)[None,:]
profile=np.exp(-x*x*12)*.65+np.exp(-x*x*220)*.35
rgb=np.repeat(np.repeat(profile[:,:,None],4,axis=2),32,axis=0)
save('skill_filament.png',Image.fromarray((rgb*255).astype('uint8'),'RGBA'))

size=256
axis=(np.arange(size,dtype=np.float32)+.5-size/2)/(size/2)
x,y=np.meshgrid(axis,axis)
r=np.sqrt(x*x+y*y)

def turbulence(x,y,t):
    # Moving Cartesian eddies avoid the old flat petal/flower appearance.
    wx=x+.12*np.sin(y*8+t)+.06*np.sin(y*21-t*2)
    wy=y+.10*np.sin(x*7-t)+.05*np.cos(x*19+t*2)
    return (.46*np.sin(wx*9+wy*7+t)+.25*np.sin(wx*19-wy*14-t*2)
            +.17*np.cos(wx*33+wy*29+t*3)+.09*np.sin(wx*61-wy*47-t*4))

orb=np.zeros((1024,1024,4),dtype=np.uint8)
flame=orb.copy()
for frame in range(16):
    t=frame/16*math.tau
    n=turbulence(x,y,t)
    depth=np.sqrt(np.clip(1-(r/.73)**2,0,1))
    cover=np.clip((.76-r+n*.035)/.06,0,1)
    veins=np.clip((.22-n)*2.4,0,1)
    hot=np.clip(.15+depth*.50+veins*.42,0,1)
    red=np.clip(.46+hot*.70,0,1)
    green=np.clip(.03+hot**2*.92,0,1)
    blue=np.clip(hot**5*.48,0,1)
    rim=np.exp(-((r-.73-n*.03)/.055)**2)
    rgb=np.stack([red,green,blue],axis=2)
    rgb[:,:,0]=np.maximum(rgb[:,:,0],rim)
    rgb[:,:,1]=np.maximum(rgb[:,:,1],rim*.8)
    alpha=np.maximum(cover,rim*.8)*np.clip((.94-r)*12,0,1)
    tile=np.dstack([rgb,alpha])
    orb[frame//4*size:(frame//4+1)*size,frame%4*size:(frame%4+1)*size]=(np.clip(tile,0,1)*255).astype('uint8')
    # Tapered rising flame, stretched noise travels upward, with a bright base.
    height=(1-y)*.5
    width=.46*(1-height)**.65+.025
    curl=.11*np.sin(height*9-t)+.045*np.sin(height*23+t*2)
    field=1-np.abs(x-curl)/np.maximum(width,.02)
    fn=turbulence(x*1.4,y-t*.14,t)
    a=np.clip((field+fn*.44-.12)*2,0,1)*np.clip(height*12,0,1)*np.clip((1-height)*9,0,1)
    warmth=np.clip(field*.65+(1-height)*.35,0,1)
    tile=np.dstack([np.ones_like(x),.13+.84*warmth**1.2,.012+.52*warmth**3,a])
    flame[frame//4*size:(frame//4+1)*size,frame%4*size:(frame%4+1)*size]=(np.clip(tile,0,1)*255).astype('uint8')
save('skill_magma_orb.png',Image.fromarray(orb,'RGBA'))
save('skill_flame.png',Image.fromarray(flame,'RGBA'))

# Authored geometric seal with a double rim, a rune band, six satellites and a core.
im=Image.new('RGBA',(768,768))
d=ImageDraw.Draw(im)
def circle(cx,cy,r,w=2,a=255):d.ellipse((cx-r,cy-r,cx+r,cy+r),outline=(255,255,255,a),width=w)
def polygon(cx,cy,r,n,rot,w=2,a=255):
    pts=[(cx+math.cos(rot+i*math.tau/n)*r,cy+math.sin(rot+i*math.tau/n)*r) for i in range(n)]
    d.line(pts+[pts[0]],fill=(255,255,255,a),width=w)
for rad,w in [(360,3),(352,2),(315,2),(302,2),(165,2),(150,3),(65,2)]:circle(384,384,rad,w)
for i in range(60):
    a=i*math.tau/60
    # Broken strokes form original angular runes instead of legible real-world text.
    center=np.array([384+math.cos(a)*334,384+math.sin(a)*334])
    tangent=np.array([-math.sin(a),math.cos(a)])
    normal=np.array([math.cos(a),math.sin(a)])
    points=[(-6,-9),(-6,9),(4,9),(4,0),(-2,0),(6,-8)]
    if i%3==0:points=[(-6,8),(0,-9),(6,8),(-6,2),(6,2)]
    if i%3==1:points=[(-5,-9),(5,-9),(5,8),(-5,8),(-5,0),(4,0)]
    d.line([tuple(center+tangent*u+normal*v) for u,v in points],fill=(255,255,255,245),width=3)
for i in range(6):
    a=i*math.tau/6
    cx,cy=384+math.cos(a)*234,384+math.sin(a)*234
    circle(cx,cy,49,2);circle(cx,cy,42,2)
    polygon(cx,cy,35,3,a);polygon(cx,cy,35,3,a+math.pi)
    circle(cx,cy,10,2)
polygon(384,384,143,6,0,3)
polygon(384,384,126,6,math.pi/6,2)
polygon(384,384,110,3,0,3)
polygon(384,384,110,3,math.pi,3)
for i in range(12):
    a=i*math.tau/12
    d.line([(384+math.cos(a)*174,384+math.sin(a)*174),(384+math.cos(a)*185,384+math.sin(a)*185)],fill='white',width=3)
save('skill_sigil.png',im)

# An opaque-enough luminous pupil field with a soft pointed silhouette.
xx,yy=np.meshgrid(np.linspace(-1,1,512),np.linspace(-1,1,256))
edge=(1-np.abs(xx))**1.8*.86
coverage=np.clip((edge-np.abs(yy))*28,0,1)
grain=.75+.12*np.sin(xx*35+yy*9)+.08*np.cos(yy*43-xx*17)
eye=np.stack([np.ones_like(xx),np.clip(.28+grain*.22,0,1),np.full_like(xx,.025),coverage*.72],axis=2)
save('skill_eye.png',Image.fromarray((eye*255).astype('uint8'),'RGBA'))
flow=np.zeros((1024,1024,4),dtype=np.uint8)
for f in range(16):
    t=f/16*math.tau
    edge=(1-np.abs(x))**1.8*.86
    coverage=np.clip((edge-np.abs(y))*35,0,1)
    n=turbulence(x*1.7,y,t)
    hot=np.clip(.3+n*.35+.22*np.exp(-((x/.3)**2+(y/.6)**2)),0,1)
    tile=np.stack([.38+hot*.62,.12+hot*.58,.012+hot**2*.18,coverage*.8],axis=2)
    flow[f//4*256:(f//4+1)*256,f%4*256:(f%4+1)*256]=(np.clip(tile,0,1)*255).astype('uint8')
save('skill_eye_flow.png',Image.fromarray(flow,'RGBA'))
print('Generated: neutral filament, animated magma orb/flame, seal, eye field')
