package com.eclipse.tarjanapa.service;

import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.algorithms.TarjanIterative;
import com.eclipse.tarjanapa.analysis.Benchmark;
import com.eclipse.tarjanapa.analysis.ClosureAnalysis;
import com.eclipse.tarjanapa.analysis.CriticalRoutesAnalysis;
import com.eclipse.tarjanapa.analysis.CsvExporter;
import com.eclipse.tarjanapa.analysis.LiveAnalysis;
import com.eclipse.tarjanapa.graph.GraphBuilder;
import com.eclipse.tarjanapa.model.Agency;
import com.eclipse.tarjanapa.model.Vehicle;
import com.eclipse.tarjanapa.tranzy.TranzyDataSource;
import com.eclipse.tarjanapa.tranzy.TranzyException;
import com.eclipse.tarjanapa.tranzy.TranzyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Punctul central al aplicației: încarcă datele, construiește graful, rulează
 * Tarjan și expune analizele. Folosit atât de API-ul REST, cât și de {@code ConsoleApp}.
 */
@Service
public class NetworkService {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    private final TranzyDataSource dataSource;
    private final ClosureAnalysis closure;
    private final CriticalRoutesAnalysis critical;
    private final LiveAnalysis live;
    private final CsvExporter exporter;
    private final BenchmarkProperties benchmarkProps;

    private volatile String agencyId;
    private volatile NetworkSnapshot snapshot;
    private volatile String lastError = "Datele nu au fost încă încărcate.";
    private volatile Benchmark.Result lastBenchmark;

    public NetworkService(TranzyProperties props, TranzyDataSource dataSource, ClosureAnalysis closure,
                          CriticalRoutesAnalysis critical, LiveAnalysis live, CsvExporter exporter,
                          BenchmarkProperties benchmarkProps) {
        this.dataSource = dataSource;
        this.closure = closure;
        this.critical = critical;
        this.live = live;
        this.exporter = exporter;
        this.benchmarkProps = benchmarkProps;
        this.agencyId = props.hasAgencyId() ? props.agencyId().trim() : null;
    }

    public String agencyId() {
        return agencyId;
    }

    /** Schimbă operatorul (de ex. ales din consolă); datele trebuie reîncărcate după. */
    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId == null || agencyId.isBlank() ? null : agencyId.trim();
    }

    public List<Agency> agencies(boolean forceRefresh) {
        return dataSource.agencies(forceRefresh);
    }

    /**
     * Încarcă (sau reîncarcă) rețeaua.
     *
     * @param forceRefresh true = ignoră cache-ul și descarcă din Tranzy
     */
    public synchronized NetworkSnapshot load(boolean forceRefresh) {
        try {
            TranzyDataSource.DatasetLoad dl = dataSource.dataset(agencyId, forceRefresh);
            GraphBuilder.BuildResult built = GraphBuilder.build(dl.dataset());
            SccResult scc = new TarjanIterative().run(built.graph().intGraph());

            List<String> warnings = new ArrayList<>(dl.warnings());
            if (built.graph().stopCount() == 0) {
                warnings.add("Graful rezultat este gol: verificați că /stop_times conține date pentru acest operator.");
            }
            NetworkSnapshot s = new NetworkSnapshot(agencyId, Instant.now(), built.graph(), built.report(), scc,
                    dl.sources(), warnings, critical);
            snapshot = s;
            lastError = null;
            log.info("Rețea încărcată (agency {}): V={}, E={}, SCC={}, cea mai mare SCC={} ({}%), Tarjan {} ms",
                    agencyId, s.state().stops(), s.state().edges(), s.state().sccCount(), s.state().largestSize(),
                    String.format("%.1f", s.state().largestPercent()), String.format("%.2f", scc.elapsedMillis()));
            return s;
        } catch (TranzyException e) {
            lastError = e.getMessage();
            throw e;
        }
    }

    /** Ultima rețea încărcată; aruncă {@link NoDataException} dacă nu există. */
    public NetworkSnapshot snapshot() {
        NetworkSnapshot s = snapshot;
        if (s == null) {
            throw new NoDataException(lastError);
        }
        return s;
    }

    public boolean isLoaded() {
        return snapshot != null;
    }

    public ClosureAnalysis.ClosureResult simulateClosure(Set<String> routeIds) {
        NetworkSnapshot s = snapshot();
        return closure.simulate(s.graph(), s.scc(), routeIds);
    }

    public LiveAnalysis.LiveResult live(boolean forceRefresh) {
        NetworkSnapshot s = snapshot();
        List<String> warnings = new ArrayList<>();
        List<Vehicle> vehicles = dataSource.vehicles(s.agencyId(), forceRefresh, warnings);
        warnings.forEach(log::warn);
        return live.analyze(s.graph(), vehicles);
    }

    /** Rulează benchmark-ul; rețeaua reală se include dacă e încărcată. Rezultatul se exportă în CSV. */
    public BenchmarkRun benchmark(Benchmark.Config config) {
        NetworkSnapshot s = snapshot;
        Benchmark.Result result = new Benchmark().run(config, s == null ? null : s.graph().intGraph());
        lastBenchmark = result;
        Path csv = exporter.benchmark(result);
        return new BenchmarkRun(result, csv.toString());
    }

    public record BenchmarkRun(Benchmark.Result result, String csvFile) {
    }

    public Benchmark.Config defaultBenchmarkConfig() {
        return benchmarkProps.toConfig();
    }

    public Benchmark.Result lastBenchmark() {
        return lastBenchmark;
    }

    /** Exportă în CSV tot ce e disponibil; întoarce fișierele scrise. */
    public List<Path> exportAll() {
        NetworkSnapshot s = snapshot();
        List<Path> files = new ArrayList<>();
        files.add(exporter.smallComponents(s.state()));
        files.add(exporter.criticalRoutes(s.criticalRoutes()));
        files.add(exporter.bridges(s.bridges()));
        if (lastBenchmark != null) {
            files.add(exporter.benchmark(lastBenchmark));
        }
        return files;
    }
}
