#!/usr/bin/env python3
"""Release signer: persistent key is provided only by a single GitHub Actions secret."""
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SECRET = os.environ.get('VITA_SIGNING_BUNDLE','').strip()
if not SECRET:
    raise SystemExit('GitHub Secret VITA_SIGNING_BUNDLE is not configured')
raw = base64.b64decode(SECRET, validate=True)
if len(raw)>64*1024:
    raise SystemExit('Signing bundle is too large')
with zipfile.ZipFile(io.BytesIO(raw)) as bundle:
    config=json.loads(bundle.read('signing.json'))
    key=bundle.read('vita-release.p12')
expected=config['certificate_sha256'].upper()
source=(ROOT/'app/src/main/java/dev/infend/vitasolver/OtaManager.java').read_text()
match=re.search(r'TRUSTED_CERT_SHA256="([0-9A-F]{64})"',source)
if match is None or match.group(1)!=expected:
    raise SystemExit('APK update verifier pins a different release certificate')
inputs=list((ROOT/'app/build/outputs/apk/release').glob('*.apk'))
if len(inputs)!=1:
    raise SystemExit('Expected exactly one unsigned release APK, got '+str(len(inputs)))
tools=Path(os.environ['ANDROID_SDK_ROOT'])/'build-tools'/'36.0.0'
dest=ROOT/'dist/VITA-SOLVER.apk'
dest.parent.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='vita-identity-') as temp:
    temp=Path(temp)
    store=temp/'vita-release.p12'
    store.write_bytes(key)
    store.chmod(0o600)
    cert=temp/'cert.der'
    env=os.environ.copy()
    env['STORE_PW']=config['keystore_password']
    env['KEY_PW']=config['key_password']
    subprocess.run(['keytool','-exportcert','-storetype','PKCS12','-alias',config['alias'],
        '-keystore',str(store),'-storepass:env','STORE_PW','-file',str(cert)],
        env=env,check=True,capture_output=True)
    actual=hashlib.sha256(cert.read_bytes()).hexdigest().upper()
    if actual!=expected:
        raise SystemExit('Signing bundle certificate mismatch')
    aligned=temp/'aligned.apk'
    subprocess.run([str(tools/'zipalign'),'-f','-p','4',str(inputs[0]),str(aligned)],check=True)
    subprocess.run([str(tools/'apksigner'),'sign','--ks',str(store),'--ks-type','PKCS12',
        '--ks-key-alias',config['alias'],'--ks-pass','env:STORE_PW','--key-pass','env:KEY_PW',
        '--out',str(dest),str(aligned)],env=env,check=True)
    subprocess.run([str(tools/'apksigner'),'verify','--print-certs',str(dest)],check=True)
    subprocess.run([str(tools/'zipalign'),'-c','-p','4',str(dest)],check=True)
print('APK signed with pinned certificate '+actual)
