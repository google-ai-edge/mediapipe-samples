# Release validation of the Python samples

One command that answers: do the MediaPipe Tasks Python samples of this repo
work with a given MediaPipe release, on the CPU and on the GPU of this Mac?

```sh
python3 .github/scripts/release_validate/run_local.py --mediapipe mediapipe==1.2.3
```

It runs every sample in `examples/*/python/*.py` across its supported models and
modes on both delegates, and checks both what the sample **returned** and every
output file (`.png` masks/annotations/figures and `.wav` audio) it **wrote to
disk** against a ground truth: the dog is found where the dog is, the sentence
is negative, the clip is speech, and `speech.wav` decodes cleanly and is heard
as the input sentence by speech recognition. A sample that runs without an error
but returns or writes a wrong result fails. The exit code is 0 only if every
case passed.

This is the Python counterpart of
[`release_validate` in mediapipe-samples-web](https://github.com/google-ai-edge/mediapipe-samples-web/tree/main/.github/scripts/release_validate),
and follows its design.

What is run and what is expected is not written in Python: it is defined once,
in JSON, in [`spec/`](spec/README.md), for every platform. See
[One spec for every platform](#one-spec-for-every-platform).

## Requirements

- macOS on Apple silicon (CPU + Metal GPU), or Windows / Linux with `--cpu-only`.
  The GPU cases are confirmed through the log of the Metal delegate, see
  [GPU cases](#gpu-cases).
- Python 3.12, the version the dependency pins were resolved for. With another
  version the run stops before it installs anything; see `--python`.
- Network access on the first run, to install the packages and to download
  the models and inputs: about 2.5 GB, or about 450 MB with
  `--small-models-only`. Later runs reuse both.

`run_local.py` itself only needs the Python standard library. Nothing is
installed outside of `--work-dir`.

## Usage

```sh
# The latest release on PyPI
python3 .github/scripts/release_validate/run_local.py

# A specific release, a release candidate, or a wheel built locally
python3 .github/scripts/release_validate/run_local.py --mediapipe mediapipe==1.2.3
python3 .github/scripts/release_validate/run_local.py --mediapipe mediapipe==1.3.0rc1 --pip-arg=--pre
python3 .github/scripts/release_validate/run_local.py --mediapipe ~/Downloads/mediapipe-1.3.0-py3-none-macosx_11_0_arm64.whl

# CPU-only validation (for example on Windows or headless CI)
python .github/scripts/release_validate/run_local.py --cpu-only

# Part of the matrix
python3 .github/scripts/release_validate/run_local.py --tasks vision/face_detector,audio --delegates cpu
python3 .github/scripts/release_validate/run_local.py --list
```

| Option | Meaning |
|---|---|
| `--mediapipe SPEC` | What pip installs as the MediaPipe under test: a requirement, or the path or URL of a wheel. Default: the latest release. |
| `--pip-arg ARG` | Extra argument for that `pip install`, repeatable (`--pip-arg=--pre`, `--pip-arg=--extra-index-url=...`). |
| `--tasks LIST` | Comma-separated domains (`vision`), tasks (`face_detector`) or both (`text/text_embedder`). Default: all. |
| `--delegates LIST` | `cpu`, `gpu` or `cpu,gpu` (default). |
| `--cpu-only` | Run only the CPU delegate cases (equivalent to `--delegates cpu`; used on Windows). |
| `--small-models-only` | Leave out the cases of the large models: speech recognition, speech synthesis, and the embedding models of the retrieval and decision tasks. |
| `--report-dir DIR` | Default: `release-validation-report` in the repo. It is deleted and written anew by every run. |
| `--work-dir DIR` | Python environment and downloaded files. Default: `.release_validation` in the repo. |
| `--python EXE` | The Python that creates the environment. Default: the one that runs `run_local.py`. |
| `--fresh-venv` | Delete the Python environment and create it again. Use it when a wheel file changed without a new version number. |
| `--no-constraints` | Do not pin the dependencies (see [Reproducibility](#reproducibility)). |
| `--timeout SECONDS` | Time limit of one case. Default: 600. |
| `--allow-unconfirmed-gpu` | Do not fail the run for `GPU_UNCONFIRMED` cases. |
| `--baseline STATUS_JSON` | `status.json` of an earlier run; the report then says per case whether the output is identical. |
| `--list` | Print the cases and exit. |

## What runs

A case is one task, model, mode and delegate, for example
`vision__face_detector__blaze_face_short_range__image__GPU`. The matrix has 162
cases for the 22 samples, each on both delegates. The task files in `spec/`
define them, and `--list` prints them.

| Domain | Tasks | Cases |
|---|---|---|
| vision | object detector (5 models, image + video), face detector (2 models, image + video), face / hand / holistic landmarker (image + video), pose landmarker (3 models, image + video), image classifier (2 models, image + video), gesture recognizer (image + video), interactive segmenter (point, negative, lasso), image segmenter (5 models: category, confidence, video), image embedder (2 models, image + video) | 108 |
| audio | audio classifier (file + stream), audio transcriber (file + stream), text to speech (4 models, speak + stream) | 24 |
| text | text classifier (2 models), language detector, text embedder (3 models), text proofreader, text summarizer (2 modes) | 18 |
| retrieval | universal embedder (text-text, text-image, image-image), semantic retriever | 8 |
| decision | decision maker (schema + individual) | 4 |

128 of the cases use small models. The other 34 (audio transcriber, text to
speech, EmbeddingGemma text embedder, retrieval, decision) use models of 90 to
615 MB and are left out with `--small-models-only`.

For every case, `run_local.py` starts a new Python process (`worker.py`) that

1. imports the sample and calls its command line entry point, `main(argv)`, with
   the model, the input, the options, the delegate and an output path, exactly
   as `python sample.py --model ... --delegate gpu` would (`python_adapter.py`
   derives these arguments from the steps and options of the case in the spec);
2. turns the MediaPipe result that `main()` returned into plain JSON, the
   observation.

Back in `run_local.py`, `verify.py` compares that observation with the
expectation of the case from the spec. A fresh process per case means that a crash in
native code fails one case, not the run, and that no state carries over from one
case to the next.

Every task has a notebook in `examples/*/python/`, and the samples use the
models and inputs of those notebooks, with two exceptions: the photos of the
face detector and pose landmarker notebooks are hosted on servers that refuse
Python's downloader, so those two samples use MediaPipe's own test images
`portrait.jpg` and `pose.jpg` instead. Text to Speech also validates the CPU
variant of Kokoro plus PocketTTS and Phonon from Hugging Face, matching the web
suite.

## Statuses

| Status | Meaning | Fails the run |
|---|---|---|
| `PASS` | The sample ran and every output check passed. On the GPU: and the log shows a GPU delegate. | no |
| `FAIL` | The sample raised an error, crashed, timed out, or returned a wrong result. | yes |
| `GPU_UNCONFIRMED` | The output is correct, but the native log has no sign that a GPU delegate was created. | yes, unless `--allow-unconfirmed-gpu` |
| `UNSUPPORTED` | MediaPipe rejects the combination with the error listed under `unsupported` in the spec of the task. | no |
| `UNVERIFIED` | The sample ran, but no expectation is defined for the case. | yes |

`PASS` is the only status that says "this works". `UNSUPPORTED` is never counted
as a pass; it is listed separately in the summary.

### Known unsupported combinations

The `unsupported` list of a task in `spec/` names the combinations that
MediaPipe itself declares unsupported, with the text of the error it raises:

- Text Proofreader, Text Summarizer, PocketTTS and Phonon on the GPU:
  `Only CPU delegate is supported.`
- The int8 object detection model on the macOS GPU:
  `Quantized tensors are not supported by the macOS/iOS GPU delegate.` Object
  detection on the GPU is validated with the float models instead.
- Text Classifier, Language Detector and Text Embedder on the macOS GPU:
  `not supported by the macOS/iOS GPU delegate (only float32 input/output tensors are supported).`
- Audio Classifier (YAMNet) on the macOS GPU:
  `Model contains operations or configurations not supported by the GPU delegate:`

Such a case is `UNSUPPORTED` only while it fails with exactly that error. If it
fails in any other way it is a `FAIL`. If it starts to work, it is checked like
every other case, and the report asks for the entry to be removed.

The list is for limitations that the library states in an error message. A
crash, a low-level error or a wrong result is a bug and stays a `FAIL`, however
long it has been there.

### GPU cases

MediaPipe has no API that tells which delegate ran a model, and a GPU request
that quietly runs on the CPU would return a correct result. So for every GPU
case the native log of the process is searched for the lines the Metal delegate
writes when it is created (`GPU_EVIDENCE` in `policy.py`). Without such a line
the case is `GPU_UNCONFIRMED`, not `PASS`. Conversely, a CPU case whose log
shows a GPU delegate fails.

If a release words those log lines differently, the GPU cases turn
`GPU_UNCONFIRMED` (never silently green): look at the `native.log` of a case and
update `GPU_EVIDENCE`. On other systems than macOS the lines are different, and
are not listed yet.

## Ground truths

The tests in `spec/` state what is true about each input, with tolerances wide
enough to hold for any correct release. The same expectation applies to both
delegates, and to every platform. The `note` of a test says how its ground
truth was established.

- Positions (a nose tip, a wrist, a box) were marked by eye on the photos and
  checked by drawing each mark and its tolerance back onto the photo. They were
  not copied from the output of a release.
- Where a value is a baseline measured from a release, the comment says so and
  the check is named "baseline". There is one: the similarity of the two
  sentences of the Text Embedder notebook. That model does not order sentences
  by meaning, so there is nothing independent to check it against.
- The transcript of the speech clip is the reference of the web release
  validation, which a person confirmed by listening. It was not confirmed
  again here.
- Synthesized speech is checked as audio (length, level, valid samples, word
  timestamps), and then given to the Audio Transcriber on the CPU: what it
  hears must be the sentence that was synthesized. Without that round trip,
  noise of the right length would pass.
- There are no golden files: no output of a release is stored and compared.
  See `--baseline` under [Reproducibility](#reproducibility) for comparing two
  runs.

`self_test.py` runs before every validation, without MediaPipe. For each case
it builds a correct output from the expectation, which must pass, and then
damages it in the ways a broken release would (a missed object, a mirrored
result, a mask of the wrong format, a score that is not a number), and every
damaged output must fail. It also checks the spec itself, and that the Python
samples can do everything the spec asks of them.

## One spec for every platform

The cases, their inputs and their ground truths are in [`spec/`](spec/README.md)
as JSON, like the spec tests of the Firebase SDKs: written once, run on every
platform. A test there names a photo, the task options and what must be found
in the photo; it does not name a command line or a Python class.

```
spec/*.json  --+-- python_adapter.py -> examples/*/python -> worker.py --+
  (one source  |                                                         +--> observations
   of truth)   +-- spec.py export ----> a runner for the Android or -----+        |
                                         iOS apps (not written yet)        verify.py, policy.py
                                                                                  |
                                                                               report
```

Each platform has two things of its own: how it drives its sample, and how it
reads the result. For the Python samples these are `python_adapter.py` and the
observers of `worker.py`. Everything else is shared: the spec, the verifiers
(`verify.py`), the rules for a status (`policy.py`) and the report.

```sh
# Is the spec consistent?
python3 .github/scripts/release_validate/spec.py check

# The cases of a platform as one flat file, for its runner
python3 .github/scripts/release_validate/spec.py export --platform android --out cases.json

# Judge what that runner observed, and write the report
python3 .github/scripts/release_validate/verify_observations.py observations.json
```

There is no Android or iOS runner yet; `verify_observations.py` has so far only
judged observations of the Python samples. What differs on those platforms is
already in the spec: the files that the Python samples write to disk
(`annotated.png`, `mask.png`, `speech.wav`) are checked for Python only, and
the `unsupported` entries, which were observed with the Python API, do not
excuse a failure of an app.

## Reproducibility

Two runs of the same release validate the same thing:

- **Packages.** `constraints.txt` pins every package except MediaPipe. If a
  release needs a version the pins exclude, the installation fails and says so;
  update the file (its header explains how), or pass `--no-constraints` for a
  run that is explicitly not reproducible.
- **Models and inputs.** `spec/assets.json` pins every file by SHA-256 and byte
  size. A file whose content changed upstream fails the cases that use it
  instead of being validated silently. Every Hugging Face model in it is
  downloaded via `huggingface_hub.hf_hub_download` at an immutable commit
  `revision`.
- **Processes.** Each case runs in a fresh process with the same arguments.
  Files that a task writes next to its model (compiled caches) are deleted
  before every case, so no case depends on what ran before it, or on a run with
  another MediaPipe version.
- **Record.** The report lists the MediaPipe version, the Python version, the
  machine, the commit of this repo, every installed package (`pip-freeze.txt`)
  and the checksum of every input.

To check that a run reproduces, or to see what a new release changed, pass the
`status.json` of an earlier run:

```sh
cp release-validation-report/status.json /tmp/first.json
python3 .github/scripts/release_validate/run_local.py --baseline /tmp/first.json
```

The report then states for every case whether its output is identical to the
earlier run (numbers are compared to four decimals; timings are ignored). This
comparison is information; it does not change the status of a case.

## The report

`release-validation-report/index.html` has two views (matching
`mediapipe-samples-web`):

- **Table**: summary cards, status/domain/delegate filters, output check
  details, inline output image thumbnails and audio players, and a `Review →`
  button on every row.
- **Review cases one by one** (`#review`): a two-column view showing the case
  description, inputs, output checks, GPU delegate log evidence and raw JSON on
  the left, and the sample's full-size output images (with `1`–`9` image tabs
  when a sample writes both an annotated image and a mask/chart), synthesized
  `.wav` audio player + ASR transcript, and console output on the right. Step
  through cases with `Next` / `Previous`, `←` / `→` arrow keys, or `Space`
  (`Esc` returns to the table).

Cases that need attention come first whenever any case failed. `report.md` is
the summary table as Markdown and `status.json` has everything, including the
complete observed output of every case. `cases/<id>/` holds the sample's printed
output, the native log and the exact arguments.

## Files

| File | Purpose |
|---|---|
| `spec/` | **The source of truth, in JSON:** tasks, models, modes, tests with their inputs and ground truths, the known unsupported combinations, and the pinned files. See [`spec/README.md`](spec/README.md). |
| `spec.py` | Reads the spec, checks it, builds the matrix, and exports the cases of a platform. |
| `run_local.py` | Entry point for the Python samples: environment, downloads, one process per case, report. |
| `python_adapter.py` | How a case of the spec becomes the command line of a Python sample. |
| `worker.py` | Runs one case inside the validation environment, and observes its result. |
| `verify.py` | Pure functions that check an observation against an expectation. For every platform. |
| `policy.py` | Turns the outcome of a case into its status; GPU evidence. |
| `verify_observations.py` | Judges the observations of the runner of another platform, and writes the report. |
| `assets.py` | Downloads the models and inputs, and checks them against their checksums. |
| `report.py` | Writes `status.json`, `report.md` and `index.html`. |
| `self_test.py` | Tests of the above; run before every validation. |
| `constraints.txt` | Pinned package versions. |

## Adding a sample

1. Write the sample like the others: a `run(...)` function that returns the
   MediaPipe result, and `main(argv=None)` that parses `--model`, `--delegate`
   and the inputs, and returns what `run()` returned.
2. Add its model and inputs to `spec/assets.json`, and the task to `spec/`:
   a file `<domain>/<task>.json` with its models, modes and tests, listed in
   `spec/index.json`. `spec/README.md` describes the format, and
   `spec.py check` says what is missing.
3. In `python_adapter.py`, name the observer of the task, and the command line
   argument of the sample for each option and each file of the spec.
4. If the result needs a new shape, add an observer to `worker.py` and a
   verifier to `verify.py`. `self_test.py` fails until the new verifier is
   covered by a correct and by damaged outputs.

To change a ground truth, add a model or add a test, edit the JSON only; see
"Changing the spec" in `spec/README.md`.
