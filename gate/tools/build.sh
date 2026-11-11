#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=$(sed -n 's/^var version = "\(.*\)"/\1/p' main.go)
for target in linux/amd64 linux/arm64 windows/amd64; do
    os=${target%/*} arch=${target#*/} ext=
    [ "$os" = windows ] && ext=.exe
    CGO_ENABLED=0 GOOS=$os GOARCH=$arch go build -trimpath -ldflags "-s -w" -o "../dist/tcguard-gate-$version-$os-$arch$ext" .
done
ls ../dist/tcguard-gate-"$version"-*
