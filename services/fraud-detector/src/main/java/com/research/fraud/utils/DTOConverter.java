package com.research.fraud.utils;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.dto.DepositEventDTO;
import com.research.fraud.dto.DepositEventResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@AllArgsConstructor
public class DTOConverter {
    public DepositEventResponse buildDepositResponse(DepositEvent depositEvent, String message) {
        List<DepositEventDTO> depositEventDTOS = Stream.of(depositEvent).map(this::toDTO).toList();

        return DepositEventResponse.builder()
                .depositEventList(depositEventDTOS)
                .message(message)
                .build();
    }

    public DepositEventDTO toDTO(DepositEvent e) {
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
                .finalFraudProbability(0)
                .createdAt(e.getCreatedAt())
                .build();
    }
}
