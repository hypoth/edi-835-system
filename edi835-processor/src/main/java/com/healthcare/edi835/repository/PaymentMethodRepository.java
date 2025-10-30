package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.PaymentMethod;
import com.healthcare.edi835.entity.Payer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for PaymentMethod entity.
 */
@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    /**
     * Finds payment methods by payer.
     */
    List<PaymentMethod> findByPayerAndIsActiveTrue(Payer payer);

    /**
     * Finds payment methods by type.
     */
    List<PaymentMethod> findByMethodTypeAndIsActiveTrue(PaymentMethod.MethodType methodType);

    /**
     * Finds all active payment methods.
     */
    List<PaymentMethod> findByIsActiveTrue();
}
