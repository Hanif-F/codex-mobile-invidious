from device_ui import *
from pathlib import Path
import json
out=Path('.tools/liquid-glass-overhaul/watch-motion');out.mkdir(exist_ok=True)
events=[]; start=time.monotonic()
def mark(action):events.append({'seconds':round(time.monotonic()-start,3),'action':action})
record=subprocess.Popen(prefix+['shell','screenrecord','--time-limit','30','/sdcard/overhaul-watch-motion.mp4'],stdout=subprocess.DEVNULL)
try:
 time.sleep(1)
 x,y=point('Description');mark('Description DOWN');adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.14)
 mark('Description UP');adb('shell','input','motionevent','UP',str(x),str(y));time.sleep(.65)
 mark('Collapse description');tap('Description')
 mark('Open More');tap('More');shot(str(out/'menu.png'))
 mark('Dismiss More');adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.5)
 mark('Watch to mini');adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.6)
 ns=[n for n in tree().iter('node') if n.get('text','').startswith('A quiet moment') and int(re.findall(r'\d+',n.get('bounds'))[1])>2000]
 if not ns:raise RuntimeError('Mini title missing')
 b=list(map(int,re.findall(r'\d+',ns[-1].get('bounds'))));x=(b[0]+b[2])//2;y=(b[1]+b[3])//2
 mark('Restore mini');adb('shell','input','tap',str(x),str(y));time.sleep(.08)
 mark('Reverse restoration with Back');adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.65)
 mark('Restore fully');adb('shell','input','tap',str(x),str(y));time.sleep(.8)
 mark('Open description again');tap('Description');shot(str(out/'restored.png'))
except Exception as error:events.append({'error':str(error)})
record.wait(timeout=35)
adb('pull','/sdcard/overhaul-watch-motion.mp4',str(out/'motion.mp4'))
(out/'input-events.json').write_text(json.dumps(events,indent=2));print(json.dumps(events))
