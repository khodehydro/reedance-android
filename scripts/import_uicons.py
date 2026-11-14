"""Convert official Uicons 3.3.1 Regular Rounded outlines into native vectors.
Usage: python scripts/import_uicons.py /path/to/extracted/npm/package
Requires fonttools + brotli. No icon font, HTML or web runtime in the APK.
"""
import sys, re, json, pathlib, shutil
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.transformPen import TransformPen
root = pathlib.Path(__file__).resolve().parents[1]
pkg = pathlib.Path(sys.argv[1])
css = (pkg/'css/regular/rounded.css').read_text()
codepoints = dict(re.findall(r'\.fi-rr-([a-z0-9-]+):before\{content:"\\([0-9a-f]+)"\}', css))
font = TTFont(next((pkg/'css').glob('uicons-regular-rounded-*.woff2')))
glyphs, cmap = font.getGlyphSet(), font.getBestCmap()
names = {'Back':'arrow-left','Home':'home','Explore':'compass-alt','Review':'layers','Library':'books','User':'user','Play':'play','Pause':'pause','Lock':'lock','LockOpen':'lock-open-alt','Bookmark':'bookmark','Quiz':'clipboard-check','Repeat':'arrows-repeat','Replay10':'replay-10','Forward10':'time-forward-ten','Volume':'volume','Mute':'volume-mute','Star':'star','Crown':'crown','Check':'check','All':'apps','Globe':'language'}
manifest = {}
for name, cssname in names.items():
    code = int(codepoints[cssname],16)
    glyph = glyphs[cmap[code]]
    b = BoundsPen(glyphs); glyph.draw(b)
    x0,y0,x1,y1 = b.bounds
    scale = 22/max(x1-x0,y1-y0)
    pen = SVGPathPen(glyphs)
    glyph.draw(TransformPen(pen,(scale,0,0,-scale,12-(x0+x1)/2*scale,12+(y0+y1)/2*scale)))
    resource = 'uicon_'+re.sub(r'(?<!^)(?=[A-Z])','_',name).lower()
    mirror = ' android:autoMirrored="true"' if name == 'Back' else ''
    (root/f'android/app/src/main/res/drawable/{resource}.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"{mirror}>\n    <path android:fillColor="#FF000000" android:pathData="{pen.getCommands()}" />\n</vector>\n')
    manifest[name] = {'glyph': cssname, 'codepoint':hex(code), 'drawable':resource}
(root/'android/app/src/main/assets/uicons-provenance.json').write_text(json.dumps({'package':'@flaticon/flaticon-uicons','version':'3.3.1','style':'Regular Rounded','icons':manifest},indent=2)+'\n')
shutil.copyfile(pkg/'LICENSE',root/'android/app/src/main/assets/Uicons-LICENSE.txt')
