package com.eclipse.tarjanapa.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Export CSV în {@code data/results/}, pentru import în Word/Excel.
 * <p>
 * Separator virgulă, zecimale cu punct, codificare UTF-8 cu BOM (ca Excel să
 * afișeze corect diacriticele). Fiecare export scrie un fișier cu marcaj de
 * timp și o copie „-latest”, ca graficele din raport să poată fi legate de un nume fix.
 */
public final class CsvExporter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path dir;

    public CsvExporter(Path dir) {
        this.dir = dir;
    }

    public Path benchmark(Benchmark.Result result) {
        List<String> lines = new ArrayList<>();
        lines.add("graf,noduri,arce,algoritm,rulari,timp_mediu_ms,timp_min_ms,timp_max_ms,operatii_medii,nr_scc,scc_maxima");
        for (Benchmark.Row r : result.rows()) {
            lines.add(String.join(",", r.graph(), i(r.nodes()), i(r.edges()), r.algorithm(), i(r.runs()),
                    d(r.avgMillis()), d(r.minMillis()), d(r.maxMillis()), d(r.avgOperations()),
                    i(r.sccCount()), i(r.largestScc())));
        }
        return write("benchmark", lines);
    }

    public Path criticalRoutes(List<CriticalRoutesAnalysis.RouteImpact> impacts) {
        List<String> lines = new ArrayList<>();
        lines.add("loc,ruta_id,ruta,tip,denumire,statii_pierdute,nr_scc_dupa,scc_maxima_dupa");
        for (CriticalRoutesAnalysis.RouteImpact x : impacts) {
            lines.add(String.join(",", i(x.rank()), q(x.routeId()), q(x.label()), q(x.type()), q(x.longName()),
                    i(x.lostStops()), i(x.sccAfter()), i(x.largestAfter())));
        }
        return write("rute-critice", lines);
    }

    public Path smallComponents(NetworkState state) {
        List<String> lines = new ArrayList<>();
        lines.add("statie_id,statie,lat,lon,scc,marime_scc,rute");
        for (StopInfo s : state.smallComponentStops()) {
            lines.add(String.join(",", q(s.id()), q(s.name()), d(s.lat()), d(s.lon()), i(s.component()),
                    i(s.componentSize()), q(String.join(" ", s.routes()))));
        }
        return write("scc-mici", lines);
    }

    public Path bridges(StructureAnalysis.BridgeReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("tip,statie_a,statie_b,rute");
        for (StructureAnalysis.BridgeInfo b : report.bridges()) {
            lines.add(String.join(",", "punte", q(b.fromName()), q(b.toName()), q(String.join(" ", b.routes()))));
        }
        for (StopInfo s : report.articulationPoints()) {
            lines.add(String.join(",", "articulatie", q(s.name()), "", q(String.join(" ", s.routes()))));
        }
        return write("punti", lines);
    }

    private Path write(String baseName, List<String> lines) {
        try {
            Files.createDirectories(dir);
            String content = "﻿" + String.join("\n", lines) + "\n";
            Path stamped = dir.resolve(baseName + "-" + LocalDateTime.now().format(STAMP) + ".csv");
            Files.writeString(stamped, content, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(baseName + "-latest.csv"), content, StandardCharsets.UTF_8);
            return stamped;
        } catch (IOException e) {
            throw new UncheckedIOException("Nu pot scrie CSV-ul în " + dir, e);
        }
    }

    private static String i(long v) {
        return Long.toString(v);
    }

    private static String d(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    /** Câmp text între ghilimele (ghilimelele interne se dublează). */
    private static String q(String s) {
        return s == null ? "" : "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
