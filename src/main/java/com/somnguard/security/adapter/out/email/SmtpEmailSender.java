package com.somnguard.security.adapter.out.email;

import com.somnguard.security.application.port.out.EmailSendException;
import com.somnguard.security.application.port.out.EmailSender;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Proveedor de correo SMTP para desarrollo local (MailHog).
 * Envía directamente al servidor SMTP configurado en Spring Mail (por defecto localhost:1025).
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${spring.mail.username:noreply@somnguard.local}") String fromEmail,
                           @Value("${app.email.sender-name:SomnGuard}") String fromName) {
        this.mailSender = mailSender;
        this.fromEmail = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : "noreply@somnguard.local";
        this.fromName = fromName;
    }

    @Override
    public void send(String to, String subject, String textContent, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);

            if (htmlContent != null && !htmlContent.isBlank()) {
                helper.setText(textContent != null ? textContent : "", htmlContent);
            } else {
                helper.setText(textContent != null ? textContent : "", false);
            }

            mailSender.send(message);
            log.info("Email enviado via SMTP (MailHog) to={} subject={}", to, subject);
        } catch (Exception ex) {
            log.warn("Fallo envio SMTP (MailHog) to={}: {}", to, ex.getMessage());
            throw new EmailSendException("No se pudo enviar el correo vía SMTP (MailHog)", ex);
        }
    }
}
