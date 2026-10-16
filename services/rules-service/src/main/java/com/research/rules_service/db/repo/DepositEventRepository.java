package com.research.rules_service.db.repo;

import com.research.rules_service.db.entity.DepositEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface DepositEventRepository extends JpaRepository<DepositEvent, UUID> {
    /** Other deposits of the same check (same MICR routing, account and serial) after the cutoff. */
    long countByMicrRoutingHashAndMicrAccountHashAndCheckSerialHashAndEventIdNotAndDepositTimestampAfter(
            String routing,
            String account,
            String serial,
            UUID eventId,
            OffsetDateTime cutoff
    );

    /** Earlier deposits of the account after the cutoff, not counting the event being evaluated. */
    long countByAccountTokenAndEventIdNotAndDepositTimestampAfter(
            String accountToken,
            UUID eventId,
            OffsetDateTime cutoff
    );

    /** Earlier deposits of the account from this device after the cutoff, not counting the event being evaluated. */
    long countByAccountTokenAndDeviceTokenAndEventIdNotAndDepositTimestampAfter(
            String accountToken,
            String deviceToken,
            UUID eventId,
            OffsetDateTime cutoff
    );
}
