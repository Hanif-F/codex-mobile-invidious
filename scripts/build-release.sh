#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../android"
if [[ -z "${JAVA_HOME:-}" && -x /opt/android-studio/jbr/bin/java ]]; then
  export JAVA_HOME=/opt/android-studio/jbr
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
keytool_bin="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
if [[ ! -f signing.properties ]]; then
  umask 077
  mkdir -p keystore
  python3 - <<'PY'
from pathlib import Path
import secrets
password = secrets.token_hex(32)
Path('keystore/storepass').write_text(password)
Path('signing.properties').write_text('storeFile=keystore/mobivious.jks\nkeyAlias=mobivious\nstorePassword=' + password + '\nkeyPassword=' + password + '\n')
PY
  "$keytool_bin" -genkeypair -keystore keystore/mobivious.jks -alias mobivious \
    -storepass:file keystore/storepass -keypass:file keystore/storepass \
    -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Mobivious Personal, O=Mobivious' >/dev/null
fi
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease --console=plain
apk_name="$(python3 - <<'PY'
import json
from pathlib import Path
metadata = json.loads(Path('app/build/outputs/apk/release/output-metadata.json').read_text())
version = metadata['elements'][0]['versionName']
print(f'Mobivious-{version}.apk')
PY
)"
mkdir -p ../artifacts
cp app/build/outputs/apk/release/app-release.apk "../artifacts/$apk_name"
(
  cd ../artifacts
  sha256sum "$apk_name" > "$apk_name.sha256"
  cat "$apk_name.sha256"
)
printf '%s\n' 'Preserve android/keystore and android/signing.properties securely for future updates.'
