from pathlib import Path
import subprocess
from PIL import Image,ImageOps,ImageDraw
root=Path('docs/visual-audit-2026-10-11')
paths=subprocess.check_output(['adb','shell','find','/data/local/tmp','-name','*.png','-newer','/data/local/tmp/visual-audit-start'],text=True).splitlines()
for remote in paths:
    local=root/'screenshots'/Path(remote).parent.name/Path(remote).name
    local.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run(['adb','pull',remote,str(local)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
# Remove accidentally collected old images; keep only new files in the manifest.
fresh={str(root/'screenshots'/Path(p).parent.name/Path(p).name) for p in paths}
for p in (root/'screenshots').glob('*/*.png'):
    if str(p) not in fresh:p.unlink()
files=sorted((root/'screenshots').rglob('*.png'))
(root/'evidence-manifest.txt').write_text('\n'.join(str(p.relative_to(root)) for p in files)+'\n')
for n,start in enumerate(range(0,len(files),12),1):
    subset=files[start:start+12]
    sheet=Image.new('RGB',(1200,4*640),(224,224,224));d=ImageDraw.Draw(sheet)
    for k,p in enumerate(subset):
        x=(k%4)*300;y=(k//4)*640
        im=Image.open(p).convert('RGB');im.thumbnail((294,594))
        sheet.paste(im,(x+(300-im.width)//2,y+42))
        label=f'{start+k+1}: {p.parent.name}\n{p.stem}'
        for line_i,line in enumerate(label.splitlines()):d.text((x+4,y+line_i*14),line[:47],fill=(0,0,0))
    (root/'contact-sheets').mkdir(exist_ok=True)
    sheet.save(root/'contact-sheets'/f'sheet-{n:02}.jpg',quality=88)
print(f'{len(paths)} fresh device captures; {len(files)} total screenshots; {(len(files)+11)//12} contact sheets')
