#!/usr/bin/env python3
"""Tests for the release metadata check.

The case that matters most is the last one: a POM that declares no licence of its own but
inherits one from spring-boot-starter-parent must be refused. A check that passed there would
publish the library to a permanent public index under Apache-2.0 because of its build parent,
which is not a licensing decision anybody made.
"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, __file__.rsplit("/", 1)[0])

from check_release_metadata import check, licence_file, local_elements  # noqa: E402

COMPLETE = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.3</version>
    </parent>
    <groupId>io.github.saddad-platform</groupId>
    <artifactId>saddad-common</artifactId>
    <version>1.0.0</version>
    <name>SADAD Platform :: Common</name>
    <description>Shared technical library.</description>
    <url>https://github.com/saddad-platform/saddad-common</url>
    <licenses>
        <license><name>Apache License, Version 2.0</name></license>
    </licenses>
    <developers>
        <developer><name>SADAD Platform</name></developer>
    </developers>
    <scm><url>https://github.com/saddad-platform/saddad-common</url></scm>
</project>
"""

# The same POM with the licence commented out, which is where this repository stands today.
INHERITED_LICENCE = COMPLETE.replace(
    """    <licenses>
        <license><name>Apache License, Version 2.0</name></license>
    </licenses>""",
    """    <!--
        <licenses>
            <license><name>Apache License, Version 2.0</name></license>
        </licenses>
    -->""")


class Workspace:
    """A throwaway directory holding a POM and optionally a LICENSE."""

    def __init__(self, pom: str, licence: bool):
        self.directory = tempfile.mkdtemp()
        with open(os.path.join(self.directory, "pom.xml"), "w") as handle:
            handle.write(pom)
        if licence:
            with open(os.path.join(self.directory, "LICENSE"), "w") as handle:
                handle.write("Apache License, Version 2.0\n")


class MetadataCheck(unittest.TestCase):

    def test_a_complete_project_is_releasable(self):
        workspace = Workspace(COMPLETE, licence=True)
        self.assertEqual([], check(workspace.directory))

    def test_a_commented_out_licence_is_not_a_licence(self):
        # The heart of it: XML comments are not declarations, and a grep would disagree.
        workspace = Workspace(INHERITED_LICENCE, licence=True)
        problems = check(workspace.directory)
        self.assertTrue(any("<licenses>" in problem for problem in problems), problems)

    def test_an_inherited_licence_is_named_as_the_trap_it_is(self):
        workspace = Workspace(INHERITED_LICENCE, licence=True)
        problems = " ".join(check(workspace.directory))
        self.assertIn("spring-boot-starter-parent", problems)
        self.assertIn("nobody chose", problems)

    def test_a_missing_licence_file_is_refused(self):
        workspace = Workspace(COMPLETE, licence=False)
        problems = check(workspace.directory)
        self.assertTrue(any("LICENSE file" in problem for problem in problems), problems)

    def test_every_required_element_is_checked(self):
        for element in ("name", "description", "url", "developers", "scm"):
            stripped = COMPLETE.replace(f"<{element}>", "<removed>").replace(
                f"</{element}>", "</removed>")
            workspace = Workspace(stripped, licence=True)
            problems = " ".join(check(workspace.directory))
            self.assertIn(f"<{element}>", problems, f"{element} was not checked")

    def test_it_reads_the_projects_own_elements(self):
        workspace = Workspace(COMPLETE, licence=True)
        elements = local_elements(os.path.join(workspace.directory, "pom.xml"))
        self.assertIn("licenses", elements)
        self.assertIn("scm", elements)

    def test_it_recognises_the_usual_licence_filenames(self):
        for name in ("LICENSE", "LICENSE.txt", "COPYING"):
            directory = tempfile.mkdtemp()
            with open(os.path.join(directory, name), "w") as handle:
                handle.write("x")
            self.assertEqual(name, licence_file(directory))


class ThisRepository(unittest.TestCase):

    def test_the_real_pom_is_refused_while_the_licence_is_undecided(self):
        # Not a hypothetical: this is the state of the repository, and the check exists to stop
        # a release in exactly this state.
        root = os.path.abspath(os.path.join(__file__, "..", "..", ".."))
        problems = check(root)
        self.assertTrue(problems, "the real POM should not yet be releasable")
        self.assertTrue(any("LICENSE" in problem or "licenses" in problem
                            for problem in problems), problems)


if __name__ == "__main__":
    unittest.main(verbosity=2)
