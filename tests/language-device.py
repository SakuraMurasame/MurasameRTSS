"""ADB UI smoke test on an unlocked device. Restores the original app language."""
import argparse, json, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
p = argparse.ArgumentParser()
p.add_argument('--adb', default='adb'); p.add_argument('--serial', required=True)
a = p.parse_args(); root = Path(__file__).resolve().parents[1]
out = root / 'build/language-device'; out.mkdir(parents=True, exist_ok=True)
strings = {r['zh']: r for r in json.loads((root / 'tools/localization.json').read_text('utf-8'))}
def tr(s, lang): return strings[s][lang]
def adb(*args): return subprocess.check_output([a.adb, '-s', a.serial, *args], timeout=30).decode('utf-8', errors='replace')
def dump():
    adb('shell', 'uiautomator', 'dump', '/sdcard/mrtss-language.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/mrtss-language.xml'))
def tap(text, tree=None, prefix=False):
    if tree is None: tree = dump()
    for n in tree.iter('node'):
        value = n.get('text', '')
        if value == text or (prefix and value.startswith(text)):
            x1,y1,x2,y2 = map(int, re.findall(r'\d+', n.get('bounds')))
            adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)); time.sleep(.3); return
    raise AssertionError('Missing UI text: ' + text)
def check(text, tree): assert any(n.get('text') == text for n in tree.iter('node')), text
def capture(name):
    with (out / (name + '.png')).open('wb') as f:
        subprocess.run([a.adb, '-s', a.serial, 'exec-out', 'screencap', '-p'], stdout=f, check=True, timeout=30)
    ET.ElementTree(dump()).write(out / (name + '.xml'), encoding='utf-8')
adb('shell', 'am', 'start', '-n', 'io.perfoverlay.app/.MainActivity'); tree = dump()
for lang in ['zh','ja','en','fr','de']:
    if any(n.get('text') == tr('关于',lang) for n in tree.iter('node')):
        current = lang; tap(tr('关于',lang), tree); break
else: raise AssertionError('Main navigation unavailable')
tap(tr('应用语言',current), prefix=True); tree = dump()
original = next(n.get('text') for n in tree.iter('node') if n.get('checked') == 'true')
adb('shell', 'input', 'keyevent', '4')
for lang,name in [('en','English'),('ja','日本語'),('fr','Français'),('de','Deutsch'),('zh','简体中文')]:
    tap(tr('应用语言',current), prefix=True); tap(name); current = lang; tree = dump()
    check(tr('关于程序',lang),tree)
    assert '图标基于你提供' not in ET.tostring(tree, encoding='unicode')
    capture(lang + '-about')
    for tab,marker in [('监控','性能概览'),('样式','外观主题'),('指标','显示项目'),('记录','记录与曲线')]:
        tap(tr(tab,lang)); tree = dump(); check(tr(marker,lang), tree)
        if tab == '记录': capture(lang + '-history'); adb('shell', 'input', 'keyevent', '4')
        elif tab == '监控': capture(lang + '-monitor')
    tap(tr('关于',lang)); print('PASS device language:',lang,flush=True)
adb('shell', 'am', 'force-stop', 'io.perfoverlay.app')
adb('shell', 'am', 'start', '-n', 'io.perfoverlay.app/.MainActivity')
check(tr('监控','zh'),dump()); tap(tr('关于','zh')); tap(tr('应用语言','zh'),prefix=True)
names = {'English','日本語','Français','Deutsch','简体中文'}
tap(original if original in names else tr('跟随系统','zh'))
print('PASS preference persistence; restored original language',flush=True)
