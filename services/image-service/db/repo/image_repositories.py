from sqlalchemy.orm import Session
from datetime import datetime, timezone, timedelta

from db.entity.image_entities import ImageFingerprintEntity


# ---------------------------------------
# Repo methods for ImageFingerprintEntity
# ---------------------------------------
def add_image_fingerprint(row: ImageFingerprintEntity, db: Session) -> None:
    db.add(row)

def upsert_image_fingerprint(event_id: str, phash: str, dhash: str | None, db: Session) -> None:
    existing = db.query(ImageFingerprintEntity).filter_by(event_id=event_id).one_or_none()
    if existing:
        existing.phash = phash
        existing.dhash = dhash
    else:
        db.add(ImageFingerprintEntity(event_id=event_id, phash=phash, dhash=dhash))

def get_image_by_event_id(event_id: str, db: Session) -> type[ImageFingerprintEntity] | None:
    return db.query(ImageFingerprintEntity).filter_by(event_id=event_id).one_or_none()

def get_recent_image_hashes(limit: int = 500, within_days: int | None = 30, db: Session = None) -> list[
    type[ImageFingerprintEntity]]:
    q = db.query(ImageFingerprintEntity)
    if within_days is not None:
        cutoff = datetime.now(timezone.utc) - timedelta(days=within_days)
        q = q.filter(ImageFingerprintEntity.created_at >= cutoff)
    return q.order_by(ImageFingerprintEntity.created_at.desc()).limit(limit).all()