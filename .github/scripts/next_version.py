#!/usr/bin/env python3
"""Work out the next version from what has actually changed since the last release.

Used by `.github/workflows/release.yml`, which releases on every push to `main`. Nobody types a
version number: the commit messages decide it, which is the only way an automatic version can
mean anything. A number that always went up by one would say nothing about whether an upgrade is
safe, and six services upgrade automatically on the strength of it.

The rule, in the order it is applied:

    a commit with `!` after its type, or a `BREAKING CHANGE:` line   ->  major   2.3.4 -> 3.0.0
    a commit starting `feat:` or `feat(scope):`                      ->  minor   2.3.4 -> 2.4.0
    anything else                                                    ->  patch   2.3.4 -> 2.3.5

Plain messages like "Fix the wallet balance" are patches, which is the safe default and matches
how this repository has been written so far. To publish a minor or a major, say so in the commit
message - `feat: add the settlement report` - and nothing else changes about how you work.

Two things stop a release happening at all:

    [skip release] anywhere in the message   ->  nothing is published
    a `chore(release):` commit               ->  nothing is published

The second is what stops the release commit this workflow makes from triggering another release
for ever.

Reads the state it needs from the environment so it can be tested without a git repository:

    LATEST_TAG   the most recent release tag, or empty for the first release
    POM_VERSION  the version in pom.xml, used only for the first release
    COMMITS      the commit messages since that tag, one per line
"""

from __future__ import annotations

import os
import re
import sys

BREAKING = re.compile(r"^(?P<type>[a-zA-Z]+)(?P<scope>\([^)]*\))?!:", re.MULTILINE)
BREAKING_FOOTER = re.compile(r"^BREAKING[ -]CHANGE:", re.MULTILINE)
FEATURE = re.compile(r"^feat(\([^)]*\))?:", re.MULTILINE | re.IGNORECASE)
RELEASE_COMMIT = re.compile(r"^chore\(release\):", re.MULTILINE | re.IGNORECASE)
SKIP = re.compile(r"\[skip release\]", re.IGNORECASE)

SEMVER = re.compile(r"^(\d+)\.(\d+)\.(\d+)")


def decide_bump(commits: str) -> str:
    """Which part of the version these commits move: major, minor or patch."""
    if BREAKING.search(commits) or BREAKING_FOOTER.search(commits):
        return "major"
    if FEATURE.search(commits):
        return "minor"
    return "patch"


def bump(version: str, part: str) -> str:
    """The next version after ``version``, moving ``part``."""
    match = SEMVER.match(version.strip())
    if not match:
        raise ValueError(f"{version!r} is not a version this can count from")
    major, minor, patch = (int(g) for g in match.groups())

    if part == "major":
        return f"{major + 1}.0.0"
    if part == "minor":
        return f"{major}.{minor + 1}.0"
    return f"{major}.{minor}.{patch + 1}"


def should_release(commits: str) -> tuple[bool, str]:
    """Whether to release these commits at all, and why not when the answer is no."""
    if not commits.strip():
        return False, "there are no commits since the last release"
    if SKIP.search(commits):
        return False, "a commit asked for this to be skipped with [skip release]"
    # Every commit is a release commit: this push is the workflow's own bump coming back round.
    lines = [line for line in commits.strip().splitlines() if line.strip()]
    if lines and all(RELEASE_COMMIT.match(line) for line in lines):
        return False, "the only change is the release commit this workflow made"
    return True, ""


def next_version(latest_tag: str, pom_version: str, commits: str) -> str:
    """The version to publish.

    The first release is whatever the POM already says, so a repository that has been sitting at
    1.0.0 publishes 1.0.0 rather than mysteriously starting at 1.0.1. After that the tag is the
    truth and the POM follows it, because the tag is what JitPack serves.
    """
    if not latest_tag.strip():
        version = pom_version.strip().replace("-SNAPSHOT", "")
        if not SEMVER.match(version):
            raise ValueError(f"the POM version {pom_version!r} is not a version number")
        return version
    return bump(latest_tag, decide_bump(commits))


def main() -> int:
    latest_tag = os.environ.get("LATEST_TAG", "")
    pom_version = os.environ.get("POM_VERSION", "")
    commits = os.environ.get("COMMITS", "")

    release, reason = should_release(commits)
    output = os.environ.get("GITHUB_OUTPUT")

    def emit(key: str, value: str) -> None:
        print(f"{key}={value}")
        if output:
            with open(output, "a") as handle:
                handle.write(f"{key}={value}\n")

    if not release:
        print(f"Not releasing: {reason}")
        emit("release", "false")
        emit("reason", reason)
        return 0

    try:
        version = next_version(latest_tag, pom_version, commits)
    except ValueError as problem:
        print(f"::error::{problem}")
        return 1

    part = "first" if not latest_tag.strip() else decide_bump(commits)
    print(f"Last release: {latest_tag or '(none)'}")
    print(f"Bump: {part}")
    print(f"Next version: {version}")
    emit("release", "true")
    emit("version", version)
    emit("bump", part)
    return 0


if __name__ == "__main__":
    sys.exit(main())
