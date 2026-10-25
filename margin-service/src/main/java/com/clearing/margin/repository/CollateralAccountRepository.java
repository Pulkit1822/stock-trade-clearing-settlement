package com.clearing.margin.repository;

import com.clearing.margin.model.CollateralAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CollateralAccountRepository extends JpaRepository<CollateralAccount, UUID> {
    Optional<CollateralAccount> findByAccountId(String accountId);
}
