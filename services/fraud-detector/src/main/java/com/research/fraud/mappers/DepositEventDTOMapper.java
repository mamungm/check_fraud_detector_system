package com.research.fraud.mappers;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.dto.DepositEventDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.statemachine.FraudDetectionWorkflowEntity;
import com.research.fraud.statemachine.FraudDetectionWorkflowRepo;
import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@AllArgsConstructor
@Slf4j
public class DepositEventDTOMapper {
    private final FraudDetectionWorkflowRepo workflowRepo;
    private final ObjectMapper objectMapper;

    public DepositEventResponse buildDepositResponse(DepositEvent depositEvent, String message) {
        List<DepositEventDTO> depositEventDTOS = Stream.of(depositEvent).map(this::toDTO).toList();

        return DepositEventResponse.builder()
                .depositEventList(depositEventDTOS)
                .message(message)
                .build();
    }

    public DepositEventDTO toDTO(DepositEvent e) {
        return toDTO(e, workflowRepo.findById(e.getEventId()).orElse(null));
    }

    /**
     * @param workflow the workflow row of this event (service completion flags and results), may be null
     */
    public DepositEventDTO toDTO(DepositEvent e, FraudDetectionWorkflowEntity workflow) {
        float depositProbability = 0;
        float finalProbability = 0;
        String riskBand = null;
        String action = null;

        // The ML service result is stored as the full completion message; the scores are inside "details".
        if (workflow != null && workflow.getMlResult() != null) {
            try {
                JsonNode root = objectMapper.readTree(workflow.getMlResult());
                JsonNode details = root.path("details");
                if ("SUCCESS".equalsIgnoreCase(root.path("status").asText("SUCCESS")) && details.isObject()) {
                    depositProbability = (float) details.path("depositFraudProbability").asDouble(0);
                    finalProbability = (float) details.path("finalFraudProbability").asDouble(0);
                    riskBand = details.path("calibratedRiskBand").asText(null);
                    action = details.path("recommendedAction").asText(null);
                }
            } catch (Exception ex) {
                log.warn("[{}] could not read ML result: {}", e.getEventId(), ex.getMessage());
            }
        }

        return DepositEventDTO.builder()
                .eventId(e.getEventId())
                .institutionId(e.getInstitutionId())
                .clearingInstitutionId(e.getClearingInstitutionId())
                .channel(e.getChannel())
                .depositTimestamp(e.getDepositTimestamp())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .accountToken(e.getAccountToken())
                .payeeToken(e.getPayeeToken())
                .payorToken(e.getPayorToken())
                .deviceToken(e.getDeviceToken())
                .region(e.getRegion())
                .checkSerialHash(e.getCheckSerialHash())
                .micrRoutingHash(e.getMicrRoutingHash())
                .micrAccountHash(e.getMicrAccountHash())
                .imageFrontUri(e.getImageFrontUri())
                .imageBackUri(e.getImageBackUri())
                .workflow(e.getWorkflow())
                .depositFraudProbability(depositProbability)
                .finalFraudProbability(finalProbability)
                .calibratedRiskBand(riskBand)
                .recommendedAction(action)
                .imageCompleted(workflow != null && workflow.isImageCompleted())
                .consortiumCompleted(workflow != null && workflow.isConsortiumCompleted())
                .mlCompleted(workflow != null && workflow.isMlCompleted())
                .ruleCompleted(workflow != null && workflow.isRuleCompleted())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
