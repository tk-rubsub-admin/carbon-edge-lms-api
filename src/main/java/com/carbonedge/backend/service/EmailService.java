package com.carbonedge.backend.service;

public interface EmailService {

    void sendHtmlEmail(String to, String subject, String textBody, String htmlBody);
}
