package com.research.fraud.db.repo;

import com.research.fraud.db.entity.InvestigatorAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InvestigatorActionRepository extends JpaRepository<InvestigatorAction, UUID> {
}
