from typing import Optional, List

from sqlalchemy.orm import Session
from db.entity.consortium_entities import ConsortiumEventEntity, TokenIndexEntity, RelationIndexEntity, TokenType, \
    BankFlowIndexEntity
from dtos.consortium_dtos import ConsortiumEventRequest


# ---------------------------------------
# Repo methods for ConsortiumEventEntity
# ---------------------------------------
def get_all_consortium_events(db: Session) -> list[type[ConsortiumEventEntity]]:
    return db.query(ConsortiumEventEntity).all()

def add_consortium_event(consortium_event: ConsortiumEventEntity, db: Session):
    db.add(consortium_event)


# ---------------------------------------
# Repo methods for TokenIndexEntity
# ---------------------------------------
def get_all_token_indices(db: Session) -> list[type[TokenIndexEntity]]:
    return db.query(TokenIndexEntity).all()

def get_token_events(token_type: TokenType, token: Optional[str], db: Session) -> List[ConsortiumEventRequest]:
    if not token:
        return []
    rows = (
        db.query(ConsortiumEventEntity)
        .join(
            TokenIndexEntity,
            ConsortiumEventEntity.eventId == TokenIndexEntity.event_id
        )
        .filter(
            TokenIndexEntity.token_type == token_type,
            TokenIndexEntity.token_value == token
        )
        .all()
    )

    return [
        ConsortiumEventRequest.model_validate({
            "eventId": row.eventId,
            "institutionId": row.institutionId,
            "clearingInstitutionId": row.clearingInstitutionId,
            "channel": row.channel,
            "depositTimestamp": row.depositTimestamp,
            "accountToken": row.accountToken,
            "payeeToken": row.payeeToken,
            "payorToken": row.payorToken,
            "deviceToken": row.deviceToken,
            "region": row.region,
            "checkSerialHash": row.checkSerialHash,
            "micrRoutingHash": row.micrRoutingHash,
            "micrAccountHash": row.micrAccountHash,
            "imageFrontUri": row.imageFrontUri,
            "imageBackUri": row.imageBackUri,
            "amount": row.amount,
            "currency": row.currency,
            "fraudDisposition": row.fraudDisposition,
        })
        for row in rows
    ]

def add_token_index(token_index: TokenIndexEntity, db: Session):
    db.add(token_index)


# ---------------------------------------
# Repo methods for RelationIndexEntity
# ---------------------------------------
def get_relation_events(payor_token: str, payee_token: str, db: Session) -> List[ConsortiumEventRequest]:
    if not payor_token or not payee_token:
        return []
    rows = (
        db.query(ConsortiumEventEntity)
        .join(RelationIndexEntity, ConsortiumEventEntity.eventId == RelationIndexEntity.event_id)
        .filter(
            RelationIndexEntity.payor_token == payor_token,
            RelationIndexEntity.payee_token == payee_token,
        )
        .all()
    )

    return [
        ConsortiumEventRequest.model_validate({
            "eventId": row.eventId,
            "institutionId": row.institutionId,
            "clearingInstitutionId": row.clearingInstitutionId,
            "channel": row.channel,
            "depositTimestamp": row.depositTimestamp,
            "accountToken": row.accountToken,
            "payeeToken": row.payeeToken,
            "payorToken": row.payorToken,
            "deviceToken": row.deviceToken,
            "region": row.region,
            "checkSerialHash": row.checkSerialHash,
            "micrRoutingHash": row.micrRoutingHash,
            "micrAccountHash": row.micrAccountHash,
            "imageFrontUri": row.imageFrontUri,
            "imageBackUri": row.imageBackUri,
            "amount": row.amount,
            "currency": row.currency,
            "fraudDisposition": row.fraudDisposition,
        })
        for row in rows
    ]


def add_relation_index(row: RelationIndexEntity, db: Session) -> None:
    db.add(row)


# ---------------------------------------
# Repo methods for BankFlowIndexEntity
# ---------------------------------------
def add_bank_flow_index(row: BankFlowIndexEntity, db: Session) -> None:
    db.add(row)

def get_bank_flow_events(
    deposit_bank: str,
    clearing_bank: str,
    db: Session
) -> List[ConsortiumEventRequest]:
    rows = (
        db.query(ConsortiumEventEntity)
        .join(BankFlowIndexEntity, ConsortiumEventEntity.eventId == BankFlowIndexEntity.event_id)
        .filter(
            BankFlowIndexEntity.deposit_institution == deposit_bank,
            BankFlowIndexEntity.clearing_institution == clearing_bank,
        )
        .all()
    )
    return [
        ConsortiumEventRequest.model_validate({
            "eventId": row.eventId,
            "institutionId": row.institutionId,
            "clearingInstitutionId": row.clearingInstitutionId,
            "channel": row.channel,
            "depositTimestamp": row.depositTimestamp,
            "accountToken": row.accountToken,
            "payeeToken": row.payeeToken,
            "payorToken": row.payorToken,
            "deviceToken": row.deviceToken,
            "region": row.region,
            "checkSerialHash": row.checkSerialHash,
            "micrRoutingHash": row.micrRoutingHash,
            "micrAccountHash": row.micrAccountHash,
            "imageFrontUri": row.imageFrontUri,
            "imageBackUri": row.imageBackUri,
            "amount": row.amount,
            "currency": row.currency,
            "fraudDisposition": row.fraudDisposition,
        })
        for row in rows
    ]