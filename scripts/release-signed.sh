#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd "$(dirname "$0")/.." && pwd -P)
version=1.0.0
output_dir=${1:-"$repository_root/dist/iDuoLauncher-$version"}

for variable_name in DUO_RELEASE_STORE_FILE DUO_RELEASE_STORE_PASSWORD DUO_RELEASE_KEY_ALIAS DUO_RELEASE_KEY_PASSWORD; do
    if [[ -z "${!variable_name:-}" ]]; then
        echo "Missing required release signing variable: $variable_name" >&2
        exit 1
    fi
done
if [[ ! -f "$DUO_RELEASE_STORE_FILE" ]]; then
    echo "DUO_RELEASE_STORE_FILE does not point to a file." >&2
    exit 1
fi
store_directory=$(cd "$(dirname "$DUO_RELEASE_STORE_FILE")" && pwd -P)
store_file="$store_directory/$(basename "$DUO_RELEASE_STORE_FILE")"
case "$store_file" in
    "$repository_root"/*)
        echo "The release keystore must be stored outside the repository." >&2
        exit 1
        ;;
esac
if [[ "$output_dir" != /* ]]; then
    output_dir="$PWD/$output_dir"
fi
if [[ -e "$output_dir" ]]; then
    echo "Refusing to replace existing release output: $output_dir" >&2
    exit 1
fi

"$repository_root/scripts/gradle.sh" :app:assembleRelease :app:bundleRelease
apk_source="$repository_root/app/build/outputs/apk/release/app-release.apk"
bundle_source="$repository_root/app/build/outputs/bundle/release/app-release.aab"
if [[ ! -f "$apk_source" || ! -f "$bundle_source" ]]; then
    echo "Signed release APK or Play bundle was not produced at the expected path." >&2
    exit 1
fi

output_parent=$(dirname "$output_dir")
mkdir -p "$output_parent"
staging_dir=$(mktemp -d "$output_parent/.duo-release.XXXXXX")
cleanup() { rm -rf "$staging_dir"; }
trap cleanup EXIT

release_name=$(basename "$output_dir")
package_dir="$staging_dir/$release_name"
mkdir -p "$package_dir"
apk_name="iDuoLauncher-$version-release.apk"
bundle_name="iDuoLauncher-$version-release.aab"
source_name="iDuoLauncher-$version-source"
cp -p "$apk_source" "$package_dir/$apk_name"
cp -p "$bundle_source" "$package_dir/$bundle_name"
"$repository_root/scripts/export-public-source.sh" "$staging_dir/$source_name"
(cd "$staging_dir" && COPYFILE_DISABLE=1 tar -czf "$package_dir/$source_name.tar.gz" "$source_name")

if command -v sha256sum >/dev/null 2>&1; then
    (cd "$package_dir" && sha256sum "$apk_name" "$bundle_name" "$source_name.tar.gz" > SHA256SUMS.txt)
elif command -v shasum >/dev/null 2>&1; then
    (cd "$package_dir" && shasum -a 256 "$apk_name" "$bundle_name" "$source_name.tar.gz" > SHA256SUMS.txt)
else
    echo "Neither sha256sum nor shasum is available." >&2
    exit 1
fi

mv "$package_dir" "$output_dir"
trap - EXIT
rm -rf "$staging_dir"
echo "Signed release package created at $output_dir"
