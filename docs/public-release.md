# Public release process

iDuo Launcher requires Android API 31 or newer and builds with Java 17. A fresh source export can
build the debug APK, run local unit tests and lint, and assemble an unsigned optimized release:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

The release build enables R8 and resource shrinking. Its keep rules retain the Window Extensions
interfaces that the optional Discover host resolves by name. Launcher persistence uses explicit
`org.json` fields, and Android widget hosts are directly constructed, so they do not need broad
serialization or reflection rules.

## Signed packages

Keep the release keystore outside the repository. The signed release helper requires all four
values below and does not print them:

```sh
export DUO_RELEASE_STORE_FILE=/absolute/path/outside/the/repository/duolauncher-release.jks
export DUO_RELEASE_STORE_PASSWORD='...'
export DUO_RELEASE_KEY_ALIAS='...'
export DUO_RELEASE_KEY_PASSWORD='...'
./scripts/release-signed.sh
```

The public release key is separate from Android's debug key. Because both variants intentionally
use `media.whitewhale.iduo`, Android will not install one as an update to an installation signed by
the other key. Preserve an existing configured debug installation; test the public release on a
separate device or disposable emulator unless a deliberate migration has been planned.

Supplying only some signing values fails configuration. Supplying none leaves ordinary
`assembleRelease` available as an unsigned build, including in CI. The helper refuses a keystore
inside the repository and refuses to overwrite an existing release directory. It packages the
signed APK, the allowlisted public source archive, and SHA-256 checksums under `dist/`.

## Public source export

`PUBLIC-FILES` is the source allowlist. Exporting fails on symlinks, missing required build files,
private paths, key files, non-image files under `docs/images`, or a destination that already
exists:

```sh
./scripts/export-public-source.sh /tmp/DuoLauncher-public
./scripts/check-public-source.sh /tmp/DuoLauncher-public
```

The checker validates a pristine exported tree. Running it with no argument in the private working
tree is expected to reject Git metadata, Gradle output, and internal files that are deliberately
outside the allowlist. CI exports into its temporary directory first, then builds and tests from
that exported copy.

The public tree includes the Android application, Gradle wrapper, unit and instrumentation tests,
release helpers, CI workflow, public root documents, this guide, and optional PNG/WebP screenshots
under `docs/images`. Internal working notes, research, device captures, artifacts, local Android SDK
configuration, and the isolated Discover probe are excluded. The probe remains optional in the
working tree and does not participate in normal launcher builds.

The [user guide](user-guide.md), [troubleshooting](troubleshooting.md), [contributor code map](architecture.md),
[privacy policy](privacy-policy.md), [1.0.0 notes](releases/1.0.0.md) and [beta notes](releases/0.15.0-beta01.md) are part of the explicit public allowlist.

## GitHub publication

Publish from the reviewed public Git checkout, with the chosen license committed. Confirm the
repository owner/name and intended visibility before creating the remote. The repository already
contains a README, license, issue forms, a pull-request template and CI; do not initialize a second
README or license on GitHub when importing it.

For the first upload, create the repository from that checkout with `gh repo create` using its
explicit owner/name, visibility, `--source .`, `--remote origin` and `--push` options. This makes the
committed source visible according to the chosen repository visibility. Wait for the Android CI
workflow and inspect failures before publishing the beta APK.

Create the version tag on the reviewed release commit and push that tag. Create a prerelease
with `gh release create`, `--verify-tag`, `--prerelease` and `--notes-file`; `--draft` keeps the
release unpublished while its attachments and description are checked. Attach only the signed
release APK, matching public source archive and `SHA256SUMS.txt` from the prepared package.
Do not attach a personal debug APK, signing configuration or device validation artifacts.

The beta notes use relative links for browsing in the source tree. Before copying them into a
GitHub Release description, resolve those links to the selected repository's tagged `blob` URLs.
Check the downloads, notes and checksums once more before publishing the draft. The regular
Actions workflow builds unsigned artifacts and does not need the private release key.

For later versions, increase Android's `versionCode`, update `versionName` and the release-helper
version, update the changelog and tested scope, and retain the original signing identity.
Add each new public release-notes file to `PUBLIC-FILES` and `.gitignore`. Keep prior published
packages intact so users can identify exactly what they installed.

## Beta 0.15.0-beta01 validation

This is an experimental Fold beta, not a general Android compatibility certification.

| Environment | Checked scope |
| --- | --- |
| Galaxy Fold8, Android 17, inner display | Same-signer personal upgrade, actual live Discover and return to Home; all 12 widget bindings and launcher preferences preserved |
| Pixel Fold emulator, Android 17, cover and inner Fold dimensions | 44 focused checks covering setup, customization, photo recovery, native widget gestures, Home return, and retained Discover motion; all passed |
| Fresh Android 15 emulator, signed release | Populated 0.14.7-to-beta upgrade, native Clock setup/binding preservation, cold start and reboot, welcome at larger text size, Android Home selection, large-font Help, shade-access cancellation, and local search/Discover recovery with Google disabled |

The Android 15 emulator's bundled Google app did not supply a Discover feed. The recovery view
and return to Home worked; this environment establishes fallback behavior, not live-feed support.
The physical Fold uses a developer-signed build to preserve its existing installation. The public
signer's optimized APK was exercised separately on the disposable Android 15 emulator.

The source passed 136 local unit tests and lint with no errors (75 warnings). A clean allowlisted
export built independently without the private workspace's SDK configuration or research module.
The GitHub workflow is supplied but has not yet run remotely.

Still needed: another physical Fold/vendor combination, a real SIM-equipped device, and longer
release performance and battery testing. The reference Fold has no SIM, so its unavailable cellular
indicator is expected. Emulator results do not establish physical animation smoothness.
