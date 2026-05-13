from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from contextlib import contextmanager
from db.entity.consortium_entities import Base

db_url = "postgresql://fraud:fraud@localhost:5432/frauddb"
engine = create_engine(db_url)
session_local = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def create_all_schema():
    Base.metadata.create_all(engine)

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