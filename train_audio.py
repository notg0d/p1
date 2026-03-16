"""
train_audio.py
--------------
Audio deepfake detection — MLAAD-tiny + WaveFake.

Pipeline:
  1. Collect WAV files from MLAAD‑tiny (real + fake) and pre‑extract
     audio from WaveFake parquet files.
  2. Convert each audio clip → log‑mel spectrogram image (224×224).
  3. Train EfficientNet‑B0 on these spectrogram "images" (transfer learning).
  4. Export to ONNX → INT8 TFLite.

Usage:
  python train_audio.py
"""

import os
import random
import struct
import io
from pathlib import Path
from typing import List, Tuple

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
import torchaudio
import timm
from PIL import Image
from torch.utils.data import DataLoader, Dataset, random_split
from torchvision import transforms
from tqdm import tqdm

import utils


# ======================================================================
# 0️⃣  Constants
# ======================================================================
SEED = 42
BATCH_SIZE = 32
EPOCHS = 10
LR = 1e-4
NUM_WORKERS = 0   # Windows‑safe
SAMPLE_RATE = 16_000
DURATION_SEC = 3  # Take 3 seconds per clip
N_MELS = 128
HOP_LENGTH = 512

TRAIN_ROOT = Path(__file__).resolve().parent

MLAAD_ROOT  = TRAIN_ROOT / "mlaad"
WAVEFAKE_ROOT = TRAIN_ROOT / "wavefake"

# Pre‑extracted spectrograms go here
SPECS_DIR = TRAIN_ROOT / "extracted_spectrograms"

CHECKPOINT_PATH = str(TRAIN_ROOT / "audio_effnet_b0.pth")
ONNX_PATH       = str(TRAIN_ROOT / "audio_effnet_b0.onnx")
TFLITE_PATH     = str(TRAIN_ROOT / "audio_effnet_b0_int8.tflite")


# ======================================================================
# 1️⃣  Collect audio paths — MLAAD‑tiny
# ======================================================================
def collect_mlaad(root: Path) -> List[Tuple[str, int]]:
    """Return (path, label) for MLAAD‑tiny WAVs."""
    samples: List[Tuple[str, int]] = []

    # Real audio — original/ folder
    for lang in ("en", "de"):
        lang_dir = root / "original" / lang
        if lang_dir.exists():
            for wav in lang_dir.rglob("*.wav"):
                samples.append((str(wav), 0))

    # Fake audio — fake/ folder (each TTS model is a subfolder)
    for lang in ("en", "de"):
        lang_dir = root / "fake" / lang
        if lang_dir.exists():
            for wav in lang_dir.rglob("*.wav"):
                samples.append((str(wav), 1))

    return samples


# ======================================================================
# 2️⃣  Pre‑extract mel spectrograms to disk (ONE TIME)
# ======================================================================
def waveform_to_spectrogram(
    waveform: torch.Tensor,
    sr: int = SAMPLE_RATE,
) -> Image.Image:
    """Convert a 1‑D waveform tensor → 224×224 PIL Image (log‑mel spec)."""
    # Ensure mono
    if waveform.dim() > 1:
        waveform = waveform.mean(dim=0)

    # Resample if needed
    if sr != SAMPLE_RATE:
        resampler = torchaudio.transforms.Resample(sr, SAMPLE_RATE)
        waveform = resampler(waveform)
        sr = SAMPLE_RATE

    # Pad or trim to fixed length
    target_len = sr * DURATION_SEC
    if waveform.shape[0] < target_len:
        waveform = torch.nn.functional.pad(waveform, (0, target_len - waveform.shape[0]))
    else:
        waveform = waveform[:target_len]

    # Compute log‑mel spectrogram
    mel_spec = torchaudio.transforms.MelSpectrogram(
        sample_rate=sr,
        n_mels=N_MELS,
        hop_length=HOP_LENGTH,
        n_fft=1024,
    )(waveform.unsqueeze(0))
    log_mel = torch.log1p(mel_spec).squeeze(0)  # (n_mels, time)

    # Normalise to 0–255 and convert to PIL
    log_mel = log_mel - log_mel.min()
    if log_mel.max() > 0:
        log_mel = log_mel / log_mel.max()
    log_mel = (log_mel * 255).byte().numpy()

    img = Image.fromarray(log_mel, mode="L").convert("RGB")
    img = img.resize((224, 224), Image.BILINEAR)
    return img


def extract_mlaad_spectrograms(
    samples: List[Tuple[str, int]],
    out_dir: Path,
) -> None:
    """Save mel‑spectrogram images for MLAAD‑tiny WAV files."""
    real_dir = out_dir / "real"
    fake_dir = out_dir / "fake"
    real_dir.mkdir(parents=True, exist_ok=True)
    fake_dir.mkdir(parents=True, exist_ok=True)

    for wav_path, label in tqdm(samples, desc="MLAAD spectrograms"):
        dest = real_dir if label == 0 else fake_dir
        file_id = str(abs(hash(wav_path)))[-12:]
        out_path = dest / f"mlaad_{file_id}.jpg"

        if out_path.exists():
            continue

        try:
            waveform, sr = torchaudio.load(wav_path)
            img = waveform_to_spectrogram(waveform, sr)
            img.save(str(out_path), quality=90)
        except Exception as e:
            pass  # Skip unreadable files


def extract_wavefake_spectrograms(
    root: Path,
    out_dir: Path,
    max_samples: int = 20_000,
) -> None:
    """Load WaveFake parquet files and save mel‑spectrogram images.
    
    Limits to max_samples to keep training manageable since WaveFake
    has 64,800 clips at 30GB.
    """
    import pyarrow.parquet as pq

    real_dir = out_dir / "real"
    fake_dir = out_dir / "fake"
    real_dir.mkdir(parents=True, exist_ok=True)
    fake_dir.mkdir(parents=True, exist_ok=True)

    data_dir = root / "data"
    if not data_dir.exists():
        utils.log("WaveFake data/ dir not found — skipping.")
        return

    parquet_files = sorted(data_dir.glob("*.parquet"))
    if not parquet_files:
        utils.log("No parquet files found in WaveFake — skipping.")
        return

    count = 0
    for pf in tqdm(parquet_files, desc="WaveFake partitions"):
        if count >= max_samples:
            break

        try:
            table = pq.read_table(str(pf))
            for i in range(len(table)):
                if count >= max_samples:
                    break

                row = table.slice(i, 1).to_pydict()
                label_str = row["real_or_fake"][0]
                label = 0 if label_str.lower() == "real" else 1
                dest = real_dir if label == 0 else fake_dir

                file_id = f"wf_{count:06d}"
                out_path = dest / f"{file_id}.jpg"

                if out_path.exists():
                    count += 1
                    continue

                # Extract audio bytes from parquet
                audio_data = row["audio"][0]
                audio_bytes = audio_data["bytes"]
                audio_sr = audio_data.get("sampling_rate", 16000)

                # Load waveform from bytes
                waveform, sr = torchaudio.load(
                    io.BytesIO(audio_bytes), format="wav"
                )
                img = waveform_to_spectrogram(waveform, sr)
                img.save(str(out_path), quality=90)
                count += 1

        except Exception as e:
            utils.log(f"Error processing {pf.name}: {e}")
            continue

    utils.log(f"Extracted {count:,} WaveFake spectrograms.")


# ======================================================================
# 3️⃣  Main
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
    # Collect MLAAD samples
    # ------------------------------------------------------------------
    utils.log("Collecting MLAAD‑tiny audio paths …")
    mlaad_samples = collect_mlaad(MLAAD_ROOT)
    n_real = sum(1 for _, l in mlaad_samples if l == 0)
    n_fake = sum(1 for _, l in mlaad_samples if l == 1)
    utils.log(f"MLAAD‑tiny: {len(mlaad_samples):,} files  (real={n_real:,}  fake={n_fake:,})")

    # ------------------------------------------------------------------
    # Pre‑extract spectrograms (ONE TIME)
    # ------------------------------------------------------------------
    marker = SPECS_DIR / ".extraction_done"
    if marker.exists():
        utils.log("Spectrograms already extracted — skipping. ⚡")
    else:
        utils.log("Pre‑extracting spectrograms to disk (one‑time cost) …")

        # MLAAD‑tiny
        extract_mlaad_spectrograms(mlaad_samples, SPECS_DIR)

        # WaveFake (limit to 20K to keep balanced)
        extract_wavefake_spectrograms(WAVEFAKE_ROOT, SPECS_DIR, max_samples=20_000)

        marker.touch()
        utils.log("Spectrogram extraction complete! ✅")

    # ------------------------------------------------------------------
    # Build dataset from pre‑extracted spectrogram images
    # ------------------------------------------------------------------
    img_transform = transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406],
                             std=[0.229, 0.224, 0.225]),
    ])

    from torchvision import datasets as tv_datasets
    full_dataset = tv_datasets.ImageFolder(str(SPECS_DIR), transform=img_transform)
    utils.log(f"Total spectrogram images: {len(full_dataset):,}")
    utils.log(f"Classes: {full_dataset.classes}")

    # Split 80/10/10
    total = len(full_dataset)
    train_n = int(0.8 * total)
    val_n   = int(0.1 * total)
    test_n  = total - train_n - val_n

    train_ds, val_ds, test_ds = random_split(
        full_dataset, [train_n, val_n, test_n],
        generator=torch.Generator().manual_seed(SEED),
    )
    utils.log(f"Split → train:{train_n}  val:{val_n}  test:{test_n}")

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True,
                              num_workers=NUM_WORKERS, pin_memory=True)
    val_loader   = DataLoader(val_ds,   batch_size=BATCH_SIZE, shuffle=False,
                              num_workers=NUM_WORKERS, pin_memory=True)
    test_loader  = DataLoader(test_ds,  batch_size=BATCH_SIZE, shuffle=False,
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
    # Training loop (with AMP)
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
    def evaluate(loader: DataLoader) -> float:
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
        val_acc = evaluate(val_loader)
        scheduler.step()
        utils.log(f"Epoch {epoch+1}/{EPOCHS} — val acc: {val_acc:.2f}%  "
                  f"lr: {scheduler.get_last_lr()[0]:.2e}")

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
    # Final test accuracy
    # ------------------------------------------------------------------
    utils.log("Loading best checkpoint for final evaluation …")
    ckpt = torch.load(CHECKPOINT_PATH, map_location=device, weights_only=True)
    model.load_state_dict(ckpt["model_state_dict"])
    test_acc = evaluate(test_loader)
    utils.log(f"Final TEST accuracy: {test_acc:.2f}%")

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
