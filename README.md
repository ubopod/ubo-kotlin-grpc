# ubo-kotlin-grpc

Kotlin gRPC bindings for the Ubo App. Mirrors the Swift package at
`../ubo-swift-grpc/` and is consumed by the Android phone-app and WearOS
app at `../ubo-kotlin-apps/` (forthcoming).

## Layout

```
ubo-kotlin-grpc/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── generate-protos.sh           # syncs ../ubo_app/rpc/proto/ → protos/src/main/proto/
├── protos/                      # ↓ Pure JVM Java+Kotlin, generates proto + grpc-Kotlin stubs
│   ├── build.gradle.kts         #   java-library + kotlin-jvm + com.google.protobuf
│   └── src/main/proto/          #   mirrored .proto sources
├── lib/                         # ↓ Android library; consumes :protos as compiled bytecode
│   ├── build.gradle.kts         #   com.android.library + kotlin-android
│   ├── consumer-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/com/ubopod/ubokotlin/
│       │       ├── UboLibrary.kt
│       │       ├── UboError.kt
│       │       ├── connection/
│       │       │   ├── ConnectionState.kt
│       │       │   ├── ReconnectPolicy.kt
│       │       │   └── UboConnection.kt
│       │       ├── conversion/
│       │       │   ├── ProtoToView.kt        # google.protobuf.Any → ViewData
│       │       │   └── ProtoFromAction.kt    # UboAction → Ubo.Action proto
│       │       └── models/
│       │           ├── Key.kt, UboColor.kt, SystemStats.kt, ...
│       │           ├── ViewData.kt           # sealed: 7 view subtypes
│       │           ├── UboAction.kt          # sealed: 50+ action variants
│       │           └── ...
│       └── test/kotlin/com/ubopod/ubokotlin/
│           ├── connection/ReconnectPolicyTest.kt
│           └── conversion/{ViewDataRoundTripTest,ActionBuildTest}.kt
└── README.md
```

### Why two modules?

The auto-generated `Ubo.java` (every Action / Event / ViewData oneof
collapsed into a single Java outer class) is roughly **20 MB / 540 K LOC
with 1100+ nested classes**. The Kotlin compiler's Java symbol indexer
can't ingest a file that large during cross-package symbol resolution —
even with 12 GB of heap. Splitting the codebase so that `:protos`
compiles the protos to .class bytecode in pure Kotlin/JVM, and the
Android `:lib` consumes them as compiled bytecode, sidesteps the limit.

## Build prerequisites

- JDK 17+ (e.g. via SDKMAN: `sdk install java 17.0.10-amzn`).
- Android SDK with `platforms;android-34` and `build-tools;34.x.x`.
  Set `ANDROID_HOME` (or create `local.properties` with `sdk.dir=...`).
- The Gradle wrapper bundled with this project; no system Gradle needed.

## Getting started

```bash
# 1. Sync proto sources from the parent ubo_app/ tree.
./generate-protos.sh

# 2. Generate stubs and build the AAR.
./gradlew :lib:assemble

# 3. Run unit tests.
./gradlew :lib:testDebugUnitTest
```

## Proto generation

`generate-protos.sh` is the source-of-truth sync script. It copies the
four proto packages — `package_info/`, `ubo/`, `store/`, `secrets/` —
from `../ubo_app/rpc/proto/` into `lib/src/main/proto/`. The Gradle
`com.google.protobuf` plugin then runs `protoc` with three plugins
during `:lib:assemble`:

- `kotlin` builtin → idiomatic Kotlin DSL builders for messages
- `grpc` (Java)    → service stubs (used by the Kotlin coroutine stubs)
- `grpckt`         → suspend / Flow-based coroutine stubs

Outputs land under `lib/build/generated/source/proto/<variant>/` and are
included in the lib's source set. Run `./generate-protos.sh --check`
to detect drift between the local proto tree and the parent Python repo.

The mirrored `.proto` files are **not committed** — they are gitignored,
matching the core repo, which ignores `/ubo_app/rpc/proto/ubo` and its
generated bindings. Sync them before building (step 1 above, or
`uv run poe proto:kotlin` from the ubo-apple-apps root). A checkout
without the sibling `ubo_app` tree cannot build until they are synced.

> **Note on Kotlin DSL builders.** The protobuf `kotlin` builtin
> (`wiFiUpdateRequestAction { reset = true }` style) is intentionally
> *not* enabled. The `ubo/v1/ubo.proto` schema is large enough that the
> generated Java outer class (`Ubo.java`, ~20 MB) overwhelms the Kotlin
> compiler's symbol resolver when it has to cross-reference tens of
> thousands of generated DSL fragments back into the Java class. The
> hand-written `UboAction → proto` builder will call the Java builders
> directly (`Ubo.WiFiUpdateRequestAction.newBuilder().setReset(true)`),
> which mirrors how the Swift port generates proto values via
> `var p = Ubo_V1_WiFiUpdateRequestAction(); p.reset = true`.

## Roadmap

Landed:

- `:protos` module — Java messages + Kotlin grpc-coroutine stubs.
- Model types under `com.ubopod.ubokotlin.models` (`Key`, `UboColor`,
  `UboAction`, `ViewData` and the seven subtypes, `MenuItemData`,
  `StatusBarData`, `SystemStats`, `UboNotification`, `InputDescription`,
  `AudioTypes`, `DisplayTypes`, `UboEvent`).
- `ReconnectPolicy` + `ConnectionState` + `UboError` sealed-class hierarchy.
- `ProtoToView.unpackViewData` — `google.protobuf.Any` typeUrl dispatch
  for all seven `ViewData` subtypes.
- `ProtoFromAction.toProto` — `UboAction` → `Ubo.Action` translation
  for keypad / audio / display / RGB ring / power / notifications /
  navigation / input / assistant variants.
- `UboConnection` — suspend `connect` / `disconnect` /
  `dispatchAction`, `subscribeToStoreChanges` returning a
  `Flow<Pair<ViewData, StatusBarData?>>`, plus `runWithRetry` for
  exponential-backoff loops.
- `UboClient` — public `StateFlow` surface (`connectionState`,
  `currentView`, `statusBar`, `activeInputs`, `systemStats`,
  `isCameraViewfinderActive`, `cameraPattern`, `lastError`,
  `isRecording`) with all action helpers (keypad, audio, display, RGB
  ring, power, notifications, navigation, input, assistant). The view
  subscription is wired through `runWithRetry`; stats / inputs / camera
  subscriptions are scaffolded with TODOs awaiting their decoders.
- `UboDiscovery.browse(context)` — Android `NsdManager`-based mDNS
  browser emitting `Flow<Set<DiscoveredDevice>>`.
- JUnit 5 tests: `ReconnectPolicyTest` (5), `ViewDataRoundTripTest` (9),
  `ActionBuildTest` (12), `UboClientTest` (4). All 30 green.

Pending:

- `subscribeToActiveInputs` / `subscribeToSystemStats` /
  `subscribeToCameraEvents` on `UboConnection` — needed to wire the
  scaffolded `start*Subscription` methods on `UboClient`.
- `subscribeToDisplayRenderEvents` and `subscribeToFrameStream` — pixel
  / RGB-frame streaming.
- `subscribeToPlaybackEvents` — audio playback events (chimes, TTS).
- Connection-verification probe inside `UboConnection.connect` matching
  Swift's 10×500 ms readiness check (currently relies on OkHttp's
  `usePlaintext` lazy-connect behaviour).
- Action-builder coverage for the long-tail variants (camera, docker,
  file system, IR, settings) — currently the converter handles only
  what the Swift port handles.
