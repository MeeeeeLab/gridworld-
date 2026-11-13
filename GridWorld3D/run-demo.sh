#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_dir"
# Both clients use the same keyboard through the active window; the server binds loopback only.
exec bash ./gradlew --project-cache-dir "${GRADLE_USER_HOME:-$HOME/.gradle}/gridworld-demo-project-cache" \
  -I "$project_dir/demo.init.gradle" :desktop:raceDemo "$@"
