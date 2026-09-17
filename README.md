# Visual Routines

Visual Routines is a free and open-source Android app for creating and
following simple everyday routines one step at a time.

The app is local-first and designed for people who may benefit from a calm,
low-distraction view of the current step. It is a general-purpose tool, not a
medical, therapeutic, emergency, productivity, habit-tracking, or remote-care
service.

## Release Status

Version `0.1.0` is the initial upstream release and the only release currently
scheduled. A release exists only when the matching `v0.1.0` source tag is
published; do not infer release status from this README or an untagged commit.

F-Droid is the sole preferred APK channel, but inclusion is an external
decision. Until a verified F-Droid listing is linked here, no preferred APK is
available. This repository does not publish APKs or Android App Bundles, and CI
artifacts are not releases.

## Features

- Create, edit, reorder, and delete routines stored on the device.
- Show one clear step at a time with optional short notes.
- Attach one optional bundled or locally selected image to a step.
- Continue one paused routine after leaving or restarting the app.
- Mark steps done, skip steps, go back, pause, reset, and finish a routine.
- Hide bundled examples or routine-management controls in local settings.

Visible step text remains complete and primary. Images are optional aids.

## Privacy And Data

Visual Routines has no app account, backend, analytics, advertising, tracking,
or Internet permission. Routine text, progress, settings, and selected image
copies stay in app-private storage during normal use.

Cloud backup is disabled. Supported Android device-to-device migration may
transfer a narrowly allowlisted set of app data. The app does not provide its
own export or import feature, so routines and image copies cannot be moved
through a Visual Routines export file. See the [privacy policy](docs/privacy-policy.md).

## Accessibility

The interaction is designed around visible text, predictable navigation,
large-text-safe scrolling, and TalkBack semantics. The final candidate is
validated on a phone, a tablet, Android 6.0 / API 23, font scale 2.0, and
human-listened screen-reader paths before release. Validation supports the
tested behavior and devices; it is not a guarantee for every configuration.

## Build From Source

The project uses Kotlin, Jetpack Compose, and the Gradle Wrapper. Android
Studio is optional. See [BUILDING.md](BUILDING.md) for the command-line toolchain
and validation commands.

## Project Contact

GitHub Issues are the primary public tracker for bugs, proposals, and project
discussion. For a lower-barrier general contact route, email
[`visual-routines@oikumene.works`](mailto:visual-routines@oikumene.works).

The address is monitored on a best-effort basis. It is not a support,
emergency, or security service, and no response time is promised. Email
necessarily shares the sender address and message with the current maintainer;
do not send routine content, images, device identifiers, account details,
credentials, or other personal or sensitive information. Use the private route
in [SECURITY.md](SECURITY.md) for a possible vulnerability.

## Contributing And Continuation

The current individual maintainer has accepted active, best-effort maintenance
of the upstream project and its F-Droid distribution. This does not promise a
support service, response times, a release schedule, or acceptance of every
report or contribution. Changes that improve source clarity, buildability,
accessibility, translations, documentation, or F-Droid metadata are welcome.

Read [CONTRIBUTING.md](CONTRIBUTING.md),
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md), [SECURITY.md](SECURITY.md), and
[MAINTAINERSHIP.md](MAINTAINERSHIP.md) before participating.

A willing successor may propose stewardship. Repository administration and
distribution identity are transferred only through an explicit agreement. If
no transfer is agreed, the GPL permits continued development in a fork, which
may need a new name and application id.

## License And Assets

Application source and documentation are licensed under
`GPL-3.0-or-later`; see [LICENSE](LICENSE). The four bundled bed-linen PNG
assets are dedicated to the public domain under CC0 1.0 as recorded in
[their provenance document](docs/bundled-image-provenance.md).

See [CHANGELOG.md](CHANGELOG.md) for release contents and known limitations.
