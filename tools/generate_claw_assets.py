"""Original dark rend material: a carved center, red fissures and bright torn edges."""
from pathlib import Path
import numpy as np
from PIL import Image
target=Path(__file__).resolve().parents[1]/'src/main/resources/assets/megawalls/textures/effect'
target.mkdir(parents=True,exist_ok=True)
x,y=np.meshgrid(np.linspace(-1,1,256),np.linspace(0,1,1024))
n=.5+.23*np.sin(y*83+x*17)+.16*np.sin(y*217-x*41)+.10*np.cos(y*419+x*73)
rim=np.exp(-((np.abs(x)-.90-.026*np.sin(y*101))/.065)**2)
vein=np.clip(1-np.abs(x-.16*np.sin(y*43)-.10*np.cos(y*97))/.04,0,1)
alpha=np.clip((1-np.abs(x))*30,0,1)*np.clip(y*60,0,1)*np.clip((1-y)*60,0,1)
red=.075+n*.035+rim*.92+vein*.42
green=.008+n*.008+rim*.34+vein*.035
blue=.020+n*.018+rim*.45+vein*.065
rgba=np.dstack([np.clip(red,0,1),np.clip(green,0,1),np.clip(blue,0,1),alpha])
out=target/'claw_rend.png'
Image.fromarray(np.rint(rgba*255).astype('uint8'),'RGBA').save(out,optimize=True)
out.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}\n',encoding='utf-8')
print('Generated claw_rend.png')
