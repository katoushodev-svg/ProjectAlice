package com.projectalice.backend.memory.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.projectalice.backend.memory.application.GetMemoryPreferencesUseCase;
import com.projectalice.backend.memory.application.UpdateMemoryPreferencesUseCase;
import com.projectalice.backend.memory.application.port.out.MemoryPreferencesUpdateConflictException;
import com.projectalice.backend.memory.domain.MemoryPreferences;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ALICE_CURSOR_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class MemoryPreferencesHttpIntegrationTest {

    private static final String PATH = "/api/v1/memory-preferences";

    @Autowired
    private org.springframework.core.env.Environment environment;

    @MockitoBean
    private GetMemoryPreferencesUseCase getPreferences;

    @MockitoBean
    private UpdateMemoryPreferencesUseCase updatePreferences;

    private HttpResponse<String> send(
            String method,
            String body,
            String ifMatch) throws Exception {

        int port = Integer.parseInt(
                environment.getRequiredProperty("local.server.port"));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + PATH));

        if (ifMatch != null) {
            builder.header("If-Match", ifMatch);
        }

        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        return HttpClient.newHttpClient().send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private MemoryPreferences preferences(
            boolean autoSave,
            boolean answerUse,
            long version) {
        return MemoryPreferences.reconstitute(
                autoSave,
                answerUse,
                version,
                OffsetDateTime.of(
                        2026, 10, 17, 17, 0, 0, 123_000_000,
                        ZoneOffset.ofHours(9)));
    }

    @Test
    void getReturnsJsonAndStrongEtag() throws Exception {
        when(getPreferences.execute())
                .thenReturn(preferences(true, true, 1));

        var response = send("GET", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("application/json");
        assertThat(response.headers().firstValue("ETag").orElse(""))
                .isEqualTo("\"memory-preferences-v1\"");
        assertThat(response.headers().firstValue("Cache-Control").orElse(""))
                .isEqualTo("no-store");
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(response.body())
                .contains("\"autoSaveEnabled\":true")
                .contains("\"answerUseEnabled\":true")
                .contains("\"version\":1");
    }

    @Test
    void patchWithoutIfMatchReturns428ProblemDetails() throws Exception {
        var response = send(
                "PATCH", "{\"autoSaveEnabled\":false}", null);

        assertThat(response.statusCode()).isEqualTo(428);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(response.body())
                .contains("\"code\":\"PRECONDITION_REQUIRED\"")
                .contains("\"requestId\"");
    }

    @Test
    void patchWithMalformedIfMatchReturns400() throws Exception {
        for (String ifMatch : new String[] {
                "W/\"memory-preferences-v1\"",
                "*",
                "\"memory-preferences-v1\", \"memory-preferences-v2\"",
                "not-an-etag"
        }) {
            var response = send(
                    "PATCH",
                    "{\"autoSaveEnabled\":false}",
                    ifMatch);

            assertThat(response.statusCode())
                    .as("If-Match=%s", ifMatch)
                    .isEqualTo(400);
            assertThat(response.body())
                    .as("If-Match=%s", ifMatch)
                    .contains("\"code\":\"VALIDATION_ERROR\"");
        }
    }

    @Test
    void patchUpdatesSettingAndReturnsNewEtag() throws Exception {
        when(getPreferences.execute())
                .thenReturn(preferences(true, true, 1));
        when(updatePreferences.execute(false, true, 1))
                .thenReturn(preferences(false, true, 2));

        var response = send(
                "PATCH",
                "{\"autoSaveEnabled\":false}",
                "\"memory-preferences-v1\"");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("ETag").orElse(""))
                .isEqualTo("\"memory-preferences-v2\"");
        assertThat(response.body())
                .contains("\"autoSaveEnabled\":false")
                .contains("\"answerUseEnabled\":true")
                .contains("\"version\":2");
    }

    @Test
    void patchVersionConflictReturns412ProblemDetails() throws Exception {
        when(getPreferences.execute())
                .thenReturn(preferences(true, true, 1));
        when(updatePreferences.execute(false, true, 1))
                .thenThrow(new MemoryPreferencesUpdateConflictException(
                        "Preferences version conflict"));

        var response = send(
                "PATCH",
                "{\"autoSaveEnabled\":false}",
                "\"memory-preferences-v1\"");

        assertThat(response.statusCode()).isEqualTo(412);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(response.body())
                .contains("\"code\":\"PRECONDITION_FAILED\"");
    }

    @Test
    void patchRejectsUnknownField() throws Exception {
        var response = send(
                "PATCH",
                "{\"unknown\":true}",
                "\"memory-preferences-v1\"");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_ERROR\"");
    }
}
