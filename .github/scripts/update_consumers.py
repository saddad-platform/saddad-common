#!/usr/bin/env python3
"""Open a dependency-update pull request on every service that uses saddad-common.

Run by `.github/workflows/update-consumers.yml` after a version has actually been published to
Maven Central.

What it does, and the reasoning behind each rule:

* **It finds the consumers rather than being told them.** The organisation's repositories are
  listed and each one's ``pom.xml`` is read; a repository is a consumer if its POM declares the
  ``saddad-common`` artifact. A hand-maintained list is the thing that is wrong the day
  somebody adds a service and forgets, and that day is invisible until a release quietly skips
  it. ``.github/consumers.yml`` exists to add repositories discovery cannot see and to exclude
  ones it should leave alone - not to replace it.

* **It changes one line.** The version of one dependency. Not the service's own version, not
  its other dependencies, not its source. A service's release is its owner's decision, and an
  automated pull request that also bumped the service version would be making that decision for
  them.

* **It is safe to run twice.** Running it again for the same version finds the branch and the
  pull request it opened last time and leaves them alone, rather than opening a second one.

* **One consumer's failure is that consumer's failure.** A repository that cannot be reached is
  reported and the rest continue: the library has already been published, and nothing about an
  unreachable service makes that untrue.

Nothing here logs the token, and no output contains a credential.
"""

from __future__ import annotations

import base64
import json
import os
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any

API = "https://api.github.com"
ARTIFACT_ID = "saddad-common"
GROUP_ID = "io.github.saddad-platform"

# The branch a pull request is opened from. Version-specific, which is what makes a second run
# for the same version find the first run's work instead of duplicating it.
BRANCH_PREFIX = "chore/update-saddad-common-"


# --------------------------------------------------------------------------------------- POM

@dataclass
class PomChange:
    """The result of editing one POM: what changed, from what, and how it was declared."""

    text: str
    previous_version: str
    style: str  # "inline" or "property"
    property_name: str | None = None

    @property
    def changed(self) -> bool:
        return self.style != "unchanged"


class DependencyNotFound(Exception):
    """The POM does not declare the library at all."""


def find_dependency_version(pom: str, artifact_id: str = ARTIFACT_ID) -> tuple[str, str, str | None]:
    """Return ``(version, style, property_name)`` for the library as this POM declares it.

    ``style`` is ``"inline"`` when the version is written in the dependency block and
    ``"property"`` when the block refers to a Maven property, because the two are edited in
    different places and a service that uses a property has chosen that on purpose.
    """
    block = _dependency_block(pom, artifact_id)
    version_match = re.search(r"<version>\s*(.*?)\s*</version>", block, re.DOTALL)
    if not version_match:
        # Inherited from a parent or a BOM. Nothing in this file to change, and rewriting the
        # service's dependency management to introduce a version would be restructuring it.
        raise DependencyNotFound(
            f"{artifact_id} is declared without a version - it is managed elsewhere "
            "(a parent POM or an imported BOM), so this file has nothing to update"
        )

    raw = version_match.group(1)
    property_match = re.fullmatch(r"\$\{([^}]+)\}", raw)
    if property_match:
        name = property_match.group(1)
        resolved = re.search(rf"<{re.escape(name)}>\s*(.*?)\s*</{re.escape(name)}>", pom, re.DOTALL)
        if not resolved:
            raise DependencyNotFound(
                f"{artifact_id} uses the property ${{{name}}}, which this POM does not define"
            )
        return resolved.group(1), "property", name

    return raw, "inline", None


def update_pom(pom: str, new_version: str, artifact_id: str = ARTIFACT_ID) -> PomChange:
    """Set the library's version in this POM, respecting how the service declares it.

    Only the version is touched. The surrounding formatting, comments and every other
    dependency are left exactly as they were, so the pull request's diff is one line and a
    reviewer can see the whole change at a glance.
    """
    current, style, property_name = find_dependency_version(pom, artifact_id)
    if current == new_version:
        return PomChange(text=pom, previous_version=current, style="unchanged",
                         property_name=property_name)

    if style == "property":
        pattern = rf"(<{re.escape(property_name)}>)\s*{re.escape(current)}\s*(</{re.escape(property_name)}>)"
        updated, count = re.subn(pattern, rf"\g<1>{new_version}\g<2>", pom)
        if count == 0:
            raise DependencyNotFound(f"could not rewrite the property {property_name}")
        return PomChange(text=updated, previous_version=current, style="property",
                         property_name=property_name)

    # Inline: rewrite the version inside that dependency block only. Replacing the version
    # string across the whole file would catch any other dependency that happens to share it.
    block = _dependency_block(pom, artifact_id)
    new_block = re.sub(r"(<version>)\s*" + re.escape(current) + r"\s*(</version>)",
                       rf"\g<1>{new_version}\g<2>", block, count=1)
    if new_block == block:
        raise DependencyNotFound("could not rewrite the inline version")
    return PomChange(text=pom.replace(block, new_block, 1), previous_version=current,
                     style="inline")


def declared_group_id(pom: str, artifact_id: str = ARTIFACT_ID) -> str | None:
    """The group id the consumer currently uses for the library, if it states one."""
    try:
        block = _dependency_block(pom, artifact_id)
    except DependencyNotFound:
        return None
    match = re.search(r"<groupId>\s*(.*?)\s*</groupId>", block, re.DOTALL)
    return match.group(1) if match else None


def _dependency_block(pom: str, artifact_id: str) -> str:
    """The ``<dependency>`` element that declares this artifact."""
    for match in re.finditer(r"<dependency>.*?</dependency>", pom, re.DOTALL):
        block = match.group(0)
        if re.search(rf"<artifactId>\s*{re.escape(artifact_id)}\s*</artifactId>", block):
            return block
    raise DependencyNotFound(f"no dependency on {artifact_id}")


def is_major_upgrade(previous: str, new: str) -> bool:
    """Whether this crosses a major version, which a reviewer must be told about."""
    def major(version: str) -> str:
        return re.split(r"[.\-]", version.strip())[0]
    return major(previous) != major(new) and major(previous).isdigit() and major(new).isdigit()


# ------------------------------------------------------------------------------------ GitHub

class GitHub:
    """The few GitHub REST calls this needs, with the token kept out of everything it prints."""

    def __init__(self, token: str) -> None:
        self._token = token

    def _request(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        url = path if path.startswith("http") else f"{API}{path}"
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(url, data=data, method=method)
        request.add_header("Authorization", f"Bearer {self._token}")
        request.add_header("Accept", "application/vnd.github+json")
        request.add_header("X-GitHub-Api-Version", "2022-11-28")
        if data:
            request.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(request) as response:
            payload = response.read()
            return json.loads(payload) if payload else None

    def get(self, path: str) -> Any:
        return self._request("GET", path)

    def post(self, path: str, body: dict[str, Any]) -> Any:
        return self._request("POST", path, body)

    def put(self, path: str, body: dict[str, Any]) -> Any:
        return self._request("PUT", path, body)

    def paginate(self, path: str) -> list[Any]:
        results: list[Any] = []
        page = 1
        while True:
            batch = self.get(f"{path}{'&' if '?' in path else '?'}per_page=100&page={page}")
            if not batch:
                break
            results.extend(batch)
            if len(batch) < 100:
                break
            page += 1
        return results


# ------------------------------------------------------------------------------------ registry

@dataclass
class Registry:
    """``.github/consumers.yml``: what to add to discovery, and what to keep out of it."""

    discover: bool = True
    include: list[str] = field(default_factory=list)
    exclude: list[str] = field(default_factory=list)


def load_registry(path: str) -> Registry:
    """Read the registry, tolerating its absence - discovery alone is a valid configuration."""
    if not os.path.exists(path):
        return Registry()
    try:
        import yaml  # Installed by the workflow; absence is not worth failing a release for.
    except ImportError:
        print("note: PyYAML is not installed, so consumers.yml was not read", file=sys.stderr)
        return Registry()

    with open(path) as handle:
        data = yaml.safe_load(handle) or {}

    return Registry(
        discover=bool(data.get("discover", True)),
        include=[str(entry) for entry in (data.get("include") or [])],
        exclude=[str(entry) for entry in (data.get("exclude") or [])],
    )


# -------------------------------------------------------------------------------------- main

@dataclass
class Outcome:
    repository: str
    status: str  # created, updated, skipped, failed
    detail: str
    previous_version: str | None = None
    location: str | None = None
    branch: str | None = None
    url: str | None = None


def discover_repositories(github: GitHub, organization: str, registry: Registry) -> list[str]:
    """Every repository to examine: what the organisation has, plus and minus the registry."""
    names: list[str] = []
    if registry.discover:
        for repo in github.paginate(f"/orgs/{organization}/repos?type=all"):
            if repo.get("archived") or repo.get("disabled"):
                continue
            names.append(repo["full_name"])

    for extra in registry.include:
        if extra not in names:
            names.append(extra)

    excluded = set(registry.exclude)
    return [name for name in names if name not in excluded]


def update_consumer(github: GitHub, repository: str, version: str, release_url: str,
                    dry_run: bool) -> Outcome:
    """Bring one consumer to the new version, or explain why it was left alone."""
    info = github.get(f"/repos/{repository}")
    base_branch = info["default_branch"]

    pom_file = github.get(f"/repos/{repository}/contents/pom.xml?ref={base_branch}")
    pom = base64.b64decode(pom_file["content"]).decode()

    change = update_pom(pom, version)
    location = "pom.xml property" if change.style == "property" else "pom.xml dependency"
    if change.style == "unchanged":
        return Outcome(repository, "skipped", f"already on {version}",
                       previous_version=change.previous_version, location=location)

    group = declared_group_id(pom)
    notes: list[str] = []
    if group and group != GROUP_ID:
        # Flagged, never rewritten: moving a service to different coordinates is a change of
        # where its dependency comes from, which is a decision, not a version bump.
        notes.append(
            f"This service declares the library under `{group}`, but it is published as "
            f"`{GROUP_ID}`. The group id has been left alone - update it deliberately."
        )
    if is_major_upgrade(change.previous_version, version):
        notes.append(
            "**Potential breaking change: major version upgrade.** Review the release notes "
            "before merging; no application source has been modified."
        )

    branch = f"{BRANCH_PREFIX}{version}"
    if dry_run:
        return Outcome(repository, "would-create",
                       f"{change.previous_version} -> {version} on {branch}",
                       previous_version=change.previous_version, location=location, branch=branch)

    # Create the branch, or accept the one a previous run created.
    base_sha = github.get(f"/repos/{repository}/git/ref/heads/{base_branch}")["object"]["sha"]
    try:
        github.post(f"/repos/{repository}/git/refs",
                    {"ref": f"refs/heads/{branch}", "sha": base_sha})
    except urllib.error.HTTPError as error:
        if error.code != 422:  # 422 is "already exists", which is the idempotent case.
            raise

    # Re-read the file on the branch: a previous run may already have written it, and the blob
    # sha of the branch's copy is what the contents API requires to accept an update.
    branch_file = github.get(f"/repos/{repository}/contents/pom.xml?ref={branch}")
    branch_pom = base64.b64decode(branch_file["content"]).decode()
    branch_change = update_pom(branch_pom, version)

    message = f"chore(deps): upgrade {ARTIFACT_ID} to {version}"
    if branch_change.changed:
        github.put(f"/repos/{repository}/contents/pom.xml", {
            "message": message,
            "content": base64.b64encode(branch_change.text.encode()).decode(),
            "sha": branch_file["sha"],
            "branch": branch,
        })

    owner = repository.split("/")[0]
    existing = github.get(f"/repos/{repository}/pulls?head={owner}:{branch}&state=open")
    if existing:
        return Outcome(repository, "updated", "reused the pull request already open",
                       previous_version=change.previous_version, location=location,
                       branch=branch, url=existing[0]["html_url"])

    pull = github.post(f"/repos/{repository}/pulls", {
        "title": message,
        "head": branch,
        "base": base_branch,
        "body": pull_request_body(change.previous_version, version, location, release_url, notes),
    })
    return Outcome(repository, "created", f"{change.previous_version} -> {version}",
                   previous_version=change.previous_version, location=location,
                   branch=branch, url=pull["html_url"])


def pull_request_body(previous: str, version: str, location: str, release_url: str,
                      notes: list[str]) -> str:
    """What a reviewer needs in order to decide, and nothing they have to go and look up."""
    body = [
        "## Dependency update",
        "",
        "Updated:",
        "",
        f"`{GROUP_ID}:{ARTIFACT_ID}`",
        "",
        "From:",
        f"`{previous}`",
        "",
        "To:",
        f"`{version}`",
        "",
        f"Declared in: {location}",
        "",
        "## Validation",
        "",
        "- Maven dependency updated",
        "- Existing service version unchanged",
        "- Existing project structure unchanged",
        "- CI will validate the change",
        "",
        "## Reason",
        "",
        f"Automated dependency update following the publication of {ARTIFACT_ID} {version}.",
        f"Release: {release_url}",
    ]
    if notes:
        body += ["", "## Please note", ""] + [f"- {note}" for note in notes]
    body += ["", "---", "",
             "Opened automatically. It is not merged automatically: the service owner reviews "
             "it, its own CI runs, and the service is released on its own schedule."]
    return "\n".join(body)


def main() -> int:
    token = os.environ.get("GITHUB_TOKEN", "")
    organization = os.environ.get("ORGANIZATION", "")
    version = os.environ.get("LIBRARY_VERSION", "")
    dry_run = os.environ.get("DRY_RUN", "false").lower() == "true"
    release_url = os.environ.get("RELEASE_URL", "")

    if not token or not organization or not version:
        # Never says which value was missing beyond its name, and never prints any of them.
        print("::error::GITHUB_TOKEN, ORGANIZATION and LIBRARY_VERSION must all be set")
        return 1

    github = GitHub(token)
    registry = load_registry(os.path.join(".github", "consumers.yml"))

    try:
        candidates = discover_repositories(github, organization, registry)
    except urllib.error.HTTPError as error:
        print(f"::error::could not list the repositories of {organization}: HTTP {error.code}")
        return 1

    print(f"Examining {len(candidates)} repositor{'y' if len(candidates) == 1 else 'ies'} "
          f"for a dependency on {ARTIFACT_ID}")

    outcomes: list[Outcome] = []
    for repository in candidates:
        try:
            outcomes.append(update_consumer(github, repository, version, release_url, dry_run))
        except DependencyNotFound as absent:
            # Not a consumer, or a consumer whose version lives somewhere this must not edit.
            print(f"  {repository}: {absent}")
        except urllib.error.HTTPError as error:
            if error.code == 404:
                print(f"  {repository}: no pom.xml, or not visible to this credential")
                continue
            outcomes.append(Outcome(repository, "failed", f"HTTP {error.code} {error.reason}"))
        except Exception as failure:  # noqa: BLE001 - one consumer must never stop the rest
            outcomes.append(Outcome(repository, "failed", str(failure)))

    report(outcomes, version, dry_run)
    # Deliberately 0 even when a consumer failed: the library is published, and the pull
    # requests that did not open can be retried on their own from the Actions tab.
    return 0


def report(outcomes: list[Outcome], version: str, dry_run: bool) -> None:
    """Say what happened, in the terminal and in the job summary."""
    buckets = {status: [o for o in outcomes if o.status == status]
               for status in ("created", "updated", "would-create", "skipped", "failed")}

    lines = [f"## Consumer dependency updates: {ARTIFACT_ID} {version}", ""]
    if dry_run:
        lines += ["**Dry run** - nothing was branched, committed or opened.", ""]

    titles = {
        "created": "Pull requests opened",
        "updated": "Pull requests already open, reused",
        "would-create": "Would open a pull request",
        "skipped": "Skipped",
        "failed": "Failed",
    }
    for status, title in titles.items():
        entries = buckets[status]
        if not entries:
            continue
        lines += [f"### {title}", ""]
        for outcome in entries:
            link = f" - [pull request]({outcome.url})" if outcome.url else ""
            lines.append(f"- `{outcome.repository}` - {outcome.detail}{link}")
        lines.append("")

    if not outcomes:
        lines += ["No repository in the organisation declares a dependency on "
                  f"`{ARTIFACT_ID}` with a version this can update.", ""]

    text = "\n".join(lines)
    print(text)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as handle:
            handle.write(text + "\n")

    if buckets["failed"]:
        # Visible in the run without failing it: the release itself succeeded.
        for outcome in buckets["failed"]:
            print(f"::warning::{outcome.repository} was not updated: {outcome.detail}")


if __name__ == "__main__":
    sys.exit(main())
