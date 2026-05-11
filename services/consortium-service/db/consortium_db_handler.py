from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from contextlib import contextmanager
from db.entities.consortium_entities import Base, ConsortiumEventEntity

db_url = "postgresql://fraud:fraud@localhost:5432/frauddb"
engine = create_engine(db_url)
session_local = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def create_all_schema():
    Base.metadata.create_all(engine)

def get_db_session():
    db = session_local()
    try:
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()

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

def get_all_consortium_events(db: Session) -> list[type[ConsortiumEventEntity]]:
    return db.query(ConsortiumEventEntity).all()

def add_consortium_event(consortium_event: ConsortiumEventEntity, db: Session):
    db.add(consortium_event)