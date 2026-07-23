# Agent guide — hopper-presentation-core

## Build & test

- **Java 21** and Maven 3.8+
- `mvn clean verify` — full compile + unit tests
- Surefire runs **sequentially** (`forkCount=1`); Hop `PluginRegistry` / `JarCache` are not safe for concurrent multi-class initialization

## Platform alignment

- Apache Hop **2.18.1**
- Jandex **3.5.3** (must match Hop so annotation index version is readable)
- Batik **1.19** (aligned with Hop)
- JUnit **5**
- Lombok available (prefer for new/refactored beans)

## Conventions

- Prefer **server-side data + server-side SVG**; do not add browser-side data fetch patterns to the engine
- REST/HTTP delivery belongs in **hopper-presentation-rest**, not this library (legacy servlet demos were removed from tests)
- Use `HJson.createMapper()` for presentation JSON round-trips
- Call `HEnvironment.init()` once before using plugins
- Apache License 2.0 headers for new files when publishing ASF-style

## Related repos

`hopper-presentation-rest`, `hopper-hop-plugins`, `hop-hopper-plugins`, `hopper-viewer`, `hopper-swt-viewer`, `hopper-frontend` under `~/git/mattcasters/`
