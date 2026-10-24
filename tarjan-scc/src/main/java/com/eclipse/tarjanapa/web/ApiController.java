package com.eclipse.tarjanapa.web;

import com.eclipse.tarjanapa.algorithms.SccChoice;
import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.analysis.Benchmark;
import com.eclipse.tarjanapa.analysis.ClosureAnalysis;
import com.eclipse.tarjanapa.analysis.CriticalRoutesAnalysis;
import com.eclipse.tarjanapa.analysis.LiveAnalysis;
import com.eclipse.tarjanapa.analysis.NetworkState;
import com.eclipse.tarjanapa.analysis.StructureAnalysis;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Agency;
import com.eclipse.tarjanapa.model.Route;
import com.eclipse.tarjanapa.model.Stop;
import com.eclipse.tarjanapa.service.NetworkService;
import com.eclipse.tarjanapa.service.NetworkSnapshot;
import com.eclipse.tarjanapa.tranzy.TranzyProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * API-ul REST folosit de pagina web.
 * <p>
 * Endpoint-urile care depind de componentele tare conexe primesc parametrul
 * {@code algorithm} = {@code tarjan} (implicit), {@code kosaraju} sau {@code gabow}.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final NetworkService service;
    private final TranzyProperties tranzy;

    public ApiController(NetworkService service, TranzyProperties tranzy) {
        this.service = service;
        this.tranzy = tranzy;
    }

    // ------------------------------------------------------------- stare

    @GetMapping("/status")
    public ApiDtos.Status status() {
        if (!service.isLoaded()) {
            String error = null;
            try {
                service.snapshot();
            } catch (RuntimeException e) {
                error = e.getMessage();
            }
            return new ApiDtos.Status(false, service.agencyId(), tranzy.hasApiKey(), error, null, List.of(), List.of());
        }
        NetworkSnapshot s = service.snapshot();
        List<ApiDtos.Source> sources = s.sources().stream()
                .map(l -> new ApiDtos.Source(l.endpoint(), l.records().size(), l.origin().name(), l.fetchedAt()))
                .toList();
        return new ApiDtos.Status(true, s.agencyId(), tranzy.hasApiKey(), null, s.loadedAt(), s.warnings(), sources);
    }

    @GetMapping("/agencies")
    public List<Agency> agencies() {
        return service.agencies(false);
    }

    /** Cei trei algoritmi SCC care se pot alege. */
    @GetMapping("/algorithms")
    public List<ApiDtos.AlgorithmDto> algorithms() {
        return Arrays.stream(SccChoice.values())
                .map(c -> new ApiDtos.AlgorithmDto(c.id(), c.label(), c.description()))
                .toList();
    }

    @PostMapping("/data/refresh")
    public ApiDtos.Status refresh(@RequestParam(required = false) String agencyId) {
        if (agencyId != null && !agencyId.isBlank()) {
            service.setAgencyId(agencyId);
        }
        service.load(true);
        return status();
    }

    // ------------------------------------------------------------ rețea

    @GetMapping("/network/summary")
    public ApiDtos.Summary summary(@RequestParam(required = false) String algorithm) {
        NetworkSnapshot s = service.snapshot();
        NetworkSnapshot.AlgorithmView v = s.view(SccChoice.parse(algorithm));
        NetworkState st = v.state();
        return new ApiDtos.Summary(s.agencyId(), s.loadedAt(), run(v.choice(), v.scc()), st.stops(), st.edges(),
                st.averageDegree(), s.graph().routes().size(), st.activeRoutes(), st.sccCount(), st.largestSize(),
                st.largestPercent(), st.smallComponentCount(), s.buildReport().unservedStops(),
                st.smallComponentStops());
    }

    @GetMapping("/network/stops")
    public List<ApiDtos.StopDto> stops(@RequestParam(required = false) String algorithm) {
        NetworkSnapshot s = service.snapshot();
        return stopDtos(s.graph(), s.view(SccChoice.parse(algorithm)).scc());
    }

    @GetMapping("/network/edges")
    public List<ApiDtos.EdgeDto> edges() {
        TransitGraph g = service.snapshot().graph();
        List<ApiDtos.EdgeDto> out = new ArrayList<>(g.edgeCount());
        for (int e = 0; e < g.edgeCount(); e++) {
            List<String> routes = new ArrayList<>();
            for (int r : g.edgeRoutes(e)) {
                routes.add(g.route(r).id());
            }
            out.add(new ApiDtos.EdgeDto(g.stop(g.edgeFrom(e)).id(), g.stop(g.edgeTo(e)).id(), routes));
        }
        return out;
    }

    @GetMapping("/routes")
    public List<ApiDtos.RouteDto> routes() {
        TransitGraph g = service.snapshot().graph();
        int[] edgeCount = new int[g.routes().size()];
        for (int e = 0; e < g.edgeCount(); e++) {
            for (int r : g.edgeRoutes(e)) {
                edgeCount[r]++;
            }
        }
        List<ApiDtos.RouteDto> out = new ArrayList<>();
        for (int r = 0; r < g.routes().size(); r++) {
            Route route = g.route(r);
            out.add(new ApiDtos.RouteDto(route.id(), route.label(), route.longName(), route.typeName(), edgeCount[r]));
        }
        out.sort((a, b) -> CriticalRoutesAnalysis.compareLabels(a.label(), b.label()));
        return out;
    }

    // ---------------------------------------------------------- analize

    @PostMapping("/simulate/close")
    public ApiDtos.ClosureDto close(@RequestBody ApiDtos.CloseRequest request) {
        Set<String> ids = new LinkedHashSet<>(request.routeIds() == null ? List.of() : request.routeIds());
        SccChoice choice = SccChoice.parse(request.algorithm());
        boolean liveOnly = Boolean.TRUE.equals(request.liveOnly());
        ClosureAnalysis.ClosureResult r;
        if (liveOnly) {
            LiveAnalysis.LiveResult live = service.live(false, choice);
            r = new ClosureAnalysis(choice.create()).simulate(live.graph(), live.scc(), ids);
        } else {
            r = service.simulateClosure(ids, choice);
        }
        return new ApiDtos.ClosureDto(run(choice, r.sccAfter()), r.closedRoutes(), r.unknownRoutes(), liveOnly,
                r.before(), r.after(), r.detachedCount(), r.detachedStops());
    }

    @GetMapping("/analysis/critical-routes")
    public List<CriticalRoutesAnalysis.RouteImpact> criticalRoutes(@RequestParam(defaultValue = "20") int limit,
                                                                   @RequestParam(required = false) String algorithm) {
        List<CriticalRoutesAnalysis.RouteImpact> all = service.view(SccChoice.parse(algorithm)).criticalRoutes();
        return limit <= 0 ? all : all.subList(0, Math.min(limit, all.size()));
    }

    @GetMapping("/analysis/bridges")
    public StructureAnalysis.BridgeReport bridges(@RequestParam(required = false) String algorithm) {
        return service.view(SccChoice.parse(algorithm)).bridges();
    }

    @GetMapping("/analysis/condensation")
    public StructureAnalysis.CondensationReport condensation(@RequestParam(required = false) String algorithm) {
        return service.view(SccChoice.parse(algorithm)).condensation();
    }

    @GetMapping("/analysis/live")
    public ApiDtos.LiveDto live(@RequestParam(defaultValue = "false") boolean refresh,
                                @RequestParam(required = false) String algorithm) {
        SccChoice choice = SccChoice.parse(algorithm);
        LiveAnalysis.LiveResult r = service.live(refresh, choice);
        NetworkState st = r.state();
        return new ApiDtos.LiveDto(run(choice, r.scc()), r.vehicles(), r.vehiclesOnRoutes(), r.activeRouteIds(),
                r.activeRouteLabels(), r.inactiveStops(), st.stops(), st.edges(), st.sccCount(), st.largestSize(),
                st.largestPercent(), st.smallComponentStops(), stopDtos(r.graph(), r.scc()));
    }

    @PostMapping("/benchmark")
    public NetworkService.BenchmarkRun benchmark(@RequestBody(required = false) ApiDtos.BenchmarkRequest req) {
        Benchmark.Config d = service.defaultBenchmarkConfig();
        Benchmark.Config config = req == null ? d : new Benchmark.Config(
                req.sizes() != null ? req.sizes() : d.sizes(),
                req.avgDegree() != null ? req.avgDegree() : d.avgDegree(),
                req.seed() != null ? req.seed() : d.seed(),
                req.runs() != null ? req.runs() : d.runs(),
                req.warmupRuns() != null ? req.warmupRuns() : d.warmupRuns(),
                req.naiveMaxNodes() != null ? req.naiveMaxNodes() : d.naiveMaxNodes());
        return service.benchmark(config);
    }

    // ---------------------------------------------------------- utilitare

    private static ApiDtos.AlgorithmRun run(SccChoice choice, SccResult scc) {
        return new ApiDtos.AlgorithmRun(choice.id(), choice.label(), scc.elapsedMillis(), scc.operations());
    }

    private static List<ApiDtos.StopDto> stopDtos(TransitGraph g, SccResult scc) {
        int largest = scc.largestComponent();
        List<ApiDtos.StopDto> out = new ArrayList<>(g.stopCount());
        for (int v = 0; v < g.stopCount(); v++) {
            Stop stop = g.stop(v);
            int c = scc.componentOf()[v];
            out.add(new ApiDtos.StopDto(stop.id(), stop.name(), stop.lat(), stop.lon(), c,
                    scc.components().get(c).length, c == largest, g.routeLabelsAtStop(v)));
        }
        return out;
    }
}
