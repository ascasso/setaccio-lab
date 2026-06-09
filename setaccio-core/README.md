# setaccio-core

Plain Java BLAKE3 hashing utilities shared by Setaccio projects.

This module intentionally has no Spring Framework or Spring Boot dependency. It can be used from Spring applications, command-line tools, tests, or any other Java code by constructing the hashing implementation directly.

## Implementations

- `ApacheCommonsBlake3HashingServiceImpl` backed by `commons-codec`
- `BouncyCastleBlake3HashingServiceImpl` backed by Bouncy Castle

Both implement `Blake3HashingService`.

## Usage

```java
Blake3HashingService hashingService = new ApacheCommonsBlake3HashingServiceImpl();

String hash = hashingService.hashString("Hello, World!");
boolean matches = hashingService.verifyHash("Hello, World!".getBytes(StandardCharsets.UTF_8), hash);
```

## Build

```bash
./gradlew :setaccio-core:build
```

## Runtime Dependencies

```text
commons-codec:commons-codec
org.bouncycastle:bcprov-jdk18on
org.slf4j:slf4j-api
```

Spring integration belongs in consuming applications such as `setaccio-lab`, not in this core library.
