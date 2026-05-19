package com.research.fraud.controller;

import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.service.DepositService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DepositController {
    private final DepositService service;

    @PostMapping("/deposit_request")
    public ResponseEntity<DepositEventResponse> ingest(@RequestBody DepositEventRequest request) throws Exception {
        return ResponseEntity.ok(service.ingest(request));
    }
}
