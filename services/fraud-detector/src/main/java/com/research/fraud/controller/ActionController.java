package com.research.fraud.controller;

import com.research.fraud.dto.FraudDispositionRequest;
import com.research.fraud.dto.FraudDispositionResponse;
import com.research.fraud.service.ActionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/action")
public class ActionController {
    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    @PostMapping("/fraudDisposition")
    public ResponseEntity<FraudDispositionResponse> fraudDispositionAction(@RequestBody FraudDispositionRequest fraudDispositionRequest) {
        actionService.fraudDispositionAction(fraudDispositionRequest.eventId(), fraudDispositionRequest.fraudDisposition());

        return ResponseEntity.ok().build();
    }
}
