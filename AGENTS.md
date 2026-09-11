# Repository Guidelines

## Project Structure & Module Organization

This is a single Gradle Java plugin for Paper 26.2 and Floodgate. Production code is under `src/main/java/com/origin173/schoolBedrockLink/`, organized by responsibility: `config`, `floodgate`, `link`, `oauth`, `http`, `security`, `audit`, `command`, `listener`, and `util`. Plugin metadata and defaults live in `src/main/resources/plugin.yml` and `config.yml`. JUnit tests mirror the base package in `src/test/java/`. Deployment and acceptance material is in `docs/`; `build/` and `plugins/` contain ignored generated artifacts.

There are no templates or static web assets: `http/HtmlPages.java` renders every browser-facing page (markup, inline stylesheet, inline SVG icons) as one shared shell, and `http/HttpUtil.java` owns the security headers. `src/test/java/.../BrowserFixture.java` is test-only scaffolding (a fake provider plus fake Floodgate), not shipped code.

## Architecture & Security Invariants

The plugin binds Bedrock/Xbox identities to a real Java profile via the school's Blessing Skin OAuth2, then Floodgate Local Linking. Before touching `link/`, `oauth/`, `floodgate/`, or `http/`, read `README.md` (身份和安全模型 section) and `docs/acceptance.md`. Non-negotiable invariants:

- Role names from `/api/players` are the only allowed profile source; the Yggdrasil profile result must intersect that name set. The browser may only submit an in-memory session `profileIndex`, never a client-supplied Java UUID.
- `Floodgate isLinked()` is not an auth credential: every Bedrock join must verify the Floodgate mapping and `approved-links.json` agree, so Floodgate Global Linking can never bypass school OAuth.
- Bedrock joins fail closed (reject) when the registry is corrupt or Floodgate is unavailable; Java players must remain unaffected by plugin failures.
- OAuth access tokens stay in memory only — never persist, log, or audit them. All HTML goes through `HtmlEscaper`, and `form-action` CSP must include the configured authorization origin plus self (a real Chromium regression; see `docs/review-2026-09-09.md`).
- Reload reads config from disk (not Bukkit's cache), validates the whole candidate, and publishes an immutable snapshot; invalid files keep the last valid one. Listener/public-URL/timeout/development-mode changes require a restart instead.

## Build, Test, and Development Commands

Use Java 25 and the Gradle Wrapper:

```powershell
.\gradlew.bat --gradle-user-home ".gradle-user-home" --no-daemon --console=plain clean test build
```

`test` runs the JUnit 5 suite. `build` creates the shaded plugin JAR in `build/libs/` (Jackson is relocated to `com.origin173.schoolBedrockLink.libs.jackson`) and stages `plugins/SchoolBedrockLink.jar` for a local Paper/Purpur server. For a faster iteration, run `.\gradlew.bat test`; run the full build before submitting changes. CI runs `clean test build` on every push and pull request, plus Qodana JVM analysis (`qodana.yaml`, JDK 25).

The build declares a Java 25 toolchain, so the Gradle launcher resolves its own JDK — a stale `javac` on `PATH` does not matter. The `test` task redirects `java.io.tmpdir` to `.gradle-tmp/`, so temp files written by tests land in the repository rather than the system temp directory. There is no local lint or format task; Qodana only runs in CI, so compile warnings are the only local static signal.

For changes to `http/` HTML flows or CSP, verify in a real browser: `.\gradlew.bat browserFixture` runs the production handlers under Chromium with a simulated provider, and `node docs/csp-probe.cjs` demonstrates the `form-action` regression. curl/302 checks alone are not sufficient evidence (see `docs/review-2026-09-09.md`). The fixture binds fixed loopback ports (app 18793, fake provider 18794) and needs a locally installed Chrome; when testing page layout, exercise the full start → authorize → callback → selection → confirm sequence at narrow widths as well as desktop, since the pages are responsive.

Pushing a `v<major>.<minor>.<patch>` tag triggers `.github/workflows/release.yml`, which builds with `-Pversion=<tag without the leading v>` (the build reads the version from that Gradle property and falls back to the value in `build.gradle.kts`) and publishes a GitHub Release carrying the shaded JAR. The release body comes from `.github/scripts/changelog.sh`, which groups the commits between the previous tag and the new one by conventional-commit prefix and adds compare/commit links, so keep writing `feat:`/`fix:`/`docs:` style subjects. The workflow can also be dispatched manually from `master`. See the README 发布 section.

## Browser UI Constraints

All page styling lives in the single `STYLES` block inside `HtmlPages`. The response CSP is `default-src 'none'; style-src 'unsafe-inline'`, so the sheet must stay self-contained: no external stylesheets, web fonts, images, or scripts can load, and font stacks must resolve to locally installed faces (the Geist tokens fall back to system UI fonts). Icons are inline SVG with `aria-hidden="true"`.

The six rendered variants are home, profile selection, success, and the 400/404/405 error pages (error pages may carry a request ID rendered in mono). Style additions should use the existing custom properties (`--canvas`, `--elevated`, `--ink`, `--body`, `--hairline`, `--link`, `--error`, `--sans`, `--mono`) rather than literal colors, and the sheet targets a single light color scheme. Restyling is presentation-only — routes, form field names, and copy are part of the flow contract and are covered by `HttpFlowTest`; do not change them for visual reasons.

## Docs to Read Before Sensitive Changes

`docs/acceptance.md` is the manual acceptance checklist (in Chinese) — update it whenever manual verification behavior changes. `docs/review-2026-09-09.md` records confirmed defects and their regression evidence. `docs/nginx.example.conf` shows the required reverse-proxy setup (Nginx overwrites XFF; only loopback peers with a single literal address are trusted).

## Coding Style & Naming Conventions

Follow the existing Java style: four spaces, UTF-8, braces on the declaration line, and focused `final` classes/services. Use `UpperCamelCase` for types, `lowerCamelCase` for methods and variables, and descriptive suffixes such as `Service`, `Handler`, `Config`, and `Test`. Keep code in the existing package structure and use immutable value objects where the surrounding code does. No repository formatter is configured, so keep formatting consistent with neighboring files and compile with Java 25.

## Testing Guidelines

Tests use JUnit Jupiter and are named after behavior, for example `expiredAndRepeatedStateAreRejected`. Add regression tests for link-policy decisions, OAuth/PKCE state handling, profile authorization, registry corruption, and fail-closed security paths. No coverage threshold is configured; every behavior or security change should include focused tests. Run `.\gradlew.bat test` locally.

## Security, Configuration & Pull Requests

Never commit OAuth secrets, tokens, or real deployment URLs. Use `SCHOOL_BEDROCK_OAUTH_SECRET` for the client secret and preserve HTTPS/redirect-URI validation. Changes to linking or OAuth must retain fail-closed behavior and update `docs/acceptance.md` when manual verification changes.

Commits use concise imperative subjects with a conventional prefix such as `fix:`, `feat:`, or `docs:`; the release changelog generator parses those prefixes, so an unprefixed subject ends up under 其他改动. Pull requests should explain behavior and security impact, list tests run, call out config/deployment changes, link an issue when applicable, and include screenshots for web UI changes.
