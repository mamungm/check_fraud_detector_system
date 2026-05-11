package com.research.fraud.dto;

public enum EnumFraudDisposition {
    UNKNOWN("UNKNOWN"),
    CONFIRMED_FRAUD("CONFIRMED_FRAUD"),
    CLEARED("CLEARED");
    
    private String s;

    EnumFraudDisposition(String s) {
        this.s = s;
    }
}
