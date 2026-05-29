package com.research.fraud.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ImageServiceResponse {
    private UUID eventId;
    private String result;
}
