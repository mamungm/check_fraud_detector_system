package com.research.fraud.db.repo;

import com.research.fraud.db.entity.DepositEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface DepositEventRepository extends JpaRepository<DepositEvent, UUID> {
    @Query("""
                SELECT COUNT(e)
                FROM DepositEvent e
                WHERE e.accountToken = :accountToken
                  AND e.depositTimestamp >= :fromTime
            """)
    long countDepositsByAccountTokenLast7Days(
            @Param("accountToken") String accountToken,
            @Param("fromTime") OffsetDateTime fromTime
    );

    @Query("""
                SELECT COALESCE(AVG(e.amount), 0)
                FROM DepositEvent e
                WHERE e.accountToken = :accountToken
                  AND e.depositTimestamp >= :fromTime
            """)
    float getAverageAmountLast30Days(
            @Param("accountToken") String accountToken,
            @Param("fromTime") OffsetDateTime fromTime
    );

    int countDepositEventsByAccountTokenAndDeviceToken(String accountToken, String deviceToken);

    @Query("""
                SELECT COUNT(e)
                FROM DepositEvent e
                WHERE (e.clearingInstitutionId = :institutionId OR e.institutionId = :institutionId)
                  AND e.fraudLabel = true
                  AND e.depositTimestamp >= :fromTime
            """)
    long countPriorFraudsForDraweeBank30d(
            @Param("institutionId") UUID institutionId,
            @Param("fromTime") OffsetDateTime fromTime
    );
}
