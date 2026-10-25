package com.clearing.clearing.repository;

import com.clearing.clearing.model.NettingGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NettingGroupRepository extends JpaRepository<NettingGroup, UUID> {
    Optional<NettingGroup> findByBusinessDate(String businessDate);
}
