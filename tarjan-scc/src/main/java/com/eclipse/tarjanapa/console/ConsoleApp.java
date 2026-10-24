package com.eclipse.tarjanapa.console;

import com.eclipse.tarjanapa.TarjanApaApplication;
import com.eclipse.tarjanapa.algorithms.SccChoice;
import com.eclipse.tarjanapa.analysis.Benchmark;
import com.eclipse.tarjanapa.analysis.ClosureAnalysis;
import com.eclipse.tarjanapa.analysis.CriticalRoutesAnalysis;
import com.eclipse.tarjanapa.analysis.NetworkState;
import com.eclipse.tarjanapa.analysis.StopInfo;
import com.eclipse.tarjanapa.analysis.StructureAnalysis;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Agency;
import com.eclipse.tarjanapa.model.Route;
import com.eclipse.tarjanapa.service.NetworkService;
import com.eclipse.tarjanapa.service.NetworkSnapshot;
import com.eclipse.tarjanapa.service.NoDataException;
import com.eclipse.tarjanapa.tranzy.TranzyException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Modul consolă, cu meniu text — util pentru capturile din raport.
 * <p>
 * Pornește contextul Spring fără server web (aceeași configurație ca serverul)
 * și folosește direct {@link NetworkService}. Toate analizele rulează cu
 * algoritmul SCC ales din meniu: Tarjan (implicit), Kosaraju sau Gabow.
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.main-class=com.eclipse.tarjanapa.console.ConsoleApp
 * </pre>
 */
public final class ConsoleApp {

    private final NetworkService service;
    private final BufferedReader in;
    private final PrintStream out;
    private SccChoice algorithm = SccChoice.TARJAN;

    private ConsoleApp(NetworkService service) {
        this.service = service;
        this.in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TarjanApaApplication.class)
                .web(WebApplicationType.NONE)
                .properties("app.load-on-startup=false", "spring.main.banner-mode=off",
                        "logging.level.root=WARN", "logging.level.com.eclipse.tarjanapa=WARN")
                .run(args);
        try {
            new ConsoleApp(ctx.getBean(NetworkService.class)).loop();
        } finally {
            ctx.close();
        }
    }

    private void loop() throws IOException {
        out.println("=== Analiza conectivității transportului public — SCC (Tarjan / Kosaraju / Gabow) ===");
        while (true) {
            out.println();
            out.println("Operator: " + (service.agencyId() == null ? "(nesetat)" : service.agencyId())
                    + (service.isLoaded() ? "   date încărcate" : "   date neîncărcate")
                    + "   algoritm: " + algorithm.label());
            out.println(" 1. Încarcă date (din cache sau Tranzy)");
            out.println(" 2. Sumar rețea");
            out.println(" 3. Închide rută(e)");
            out.println(" 4. Rute critice");
            out.println(" 5. Punți și puncte de articulație");
            out.println(" 6. Graf condensat");
            out.println(" 7. Benchmark");
            out.println(" 8. Export CSV");
            out.println(" 9. Reîncarcă forțat din Tranzy");
            out.println(" A. Alege algoritmul SCC (Tarjan / Kosaraju / Gabow)");
            out.println(" C. Compară cei 3 algoritmi pe rețeaua reală");
            out.println(" 0. Ieșire");
            String choice = ask("Opțiune");
            if (choice == null || choice.equals("0")) {
                return;
            }
            try {
                switch (choice.trim().toUpperCase(Locale.ROOT)) {
                    case "1" -> load(false);
                    case "2" -> summary();
                    case "3" -> closeRoutes();
                    case "4" -> criticalRoutes();
                    case "5" -> bridges();
                    case "6" -> condensation();
                    case "7" -> benchmark();
                    case "8" -> exportCsv();
                    case "9" -> load(true);
                    case "A" -> chooseAlgorithm();
                    case "C" -> compareAlgorithms();
                    default -> out.println("Opțiune necunoscută.");
                }
            } catch (TranzyException | NoDataException | IllegalArgumentException | IllegalStateException e) {
                out.println("Eroare: " + e.getMessage());
            }
        }
    }

    private void load(boolean force) throws IOException {
        if (service.agencyId() == null) {
            List<Agency> agencies = service.agencies(false);
            out.println("Operatori disponibili:");
            for (Agency a : agencies) {
                out.printf("  %-6s %s%n", a.id(), a.name());
            }
            String id = ask("ID-ul operatorului");
            if (id == null || id.isBlank()) {
                return;
            }
            service.setAgencyId(id);
        }
        NetworkSnapshot s = service.load(force);
        out.printf("Încărcat: %d stații, %d arce, %d rute.%n", s.graph().stopCount(), s.graph().edgeCount(),
                s.graph().routes().size());
        s.sources().forEach(l -> out.printf("  /%-11s %6d înregistrări  (%s, %s)%n",
                l.endpoint(), l.records().size(), l.origin(), l.fetchedAt()));
        s.warnings().forEach(w -> out.println("  Avertisment: " + w));
    }

    private void chooseAlgorithm() throws IOException {
        SccChoice[] all = SccChoice.values();
        for (int i = 0; i < all.length; i++) {
            out.printf(" %d. %-9s %s%n", i + 1, all[i].label(), all[i].description());
        }
        String a = ask("Algoritm [" + algorithm.label() + "]");
        if (a == null || a.isBlank()) {
            return;
        }
        a = a.trim();
        algorithm = a.matches("[1-9]") && Integer.parseInt(a) <= all.length
                ? all[Integer.parseInt(a) - 1] : SccChoice.parse(a);
        out.println("Algoritm ales: " + algorithm.label());
    }

    /** Rulează cei trei algoritmi pe rețeaua încărcată și verifică faptul că dau aceleași componente. */
    private void compareAlgorithms() {
        NetworkSnapshot s = service.snapshot();
        out.printf("%-10s %8s %12s %12s %10s%n", "Algoritm", "SCC", "SCC maximă", "Timp (ms)", "Operații");
        NetworkSnapshot.AlgorithmView first = null;
        for (SccChoice c : SccChoice.values()) {
            NetworkSnapshot.AlgorithmView v = s.view(c);
            out.printf(Locale.ROOT, "%-10s %8d %12d %12.3f %10d%n", c.label(), v.scc().count(), v.scc().largestSize(),
                    v.scc().elapsedMillis(), v.scc().operations());
            if (first == null) {
                first = v;
            } else if (!first.scc().samePartitionAs(v.scc())) {
                out.println("ATENȚIE: " + c.label() + " a găsit alte componente decât " + first.choice().label() + "!");
                return;
            }
        }
        out.println("Toți trei au găsit exact aceleași componente tare conexe.");
        out.println("(Timpul e măsurat la prima rulare; pentru medii cu warm-up folosiți opțiunea 7, Benchmark.)");
    }

    private void summary() {
        NetworkSnapshot s = service.snapshot();
        NetworkSnapshot.AlgorithmView v = s.view(algorithm);
        NetworkState st = v.state();
        out.println("--- Sumar rețea ---");
        out.printf("Stații (V):                 %d%n", st.stops());
        out.printf("Arce (E):                   %d%n", st.edges());
        out.printf("Grad mediu (E/V):           %.2f%n", st.averageDegree());
        out.printf("Rute (cu arce / total):     %d / %d%n", st.activeRoutes(), s.graph().routes().size());
        out.printf("Componente tare conexe:     %d%n", st.sccCount());
        out.printf("Cea mai mare SCC:           %d stații (%.1f%%)%n", st.largestSize(), st.largestPercent());
        out.printf("Timp %-22s %.3f ms, %d operații%n", algorithm.label() + ":", v.scc().elapsedMillis(),
                v.scc().operations());
        out.printf("Stații nedeservite (excluse): %d%n", s.buildReport().unservedStops());
        if (!st.smallComponentStops().isEmpty()) {
            out.println("Stații în afara SCC mari:");
            printStops(st.smallComponentStops(), 40);
        }
    }

    private void closeRoutes() throws IOException {
        TransitGraph g = service.snapshot().graph();
        String line = ask("Rute de închis (numere sau id-uri, separate prin virgulă)");
        if (line == null || line.isBlank()) {
            return;
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String token : line.split("[,;\\s]+")) {
            if (!token.isBlank()) {
                ids.add(resolveRoute(g, token.trim()));
            }
        }
        ClosureAnalysis.ClosureResult r = service.simulateClosure(ids, algorithm);
        out.printf("Recalculat cu %s în %.3f ms%n", algorithm.label(), r.sccAfter().elapsedMillis());
        out.println("Rute închise: " + r.closedRoutes().stream().map(ClosureAnalysis.RouteRef::label).toList());
        if (!r.unknownRoutes().isEmpty()) {
            out.println("Rute necunoscute: " + r.unknownRoutes());
        }
        out.printf("SCC: %d → %d;  cea mai mare SCC: %d → %d stații%n",
                r.before().sccCount(), r.after().sccCount(), r.before().largestSize(), r.after().largestSize());
        out.printf("Stații desprinse din componenta mare: %d%n", r.detachedCount());
        printStops(r.detachedStops(), 50);
    }

    /** Acceptă atât numărul rutei („22”), cât și route_id-ul. */
    private static String resolveRoute(TransitGraph g, String token) {
        if (g.routeIndexOf(token) >= 0) {
            return token;
        }
        for (Route r : g.routes()) {
            if (token.equalsIgnoreCase(r.shortName())) {
                return r.id();
            }
        }
        return token;
    }

    private void criticalRoutes() throws IOException {
        String l = ask("Câte rute afișez [20]");
        int limit = l == null || l.isBlank() ? 20 : Integer.parseInt(l.trim());
        List<CriticalRoutesAnalysis.RouteImpact> all = service.view(algorithm).criticalRoutes();
        out.println("Rute critice calculate cu " + algorithm.label() + ":");
        out.printf("%-4s %-8s %-10s %8s %8s %10s  %s%n", "Loc", "Ruta", "Tip", "Pierdute", "SCC", "SCC max", "Denumire");
        for (CriticalRoutesAnalysis.RouteImpact x : all.subList(0, Math.min(limit, all.size()))) {
            out.printf("%-4d %-8s %-10s %8d %8d %10d  %s%n", x.rank(), x.label(), x.type(), x.lostStops(),
                    x.sccAfter(), x.largestAfter(), x.longName() == null ? "" : x.longName());
        }
    }

    private void bridges() {
        StructureAnalysis.BridgeReport r = service.view(algorithm).bridges();
        out.printf("Punți (segmente fără alternativă): %d%n", r.bridgeCount());
        r.bridges().stream().limit(40).forEach(b ->
                out.printf("  %s — %s   [%s]%n", b.fromName(), b.toName(), String.join(", ", b.routes())));
        if (r.bridgeCount() > 40) {
            out.println("  … încă " + (r.bridgeCount() - 40));
        }
        out.printf("Puncte de articulație (stații fără ocolire): %d%n", r.articulationCount());
        printStops(r.articulationPoints(), 40);
    }

    private void condensation() {
        StructureAnalysis.CondensationReport c = service.view(algorithm).condensation();
        out.println("Componente calculate cu " + algorithm.label() + ":");
        out.printf("Componente (noduri în DAG): %d%n", c.componentCount());
        out.printf("Arce în DAG:                %d%n", c.dagEdges());
        out.printf("Surse / destinații:         %d / %d (izolate: %d)%n", c.sources(), c.sinks(), c.isolated());
        out.printf("Arce minime de adăugat pentru tare conexitate: %d%n", c.minEdgesToAdd());
        c.components().stream().limit(15).forEach(k -> out.printf("  SCC %-5d mărime %-6d in %-3d out %-3d %s%s  %s%n",
                k.id(), k.size(), k.inDegree(), k.outDegree(), k.source() ? "[sursă]" : "",
                k.sink() ? "[destinație]" : "", String.join(", ", k.sampleStops())));
    }

    private void benchmark() throws IOException {
        Benchmark.Config d = service.defaultBenchmarkConfig();
        out.println("Configurație implicită: mărimi " + d.sizes() + ", grad mediu " + d.avgDegree()
                + ", seed " + d.seed() + ", " + d.runs() + " rulări + " + d.warmupRuns() + " warm-up.");
        String deg = ask("Grad mediu [" + d.avgDegree() + "]");
        Benchmark.Config config = deg == null || deg.isBlank() ? d
                : new Benchmark.Config(d.sizes(), Double.parseDouble(deg.trim()), d.seed(), d.runs(), d.warmupRuns(),
                d.naiveMaxNodes());
        out.println("Rulez benchmark-ul…");
        NetworkService.BenchmarkRun run = service.benchmark(config);
        out.printf("%-14s %8s %9s %-16s %12s %14s %7s%n", "Graf", "V", "E", "Algoritm", "Timp (ms)", "Operații", "SCC");
        for (Benchmark.Row r : run.result().rows()) {
            out.printf(Locale.ROOT, "%-14s %8d %9d %-16s %12.3f %14.0f %7d%n", r.graph(), r.nodes(), r.edges(),
                    r.algorithm(), r.avgMillis(), r.avgOperations(), r.sccCount());
        }
        out.println("Toți algoritmii au găsit aceleași componente. CSV: " + run.csvFile());
    }

    private void exportCsv() {
        List<Path> files = service.exportAll(algorithm);
        out.println("Fișiere scrise:");
        files.forEach(f -> out.println("  " + f.toAbsolutePath()));
    }

    private void printStops(List<StopInfo> stops, int max) {
        stops.stream().limit(max).forEach(s -> out.printf("  [SCC %d, %d stații] %s (%s)  rute: %s%n",
                s.component(), s.componentSize(), s.name(), s.id(), String.join(", ", s.routes())));
        if (stops.size() > max) {
            out.println("  … încă " + (stops.size() - max));
        }
    }

    private String ask(String prompt) throws IOException {
        out.print(prompt + ": ");
        out.flush();
        return in.readLine();
    }
}
