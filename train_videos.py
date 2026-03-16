
import os
import random
import time
from pathlib import Path
from typing import List, Tuple

import cv2
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
import timm
from PIL import Image
from torch.utils.data import ConcatDataset, DataLoader, Dataset, random_split
from torchvision import datasets, transforms
from tqdm import tqdm

import utils


# ======================================================================
# 0️⃣  Constants
# ======================================================================
SEED = 42
FRAMES_PER_VIDEO = 8           # fewer frames = faster extraction + training
BATCH_SIZE = 32                # larger batch with AMP
EPOCHS = 10
LR = 1e-4
NUM_WORKERS = 0                # safe default for Windows

TRAIN_ROOT = Path(__file__).resolve().parent

FF_ROOT      = TRAIN_ROOT / "FaceForensics++"
CELEBDF_ROOT = TRAIN_ROOT / "Celeb-DF-v2"
DA_ROOT      = TRAIN_ROOT / "deepaction_v1"

# Pre‑extracted frames go here (created once, reused every run)
FRAMES_DIR = TRAIN_ROOT / "extracted_frames"

CHECKPOINT_PATH = str(TRAIN_ROOT / "video_effnet_b0.pth")
ONNX_PATH       = str(TRAIN_ROOT / "video_effnet_b0.onnx")
TFLITE_PATH     = str(TRAIN_ROOT / "video_effnet_b0_int8.tflite")


# ======================================================================
# 1️⃣  Collect video paths (label 0 = real, 1 = fake)
# ======================================================================
def _glob_mp4(folder: Path) -> List[str]:
    if not folder.exists():
        return []
    return sorted(str(p) for p in folder.rglob("*.mp4"))


def collect_faceforensics(root: Path) -> List[Tuple[str, int]]:
    samples: List[Tuple[str, int]] = []
    for sub in ("youtube", "actors"):
        for p in _glob_mp4(root / "original_sequences" / sub / "c23" / "videos"):
            samples.append((p, 0))
    manip = root / "manipulated_sequences"
    if manip.exists():
        for method_dir in sorted(manip.iterdir()):
            if method_dir.is_dir():
                for p in _glob_mp4(method_dir / "c23" / "videos"):
                    samples.append((p, 1))
    return samples


def collect_celebdf(root: Path) -> List[Tuple[str, int]]:
    samples: List[Tuple[str, int]] = []
    for real_sub in ("Celeb-real", "YouTube-real"):
        for p in _glob_mp4(root / real_sub):
            samples.append((p, 0))
    for p in _glob_mp4(root / "Celeb-synthesis"):
        samples.append((p, 1))
    return samples


def collect_deepaction(root: Path) -> List[Tuple[str, int]]:
    samples: List[Tuple[str, int]] = []
    for p in _glob_mp4(root / "Pexels"):
        samples.append((p, 0))
    for sub in ("RunwayML", "CogVideoX5B", "StableDiffusion",
                "Veo", "VideoPoet", "BDAnimateDiffLightning"):
        for p in _glob_mp4(root / sub):
            samples.append((p, 1))
    return samples


# ======================================================================
# 2️⃣  Pre‑extract frames to disk (ONE TIME — skipped on subsequent runs)
# ======================================================================
def extract_and_save_frames(
    samples: List[Tuple[str, int]],
    out_dir: Path,
    n_frames: int = FRAMES_PER_VIDEO,
) -> None:
    """
    For each video, save N evenly‑spaced frames as JPEG files.
    Directory layout:  out_dir / real|fake / <video_hash>_f<N>.jpg
    Skips extraction if out_dir already exists and is non‑empty.
    """
    real_dir = out_dir / "real"
    fake_dir = out_dir / "fake"
    real_dir.mkdir(parents=True, exist_ok=True)
    fake_dir.mkdir(parents=True, exist_ok=True)

    for vid_path, label in tqdm(samples, desc="Extracting frames"):
        dest = real_dir if label == 0 else fake_dir
        # Use a short hash of the path as the file prefix
        vid_id = str(abs(hash(vid_path)))[-12:]

        # Skip if frames for this video already exist
        first_frame = dest / f"{vid_id}_f00.jpg"
        if first_frame.exists():
            continue

        cap = cv2.VideoCapture(vid_path)
        if not cap.isOpened():
            continue
        total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if total <= 0:
            cap.release()
            continue

        indices = np.linspace(0, total - 1, n_frames, dtype=int)
        last_frame = None

        for i, idx in enumerate(indices):
            cap.set(cv2.CAP_PROP_POS_FRAMES, int(idx))
            ret, bgr = cap.read()
            if ret:
                last_frame = bgr
            elif last_frame is not None:
                bgr = last_frame
            else:
                continue

            # Resize here to save disk space and avoid doing it during training
            bgr = cv2.resize(bgr, (224, 224))
            out_path = dest / f"{vid_id}_f{i:02d}.jpg"
            cv2.imwrite(str(out_path), bgr, [cv2.IMWRITE_JPEG_QUALITY, 90])

        cap.release()


# ======================================================================
# 3️⃣  Per‑video evaluation (average frame predictions)
# ======================================================================
@torch.no_grad()
def evaluate_per_video(
    model: nn.Module,
    samples: List[Tuple[str, int]],
    device: torch.device,
    transform,
    n_frames: int = FRAMES_PER_VIDEO,
) -> float:
    model.eval()
    correct = 0
    total = 0

    for path, label in tqdm(samples, desc="Video‑level eval", leave=False):
        vid_id = str(abs(hash(path)))[-12:]
        dest = FRAMES_DIR / ("real" if label == 0 else "fake")

        frame_paths = sorted(dest.glob(f"{vid_id}_f*.jpg"))
        if not frame_paths:
            continue

        imgs = []
        for fp in frame_paths[:n_frames]:
            img = Image.open(fp).convert("RGB")
            imgs.append(transform(img))

        batch = torch.stack(imgs).to(device)
        logits = model(batch)
        probs = torch.softmax(logits, dim=1).mean(0)
        pred = probs.argmax().item()

        correct += int(pred == label)
        total += 1

    return 100.0 * correct / total if total else 0.0


# ======================================================================
# 4️⃣  Main
# ======================================================================
def main() -> None:
    random.seed(SEED)
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    torch.backends.cudnn.benchmark = True

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    use_amp = device.type == "cuda"
    utils.log(f"Using device: {device}  |  AMP: {use_amp}")

    # ------------------------------------------------------------------
    # Collect video paths
    # ------------------------------------------------------------------
    utils.log("Collecting video paths …")
    ff_samples  = collect_faceforensics(FF_ROOT)
    cdf_samples = collect_celebdf(CELEBDF_ROOT)
    da_samples  = collect_deepaction(DA_ROOT)

    all_samples = ff_samples + cdf_samples + da_samples
    random.shuffle(all_samples)

    n_real = sum(1 for _, l in all_samples if l == 0)
    n_fake = sum(1 for _, l in all_samples if l == 1)
    utils.log(f"FF++: {len(ff_samples):,}  |  Celeb-DF: {len(cdf_samples):,}  |  DeepAction: {len(da_samples):,}")
    utils.log(f"Total: {len(all_samples):,} videos  (real={n_real:,}  fake={n_fake:,})")

    # ------------------------------------------------------------------
    # Pre‑extract frames (ONE TIME — takes ~10‑20 min, then instant)
    # ------------------------------------------------------------------
    marker = FRAMES_DIR / ".extraction_done"
    if marker.exists():
        utils.log("Frames already extracted — skipping. ⚡")
    else:
        utils.log("Pre‑extracting frames to disk (one‑time cost) …")
        extract_and_save_frames(all_samples, FRAMES_DIR, FRAMES_PER_VIDEO)
        marker.touch()
        utils.log("Frame extraction complete! ✅")

    # ------------------------------------------------------------------
    # Split (video‑level) then build loaders from pre‑extracted images
    # ------------------------------------------------------------------
    total = len(all_samples)
    train_n = int(0.8 * total)
    val_n   = int(0.1 * total)
    test_n  = total - train_n - val_n

    train_samples = all_samples[:train_n]
    val_samples   = all_samples[train_n:train_n + val_n]
    test_samples  = all_samples[train_n + val_n:]

    utils.log(f"Split → train:{train_n}  val:{val_n}  test:{test_n}")

    # Use ImageFolder on the pre‑extracted frames directory
    # (real/ and fake/ subdirs = class 0 and class 1)
    img_transform = transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406],
                             std=[0.229, 0.224, 0.225]),
    ])

    full_dataset = datasets.ImageFolder(str(FRAMES_DIR), transform=img_transform)
    utils.log(f"Total frames on disk: {len(full_dataset):,}")

    # Split the frame dataset 80/10/10
    total_frames = len(full_dataset)
    train_f = int(0.8 * total_frames)
    val_f   = int(0.1 * total_frames)
    test_f  = total_frames - train_f - val_f

    train_ds, val_ds, test_ds = random_split(
        full_dataset, [train_f, val_f, test_f],
        generator=torch.Generator().manual_seed(SEED),
    )

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True,
                              num_workers=NUM_WORKERS, pin_memory=True)
    val_loader   = DataLoader(val_ds,   batch_size=BATCH_SIZE, shuffle=False,
                              num_workers=NUM_WORKERS, pin_memory=True)

    # ------------------------------------------------------------------
    # Model – EfficientNet‑B0 (pretrained)
    # ------------------------------------------------------------------
    utils.log("Creating EfficientNet‑B0 (pretrained) …")
    model = timm.create_model("efficientnet_b0", pretrained=True, num_classes=2).to(device)

    criterion = nn.CrossEntropyLoss()
    optimizer = optim.AdamW(model.parameters(), lr=LR)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=EPOCHS)
    scaler = torch.amp.GradScaler(enabled=use_amp)

    # ------------------------------------------------------------------
    # Training loop (with AMP for GPU speed)
    # ------------------------------------------------------------------
    def train_one_epoch(epoch_idx: int) -> None:
        model.train()
        running_loss = 0.0
        correct = 0
        total = 0
        pbar = tqdm(train_loader, desc=f"Epoch {epoch_idx+1}/{EPOCHS}")
        for imgs, targets in pbar:
            imgs, targets = imgs.to(device), targets.to(device)
            optimizer.zero_grad()

            with torch.amp.autocast(device_type=device.type, enabled=use_amp):
                outputs = model(imgs)
                loss = criterion(outputs, targets)

            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()

            running_loss += loss.item() * imgs.size(0)
            _, preds = torch.max(outputs, 1)
            correct += (preds == targets).sum().item()
            total += targets.size(0)
            pbar.set_postfix(loss=f"{running_loss/total:.4f}",
                             acc=f"{100.*correct/total:.1f}%")

    @torch.no_grad()
    def evaluate_frames(loader: DataLoader) -> float:
        model.eval()
        correct = 0
        total = 0
        for imgs, targets in loader:
            imgs, targets = imgs.to(device), targets.to(device)
            with torch.amp.autocast(device_type=device.type, enabled=use_amp):
                outputs = model(imgs)
            _, preds = torch.max(outputs, 1)
            correct += (preds == targets).sum().item()
            total += targets.size(0)
        return 100.0 * correct / total if total else 0.0

    best_val_acc = 0.0
    for epoch in range(EPOCHS):
        train_one_epoch(epoch)
        val_acc = evaluate_frames(val_loader)
        scheduler.step()
        utils.log(f"Epoch {epoch+1}/{EPOCHS} — val acc: {val_acc:.2f}%  lr: {scheduler.get_last_lr()[0]:.2e}")

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            torch.save({
                "epoch": epoch + 1,
                "model_state_dict": model.state_dict(),
                "optimizer_state_dict": optimizer.state_dict(),
                "val_accuracy": val_acc,
            }, CHECKPOINT_PATH)
            utils.log(f"  ↳ Saved best checkpoint ({val_acc:.2f}%)")

    # ------------------------------------------------------------------
    # Final video‑level test accuracy
    # ------------------------------------------------------------------
    utils.log("Loading best checkpoint for final evaluation …")
    ckpt = torch.load(CHECKPOINT_PATH, map_location=device, weights_only=True)
    model.load_state_dict(ckpt["model_state_dict"])
    test_acc = evaluate_per_video(model, test_samples, device, img_transform)
    utils.log(f"Final TEST accuracy (video‑level): {test_acc:.2f}%")

    # ------------------------------------------------------------------
    # Export → ONNX → INT8 TFLite
    # ------------------------------------------------------------------
    dummy_input = torch.randn(1, 3, 224, 224).to(device)
    utils.export_onnx(model, dummy_input, ONNX_PATH)

    rep_loader = DataLoader(train_ds, batch_size=1, shuffle=True, num_workers=0)
    utils.convert_onnx_to_tflite(
        ONNX_PATH, TFLITE_PATH,
        representative_loader=rep_loader,
        quantise=True,
    )

    tflite_size = utils.get_file_size_mb(TFLITE_PATH)
    utils.log(f"Exported INT8 TFLite model size: {tflite_size:.2f} MB")
    utils.log("Done! ✅")


if __name__ == "__main__":
    torch.multiprocessing.freeze_support()
    main()
