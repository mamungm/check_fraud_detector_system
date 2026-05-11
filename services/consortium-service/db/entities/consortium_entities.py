from sqlalchemy import Column, String, DateTime, Float, Enum
from sqlalchemy.ext.declarative import declarative_base
from dtos.consortium_dtos import FraudDisposition

Base = declarative_base()

class ConsortiumEventEntity(Base):
    __tablename__ = "consortium_events"

    eventId = Column(String, primary_key=True, index=True)
    institutionId = Column(String)
    clearingInstitutionId = Column(String, nullable=True, default=None)

    channel = Column(String, nullable=False)

    depositTimestamp = Column(DateTime, nullable=False)

    accountToken = Column(String, nullable=True, default=None)
    payeeToken = Column(String, nullable=True, default=None)
    payorToken = Column(String, nullable=True, default=None)
    deviceToken = Column(String, nullable=True, default=None)

    region = Column(String, nullable=True, default=None)
    checkSerialHash = Column(String, nullable=False)

    micrRoutingHash = Column(String, nullable=True, default=None)
    micrAccountHash = Column(String, nullable=True, default=None)

    imageFrontUri = Column(String, nullable=True, default=None)
    imageBackUri = Column(String, nullable=True, default=None)

    amount = Column(Float, nullable=False)
    currency = Column(String, nullable=False)
    fraudDisposition = Column(Enum(FraudDisposition), default=FraudDisposition.UNKNOWN, nullable=False)
    imageFingerprint = Column(String, nullable=True)