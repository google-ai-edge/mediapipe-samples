# Contribution file inventory

This directory is the **newly added ASL experiment** relative to the upstream `google-ai-edge/mediapipe-samples` repository. No preexisting upstream source files were replaced. The following are new project-specific files and their purposes:

| File | Why it was added |
|---|---|
| `src/asl_project/__init__.py` | Defines the selected 12 ground-truth labels and fixed 26-class model checkpoint. |
| `src/asl_project/inference.py` | Wraps pretrained EfficientNet-B0 26-label inference. |
| `src/asl_project/crop.py` | Detects hand landmarks and constructs automatic hand crops using MediaPipe. |
| `src/asl_project/metrics.py` | Computes accuracy, detection failures, and optional background breakdowns. |
| `scripts/download_data.py` | Downloads original images with VOC annotations. |
| `scripts/prepare_dataset.py` | Produces a reproducible image-level split/manifest. |
| `scripts/evaluate.py` | Runs both inference conditions and saves JSON and per-photo CSV. |
| `scripts/smoke_test.py` | Verifies classifier and cropper on genuine hand-sign photos. |
| `scripts/report_results.py` | Generates the baseline-versus-crop comparison table from measured JSON. |
| `scripts/make_background_sheet.py` | Generates a background annotation CSV for manual review. |
| `scripts/apply_background_sheet.py` | Applies the manually reviewed background labels to the manifest. |
| `tests/test_evaluation.py` | Tests JSON/CSV output consistency with mock inference, not measured accuracy. |
| `tests/test_manifest.py` | Tests extraction of a manifest from VOC-style images/labels. |
| `tests/test_metrics.py` | Tests accuracy and crop-box computation rules. |
| `notebooks/ASL_Ablation_Colab.ipynb` | Reproducible Colab workflow for real baseline and crop evaluations. |
| `requirements.txt` | Declares Python dependencies. |
| `.gitignore` | Prevents accidentally committing datasets, downloaded models, and caches. |
| `results/README.md` | Identifies expected measured output files and guards against estimated numbers. |
| `README.md` | Explains the question, evaluation protocol, setup, limitations, and attribution. |
| `CONTRIBUTIONS.md` | Links to this same file-level inventory. |

**External components:** Google MediaPipe code remains part of the upstream fork; EfficientNet-B0 weights and the public ASL dataset are separately downloaded. The project-specific code was developed as a new experiment, with AI assistance for scaffolding and writing. No mock/unit-test output is presented as a measured accuracy result.
