package com.fitouts.shared.persistence;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.checklist.dto.RoomScopeDto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RoomScopesJsonConverter implements AttributeConverter<List<RoomScopeDto>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<RoomScopeDto>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<RoomScopeDto> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize room scopes", e);
        }
    }

    @Override
    public List<RoomScopeDto> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<RoomScopeDto> list = MAPPER.readValue(dbData, TYPE);
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
