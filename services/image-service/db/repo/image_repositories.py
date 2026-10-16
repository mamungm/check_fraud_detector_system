from sqlalchemy import or_
from sqlalchemy.orm import Session
from datetime import datetime, timezone, timedelta

from db.entity.image_entities import ImageFingerprintEntity


# ---------------------------------------
# Repo methods for ImageFingerprintEntity
# ---------------------------------------
def add_image_fingerprint(row: ImageFingerprintEntity, db: Session) -> None:
    db.add(row)

def upsert_image_fingerprint(event_id: str, phash: str, dhash: str | None, db: Session,
                             content_hash: str | None = None, check_key: str | None = None) -> None:
    existing = db.query(ImageFingerprintEntity).filter_by(event_id=event_id).one_or_none()
    if existing:
        existing.phash = phash
        existing.dhash = dhash
        existing.content_hash = content_hash
        existing.check_key = check_key
    else:
        db.add(ImageFingerprintEntity(event_id=event_id, phash=phash, dhash=dhash,
                                      content_hash=content_hash, check_key=check_key))

def get_duplicate_candidates(event_id: str, content_hash: str | None, check_key: str | None,
                             within_days: int, db: Session) -> list[type[ImageFingerprintEntity]]:
    """Fingerprints of OTHER events that could be duplicates of this one: identical image content,
    or the same check identity. The event itself (e.g. a Kafka retry) is never a candidate."""
    conditions = []
    if content_hash:
        conditions.append(ImageFingerprintEntity.content_hash == content_hash)
    if check_key:
        conditions.append(ImageFingerprintEntity.check_key == check_key)
    if not conditions:
        return []
    cutoff = datetime.now(timezone.utc) - timedelta(days=within_days)
    return (
        db.query(ImageFingerprintEntity)
        .filter(ImageFingerprintEntity.event_id != event_id)
        .filter(ImageFingerprintEntity.created_at >= cutoff)
        .filter(or_(*conditions))
        .order_by(ImageFingerprintEntity.created_at.desc())
        .limit(500)
        .all()
    )

def get_image_by_event_id(event_id: str, db: Session) -> type[ImageFingerprintEntity] | None:
    return db.query(ImageFingerprintEntity).filter_by(event_id=event_id).one_or_none()

def get_recent_image_hashes(limit: int = 500, within_days: int | None = 30, db: Session = None) -> list[
    type[ImageFingerprintEntity]]:
    q = db.query(ImageFingerprintEntity)
    if within_days is not None:
        cutoff = datetime.now(timezone.utc) - timedelta(days=within_days)
        q = q.filter(ImageFingerprintEntity.created_at >= cutoff)
    return q.order_by(ImageFingerprintEntity.created_at.desc()).limit(limit).all()