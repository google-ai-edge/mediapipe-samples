# Setup
Use pip to install the following dependencies
```
pip install torch typing_extensions numpy Pillow requests pytorch_lightning absl-py
```


# Usage
Convert the model checkpoints into a bins folder using the script:
```
python3 convert.py --ckpt_path <ckpt_path> --output_path <output_path>
```