package com.somnguard.security.application.port.out;

/**
 * Puerto de envío de correo. Los casos de uso dependen de esta abstracción,
 * nunca de un proveedor concreto.
 */
public interface EmailSender {

    /**
     * @param to destinatario
     * @param subject asunto
     * @param textContent contenido plano (obligatorio)
     * @param htmlContent contenido HTML (opcional, {@code null} si no aplica)
     * @throws EmailSendException si el envío falla
     */
    void send(String to, String subject, String textContent, String htmlContent);
}
