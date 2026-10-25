package com.somnguard.security.adapter.out.email;

import com.somnguard.security.application.port.out.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailSenderConfig {

    @Bean
    @ConditionalOnProperty(name = "app.email.provider", havingValue = "brevo", matchIfMissing = true)
    public EmailSender brevoEmailSender(BrevoEmailProperties properties) {
        return new BrevoEmailSender(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.email.provider", havingValue = "mailhog")
    public EmailSender mailhogEmailSender(JavaMailSender javaMailSender,
                                          @Value("${spring.mail.username:noreply@somnguard.local}") String fromEmail,
                                          @Value("${app.email.sender-name:SomnGuard}") String fromName) {
        return new SmtpEmailSender(javaMailSender, fromEmail, fromName);
    }
}
