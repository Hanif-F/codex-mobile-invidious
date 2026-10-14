#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../android"
initialize_signing=false
case "${1:-}" in
  --init-signing) initialize_signing=true; shift ;;
  --help)
    echo 'Usage: scripts/build-release.sh [--init-signing]'
    echo '--init-signing creates the first signing identity only in an empty keystore directory.'
    exit 0 ;;
esac
[[ $# -eq 0 ]] || { echo "Unknown option: $1" >&2; exit 2; }
if [[ -z "${JAVA_HOME:-}" && -x /opt/android-studio/jbr/bin/java ]]; then
  export JAVA_HOME=/opt/android-studio/jbr
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
keytool_bin="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
if "$initialize_signing"; then
  umask 077
  python3 - "$keytool_bin" <<'PY'
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile

configuration = Path('signing.properties')
directory = Path('keystore')
if configuration.exists() or directory.exists() and any(directory.iterdir()):
    raise SystemExit('Signing material already exists. Restore/use its configuration; do not replace the release identity.')
directory.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix='.initialize-', dir=directory) as temporary:
    stage = Path(temporary)
    password = secrets.token_hex(32)
    password_file = stage / 'password'
    password_file.write_text(password)
    subprocess.run([sys.argv[1], '-genkeypair', '-keystore', str(stage / 'mobivious.jks'),
                    '-alias', 'mobivious', '-storepass:file', str(password_file),
                    '-keypass:file', str(password_file), '-keyalg', 'RSA', '-keysize', '4096',
                    '-validity', '10000', '-dname', 'CN=Mobivious Personal, O=Mobivious'],
                   check=True, stdout=subprocess.DEVNULL)
    staged_configuration = stage / 'signing.properties'
    staged_configuration.write_text('storeFile=keystore/mobivious.jks\nkeyAlias=mobivious\n'
                                    f'storePassword={password}\nkeyPassword={password}\n')
    # Exclusive links never overwrite material from a concurrent initialization.
    key = directory / 'mobivious.jks'
    os.link(stage / 'mobivious.jks', key)
    try:
        os.link(staged_configuration, configuration)
    except BaseException:
        key.unlink()  # Only the key created by this invocation.
        raise
PY
elif [[ ! -f signing.properties ]]; then
  echo 'Missing android/signing.properties. Restore the existing release signing material.' >&2
  echo 'For a first release only, use scripts/build-release.sh --init-signing.' >&2
  exit 1
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
