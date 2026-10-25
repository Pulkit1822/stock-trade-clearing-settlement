package com.clearing.refdata.repository;

import com.clearing.refdata.model.Participant;
import com.clearing.refdata.model.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Optional<Participant> findByParticipantId(String participantId);

    List<Participant> findByStatus(ParticipantStatus status);
}
