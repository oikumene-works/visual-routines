# Changelog

All notable user-visible changes are recorded here.

## 0.3.0 - Unreleased

### Added

- Choose System, Light or Dark appearance, with Standard or Black (AMOLED)
  styling for dark appearance. Both choices are stored on the device.
- Keep the selected dark style while using Light; System applies it when the
  device switches to dark appearance.

### Fixed

- Match Android's launch screen to the selected appearance on Android 12 and
  later. On older versions, wait for the correctly colored app window instead
  of showing a mismatched starting preview.
- Keep the editor's active writing line above the keyboard, including short
  landscape layouts with large text.

- Keep a new routine's identity when restoring its editor after an uncertain
  save, so retrying the save updates that routine instead of creating a duplicate.
- Preserve stored image copies when recovery is uncertain, including when the
  routine catalog is missing and new routines are later saved. Normal successful
  removal of an image's last saved reference still permits deletion after editing
  finishes. Uncertain copies may remain until app storage is cleared or the app
  is uninstalled.
- Ignore wrongly typed saved progress during startup so intact routines remain
  accessible, without clearing the stored progress snapshot.

This is an unpublished candidate; these changes are not part of released
version 0.2.0. Codex implemented the changes and ran local and device checks,
including synthetic recovery cases. The operator selected the Appearance design
and confirmed the focused TalkBack listening checks on phone and tablet.
Validation covers the recorded cases, not every device or the frequency of
exceptional recovery conditions. A new F-Droid binary has not been validated.

## 0.2.0 - 2026-10-30

### Added

- Create an independent, editable personal routine from a bundled example.
  The original example and its progress stay unchanged.
- Send feedback through your email app or copy the project contact address
  from Settings.

### Unchanged Limitations

- Routine and image export/import remain unavailable.
- Feedback is handled on a best-effort basis; replies are not guaranteed.

This candidate was implemented and tested by Codex with operator-directed
wording and human TalkBack listening checks. Automated checks and those bounded
listening checks do not establish usability for every user or device.

## 0.1.0 - 2026-09-17

### Added

- Local creation, editing, ordering, and deletion of routines.
- One-step-at-a-time routine execution with previous, done, skip, pause,
  continue, reset, and completion behavior.
- Stable local routine and step identities with protected JSON migration.
- Optional notes and one optional bundled or locally selected image per step.
- Four CC0 bed-linen instructional images with recorded provenance.
- Local settings for bundled examples and routine-management visibility.
- Large-text-safe layouts, TalkBack semantics, and phone/tablet adaptation.
- Explicit no-cloud-backup policy with narrowly allowlisted Android
  device-to-device migration.

### Known Limitations

- The app does not provide routine or image export/import.
- There is no account, synchronization, remote editing, timer, reminder,
  text-to-speech feature, in-app camera capture, or completion history.
- Images are optional aids; some bundled routine steps have no image.
- F-Droid availability is not guaranteed until its independent review, build,
  and publication complete.
