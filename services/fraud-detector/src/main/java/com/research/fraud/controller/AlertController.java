package com.research.fraud.controller;

import com.research.fraud.db.entity.FraudCase;
import com.research.fraud.db.entity.InvestigatorAction;
import com.research.fraud.db.repo.FraudAlertRepository;
import com.research.fraud.db.repo.FraudAlertView;
import com.research.fraud.db.repo.FraudCaseRepository;
import com.research.fraud.db.repo.InvestigatorActionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AlertController {
    private final FraudAlertRepository fraudAlertRepository;
    private final FraudCaseRepository fraudCaseRepository;
    private final InvestigatorActionRepository investigatorActionRepository;

    public AlertController(FraudAlertRepository fraudAlertRepository, FraudCaseRepository fraudCaseRepository,
                           InvestigatorActionRepository investigatorActionRepository) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.fraudCaseRepository = fraudCaseRepository;
        this.investigatorActionRepository = investigatorActionRepository;
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts() {
        List<FraudAlertView> fraudAlertViewList = fraudAlertRepository.findTopAlerts(PageRequest.of(0, 100));
        return fraudAlertViewList.stream()
                .map(f -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("alert_id", f.getAlertId());
                    m.put("event_id", f.getEventId());
                    m.put("risk_score", f.getRiskScore());
                    m.put("decision", f.getDecision());
                    m.put("reason_codes", f.getReasonCodes());
                    m.put("status", f.getStatus());
                    m.put("created_at", f.getCreatedAt());
                    return m;
                })
                .toList();
    }

    @PostMapping("/cases/{caseId}/actions")
    public Map<String, Object> action(@PathVariable UUID caseId, @RequestBody Map<String, Object> body) {
        InvestigatorAction investigatorAction = InvestigatorAction.builder()
                .caseId(caseId)
                .actor((String) body.getOrDefault("actor", "analyst"))
                .actionType((InvestigatorAction.ActionType) body.getOrDefault("actionType", "DISPOSITION"))
                .notes((String) body.getOrDefault("notes", ""))
                .disposition((InvestigatorAction.Disposition) body.getOrDefault("disposition", "UNKNOWN"))
                .build();
        investigatorActionRepository.save(investigatorAction);

        FraudCase fraudCase = FraudCase.builder()
                .status((FraudCase.CaseStatus) body.getOrDefault("caseStatus", "CLOSED"))
                .caseId(caseId)
                .build();
        fraudCaseRepository.save(fraudCase);

        return Map.of("caseId", caseId, "status", "updated");
    }
}
