# Releasing saddad-common

The library is distributed by **JitPack**. There is no account to create, no key to generate and
no secret to store: JitPack watches this repository, and when somebody asks for a version it
clones that git tag, builds it, and serves the result.

## You do not release it. Pushing releases it.

Push to `main` and a version publishes itself: the workflow works out the number, puts it in the
POM, commits it, tags it, creates the GitHub Release, and waits for JitPack to build it.

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
creating a tag or a release.

### The one rule behind all of this

**The git tag is the version**, because JitPack serves the tag name verbatim. That is why tags
here have no `v`: a `v1.0.1` tag would put `<version>v1.0.1</version>` into six service POMs. The
workflow creates the tags, so this is mostly something you no longer have to remember.

### If something goes wrong

| What happened | What to do |
|---|---|
| The workflow failed before JitPack | The tag was never served. Delete the tag and the release, fix, and release again with the same number. |
| JitPack failed to build | Read `https://jitpack.io/com/github/saddad-platform/saddad-common/<version>/build.log` - it is the actual build output and says exactly what broke. Fix, then release a new version: JitPack caches a result per version, including a failure. |
| JitPack was still building when the workflow gave up | Nothing is wrong. The tag is published; check <https://jitpack.io/#saddad-platform/saddad-common> a minute later. |

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

### A licence

Not required by JitPack, and not currently declared. It is still worth adding: without one, anyone
reading the repository has no idea what they are permitted to do with the code, and the safe
assumption is "nothing". Add a `LICENSE` file and uncomment the `<licenses>` block in `pom.xml`.

## What the automation will and will not do

| | |
|---|---|
| Build and serve the library from a tag | Yes, JitPack, on demand |
| Touch any other repository | **No** |
| Release or deploy a service | **No** |
| Run anything automatically on a push | **No** |
| Run test cases on GitHub Actions | **No** - `./verify.sh` runs them on your machine |

## Updating the services

By hand, when each service wants it. In that service's `pom.xml`:

```xml
<dependency>
    <groupId>com.github.saddad-platform</groupId>
    <artifactId>saddad-common</artifactId>
    <version>1.0.1</version>
</dependency>
```

The six services that use the library are saddad-admin, saddad-auth, saddad-employees,
saddad-onboarding, saddad-violations and saddad-wallet. Each already has the JitPack repository
declared, so changing the version is the only edit needed.
