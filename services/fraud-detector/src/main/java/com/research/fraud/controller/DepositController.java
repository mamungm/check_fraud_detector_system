package com.research.fraud.controller;

import com.research.fraud.dto.DepositEventDTO;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.service.DepositService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin("*")
public class DepositController {
    private final DepositService service;

    @PostMapping("/deposit_request")
    public ResponseEntity<DepositEventResponse> ingest(@RequestBody DepositEventRequest request) throws Exception {
        return ResponseEntity.ok(service.ingest(request));
    }

    @GetMapping("/deposit_event_list")
    public ResponseEntity<List<DepositEventDTO>> getDepositEventList() {
        return ResponseEntity.ok(service.getDepositEventList());
    }
}
