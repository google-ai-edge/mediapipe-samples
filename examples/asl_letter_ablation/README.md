# ASL fingerspelling — automatic hand-crop ablation

This experiment extends the official [MediaPipe samples](https://github.com/google-ai-edge/mediapipe-samples) fork with a still-image classification study. **All upstream sample files remain unchanged.** The new code lives under `examples/asl_letter_ablation/`.

## Research question

How does adding MediaPipe automatic hand cropping change top-1 letter classification accuracy for a fixed pretrained ASL EfficientNet-B0 classifier on the same public validation images?

| Condition | Input to classifier | Output |
|---|---|---|
| Full frame (baseline) | Original image | Top-1 class from 26 output labels |
| Automatic hand crop | MediaPipe hand bounding box with 25% margin | Top-1 class from the same 26 output labels |

We evaluate ground-truth letters A, B, C, D, E, F, L, O, R, S, V, Y. A predicted letter outside this 12-letter subset is counted as wrong; detection failures in the crop condition also count as wrong. The model weights, image processor, and image list are held fixed.

**Primary metric:** top-1 accuracy (%) = correct / evaluated images × 100. Secondary: hand-detection failure rate and, if manually annotated, accuracy by plain/cluttered background.

## External model and data

- **Classifier:** [abdollahhh/asl-sign-language-efficientnet-b0](https://huggingface.co/abdollahhh/asl-sign-language-efficientnet-b0), fixed pretrained 26-letter EfficientNet-B0 checkpoint.
- **Hand detector:** [MediaPipe Hand Landmarker](https://developers.google.com/mediapipe/solutions/vision/hand_landmarker), single-image inference. No ground-truth bounding box is used for inference.
- **Data:** [University of Waikato ASL Letters](https://datasets.cms.waikato.ac.nz/ufdl/american-sign-language-letters/) in Pascal VOC format (original images plus annotations).

**Limitations:** This public dataset does not supply verified signer IDs or independently held-out background settings. The train/validation/test image split therefore does **not** support claims about unseen signers or independently novel backgrounds. Accuracy on the public validation set must not be described as such. Background metrics are omitted until photographs have been annotated by observation. No training or fine-tuning is performed.

## Run in Google Colab

Open [notebooks/ASL_Ablation_Colab.ipynb](notebooks/ASL_Ablation_Colab.ipynb) in Colab and run its cells in order. The first code cell clones this fork into `/content/cs117-asl-ablation` if necessary and enters this experiment folder. Use the evaluation's measured output, not published performance figures or test mocks.

## Command-line reproduction

From the fork root:

```bash
cd examples/asl_letter_ablation
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v

python scripts/download_data.py
python scripts/prepare_dataset.py
python scripts/smoke_test.py --check-crops --output results/smoke_test.txt
python scripts/evaluate.py --split val --method full --output results/baseline_val.json
python scripts/evaluate.py --split val --method crop --output results/crop_val.json
python scripts/report_results.py
```

The baseline results are written to `results/baseline_val.json` with per-image predictions in `results/baseline_val_predictions.csv`. The crop results and comparison table are saved in the same folder. Commit the **actual measured** JSON and prediction CSV; the project data and model weights stay untracked.

Optional background analysis: generate `docs/background_audit.csv` with `python scripts/make_background_sheet.py --split val`; visually inspect and label images, apply with `python scripts/apply_background_sheet.py`, then rerun both methods. Do not infer background labels from model predictions.

## Provenance and attribution

This fork retains the official Google sample repository and license. The ASL-specific classifier integration, evaluation, data tools, tests, and notebook are added in this subdirectory; see [CONTRIBUTION.md](CONTRIBUTION.md) for a file-by-file inventory. The classifier weights and public ASL dataset are independently sourced from the links above and downloaded at runtime. ChatGPT was used to assist code scaffolding and documentation; real inference and accuracy results must be independently executed.
