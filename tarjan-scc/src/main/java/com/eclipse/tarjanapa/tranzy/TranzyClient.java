package com.eclipse.tarjanapa.tranzy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Client HTTP minimal pentru Tranzy Open Data API.
 * <p>
 * Trimite headerele {@code X-API-KEY} și (opțional) {@code X-Agency-Id} și
 * tratează erorile: cheie lipsă, 401/403, 429 cu reîncercare și backoff
 * exponențial (respectând {@code Retry-After}), erori 5xx și de rețea.
 */
public class TranzyClient {

    private static final Logger log = LoggerFactory.getLogger(TranzyClient.class);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final TranzyProperties props;
    private final HttpClient http;

    public TranzyClient(TranzyProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Descarcă un endpoint și întoarce corpul răspunsului (JSON brut).
     *
     * @param endpoint de ex. "routes", "stop_times"
     * @param agencyId operatorul, sau null pentru endpoint-uri globale ("agency")
     */
    public String fetch(String endpoint, String agencyId) {
        if (!props.hasApiKey()) {
            throw new TranzyException(TranzyException.Kind.MISSING_API_KEY,
                    "Lipsește cheia API. Setați variabila de mediu TRANZY_API_KEY "
                            + "(cheia se obține gratuit din contul de dezvoltator Tranzy).");
        }
        String base = props.baseUrl().endsWith("/") ? props.baseUrl() : props.baseUrl() + "/";
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(base + endpoint))
                .timeout(props.timeout())
                .header("Accept", "application/json")
                .header("X-API-KEY", props.apiKey())
                .GET();
        if (agencyId != null && !agencyId.isBlank()) {
            rb.header("X-Agency-Id", agencyId);
        }
        HttpRequest request = rb.build();

        Duration backoff = props.initialBackoff();
        for (int attempt = 0; ; attempt++) {
            boolean lastAttempt = attempt >= props.maxRetries();
            try {
                HttpResponse<String> resp = http.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = resp.statusCode();

                if (status >= 200 && status < 300) {
                    return resp.body();
                }
                if (status == 401 || status == 403) {
                    throw new TranzyException(TranzyException.Kind.UNAUTHORIZED,
                            "Tranzy a refuzat accesul (HTTP " + status + ") la /" + endpoint
                                    + ": verificați TRANZY_API_KEY și agency ID-ul (" + agencyId + ").");
                }
                if (status == 429 || status >= 500) {
                    if (lastAttempt) {
                        throw new TranzyException(
                                status == 429 ? TranzyException.Kind.RATE_LIMITED : TranzyException.Kind.HTTP_ERROR,
                                "Tranzy /" + endpoint + " a răspuns HTTP " + status + " și după "
                                        + (attempt + 1) + " încercări.");
                    }
                    Duration wait = retryAfter(resp).orElse(backoff);
                    log.warn("Tranzy /{}: HTTP {}, reîncerc peste {} ms (încercarea {}/{})",
                            endpoint, status, wait.toMillis(), attempt + 1, props.maxRetries());
                    sleep(wait);
                    backoff = min(backoff.multipliedBy(2), MAX_BACKOFF);
                    continue;
                }
                throw new TranzyException(TranzyException.Kind.HTTP_ERROR,
                        "Tranzy /" + endpoint + " a răspuns HTTP " + status + ": " + abbreviate(resp.body()));
            } catch (IOException e) {
                if (lastAttempt) {
                    throw new TranzyException(TranzyException.Kind.NETWORK,
                            "Eroare de rețea la Tranzy /" + endpoint + ": " + e.getMessage(), e);
                }
                log.warn("Tranzy /{}: eroare de rețea ({}), reîncerc peste {} ms",
                        endpoint, e.getMessage(), backoff.toMillis());
                sleep(backoff);
                backoff = min(backoff.multipliedBy(2), MAX_BACKOFF);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TranzyException(TranzyException.Kind.NETWORK, "Cerere întreruptă", e);
            }
        }
    }

    /** Antetul Retry-After (în secunde), dacă există. */
    private static Optional<Duration> retryAfter(HttpResponse<?> resp) {
        return resp.headers().firstValue("Retry-After").flatMap(v -> {
            try {
                return Optional.of(min(Duration.ofSeconds(Long.parseLong(v.trim())), MAX_BACKOFF));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranzyException(TranzyException.Kind.NETWORK, "Așteptare întreruptă", e);
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
