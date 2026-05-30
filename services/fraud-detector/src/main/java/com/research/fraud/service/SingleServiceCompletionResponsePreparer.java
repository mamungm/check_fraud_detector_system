package com.research.fraud.service;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.dto.ServiceCompletionEvent;
import com.research.fraud.mappers.DepositEventDTOMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SingleServiceCompletionResponsePreparer {
    private final DepositEventRepository depositEventRepository;
    private final DepositEventDTOMapper depositEventDtoMapper;

    public DepositEventResponse prepareConsortiumServiceResponseMessage(ServiceCompletionEvent serviceCompletionEvent) {
        DepositEvent depositEvent = depositEventRepository.findByEventId(serviceCompletionEvent.eventId()).getFirst();

        return depositEventDtoMapper.buildDepositResponse(depositEvent, "consortium service completed");
    }

    public DepositEventResponse prepareImageServiceResponseMessage(ServiceCompletionEvent serviceCompletionEvent) {
        DepositEvent depositEvent = depositEventRepository.findByEventId(serviceCompletionEvent.eventId()).getFirst();

        return depositEventDtoMapper.buildDepositResponse(depositEvent, "image service completed");
    }

    public DepositEventResponse prepareMLServiceResponseMessage(ServiceCompletionEvent serviceCompletionEvent) {
        DepositEvent depositEvent = depositEventRepository.findByEventId(serviceCompletionEvent.eventId()).getFirst();

        return depositEventDtoMapper.buildDepositResponse(depositEvent, "ml service completed");
    }
}
