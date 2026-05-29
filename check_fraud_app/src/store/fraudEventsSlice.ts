// src/store/fraudEventsSlice.ts
import {createSlice, createAsyncThunk, type PayloadAction} from "@reduxjs/toolkit";
import type {FraudEvent} from "../models/FraudEvent";
import {FraudEventService} from "../services/FraudEventService";

const API_BASE = "http://localhost:8080/api";

interface FraudEventsState {
    events: FraudEvent[];
    loading: boolean;
    error: string | null;
}

const initialState: FraudEventsState = {
    events: [],
    loading: false,
    error: null,
};

// thunk to fetch all events
export const fetchAllEvents = createAsyncThunk<FraudEvent[]>(
    `${API_BASE}/deposit_event_list`,
    async (_, {rejectWithValue}) => {
        try {
            const res = await FraudEventService.getAllEvents();
            return res;
        } catch (err: any) {
            return rejectWithValue(err.message || "Failed to fetch events");
        }
    }
);

const slice = createSlice({
    name: "fraudEvents",
    initialState,
    reducers: {
        addEvent(state, action: PayloadAction<FraudEvent>) {
            state.events.unshift(action.payload);
            if (state.events.length > 100) {
                state.events = state.events.slice(0, 100);
            }
        },
        setEvents(state, action: PayloadAction<FraudEvent[]>) {
            state.events = action.payload;
        },
        addOrUpdateEvent(state, action: PayloadAction<FraudEvent>) {
            const incoming = action.payload;
            const idx = state.events.findIndex(e => e.eventId === incoming.eventId);

            if (idx >= 0) {
                // update existing event
                state.events[idx] = {
                    ...state.events[idx],
                    ...incoming,
                };
            } else {
                // add new event to the front
                state.events.unshift(incoming);
            }

            // cap
            if (state.events.length > 100) {
                state.events = state.events.slice(0, 100);
            }
        }
    },
    extraReducers: (builder) => {
        builder.addCase(fetchAllEvents.pending, (state) => {
            state.loading = true;
            state.error = null;
        });
        builder.addCase(fetchAllEvents.fulfilled, (state, action) => {
            state.loading = false;
            state.events = action.payload;
        });
        builder.addCase(fetchAllEvents.rejected, (state, action) => {
            state.loading = false;
            state.error = action.payload as string ?? "Failed";
        });
    }
});

export const {addEvent, setEvents, addOrUpdateEvent} = slice.actions;
export default slice.reducer;