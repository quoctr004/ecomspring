package com.hieu.shipping_service.repository;

import com.hieu.shipping_service.entity.CarrierConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierConfigRepository extends JpaRepository<CarrierConfigEntity, String> {
}
