# Measured experiment outputs

Actual model runs will produce:

- `baseline_val.json` and `baseline_val_predictions.csv`: full-frame validation accuracy and per-image predictions.
- `crop_val.json` and `crop_val_predictions.csv`: automatic hand-crop accuracy on the same images.
- `main_table.csv`: side-by-side comparison from real output files.
- `smoke_test.txt`: classification and hand detection for a few real hand-sign images.

There is no measured accuracy committed yet; never use numbers from the mock unit tests as experiment results.
