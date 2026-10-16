from sqlalchemy import create_engine, inspect, text
import os
from sqlalchemy.orm import sessionmaker
from contextlib import contextmanager

from db.entity.image_entities import Base

db_url = os.getenv("DATABASE_URL", "postgresql://fraud:fraud@host.docker.internal:5432/frauddb")
engine = create_engine(db_url)
session_local = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Columns added after the first release. create_all() never alters an existing table.
_ADDED_COLUMNS = {
    "image_fingerprint": {"content_hash": "VARCHAR(64)", "check_key": "VARCHAR(64)"},
}


def _add_missing_columns():
    inspector = inspect(engine)
    with engine.begin() as conn:
        for table, columns in _ADDED_COLUMNS.items():
            if not inspector.has_table(table):
                continue
            existing = {c["name"] for c in inspector.get_columns(table)}
            for name, ddl in columns.items():
                if name not in existing:
                    conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {name} {ddl}"))


def create_all_schema():
    Base.metadata.create_all(engine)
    _add_missing_columns()

@contextmanager
def get_db_session_from_context():
    db = session_local()
    try:
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()