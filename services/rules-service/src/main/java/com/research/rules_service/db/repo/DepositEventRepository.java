package com.research.rules_service.db.repo;

import com.research.rules_service.db.entity.DepositEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface DepositEventRepository extends JpaRepository<DepositEvent, UUID> {
    long countByMicrRoutingHashAndMicrAccountHashAndCheckSerialHashAndEventIdNot(
            String routing,
            String account,
            String serial,
            UUID eventId
    );

    long countByAccountTokenAndDepositTimestampAfter(
            String accountToken,
            OffsetDateTime cutoff
    );

    long countByAccountTokenAndDeviceTokenAndDepositTimestampAfter(
            String accountToken,
            String deviceToken,
            OffsetDateTime cutoff
    );
}
