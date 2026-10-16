import type {FraudEvent, WorkflowDetails} from "../models/FraudEvent";

const API_BASE = "http://localhost:8080/api";
const IMAGE_SERVICE_BASE = "http://localhost:8002/image";

/** URL the browser can load a check image from (the image service serves the file stored under `uri`). */
export const checkImageUrl = (uri: string): string =>
    `${IMAGE_SERVICE_BASE}/file?uri=${encodeURIComponent(uri)}`;

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

    async getWorkflowDetails(eventId: string): Promise<WorkflowDetails | null> {
        try {
            const response = await fetch(`${API_BASE}/deposit_event/${eventId}/workflow`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error("Failed to fetch workflow details:", error);
            return null;
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