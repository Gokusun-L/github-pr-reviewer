package com.gokusun.prreviewer.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.UnknownHttpStatusCodeException;

class RetryDecisionTest {

  @Test
  void transientAiExceptionIsRetryable() {
    assertThat(RetryDecision.retryable(new TransientAiException("503 - upstream"))).isTrue();
  }

  @Test
  void nonTransientAiExceptionIsNotRetryable() {
    assertThat(RetryDecision.retryable(new NonTransientAiException("401 - bad key"))).isFalse();
  }

  @Test
  void resourceAccessExceptionIsRetryable() {
    assertThat(RetryDecision.retryable(new ResourceAccessException("Connection timed out")))
        .isTrue();
  }

  @Test
  void serverErrorStatusIsRetryable() {
    assertThat(
            RetryDecision.retryable(
                HttpServerErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR, "boom", HttpHeaders.EMPTY, null, null)))
        .isTrue();
  }

  @Test
  void unauthorizedIsNotRetryable() {
    assertThat(
            RetryDecision.retryable(
                HttpClientErrorException.create(
                    HttpStatus.UNAUTHORIZED, "unauthorized", HttpHeaders.EMPTY, null, null)))
        .isFalse();
  }

  @Test
  void clientErrorStatusIsNotRetryable() {
    assertThat(
            RetryDecision.retryable(
                HttpClientErrorException.create(
                    HttpStatusCode.valueOf(422), "unprocessable", HttpHeaders.EMPTY, null, null)))
        .isFalse();
  }

  @Test
  void rateLimitIsNotImmediatelyRetryable() {
    assertThat(
            RetryDecision.retryable(
                HttpClientErrorException.create(
                    HttpStatus.TOO_MANY_REQUESTS, "slow down", HttpHeaders.EMPTY, null, null)))
        .isFalse();
    assertThat(
            RetryDecision.retryable(
                HttpClientErrorException.create(
                    HttpStatus.FORBIDDEN, "forbidden", HttpHeaders.EMPTY, null, null)))
        .isFalse();
  }

  @Test
  void unknownServerStatusCodeIsRetryable() {
    assertThat(
            RetryDecision.retryable(
                new UnknownHttpStatusCodeException(599, "unknown", HttpHeaders.EMPTY, null, null)))
        .isTrue();
  }

  @Test
  void plainRuntimeExceptionIsNotRetryable() {
    assertThat(RetryDecision.retryable(new IllegalStateException("boom"))).isFalse();
  }

  @Test
  void nullIsNotRetryable() {
    assertThat(RetryDecision.retryable(null)).isFalse();
  }
}
