package com.clearing.refdata.repository;

import com.clearing.refdata.model.Account;
import com.clearing.refdata.model.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for {@link Account} entities.
 *
 * <p>All query methods map to indexes defined in {@code V2__add_accounts_table.sql}.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Finds an account by its business identifier.
     * Maps to {@code idx_accounts_account_id}.
     */
    Optional<Account> findByAccountId(String accountId);

    /**
     * Finds all accounts owned by a participant.
     * Maps to {@code idx_accounts_participant_id}.
     */
    List<Account> findByParticipantId(String participantId);

    /**
     * Finds all accounts by lifecycle status.
     * Maps to {@code idx_accounts_status}.
     */
    List<Account> findByStatus(AccountStatus status);
}
