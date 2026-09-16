# Contributing

Thank you for considering a contribution to Visual Routines.

The current individual maintainer has accepted active, best-effort maintenance
of the upstream project and its F-Droid distribution. Version `v0.1.0` is the
only release currently scheduled. A contribution may be useful even when it is
not merged, but review, acceptance, response time, and a future release are not
promised.

## Before Opening Work

- Read the product boundary in [README.md](README.md).
- Use repository Issues only when they are enabled. Otherwise, a focused pull
  request may describe the problem it addresses.
- Keep changes small and explain the user-visible reason for them.
- Do not include routine data, personal images, device identifiers, account
  details, credentials, or other personal information in an issue, commit,
  screenshot, log, or test fixture.
- For a possible security vulnerability, follow [SECURITY.md](SECURITY.md)
  instead of opening a public report.

## Product And Accessibility Expectations

Visual Routines stays local-first. Do not add accounts, analytics, advertising,
tracking, backend dependencies, or network permissions without an explicit
project decision.

Visible step text must remain complete without an image, color, or sound.
Interactive changes should preserve large-text scrolling, clear semantics,
stable focus behavior, and predictable phone/tablet layouts. Automated
accessibility checks support but do not replace human screen-reader listening.

Routine text should be short, concrete, calm, and non-judgmental. The project
does not make medical, therapeutic, safety, or productivity claims.

## Validation

Run the command-line matrix in [BUILDING.md](BUILDING.md). Add or update focused
tests when behavior changes. Do not replace screenshot goldens without
reviewing the rendered result and explaining the intended visual change.

Physical-device evidence must identify the exact source commit and affected
path. Do not claim broad device or accessibility support from a single smoke
test.

## Licensing And Assets

By submitting code or documentation, you agree that it may be distributed
under `GPL-3.0-or-later` with the project. State the source and compatible
license of any third-party material.

Image contributions need complete provenance and redistribution rights. Do not
submit private photos, copyrighted material without permission, or generated
assets without their generation and review record.

## Community Continuation

Read [MAINTAINERSHIP.md](MAINTAINERSHIP.md) before proposing stewardship or a
long-term roadmap. A pull request, title, or period of activity does not grant
repository or distribution control implicitly.
