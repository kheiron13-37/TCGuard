#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
version=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$root/pom.xml" | head -1)
out="$root/.build"
rm -rf "$out" && mkdir -p "$out" "$root/dist"
"${JAVAC:-javac}" --release 8 -Xlint:-options -encoding UTF-8 \
    -cp "$root/libs/spigot-1.8.8.jar" -d "$out" $(find "$root/src" -name '*.java')
cp "$root/resources/config.yml" "$out/"
sed "s/\${project.version}/$version/" "$root/resources/plugin.yml" > "$out/plugin.yml"
"${JAR:-jar}" cf "$root/dist/TCGuard-$version.jar" -C "$out" .
echo "dist/TCGuard-$version.jar"
