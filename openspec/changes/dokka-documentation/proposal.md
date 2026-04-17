## Why

The SDK now emits rich KDoc on every generated class, property, and service
method. But consumers never see it — the published Maven Central artifacts
ship empty javadoc JARs (a vanniktech default to satisfy Central's
requirements). IDE consumers hovering over `FeedService.getTimeline()` see
no documentation. Adding Dokka replaces those empty stubs with real
KDoc-derived javadoc JARs and optionally hosts browsable HTML docs.

## What Changes

- **Dokka Gradle plugin**: Add the Dokka plugin to `:at-protocol-runtime`,
  `:at-protocol-models`, and `:at-protocol-oauth`. Configure it to generate
  both Javadoc-format JARs (for Maven Central / IDE hover) and HTML docs
  (for browsing).
- **Replace empty javadoc JARs**: Wire Dokka's javadoc JAR output into
  vanniktech's publishing pipeline so published artifacts contain real
  documentation instead of empty stubs.
- **HTML documentation site**: Generate a combined multi-module HTML doc site
  and publish it to GitHub Pages alongside the existing OAuth client-metadata.
- **CI integration**: Add a doc generation step to the release workflow so
  docs stay in sync with published versions.

## Capabilities

### New Capabilities

- `sdk-documentation`: Dokka-based documentation generation pipeline,
  javadoc JAR integration, and GitHub Pages hosting.

### Modified Capabilities

_(none — this is additive tooling, no spec-level behavior changes)_

## Impact

- **Build files**: `gradle/libs.versions.toml` gains Dokka version + plugin.
  Each publishable module's `build.gradle.kts` gains Dokka configuration.
- **Publishing**: Vanniktech's empty javadoc JAR is replaced by Dokka's
  javadoc output. No change to artifact coordinates or consumer API.
- **CI**: Release workflow gains a docs generation + GitHub Pages deploy step.
- **GitHub Pages**: New `/api/` path under `kikin81.github.io/atproto-kotlin/`
  for browsable HTML docs.
- **Breaking changes**: None.
