# AI Model Export Scripts

This directory contains scripts used to convert and export AI models for the ZenConverter project.
These scripts are kept here for provenance and tracking of how the models were generated.

## Real-ESRGAN-x4plus ONNX Export

The `export_realesrgan.py` script downloads the official PyTorch `.pth` checkpoint of `RealESRGAN_x4plus` from the `xinntao/Real-ESRGAN` repository and converts it to a standard `.onnx` file with dynamic axes.

### How to run

1. Ensure you have Python installed.
2. Create and activate a virtual environment (recommended):
   ```bash
   python -m venv venv
   # On Windows
   .\venv\Scripts\activate
   # On Linux/Mac
   source venv/bin/activate
   ```
3. Install dependencies:
   ```bash
   pip install torch torchvision onnx basicsr
   ```
4. Run the script:
   ```bash
   python export_realesrgan.py
   ```

The script will output the converted `.onnx` model to your system's `Downloads` folder, ready to be uploaded to Cloudflare R2.

The checked-in export (`RealESRGAN_x4plus.onnx`) is uploaded to R2 and fetched by
the app at runtime from `https://assets.xlab.my/models/RealESRGAN_x4plus.onnx`.
Its SHA-256 (`39d5218cfcef542d667821a0d2072cfa51bfd857ab0e4ae7dc067c399a88d323`)
and size (`67,051,973` bytes) are recorded in `docs/license-and-attribution.md`;
update both if the export changes.
