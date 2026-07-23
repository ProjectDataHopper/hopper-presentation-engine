# Data Hopper Ecosystem

| Repository | Purpose | Platform / status |
|------------|---------|-------------------|
| **hopper-presentation-core** | Core library | Java 21, Hop 2.18.1, published to Nexus `hopper` as `1.0.0-SNAPSHOT` |
| **hopper-presentation-rest** | REST API + HTML/SVG delivery | Java 21, Hop 2.18.1 — primary web path; [smoke test](https://github.com/mattcasters/hopper-presentation-rest/blob/main/docs/smoke-test.md) |
| **hopper-hop-plugins** | Pipeline connector + pipeline/workflow SVG components | Java 21, Hop 2.18.1; tests green |
| **hop-hopper-plugins** | Hop GUI AutoDoc | Java 21, Hop 2.18.1; GUI features only |
| **hopper-swt-viewer** | SWT desktop presentation viewer | Java 21, Hop 2.18.1; thin consumer of hopper-presentation-core |
| **hopper-viewer** | Legacy Jetty viewer | **Deprecated** → use hopper-presentation-rest |
| **hopper-frontend** | Vaadin UI | **Archived** (Hop 0.60) |

## Nexus

Hosted repository: **https://repository.data-hopper.com/repository/hopper/**  
Coordinates: `org.hopper:*:1.0.0-SNAPSHOT`  
Credentials / deploy: [publishing.md](publishing.md)

## Dependency direction

```
hopper-presentation-rest / hop-hopper-plugins / hopper-swt-viewer
            │
            ├── hopper-hop-plugins (Hop-specific Data Hopper plugins)
            │
            └── hopper-presentation-core
                    │
                    └── hop-core / hop-engine (+ Batik, Jackson, …)
```

Publish order: **hopper-presentation-core** → **hopper-hop-plugins** → **hop-hopper-plugins** / **hopper-presentation-rest** / **hopper-swt-viewer**.
