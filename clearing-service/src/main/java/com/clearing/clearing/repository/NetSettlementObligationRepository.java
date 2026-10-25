package com.clearing.clearing.repository;

import com.clearing.clearing.model.NetSettlementObligation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NetSettlementObligationRepository extends JpaRepository<NetSettlementObligation, UUID> {
    List<NetSettlementObligation> findByNettingGroup_Id(UUID nettingGroupId);
}
