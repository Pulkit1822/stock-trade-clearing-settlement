package com.clearing.corporate.repository;

import com.clearing.corporate.model.CorporateAction;
import com.clearing.corporate.model.CorporateActionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CorporateActionRepository extends JpaRepository<CorporateAction, UUID> {

    Optional<CorporateAction> findByCorporateActionId(String corporateActionId);

    List<CorporateAction> findByInstrumentId(String instrumentId);

    List<CorporateAction> findByStatus(CorporateActionStatus status);
}
