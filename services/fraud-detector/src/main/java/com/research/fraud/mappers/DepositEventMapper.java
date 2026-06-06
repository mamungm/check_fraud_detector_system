package com.research.fraud.mappers;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.dto.DepositEventRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DepositEventMapper {
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "fraudLabel", ignore = true)
    @Mapping(target = "workflow", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DepositEvent toEntity(DepositEventRequest request);
}
