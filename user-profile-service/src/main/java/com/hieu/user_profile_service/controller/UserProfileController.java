package com.hieu.user_profile_service.controller;

import com.hieu.common.security.AuthenticatedUser;
import com.hieu.user_profile_service.dto.*;
import com.hieu.user_profile_service.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService service;

    // ── Caller's own profile ──────────────────────────────────────────────

    @GetMapping("/me")
    public UserProfileDTO getMyProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.getProfile(user.userId());
    }

    @PatchMapping("/me")
    public UserProfileDTO updateMyProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestBody UpdateProfileRequest req) {
        return service.updateProfile(user.userId(), req);
    }

    // ── Caller's addresses ────────────────────────────────────────────────

    @GetMapping("/me/addresses")
    public List<AddressDTO> getMyAddresses(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.getAddresses(user.userId());
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<AddressDTO> createAddress(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody UpsertAddressRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAddress(user.userId(), req));
    }

    @PatchMapping("/me/addresses/{addressId}")
    public AddressDTO updateAddress(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long addressId,
                                    @Valid @RequestBody UpsertAddressRequest req) {
        return service.updateAddress(user.userId(), addressId, req);
    }

    @DeleteMapping("/me/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@AuthenticationPrincipal AuthenticatedUser user,
                              @PathVariable Long addressId) {
        service.deleteAddress(user.userId(), addressId);
    }

    @PostMapping("/me/addresses/{addressId}/set-default")
    public AddressDTO setDefault(@AuthenticationPrincipal AuthenticatedUser user,
                                 @PathVariable Long addressId) {
        return service.setDefaultAddress(user.userId(), addressId);
    }

    // ── Admin endpoints ───────────────────────────────────────────────────

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public UserProfileDTO getProfileByAdmin(@PathVariable String userId) {
        return service.getProfile(userId);
    }

    @GetMapping("/by-email/{email}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public UserProfileDTO getByEmail(@PathVariable String email) {
        return service.getProfileByEmail(email);
    }

    // ── Internal (no-auth) for saga ───────────────────────────────────────

    @GetMapping("/{userId}/addresses/{addressId}")
    public AddressDTO getAddressInternal(@PathVariable String userId,
                                         @PathVariable Long addressId) {
        return service.getAddress(userId, addressId);
    }
}
