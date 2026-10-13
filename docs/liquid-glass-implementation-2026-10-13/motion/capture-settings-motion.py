import subprocess,time,xml.etree.ElementTree as ET,re,json,sys
from pathlib import Path
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
prefix=['adb','-s','emulator-5554'];events=[]
def adb(*args,**kw):return subprocess.run(prefix+list(args),check=True,stdout=subprocess.PIPE,**kw).stdout
def tap(label):
 adb('shell','uiautomator','dump','/sdcard/overhaul-motion.xml')
 root=ET.fromstring(adb('shell','cat','/sdcard/overhaul-motion.xml'))
 nodes=[n for n in root.iter('node') if n.get('text')==label or n.get('content-desc')==label]
 if not nodes:raise RuntimeError('Missing '+label)
 bounds=list(map(int,re.findall(r'\d+',nodes[0].get('bounds'))));x=(bounds[0]+bounds[2])//2;y=(bounds[1]+bounds[3])//2
 events.append({'seconds':round(time.monotonic()-start,3),'tap':label,'x':x,'y':y})
 adb('shell','input','tap',str(x),str(y));time.sleep(.8)
def swipe(up=True):
 events.append({'seconds':round(time.monotonic()-start,3),'swipe':'up' if up else 'down'})
 adb('shell','input','swipe','700','2250' if up else '950','700','950' if up else '2250','400');time.sleep(.7)
adb('shell','dumpsys','gfxinfo','net.wingress.mobivious.debug','reset')
record=subprocess.Popen(prefix+['shell','screenrecord','--time-limit','30','/sdcard/overhaul-settings-motion.mp4'],stdout=subprocess.DEVNULL)
start=time.monotonic()
try:
 tap('Settings');tap('Playback');swipe();swipe();swipe(False);tap('Cancel');tap('Back from Settings')
except Exception as error:events.append({'error':str(error)})
record.wait(timeout=35)
(out/'input-events.json').write_text(json.dumps(events,indent=2))
(out/'frame-timing.txt').write_bytes(adb('shell','dumpsys','gfxinfo','net.wingress.mobivious.debug','framestats'))
adb('pull','/sdcard/overhaul-settings-motion.mp4',str(out/'motion.mp4'))
print(json.dumps(events))
