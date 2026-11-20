# The validation spec

These JSON files say what the release validation runs and what it expects:
which models and modes of every task, what each test feeds to the task, and
what must be true about the result. They are the one source of truth for every
platform. Nothing in them is specific to the sample of one platform.

| File | Content |
|---|---|
| `index.json` | The tasks in the order of the matrix, the delegates, and the platforms. |
| `assets.json` | Every model and input file, pinned by SHA-256 and size. |
| `<domain>/<task>.json` | One task: its models, its modes and its tests. |

`../spec.py` reads them. Check the spec after every change:

```sh
python3 .github/scripts/release_validate/spec.py check
```

## Who runs it

- **Python samples** (`examples/*/python`): `run_local.py`. `python_adapter.py`
  turns the steps and options of a case into the command line of the sample.
- **Android and iOS apps**: there is no runner for them yet. The spec lists
  their sample directories, and this is the path a runner takes:

  ```sh
  # 1. The cases of the platform as one flat file: options, steps and
  #    expectation of every case resolved, and the pinned files it needs.
  python3 .github/scripts/release_validate/spec.py export --platform android --out cases.json

  # 2. The runner drives the apps through these cases and writes what each
  #    returned (see "Observations") to observations.json.

  # 3. The same verifiers, rules and report as for the Python samples.
  python3 .github/scripts/release_validate/verify_observations.py observations.json
  ```

  So a runner has two things of its own: how it drives its sample (the
  counterpart of `python_adapter.py`) and how it reads the result (the
  counterpart of the observers in `worker.py`). It needs no checks.

A case is one task, model, mode and delegate. Its id, for example
`vision__face_detector__blaze_face_short_range__image__GPU`, is the same on
every platform.

## A task

```json
{
  "domain": "vision",
  "task": "object_detector",
  "heading": "Object Detection",
  "samples": {
    "python": "examples/object_detection/python/object_detector.py",
    "android": "examples/object_detection/android",
    "ios": "examples/object_detection/ios"
  },
  "models": [
    {"id": "efficientdet_lite0_int8", "asset": "efficientdet_lite0_int8.tflite"},
    {"id": "efficientdet_lite0_float32", "asset": "efficientdet_lite0_float32.tflite"}
  ],
  "modes": [
    {"id": "image", "options": {"runningMode": "IMAGE"}},
    {"id": "video", "options": {"runningMode": "VIDEO"}}
  ],
  "unsupported": [
    {
      "platform": "python", "system": "Darwin", "model": "efficientdet_lite0_int8", "delegate": "GPU",
      "error": "Quantized tensors are not supported by the macOS/iOS GPU delegate.",
      "reason": "The macOS GPU delegate rejects quantized (int8) models."
    }
  ],
  "tests": [
    {
      "about": "The photo shows a cat and a puppy. Both must be detected, each with a box around it, and nothing else.",
      "note": "The boxes were marked by eye; 0.5 IoU is the usual detection criterion.",
      "options": {"scoreThreshold": 0.5},
      "files": {"annotated": "annotated.png"},
      "steps": [{"key": "cat_and_dog.jpg", "image": "cat_and_dog.jpg", "caption": "A cat (left) and a puppy (right)"}],
      "expect": {
        "type": "detections",
        "objects": [{"label": "cat", "minScore": 0.5, "box": {"x": 0.12, "y": 0.4, "width": 0.38, "height": 0.45}, "minIoU": 0.5}],
        "onlyLabels": ["cat", "dog"]
      }
    }
  ]
}
```

| Key | Meaning |
|---|---|
| `samples` | Where the sample of each platform is. A task has cases on the platforms listed here. |
| `models` | `asset` names a model of `assets.json`. `large: true` leaves the model out with `--small-models-only`. The first model is the one the samples download by default. |
| `modes` | The ways the task is run, each with the options that make it that mode. |
| `unsupported` | Combinations MediaPipe itself rejects, with the text of its error. A case is `UNSUPPORTED` only while it fails with exactly that error. An entry applies to a `delegate`, and optionally to one `platform`, `model` and `system` (as Python's `platform.system()` names it). The entries so far were observed with the Python API. |
| `tests` | The ground truths. Every model in every mode is covered by exactly one test. |

`note`, anywhere, is a comment: JSON has none. Notes say how a ground truth was
established; they are dropped when the spec is read.

## A test

| Key | Meaning |
|---|---|
| `models`, `modes` | The model ids and mode ids the test covers. Default: all of the task. |
| `about` | What must be true of the result, for the report. No word about files or command lines. |
| `options` | Task options that hold in every mode of the test. |
| `steps` | What is fed to the task. Each step yields one observation, under its `key`. |
| `expect` | What must be true about the result; `type` names the verifier (see "Expectations"). |
| `files` | Files the sample writes once for all steps, by role (see "Files"). |
| `inputs` | What the report shows as inputs. Default: the image, audio or text of each step with its `caption`. |
| `inMode` | Per mode id, what is different in that mode: `about` replaces, `options` and `expect` are merged key by key. |
| `name` | A name for the test, where one is useful. |
| `probes`, `index`, `questions`, `listenWith` | Data of one task that all steps share; see the task files. |

The options of a case are those of its mode, then of its test, then of
`inMode`; a later one wins.

### Steps

A step has a `key`, an optional `caption` for the report, and inputs:

| Input | Meaning |
|---|---|
| `image`, `audio` | The name of an input of `assets.json`. |
| `text` | A text. |
| `pair` | Two inputs to compare, each `{"image": NAME}` or `{"text": TEXT}`. |
| `query` | A text to search the `index` of the test for. |
| `point`, `lasso`, `negativeStroke` | A click `[x, y]`, a box `[x0, y0, x1, y1]` to lasso, a stroke `[x0, y0, x1, y1]` to exclude, as fractions of the image. The option `select` says whether `point` or `lasso` selects the object. |

`expect` in a step is what must be true of the result of that step, and
`files` what the sample writes for it.

### Options

Options have the names of the MediaPipe task options, in camel case:
`runningMode` (`IMAGE`, `VIDEO`, `AUDIO_CLIPS`, `AUDIO_STREAM`),
`scoreThreshold`, `maxResults`, `numFaces`, `numHands`,
`outputFaceBlendshapes`, `outputFacialTransformationMatrixes`,
`outputSegmentationMasks`, `outputSegmentationMask`, `l2Normalize`, `quantize`,
`limit`, `minSimilarity`. A few describe how the sample is used:
`confidenceCategory` (the category whose confidence mask is checked), `stream`
(feed or synthesize piece by piece), `summaryMode` (`KEYPOINTS`, `TLDR`),
`evaluate` (`schema`: all questions in one call; `individual`: one call each),
`select` (see above).

A runner must give its sample every option of a case, or know that the sample
always uses that value. It must not run a case with another value. For the
Python samples `python_adapter.py` states both, and `self_test.py` checks it.

What a mode means beyond its options is up to the sample. For example, in
`VIDEO` mode most of the Python samples feed the image as three consecutive
frames and return the result of the last.

## Expectations

`expect.type` names the verifier in `../verify.py`. The verifier is given each
step as a whole: its inputs (a check may need the text that was proofread)
together with its `expect`. For the types that observe several steps these
become the entries of `runs`, in the order of the steps; the other types have
one step, whose inputs join the `expect` of the test.

| Type | Steps | Checked |
|---|---|---|
| `detections` | one | Each object is found with its label, a score, and a box that overlaps the true one; nothing else is found. |
| `faces` | one | The number of faces, the score, the eyes and nose in place, the size of the box. |
| `landmarks` | several | Per group (faces, hands, poses), the instances, their landmark count, the named points in place, plus `equals`, `ranges`, `fields` and a `mask`. |
| `classification` | several | The top label is one of `labels`, with a score. |
| `similarity` | several | Each cosine similarity lies in its `range`; `ordered` pairs are told apart. |
| `textLabels` | several | The label of each text is the top one (or `"match": "present"`: among the results). |
| `audioClassification` | one | Every full window of the clip gets the label, at the right timestamps. |
| `transcript` | one | The transcript matches `reference`, by word error rate; the segments are final and in order. |
| `speech` | one | Valid audio of a plausible length and level, word timestamps, and speech recognition hears the text. |
| `retrieval` | several | The best match of each query is the record `top`. |
| `decisions` | several | Each text gets its answers, with probabilities that agree with them. |
| `segmentation` | one | Each category covers its share of the image, where it is; optionally a confidence mask. |
| `selection` | several | The selected region: its share, centre and bounds, and which `probes` it contains. |
| `proofreading` | several | The text is fixed, and the corrections lead from the input to the result. |
| `summary` | one | Shorter than the text, in the right form, about the text. |

Positions are fractions of the image width and height. Tolerances are wide
enough to hold for any correct release and for both delegates; the same
expectation applies on every platform.

### Files

Some samples write their result to disk: an annotated image, a mask, a `.wav`
file. `files` names them by role: `annotated`, `figure`, `mask`, `blurred`,
`overlay`, `audio`. On a platform with `"files": true` in `index.json` (the
Python samples) the runner tells the sample to write them, and each is checked
against the result: the mask on disk is the mask that was returned, the
annotations were drawn, the audio file holds the samples. On the other
platforms the files are no part of a case, and the checks of the result are the
same.

## Observations

A runner reports, for every case, what the task returned:

```json
{"id": "vision__face_detector__blaze_face_short_range__image__CPU",
 "runs": [{"key": "portrait.jpg", "data": {"imageWidth": 820, "imageHeight": 1024, "detections": [
   {"score": 0.92, "box": {"x": 283, "y": 115, "width": 234, "height": 234}, "keypoints": [[0.45, 0.17], [0.55, 0.17]]}]}}],
 "error": null,
 "gpuEvidence": []}
```

`runs` has one entry per step, with the `key` of the step. `error` is the
message of the error that ended the case. `gpuEvidence` holds the lines of the
native log that show a GPU delegate was created; a GPU case without them is
`GPU_UNCONFIRMED`, not `PASS`, because a request for the GPU that quietly runs
on the CPU returns a correct result too.

The form of `data` follows from the type of the expectation. The observers in
`../worker.py` are the reference; `correct_runs()` in `../self_test.py` builds
a correct one for every case.

| Type | `data` |
|---|---|
| `detections` | `imageWidth`, `imageHeight`, `detections`: `[{label, score, box: {x, y, width, height}}]`, the box in pixels. |
| `faces` | `imageWidth`, `imageHeight`, `detections`: `[{score, box, keypoints: [[x, y], ...]}]`, keypoints as fractions. |
| `landmarks` | Under the `key` of each group of the expectation, a list of instances `{landmarks: [[x, y], ...]}`, with a `score` and the `fields` the expectation names (for example `gesture`). The keys of `equals` hold their values; `values` holds the numbers of `ranges` by key. `mask`, where expected: see below. |
| `classification`, `textLabels` | `categories`: `[{label, score}]`. |
| `similarity` | `similarity`: the cosine similarity of the pair. |
| `audioClassification` | `results`: `[{timestampMs, categories: [{label, score}]}]`, one per window. |
| `transcript` | `clipMs`, `segments`: `[{transcript, isFinal, fromMs, toMs}]`. |
| `speech` | `pieces`: `[{chunkIndex, isFinal, samples, sampleRateHz, dtype: "float32", ndim: 1}]`, and over all pieces `sampleRateHz`, `durationSec`, `rms`, `peak`, `nonFinite`; `wordTimestamps`: `[{word, startSec, endSec}]`; `heard`: what the model of `listenWith` transcribes from the audio, on the CPU (or `heardError`). |
| `retrieval` | `results`: `[{id, score}]`, best first. |
| `decisions` | `urgent`: `{value, probabilityTrue}`, `department`: `{selected, probabilities: {choice: p}}`, `severity`: `{expectedScore, probabilities: [p, ...]}`. |
| `segmentation` | `imageWidth`, `imageHeight`, `maskWidth`, `maskHeight`, `labels` (the category names of the model), `classes`: `[{index, label, fraction, centroid: {x, y}}]` for every category in the mask, `confidence`: the mask of `confidenceCategory` (see below) with `category`, `index` and `count`, or null. |
| `selection` | `imageWidth`, `imageHeight`, `mask`: `{width, height, channels, min, max, nonFinite}`, and of the pixels above 0.5: `fraction`, `centroid: {x, y}`, `bounds: {minX, maxX, minY, maxY}`; `probes`: for every probe of the test, whether the mask selects that point. |
| `proofreading` | `proofreadText`, `corrections`: `[{type, text}]` with the types `SAME`, `INSERTION`, `DELETION`. |
| `summary` | `summary`. |

A confidence mask is reported as `{width, height, channels, min, max,
nonFinite, fraction, centroid}`: its format and value range, and the share and
centre of the pixels above 0.5. The mask of a landmark task also has
`imageWidth` and `imageHeight` (or `imageAspect`, if the sample scaled the
image), to compare its size with.

Where a sample writes files, the observation describes each one too
(`annotatedFile`, `figureFile`, `maskFile`, `blurFile`, `overlayFile`,
`wavFile`, and `file` of a mask); see `worker.py`.

## Changing the spec

- **A ground truth**: edit `expect` (or the `expect` of the step), and say in
  `note` how the new value was established. A value copied from the output of
  a release is a baseline, not a truth; the suite has one, and names it so.
- **A model**: add it to `assets.json` (with its checksum) and to `models` of
  the task. If the tests of the task do not hold for it, give it a test of its
  own with `models`.
- **A mode or a test**: add the mode to `modes`, and make sure a test covers
  it; `spec.py check` says if none or several do.
- **A task**: add `<domain>/<task>.json` and list it in `index.json`.
- **An option or input the samples do not have yet**: teach each runner how to
  give it to its sample (for Python: `python_adapter.py`).
- **A new kind of result**: add a verifier to `verify.py` and its type to
  `spec.py`, and an observer to each runner. `self_test.py` fails until the
  verifier is covered by a correct and by damaged outputs.

`self_test.py` runs before every validation. It checks the spec, that the
Python samples can do what it asks, and that every expectation accepts a
correct result and rejects a damaged one, with and without the files.
