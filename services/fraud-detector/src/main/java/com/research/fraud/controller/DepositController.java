package com.research.fraud.controller;

import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.service.DepositService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/deposits")
public class DepositController {
    private final DepositService service;

    public DepositController(DepositService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String,Object> ingest(@RequestBody DepositEventRequest request) throws Exception {
        return service.ingest(request);
    }
}
