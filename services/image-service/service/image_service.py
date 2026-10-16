import hashlib
import os
import re
import time
from urllib.parse import unquote, urlparse
from typing import Dict, Any, List, Optional

import cv2
import imagehash
import numpy as np
import pytesseract
from PIL import Image
from sqlalchemy.orm import Session

from db.repo.image_repositories import get_duplicate_candidates, upsert_image_fingerprint
from dtos.image_dtos import ImageAnalysisRequest, ImageAnalysisResponse


def to_local_path(uri: str) -> str:
    """Accepts a plain path or a file:// URI (file:///abs/path or file:/abs/path)."""
    if uri.startswith("file:"):
        return unquote(urlparse(uri).path)
    return uri


def load_image(path: str) -> np.ndarray:
    path = to_local_path(path)

    if not os.path.exists(path):
        raise FileNotFoundError(f"Image not found: {path}")

    img = cv2.imread(path)
    if img is None:
        raise ValueError(f"Could not read image: {path}")

    return img


def compute_check_key(event: ImageAnalysisRequest) -> Optional[str]:
    """Identity of the physical check (routing | account | serial). None if no MICR data was sent."""
    if not (event.checkSerialHash or event.micrAccountHash or event.micrRoutingHash):
        return None
    raw = f"{event.micrRoutingHash}|{event.micrAccountHash}|{event.checkSerialHash}"
    return hashlib.sha256(raw.encode()).hexdigest()


# pHash distance up to this counts as visually the same image
PHASH_DUPLICATE_MAX_DISTANCE = 5
DUPLICATE_LOOKBACK_DAYS = 90


def perceptual_hash_score(image_path: str, event_id: str, db: Session,
                          check_key: Optional[str] = None) -> Dict[str, Any]:
    """
    Duplicate detection. A pHash match alone is NOT enough: checks from one template look
    identical to a perceptual hash (only the serial/amount text differs), so every check would be
    flagged as a duplicate of every other. A stored check counts as a duplicate when:
      - its decoded image content is byte-identical (exact resubmission), or
      - the pHash is close AND it has the same check identity (re-scan / re-photo of the same check).
    Without MICR data only exact resubmissions are detected.
    """
    pil_img = Image.open(to_local_path(image_path)).convert("L")

    phash = imagehash.phash(pil_img)    # compact "fingerprint" of an image by analyzing its visual features
    dhash = imagehash.dhash(pil_img)    # captures gradient information by comparing adjacent pixel values, useful for detecting subtle alterations

    phash_hex = str(phash)
    dhash_hex = str(dhash)
    content_hash = hashlib.sha256(pil_img.tobytes() + str(pil_img.size).encode()).hexdigest()

    duplicate_score = 0.0
    closest_event = None
    closest_distance = None
    basis = None

    candidates = get_duplicate_candidates(event_id, content_hash, check_key, DUPLICATE_LOOKBACK_DAYS, db)

    for row in candidates:
        distance = phash - imagehash.hex_to_hash(str(row.phash))

        if closest_distance is None or distance < closest_distance:
            closest_distance = distance
            closest_event = row.event_id

        if row.content_hash == content_hash:
            score, how = 1.0, "EXACT_IMAGE_CONTENT"
        elif check_key and row.check_key == check_key and distance <= PHASH_DUPLICATE_MAX_DISTANCE:
            score, how = 1.0 - (distance / 10.0), "PHASH_AND_SAME_CHECK_IDENTITY"
        else:
            continue

        if score > duplicate_score:
            duplicate_score, basis = score, how

    upsert_image_fingerprint(event_id, phash_hex, dhash_hex, db, content_hash=content_hash, check_key=check_key)

    return {
        "phash": phash_hex,
        "dhash": dhash_hex,
        "duplicate_score": float(round(duplicate_score, 4)),
        "duplicate_basis": basis,
        "closest_event": closest_event,
        "closest_distance": int(closest_distance) if closest_distance is not None else None
    }

# Region of the amount field (top-right of the front image) as fractions of (y0, y1, x0, x1).
# Like the signature/endorsement regions this assumes a typical check template.
AMOUNT_REGION = (0.20, 0.46, 0.70, 0.97)


# Preprocessing variants tried in order until one reads the expected amount.
# (region, scale, binarize, tesseract page-segmentation mode, restrict characters to money symbols)
#   binarize: None = grayscale as is, "otsu" = Otsu threshold
OCR_VARIANTS = [
    ("amount", 4, None, 7, True),
    ("amount", 4, "otsu", 6, True),
    ("amount", 5, "otsu", 7, True),
    ("page", 3, "otsu", 6, False),
]
MONEY_WHITELIST = "-c tessedit_char_whitelist=$0123456789,."


def _crop_amount_region(img: np.ndarray) -> np.ndarray:
    h, w = img.shape[:2]
    y0, y1, x0, x1 = AMOUNT_REGION
    return img[int(y0 * h):int(y1 * h), int(x0 * w):int(x1 * w)]


def extract_ocr_text(img: np.ndarray, region: str = "page", scale: float = 1.5, binarize: Optional[str] = "otsu",
                     psm: int = 3, money_only: bool = False) -> str:
    if region == "amount":
        img = _crop_amount_region(img)

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # Small text needs a lot of upscaling for Tesseract to read it
    gray = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    if binarize == "otsu":
        gray = cv2.GaussianBlur(gray, (3, 3), 0)
        _, gray = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    config = f"--psm {psm}" + (f" {MONEY_WHITELIST}" if money_only else "")
    return pytesseract.image_to_string(gray, config=config)


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

    # OCR often drops or misplaces the separators ("$48153", "$7 16.29"). After a "$", read the
    # run of digits and treat the last two as cents.
    for run in re.findall(r"\$\s*([0-9][0-9,.\s]{2,})", text):
        digits = re.sub(r"\D", "", run)
        if len(digits) >= 3:
            amounts.append(int(digits) / 100.0)

    return list(set(amounts))


def amount_match_score(expected_amount: float, ocr_amounts: List[float]) -> bool:
    """Exact match in cents. Any difference in the amount is relevant for alteration detection."""
    expected_cents = round(expected_amount * 100)
    return any(round(amt * 100) == expected_cents for amt in ocr_amounts)


def verify_amount(img: np.ndarray, expected_amount: float) -> Dict[str, Any]:
    """
    Tries the OCR variants in order and stops at the first one that reads the expected amount.
    If none does, the result is a mismatch (an amount was read but differs) or unreadable
    (no amount could be read at all).
    """
    all_amounts: set = set()
    text = ""
    attempts = 0

    for region, scale, binarize, psm, money_only in OCR_VARIANTS:
        attempts += 1
        text = extract_ocr_text(img, region, scale, binarize, psm, money_only)
        amounts = extract_amounts_from_text(text)
        all_amounts.update(amounts)
        if amount_match_score(expected_amount, amounts):
            return {"match": True, "readable": True, "amounts": sorted(all_amounts), "text": text, "attempts": attempts}

    return {"match": False, "readable": bool(all_amounts), "amounts": sorted(all_amounts), "text": text,
            "attempts": attempts}


def _long_lines(mask: np.ndarray, min_len: int, max_gap: int, threshold: int = 80) -> np.ndarray:
    lines = cv2.HoughLinesP(mask, rho=1, theta=np.pi / 180, threshold=threshold,
                            minLineLength=min_len, maxLineGap=max_gap)
    return np.empty((0, 4), dtype=int) if lines is None else lines.reshape(-1, 4)


def layout_anomaly_stats(img: np.ndarray) -> Dict[str, int]:
    """
    Structure counts used by the layout heuristic (all sizes relative to the image width):
    - horizontal_rules: long dark horizontal lines (payee/amount-words/memo/signature lines, border).
    - oblique_lines: long straight lines that are neither horizontal nor vertical, found in a
      "faint ink" mask (pixels slightly different from the page background). Security patterns on
      forgeries or pasted templates often show up like this; real form lines are axis aligned.
    """
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    h, w = gray.shape

    # dark horizontal rules via morphological opening
    _, dark = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    opened = cv2.morphologyEx(dark, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (max(int(0.15 * w), 3), 1)))
    n, _, stats, _ = cv2.connectedComponentsWithStats(opened)
    horizontal_rules = sum(1 for i in range(1, n) if stats[i][2] >= 0.15 * w and stats[i][3] <= 0.02 * h)

    # faint, long, oblique lines
    background = int(np.median(gray))
    deviation = np.abs(gray.astype(np.int16) - background)
    faint = ((deviation >= 12) & (deviation <= 60)).astype(np.uint8) * 255
    oblique = 0
    for x1, y1, x2, y2 in _long_lines(faint, min_len=int(0.2 * w), max_gap=int(0.0125 * w)):
        angle = abs(np.degrees(np.arctan2(y2 - y1, x2 - x1))) % 180
        if 8 <= angle <= 82 or 98 <= angle <= 172:
            oblique += 1

    return {"horizontal_rules": horizontal_rules, "oblique_lines": oblique}


def layout_anomaly_score(img: np.ndarray) -> float:
    """
    Heuristic (scale independent):
    - 3 or more long oblique lines            -> 0.8  (pattern that does not belong on the form)
    - fewer than 3 horizontal form rules      -> 0.7  (expected check structure is missing)
    - 1-2 oblique lines                       -> 0.5
    - otherwise                               -> 0.1
    """
    stats = layout_anomaly_stats(img)

    if stats["oblique_lines"] >= 3:
        return 0.8
    if stats["horizontal_rules"] < 3:
        return 0.7
    if stats["oblique_lines"] >= 1:
        return 0.5
    return 0.1


def font_anomaly_score(img: np.ndarray) -> float:
    """
    Looks for signs of tampering in the amount field only (that is where alterations happen):
    - Glyph height variation: text-like connected components are measured with sizes relative to
      the image, so the result does not depend on resolution or font size. Pasted or overwritten
      digits and stray marks make the heights uneven (score = 3 x coefficient of variation).
    - Foreign ink: a clear share of strongly coloured pixels in the amount field while the rest of
      the check is (almost) monochrome scores 0.9.
    - Fewer than 3 glyphs in the amount field -> 0.3, the field cannot be judged (neutral, below the
      0.5 reason threshold; a short amount such as "$5.00" legitimately has few components).
    """
    h, w = img.shape[:2]
    y0, y1, x0, x1 = AMOUNT_REGION
    roi = img[int(y0 * h):int(y1 * h), int(x0 * w):int(x1 * w)]

    # foreign ink colour
    saturated = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)[..., 1] > 60
    mask = np.zeros(saturated.shape, dtype=bool)
    mask[int(y0 * h):int(y1 * h), int(x0 * w):int(x1 * w)] = True
    roi_colored = float(saturated[mask].mean())
    rest_colored = float(saturated[~mask].mean())
    if roi_colored > 0.01 and roi_colored > 3 * rest_colored:
        return 0.9

    # glyph height variation
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    _, binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    num_labels, _, stats, _ = cv2.connectedComponentsWithStats(binary)

    heights = [
        stats[i][3] for i in range(1, num_labels)
        if 0.008 * h <= stats[i][3] <= 0.15 * h and stats[i][2] <= 0.2 * w and stats[i][4] >= 4
    ]

    if len(heights) < 3:
        return 0.3

    mean = float(np.mean(heights))
    coeff_var = float(np.std(heights)) / mean if mean > 0 else 1.0

    return round(min(coeff_var * 3.0, 1.0), 4)


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
    if not event.imageBackUri:
        reasons.append("BACK_IMAGE_NOT_PROVIDED")
    else:
        try:
            back_img = load_image(event.imageBackUri)
        except Exception:
            reasons.append("BACK_IMAGE_UNREADABLE")

    hash_result = perceptual_hash_score(event.imageFrontUri, event.eventId, db, compute_check_key(event))

    if hash_result["duplicate_score"] >= 0.8:
        reasons.append("DUPLICATE_OR_NEAR_DUPLICATE_IMAGE")

    ocr = verify_amount(front_img, event.amount)
    ocr_text, ocr_amounts, ocr_match = ocr["text"], ocr["amounts"], ocr["match"]

    if not ocr_match:
        # "read a different amount" (possible alteration) vs "could not read an amount at all"
        reasons.append("OCR_AMOUNT_MISMATCH" if ocr["readable"] else "OCR_AMOUNT_UNREADABLE")

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
    # Only judge the endorsement when a back image was actually analysed.
    if back_img is not None and end_score < 0.5:
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