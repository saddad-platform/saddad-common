#!/usr/bin/env python3
"""Tests for the parts of the consumer update that decide what a POM should say.

Run with ``python3 .github/scripts/test_update_consumers.py``. No dependencies and no network:
everything here is the pure half of the automation, which is the half that can silently do the
wrong thing to somebody else's repository. The GitHub calls are deliberately not mocked - what
matters is that the edit is right, and an assertion about a mock would only be an assertion
about the mock.
"""

import sys
import unittest

sys.path.insert(0, __file__.rsplit("/", 1)[0])

from update_consumers import (  # noqa: E402
    DependencyNotFound,
    declared_group_id,
    find_dependency_version,
    is_major_upgrade,
    pull_request_body,
    update_pom,
)

INLINE = """<project>
    <artifactId>saddad-auth</artifactId>
    <version>3.8.2</version>
    <dependencies>
        <dependency>
            <groupId>com.github.saddad-platform</groupId>
            <artifactId>saddad-common</artifactId>
            <version>1.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</project>
"""

PROPERTY = """<project>
    <version>4.2.7</version>
    <properties>
        <saddad-common.version>1.0.0</saddad-common.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>com.github.saddad-platform</groupId>
            <artifactId>saddad-common</artifactId>
            <version>${saddad-common.version}</version>
        </dependency>
    </dependencies>
</project>
"""

MANAGED = """<project>
    <dependencies>
        <dependency>
            <groupId>com.github.saddad-platform</groupId>
            <artifactId>saddad-common</artifactId>
        </dependency>
    </dependencies>
</project>
"""


class InlineVersion(unittest.TestCase):

    def test_it_reads_the_version(self):
        version, style, prop = find_dependency_version(INLINE)
        self.assertEqual(("1.0.0", "inline", None), (version, style, prop))

    def test_it_updates_only_the_library(self):
        change = update_pom(INLINE, "1.0.1")
        self.assertEqual("1.0.0", change.previous_version)
        self.assertIn("<artifactId>saddad-common</artifactId>\n            <version>1.0.1</version>",
                      change.text)
        # The unrelated dependency that happened to share the version string is untouched -
        # the reason the edit is scoped to the dependency block rather than the file.
        self.assertIn("<artifactId>postgresql</artifactId>\n            <version>1.0.0</version>",
                      change.text)

    def test_it_leaves_the_service_version_alone(self):
        change = update_pom(INLINE, "1.0.1")
        self.assertIn("<artifactId>saddad-auth</artifactId>\n    <version>3.8.2</version>",
                      change.text)

    def test_the_diff_is_one_line(self):
        change = update_pom(INLINE, "1.0.1")
        before, after = INLINE.splitlines(), change.text.splitlines()
        differing = [i for i, (a, b) in enumerate(zip(before, after)) if a != b]
        self.assertEqual(1, len(differing))


class PropertyVersion(unittest.TestCase):

    def test_it_reads_through_the_property(self):
        version, style, prop = find_dependency_version(PROPERTY)
        self.assertEqual(("1.0.0", "property", "saddad-common.version"), (version, style, prop))

    def test_it_updates_the_property_not_the_dependency(self):
        change = update_pom(PROPERTY, "1.1.0")
        self.assertEqual("property", change.style)
        self.assertIn("<saddad-common.version>1.1.0</saddad-common.version>", change.text)
        # The declaration keeps pointing at the property: the service chose that style.
        self.assertIn("<version>${saddad-common.version}</version>", change.text)

    def test_it_leaves_the_service_version_alone(self):
        change = update_pom(PROPERTY, "1.1.0")
        self.assertIn("<version>4.2.7</version>", change.text)


class NothingToDo(unittest.TestCase):

    def test_a_consumer_already_on_the_version_is_left_alone(self):
        change = update_pom(INLINE, "1.0.0")
        self.assertEqual("unchanged", change.style)
        self.assertFalse(change.changed)
        self.assertEqual(INLINE, change.text)

    def test_a_managed_version_is_refused_rather_than_invented(self):
        with self.assertRaises(DependencyNotFound):
            update_pom(MANAGED, "1.0.1")

    def test_a_pom_without_the_library_is_not_a_consumer(self):
        with self.assertRaises(DependencyNotFound):
            update_pom("<project><dependencies></dependencies></project>", "1.0.1")


class GroupIdAndSeverity(unittest.TestCase):

    def test_it_reads_the_declared_group(self):
        self.assertEqual("com.github.saddad-platform", declared_group_id(INLINE))

    def test_a_major_upgrade_is_recognised(self):
        self.assertTrue(is_major_upgrade("1.4.2", "2.0.0"))

    def test_a_minor_upgrade_is_not(self):
        self.assertFalse(is_major_upgrade("1.0.0", "1.1.0"))
        self.assertFalse(is_major_upgrade("1.0.0", "1.0.1"))


class Body(unittest.TestCase):

    def test_it_states_both_versions_and_the_release(self):
        body = pull_request_body("1.0.0", "1.0.1", "pom.xml dependency",
                                 "https://example.invalid/releases/tag/v1.0.1", [])
        self.assertIn("`1.0.0`", body)
        self.assertIn("`1.0.1`", body)
        self.assertIn("Existing service version unchanged", body)
        self.assertIn("https://example.invalid/releases/tag/v1.0.1", body)

    def test_a_major_upgrade_is_flagged_to_the_reviewer(self):
        body = pull_request_body("1.9.0", "2.0.0", "pom.xml dependency", "",
                                 ["**Potential breaking change: major version upgrade.**"])
        self.assertIn("Potential breaking change", body)

    def test_it_never_promises_an_automatic_merge(self):
        body = pull_request_body("1.0.0", "1.0.1", "pom.xml dependency", "", [])
        self.assertIn("not merged automatically", body)


if __name__ == "__main__":
    unittest.main(verbosity=2)
