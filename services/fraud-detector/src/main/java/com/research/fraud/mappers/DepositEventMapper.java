package com.research.fraud.mappers;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.dto.DepositEventRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DepositEventMapper {
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    DepositEvent toEntity(DepositEventRequest request);
}
