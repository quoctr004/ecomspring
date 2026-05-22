package com.hieu.payment_service.repository;

import com.hieu.payment_service.entity.PaymentMethodOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodOverrideRepository extends JpaRepository<PaymentMethodOverrideEntity, String> {
}
