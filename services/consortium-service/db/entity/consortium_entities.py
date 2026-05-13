from sqlalchemy import Column, String, DateTime, Float, Enum, Integer, Index, func
from sqlalchemy.ext.declarative import declarative_base
from dtos.consortium_dtos import FraudDisposition
from enum import Enum as TypeEnum

Base = declarative_base()

class TokenType(str, TypeEnum):
    UNKNOWN = "UNKNOWN"
    ACCOUNT = "ACCOUNT"
    PAYEE = "PAYEE"
    PAYOR = "PAYOR"
    DEVICE = "DEVICE"
    IMAGE = "IMAGE"

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


class TokenIndexEntity(Base):
    __tablename__ = "token_index"

    id = Column(Integer, primary_key=True, autoincrement=True)
    token_type = Column(Enum(TokenType), default=TokenType.UNKNOWN, nullable=False)
    token_value = Column(String(256), nullable=False)    # token hash/fingerprint
    event_id = Column(String(64), nullable=False)        # join key to event
    institution_id = Column(String(64), nullable=False)
    deposit_timestamp = Column(DateTime(timezone=True), nullable=False)
    fraud_disposition = Column(String(32), nullable=False, default="UNKNOWN")
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    __table_args__ = (
        Index("ix_token_index_type_value", "token_type", "token_value"),
        Index("ix_token_index_ts", "deposit_timestamp"),
        Index("ix_token_index_event_id", "event_id"),
    )


class RelationIndexEntity(Base):
    __tablename__ = "relation_index"

    id = Column(Integer, primary_key=True, autoincrement=True)
    payor_token = Column(String, nullable=False)
    payee_token = Column(String, nullable=False)
    event_id = Column(String, nullable=False, index=True)
    institution_id = Column(String, nullable=False)
    deposit_timestamp = Column(DateTime(timezone=True), nullable=False)
    fraud_disposition = Column(String, nullable=False, default="UNKNOWN")
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    __table_args__ = (
        Index("ix_relation_payor_payee_ts", "payor_token", "payee_token", "deposit_timestamp"),
    )


class BankFlowIndexEntity(Base):
    __tablename__ = "bank_flow_index"

    id = Column(Integer, primary_key=True, autoincrement=True)
    deposit_institution = Column(String, nullable=False)
    clearing_institution = Column(String, nullable=False)
    event_id = Column(String, nullable=False, index=True)
    deposit_timestamp = Column(DateTime(timezone=True), nullable=False)
    fraud_disposition = Column(String, nullable=False, default="UNKNOWN")
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    __table_args__ = (
        Index("ix_bank_flow_institutions_ts", "deposit_institution", "clearing_institution", "deposit_timestamp"),
    )