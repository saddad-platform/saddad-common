# saddad-common

Shared technical library for the SADAD platform: the API response envelope, the error catalogue
and exception model, JWT and password security, JPA base entities, the one-time-code service, the
outbound integration caller and the web filters every service mounts.

It is deliberately technical. Domain and business logic belong to the services that own them, and
nothing here knows what a violation, a wallet or an onboarding application is.

## Installation

Published through [JitPack](https://jitpack.io/#saddad-platform/saddad-common). No account, no
token and no credential of any kind - JitPack builds this repository's tags on demand and serves
them to anyone.

Two things to add, the repository and the dependency.

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.saddad-platform</groupId>
    <artifactId>saddad-common</artifactId>
    <version>VERSION</version>
</dependency>
```

### Gradle

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.saddad-platform:saddad-common:VERSION")
}
```

`VERSION` is a git tag from the
[releases page](https://github.com/saddad-platform/saddad-common/releases) - `1.0.0`, and so on.
The tag is the version: there is no separate publishing step, so what you ask for is exactly what
was tagged.

## Requirements

| | |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.x |

The library is built against Spring Boot 3.3.3 and expects to run inside a Spring Boot
application. It brings `spring-boot-starter-web`, `-validation`, `-security` and `-data-jpa` with
it, so a service that depends on this does not declare those separately.

Components live under `com.sadad.common`. A Spring Boot application that wants them scanned needs
its own scan to include that package, which every service on the platform does with:

```java
@SpringBootApplication
@ComponentScan(basePackages = "com.sadad")
```

## Building it yourself

```bash
./mvnw verify
```

Java 21 is the only thing you need installed. The Maven wrapper (`./mvnw`) downloads and uses
Maven 3.9.16, so your build, GitHub's and JitPack's are the same build - which matters here:
JitPack's own Maven is too old for the compiler plugin Spring Boot 3.3 manages, and the wrapper
is what makes that a solved problem rather than a recurring one. No credentials are required.

To try a change against a service before it is released, install it locally and the service picks
it up from there instead of JitPack:

```bash
./mvnw install
```

## Releasing

Set the version in `pom.xml`, push, and publish a GitHub Release whose tag is exactly that
version. JitPack does the rest, and every service that uses the library gets a pull request
offering the upgrade. [RELEASING.md](RELEASING.md) has the detail.

The tests run on your machine, not on GitHub Actions. Run them before you push, and always
before you tag a release:

```bash
./verify.sh
```

## Licence

Not yet chosen. JitPack does not require one, but without it nobody reading this repository knows
what they are permitted to do with the code. See [RELEASING.md](RELEASING.md), "A licence".
