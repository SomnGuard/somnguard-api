package com.somnguard.security.adapter.out.email;


public final class EmailTemplates {

    private EmailTemplates() {}

    /**
     * Verificación de correo (registro).
     */
    public static String verificationEmail(String firstName, String code) {
        return base(
                "Verifica tu correo electrónico",
                "Hola " + escape(firstName) + ",<br><br>"
                        + "Gracias por registrarte en SomnGuard.<br>"
                        + "Usa el siguiente código para verificar tu cuenta:",
                code,
                "Este código expira en 15 minutos y solo puede usarse una vez.");
    }

    /**
     * Recuperación de contraseña.
     */
    public static String passwordResetEmail(String code, int expiryMinutes) {
        return base(
                "Recuperación de contraseña",
                "Recibimos una solicitud para restablecer la contraseña de tu cuenta.<br>"
                        + "Usa el siguiente código para continuar con el proceso:<br><br>"
                        + "<span style=\"color:#6b7280;\">Si no solicitaste este cambio, "
                        + "puedes ignorar este correo.</span>",
                code,
                "Este código expira en " + expiryMinutes + " minutos y solo puede usarse una vez.");
    }

    /**
     * Confirmación de cambio de correo.
     */
    public static String emailChangeEmail(String firstName, String newEmail, String code) {
        return base(
                "Confirma tu nuevo correo",
                "Hola " + escape(firstName) + ",<br><br>"
                        + "Solicitaste cambiar tu correo electrónico a <strong>"
                        + escape(newEmail) + "</strong>.<br>"
                        + "Usa el siguiente código para confirmar este cambio:",
                code,
                "Este código expira en 15 minutos y solo puede usarse una vez.");
    }

    static String base(String title, String messageHtml, String code, String expiration) {
        return "<!doctype html>"
                + "<html><head><meta charset=\"utf-8\"></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f7f4ec;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background-color:#f7f4ec;padding:24px 12px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"500\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:500px;width:100%;background-color:#ffffff;"
                + "border-radius:12px;overflow:hidden;\">"
                + "<tr><td align=\"center\" "
                + "style=\"padding:28px 32px 0;font-family:Arial,Helvetica,sans-serif;\">"
                + "<div style=\"font-size:13px;letter-spacing:4px;color:#0f766e;"
                + "font-weight:bold;\">SOMNGUARD</div>"
                + "</td></tr>"
                + "<tr><td align=\"center\" "
                + "style=\"padding:16px 32px 0;font-family:Arial,Helvetica,sans-serif;\">"
                + "<div style=\"font-size:20px;font-weight:bold;color:#111111;\">"
                + escape(title) + "</div>"
                + "</td></tr>"
                + "<tr><td align=\"center\" "
                + "style=\"padding:16px 32px 0;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:14px;line-height:22px;color:#374151;\">"
                + messageHtml
                + "</td></tr>"
                + "<tr><td align=\"center\" style=\"padding:24px 32px 0;\">"
                + "<div style=\"display:inline-block;background-color:#f7f4ec;"
                + "border:1px solid #e5e0d3;border-radius:8px;padding:14px 32px;"
                + "font-family:Arial,Helvetica,sans-serif;font-size:30px;font-weight:bold;"
                + "letter-spacing:8px;color:#111111;\">"
                + escape(code) + "</div>"
                + "</td></tr>"
                + "<tr><td align=\"center\" "
                + "style=\"padding:16px 32px 0;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:13px;color:#6b7280;\">"
                + escape(expiration)
                + "</td></tr>"
                + "<tr><td align=\"center\" "
                + "style=\"padding:24px 32px 28px;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:13px;color:#9ca3af;border-top:1px solid #f0ece0;\">"
                + "Equipo SomnGuard"
                + "</td></tr>"
                + "</table>"
                + "</td></tr></table>"
                + "</body></html>";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
