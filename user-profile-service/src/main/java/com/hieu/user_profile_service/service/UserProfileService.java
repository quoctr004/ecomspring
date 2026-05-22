package com.hieu.user_profile_service.service;

import com.hieu.user_profile_service.dto.*;
import com.hieu.user_profile_service.entity.AddressJpaEntity;
import com.hieu.user_profile_service.entity.UserProfileJpaEntity;
import com.hieu.user_profile_service.exception.AddressNotFoundException;
import com.hieu.user_profile_service.exception.UserProfileNotFoundException;
import com.hieu.user_profile_service.kafka.event.ProfileUpsertedSpringEvent;
import com.hieu.user_profile_service.repository.AddressRepository;
import com.hieu.user_profile_service.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository profileRepo;
    private final AddressRepository addressRepo;
    private final ApplicationEventPublisher applicationEventPublisher;

    // ── Profile ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserProfileDTO getProfile(String userId) {
        return toDTO(findProfile(userId));
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getProfileByEmail(String email) {
        return profileRepo.findByEmail(email)
                .map(this::toDTO)
                .orElseThrow(() -> new UserProfileNotFoundException("email:" + email));
    }

    @Transactional
    public UserProfileDTO updateProfile(String userId, UpdateProfileRequest req) {
        UserProfileJpaEntity e = findProfile(userId);
        if (req.getPhone() != null)       e.setPhone(req.getPhone());
        if (req.getFirstName() != null)   e.setFirstName(req.getFirstName());
        if (req.getLastName() != null)    e.setLastName(req.getLastName());
        if (req.getAvatarUrl() != null)   e.setAvatarUrl(req.getAvatarUrl());
        if (req.getDateOfBirth() != null) e.setDateOfBirth(req.getDateOfBirth());
        if (req.getGender() != null)      e.setGender(req.getGender());
        UserProfileJpaEntity saved = profileRepo.save(e);
        // Deferred to AFTER_COMMIT via ProfileUpsertedListener — ensures DB row visible before Kafka fires.
        applicationEventPublisher.publishEvent(new ProfileUpsertedSpringEvent(
                saved.getUserId(), saved.getEmail(), saved.getFirstName(), saved.getLastName(), saved.getPhone()));
        return toDTO(saved);
    }

    // ── Addresses ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AddressDTO> getAddresses(String userId) {
        return addressRepo.findByUserProfile_UserId(userId)
                .stream().map(this::toAddressDTO).toList();
    }

    @Transactional(readOnly = true)
    public AddressDTO getAddress(String userId, Long addressId) {
        return addressRepo.findByIdAndUserProfile_UserId(addressId, userId)
                .map(this::toAddressDTO)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    @Transactional
    public AddressDTO createAddress(String userId, UpsertAddressRequest req) {
        // Lazy-provision the profile: auth-service does not currently publish the
        // auth.user-registered event, so users registered before the Kafka topic
        // exists never got a row here. Backfilling on first write keeps the
        // checkout flow unblocked without requiring a manual migration.
        UserProfileJpaEntity profile = findOrCreateProfile(userId);
        if (req.isDefault()) {
            addressRepo.clearDefaultForUser(userId);
        }
        AddressJpaEntity addr = buildAddress(req, profile);
        return toAddressDTO(addressRepo.save(addr));
    }

    @Transactional
    public AddressDTO updateAddress(String userId, Long addressId, UpsertAddressRequest req) {
        AddressJpaEntity addr = findAddress(userId, addressId);
        if (req.isDefault()) {
            addressRepo.clearDefaultForUser(userId);
        }
        applyAddressUpdate(addr, req);
        return toAddressDTO(addressRepo.save(addr));
    }

    @Transactional
    public void deleteAddress(String userId, Long addressId) {
        AddressJpaEntity addr = findAddress(userId, addressId);
        addressRepo.delete(addr);
    }

    @Transactional
    public AddressDTO setDefaultAddress(String userId, Long addressId) {
        findProfile(userId); // ensure profile exists
        addressRepo.clearDefaultForUser(userId);
        AddressJpaEntity addr = findAddress(userId, addressId);
        addr.setDefault(true);
        return toAddressDTO(addressRepo.save(addr));
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private UserProfileJpaEntity findProfile(String userId) {
        return profileRepo.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
    }

    /**
     * Find the profile or insert a placeholder when missing. Used by write paths
     * (address create / set-default) so the user can complete checkout even when
     * no Kafka {@code auth.user-registered} event ever arrived. The placeholder
     * email is overwritten the first time the user PATCHes /me.
     */
    private UserProfileJpaEntity findOrCreateProfile(String userId) {
        return profileRepo.findById(userId).orElseGet(() -> {
            UserProfileJpaEntity e = new UserProfileJpaEntity();
            e.setUserId(userId);
            e.setEmail(userId + "@placeholder.local");
            OffsetDateTime now = OffsetDateTime.now();
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            return profileRepo.save(e);
        });
    }

    private AddressJpaEntity findAddress(String userId, Long addressId) {
        return addressRepo.findByIdAndUserProfile_UserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    private AddressJpaEntity buildAddress(UpsertAddressRequest req, UserProfileJpaEntity profile) {
        AddressJpaEntity a = new AddressJpaEntity();
        a.setUserProfile(profile);
        applyAddressUpdate(a, req);
        return a;
    }

    private void applyAddressUpdate(AddressJpaEntity a, UpsertAddressRequest req) {
        if (req.getLabel() != null)         a.setLabel(req.getLabel());
        if (req.getRecipientName() != null) a.setRecipientName(req.getRecipientName());
        if (req.getRecipientPhone() != null)a.setRecipientPhone(req.getRecipientPhone());
        if (req.getStreet() != null)        a.setStreet(req.getStreet());
        if (req.getWard() != null)          a.setWard(req.getWard());
        if (req.getDistrict() != null)      a.setDistrict(req.getDistrict());
        if (req.getCity() != null)          a.setCity(req.getCity());
        if (req.getCountry() != null)       a.setCountry(req.getCountry());
        if (req.getPostalCode() != null)    a.setPostalCode(req.getPostalCode());
        a.setDefault(req.isDefault());
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private UserProfileDTO toDTO(UserProfileJpaEntity e) {
        return UserProfileDTO.builder()
                .userId(e.getUserId())
                .email(e.getEmail())
                .phone(e.getPhone())
                .firstName(e.getFirstName())
                .lastName(e.getLastName())
                .avatarUrl(e.getAvatarUrl())
                .dateOfBirth(e.getDateOfBirth())
                .gender(e.getGender())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public AddressDTO toAddressDTO(AddressJpaEntity a) {
        return AddressDTO.builder()
                .id(a.getId())
                .userId(a.getUserProfile().getUserId())
                .label(a.getLabel())
                .recipientName(a.getRecipientName())
                .recipientPhone(a.getRecipientPhone())
                .street(a.getStreet())
                .ward(a.getWard())
                .district(a.getDistrict())
                .city(a.getCity())
                .country(a.getCountry())
                .postalCode(a.getPostalCode())
                .isDefault(a.isDefault())
                .build();
    }
}
