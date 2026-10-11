from pathlib import Path
from PIL import Image,ImageDraw
r=Path('docs/visual-audit-2026-10-11')
for group in (r/'screenshots').iterdir():
 if not group.is_dir():continue
 files=sorted(group.glob('*.png'))
 for n,start in enumerate(range(0,len(files),12),1):
  sub=files[start:start+12];out=Image.new('RGB',(1200,((len(sub)+3)//4)*640),(224,224,224));d=ImageDraw.Draw(out)
  for k,p in enumerate(sub):
   x=k%4*300;y=k//4*640;im=Image.open(p).convert('RGB');im.thumbnail((294,600));out.paste(im,(x+(300-im.width)//2,y+34));d.text((x+4,y+3),p.stem[:43],fill=(0,0,0))
  out.save(r/'contact-sheets'/f'{group.name}-{n:02}.jpg',quality=90)
print('Grouped contact sheets ready')
