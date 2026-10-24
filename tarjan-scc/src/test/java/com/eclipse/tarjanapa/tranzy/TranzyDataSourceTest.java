package com.eclipse.tarjanapa.tranzy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Politica de cache, fără rețea: clientul nu are cheie API, deci orice descărcare eșuează. */
class TranzyDataSourceTest {

    @TempDir
    Path cacheDir;

    private TranzyDataSource dataSource() {
        TranzyProperties props = new TranzyProperties("https://api.tranzy.ai/v1/opendata/", "", "1", cacheDir,
                Duration.ofHours(24), Duration.ofSeconds(60), 0, Duration.ofMillis(1), Duration.ofSeconds(5), false);
        return new TranzyDataSource(props, new TranzyClient(props), new ObjectMapper());
    }

    private void copyFixtures(Path target) throws IOException {
        Files.createDirectories(target);
        for (String name : new String[]{"routes", "trips", "stops", "stop_times", "vehicles"}) {
            try (InputStream in = getClass().getResourceAsStream("/fixtures/gtfs/" + name + ".json")) {
                Files.copy(in, target.resolve(name + ".json"));
            }
        }
    }

    @Test
    void cacheProaspatSeFolosesteFaraRetea() throws IOException {
        copyFixtures(cacheDir.resolve("1"));
        TranzyDataSource.DatasetLoad load = dataSource().dataset("1", false);
        assertEquals(3, load.dataset().routes().size());
        assertEquals(TranzyDataSource.Origin.CACHE, load.sources().get(0).origin());
    }

    @Test
    void cacheVechiSeFolosesteCandDescarcareaEsueaza() throws IOException {
        copyFixtures(cacheDir.resolve("1"));
        FileTime old = FileTime.from(Instant.now().minus(Duration.ofDays(3)));
        try (var files = Files.list(cacheDir.resolve("1"))) {
            files.forEach(f -> {
                try {
                    Files.setLastModifiedTime(f, old);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        TranzyDataSource.DatasetLoad load = dataSource().dataset("1", false);
        assertEquals(TranzyDataSource.Origin.STALE_CACHE, load.sources().get(0).origin());
        assertFalse(load.warnings().isEmpty());
    }

    @Test
    void faraCacheSiFaraCheieEroareClara() {
        TranzyException e = assertThrows(TranzyException.class, () -> dataSource().dataset("1", false));
        assertEquals(TranzyException.Kind.MISSING_API_KEY, e.kind());
    }

    @Test
    void faraAgencyEroareClara() {
        TranzyException e = assertThrows(TranzyException.class, () -> dataSource().dataset(" ", false));
        assertEquals(TranzyException.Kind.MISSING_AGENCY, e.kind());
    }

    @Test
    void caleaCacheului() {
        assertEquals(cacheDir.resolve("1").resolve("stop_times.json"), dataSource().cacheFile("1", "stop_times"));
        assertEquals(cacheDir.resolve("_global").resolve("agency.json"), dataSource().cacheFile(null, "agency"));
    }
}
