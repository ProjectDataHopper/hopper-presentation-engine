# Code review notes — hopper-presentation-core (2026-07-22)

Review after Hop 2.18.1 / Java 21 port. Severity: **P0** must-fix, **P1** should-fix, **P2** backlog.

## Findings

### P1 — Connector streaming state — **FIXED**

**Where:** Sort, filter, distinct, selection, passthrough, chain  
**Issue:** Listeners were never removed from the source after completion.  
**Fix:** `HBaseConnector.attachToSource` / `detachFromSource`; all transform connectors detach in `waitUntilFinished()` `finally`. Covered by listener-cleanup unit test.

### P1 — Distinct is “previous row” distinct, not full-set distinct — **DOCUMENTED**

**Where:** `HDistinctConnector`  
**Issue:** Compares only to the previous row.  
**Fix:** Class + plugin description + `docs/connectors.md` clarify adjacent-only semantics. Full-set distinct remains P2.

### P1 — Simple filter AND of equality only — **DOCUMENTED**

**Where:** `HSimpleFilterConnector`  
**Issue:** Equality-only filter semantics.  
**Fix:** Class Javadoc + `docs/connectors.md` describe AND across fields / OR within a field’s value set.

### P1 — REST connector stack — **FIXED**

**Where:** `HRestConnector`  
**Issue:** Apache HttpClient + json-simple + unused Jersey.  
**Fix:** JDK `HttpClient` + Jackson; removed json-simple and Jersey client dependencies; unit tests with local `HttpServer`.

### P2 — Large component classes

Crosstab (~1.2k LOC), table (~850 LOC), chart base (~600 LOC). Hard to unit-test edge cases without extraction. Prefer locking behavior with presentation tests before refactoring.

### P2 — Logging noise

`HPresentation.doLayout` logs every parameter at Basic. Prefer Debug for production embeds.

### P2 — `maxSurface` geometry semantics

`HGeometry.maxSurface` stores max right/bottom in width/height fields rather than true width/height. Call sites appear consistent; document or fix with tests if public.

### Fixed in modernization / P1 pass

- Jandex version mismatch with Hop 2.18.1 (init failures)
- JSON `fullName` round-trip failure from `HopMetadataBase`
- Race-prone `HEnvironment.init` (now synchronized/idempotent)
- Removed dead `ITypeMetadata` adapters and engine `@Path` on DB connection
- SQL connector test restored on JUnit 5
- Transform listener cleanup (`attachToSource` / `detachFromSource`)
- REST connector on JDK HttpClient + Jackson; Jersey/json-simple removed

## Test coverage status

| Area | Status |
|------|--------|
| Sample / list / sort / filter / distinct / passthrough / selection | Unit tests added |
| SQL (H2) | Unit test (JUnit 5) |
| REST | Unit tests with local HttpServer |
| Listener cleanup | Unit test on shared source instance |
| Presentation SVG renders | Existing integration tests |
| Chain / metadata connectors | Still thin |
| Component unit tests (isolated) | Still thin (mostly presentation-level) |
| NPE JSON fixtures | Exercised via SteelWheels presentation suite |

## Recommended next fixes

1. Chain connector unit test  
2. Assert SVG content (not only “did not throw”) in presentation tests  
3. Lombok pass on metadata beans  
4. Optional full-set distinct mode (P2)  
