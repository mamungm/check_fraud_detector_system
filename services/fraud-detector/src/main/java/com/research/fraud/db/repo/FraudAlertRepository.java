package com.research.fraud.db.repo;

import com.research.fraud.db.entity.FraudAlert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    @Query("""
                SELECT f.alertId AS alertId,
                       f.eventId AS eventId,
                       f.riskScore AS riskScore,
                       f.decision AS decision,
                       f.reasonCodes AS reasonCodes,
                       f.status AS status,
                       f.createdAt AS createdAt
                FROM FraudAlert f
                ORDER BY f.createdAt DESC
            """)
    List<FraudAlertView> findTopAlerts(Pageable pageable);
}
