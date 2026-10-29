package com.carbonedge.backend.service;

import com.carbonedge.backend.exception.IntegrationException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendHtmlEmail(String to, String subject, String textBody, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String from = authenticatedMailbox();
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IntegrationException("Failed to send email: " + exception.getMessage());
        }
    }

    private String authenticatedMailbox() {
        if (mailSender instanceof JavaMailSenderImpl sender
                && sender.getUsername() != null
                && !sender.getUsername().isBlank()) {
            return sender.getUsername().trim();
        }
        throw new IntegrationException("SMTP sender mailbox is not configured");
    }
}
