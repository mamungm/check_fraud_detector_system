import os
import re
import time
from typing import Dict, Any, List, Optional

import cv2
import imagehash
import numpy as np
import pytesseract
from PIL import Image
from sqlalchemy.orm import Session

from db.repo.image_repositories import get_recent_image_hashes, upsert_image_fingerprint
from dtos.image_dtos import ImageAnalysisRequest, ImageAnalysisResponse


def load_image(path: str) -> np.ndarray:
    if path.startswith("file://"):
        path = path.replace("file://", "")

    if not os.path.exists(path):
        raise FileNotFoundError(f"Image not found: {path}")

    img = cv2.imread(path)
    if img is None:
        raise ValueError(f"Could not read image: {path}")

    return img

def perceptual_hash_score(image_path: str, event_id: str, db: Session) -> Dict[str, Any]:
    pil_img = Image.open(image_path).convert("L")

    phash = imagehash.phash(pil_img)    # compact "fingerprint" of an image by analyzing its visual features
    dhash = imagehash.dhash(pil_img)    # captures gradient information by comparing adjacent pixel values, useful for detecting subtle alterations

    phash_hex = str(phash)
    dhash_hex = str(dhash)

    duplicate_score = 0.0
    closest_event = None
    closest_distance = 999

    candidates = get_recent_image_hashes(limit=500, within_days=90, db=db)

    for row in candidates:
        distance = phash - imagehash.hex_to_hash(str(row.phash))

        if distance < closest_distance:
            closest_distance = distance
            closest_event = row.event_id

        # pHash distance 0-5 is usually very similar
        if distance <= 5:
            duplicate_score = max(duplicate_score, 1.0 - (distance / 10.0))

    upsert_image_fingerprint(event_id, phash_hex, dhash_hex, db)

    return {
        "phash": phash_hex,
        "dhash": dhash_hex,
        "duplicate_score": float(round(duplicate_score, 4)),
        "closest_event": closest_event,
        "closest_distance": int(closest_distance) if closest_event else None
    }

def extract_ocr_text(img: np.ndarray) -> str:
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # Improve OCR readability
    gray = cv2.resize(gray, None, fx=1.5, fy=1.5)
    gray = cv2.GaussianBlur(gray, (3, 3), 0)
    _, binary = cv2.threshold(
        gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU
    )

    text = pytesseract.image_to_string(binary)
    return text

def extract_amounts_from_text(text: str) -> List[float]:
    patterns = [
        r"\$\s*([0-9,]+\.\d{2})",
        r"([0-9,]+\.\d{2})"
    ]

    amounts = []

    for pattern in patterns:
        matches = re.findall(pattern, text)
        for m in matches:
            try:
                amounts.append(float(m.replace(",", "")))
            except ValueError:
                pass

    return list(set(amounts))


def amount_match_score(expected_amount: float, ocr_amounts: List[float]) -> bool:
    for amt in ocr_amounts:
        if abs(amt - expected_amount) <= 0.01:
            return True
    return False

def layout_anomaly_score(img: np.ndarray) -> float:
    """
    Heuristic:
    - Detect major horizontal/vertical lines.
    - Real/synthetic checks usually have structured lines.
    - Too few or too many line structures => anomaly.
    """

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 50, 150)

    lines = cv2.HoughLinesP(
        edges,
        rho=1,
        theta=np.pi / 180,
        threshold=120,
        minLineLength=120,
        maxLineGap=10
    )

    line_count = 0 if lines is None else len(lines)

    if 8 <= line_count <= 80:
        return 0.1
    elif line_count < 8:
        return 0.7
    else:
        return 0.5


def font_anomaly_score(img: np.ndarray) -> float:
    """
    Very simple MVP heuristic:
    - Segment text-like connected components.
    - Estimate size variance.
    - High variance may indicate mixed font/amount alteration.
    """

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    _, binary = cv2.threshold(
        gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )

    num_labels, labels, stats, _ = cv2.connectedComponentsWithStats(binary)

    heights = []

    for i in range(1, num_labels):
        x, y, w, h, area = stats[i]

        if 10 <= h <= 80 and 10 <= w <= 300 and 20 <= area <= 5000:
            heights.append(h)

    if len(heights) < 5:
        return 0.6

    std = float(np.std(heights))
    mean = float(np.mean(heights))

    coeff_var = std / mean if mean > 0 else 1.0

    return round(min(coeff_var, 1.0), 4)


def signature_presence_score(front_img: np.ndarray) -> float:
    """
    Heuristic:
    - Signature normally appears in lower-right region.
    - Detect ink density / edge density in that region.
    """

    h, w, _ = front_img.shape

    roi = front_img[int(h * 0.60):int(h * 0.90), int(w * 0.65):int(w * 0.95)]

    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 50, 150)

    edge_density = np.sum(edges > 0) / edges.size

    if edge_density > 0.015:
        return 0.95
    elif edge_density > 0.007:
        return 0.6
    else:
        return 0.1


def endorsement_score(back_img: Optional[np.ndarray]) -> float:
    """
    Heuristic:
    - Endorsement usually appears in upper/middle back region.
    - Detect text/ink density.
    """

    if back_img is None:
        return 0.0

    h, w, _ = back_img.shape

    roi = back_img[int(h * 0.10):int(h * 0.65), int(w * 0.05):int(w * 0.95)]

    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    _, binary = cv2.threshold(
        gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
    )

    ink_density = np.sum(binary > 0) / binary.size

    if ink_density > 0.025:
        return 0.95
    elif ink_density > 0.012:
        return 0.6
    else:
        return 0.1

# def score_event(e: DepositEvent) -> ScoreResponse:
#     start = time.time()
#     reasons = []
#     s = 0.02
#     uri = (e.imageFrontUri or "") + " " + (e.imageBackUri or "")
#     if "altered" in uri.lower():
#         s += 0.55; reasons.append("IMAGE_ALTERATION_INDICATOR")
#     if "counterfeit" in uri.lower():
#         s += 0.60; reasons.append("COUNTERFEIT_IMAGE_PATTERN")
#     if "missing_signature" in uri.lower():
#         s += 0.35; reasons.append("MISSING_SIGNATURE")
#     if "missing_endorsement" in uri.lower():
#         s += 0.25; reasons.append("MISSING_ENDORSEMENT")
#     fingerprint = hashlib.sha256(uri.encode()).hexdigest()[:32]
#     return ScoreResponse(
#         score=min(s, 0.99),
#         reasonCodes=reasons or ["IMAGE_NO_MAJOR_ANOMALY"],
#         explanation={
#             "imageFingerprint": fingerprint,
#             "ocr": {
#                 "courtesyAmount": str(e.amount),
#                 "legalAmount": str(e.amount),
#                 "amountMismatch": False
#             },
#             "checks": {
#                 "signaturePresent": "missing_signature" not in uri.lower(),
#                 "endorsementPresent": "missing_endorsement" not in uri.lower()
#             }
#         },
#         latencyMs=int((time.time() - start) * 1000)
#     )

def image_process_event(event: ImageAnalysisRequest, db: Session) -> ImageAnalysisResponse:
    start = time.time()
    reasons = []

    front_img = load_image(event.imageFrontUri)

    back_img = None
    if event.imageBackUri:
        try:
            back_img = load_image(event.imageBackUri)
        except Exception:
            reasons.append("BACK_IMAGE_UNREADABLE")

    hash_result = perceptual_hash_score(event.imageFrontUri, event.eventId, db)

    if hash_result["duplicate_score"] >= 0.8:
        reasons.append("DUPLICATE_OR_NEAR_DUPLICATE_IMAGE")

    ocr_text = extract_ocr_text(front_img)
    ocr_amounts = extract_amounts_from_text(ocr_text)
    ocr_match = amount_match_score(event.amount, ocr_amounts)

    if not ocr_match:
        reasons.append("OCR_AMOUNT_MISMATCH")

    layout_score = layout_anomaly_score(front_img)
    if layout_score >= 0.5:
        reasons.append("LAYOUT_ANOMALY")

    font_score = font_anomaly_score(front_img)
    if font_score >= 0.5:
        reasons.append("FONT_INCONSISTENCY")

    sig_score = signature_presence_score(front_img)
    if sig_score < 0.5:
        reasons.append("SIGNATURE_REGION_LOW_INK")

    end_score = endorsement_score(back_img)
    if end_score < 0.5:
        reasons.append("ENDORSEMENT_REGION_LOW_INK")

    latency = int((time.time() - start) * 1000)

    return ImageAnalysisResponse(
        image_duplicate_score=hash_result["duplicate_score"],
        ocr_amount_match=ocr_match,
        layout_anomaly_score=layout_score,
        font_anomaly_score=font_score,
        signature_presence_score=sig_score,
        endorsement_score=end_score,
        reasonCodes=reasons or ["IMAGE_NO_MAJOR_ANOMALY"],
        explanation={
            "ocrTextPreview": ocr_text[:500],
            "ocrAmountsDetected": ocr_amounts,
            "expectedAmount": event.amount,
            "hashing": hash_result,
            "heuristics": {
                "layout": "Hough line structure count",
                "font": "connected component text-height variance",
                "signature": "lower-right edge density",
                "endorsement": "back-image ink density"
            },
            "limitations": [
                "MVP heuristic only",
                "Not forensic-grade document authentication",
                "Should be calibrated with real check images"
            ]
        },
        latencyMs=latency
    )