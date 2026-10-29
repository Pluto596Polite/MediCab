#!/usr/bin/env bash
# Publishes a MediCab release on GitHub. Installed copies of MediCab find it
# the next time they open and offer to update (so does Obtainium).
#
#   1. Bump versionCode and versionName in app/build.gradle.kts and commit.
#   2. tools/release.sh "What's new in this version"
#
# The APK is built and signed here with the upload key from keystore.properties,
# so the key never leaves this machine. Every release must use the same key, or
# Android refuses to install it over the previous version.
set -euo pipefail
cd "$(dirname "$0")/.."

usage='Usage: tools/release.sh "What is new in this version"'
notes=${1:?$usage}
version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
tag="v$version"

die() { echo "release: $*" >&2; exit 1; }
[ -n "$version" ] || die "couldn't read versionName from app/build.gradle.kts"
[ -f keystore.properties ] || die "keystore.properties is missing, so the APK can't be signed"
[ -z "$(git status --porcelain)" ] || die "commit or stash your changes first"
[ "$(git rev-parse --abbrev-ref HEAD)" = main ] || die "release from main"
gh release view "$tag" >/dev/null 2>&1 && die "$tag is already released; bump versionCode and versionName"

if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/Library/Java/JavaVirtualMachines/temurin-21.0.12/Contents/Home" ]; then
    export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-21.0.12/Contents/Home"
fi

echo "Testing and building MediCab ${version}..."
./gradlew --quiet :app:testDebugUnitTest :app:assembleRelease

# One APK per processor type (the in-app updater picks the right one) plus a universal one.
rm -rf build/release && mkdir -p build/release
for built in app/build/outputs/apk/release/app-*-release.apk; do
    abi=$(basename "$built" | sed 's/^app-\(.*\)-release\.apk$/\1/')
    cp "$built" "build/release/MediCab-$version-$abi.apk"
done
ls build/release/*.apk >/dev/null 2>&1 || die "no APKs were built"

git push --quiet origin main
git tag -a "$tag" -m "MediCab $version"
git push --quiet origin "$tag"
gh release create "$tag" build/release/*.apk --title "MediCab $version" --notes "$notes"
echo "Released $tag: $(gh release view "$tag" --json url --jq .url)"
