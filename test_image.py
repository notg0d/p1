"""
test_image.py
-------------
Test the trained image deepfake detection model on individual images or folders.

Usage:
    python test_image.py path/to/image.jpg
    python test_image.py path/to/folder_of_images
    python test_image.py path/to/folder --report
"""

import argparse
import os
import sys
from pathlib import Path

import numpy as np
import timm
import torch
import torch.nn as nn
from PIL import Image
from torchvision import transforms

import utils

TRAIN_ROOT = Path(__file__).resolve().parent
CHECKPOINT = TRAIN_ROOT / "adm_cifake_mobilenetv3.pth"
LABELS = {0: "REAL", 1: "AI-GENERATED"}
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tiff"}


def load_model(device: torch.device) -> nn.Module:
    model = timm.create_model("mobilenetv3_small_100", pretrained=False, num_classes=2)
    ckpt = torch.load(str(CHECKPOINT), map_location=device, weights_only=True)
    model.load_state_dict(ckpt["model_state_dict"])
    model = model.to(device)
    model.eval()
    return model


def predict_image(
    model: nn.Module,
    img_path: str,
    transform: transforms.Compose,
    device: torch.device,
) -> dict:
    """Return prediction dict with label, confidence, and probabilities."""
    img = Image.open(img_path).convert("RGB")
    tensor = transform(img).unsqueeze(0).to(device)

    with torch.no_grad():
        logits = model(tensor)
        probs = torch.softmax(logits, dim=1).squeeze()

    pred_idx = probs.argmax().item()
    return {
        "file": os.path.basename(img_path),
        "prediction": LABELS[pred_idx],
        "confidence": probs[pred_idx].item() * 100,
        "real_prob": probs[0].item() * 100,
        "fake_prob": probs[1].item() * 100,
    }


def main():
    parser = argparse.ArgumentParser(description="Test image deepfake detection model")
    parser.add_argument("input", help="Path to an image file or folder of images")
    parser.add_argument("--report", action="store_true",
                        help="Print a summary table at the end")
    args = parser.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    utils.log(f"Device: {device}")

    if not CHECKPOINT.exists():
        utils.log(f"ERROR: Checkpoint not found at {CHECKPOINT}")
        sys.exit(1)

    model = load_model(device)
    transform = utils.get_image_transform()

    input_path = Path(args.input)

    # Collect image paths
    if input_path.is_file():
        image_paths = [str(input_path)]
    elif input_path.is_dir():
        image_paths = sorted(
            str(p) for p in input_path.rglob("*")
            if p.suffix.lower() in IMAGE_EXTS
        )
    else:
        utils.log(f"ERROR: {args.input} is not a valid file or directory")
        sys.exit(1)

    if not image_paths:
        utils.log("No images found.")
        sys.exit(0)

    utils.log(f"Testing {len(image_paths)} image(s) …\n")

    results = []
    for img_path in image_paths:
        try:
            result = predict_image(model, img_path, transform, device)
            results.append(result)
            icon = "🟢" if result["prediction"] == "REAL" else "🔴"
            print(f"  {icon} {result['file']:<40}  "
                  f"{result['prediction']:<15} "
                  f"({result['confidence']:.1f}% confidence)  "
                  f"[real={result['real_prob']:.1f}%  fake={result['fake_prob']:.1f}%]")
        except Exception as e:
            print(f"  ⚠️  {os.path.basename(img_path)}: Error — {e}")

    # Summary
    if args.report and results:
        real_count = sum(1 for r in results if r["prediction"] == "REAL")
        fake_count = sum(1 for r in results if r["prediction"] == "AI-GENERATED")
        avg_conf = np.mean([r["confidence"] for r in results])
        print(f"\n{'='*60}")
        print(f"  SUMMARY")
        print(f"  Total: {len(results)}  |  Real: {real_count}  |  AI-Generated: {fake_count}")
        print(f"  Average confidence: {avg_conf:.1f}%")
        print(f"{'='*60}")


if __name__ == "__main__":
    main()
