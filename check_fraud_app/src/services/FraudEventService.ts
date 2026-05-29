import type {FraudEvent} from "../models/FraudEvent";

const API_BASE = "http://localhost:8080/api";

export const FraudEventService = {
    async getAllEvents(): Promise<FraudEvent[]> {
        try {
            const url = `${API_BASE}/deposit_event_list`;
            console.info(`Fetching all events from ${url}`);
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error("Failed to fetch events:", error);
            return [];
        }
    },

    async getEventById(eventId: string): Promise<FraudEvent | null> {
        try {
            const response = await fetch(`${API_BASE}/deposit-events/${eventId}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error("Failed to fetch event:", error);
            return null;
        }
    },
};