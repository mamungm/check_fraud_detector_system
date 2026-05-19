package com.research.fraud.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@AllArgsConstructor
public class EventWSSessionMapper {
    private final Map<UUID, String> eventWSSessionMap;

    public void mapEventToWSSession(UUID eventId, String sessionId) {
        eventWSSessionMap.put(eventId, sessionId);
    }

    public String getWSSessionIdFromEventId(UUID eventId) {
        return eventWSSessionMap.get(eventId);
    }
}
