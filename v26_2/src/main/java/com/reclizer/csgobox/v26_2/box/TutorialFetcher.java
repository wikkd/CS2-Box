package com.reclizer.csgobox.v26_2.box;

import com.reclizer.csgobox.v26_2.CsgoBox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Fetches tutorial markdown content from a list of {@link TutorialSources.Source}s.
 * Tries each enabled source in order; the first successful response wins.
 *
 * <p>All exceptions are caught and logged at WARN level; the caller treats
 * a {@code null} return as "no source succeeded". The HttpClient is reused
 * across calls but is otherwise stateless.</p>
 */
final class TutorialFetcher {

    /**
     * Hard cap on the size of a tutorial response body. Tutorials are short
     * markdown files; anything multi-MB is almost certainly hostile. Refusing
     * here prevents a malicious or compromised mirror from OOM-ing the JVM
     * via {@link HttpResponse.BodyHandlers#ofString()}.
     */
    private static final long MAX_BODY_BYTES = 10L * 1024 * 1024;

    private final HttpClient client;

    TutorialFetcher() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Returns the body of the first successful response, or {@code null}
     * if every enabled source failed or returned empty content.
     */
    String fetch(String fileName, List<TutorialSources.Source> sources) {
        for (TutorialSources.Source src : sources) {
            if (!src.enabled()) continue;
            String url = src.baseUrl() + fileName;
            String body = tryOnce(url, src);
            if (body != null) {
                return body;
            }
        }
        return null;
    }

    private String tryOnce(String url, TutorialSources.Source src) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                CsgoBox.LOGGER.warn(
                        "Tutorial fetch from {} rejected: scheme '{}' must be http(s)",
                        url, scheme);
                return null;
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(src.timeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                if (resp.body().length() > MAX_BODY_BYTES) {
                    CsgoBox.LOGGER.warn(
                            "Tutorial from {} exceeded {} bytes (got {}); refusing",
                            url, MAX_BODY_BYTES, resp.body().length());
                    return null;
                }
                CsgoBox.LOGGER.info("Downloaded tutorial from {} ({}): {} bytes",
                        src.name(), url, resp.body().length());
                return resp.body();
            }
            CsgoBox.LOGGER.warn("Tutorial fetch from {} returned HTTP {}: {}",
                    src.name(), resp.statusCode(), url);
        } catch (Exception e) {
            CsgoBox.LOGGER.warn("Tutorial fetch from {} failed: {} ({})",
                    src.name(), url, e.getMessage());
        }
        return null;
    }
}