#!/usr/bin/env python3
"""Refuse to release until this project's own POM carries the metadata Maven Central needs.

Run by `.github/workflows/release.yml` before anything is signed or uploaded.

The subtle one, and the reason this is a parser rather than a few greps:

**The licence can be inherited without anybody choosing it.** This project's parent is
`spring-boot-starter-parent`, which declares Apache-2.0. So the *effective* POM has a
`<licenses>` block whatever this repository does, a check that looks at the effective POM
passes, and the artifact is published to a permanent public index declaring a licence the
owner never agreed to. Reading the project's own POM - and reading it as XML, so a commented
out example is not mistaken for a declaration - is what makes the check mean what it says.

Exit status 0 when the project is releasable, 1 when it is not, with the reason and what to do
about it.
"""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ElementTree

POM_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}"

# What Maven Central requires of a published POM, and what each one is for.
REQUIRED = {
    "name": "the human-readable project name",
    "description": "what the library is",
    "url": "the project's home page",
    "licenses": "the licence the artifact is published under",
    "developers": "who is responsible for it",
    "scm": "where the source lives",
}

LICENCE_FILES = ("LICENSE", "LICENSE.txt", "LICENSE.md", "COPYING")


def local_elements(pom_path: str) -> set[str]:
    """The top-level elements this POM declares itself.

    Parsed rather than searched: an example inside an XML comment is not a declaration, and a
    release that treated it as one would publish metadata nobody wrote.
    """
    root = ElementTree.parse(pom_path).getroot()
    return {child.tag.replace(POM_NAMESPACE, "") for child in root}


def parent_artifact(pom_path: str) -> str | None:
    """The parent this POM inherits from, if any - the source of an accidental licence."""
    root = ElementTree.parse(pom_path).getroot()
    parent = root.find(f"{POM_NAMESPACE}parent")
    if parent is None:
        return None
    artifact = parent.find(f"{POM_NAMESPACE}artifactId")
    return artifact.text if artifact is not None else None


def licence_file(directory: str) -> str | None:
    for name in LICENCE_FILES:
        if os.path.isfile(os.path.join(directory, name)):
            return name
    return None


def check(directory: str = ".") -> list[str]:
    """Every reason this project cannot be published, in the order a person would fix them."""
    problems: list[str] = []
    pom_path = os.path.join(directory, "pom.xml")

    declared = local_elements(pom_path)
    for element, purpose in REQUIRED.items():
        if element not in declared:
            problems.append(f"pom.xml declares no <{element}> - Maven Central requires it for "
                            f"{purpose}.")

    if "licenses" not in declared:
        parent = parent_artifact(pom_path)
        if parent:
            problems.append(
                f"The <licenses> block must be declared here, not inherited. This POM's parent "
                f"({parent}) declares its own licence, so the effective POM appears to have one "
                f"and the artifact would be published under a licence nobody chose.")

    if licence_file(directory) is None:
        problems.append("There is no LICENSE file in the repository root. Maven Central "
                        "publishes the licence as a fact about the artifact, so it has to be a "
                        "real one.")

    return problems


def main() -> int:
    problems = check(os.environ.get("PROJECT_DIR", "."))
    if not problems:
        print("Release metadata is complete.")
        return 0

    for problem in problems:
        print(f"::error::{problem}")
    print("::error::MANUAL CONFIGURATION REQUIRED: choose a licence, add the LICENSE file, and "
          "uncomment the <licenses> block in pom.xml. See RELEASING.md, 'One-time setup'.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
