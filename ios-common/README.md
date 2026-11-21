# MediaPipe Sample Common (iOS)

Shared local Swift Package (`MediaPipeSampleCommon`) used across the modernized
MediaPipe iOS sample apps (iOS 18.0+, Swift 6).

## Products

- **`MediaPipeSampleUI`**:
  - `MediaPipeTheme.swift` — Adaptive Light/Dark MediaPipe teal palette
    (`#007F8B` / `#12B5CB`), vector `MediaPipeLogoMark`, elevated card styling,
    and adaptive glass pills (native Liquid Glass on iOS 26+, thin material on
    iOS 18).
  - `MediaPipeHeader.swift` — Branded navigation title, compact live status /
    latency / delegate strip, and consistent settings toolbar button.
  - `MediaPipeComponents.swift` — Reusable error banner with recovery action,
    telemetry metric tiles, capsule preset/mode chip bar, primary action button,
    labeled multiline input card, and grouped Settings sheet wrapper.
  - `SampleModelLocator.swift` — Consistent model path resolver across
    standalone app bundles, developer Documents sideloading, unit tests, and
    multi-sample validation hosts (with 0-byte stub detection).
- **`MediaPipeSampleTesting`**:
  - `EmbeddingMetrics.swift` — Shared cosine similarity, L2 norm, and finiteness
    helpers for embedding tests.

## Running unit tests

```bash
swift test --package-path ios-common
```
