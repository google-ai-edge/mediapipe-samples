# MediaPipe validation app for Android

One Android app that contains every Android sample of this repository, and
runs the cases of the [validation spec](../.github/scripts/release_validate/spec/README.md)
in them. It is how a MediaPipe release is validated on a phone: the same
cases, the same ground truths and the same verifiers as for the
[Python samples](../.github/scripts/release_validate/README.md), run in the
sample apps as their users run them.

```sh
# Build the app, install it on the connected phone, run every case, write the report
python3 .github/scripts/release_validate/run_android.py --build

# A release candidate from a staging repository
python3 .github/scripts/release_validate/run_android.py --build \
    --mediapipe-version 1.3.0 --mediapipe-repo https://example.com/staging/maven

# Part of the matrix
python3 .github/scripts/release_validate/run_android.py --build --tasks vision/face_detector,text --delegates cpu
```

The exit code is 0 only if every case passed. The report is written to
`release-validation-report-android/` (`index.html`), with a screenshot of the
sample and the log of the app for every case.

## How it works

```
spec/*.json --- spec.py export ---> cases.json + inputs --+
                                                           |  (assets of the app)
examples/*/android/app/src/main  (sources, in place) ------+--> app-debug.apk
adapters/<task>/<Task>Adapter.kt (drives one sample) ------+
                                                                  |  adb install
run_android.py --- am instrument -e case <id> -------------> the phone: starts the MainActivity of the
      |                                                      sample, drives its ViewModel, reads its UI state
      +<-- adb pull: result.json, screenshot.png, device.log <----+
      |
 verify.py, policy.py (shared with the Python samples) --> report
```

- **The samples are not copied.** Each sample is a library module of this build
  (`:samples:<task>`) whose sources are the sources of the sample, where they
  are. The models come from the assets directory of the sample, where its own
  download script puts them (pinned by SHA-256).
- **A case runs in the real sample.** The runner starts the `MainActivity` of
  the sample, gets the ViewModel that the activity created, calls it as the
  screen of the sample does (select the model and the delegate, hand it the
  photo as a `content://` URI, tap "Classify"), and reads the result from the
  UI state the screen is drawn from. What the user would see is on the
  screenshot.
- **One process per case.** `run_android.py` starts every case with
  `am instrument`, which starts the app anew. A crash in native code ends one
  case, not the run, and the log of the process is the log of the case. That
  log is the evidence for the delegate: a GPU case passes only if it shows
  that a GPU delegate was created.
- **The app is self-contained.** The cases and their input files are assets of
  the app, so nothing is pushed to the phone and the phone needs no network.
  The app instruments itself; there is no separate test APK.
- **The phone decides nothing.** The app reports what the sample showed;
  `run_android.py` judges it on the host. It also checks that the app holds
  each case as the spec has it now, so a stale app cannot pass.

## Build

Requirements: a JDK 17 or 21 (`JAVA_HOME`; the JDK of Android Studio works),
the Android SDK (`ANDROID_HOME` or `local.properties`), and `python3` on the
path (it resolves the spec into `cases.json`; it needs no packages). The first
build downloads the models of the samples, about 2.6 GB for all of them.

```sh
cd android-validation
./gradlew :app:assembleDebug                                   # every sample
./gradlew :app:assembleDebug -Ptasks=object_detector,text_classifier
```

| Property | Meaning |
|---|---|
| `-Ptasks=a,b` | Only the samples of these tasks of the spec. Default: all. |
| `-PmediapipeVersion=1.3.0` | Build against this MediaPipe version instead of the one the samples name. |
| `-PmediapipeRepo=<path or URL>` | A Maven repository that has that version. `mavenLocal()` is searched too. |
| `-Pabis=arm64-v8a,x86_64` | The ABIs of the native libraries. Default: `arm64-v8a`. |
| `-Ppython=<exe>` | The Python that runs `spec.py`. Default: `python3`. |

The versions of everything else are those of the samples: the version catalog
of this build is the union of the catalogs of the samples
(`settings.gradle.kts` writes it to `samples/libs.versions.toml`), and the
build fails if two samples disagree.

### What the build generates for a sample

Three things of a sample cannot go into one app as they are.
`build.gradle.kts` generates them into `samples/<task>/generated/` (ignored by
git) every time Gradle configures the build:

| | Why | What is generated |
|---|---|---|
| Resources | All modules of an app share one resource namespace, and the samples use the same names for different things (`app_name`, `settings_title`, ...). | A copy in which the strings and resource files of the sample have the prefix `<task>_`. |
| `R` | The sources refer to `R.string.app_name` of their own package. | `SampleResources.kt` in that package: a `typealias R` that maps each name to the prefixed resource. The sources need no change. |
| Manifest | An app has one launcher activity and one `<application>`. | The manifest of the sample without its launcher intent filter; its activities keep their theme and label, and are not exported. |

The build fails, and says what to do, if a sample declares a kind of resource
it does not know how to prefix, if two samples define a style of the same name
differently, if two samples bundle different files under the same asset name,
or if a sample declares a dependency in a form the build cannot read.

### What differs from the sample's own app

The sources and models are the same; the process around them is not:

- The manifest is the union of the manifests of the samples. Every sample
  therefore has the permissions and `<uses-native-library>` entries of all of
  them (two samples declare the vendor OpenCL libraries, so the GPU delegate
  can load OpenCL in every sample here), and `largeHeap`.
- Gradle resolves one version of every dependency for the whole app. Where
  samples pull different versions of a transitive dependency, the highest
  wins.
- Image Segmentation has two Android apps that share one Kotlin package. Only
  one of them, `category_mask`, can be part of the app.
- The app does not ask for the camera or the microphone. The validation uses
  what a sample can do with a file.

## Run

`run_android.py` does all of it. By hand:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
PKG=com.google.mediapipe.examples.validation
RUNNER=$PKG/$PKG.runner.ValidationInstrumentation

# One case. The first line of the output names the directory with the results.
adb shell am instrument -w -e case vision__object_detector__efficientdet_lite0_float32__image__CPU $RUNNER

# Every case of the app in one process (no isolation between cases)
adb shell am instrument -w $RUNNER

adb pull /sdcard/Android/data/$PKG/files/validation/out
```

For every case the app writes `<id>.json` (what the sample showed: `runs`, or
the `error` the sample showed its user, or `runnerError` if the case could not
be run as the spec defines it), `<id>.png` (the screen) and `<id>.log` (logcat
of the process during the case). The screen of the phone must be unlocked: an
activity composes its screen, and creates its ViewModel, only when it is
visible. The runner wakes the phone and dismisses a keyguard without a
credential.

### In a device lab

The lab needs one APK and a host that can run `adb`: install the app, run
`am instrument` once per case, pull the results directory, and judge it with
`run_android.py` (`--apk`, `--serial`) or, if the host only collects files,
with `verify_observations.py` on the `observations.json` that `run_android.py`
writes. The verdict is the exit code of that script, not the exit code of the
instrumentation: the phone reports observations, the host decides.

## Open a sample by hand

The launcher activity of the app lists the samples; an entry opens the
activity of that sample, as its own app does. This is for looking at a sample
inside this build. The validation does not use it.

## Adapters

`adapters/<task>/<Task>Adapter.kt` drives the sample of one task. It is
compiled into the module of that sample, so it sees its `internal`
declarations. It is the Android counterpart of `python_adapter.py` and the
observers of `worker.py`: the two things a platform has of its own, how it
drives its sample and how it reads the result. It has no checks.

An adapter (see [`ObjectDetectorAdapter.kt`](adapters/object_detector/ObjectDetectorAdapter.kt)):

1. Maps the model of the case to the model setting of the sample, and names
   the asset the sample loads for it (`sample.usesModelAsset`). The runner
   fails the case unless that file is the model the spec pins, whatever its
   name in the sample.
2. Applies the delegate and every option of the case through the ViewModel.
   The runner fails a case whose adapter did not read every option, and
   `requireApplied` fails it if the sample did not take a value (samples clamp
   settings). A case never runs with other values than the spec states.
3. Feeds each step as a user would, waits for the UI state to hold the result
   or an error, and returns the result in the form the spec defines
   ("Observations" in the spec README). An error the sample shows its user is
   the outcome of the case (`SampleError`).

An adapter reports only what the sample holds in its UI state. It does not
call MediaPipe, or the helper of the sample, to compute what the sample did
not.

### Adding a sample

Add `"android": "examples/<sample>/android"` to `samples` of the task in the
spec, and write its adapter. `self_test.py` fails while a task has Android
cases and no adapter that knows its models.

## Files

| | |
|---|---|
| `settings.gradle.kts` | Which samples are part of the build, and with which versions. |
| `build.gradle.kts` | Turns each sample into a library module; generates its resources, `R` and manifest. |
| `app/` | The app: the list of samples, the cases and inputs as assets, the instrumentation entry in the manifest. |
| `runner/` | Runs a case: `ValidationInstrumentation`, `CaseRunner`, and what adapters build on (`SampleSession`, `ValidationCase`). |
| `adapters/<task>/` | The adapter of each sample. |
| `samples/` | Generated. One module directory per sample, and the merged version catalog. |
