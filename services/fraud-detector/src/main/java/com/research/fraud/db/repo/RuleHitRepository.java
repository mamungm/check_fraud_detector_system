package com.research.fraud.db.repo;

import com.research.fraud.db.entity.RuleHit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RuleHitRepository extends JpaRepository<RuleHit, UUID> {
}
