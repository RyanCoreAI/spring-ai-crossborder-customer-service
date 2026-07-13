package com.omnimerchant.advice;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn405WhenRequestMethodIsNotSupported() {
        var request = new MockHttpServletRequest("GET", "/api/widget/session");
        var exception = new HttpRequestMethodNotSupportedException("GET", List.of("POST"));

        var response = handler.handleMethodNotSupported(exception, request);

        assertThat(response.getCode()).isEqualTo("405");
        assertThat(response.getMessage()).contains("GET").contains("POST");
    }

    @Test
    void shouldReturn400WhenJsonBodyIsUnreadable() {
        var request = new MockHttpServletRequest("POST", "/api/admin/login");
        var exception = new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0]));

        var response = handler.handleUnreadableMessage(exception, request);

        assertThat(response.getCode()).isEqualTo("400");
        assertThat(response.getMessage()).contains("JSON");
    }

    @Test
    void shouldReturn404WhenResourceDoesNotExist() {
        var request = new MockHttpServletRequest("GET", "/api/not-found");
        var exception = new NoResourceFoundException(HttpMethod.GET, "/api/not-found", null);

        var response = handler.handleNoResource(exception, request);

        assertThat(response.getCode()).isEqualTo("404");
    }
}
