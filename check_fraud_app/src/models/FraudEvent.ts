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
}