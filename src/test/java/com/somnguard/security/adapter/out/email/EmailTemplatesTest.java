package com.somnguard.security.adapter.out.email;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailTemplatesTest {

    @Test
    void verificationEmailRendersAllPlaceholders() {
        String html = EmailTemplates.verificationEmail("Ana", "598808");

        assertTrue(html.contains("Verifica tu correo electrónico"));
        assertTrue(html.contains("598808"));
        assertTrue(html.contains("15 minutos"));
        assertTrue(html.contains("SomnGuard"));
        assertTrue(html.contains("Equipo SomnGuard"));
        assertFalse(html.contains("{{TITLE}}"));
        assertFalse(html.contains("{{MESSAGE}}"));
        assertFalse(html.contains("{{CODE}}"));
        assertFalse(html.contains("{{EXPIRATION}}"));
    }

    @Test
    void passwordResetEmailRendersWithIgnoreNote() {
        String html = EmailTemplates.passwordResetEmail("123456", 15);

        assertTrue(html.contains("Recuperación de contraseña"));
        assertTrue(html.contains("123456"));
        assertTrue(html.contains("15 minutos"));
        assertTrue(html.contains("puedes ignorar este correo"));
    }

    @Test
    void emailChangeEmailRendersNewAddress() {
        String html = EmailTemplates.emailChangeEmail("Luis", "nuevo@mail.com", "654321");

        assertTrue(html.contains("Confirma tu nuevo correo"));
        assertTrue(html.contains("nuevo@mail.com"));
        assertTrue(html.contains("654321"));
    }

    @Test
    void baseKeepsSharedDesign() {
        String html = EmailTemplates.verificationEmail("Ana", "598808");

        assertTrue(html.contains("#f7f4ec"));
        assertTrue(html.contains("max-width:500px"));
        assertTrue(html.contains("border-radius:12px"));
        assertFalse(html.toLowerCase().contains("<script"));
    }

    @Test
    void userDataIsEscaped() {
        String html = EmailTemplates.verificationEmail("<b>Ana</b>", "598808");

        assertFalse(html.contains("<b>Ana</b>"));
        assertTrue(html.contains("&lt;b&gt;Ana&lt;/b&gt;"));
        assertTrue(html.contains("598808"));
    }
}
