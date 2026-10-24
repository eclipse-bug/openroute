package com.eclipse.tarjanapa.tranzy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configurarea accesului la Tranzy Open Data API (secțiunea {@code tranzy:} din application.yml).
 *
 * @param baseUrl        adresa de bază a API-ului
 * @param apiKey         cheia API (din variabila de mediu TRANZY_API_KEY)
 * @param agencyId       operatorul (X-Agency-Id); gol ⇒ la pornire se afișează lista operatorilor
 * @param cacheDir       directorul cache-ului JSON local
 * @param cacheTtl       vechimea maximă a cache-ului pentru datele statice (implicit 24h)
 * @param vehiclesTtl    vechimea maximă a cache-ului pentru poziția vehiculelor
 * @param maxRetries     numărul maxim de reîncercări la 429 / 5xx / erori de rețea
 * @param initialBackoff pauza inițială între reîncercări (se dublează la fiecare încercare)
 * @param timeout        timeout-ul unei cereri HTTP
 * @param loadShapes     dacă se descarcă și /shapes (nu e necesar pentru graf)
 */
@ConfigurationProperties(prefix = "tranzy")
public record TranzyProperties(
        @DefaultValue("https://api.tranzy.ai/v1/opendata/") String baseUrl,
        String apiKey,
        String agencyId,
        @DefaultValue("data/cache") Path cacheDir,
        @DefaultValue("24h") Duration cacheTtl,
        @DefaultValue("60s") Duration vehiclesTtl,
        @DefaultValue("4") int maxRetries,
        @DefaultValue("1s") Duration initialBackoff,
        @DefaultValue("30s") Duration timeout,
        @DefaultValue("false") boolean loadShapes) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasAgencyId() {
        return agencyId != null && !agencyId.isBlank();
    }
}
