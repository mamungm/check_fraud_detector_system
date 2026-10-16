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
    imageFrontUri?: string;
    imageBackUri?: string;
    // which services finished analysing the deposit (from the workflow table)
    imageCompleted?: boolean;
    consortiumCompleted?: boolean;
    mlCompleted?: boolean;
    ruleCompleted?: boolean;
    workflow?: string;
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

/** Stored workflow row of one deposit: completion flags and each service's full response (null until it responds). */
export interface WorkflowDetails {
    eventId: string;
    consortiumCompleted: boolean;
    imageCompleted: boolean;
    ruleCompleted: boolean;
    mlCompleted: boolean;
    consortiumResult: any | null;
    imageResult: any | null;
    ruleResult: any | null;
    mlResult: any | null;
    createdAt?: string;
    updatedAt?: string;
}
