# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**NewsApp** is a **Kotlin Multiplatform (KMP) + Compose Multiplatform** news-reader app targeting **Android, iOS, Desktop (JVM), and Web (Kotlin/JS + Kotlin/Wasm)**. Unlike template-heavy starters, this repo is a **plain JetBrains KMP Wizard project** — there is no external starter library, no convention plugins, no MVI base class. All app code lives in this repo.

- Kotlin package root / Android + iOS namespace: `com.ggdevhub.newsapp`
- Android `applicationId`: `com.ggdevhub.newsapp`; iOS framework: `SharedLogic` (static)
- Compose resources package: `newsapp.sharedui.generated.resources` (e.g. `import newsapp.sharedui.generated.resources.Res`)
- Kotlin `2.4.10`, Gradle `9.5`, AGP `9.3.1`, Compose Multiplatform `1.11.1`, JVM `11`, Android `minSdk 24` / `compileSdk 37` / `targetSdk 36`
- Targets: **Android + iOS + Desktop (JVM) + Web (js & wasmJs)** — all four platforms must keep building.
- Git remote: `origin` → GitHub `gariustakou/newsapp`, default branch `main`.

> **What NewsApp does (in progress):** a news feed organized by category/rubric (chips), an article detail screen with an "open original" external link, infinite-scroll pagination, and offline-first caching. The functional design is **locked** in `docs/superpowers/specs/2026-08-13-news-feature-design.md` and the step-by-step build in `docs/superpowers/plans/2026-08-13-news-feature.md`. Read both before touching News code — their decisions are binding.

---

## AI Working Instructions

**Persona & mindset**
You are a **senior mobile/multiplatform engineer** with deep **Kotlin Multiplatform + Compose Multiplatform** experience and a strong **UI/UX** sense. You reason across **all four targets** (Android, iOS, Desktop, Web) on every change. Apply high standards: clarity, correctness, performance, testability, maintainability. Recommend the best approach **for this project's actual context** and make proactive improvement proposals. Never write code without considering its impact on the rest of the system **and on every target platform**.

### Before acting — mandatory workflow (non-negotiable)

Three things happen on essentially **every task, before writing code**. Do not skip them.

1. **Invoke the right superpowers skill FIRST.** Skills encode the process. If a skill plausibly applies, use it and announce `Using [skill] to …`.
   - New feature / screen / component / any creative work → `superpowers:brainstorming` **first** (design + user approval), then `superpowers:writing-plans`, then `superpowers:executing-plans` (or `subagent-driven-development`).
   - Bug / test failure / unexpected behavior → `superpowers:systematic-debugging`.
   - Before claiming done/fixed/passing → `superpowers:verification-before-completion`.
   - After a feature → `superpowers:requesting-code-review`; ready to integrate → `superpowers:finishing-a-development-branch`.
   - Never write code, scaffold, or take implementation action for a new feature before a design has been presented and approved (brainstorming gate).

2. **Research the docs in the `kmp` NotebookLM notebook.** Before any non-trivial KMP/Compose decision, **query the `kmp` notebook** — it holds the official Kotlin/Compose Multiplatform docs + videos. Use the Gemini Notebook MCP (`mcp__gemini-notebook-mcp__notebook_query`, notebook titled `kmp`) or the `notebooklm` skill. Query it for: source-set hierarchy & `expect`/`actual`, per-platform dependencies (Ktor engines), **Room KMP** setup and its platform limits, multiplatform ViewModel/lifecycle, navigation, offline-first patterns, and the recommended KMP project layout. Prefer it over guessing; base decisions on what it returns.

3. **Consult / draw inspiration from the reference projects.** Before designing a layer, look at how the maintainer's own reference projects already solve it — reuse proven patterns and versions instead of inventing:
   - **`ido_app`** (`/Users/hoozon2024/Desktop/bureau/kmp_project/ido_app`) — the maintainer's multi-module, Chirp-style KMP app. Primary source for **proven dependency versions** (Ktor/Koin/Coil/Room/serialization), **Ktor-engine-per-source-set** wiring, **Room KMP** setup, **Koin** module patterns, and the **offline-first repository**. Take the *patterns and versions*, **not** its multi-module/`build-logic` structure (this repo is mono-module).
   - **[Chirp](https://github.com/philipplackner/Chirp)** (P. Lackner) — reference for clean multi-module KMP architecture, DI, and convention plugins (conceptual inspiration only here).
   - **[Nib_Note_KMP_NativeUI](https://github.com/MuhammadUnderScoreUsman/Nib_Note_KMP_NativeUI)** — native-UI KMP pattern (shared logic + native Compose/SwiftUI), matching this repo's UI-sharing strategy.
   - **`neotrackapp`** (`/Users/hoozon2024/Desktop/bureau/androidproject/neotrackapp`) — the maintainer's pure-Android app; useful only for Android-side idioms, not KMP structure.

### Sources of truth — priority order (non-negotiable)

1. **The News spec & plan** — `docs/superpowers/specs/2026-08-13-news-feature-design.md`, `docs/superpowers/plans/2026-08-13-news-feature.md`.
2. **Existing project structure & code** (what is already written here — follow its patterns).
3. **The `kmp` NotebookLM notebook** (official Kotlin/Compose Multiplatform docs + videos) — query it via the Gemini Notebook MCP / `notebooklm` skill before any non-trivial KMP decision.
4. **Reference projects** for patterns (architecture inspiration only — do NOT copy their multi-module structure): `ido_app` (`/Users/hoozon2024/Desktop/bureau/kmp_project/ido_app`, a multi-module Chirp-style KMP app — source of proven dependency versions), Philipp Lackner's [Chirp](https://github.com/philipplackner/Chirp), and [Nib_Note_KMP_NativeUI](https://github.com/MuhammadUnderScoreUsman/Nib_Note_KMP_NativeUI) (native-UI KMP note app).
5. General **KMP / Compose Multiplatform** best practices.
6. General **Android / iOS / Desktop / Web** best practices.

If a genuine conflict or ambiguity remains after checking these, **ask** or propose an option for validation before implementing.

### Golden rules — non-negotiable

- **Mono-module is locked.** This project is deliberately **mono-module** (`sharedLogic` + `sharedUI` + app modules). **Do NOT re-propose a Chirp-style multi-module / `build-logic` convention-plugins split** — that was evaluated and declined. Clean Architecture is achieved **by packages/layers inside the existing modules**, not by new Gradle modules.
- **Comment code in French, pedagogically.** The maintainer is learning KMP and wants code that teaches. Every non-obvious line gets a brief French comment explaining **why**, not just what — especially for KMP mechanisms (`expect`/`actual`, source sets, per-platform Ktor engines, offline-first, DI wiring). Match the style already in the build files and `androidApp/App.kt`.
- **Never break another platform.** A change that compiles on JVM may fail on `wasmJs`, `js`, or native. Verify across targets before claiming done (see Compile gate).
- **Disable by commenting, not deleting.** When turning something off (e.g. the Android target of `sharedUI`), **comment it out with a French note explaining why and how to re-enable** — never delete. This is an established convention here.
- **Never duplicate what exists.** Before adding a helper/util/wrapper, search the repo (and the reference projects) first — reuse or extend.
- **Keep the API surface minimal.** Prefer `internal`/`private` for anything not needed outside its package/module. DTOs and Room entities stay internal to the data layer; only domain models cross boundaries.
- **Ask when genuinely ambiguous** — otherwise pick the sensible best-practice default and state it.

### Always invoke superpowers skills before acting

| Situation | Skill |
|---|---|
| New feature / screen / component / any creative work | `superpowers:brainstorming` **first**, no exceptions |
| Resolving a bug, test failure, or unexpected behavior | `superpowers:systematic-debugging` |
| Multi-step implementation | `superpowers:writing-plans` → `superpowers:executing-plans` (or `subagent-driven-development`) |
| Before claiming work is done / fixed / passing | `superpowers:verification-before-completion` |
| After implementing a feature | `superpowers:requesting-code-review` |
| Work complete, ready to integrate | `superpowers:finishing-a-development-branch` |

### The `kmp` NotebookLM notebook — authoritative KMP reference

Use the **`kmp`** notebook (Gemini Notebook MCP, or the `notebooklm` skill) as the source of truth for Kotlin/Compose Multiplatform best practices (official docs + videos). **Query it before** non-trivial decisions on: source-set hierarchy & `expect`/`actual`, per-platform dependencies (Ktor engines), multiplatform ViewModel & lifecycle, Room KMP (and its platform limits), navigation, and the recommended KMP project layout.

---

## Module architecture

Deliberate **mono-module** design. The News feature is organized by **packages/layers inside these existing modules**, never new Gradle modules.

| Module | Targets | Role |
|--------|---------|------|
| `sharedLogic` | android, ios, jvm, js, wasmJs | All shared **non-UI** logic (domain, data, ViewModels, DI). Produces the iOS framework `SharedLogic` (static). |
| `sharedUI` | jvm, js, wasmJs **only** | Compose Multiplatform UI shared by **Desktop + Web only**. `api(project(":sharedLogic"))`. |
| `androidApp` | android | Android app with its **own native Jetpack Compose (AndroidX)** UI. Depends on `:sharedLogic` only — NOT `:sharedUI`. |
| `desktopApp` | jvm | Desktop entry point; depends on `:sharedUI`. |
| `webApp` | js, wasmJs | Web entry point; depends on `:sharedUI`. |
| `iosApp` | iOS | Native **SwiftUI** app (Xcode project) consuming the `SharedLogic` framework. |

### The critical UI-sharing rule
Logic is shared everywhere; **UI is shared selectively**:
- **Android** → native Jetpack Compose (AndroidX) in `androidApp` — does **not** use `sharedUI`.
- **Desktop + Web** → **shared** Compose Multiplatform in `sharedUI`.
- **iOS** → native **SwiftUI** in `iosApp`, over the shared logic/ViewModels.

This is intentional and encoded as commented-out `android { }` blocks in `sharedUI/build.gradle.kts` and a commented `implementation(project(":sharedUI"))` in `androidApp/build.gradle.kts`. **Leave these disabled** unless the maintainer explicitly asks to re-share the Android UI.

### Layering (Clean Architecture by packages)
`presentation → domain ← data`. The `domain` package depends on **nothing** (no Ktor, no Room, no Coil). The remote API (**Currents**) is isolated in `news/data/remote` behind a `NewsRepository` interface so it stays **swappable** (GNews/NewsAPI were evaluated; Currents chosen for browser-CORS + free pagination). Organize by **feature/screen**, mirroring the layer tree:
```
sharedLogic/src/commonMain/.../news/{domain,data,presentation,di,util}
sharedUI/src/commonMain/.../news/ui        # Desktop + Web Compose screens
androidApp/src/main/.../news/ui            # Android native Compose screens
```

### Web has no local database
Room does **not** compile for `js`/`wasmJs`. Room is isolated behind a `NewsLocalDataSource` interface in `commonMain`, with a Room implementation in an intermediate **`nonWebMain`** source set (android/ios/jvm) and an **in-memory** implementation used on Web (js/wasmJs) via a platform Koin module. **Never put Room in `commonMain`.** Web is network-only (session cache); Android/iOS/Desktop are offline-first.

---

## Architecture — MVI (State / Action / Event) + typed Result + offline-first repository

This project uses **MVI / Unidirectional Data Flow**, the **same pattern as the maintainer's own reference projects** `ido_app` and `neotrackapp` (verified in their code: per-feature `XxxState.kt` / `XxxAction.kt` / `XxxEvent.kt` files with a single `onAction(action)` entry point). We implement it directly — **no external MVI base class** (this repo has no DevAtrii starter). Follow this pattern for every screen.

- **State** — an immutable `data class …State` snapshot the UI renders. Only the ViewModel mutates it via `_state.update { }`. Keep transient view state (dialog/dropdown visibility, focus, animation) local to the composable, **never** in `State`.
- **Action** — a `sealed interface …Action` of every user intent. The UI sends **all** intents through **one** entry point: `fun onAction(action)`. No business logic in composables.
- **Event** — a `sealed interface …Event` of one-shot effects (navigation, snackbar, open-link), emitted via a `Channel(...).receiveAsFlow()` and observed once by the `Root`/screen. Do **not** model one-shot effects as state (avoids re-triggering on recomposition).
- **Shared multiplatform ViewModel** (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`) in `news/presentation/`, exposing `state: StateFlow<…State>`, `events: Flow<…Event>`, and `onAction(...)`. The **same** ViewModel is consumed by Android (native Compose), Desktop/Web (shared Compose via `koinViewModel()`), and iOS (SwiftUI observes `state`, dispatches `Action`, reacts to `events`).
- **UI split:** a stateless `Screen(state, onAction)` (preview-able/testable) + a connected `Root` that collects `state`, observes `events`, and performs navigation. ViewModels never navigate directly — they emit an `Event`.
- **Typed errors.** Fallible operations return `Result<D, DataError>` (custom sealed types in `news/domain/model`), never exceptions as control flow. The presentation layer maps errors to UI messages / `ShowError` events.
- **Domain models never leak infrastructure.** DTOs (remote), Room entities (local), and domain `Article` are distinct; map at each boundary with extension-function mappers. UI only ever sees domain models.
- **Repository is the source of truth (offline-first).** `NewsRepositoryImpl` combines a remote source (Currents/Ktor) and a local source (Room/in-memory), exposes a `Flow`, and handles pagination + refresh + a country fallback cascade. Side effects live in the ViewModel/repository, never in composables or mappers.

### News feature specifics (from the spec)
- **API:** Currents (`https://api.currentsapi.services/v1`, `latest-news` / `search`), key `CURRENTS_API_KEY` injected via **BuildKonfig** from `local.properties`.
- **Categories/chips** (one active at a time): À la une (default) · Cameroun · Afrique · Business · Tech · Sport · Santé · Divertissement. Chips map to Currents `country`/`category` params.
- **Language/country:** default `fr` + Cameroun, with a language selector; country priority Cameroun → Africa → France → Canada → USA, plus an automatic fallback cascade when coverage is thin.
- **Pagination:** infinite scroll, ~20/page, bottom loader.
- **Detail:** native screen + "Lire l'article" opens the original URL externally via an `openUrl` `expect`/`actual`.
- **v2 (deferred):** search, favorites/bookmarks, in-app WebView, iOS SwiftUI screens, Web OPFS persistence, combined filters.

---

## Build, Run & Test

Always use the Gradle wrapper (`./gradlew`).

### Run
- Android: `./gradlew :androidApp:assembleDebug` · install: `./gradlew :androidApp:installDebug`
- Desktop: `./gradlew :desktopApp:run` (hot reload: `./gradlew :desktopApp:hotRun --auto`)
- Web (wasm, faster): `./gradlew :webApp:wasmJsBrowserDevelopmentRun` · (js): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run.

### Test
- Android host tests: `./gradlew :sharedLogic:testAndroidHostTest :sharedUI:testAndroidHostTest`
- Desktop/JVM (fastest for commonTest): `./gradlew :sharedLogic:jvmTest`
- Web: `./gradlew :sharedLogic:wasmJsTest` / `:sharedLogic:jsTest`
- iOS: `./gradlew :sharedLogic:iosSimulatorArm64Test`
- Single test: `./gradlew :sharedLogic:jvmTest --tests "com.ggdevhub.newsapp.*SomeTest*"`

### ⚠️ Compile gate — correct task names (verify across targets)
`sharedLogic` and `sharedUI` use the AGP `com.android.kotlin.multiplatform.library` plugin, which produces an `androidMain` variant **with no debug/release split**. **`compileDebugKotlinAndroid` does NOT exist for these modules.** To type-check after a change, run the per-target compile tasks:
```bash
./gradlew :sharedLogic:compileKotlinJvm \
          :sharedLogic:compileKotlinJs \
          :sharedLogic:compileKotlinWasmJs \
          :sharedLogic:compileKotlinIosSimulatorArm64 \
          :sharedLogic:compileAndroidMain
```
`:androidApp` **is** a real Android application, so it has `:androidApp:compileDebugKotlin` / `assembleDebug` (this transitively compiles `sharedLogic`'s Android variant). When a build fails only on a network-dependent Android tool (`aapt2` from `dl.google.com`), it's an environment/sandbox limitation, not a code error — retry with network.

---

## Dependencies & conventions

- All versions live in the **version catalog** `gradle/libs.versions.toml`; reference as `libs.*`. Add new libs there, never inline. TOML uses `-` separators (`ktor-client-core`) → Kotlin accessor uses `.` (`libs.ktor.client.core`).
- **News stack already wired** (versions aligned with `ido_app`): Ktor 3.5.1 (`core`/`content-negotiation`/`serialization-kotlinx-json`/`logging` + per-platform engines — **okhttp** for android+jvm, **darwin** for ios, **js** for js+wasmJs), kotlinx-serialization (+ plugin), kotlinx-coroutines, kotlinx-datetime, Koin 4.2.2, Coil3 3.5.0. Reuse the existing per-source-set engine wiring in `sharedLogic/build.gradle.kts`.
- **To add when implementing News** (see spec §13 / plan Task 1): Room 2.8.4 + KSP + `sqlite-bundled` 2.7.0 + BuildKonfig 0.22.0 + `jetbrains-lifecycle-viewmodel`. **KSP version must match the Kotlin version exactly** (format `2.4.10-x.y.z` — look it up on the KSP releases page). Room KMP needs `kotlin.native.disableCompilerDaemon=true` in `gradle.properties`.
- Per-platform dependencies go in the right source set's `dependencies { }` block; common ones in `commonMain` propagate to all targets. Use `implementation` by default; `api` only to re-export (e.g. `sharedUI` re-exports `sharedLogic`, and iOS-framework exports).

---

## Security & secrets

- **Never commit secrets.** `CURRENTS_API_KEY` lives in `local.properties` (git-ignored) and reaches code only via BuildKonfig. Never hardcode it, never log it. (A client app key is not truly secret — for production, proxy through a backend.)
- Never log tokens or sensitive data. Prefer `internal`/`private` visibility to keep the surface small.
- Run `/security-review` on anything touching networking, storage/sync, or credentials.

## Git

- Branch off `main` before starting work; commit or push **only when the maintainer asks**. Remote: `origin` → `gariustakou/newsapp`.
- Only `README.md` is currently committed; the code/config is not yet under version control — offer a clean initial commit before large changes if useful.

---

## Definition of done — checklist

- [ ] Mono-module respected; Clean Architecture by packages (`presentation → domain ← data`); no duplicated utilities.
- [ ] MVI respected: shared ViewModel exposing `state: StateFlow<…State>` + `events: Flow<…Event>` + single `onAction(…Action)`; per-screen `XxxState`/`XxxAction`/`XxxEvent`; no business logic in composables; no transient UI state in `State`; ViewModels emit `Event`s instead of navigating.
- [ ] Typed `Result`/`DataError`; DTO/entity/domain kept distinct with mappers; UI sees domain models only.
- [ ] UI written **per platform** where required: Android native Compose (`androidApp`), shared Compose (`sharedUI`, Desktop+Web), SwiftUI (iOS, v2). Android does **not** use `sharedUI`.
- [ ] Room only in `nonWebMain`; Web uses the in-memory `NewsLocalDataSource`. Nothing Room in `commonMain`.
- [ ] Koin DI used; new modules registered in `initKoin()`; ViewModels resolved via `koinViewModel()`.
- [ ] **Builds on all relevant targets** (see Compile gate) — not just JVM.
- [ ] Code **commented in French**, pedagogically. Disabled code is commented (with a why + how-to-re-enable), not deleted.
- [ ] Relevant tests added (`:sharedLogic:jvmTest`), and `verification-before-completion` run before claiming done.

## Reference docs
- [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) · [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/) · [Kotlin/Wasm](https://kotl.in/wasm/) · [Currents API](https://currentsapi.services/en/docs/) — plus the **`kmp` NotebookLM** notebook for authoritative KMP/Compose guidance.
