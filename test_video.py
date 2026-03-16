"""
test_video.py
-------------
Test the trained video deepfake detection model on individual videos or folders.

Usage:
    python test_video.py path/to/video.mp4
    python test_video.py path/to/folder_of_videos
    python test_video.py path/to/folder --report
"""

import argparse
import os
import sys
from pathlib import Path
from typing import List

import cv2
import numpy as np
import timm
import torch
import torch.nn as nn
from PIL import Image
from torchvision import transforms

import utils

TRAIN_ROOT = Path(__file__).resolve().parent
CHECKPOINT = TRAIN_ROOT / "video_effnet_b0.pth"
LABELS = {0: "REAL", 1: "AI-GENERATED"}
VIDEO_EXTS = {".mp4", ".avi", ".mov", ".mkv", ".webm", ".flv"}
FRAMES_PER_VIDEO = 8


def load_model(device: torch.device) -> nn.Module:
    model = timm.create_model("efficientnet_b0", pretrained=False, num_classes=2)
    ckpt = torch.load(str(CHECKPOINT), map_location=device, weights_only=True)
    model.load_state_dict(ckpt["model_state_dict"])
    model = model.to(device)
    model.eval()
    return model


def extract_frames(video_path: str, n_frames: int = FRAMES_PER_VIDEO) -> List[Image.Image]:
    """Extract N evenly-spaced frames from a video."""
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        return []
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if total <= 0:
        cap.release()
        return []

    indices = np.linspace(0, total - 1, n_frames, dtype=int)
    frames = []
    for idx in indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, int(idx))
        ret, bgr = cap.read()
        if ret:
            rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
            frames.append(Image.fromarray(rgb))
        elif frames:
            frames.append(frames[-1].copy())
    cap.release()

    while len(frames) < n_frames and frames:
        frames.append(frames[-1].copy())
    return frames[:n_frames]


@torch.no_grad()
def predict_video(
    model: nn.Module,
    video_path: str,
    transform: transforms.Compose,
    device: torch.device,
) -> dict:
    """Predict a single video by averaging frame-level scores."""
    frames = extract_frames(video_path)
    if not frames:
        return None

    batch = torch.stack([transform(f) for f in frames]).to(device)
    logits = model(batch)
    probs = torch.softmax(logits, dim=1)

    # Per-frame predictions (for detail)
    frame_preds = probs.argmax(dim=1).cpu().tolist()
    n_real_frames = frame_preds.count(0)
    n_fake_frames = frame_preds.count(1)

    # Video-level: average probabilities across frames
    avg_probs = probs.mean(dim=0)
    pred_idx = avg_probs.argmax().item()

    # Duration info
    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30
    frame_count = cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0
    duration = frame_count / fps if fps > 0 else 0
    cap.release()

    return {
        "file": os.path.basename(video_path),
        "prediction": LABELS[pred_idx],
        "confidence": avg_probs[pred_idx].item() * 100,
        "real_prob": avg_probs[0].item() * 100,
        "fake_prob": avg_probs[1].item() * 100,
        "frames_analysed": len(frames),
        "real_frames": n_real_frames,
        "fake_frames": n_fake_frames,
        "duration_s": duration,
    }


def main():
    parser = argparse.ArgumentParser(description="Test video deepfake detection model")
    parser.add_argument("input", help="Path to a video file or folder of videos")
    parser.add_argument("--report", action="store_true",
                        help="Print a summary table at the end")
    parser.add_argument("--frames", type=int, default=FRAMES_PER_VIDEO,
                        help=f"Frames to sample per video (default: {FRAMES_PER_VIDEO})")
    args = parser.parse_args()

    n_frames = args.frames

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    utils.log(f"Device: {device}")

    if not CHECKPOINT.exists():
        utils.log(f"ERROR: Checkpoint not found at {CHECKPOINT}")
        sys.exit(1)

    model = load_model(device)
    transform = transforms.Compose([
        transforms.Resize(224),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406],
                             std=[0.229, 0.224, 0.225]),
    ])

    input_path = Path(args.input)

    # Collect video paths
    if input_path.is_file():
        video_paths = [str(input_path)]
    elif input_path.is_dir():
        video_paths = sorted(
            str(p) for p in input_path.rglob("*")
            if p.suffix.lower() in VIDEO_EXTS
        )
    else:
        utils.log(f"ERROR: {args.input} is not a valid file or directory")
        sys.exit(1)

    if not video_paths:
        utils.log("No videos found.")
        sys.exit(0)

    utils.log(f"Testing {len(video_paths)} video(s) with {n_frames} frames each …\n")

    results = []
    for vid_path in video_paths:
        try:
            result = predict_video(model, vid_path, transform, device)
            if result is None:
                print(f"  ⚠️  {os.path.basename(vid_path)}: Could not read video")
                continue

            results.append(result)
            icon = "🟢" if result["prediction"] == "REAL" else "🔴"
            print(
                f"  {icon} {result['file']:<40}  "
                f"{result['prediction']:<15} "
                f"({result['confidence']:.1f}% confidence)  "
                f"[real={result['real_prob']:.1f}%  fake={result['fake_prob']:.1f}%]  "
                f"frames: {result['real_frames']}R/{result['fake_frames']}F  "
                f"({result['duration_s']:.1f}s)"
            )
        except Exception as e:
            print(f"  ⚠️  {os.path.basename(vid_path)}: Error — {e}")

    # Summary
    if args.report and results:
        real_count = sum(1 for r in results if r["prediction"] == "REAL")
        fake_count = sum(1 for r in results if r["prediction"] == "AI-GENERATED")
        avg_conf = np.mean([r["confidence"] for r in results])
        print(f"\n{'='*70}")
        print(f"  SUMMARY")
        print(f"  Total: {len(results)}  |  Real: {real_count}  |  AI-Generated: {fake_count}")
        print(f"  Average confidence: {avg_conf:.1f}%")
        print(f"  Frames per video: {n_frames}")
        print(f"{'='*70}")


if __name__ == "__main__":
    main()
