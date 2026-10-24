"""Generate an offline visual harness from the actual mini program sources."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
names=['home','orders','order-detail','profile','login','places','settings','negotiation','coupons','safety']
modules={str(f.relative_to(root)):f.read_text() for d in ['pages','lib','config'] for f in (root/d).rglob('*.js')}
templates={n:(root/'pages'/n/'index.wxml').read_text() for n in names}
css=(root/'pages/shared.wxss').read_text()+''.join((root/'pages'/n/'index.wxss').read_text() for n in names)
payload='window.SOURCE='+json.dumps({'modules':modules,'templates':templates,'css':css},ensure_ascii=False)+';'
(root/'preview/source.js').write_text(payload)
print('Generated preview/source.js from actual pages/lib/config; no cloud connection.')
