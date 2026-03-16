
import os
import random
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
import timm
from datasets import load_dataset
from torch.utils.data import ConcatDataset, DataLoader, random_split
from torchvision import datasets
from tqdm import tqdm
from PIL import Image

import utils


def main() -> None:
    # ------------------------------------------------------------------
    # 0️⃣ Reproducibility
    # ------------------------------------------------------------------
    SEED = 42
    random.seed(SEED)
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    torch.backends.cudnn.benchmark = True

    # ------------------------------------------------------------------
    # 1️⃣ Device (GPU if available)
    # ------------------------------------------------------------------
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    utils.log(f"Using device: {device}")

    # ------------------------------------------------------------------
    # 2️⃣ Data locations (your new folders)
    # ------------------------------------------------------------------
    ADM_ROOT = r"C:\Users\uwais\u\project\train\ADM\imagenet_ai_0508_adm"
    CIFAKE_CACHE = r"C:\Users\uwais\u\project\train\cifake_cache"

    # ------------------------------------------------------------------
    # 3️⃣ Output artefact paths (unchanged)
    # ------------------------------------------------------------------
    CHECKPOINT_PATH = r"C:\Users\uwais\u\project\train\adm_cifake_mobilenetv3.pth"
    ONNX_PATH = r"C:\Users\uwais\u\project\train\adm_cifake.onnx"
    TFLITE_PATH = r"C:\Users\uwais\u\project\train\adm_cifake_int8.tflite"

    # ------------------------------------------------------------------
    # 4️⃣ Helper: safe PIL loader – returns a black placeholder on error
    # ------------------------------------------------------------------
    def pil_loader_fallback(path: str) -> Image.Image:
        """
        Load an image with Pillow.  If Pillow cannot read the file, log a warning
        and return a solid black RGB image (224x224).  The downstream
        ``transforms`` will resize / crop anyway, so the exact size does not
        matter.
        """
        try:
            with open(path, "rb") as f:
                img = Image.open(f)
                return img.convert("RGB")
        except Exception as exc:
            utils.log(f"⚠️  Unable to open image {path}: {exc} – using black placeholder.")
            # 224×224 placeholder matches the later transform size
            return Image.new("RGB", (224, 224), (0, 0, 0))

    # ------------------------------------------------------------------
    # 5️⃣ Load ADM (torchvision ImageFolder) with robust loader
    # ------------------------------------------------------------------
    utils.log("Loading ADM dataset …")
    adm_transform = utils.get_image_transform()
    adm_dataset = datasets.ImageFolder(
        root=ADM_ROOT,
        transform=adm_transform,
        loader=pil_loader_fallback,      # <-- robust loader
    )

    # ------------------------------------------------------------------
    # 6️⃣ Load CIFAKE via 🤗 datasets, wrap it as a torch Dataset
    # ------------------------------------------------------------------
    utils.log("Downloading / loading CIFAKE …")
    cifake_hf = load_dataset(
        "dragonintelligence/CIFAKE-image-dataset",
        cache_dir=CIFAKE_CACHE,
    )
    cifake_raw = cifake_hf.get("train") or next(iter(cifake_hf.values()))

    class CIFAKEWrapper(torch.utils.data.Dataset):
        """Thin wrapper that turns the HF Dataset into a torch Dataset."""

        def __init__(self, hf_dataset):
            self.ds = hf_dataset
            self.transform = utils.get_image_transform()

        def __len__(self):
            return len(self.ds)

        def __getitem__(self, idx):
            item = self.ds[idx]
            img = item["image"]
            # ``image`` may be a PIL.Image, a numpy array or raw bytes.
            if isinstance(img, np.ndarray):
                img = Image.fromarray(img)
            elif isinstance(img, (bytes, bytearray)):
                from io import BytesIO

                img = Image.open(BytesIO(img)).convert("RGB")
            # If Pillow fails for some unexpected reason we fallback to a black image.
            if not isinstance(img, Image.Image):
                utils.log(f"⚠️  Unexpected image type for index {idx} – using placeholder.")
                img = Image.new("RGB", (224, 224), (0, 0, 0))
            label = int(item["label"])          # 0 = real, 1 = synthetic
            img = self.transform(img)
            return img, label

    cifake_dataset = CIFAKEWrapper(cifake_raw)

    # ------------------------------------------------------------------
    # 7️⃣ Concatenate & split (80/10/10)
    # ------------------------------------------------------------------
    combined = ConcatDataset([adm_dataset, cifake_dataset])
    total_len = len(combined)
    train_len = int(0.8 * total_len)
    val_len = int(0.1 * total_len)
    test_len = total_len - train_len - val_len

    utils.log(
        f"Total images: {total_len} → train:{train_len} val:{val_len} test:{test_len}"
    )

    train_set, val_set, test_set = random_split(
        combined,
        [train_len, val_len, test_len],
        generator=torch.Generator().manual_seed(SEED),
    )

    # Using ``num_workers=0`` avoids the spawn‑related multiprocessing crash on Windows.
    # If you have a fast CPU and want parallel loading, increase this number after confirming
    # the script runs without the “bootstrap before fork” error.
    train_loader = DataLoader(
        train_set, batch_size=64, shuffle=True, num_workers=0, pin_memory=False
    )
    val_loader = DataLoader(
        val_set, batch_size=64, shuffle=False, num_workers=0, pin_memory=False
    )
    test_loader = DataLoader(
        test_set, batch_size=64, shuffle=False, num_workers=0, pin_memory=False
    )

    # ------------------------------------------------------------------
    # 8️⃣ Model – MobileNet‑V3‑Small (timm)
    # ------------------------------------------------------------------
    utils.log("Creating MobileNet‑V3‑Small model …")
    model = timm.create_model(
        "mobilenetv3_small_100", pretrained=False, num_classes=2
    ).to(device)

    criterion = nn.CrossEntropyLoss()
    optimizer = optim.AdamW(model.parameters(), lr=3e-4)

    # ------------------------------------------------------------------
    # 9️⃣ Training loop (10 epochs)
    # ------------------------------------------------------------------
    EPOCHS = 10

    def train_one_epoch(epoch_idx: int) -> None:
        model.train()
        running_loss = 0.0
        correct = 0
        total = 0
        pbar = tqdm(train_loader, desc=f"Epoch {epoch_idx+1}/{EPOCHS} [train]")
        for imgs, targets in pbar:
            imgs, targets = imgs.to(device), targets.to(device)

            optimizer.zero_grad()
            outputs = model(imgs)
            loss = criterion(outputs, targets)
            loss.backward()
            optimizer.step()

            running_loss += loss.item() * imgs.size(0)
            _, preds = torch.max(outputs, 1)
            correct += (preds == targets).sum().item()
            total += targets.size(0)

            pbar.set_postfix(
                loss=running_loss / total,
                acc=100.0 * correct / total,
            )

    def evaluate(loader: DataLoader) -> float:
        """Return accuracy (percentage) on the given loader."""
        model.eval()
        correct = 0
        total = 0
        with torch.no_grad():
            for imgs, targets in loader:
                imgs, targets = imgs.to(device), targets.to(device)
                outputs = model(imgs)
                _, preds = torch.max(outputs, 1)
                correct += (preds == targets).sum().item()
                total += targets.size(0)
        return 100.0 * correct / total

    for epoch in range(EPOCHS):
        train_one_epoch(epoch)
        val_acc = evaluate(val_loader)
        utils.log(f"Validation accuracy after epoch {epoch+1}: {val_acc:.2f}%")

    test_acc = evaluate(test_loader)
    utils.log(f"Final TEST accuracy: {test_acc:.2f}%")

    # ------------------------------------------------------------------
    # 🔟 Save checkpoint
    # ------------------------------------------------------------------
    utils.log(f"Saving checkpoint to {CHECKPOINT_PATH}")
    torch.save(
        {
            "epoch": EPOCHS,
            "model_state_dict": model.state_dict(),
            "optimizer_state_dict": optimizer.state_dict(),
            "test_accuracy": test_acc,
        },
        CHECKPOINT_PATH,
    )

    # ------------------------------------------------------------------
    # 🛠️ Export → ONNX → INT8 TFLite (using utils helpers)
    # ------------------------------------------------------------------
    dummy_input = torch.randn(1, 3, 224, 224).to(device)

    # 1️⃣ ONNX
    utils.export_onnx(model, dummy_input, ONNX_PATH)

    # 2️⃣ Representative data for INT8 calibration – a few random batches are enough.
    rep_loader = DataLoader(train_set, batch_size=1, shuffle=True, num_workers=0)

    # 3️⃣ Convert to TFLite (quantised)
    utils.convert_onnx_to_tflite(
        ONNX_PATH,
        TFLITE_PATH,
        representative_loader=rep_loader,
        quantise=True,
    )

    # ------------------------------------------------------------------
    # 📏 Report final model size
    # ------------------------------------------------------------------
    tflite_size = utils.get_file_size_mb(TFLITE_PATH)
    utils.log(f"Exported INT8 TFLite model size: {tflite_size:.2f} MB")


# ----------------------------------------------------------------------
# Windows entry‑point guard
# ----------------------------------------------------------------------
if __name__ == "__main__":
    # Required for the spawn start‑method on Windows
    torch.multiprocessing.freeze_support()
    main()
