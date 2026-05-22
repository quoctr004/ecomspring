package com.hieu.user_profile_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpsertAddressRequest {
    private String label;

    @NotBlank
    private String recipientName;

    @NotBlank
    private String recipientPhone;

    @NotBlank
    private String street;

    private String ward;
    private String district;

    @NotBlank
    private String city;

    private String country;
    private String postalCode;
    @JsonProperty("isDefault")
    private boolean isDefault;
}
