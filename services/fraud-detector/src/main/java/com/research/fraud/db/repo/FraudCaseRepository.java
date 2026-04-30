package com.research.fraud.db.repo;

import com.research.fraud.db.entity.FraudCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FraudCaseRepository extends JpaRepository<FraudCase, UUID> {
}
