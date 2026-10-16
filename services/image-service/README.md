# Image-Based Check Fraud Analysis Service
This implementation represents an image fraud detection microservice for a check-fraud detection system. Its goal is to analyze the front and optional back images of a deposited check and produce fraud-relevant image signals such as duplicate detection, OCR amount matching, layout anomaly, font inconsistency, signature presence, and endorsement presence.

### Core Idea
When a check deposit event arrives, the service receives:
```text
eventId
amount
imageFrontUri
imageBackUri                (optional)
micrRoutingHash             (optional)
micrAccountHash             (optional)
checkSerialHash             (optional)
```
Image URIs can be a plain local path or a `file://` URI. The MICR/serial hashes are optional, but they are what makes duplicate detection reliable (see below).

#### Interfaces
| Interface | Purpose |
|---|---|
| `POST /image/analyze` | Analyse one deposit's images and return an `ImageAnalysisResponse`. |
| `GET /image/health` | Liveness check. |
| Kafka `Image_Service_CMD` (in) | Same as `/image/analyze`. The result is reported on `Image_Service_Response` (`details` holds the response). |

The service also subscribes to `check.deposit.fraud.decision`, but that topic is currently ignored.

The service loads the check image, extracts visual and textual evidence, applies several heuristic checks, and returns a structured risk explanation.
The output is not a final fraud decision. Instead, it produces image-level fraud features that can be consumed by a larger fraud scoring system.

#### Image Duplicate Detection
For every front image the service computes and stores in the `image_fingerprint` table:
```text
pHash          perceptual hash
dHash          gradient hash
content_hash   SHA-256 of the decoded grayscale pixels (exact duplicates)
check_key      SHA-256 of routingHash | accountHash | serialHash (identity of the physical check)
```
A `pHash - perceptual hash` is different from a cryptographic hash. Instead of changing completely when one pixel changes, it stays similar when the image is visually similar.

**A pHash match alone is not treated as a duplicate.** Checks printed from one template look the same to a perceptual hash, because only the serial number and amount text differ. On the synthetic data every check was within distance 2 of every other, so every check was flagged as a duplicate of the previous one. A stored check from another event counts as a duplicate when:
```text
its image content is byte-identical (content_hash)                         -> exact resubmission
OR  pHash distance <= 5  AND  same check_key                              -> re-scan / re-photo of the same check
```
For the second case the score is `1.0 - distance / 10`. Without MICR/serial data in the request, only exact resubmissions are detected. The event being analysed is never compared with itself, so Kafka retries of the same `eventId` do not flag themselves. Candidates are looked up by `content_hash` / `check_key` within the last 90 days (up to 500 rows).

A score of 0.8 or more (pHash distance of 2 or less for a re-scan) triggers:
```text
DUPLICATE_OR_NEAR_DUPLICATE_IMAGE
```
The response explanation shows `duplicate_basis` (`EXACT_IMAGE_CONTENT` or `PHASH_AND_SAME_CHECK_IDENTITY`) and the closest stored event.
This is useful for detecting duplicate presentment, where the same check may be deposited more than once, possibly at different banks.

#### OCR-Based Amount Verification
The service reads the courtesy amount from the front image with Tesseract and compares it with the submitted `amount`.
Text on a check is small, so the page is not OCR'd as it is. The amount field (top-right region) is cropped, upscaled 4-5 times and read in single-line mode with the characters restricted to `$ 0-9 , .`. Up to four variants are tried in order and the check stops at the first one that reads the expected amount:
```text
1. amount field, 4x, grayscale, single line
2. amount field, 4x, Otsu threshold, text block
3. amount field, 5x, Otsu threshold, single line
4. whole page, 3x, Otsu threshold (fallback in case the amount is not in the usual place)
```
Amounts are then extracted with regular expressions such as:
```text
$1,250.00
1250.00
$48153      (separators lost: the last two digits are read as cents)
$7 16.29    (stray space)
```
The amount must match exactly in cents. Any difference counts, because any change of the amount is relevant for alteration detection. The result is one of:
```text
amount read and equal              -> ocr_amount_match = true
amount read but different          -> ocr_amount_match = false, OCR_AMOUNT_MISMATCH   (possible alteration)
no amount could be read at all     -> ocr_amount_match = false, OCR_AMOUNT_UNREADABLE (the field cannot be confirmed)
```
`ocr_amount_match` therefore means "the amount was confirmed", and the reason code tells the two failure cases apart. The response explanation lists every amount that was detected.

#### Layout Anomaly Detection
The service checks whether the front image has the structure of a check form. All sizes are relative to the image width, so the result does not depend on resolution. It counts:
```text
horizontal rules   long dark horizontal lines (payee, amount-in-words, memo, signature lines, border)
oblique lines      long straight lines that are neither horizontal nor vertical, found in a "faint ink"
                   mask (pixels slightly different from the page background)
```
The score is:
```text
3 or more oblique lines          -> 0.8   a pattern that does not belong on a form (typical of forged templates)
fewer than 3 horizontal rules    -> 0.7   the expected form structure is missing
1-2 oblique lines                -> 0.5
otherwise                        -> 0.1
```
A score of 0.5 or more adds:
```text
LAYOUT_ANOMALY
```
Faint background security patterns on real checks could also look like oblique lines. This is a known assumption of the heuristic.

#### Font Anomaly Detection
Alterations happen in the amount field, so the check looks only at that region (top-right of the front image). It uses two signals:
```text
glyph height variation   text-like connected components, sized relative to the image; score = 3 x (std / mean of heights)
foreign ink              a clear share of strongly coloured pixels in the amount field while the rest of the
                         check is almost monochrome -> 0.9
```
Pasted or overwritten digits and stray marks make glyph heights uneven. If fewer than 3 glyphs can be measured the field cannot be judged and the score is a neutral 0.3, because a short amount such as `$5.00` legitimately has few components.
A score of 0.5 or more adds:
```text
FONT_INCONSISTENCY
```

#### Signature Presence Detection
The service checks the lower-right region of the front image, where signatures usually appear. It computes edge density in that region.
The assumption is:
```text
signature present → enough ink/edge activity
signature missing → low edge density
```
If the edge density is low, the service assigns a low signature score and adds:
```text
SIGNATURE_REGION_LOW_INK
```

#### Endorsement Detection
For the back image, the service checks the upper/middle region where endorsement marks are commonly expected.
It uses thresholding and ink-density estimation. The assumption is:
```text
endorsement present → enough dark pixels / ink density
endorsement missing → low ink density
```
If the endorsement score is low, it adds:
```text
ENDORSEMENT_REGION_LOW_INK
```
This is only evaluated when a back image was analysed. With no back image the endorsement score is 0.0, the response includes `BACK_IMAGE_NOT_PROVIDED`, and no endorsement reason is added.

### Reason Code Reference
The response has no single score. It returns six features and a list of reason codes:

| Reason code | Condition | Feature / measurement |
|---|---|---|
| `DUPLICATE_OR_NEAR_DUPLICATE_IMAGE` | `image_duplicate_score >= 0.8` | exact content match, or pHash distance of 2 or less with the same check identity |
| `OCR_AMOUNT_MISMATCH` | an amount was read but none equals `amount` (exact cents) | `ocr_amount_match` is false |
| `OCR_AMOUNT_UNREADABLE` | no amount could be read from the image | `ocr_amount_match` is false |
| `LAYOUT_ANOMALY` | `layout_anomaly_score >= 0.5` | 3 or more oblique lines (0.8), fewer than 3 horizontal rules (0.7), 1-2 oblique lines (0.5); 0.1 otherwise |
| `FONT_INCONSISTENCY` | `font_anomaly_score >= 0.5` | glyph height variation or foreign ink colour in the amount field; 0.3 (neutral) if fewer than 3 glyphs |
| `SIGNATURE_REGION_LOW_INK` | `signature_presence_score < 0.5` | edge density of 0.007 or less in the lower-right region |
| `ENDORSEMENT_REGION_LOW_INK` | `endorsement_score < 0.5` | ink density of 0.012 or less in the back image |
| `BACK_IMAGE_NOT_PROVIDED` | no `imageBackUri` | informational |
| `BACK_IMAGE_UNREADABLE` | back image could not be loaded | informational |

If nothing fires the response contains `IMAGE_NO_MAJOR_ANOMALY`. An unreadable front image fails the request.

### Limitations
This implementation is an MVP heuristic system, not a forensic-grade check authentication engine.

Measured on the synthetic images in `synthetic-data/out/images` (200 normal checks and every fraud sample):
```text
Class                  LAYOUT_ANOMALY   FONT_INCONSISTENCY   SIGNATURE_LOW   ENDORSEMENT_LOW
normal                 0%               0%                   0%              0%
altered (100)          2%               100%                 -               -
counterfeit (56)       100%             16%                  -               -
missing signature      0%               0%                   44%             -
missing endorsement    0%               0%                   0%              100%
```
The 2% layout and 16% font hits on those classes come from combined samples and from counterfeit diagonal lines crossing the amount field.

OCR amount matching on the same images (the synthetic generator draws the true amount on every check, so the target is a match). The tampered column is the same image checked against an amount with one digit changed, which must not match:
```text
Class                  correct amount matched   tampered amount wrongly matched
normal                 80/80                    0/80
missing signature      19/20                    0/20
missing endorsement    20/20                    0/20
altered                33/40  (3 unreadable)    0/40
counterfeit            13/25                    0/25
```
The altered and counterfeit misses are mostly caused by the generator itself: the faint diagonal lines of counterfeits and the scribbles of altered checks are drawn over the amount and overwrite digit strokes, so the digits are physically damaged. The previous OCR matched only 3 of 50 checks. The synthetic generator falls back to Pillow's tiny default font (8 px text), which is why the layout and font thresholds are relative to the image size and not fixed pixel values.

Other limitations:
```text
OCR may fail on low-quality images, and the amount field position is assumed to be the usual top-right area (the page-wide fallback is slower and less reliable)
OCR takes up to about 0.3 s for a clean check and about 1 s when all four variants are needed
fixed region assumptions may not work for all check templates
heuristic thresholds are not calibrated
no deep learning model is used
duplicate detection needs MICR/serial data to catch re-scans, exact copies are always detected
the training data (synthetic-data/generate_training_table.py) simulates these image features from the fraud
label instead of computing them with this service, so a model trained on it does not see the same feature
distributions that this service produces
```
In production, thresholds should be calibrated using real check images, and the model should be trained on features produced by this service.
