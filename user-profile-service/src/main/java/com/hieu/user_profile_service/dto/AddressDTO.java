package com.hieu.user_profile_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Serialised address payload. The {@code @JsonProperty("isDefault")} forces
 * Jackson to expose the boolean as {@code isDefault} on the wire — without
 * it, Lombok's {@code isDefault()} getter would strip the {@code is} prefix
 * and emit {@code "default": true} which doesn't match the FE contract.
 */
@Value
@Builder
public class AddressDTO {
    Long id;
    String userId;
    String label;
    String recipientName;
    String recipientPhone;
    String street;
    String ward;
    String district;
    String city;
    String country;
    String postalCode;
    @JsonProperty("isDefault")
    boolean isDefault;
}
