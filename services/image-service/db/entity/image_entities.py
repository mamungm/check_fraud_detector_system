from sqlalchemy import Column, Integer, String, DateTime, Index
from sqlalchemy.sql import func
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()

class ImageFingerprintEntity(Base):
    __tablename__ = "image_fingerprint"

    id = Column(Integer, primary_key=True, autoincrement=True)
    event_id = Column(String(128), nullable=False, unique=True, index=True)
    phash = Column(String(64), nullable=False, index=True)   # store hex string
    dhash = Column(String(64), nullable=True)
    content_hash = Column(String(64), nullable=True, index=True)   # sha256 of decoded grayscale pixels (exact duplicates)
    check_key = Column(String(64), nullable=True, index=True)      # sha256 of routing|account|serial (check identity)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    __table_args__ = (
        Index("ix_image_phash", "phash"),
    )