package com.hieu.catalog_service.interfaces.rest;

import com.hieu.catalog_service.infrastructure.persistence.jpa.entities.HeroBannerJpaEntity;
import com.hieu.catalog_service.infrastructure.persistence.jpa.repositories.HeroBannerJpaRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hero banner CRUD. Public {@code GET /api/banners/active} feeds the storefront
 * slideshow; admin endpoints under {@code /api/banners/admin/**} manage rows.
 *
 * <p>Banner schedule:
 * <ul>
 *   <li>{@code enabled} — master kill switch.</li>
 *   <li>{@code startsAt / endsAt} — optional date window; active iff inside
 *       the window (NULL means open-ended).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class HeroBannerController {

    private final HeroBannerJpaRepository repo;

    /** Storefront slideshow — only active banners, sorted by displayOrder. */
    @GetMapping("/active")
    public List<Map<String, Object>> active() {
        return repo.findActive(OffsetDateTime.now())
                .stream().map(HeroBannerController::toMap).toList();
    }

    /** Admin — every banner including disabled / scheduled. */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> adminList() {
        return repo.findAllByOrderByDisplayOrderAscIdAsc()
                .stream().map(HeroBannerController::toMap).toList();
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody BannerRequest req) {
        var e = new HeroBannerJpaEntity();
        applyAll(e, req);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        return toMap(repo.save(e));
    }

    @PatchMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody BannerRequest req) {
        var e = repo.findById(id).orElseThrow(() -> new RuntimeException("Banner not found: " + id));
        applyAll(e, req);
        e.setUpdatedAt(OffsetDateTime.now());
        return toMap(repo.save(e));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repo.deleteById(id);
    }

    private static void applyAll(HeroBannerJpaEntity e, BannerRequest r) {
        if (r.title() != null)        e.setTitle(r.title());
        if (r.subtitle() != null)     e.setSubtitle(r.subtitle());
        if (r.imageUrl() != null)     e.setImageUrl(r.imageUrl());
        if (r.targetUrl() != null)    e.setTargetUrl(r.targetUrl());
        if (r.ctaLabel() != null)     e.setCtaLabel(r.ctaLabel());
        if (r.enabled() != null)      e.setEnabled(r.enabled());
        if (r.displayOrder() != null) e.setDisplayOrder(r.displayOrder());
        if (r.startsAt() != null)     e.setStartsAt(OffsetDateTime.parse(r.startsAt()));
        if (r.endsAt() != null)       e.setEndsAt(OffsetDateTime.parse(r.endsAt()));
    }

    private static Map<String, Object> toMap(HeroBannerJpaEntity e) {
        // LinkedHashMap so the JSON keys come out in a stable, readable order.
        var m = new LinkedHashMap<String, Object>();
        m.put("id", e.getId());
        m.put("title", e.getTitle());
        m.put("subtitle", e.getSubtitle());
        m.put("imageUrl", e.getImageUrl());
        m.put("targetUrl", e.getTargetUrl());
        m.put("ctaLabel", e.getCtaLabel());
        m.put("enabled", e.isEnabled());
        m.put("displayOrder", e.getDisplayOrder());
        m.put("startsAt", e.getStartsAt());
        m.put("endsAt", e.getEndsAt());
        return m;
    }

    public record BannerRequest(
            @NotBlank String title,
            String subtitle,
            String imageUrl,
            String targetUrl,
            String ctaLabel,
            Boolean enabled,
            Integer displayOrder,
            String startsAt,
            String endsAt
    ) {}
}
