package com.hieu.catalog_service.infrastructure.persistence.jpa.repositories;

import com.hieu.catalog_service.infrastructure.persistence.jpa.entities.HeroBannerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface HeroBannerJpaRepository extends JpaRepository<HeroBannerJpaEntity, Long> {

    @Query("""
           SELECT b FROM HeroBannerJpaEntity b
           WHERE b.enabled = true
             AND (b.startsAt IS NULL OR b.startsAt <= :now)
             AND (b.endsAt   IS NULL OR b.endsAt   >  :now)
           ORDER BY b.displayOrder ASC, b.id ASC
           """)
    List<HeroBannerJpaEntity> findActive(OffsetDateTime now);

    List<HeroBannerJpaEntity> findAllByOrderByDisplayOrderAscIdAsc();
}
