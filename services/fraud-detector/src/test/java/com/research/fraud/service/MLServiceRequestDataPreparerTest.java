package com.research.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.MLServiceRequest;
import com.research.fraud.statemachine.FraudDetectionWorkflowEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MLServiceRequestDataPreparerTest {

    @Mock
    DepositEventRepository depositEventRepository;

    @Mock
    ObjectMapper objectMapper;

    MLServiceRequestDataPreparer preparer;

    @BeforeEach
    void setup() {
        preparer = new MLServiceRequestDataPreparer(depositEventRepository, objectMapper);
    }

    @Test
    void prepareMLServiceRequest_buildsExpectedRequest() throws JsonProcessingException {
        UUID eventId = UUID.randomUUID();

        // Create a DepositEvent returned by repository
        DepositEvent depositEvent = DepositEvent.builder()
                .eventId(eventId)
                .institutionId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(1234.56))
                .channel(DepositEvent.Channel.mobile)
                .accountToken("acct-1")
                .deviceToken("dev-1")
                .depositTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2))
                .build();

        when(depositEventRepository.getReferenceById(eventId)).thenReturn(depositEvent);
        when(depositEventRepository.countDepositsByAccountTokenLast7Days(
                eq("acct-1"),
                any(OffsetDateTime.class)
        )).thenReturn(5L);
        when(depositEventRepository.getAverageAmountLast30Days(
                eq(depositEvent.getAccountToken()),
                any(OffsetDateTime.class)
        )).thenReturn(200.0f);
        when(depositEventRepository.countDepositEventsByAccountTokenAndDeviceToken(depositEvent.getAccountToken(), depositEvent.getDeviceToken())).thenReturn(0);
        when(depositEventRepository.countPriorFraudsForDraweeBank30d(
                eq(depositEvent.getInstitutionId()),
                any(OffsetDateTime.class)
        )).thenReturn(2L);

        // Build simple ConsortiumResult and ImageResult objects and have ObjectMapper return them
        ConsortiumResult consortium = new ConsortiumResult();
        ConsortiumDetails details = new ConsortiumDetails();
        SupportingLinkedCounts slc = new SupportingLinkedCounts();
        TokenInfo payee = new TokenInfo();
        payee.setInstitutionCount14d(3);
        payee.setFraudCount30d(1);
        slc.setPAYEE(payee);
        TokenInfo deviceInfo = new TokenInfo();
        deviceInfo.setFraudCount30d(2);
        slc.setDEVICE(deviceInfo);
        ConsortiumExplanation expl = new ConsortiumExplanation();
        ScoreBlend sb = new ScoreBlend();
        sb.setBankFlowRisk(0.42f);
        expl.setScoreBlend(sb);
        details.setSupportingLinkedCounts(slc);
        details.setExplanation(expl);
        consortium.setDetails(details);

        ImageResult image = new ImageResult();
        ImageDetails imgDetails = new ImageDetails();
        imgDetails.setImage_duplicate_score(0.88f);
        imgDetails.setOcr_amount_match(true);
        imgDetails.setLayout_anomaly_score(0.1f);
        imgDetails.setFont_anomaly_score(0.2f);
        imgDetails.setSignature_presence_score(0.3f);
        imgDetails.setEndorsement_score(0.4f);
        image.setDetails(imgDetails);

        when(objectMapper.readValue(anyString(), eq(ConsortiumResult.class))).thenReturn(consortium);
        when(objectMapper.readValue(anyString(), eq(ImageResult.class))).thenReturn(image);

        FraudDetectionWorkflowEntity workflow = FraudDetectionWorkflowEntity.builder()
                .eventId(eventId)
                .consortiumResult("{}")
                .imageResult("{}")
                .build();

        MLServiceRequest req = preparer.prepareMLServiceRequest(workflow, eventId);

        assertNotNull(req);
        assertEquals(depositEvent.getInstitutionId().toString(), req.getInstitutionId());
        assertEquals(depositEvent.getAmount().floatValue(), req.getAmount());
        assertEquals(5.0f, req.getAccount_deposit_count_7d());
        assertEquals(200.0f, req.getAccount_avg_amount_30d());
        assertEquals(0.0f, req.getAccount_new_device());
        assertEquals(3.0f, req.getPayee_seen_institutions_14d());
        assertEquals(1.0f, req.getPayee_fraud_count_30d());
        assertEquals(2.0f, req.getDevice_fraud_count_30d());
        assertEquals(0.88f, req.getImage_duplicate_score());
        assertTrue(req.isOcr_amount_match());
        assertEquals(0.42f, req.getDeposit_to_clearing_bank_risk());
    }
}

