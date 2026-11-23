#!/usr/bin/env python3
"""Generate one official OTA manifest with cumulative missed-version history."""
import argparse
from datetime import datetime,timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.request import urlopen,Request

PACKAGE='dev.infend.vitasolver'
MAX_HISTORY_BYTES=1700000

def history_from_previous(repo):
    url=f'https://github.com/{repo}/releases/latest/download/update.json'
    try:
        request=Request(url,headers={'User-Agent':'VITA-SOLVER-OTA-BUILDER/1.0'})
        with urlopen(request,timeout=15) as stream:
            raw=stream.read(MAX_HISTORY_BYTES+1)
            if len(raw)>MAX_HISTORY_BYTES:raise ValueError('previous manifest too large')
        old=json.loads(raw)
        if old.get('schema')!=1 or old.get('packageName')!=PACKAGE:
            return []
        result=old.get('history') or [dict(versionCode=old['versionCode'],versionName=old['versionName'],notes=old.get('notes',''))]
        return [it for it in result if isinstance(it,dict) and isinstance(it.get('versionCode'),int)]
    except Exception as e:
        print('No readable preceding OTA history: '+str(e))
        return []

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--apk',required=True)
    p.add_argument('--aapt',required=True)
    p.add_argument('--repo',required=True)
    p.add_argument('--output',required=True)
    p.add_argument('--github-output',required=True)
    a=p.parse_args()
    if a.repo.casefold()!='infend/vita-mahjong-solver':raise SystemExit('Untrusted repository for OTA: '+a.repo)
    apk=Path(a.apk)
    badging=subprocess.check_output([a.aapt,'dump','badging',str(apk)],text=True)
    m=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging,re.M)
    if not m:raise SystemExit('aapt did not return APK package version')
    package,code_text,version=m.groups()
    code=int(code_text)
    if package!=PACKAGE or code<1 or not re.fullmatch(r'\d+\.\d+\.\d+',version):
        raise SystemExit('Invalid APK identity or version')
    tag='v'+version
    note_path=Path('release_notes')/(tag+'.txt')
    if not note_path.is_file():raise SystemExit('Missing required release notes: '+str(note_path))
    notes=note_path.read_text(encoding='utf-8').strip()
    if not notes or len(notes)>5000:raise SystemExit('Invalid release notes')
    old=history_from_previous(a.repo)
    entries=[e for e in old if int(e['versionCode'])<code]
    entries.append({'versionCode':code,'versionName':version,'notes':notes})
    # De-duplicate by version code and keep chronological order.
    uniq={int(item['versionCode']):item for item in entries}
    history=[uniq[k] for k in sorted(uniq)]
    data={'schema':1,'packageName':PACKAGE,'versionCode':code,'versionName':version,
          'apkUrl':f'https://github.com/{a.repo}/releases/download/{tag}/VITA-SOLVER.apk',
          'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
          'sizeBytes':apk.stat().st_size,
          'publishedAt':datetime.now(timezone.utc).isoformat(timespec='seconds'),
          'notes':notes,'history':history}
    dest=Path(a.output);dest.parent.mkdir(exist_ok=True,parents=True)
    dest.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    with open(a.github_output,'a',encoding='utf-8') as f:
        f.write(f'tag={tag}\nversion={version}\nversion_code={code}\n')
    print('Generated official OTA manifest for',a.repo,tag)

if __name__=='__main__':main()
