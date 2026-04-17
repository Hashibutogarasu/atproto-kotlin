## Overview

Add Dokka to generate real javadoc JARs (replacing vanniktech's empty stubs)
and a browsable HTML documentation site deployed to GitHub Pages.

## Dokka Plugin Setup

### Version catalog

Add to `gradle/libs.versions.toml`:

```toml
[versions]
dokka = "2.0.0"

[plugins]
dokka = { id = "org.jetbrains.dokka", version.ref = "dokka" }
```

Dokka 2.0.0 is the latest stable release supporting Kotlin 2.x and KMP.

### Root project

Apply Dokka at the root level for multi-module HTML aggregation:

```kotlin
// build.gradle.kts (root)
plugins {
    alias(libs.plugins.dokka)
}

dependencies {
    dokka(project(":at-protocol-runtime"))
    dokka(project(":at-protocol-models"))
    dokka(project(":at-protocol-oauth"))
}
```

This enables `./gradlew :dokkaGeneratePublicationHtml` to produce a
combined multi-module HTML site.

### Per-module configuration

Each publishable module (`:at-protocol-runtime`, `:at-protocol-models`,
`:at-protocol-oauth`) applies the Dokka plugin:

```kotlin
plugins {
    alias(libs.plugins.dokka)
}
```

Dokka auto-discovers KDoc from Kotlin source sets. No additional
source set configuration is needed for JVM or KMP modules.

## Javadoc JAR Integration

### How vanniktech currently works

The vanniktech maven-publish plugin creates an empty javadoc JAR via
`jvmEmptyJavadocJar` (for KMP modules) or `plainJavadocJar` (for JVM
modules) to satisfy Maven Central's requirement. These are empty stubs.

### Replacing with Dokka output

Vanniktech 0.36+ supports configuring a custom javadoc JAR. When Dokka
is applied, we configure vanniktech to use Dokka's javadoc output:

```kotlin
mavenPublishing {
    configure(JavadocJar.Dokka("dokkaGeneratePublicationJavadoc"))
}
```

This tells vanniktech to run Dokka's javadoc generation task and package
its output as the javadoc JAR instead of the empty stub.

### KMP module consideration

For `:at-protocol-runtime` and `:at-protocol-models` (KMP modules), the
javadoc JAR attaches to the JVM publication. The `kotlinMultiplatform`
metadata publication gets the same javadoc JAR. Dokka handles KMP
source sets natively.

## HTML Documentation Site

### Generation

`./gradlew :dokkaGeneratePublicationHtml` at the root produces a combined
HTML site under `build/dokka/html/` with cross-module linking.

### GitHub Pages deployment

The existing GitHub Pages setup serves from `docs/` on the `main` branch
(used for `oauth/client-metadata.json`). The HTML docs go under `docs/api/`.

Options for deployment:
1. **Build-time commit**: Generate docs during release, commit to `docs/api/`,
   push. Simple but adds generated files to the repo.
2. **GitHub Actions artifact**: Upload docs as a Pages artifact in CI.
   Cleaner but requires Pages to be configured for Actions deployment.

Recommended: **Option 2** — use a separate GitHub Actions job that builds
docs and deploys via `actions/deploy-pages`. This keeps generated HTML
out of the repo while still serving it at the Pages URL.

### URL structure

- `https://kikin81.github.io/atproto-kotlin/api/` — root of the HTML docs
- `https://kikin81.github.io/atproto-kotlin/api/at-protocol-runtime/` — runtime module
- `https://kikin81.github.io/atproto-kotlin/api/at-protocol-models/` — models module
- `https://kikin81.github.io/atproto-kotlin/api/at-protocol-oauth/` — OAuth module

## CI Integration

### Release workflow addition

Add a `docs` job to `.github/workflows/release.yaml` that runs after
the release job (only when a new version is published):

```yaml
docs:
  name: Deploy API Docs
  needs: release
  if: needs.release.outputs.new-release-published == 'true'
  runs-on: ubuntu-latest
  permissions:
    pages: write
    id-token: write
  steps:
    - uses: actions/checkout@v6
      with:
        ref: v${{ needs.release.outputs.new-release-version }}
    - # JDK, Node, lexicons setup...
    - run: ./gradlew :dokkaGeneratePublicationHtml
    - uses: actions/upload-pages-artifact@v3
      with:
        path: build/dokka/html
    - uses: actions/deploy-pages@v4
```

### Considerations

- The `docs/` directory currently hosts `oauth/client-metadata.json` which
  must remain accessible. The Pages deployment needs to merge the API docs
  with existing static content, or we serve everything from the Actions
  artifact (including the client-metadata.json).
- Simplest path: keep `docs/` for static files (client-metadata) and use
  a separate branch or artifact for API docs. Or consolidate everything
  into the Actions-based deployment.

## Affected Modules

| Module | Dokka Plugin | Javadoc JAR | HTML Docs |
|--------|-------------|-------------|-----------|
| `:at-protocol-runtime` | Yes | Dokka javadoc | Included |
| `:at-protocol-models` | Yes | Dokka javadoc | Included |
| `:at-protocol-oauth` | Yes | Dokka javadoc | Included |
| `:at-protocol-generator` | No | Not published | Not included |
| `:samples:android` | No | Not published | Not included |

## Non-goals

- Custom Dokka themes or branding (default theme is fine for v1)
- Versioned docs (multiple versions hosted simultaneously)
- Dokka for the generator module (internal, not published)
