package com.research.fraud.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsortiumResult {
    private String eventId;
    private String service;
    private ConsortiumDetails details;
    private int latencyMs;
    private String status;
    private double ts;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ConsortiumDetails {
    private float score;
    private ArrayList<String> topReasons;
    private SupportingLinkedCounts supportingLinkedCounts;
    private RecencyWindows recencyWindows;
    private ConsortiumExplanation explanation;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SupportingLinkedCounts {
    private TokenInfo PAYEE;
    private TokenInfo PAYOR;
    private TokenInfo ACCOUNT;
    private TokenInfo DEVICE;
    private TokenInfo IMAGE;
    private PayorPayeeRelationship payorPayeeRelationship;
    private BankFlow bankFlow;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TokenInfo {
    private String tokenType;
    private String tokenPreview;
    private float totalAppearances;
    private int appearances7d;
    private int appearances14d;
    private int appearances30d;
    private int institutionCount14d;
    private int institutionCount30d;
    private int fraudCount30d;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PayorPayeeRelationship {
    private int relationshipAppearances30d;
    private int relationshipFraudCount30d;
    private int relationshipInstitutionCount30d;
    private String payorPreview;
    private String payeePreview;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BankFlow {
    private String depositInstitution;
    private String clearingInstitution;
    private int bankFlowAppearances30d;
    private int bankFlowFraudCount30d;
    private float bankFlowFraudRate30d;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RecencyWindows {
    private String shortWindow;
    private String mediumWindow;
    private String longWindow;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ConsortiumExplanation {
    private ScoreBlend scoreBlend;
    private Privacy privacy;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ScoreBlend {
    private float maxTokenRiskWeight;
    private float relationshipRiskWeight;
    private float bankFlowRiskWeight;
    private float maxTokenRisk;
    private float relationshipRisk;
    private float bankFlowRisk;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Privacy {
    private boolean rawPIIStored;
    private String tokenization;
    private String returnedTokens;
}