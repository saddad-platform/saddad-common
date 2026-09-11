# Releasing saddad-common

The library is distributed by **JitPack**. There is no account to create, no key to generate and
no secret to store: JitPack watches this repository, and when somebody asks for a version it
clones that git tag, builds it, and serves the result.

So releasing is: **set the version, tag it, publish a GitHub Release.** Everything else happens on
its own.

## The one rule

**The git tag is the version.** JitPack does not read your POM to decide what to call a release;
it uses the tag name. So the tag and the POM version have to be the same text:

| Tag | What consumers would write |
|---|---|
| `1.0.1` | `<version>1.0.1</version>` |
| `v1.0.1` | `<version>v1.0.1</version>` — which is why we do not do this |

Tags here carry **no `v` prefix**. The release workflow refuses a `v` tag and tells you to
re-tag, rather than letting six services end up with a `v` in their POMs.

## Releasing, step by step

1. **Check it locally. This is the only place the tests run.** Test cases are not run on GitHub
   Actions on this platform, so nothing else will catch a failure for you:

   ```bash
   ./verify.sh
   ```

2. **Choose the version.** This is a judgement about the change, so nothing automates it:

   | Change | Example |
   |---|---|
   | Bug fix, internal change, nothing a caller can see | `1.0.0` → `1.0.1` |
   | New API, new optional behaviour, nothing removed | `1.0.1` → `1.1.0` |
   | A public API changed or was removed, or behaviour changed incompatibly | `1.1.0` → `2.0.0` |

3. **Set it in the POM and push:**

   ```bash
   mvn versions:set -DnewVersion=1.0.1 -DgenerateBackupPoms=false
   git commit -am "chore(release): 1.0.1"
   git push
   ```

4. **Publish a GitHub Release** on that commit, tagged exactly `1.0.1`.

5. **Watch the Release workflow.** It checks the tag and the POM agree, builds what JitPack will
   build, asks JitPack to build it now and waits for the answer, then opens a pull request on
   every service that uses the library. It does not run the tests - step 1 did that.

6. **Review those pull requests.** One per service, each changing one line. Merging one does not
   release that service; that stays the service owner's decision.

### Rehearsing

Actions tab → **Release** → **Run workflow**, give it an existing tag and set **dry run** to
`true`. Everything happens except asking JitPack and opening pull requests.

### If something goes wrong

| What happened | What to do |
|---|---|
| The workflow failed before JitPack | The tag was never served. Delete the tag and the release, fix, and release again with the same number. |
| JitPack failed to build | Read `https://jitpack.io/com/github/saddad-platform/saddad-common/<version>/build.log`. Fix, then release a new version: a tag JitPack has already built is cached. |
| JitPack was still building when the workflow gave up | Nothing is wrong. Check <https://jitpack.io/#saddad-platform/saddad-common>, then run **Update consuming services** by hand with that version. |
| The pull requests did not appear | Run **Update consuming services** on its own. It is safe to run repeatedly. |

## Setup

Almost none. JitPack needs nothing configured: the first time anybody requests a version, it
builds it.

Two files in this repository are the whole configuration:

- **`jitpack.yml`** tells JitPack to use JDK 21 and to attach the sources and Javadoc jars.
  Without the JDK line it builds with an older Java and fails.
- **`pom.xml`** carries the version, which must match the tag.

### The one optional thing

The automatic pull requests to the services need a credential, because GitHub does not let a
workflow in one repository write to another. Without it, releases still work perfectly and you
bump the version in each service by hand.

**The simple way:**

1. <https://github.com/settings/personal-access-tokens> → **Generate new token** → fine-grained.
2. Resource owner `saddad-platform`, and select the six service repositories.
3. Permissions: **Contents: Read and write**, **Pull requests: Read and write**. Nothing else.
4. Add it as the repository secret `CONSUMER_UPDATE_TOKEN` under
   **Settings → Secrets and variables → Actions**.

**The better way**, because it is not tied to your personal account: create a GitHub App in the
organisation with those same two permissions, install it on the service repositories, then add the
Actions variable `CONSUMER_APP_ID` and the secret `CONSUMER_APP_PRIVATE_KEY`.

### A licence

Not required by JitPack, and not currently declared. It is still worth adding: without one, anyone
reading the repository has no idea what they are permitted to do with the code, and the safe
assumption is "nothing". Add a `LICENSE` file and uncomment the `<licenses>` block in `pom.xml`.

## What the automation will and will not do

| | |
|---|---|
| Build and serve the library from a tag | Yes, JitPack, on demand |
| Open one pull request per consuming service | Yes |
| Change the library version in a service's POM | Yes, that one line |
| Change a service's own version | **No** |
| Change a service's other dependencies or its source | **No** |
| Merge a consumer pull request | **No** |
| Release or deploy a service | **No** |
| Run anything automatically on a push | **No** |
| Run test cases on GitHub Actions | **No** - `./verify.sh` runs them on your machine |

## Consumer discovery

After a release, the automation lists the organisation's repositories, reads each `pom.xml`, and
treats any repository declaring the `saddad-common` artifact as a consumer. Nothing has to be
registered for a new service to be included. `.github/consumers.yml` exists for the two cases
discovery cannot cover: a consumer outside the organisation, and a repository to leave alone.

A service whose version comes from a parent POM or an imported BOM is reported and skipped rather
than edited, because introducing a version into a service's dependency management is a change to
how that service is structured, not a version bump.
