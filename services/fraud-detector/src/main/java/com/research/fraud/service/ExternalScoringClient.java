package com.research.fraud.service;

import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.ScoreResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class ExternalScoringClient {
    private final RestClient restClient = RestClient.create();

    @Value("${fraud.services.ml}") String mlUrl;
    @Value("${fraud.services.image}") String imageUrl;
    @Value("${fraud.services.consortium}") String consortiumUrl;

    public ScoreResponse mlScore(DepositEventRequest req) {
        return restClient.post().uri(mlUrl + "/score").body(req).retrieve().body(ScoreResponse.class);
    }

    public ScoreResponse imageScore(DepositEventRequest req) {
        return restClient.post().uri(imageUrl + "/analyze").body(req).retrieve().body(ScoreResponse.class);
    }

    public ScoreResponse consortiumScore(DepositEventRequest req) {
        return restClient.post().uri(consortiumUrl + "/match").body(req).retrieve().body(ScoreResponse.class);
    }

    public ScoreResponse fallback(String reason) {
        return new ScoreResponse(0.0, List.of("SERVICE_UNAVAILABLE"), Map.of("error", reason), 0);
    }
}
