package com.research.fraud.db.repo;

import com.research.fraud.db.entity.ModelScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelScoreRepository extends JpaRepository<ModelScore, Long> {
}
