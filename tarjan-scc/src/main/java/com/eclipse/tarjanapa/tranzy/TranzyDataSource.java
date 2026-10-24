package com.eclipse.tarjanapa.tranzy;

import com.eclipse.tarjanapa.model.Agency;
import com.eclipse.tarjanapa.model.GtfsDataset;
import com.eclipse.tarjanapa.model.Vehicle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Sursa de date cu cache local în fișiere JSON.
 * <p>
 * Fiecare răspuns se salvează în {@code {cacheDir}/{agencyId}/{endpoint}.json}.
 * Politica pentru fiecare endpoint:
 * <ol>
 *   <li>dacă nu se forțează reîmprospătarea și cache-ul e mai nou decât TTL-ul → se citește din cache;</li>
 *   <li>altfel se descarcă din Tranzy și se suprascrie cache-ul (doar dacă răspunsul e valid);</li>
 *   <li>dacă descărcarea eșuează (offline, fără cheie, 429...) dar există un cache
 *       vechi → se folosește cache-ul vechi, cu avertisment. Astfel aplicația
 *       merge offline la prezentare.</li>
 * </ol>
 */
public class TranzyDataSource {

    private static final Logger log = LoggerFactory.getLogger(TranzyDataSource.class);
    private static final String GLOBAL_DIR = "_global";

    private final TranzyProperties props;
    private final TranzyClient client;
    private final ObjectMapper mapper;

    public TranzyDataSource(TranzyProperties props, TranzyClient client, ObjectMapper mapper) {
        this.props = props;
        this.client = client;
        this.mapper = mapper;
    }

    /** De unde provin datele unui endpoint. */
    public enum Origin { CACHE, API, STALE_CACHE }

    /** Un endpoint încărcat: înregistrările JSON, originea și momentul descărcării. */
    public record Loaded(String endpoint, JsonNode records, Origin origin, Instant fetchedAt) {
    }

    /** Setul GTFS încărcat, cu detaliile fiecărui endpoint și avertismentele apărute. */
    public record DatasetLoad(GtfsDataset dataset, List<Loaded> sources, List<String> warnings) {
    }

    public Path cacheFile(String agencyId, String endpoint) {
        String dir = agencyId == null || agencyId.isBlank() ? GLOBAL_DIR : agencyId;
        return props.cacheDir().resolve(dir).resolve(endpoint + ".json");
    }

    /** Lista operatorilor (pentru alegerea X-Agency-Id). */
    public List<Agency> agencies(boolean forceRefresh) {
        Loaded l = load("agency", null, props.cacheTtl(), forceRefresh, false, new ArrayList<>());
        return GtfsParser.agencies(l.records());
    }

    /** Datele statice necesare grafului: routes, trips, stops, stop_times (+ shapes, opțional). */
    public DatasetLoad dataset(String agencyId, boolean forceRefresh) {
        requireAgency(agencyId);
        List<String> warnings = new ArrayList<>();
        List<Loaded> sources = new ArrayList<>();
        Loaded routes = load("routes", agencyId, props.cacheTtl(), forceRefresh, false, warnings);
        Loaded trips = load("trips", agencyId, props.cacheTtl(), forceRefresh, false, warnings);
        Loaded stops = load("stops", agencyId, props.cacheTtl(), forceRefresh, false, warnings);
        Loaded stopTimes = load("stop_times", agencyId, props.cacheTtl(), forceRefresh, false, warnings);
        sources.addAll(List.of(routes, trips, stops, stopTimes));
        if (props.loadShapes()) {
            try {
                sources.add(load("shapes", agencyId, props.cacheTtl(), forceRefresh, true, warnings));
            } catch (TranzyException e) {
                warnings.add("Formele traseelor (/shapes) nu au putut fi încărcate: " + e.getMessage());
            }
        }

        GtfsDataset ds = new GtfsDataset(
                parse(routes, GtfsParser::routes, warnings),
                parse(trips, GtfsParser::trips, warnings),
                parse(stops, GtfsParser::stops, warnings),
                parse(stopTimes, GtfsParser::stopTimes, warnings));
        return new DatasetLoad(ds, sources, warnings);
    }

    /** Pozițiile vehiculelor (cache scurt; lista poate fi goală noaptea). */
    public List<Vehicle> vehicles(String agencyId, boolean forceRefresh, List<String> warnings) {
        requireAgency(agencyId);
        Loaded l = load("vehicles", agencyId, props.vehiclesTtl(), forceRefresh, true, warnings);
        return GtfsParser.vehicles(l.records());
    }

    private static void requireAgency(String agencyId) {
        if (agencyId == null || agencyId.isBlank()) {
            throw new TranzyException(TranzyException.Kind.MISSING_AGENCY,
                    "Nu este setat operatorul (tranzy.agency-id în application.yml sau TRANZY_AGENCY_ID). "
                            + "Lista operatorilor este afișată la pornire și la GET /api/agencies.");
        }
    }

    private <T> List<T> parse(Loaded l, Function<JsonNode, List<T>> parser, List<String> warnings) {
        List<T> list = parser.apply(l.records());
        int skipped = l.records().size() - list.size();
        if (skipped > 0) {
            warnings.add("/" + l.endpoint() + ": " + skipped + " înregistrări fără câmpurile obligatorii au fost ignorate.");
        }
        return list;
    }

    /** Încarcă un endpoint aplicând politica de cache descrisă la nivelul clasei. */
    Loaded load(String endpoint, String agencyId, Duration ttl, boolean force, boolean allowEmpty,
                List<String> warnings) {
        Path file = cacheFile(agencyId, endpoint);
        boolean cached = Files.isRegularFile(file);

        if (cached && !force) {
            Instant modified = lastModified(file);
            if (Duration.between(modified, Instant.now()).compareTo(ttl) < 0) {
                return new Loaded(endpoint, readRecords(file), Origin.CACHE, modified);
            }
        }

        try {
            String body = client.fetch(endpoint, agencyId);
            JsonNode records = parseBody(endpoint, body);
            if (records.isEmpty() && !allowEmpty) {
                throw new TranzyException(TranzyException.Kind.EMPTY_RESPONSE,
                        "Tranzy /" + endpoint + " a întors o listă goală (agency " + agencyId + ").");
            }
            write(file, body);
            log.info("Tranzy /{}: {} înregistrări descărcate → {}", endpoint, records.size(), file);
            return new Loaded(endpoint, records, Origin.API, Instant.now());
        } catch (TranzyException e) {
            if (!cached) {
                throw e;
            }
            String msg = "/" + endpoint + ": " + e.getMessage() + " Se folosește cache-ul din "
                    + lastModified(file) + ".";
            log.warn(msg);
            warnings.add(msg);
            return new Loaded(endpoint, readRecords(file), Origin.STALE_CACHE, lastModified(file));
        }
    }

    private JsonNode parseBody(String endpoint, String body) {
        if (body == null || body.isBlank()) {
            throw new TranzyException(TranzyException.Kind.EMPTY_RESPONSE,
                    "Tranzy /" + endpoint + " a întors un răspuns gol.");
        }
        try {
            return GtfsParser.records(mapper.readTree(body));
        } catch (IOException e) {
            throw new TranzyException(TranzyException.Kind.NETWORK,
                    "Tranzy /" + endpoint + " a întors JSON invalid: " + e.getMessage(), e);
        }
    }

    private JsonNode readRecords(Path file) {
        try {
            return GtfsParser.records(mapper.readTree(file.toFile()));
        } catch (IOException e) {
            throw new TranzyException(TranzyException.Kind.NETWORK,
                    "Cache-ul " + file + " nu poate fi citit: " + e.getMessage(), e);
        }
    }

    /** Scriere atomică: fișier temporar + mutare, ca un cache pe jumătate scris să nu existe niciodată. */
    private static void write(Path file, String body) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            Files.writeString(tmp, body);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Nu pot scrie cache-ul {}: {}", file, e.getMessage());
        }
    }

    private static Instant lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
