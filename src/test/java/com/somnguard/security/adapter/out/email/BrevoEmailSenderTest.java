package com.somnguard.security.adapter.out.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import com.somnguard.security.application.port.out.EmailSendException;

@ExtendWith(MockitoExtension.class)
class BrevoEmailSenderTest {

    @Mock
    RestClient restClient;
    @Mock
    RestClient.RequestBodyUriSpec uriSpec;
    @Mock
    RestClient.RequestBodySpec bodySpec;
    @Mock
    RestClient.ResponseSpec responseSpec;

    BrevoEmailProperties properties;
    BrevoEmailSender sender;

    final String apiKey = "test-key-no-real-secret-123";

    @BeforeEach
    void setup() {
        properties = new BrevoEmailProperties();
        properties.setApiKey(apiKey);
        properties.setSenderEmail("somnguard.noreply@gmail.com");
        properties.setSenderName("SomnGuard");
        sender = new BrevoEmailSender(properties, restClient);
    }

    private void stubHttpChain() {
        when(restClient.post()).thenReturn(uriSpec);
        doReturn(bodySpec).when(uriSpec).uri(anyString());
        doReturn(bodySpec).when(bodySpec).headers(any());
        doReturn(bodySpec).when(bodySpec).body((Object) any());
        doReturn(responseSpec).when(bodySpec).retrieve();
    }

    @Test
    void buildsCorrectPayloadWithSenderAndRecipient() {
        Map<String, Object> payload =
                sender.buildPayload("user@mail.com", "Asunto", "texto", "<p>html</p>");

        Map<?, ?> from = (Map<?, ?>) payload.get("sender");
        assertEquals("somnguard.noreply@gmail.com", from.get("email"));
        assertEquals("SomnGuard", from.get("name"));
        List<?> to = (List<?>) payload.get("to");
        assertEquals("user@mail.com", ((Map<?, ?>) to.get(0)).get("email"));
        assertEquals("Asunto", payload.get("subject"));
        assertEquals("texto", payload.get("textContent"));
        assertEquals("<p>html</p>", payload.get("htmlContent"));
    }

    @Test
    void textOnlyOmitsHtmlContent() {
        Map<String, Object> payload =
                sender.buildPayload("user@mail.com", "Asunto", "texto", null);

        assertFalse(payload.containsKey("htmlContent"));
        assertEquals("texto", payload.get("textContent"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sendsWithApiKeyHeaderAndConfiguredUrl() {
        stubHttpChain();
        doReturn(Map.of("messageId", "abc")).when(responseSpec).body(Map.class);

        sender.send("user@mail.com", "Asunto", "texto", null);

        verify(uriSpec).uri("https://api.brevo.com/v3/smtp/email");
        ArgumentCaptor<Consumer> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(bodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        ((Consumer<HttpHeaders>) headersCaptor.getValue()).accept(headers);
        assertEquals(apiKey, headers.getFirst("api-key"));
        assertEquals("application/json", headers.getContentType().toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void brevo401MapsToControlledErrorWithoutSecret() {
        stubHttpChain();
        HttpClientErrorException unauthorized = mock(HttpClientErrorException.class);
        when(unauthorized.getStatusCode()).thenReturn(HttpStatus.UNAUTHORIZED);
        when(unauthorized.getResponseBodyAsString()).thenReturn("{\"message\":\"Invalid api key\"}");
        doThrow(unauthorized).when(responseSpec).body(Map.class);

        EmailSendException ex = assertThrows(EmailSendException.class,
                () -> sender.send("user@mail.com", "Asunto", "texto", null));

        assertFalse(ex.getMessage().contains(apiKey),
                "El error no debe exponer la API key");
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectionErrorMapsToControlledError() {
        stubHttpChain();
        doThrow(new ResourceAccessException("I/O error")).when(responseSpec).body(Map.class);

        EmailSendException ex = assertThrows(EmailSendException.class,
                () -> sender.send("user@mail.com", "Asunto", "texto", null));

        assertTrue(ex.getMessage().contains("brevo"));
        assertFalse(ex.getMessage().contains(apiKey));
    }

    @Test
    void missingApiKeyFailsFast() {
        BrevoEmailProperties empty = new BrevoEmailProperties();
        empty.setApiKey("");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new BrevoEmailSender(empty, restClient));

        assertTrue(ex.getMessage().contains("BREVO_API_KEY"));
    }

    @Test
    void defaultSenderIsSomnguard() {
        BrevoEmailProperties defaults = new BrevoEmailProperties();

        assertEquals("somnguard.noreply@gmail.com", defaults.getSenderEmail());
        assertEquals("SomnGuard", defaults.getSenderName());
    }
}
