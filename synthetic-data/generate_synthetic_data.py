import argparse, csv, hmac, hashlib, random, uuid
from pathlib import Path
from datetime import datetime, timedelta, timezone
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SECRET = b"demo-consortium-key-change-me"

def tok(value: str) -> str:
    return hmac.new(SECRET, value.encode(), hashlib.sha256).hexdigest()

def write_csv(path, rows):
    if not rows:
        return
    with open(path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

def draw_check_image(path: Path, amount: float, serial: str, scenario: str, front: bool = True):
    width, height = 1200, 520
    bg = 245 if front else 238
    img = Image.new("RGB", (width, height), (bg, bg, bg))
    draw = ImageDraw.Draw(img)

    try:
        font_big = ImageFont.truetype("DejaVuSans.ttf", 42)
        font_med = ImageFont.truetype("DejaVuSans.ttf", 28)
        font_small = ImageFont.truetype("DejaVuSans.ttf", 22)
    except Exception:
        font_big = font_med = font_small = ImageFont.load_default()

    draw.rectangle([20, 20, width - 20, height - 20], outline=(40, 40, 40), width=3)
    draw.text((55, 50), "SYNTHETIC DEMO CHECK", fill=(30, 30, 30), font=font_big)
    draw.text((900, 55), f"No. {serial}", fill=(30, 30, 30), font=font_med)

    if front:
        draw.text((55, 140), "Pay to the order of:", fill=(40, 40, 40), font=font_med)
        draw.line([320, 175, 880, 175], fill=(50, 50, 50), width=2)
        draw.text((900, 135), f"${amount:,.2f}", fill=(20, 20, 20), font=font_big)

        draw.text((55, 235), "Amount in words:", fill=(40, 40, 40), font=font_med)
        draw.line([300, 270, 1100, 270], fill=(50, 50, 50), width=2)

        draw.text((55, 350), "Memo:", fill=(40, 40, 40), font=font_med)
        draw.line([140, 383, 470, 383], fill=(50, 50, 50), width=2)

        draw.text((710, 350), "Signature:", fill=(40, 40, 40), font=font_med)
        if "missing_signature" not in scenario:
            draw.arc([870, 330, 1080, 430], 190, 350, fill=(25, 25, 25), width=3)
            draw.line([880, 385, 1080, 365], fill=(25, 25, 25), width=2)
        draw.line([850, 395, 1100, 395], fill=(50, 50, 50), width=2)

        micr = f"MICR {serial} 123456789 987654321"
        draw.text((90, 455), micr, fill=(10, 10, 10), font=font_med)
    else:
        draw.text((55, 80), "ENDORSE HERE", fill=(30, 30, 30), font=font_big)
        draw.line([55, 150, 1120, 150], fill=(50, 50, 50), width=2)
        if "missing_endorsement" not in scenario:
            draw.text((90, 205), "For mobile deposit only", fill=(20, 20, 20), font=font_med)
            draw.arc([90, 260, 450, 390], 190, 350, fill=(25, 25, 25), width=3)
        draw.rectangle([55, 410, 1120, 465], outline=(80, 80, 80), width=2)
        draw.text((70, 425), "Synthetic back image - demo only", fill=(60, 60, 60), font=font_small)

    if "altered" in scenario:
        draw.rectangle([880, 125, 1110, 205], outline=(200, 50, 50), width=5)
        draw.text((885, 210), "ALTERATION-LIKE REGION", fill=(150, 30, 30), font=font_small)
        for _ in range(12):
            x = random.randint(860, 1110)
            y = random.randint(120, 210)
            draw.line([x, y, x + random.randint(-30, 30), y + random.randint(-10, 10)], fill=(180, 180, 180), width=3)

    if "counterfeit" in scenario:
        for x in range(40, width, 80):
            draw.line([x, 25, x + 420, height - 25], fill=(215, 215, 215), width=2)
        draw.text((720, 455), "LOW TEMPLATE CONFIDENCE", fill=(120, 40, 40), font=font_small)

    if random.random() < 0.4:
        img = img.filter(ImageFilter.SMOOTH)

    img.save(path, format="PNG")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="out")
    ap.add_argument("--banks", type=int, default=5)
    ap.add_argument("--events", type=int, default=2000)
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    now = datetime.now(timezone.utc)

    banks = [
        {
            "institution_id": str(uuid.uuid4()),
            "name": f"Bank-{i+1}",
            "region": random.choice(["NL","ON","BC","AB"])
        }
        for i in range(args.banks)
    ]

    accounts, deposits, clearings, labels, consortium = [], [], [], [], []
    image_dir = out / "images"
    image_dir.mkdir(exist_ok=True)

    raw_accounts = []
    for bank in banks:
        for i in range(250):
            raw = f"{bank['name']}-acct-{i}"
            raw_accounts.append((bank, raw))
            accounts.append({
                "institution_id": bank["institution_id"],
                "account_token": tok(raw),
                "account_type": random.choice(["personal","business"]),
                "risk_tier": random.choices(["LOW","MEDIUM","HIGH"], [0.8,0.15,0.05])[0]
            })

    duplicate_templates = []
    for _ in range(60):
        routing = tok("routing-" + str(random.randint(100, 999)))
        acct = tok("payor-" + str(random.randint(1000, 9999)))
        serial = str(random.randint(100000, 999999))
        duplicate_templates.append((routing, acct, serial))

    for i in range(args.events):
        bank, acct_raw = random.choice(raw_accounts)
        is_dup = random.random() < 0.07
        is_altered = random.random() < 0.04
        is_counterfeit = random.random() < 0.03
        is_mule = random.random() < 0.05

        if is_dup:
            routing, micr_acct, serial = random.choice(duplicate_templates)
        else:
            routing = tok("routing-" + str(random.randint(100, 999)))
            micr_acct = tok("payor-" + str(random.randint(1000, 9999)))
            serial = str(random.randint(100000, 999999))

        amount = round(random.lognormvariate(6.8, 0.65), 2)
        if is_altered or is_counterfeit:
            amount *= random.uniform(2.0, 6.0)
        amount = min(amount, 50000.00)

        payee_raw = f"payee-{random.randint(1, 500)}"
        if is_mule:
            payee_raw = f"mule-payee-{random.randint(1, 15)}"

        device_raw = f"device-{random.randint(1, 1500)}"
        if random.random() < 0.08:
            device_raw += "-NEW"

        suffix = []
        if is_altered:
            suffix.append("altered")
        if is_counterfeit:
            suffix.append("counterfeit")
        if random.random() < 0.02:
            suffix.append("missing_signature")
        if random.random() < 0.02:
            suffix.append("missing_endorsement")

        scenario = ",".join([
            name for name, flag in [
                ("duplicate", is_dup),
                ("altered", is_altered),
                ("counterfeit", is_counterfeit),
                ("mule", is_mule)
            ] if flag
        ]) or "normal"

        image_tag = "_".join(suffix) if suffix else "normal"
        image_name = f"check_{i}_{image_tag}.png"
        back_image_name = f"back_check_{i}_{image_tag}.png"

        draw_check_image(image_dir / image_name, amount, serial, image_tag, front=True)
        draw_check_image(image_dir / back_image_name, amount, serial, image_tag, front=False)

        event_id = str(uuid.uuid4())
        ts = now - timedelta(minutes=random.randint(0, 60*24*30))

        deposits.append({
            "event_id": event_id,
            "institution_id": bank["institution_id"],
            "channel": random.choice(["mobile","ATM","branch"]),
            "deposit_timestamp": ts.isoformat(),
            "amount": f"{amount:.2f}",
            "currency": "CAD",
            "account_token": tok(acct_raw),
            "payee_token": tok(payee_raw),
            "device_token": tok(device_raw),
            "region": random.choice(["NL","ON","BC","AB","OUT_OF_REGION"]),
            "check_serial": serial,
            "micr_routing_hash": routing,
            "micr_account_hash": micr_acct,
            "image_front_uri": str((image_dir / image_name).resolve()),
            "image_back_uri": str((image_dir / back_image_name).resolve())
        })

        clearings.append({
            "clearing_event_id": str(uuid.uuid4()),
            "deposit_event_id": event_id,
            "presentment_timestamp": (ts + timedelta(hours=random.randint(1,48))).isoformat(),
            "amount": f"{amount:.2f}",
            "drawee_routing_hash": routing,
            "return_code": "" if random.random() > 0.08 else random.choice(["DUP","ALTERED","NSF","FRAUD"])
        })

        labels.append({
            "event_id": event_id,
            "label": "fraud" if scenario != "normal" else "legit",
            "scenario": scenario
        })

        consortium.append({
            "event_id": event_id,
            "token_type": "micr_check",
            "token_value_hash": tok(f"{routing}:{micr_acct}:{serial}"),
            "institution_count": "",
            "fraud_count_7d": "",
            "fraud_count_30d": "",
            "duplicate_appearance_count": "",
            "network_risk_score": ""
        })

    write_csv(out/"institutions.csv", banks)
    write_csv(out/"accounts.csv", accounts)
    write_csv(out/"deposit_events.csv", deposits)
    write_csv(out/"clearing_events.csv", clearings)
    write_csv(out/"fraud_labels.csv", labels)
    write_csv(out/"consortium_events.csv", consortium)
    print(f"Wrote synthetic data to {out}")
    print(f"Wrote valid PNG images to {image_dir}")

if __name__ == "__main__":
    main()
