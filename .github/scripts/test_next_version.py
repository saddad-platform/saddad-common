#!/usr/bin/env python3
"""Tests for the automatic version.

Worth testing carefully rather than trusting: this number is published to a permanent public
index and six services upgrade on the strength of it. Getting a breaking change wrong here means
a major change arriving in those services labelled as a patch.
"""

import sys
import unittest

sys.path.insert(0, __file__.rsplit("/", 1)[0])

from next_version import bump, decide_bump, next_version, should_release  # noqa: E402


class WhatKindOfChange(unittest.TestCase):

    def test_an_ordinary_message_is_a_patch(self):
        # How this repository has actually been written: "Fix", "update", "Auto update".
        for message in ["Fix", "update", "Auto update", "Fix the wallet balance", "tidy up"]:
            self.assertEqual("patch", decide_bump(message), message)

    def test_a_conventional_fix_is_a_patch(self):
        self.assertEqual("patch", decide_bump("fix: the drawer opened on the wrong side"))

    def test_a_feature_is_a_minor(self):
        self.assertEqual("minor", decide_bump("feat: publish the password policy"))

    def test_a_scoped_feature_is_a_minor(self):
        self.assertEqual("minor", decide_bump("feat(otp): add a resend ceiling"))

    def test_an_exclamation_mark_is_a_major(self):
        self.assertEqual("major", decide_bump("feat!: rename the settlement outcome"))

    def test_a_scoped_exclamation_mark_is_a_major(self):
        self.assertEqual("major", decide_bump("refactor(api)!: drop the old envelope"))

    def test_a_breaking_change_footer_is_a_major(self):
        message = "fix: correct the header\n\nBREAKING CHANGE: the field was renamed"
        self.assertEqual("major", decide_bump(message))

    def test_the_strongest_signal_in_the_batch_wins(self):
        # A push carries several commits; one breaking change makes the whole release breaking.
        batch = "fix: a typo\nfeat: a new report\nfeat!: remove the old one"
        self.assertEqual("major", decide_bump(batch))

        batch = "fix: a typo\nfeat: a new report"
        self.assertEqual("minor", decide_bump(batch))


class Counting(unittest.TestCase):

    def test_patch(self):
        self.assertEqual("2.3.5", bump("2.3.4", "patch"))

    def test_minor_resets_the_patch(self):
        self.assertEqual("2.4.0", bump("2.3.4", "minor"))

    def test_major_resets_both(self):
        self.assertEqual("3.0.0", bump("2.3.4", "major"))

    def test_it_refuses_a_tag_it_cannot_count_from(self):
        for tag in ["release-one", "v2.3", "", "latest"]:
            with self.assertRaises(ValueError, msg=tag):
                bump(tag, "patch")


class WhetherToReleaseAtAll(unittest.TestCase):

    def test_nothing_to_release(self):
        released, reason = should_release("")
        self.assertFalse(released)
        self.assertIn("no commits", reason)

    def test_an_author_can_opt_out(self):
        released, reason = should_release("docs: fix a typo [skip release]")
        self.assertFalse(released)
        self.assertIn("skip release", reason)

    def test_the_workflows_own_commit_does_not_start_another_release(self):
        # The loop this prevents: release -> commit -> push -> release -> ...
        released, reason = should_release("chore(release): 1.0.1")
        self.assertFalse(released)
        self.assertIn("release commit", reason)

    def test_a_real_change_alongside_a_release_commit_still_releases(self):
        released, _ = should_release("chore(release): 1.0.1\nfix: a real change")
        self.assertTrue(released)

    def test_ordinary_work_releases(self):
        released, reason = should_release("Fix the wallet balance")
        self.assertTrue(released)
        self.assertEqual("", reason)


class TheVersionItself(unittest.TestCase):

    def test_the_first_release_is_what_the_pom_says(self):
        # A repository sitting at 1.0.0 publishes 1.0.0, not a surprising 1.0.1.
        self.assertEqual("1.0.0", next_version("", "1.0.0", "Fix"))

    def test_the_first_release_drops_a_snapshot_suffix(self):
        self.assertEqual("1.0.0", next_version("", "1.0.0-SNAPSHOT", "Fix"))

    def test_after_the_first_the_tag_is_the_truth(self):
        # Even if the POM has drifted, the tag is what JitPack served, so counting is from there.
        self.assertEqual("1.0.1", next_version("1.0.0", "9.9.9", "Fix"))

    def test_a_feature_after_a_release(self):
        self.assertEqual("1.1.0", next_version("1.0.3", "1.0.3", "feat: add the report"))

    def test_a_breaking_change_after_a_release(self):
        self.assertEqual("2.0.0", next_version("1.4.2", "1.4.2", "feat!: change the envelope"))

    def test_a_nonsense_pom_on_a_first_release_is_refused(self):
        with self.assertRaises(ValueError):
            next_version("", "not-a-version", "Fix")


class TheSequenceOverTime(unittest.TestCase):
    """A run of pushes, to show the numbers a team would actually end up with."""

    def test_a_plausible_history(self):
        history = [
            ("", "1.0.0", "initial", "1.0.0"),
            ("1.0.0", "1.0.0", "Fix", "1.0.1"),
            ("1.0.1", "1.0.1", "update", "1.0.2"),
            ("1.0.2", "1.0.2", "feat: add the corporate report", "1.1.0"),
            ("1.1.0", "1.1.0", "fix: the report period", "1.1.1"),
            ("1.1.1", "1.1.1", "feat!: drop the old outcome names", "2.0.0"),
        ]
        for tag, pom, message, expected in history:
            self.assertEqual(expected, next_version(tag, pom, message),
                             f"{tag or 'none'} + {message!r}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
