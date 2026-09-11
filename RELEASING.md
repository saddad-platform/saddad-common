# Releasing saddad-common

The library is distributed by **JitPack**. There is no account to create, no key to generate and
no secret to store: JitPack watches this repository, and when somebody asks for a version it
clones that git tag, builds it, and serves the result.

## You do not release it. Pushing releases it.

Push to `main` and a version publishes itself: the workflow works out the number, puts it in the
POM, commits it, tags it, creates the GitHub Release, waits for JitPack to build it, and opens a
pull request on every service that uses the library.

The only thing you control is **the number**, and you control it with the commit message you were
writing anyway:

| Your commit message | Version goes | Meaning |
|---|---|---|
| `Fix the wallet balance` | `1.0.0` → `1.0.1` | patch: nothing a caller can see |
| `fix: the report period` | `1.0.1` → `1.0.2` | patch |
| `feat: add the corporate report` | `1.0.2` → `1.1.0` | minor: something new, nothing removed |
| `feat!: rename the outcomes` | `1.1.0` → `2.0.0` | major: something will break |
| `refactor(api)!: drop the old envelope` | `2.0.0` → `3.0.0` | major |

Ordinary messages are patches, which is the safe default and how this repository has been written
so far. You only have to think about it when you add something (`feat:`) or break something
(`!`) - and then the number is telling six services whether the upgrade is safe, which is the
entire reason versions exist.

**Before you push, run the tests. Nothing on GitHub does:**

```bash
./verify.sh
```

### Not publishing a particular push

Put `[skip release]` anywhere in the commit message. Documentation-only changes are skipped
automatically.

### Publishing a specific number

Actions → **Release** → **Run workflow**, and type the version. Leave the box empty to let the
commits decide, as a push would. Tick **dry run** to work everything out and build it without
creating a tag, a release or any pull requests.

### The one rule behind all of this

**The git tag is the version**, because JitPack serves the tag name verbatim. That is why tags
here have no `v`: a `v1.0.1` tag would put `<version>v1.0.1</version>` into six service POMs. The
workflow creates the tags, so this is mostly something you no longer have to remember.

### If something goes wrong

| What happened | What to do |
|---|---|
| The workflow failed before JitPack | The tag was never served. Delete the tag and the release, fix, and release again with the same number. |
| JitPack failed to build | Read `https://jitpack.io/com/github/saddad-platform/saddad-common/<version>/build.log` - it is the actual build output and says exactly what broke. Fix, then release a new version: JitPack caches a result per version, including a failure. |
| JitPack was still building when the workflow gave up | Nothing is wrong. Check <https://jitpack.io/#saddad-platform/saddad-common>, then run **Update consuming services** by hand with that version. |
| The pull requests did not appear | Run **Update consuming services** on its own. It is safe to run repeatedly. |

## Setup

Almost none. JitPack needs nothing configured: the first time anybody requests a version, it
builds it.

Three things in this repository are the whole configuration:

- **`jitpack.yml`** tells JitPack to use JDK 21, to build through the Maven wrapper, and to
  attach the sources and Javadoc jars.
- **`mvnw` and `.mvn/`** pin Maven 3.9.16. JitPack's own Maven is older than 3.6.3, which the
  compiler plugin Spring Boot 3.3 manages refuses to run on, so without the wrapper every
  JitPack build fails with "The plugin ... requires Maven version 3.6.3" while building fine
  locally. Keep all three files committed.
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
