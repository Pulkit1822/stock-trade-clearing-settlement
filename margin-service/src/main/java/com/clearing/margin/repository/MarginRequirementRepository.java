package com.clearing.margin.repository;

import com.clearing.margin.model.MarginRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MarginRequirementRepository extends JpaRepository<MarginRequirement, UUID> {
    Optional<MarginRequirement> findByAccountId(String accountId);
}
