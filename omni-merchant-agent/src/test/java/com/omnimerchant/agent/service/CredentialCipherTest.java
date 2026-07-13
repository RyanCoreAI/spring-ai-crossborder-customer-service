package com.omnimerchant.agent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialCipherTest {

    @Test
    void shouldRoundTripWithBase64Aes256Key() {
        var cipher = new CredentialCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        var encrypted = cipher.encrypt("credential-value");

        assertTrue(encrypted.length() > 20);
        assertEquals("credential-value", cipher.decrypt(encrypted));
    }

    @Test
    void shouldRejectNonBase64ConfigurationWithActionableMessage() {
        var error = assertThrows(IllegalStateException.class,
                () -> new CredentialCipher("not-a-base64-key"));

        assertTrue(error.getMessage().contains("Base64"));
    }

    @Test
    void shouldRejectWrongDecodedKeyLength() {
        var error = assertThrows(IllegalStateException.class,
                () -> new CredentialCipher("c2hvcnQ="));

        assertTrue(error.getMessage().contains("16, 24, or 32"));
    }
}
