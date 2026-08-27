package com.fitouts.shared.geocode;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeocodeResult {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String displayName;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private String area;
    private String mapsShareUrl;
}
