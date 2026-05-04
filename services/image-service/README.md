# Image-Based Check Fraud Analysis Service
This implementation represents an image fraud detection microservice for a check-fraud detection system. Its goal is to analyze the front and optional back images of a deposited check and produce fraud-relevant image signals such as duplicate detection, OCR amount matching, layout anomaly, font inconsistency, signature presence, and endorsement presence.

### Core Idea
When a check deposit event arrives, the service receives:
```text
eventId
amount
imageFrontUri
imageBackUri
```
The service loads the check image, extracts visual and textual evidence, applies several heuristic checks, and returns a structured risk explanation.
The output is not a final fraud decision. Instead, it produces image-level fraud features that can be consumed by a larger fraud scoring system.

#### Image Duplicate Detection
The service uses perceptual hashing  to detect whether the same or visually similar check image has already appeared. It computes:
```text
pHash
dHash
```
The implementation mainly uses `pHash` for comparison. A `pHash - perceptual hash` is different from a cryptographic hash.
Instead of changing completely when one pixel changes, it remains similar when the image is visually similar.
This allows the system to detect:
```text
same check image reused
slightly modified duplicate
near-duplicate deposit image
```
If the Hamming distance between the current hash and a previous hash is small, the image receives a high duplicate score. In theory:
```text
lower hash distance = higher visual similarity = higher duplicate risk
```
This is useful for detecting duplicate presentment, where the same check image may be deposited multiple times.

A low value suggests high duplicate or near-duplicate, which will trigger:
```text
DUPLICATE_OR_NEAR_DUPLICATE_IMAGE
```

#### OCR-Based Amount Verification
The service applies OCR to the front check image using Tesseract. Before OCR, it preprocesses the image:
```text
grayscale conversion
image resizing
Gaussian blur
Otsu thresholding
```
This improves text visibility for OCR.

Then it extracts money-like values using regular expressions such as:
```text
$1,250.00
1250.00
```
The detected OCR amounts are compared against the expected transaction amount. If no OCR amount matches the submitted amount within a small tolerance, the system adds:
```text
OCR_AMOUNT_MISMATCH
```
The theoretical purpose is to detect possible check alteration, such as when the submitted deposit amount differs from the amount visible on the check.

#### Layout Anomaly Detection
The service uses edge detection and probabilistic Hough line detection to estimate the structure of the check image.
A normal check usually has predictable layout elements:
```text
horizontal lines
signature line
amount box
memo line
routing/account number region
```
The implementation counts detected lines. The logic is:
```text
reasonable number of lines → low anomaly
too few lines → suspicious layout
too many lines → possible noisy or abnormal image
```
This is a heuristic way to detect whether the check image resembles a structured check document.

#### Font Anomaly Detection
The service estimates font inconsistency using connected components. It thresholds the image and detects text-like components. 
Then it measures the height distribution of those components. The assumption is:
```text
normal check text has somewhat consistent component sizes
altered checks may contain pasted or edited text with inconsistent font sizes
```
The implementation calculates the coefficient of variation:
```text
standard deviation / mean height
```
A high value suggests inconsistent text size and may trigger:
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

### Limitations
This implementation is an MVP heuristic system, not a forensic-grade check authentication engine.

Main limitations:
```text
in-memory duplicate hash store is not persistent
OCR may fail on low-quality images
fixed region assumptions may not work for all check templates
heuristic thresholds are not calibrated
font and layout checks are simplistic
no deep learning model is used
```
In production, the in-memory hash store should be replaced with a persistent database table, and thresholds should be calibrated using real check images.