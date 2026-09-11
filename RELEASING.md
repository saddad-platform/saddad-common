# Releasing saddad-common

A release publishes an immutable version to Maven Central and opens a dependency-update pull
request on every service that uses it. This is the whole process, and the one-time setup it
depends on.

## The one rule

**A published version is permanent.** Maven Central does not allow a version to be replaced,
deleted or re-uploaded with different contents. If `1.0.1` is wrong, the fix is `1.0.2`. The
release workflow checks Maven Central before it uploads anything and refuses to continue if the
version is already there, but the rule is worth knowing before you reach it.

## Cutting a release

1. **Merge everything** you want in the release to `main`. CI builds and tests every push, so
   `main` is already known to build.

2. **Choose the version**, following semantic versioning. This is a judgement about the change,
   so nothing automates it:

   | Change | Example |
   |---|---|
   | Bug fix, internal change, nothing a caller can see | `1.0.0` → `1.0.1` |
   | New API, new optional behaviour, nothing removed | `1.0.1` → `1.1.0` |
   | A public API changed or was removed, or behaviour changed incompatibly | `1.1.0` → `2.0.0` |

3. **Set that version in `pom.xml`** and commit it to `main`:

   ```bash
   mvn versions:set -DnewVersion=1.0.1 -DgenerateBackupPoms=false
   git commit -am "chore(release): 1.0.1"
   git push
   ```

   The POM holds the released version rather than a `-SNAPSHOT`. The workflow compares it
   against the tag and refuses to publish if they disagree, so what reaches Maven Central can
   only be the version the tag names.

4. **Publish a GitHub Release** on that commit, tagged `v1.0.1`. The `v` belongs to the tag; the
   Maven version never has it.

5. **Watch the run.** The Release workflow, in order:

   - checks the tag and the POM version agree, and that it is not a snapshot;
   - checks the version is not already on Maven Central;
   - checks the licence and the other metadata Central requires are present;
   - checks all four publishing secrets exist;
   - runs the tests, and stops here if any fail;
   - builds the jar, the sources jar and the Javadoc jar, and signs all three;
   - checks every artifact and every signature was produced;
   - uploads to the Central Portal and waits until Central reports it published;
   - opens the consumer pull requests.

6. **Review the consumer pull requests.** One per service, each changing one line. They are not
   merged automatically, and merging one does not release that service.

### Rehearsing

Run the Release workflow by hand from the Actions tab with an existing tag and **dry run**
turned on. Everything above happens except the upload and the pull requests.

### If something fails after publication

The version is published; do not try again with the same number. Re-run **Update consuming
services** on its own from the Actions tab with the version you published. It is safe to run
repeatedly: it reuses the branch and the pull request it opened before rather than creating
more.

## One-time setup

None of this is in the repository, and none of it can be done from here. Until it is done, the
release workflow stops with the reason rather than failing somewhere confusing.

### 1. Choose a licence — REQUIRED, NOT DONE

Maven Central rejects a POM with no licence. This repository has no LICENSE file, no licence
headers and no statement of licensing anywhere, so there is nothing to infer the intent from and
it has deliberately been left undone rather than guessed at.

1. Decide the licence. For a library intended for public consumption this is usually Apache-2.0
   or MIT; it is a legal decision for the owner, not a technical one.
2. Add the licence text as `LICENSE` in the repository root.
3. Uncomment the `<licenses>` block in `pom.xml` and make it match.

### 2. Claim the Maven Central namespace — REQUIRED, STATUS UNKNOWN

The artifact publishes under `io.github.saddad-platform`. Maven Central grants that namespace to
whoever can prove they control the GitHub organisation of the same name.

1. Sign in at <https://central.sonatype.com> with the GitHub organisation account.
2. Add the namespace `io.github.saddad-platform` and complete the verification it asks for.
3. Generate a **user token** in the portal. The token's username and password are the two
   secrets below - not the account's own password.

This cannot be verified from inside the repository. If it has not been done, the upload fails
with an authorisation error from Central.

### 3. Create a signing key — REQUIRED

Maven Central requires every artifact to carry a detached PGP signature.

```bash
gpg --gen-key                                  # a key for the release identity
gpg --list-secret-keys --keyid-format=long     # note the key id
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>   # publish it so Central can verify
gpg --armor --export-secret-keys <KEY_ID>      # the value for GPG_PRIVATE_KEY
```

The exported private key and its passphrase go into repository secrets and nowhere else. Neither
is ever committed, printed or written to a settings file.

### 4. Add the repository secrets — REQUIRED

Settings → Secrets and variables → Actions:

| Secret | What it is |
|---|---|
| `CENTRAL_USERNAME` | The username half of the Central Portal user token |
| `CENTRAL_PASSWORD` | The password half of the Central Portal user token |
| `GPG_PRIVATE_KEY` | The armoured private key exported above |
| `GPG_PASSPHRASE` | That key's passphrase |

### 5. Allow the automation to open pull requests elsewhere — REQUIRED for consumer updates

A workflow's own token only works in the repository it runs in, so opening a pull request on a
service needs a credential of its own. A GitHub App is preferred: it is not tied to a person, it
can be restricted to exactly two permissions, and its token expires with the run.

1. Create a GitHub App in the organisation with these repository permissions, and no others:

   | Permission | Level |
   |---|---|
   | Contents | Read and write |
   | Pull requests | Read and write |
   | Metadata | Read |

2. Install it on the consuming repositories.
3. Add an Actions **variable** `CONSUMER_APP_ID` with the App's id, and a **secret**
   `CONSUMER_APP_PRIVATE_KEY` with its private key.

A fine-grained personal access token in the secret `CONSUMER_UPDATE_TOKEN` works as a fallback,
with the same two permissions. It is a fallback rather than the default because it carries a
person's own access and outlives the run.

Without either, publication still succeeds and the consumer step stops with that explanation.

## What the automation will and will not do

| | |
|---|---|
| Publish the library to Maven Central | Yes, from a GitHub Release only |
| Open one pull request per consuming service | Yes |
| Change the library version in a service's POM | Yes, that one line |
| Change a service's own version | **No** |
| Change a service's other dependencies or its source | **No** |
| Merge a consumer pull request | **No** |
| Release or deploy a service | **No** |
| Publish from a normal push to `main` | **No** |

## Consumer discovery

After a release, the automation lists the organisation's repositories, reads each `pom.xml`, and
treats any repository declaring the `saddad-common` artifact as a consumer. Nothing has to be
registered for a new service to be included. `.github/consumers.yml` exists for the two cases
discovery cannot cover: a consumer outside the organisation, and a repository that should be
left alone.

A service whose version is managed by a parent POM or an imported BOM is reported and skipped
rather than edited, because introducing a version into a service's dependency management is a
change to how that service is structured, not a version bump.
