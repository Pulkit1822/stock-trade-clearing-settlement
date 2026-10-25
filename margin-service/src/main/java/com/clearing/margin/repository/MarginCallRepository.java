package com.clearing.margin.repository;

import com.clearing.margin.model.MarginCall;
import com.clearing.margin.model.MarginCallStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarginCallRepository extends JpaRepository<MarginCall, UUID> {
    List<MarginCall> findByAccountIdAndStatus(String accountId, MarginCallStatus status);
}
