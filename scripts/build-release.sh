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
./gradlew testReleaseUnitTest assembleRelease --console=plain
mkdir -p ../artifacts
cp app/build/outputs/apk/release/app-release.apk ../artifacts/Mobivious-0.1.0.apk
sha256sum ../artifacts/Mobivious-0.1.0.apk
printf '%s\n' 'Preserve android/keystore and android/signing.properties securely for future updates.'
