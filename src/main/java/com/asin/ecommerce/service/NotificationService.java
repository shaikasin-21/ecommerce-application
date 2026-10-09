package com.asin.ecommerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the order confirmation email asynchronously, only after the order transaction has committed.
 * If no mail server is configured the message is just logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.mail.from:no-reply@shop.local}")
    private String from;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("[{}] Sending confirmation for order #{} to {}", Thread.currentThread().getName(),
                event.orderId(), event.email());
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("No mail server configured; skipping real email for order #{}", event.orderId());
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(event.email());
            msg.setSubject("Order #" + event.orderId() + " confirmed");
            msg.setText("Thank you for your order #" + event.orderId() + ". Total: " + event.total());
            sender.send(msg);
        } catch (MailException e) {
            log.warn("Could not send confirmation email for order #{}: {}", event.orderId(), e.getMessage());
        }
    }
}
