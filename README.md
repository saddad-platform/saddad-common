# saddad-common

Shared technical library for the SADAD platform: the API response envelope, the error catalogue
and exception model, JWT and password security, JPA base entities, the one-time-code service,
the outbound integration caller and the web filters every service mounts.

It is deliberately technical. Domain and business logic belong to the services that own them,
and nothing here knows what a violation, a wallet or an onboarding application is.

## Installation

The library is published to Maven Central. No authentication, no token, no extra repository and
no `settings.xml` is needed to use it.

### Maven

```xml
<dependency>
    <groupId>io.github.saddad-platform</groupId>
    <artifactId>saddad-common</artifactId>
    <version>VERSION</version>
</dependency>
```

### Gradle

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.saddad-platform:saddad-common:VERSION")
}
```

Replace `VERSION` with the version you want; the published versions are listed on the
[releases page](https://github.com/saddad-platform/saddad-common/releases).

## Requirements

| | |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.x |

The library is built against Spring Boot 3.3.3 and expects to run inside a Spring Boot
application. It brings `spring-boot-starter-web`, `-validation`, `-security` and `-data-jpa`
with it, so a service that depends on this does not declare those separately.

Components live under `com.sadad.common`. A Spring Boot application that wants them scanned
needs its own scan to include that package, which every service on the platform does with:

```java
@SpringBootApplication
@ComponentScan(basePackages = "com.sadad")
```

## Building it yourself

```bash
mvn verify
```

Java 21 and Maven 3.9 or newer. No credentials are required to build or test.

To try a change against a service before it is released, install it into your local repository
and the service will resolve it from there rather than from Maven Central:

```bash
mvn install
```

## Releasing

Releases are cut from a GitHub Release and published automatically. See
[RELEASING.md](RELEASING.md) for the process and for the one-time account setup it depends on.

Publishing a release also opens a dependency-update pull request on every service in the
organisation that uses the library. Those pull requests are never merged automatically: each
service's owner reviews it, that service's own CI runs against it, and the service is released
on its own schedule. A library release never changes a service's version.

## Licence

**Not yet chosen.** This repository has no LICENSE file, and Maven Central requires one before
anything can be published. Choosing it is the owner's decision; see the note at the top of
`pom.xml` and the prerequisites in [RELEASING.md](RELEASING.md).
