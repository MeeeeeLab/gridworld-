#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
java_bin=java
if [[ -n "${JAVA_HOME:-}" ]]; then java_bin="$JAVA_HOME/bin/java"; fi
if [[ "$(uname -s)" == Darwin ]]; then
  if java21_home=$(/usr/libexec/java_home -v 21 2>/dev/null); then
    java_bin="$java21_home/bin/java"
  else
    echo "Java 21 was not found. Using the current Java; Java 21 is recommended for this demo."
  fi
fi
exec "$java_bin" --enable-native-access=ALL-UNNAMED -cp 'lib/*' RaceLauncher "$@"
