import subprocess,time,re,xml.etree.ElementTree as ET
prefix=['adb','-s','emulator-5554']
def adb(*args):return subprocess.run(prefix+list(args),check=True,stdout=subprocess.PIPE).stdout
def tree():
 adb('shell','uiautomator','dump','/sdcard/overhaul-manual.xml')
 return ET.fromstring(adb('shell','cat','/sdcard/overhaul-manual.xml'))
def point(label):
 ns=list(tree().iter('node'));nodes=[n for n in ns if n.get('content-desc')==label] or [n for n in ns if n.get('text')==label]
 if not nodes:raise RuntimeError('Missing '+label)
 b=list(map(int,re.findall(r'\d+',nodes[0].get('bounds'))));return ((b[0]+b[2])//2,(b[1]+b[3])//2)
def tap(label,wait=.7):
 x,y=point(label);adb('shell','input','tap',str(x),str(y));time.sleep(wait)
def shot(path):
 with open(path,'wb') as f:f.write(adb('exec-out','screencap','-p'))
