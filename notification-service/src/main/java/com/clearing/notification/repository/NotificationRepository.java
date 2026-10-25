package com.clearing.notification.repository;

import com.clearing.notification.model.Notification;
import com.clearing.notification.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** All notifications for a specific trade — buyer + seller records. */
    List<Notification> findByTradeId(String tradeId);

    /** All notifications sent to a specific account. */
    List<Notification> findByAccountId(String accountId);

    /** Find by status — used to detect PENDING/FAILED notifications for retry. */
    List<Notification> findByStatus(NotificationStatus status);
}
