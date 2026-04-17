## 1. Dokka plugin setup

- [x] 1.1 Add Dokka version (`2.0.0`) and plugin to `gradle/libs.versions.toml`
- [x] 1.2 Apply Dokka plugin to root `build.gradle.kts` with `dokka()` dependencies on the three publishable modules
- [x] 1.3 Apply Dokka plugin to `:at-protocol-runtime/build.gradle.kts`
- [x] 1.4 Apply Dokka plugin to `:at-protocol-models/build.gradle.kts`
- [x] 1.5 Apply Dokka plugin to `:at-protocol-oauth/build.gradle.kts`
- [x] 1.6 Verify `./gradlew :at-protocol-runtime:dokkaGenerateModuleHtml` runs successfully
- [x] 1.7 Verify `./gradlew :at-protocol-oauth:dokkaGenerateModuleHtml` runs successfully

## 2. Javadoc JAR integration

- [x] 2.1 Configure vanniktech in `:at-protocol-runtime` to use `JavadocJar.Dokka("dokkaGenerateModuleHtml")` instead of the empty javadoc JAR
- [x] 2.2 Configure vanniktech in `:at-protocol-models` to use Dokka javadoc JAR
- [x] 2.3 Configure vanniktech in `:at-protocol-oauth` to use Dokka javadoc JAR
- [x] 2.4 Verify `./gradlew :at-protocol-runtime:publishToMavenLocal` (or the publish task minus signing) produces a non-empty javadoc JAR
- [x] 2.5 Spot-check: unzip the javadoc JAR and confirm it contains HTML files with real documentation

## 3. HTML documentation generation

- [x] 3.1 Verify `./gradlew :dokkaGeneratePublicationHtml` produces a combined multi-module HTML site under `build/dokka/html/`
- [x] 3.2 Open the generated HTML in a browser and confirm cross-module links work
- [x] 3.3 Spot-check: confirm KDoc descriptions appear on a representative class, property, and service method

## 4. GitHub Pages deployment

- [x] 4.1 Add a `docs` job to `.github/workflows/release.yaml` that generates HTML docs and deploys to GitHub Pages via `actions/deploy-pages`
- [x] 4.2 Ensure the deployment includes existing static content (`oauth/client-metadata.json`) alongside the API docs
- [x] 4.3 Verify the docs are accessible at `https://kikin81.github.io/atproto-kotlin/api/` after a release

## 5. Build verification

- [x] 5.1 `./gradlew build` still passes (Dokka doesn't break the existing build)
- [x] 5.2 `./gradlew spotlessCheck` passes
- [x] 5.3 `pre-commit run --all-files` passes
- [x] 5.4 Update root `README.md` to link to the API documentation site
