export interface FraudEvent {
    eventId: string;
    institutionId: string;
    amount: number;
    channel: string;
    depositFraudProbability: number;
    finalFraudProbability: number;
    calibratedRiskBand: string;
    recommendedAction: string;
    timestamp: string;
    // which services finished analysing the deposit (from the workflow table)
    imageCompleted?: boolean;
    consortiumCompleted?: boolean;
    mlCompleted?: boolean;
    ruleCompleted?: boolean;
}

export interface DepositEvent {
    institutionId: string,
    clearingInstitutionId: string,
    channel: string,
    depositTimestamp: string, // ISO 8601
    amount: number,
    currency: string,
    accountToken: string,
    payeeToken: string,
    payorToken: string,
    deviceToken: string,
    region: string,
    checkSerialHash: string,
    micrRoutingHash: string,
    micrAccountHash: string,
    imageFrontUri: string,
    imageBackUri: string
}