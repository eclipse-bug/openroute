package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.IntGraph;
import com.eclipse.tarjanapa.algorithms.RandomGraphGenerator;
import com.eclipse.tarjanapa.algorithms.SccAlgorithm;
import com.eclipse.tarjanapa.algorithms.SccAlgorithms;
import com.eclipse.tarjanapa.algorithms.SccResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark-ul empiric al celor 4 algoritmi SCC.
 * <p>
 * Pentru fiecare graf (rețeaua reală + grafuri aleatoare de mărimi crescătoare):
 * <ol>
 *   <li>câteva rulări de încălzire (warm-up), ca JIT-ul să compileze codul;</li>
 *   <li>{@code runs} rulări măsurate; se raportează media timpului și a operațiilor;</li>
 *   <li>se verifică faptul că toți algoritmii au găsit exact aceeași partiție —
 *       la nepotrivire se aruncă {@link IllegalStateException}.</li>
 * </ol>
 * NaiveScc rulează doar pe grafuri cu cel mult {@code naiveMaxNodes} noduri (O(V·(V+E))).
 * Totul rulează pe un fir cu stivă mare, pentru varianta recursivă a lui Tarjan.
 */
public final class Benchmark {

    private static final Logger log = LoggerFactory.getLogger(Benchmark.class);

    /**
     * @param sizes         mărimile grafurilor aleatoare (număr de noduri)
     * @param avgDegree     gradul mediu de ieșire al grafurilor aleatoare (densitatea)
     * @param seed          sămânța fixă (reproductibilitate)
     * @param runs          rulări măsurate per algoritm și graf
     * @param warmupRuns    rulări de încălzire, nemăsurate
     * @param naiveMaxNodes limita de noduri pentru NaiveScc
     */
    public record Config(List<Integer> sizes, double avgDegree, long seed, int runs, int warmupRuns,
                         int naiveMaxNodes) {

        public Config {
            sizes = List.copyOf(sizes);
            if (runs < 1) {
                throw new IllegalArgumentException("Numărul de rulări trebuie să fie ≥ 1");
            }
            if (warmupRuns < 0) {
                throw new IllegalArgumentException("Numărul de rulări de încălzire trebuie să fie ≥ 0");
            }
        }

        public static Config defaults() {
            return new Config(List.of(100, 1_000, 10_000, 100_000), 2.0, 42L, 5, 2, 10_000);
        }
    }

    /** Un rând din tabelul de rezultate (un algoritm pe un graf). */
    public record Row(String graph, int nodes, int edges, String algorithm, int runs,
                      double avgMillis, double minMillis, double maxMillis, double avgOperations,
                      int sccCount, int largestScc) {
    }

    /** Rezultatul complet; {@code verified} = toți algoritmii au dat aceleași componente pe fiecare graf. */
    public record Result(Config config, List<Row> rows, boolean verified) {
    }

    /** Un graf de test, cu nume pentru tabel. */
    public record NamedGraph(String name, IntGraph graph) {
    }

    public Result run(Config config, IntGraph realNetwork) {
        return SccAlgorithms.withBigStack(() -> runOnCurrentThread(config, realNetwork));
    }

    private Result runOnCurrentThread(Config config, IntGraph realNetwork) {
        List<NamedGraph> graphs = new ArrayList<>();
        if (realNetwork != null) {
            graphs.add(new NamedGraph("retea-reala", realNetwork));
        }
        for (int n : config.sizes()) {
            graphs.add(new NamedGraph("aleator-" + n,
                    RandomGraphGenerator.generate(n, config.avgDegree(), config.seed() + n)));
        }

        List<Row> rows = new ArrayList<>();
        for (NamedGraph ng : graphs) {
            rows.addAll(measureGraph(ng, config));
        }
        return new Result(config, List.copyOf(rows), true);
    }

    /** Măsoară toți algoritmii pe un graf și verifică acordul lor. */
    List<Row> measureGraph(NamedGraph ng, Config config) {
        IntGraph g = ng.graph();
        List<Row> rows = new ArrayList<>();
        SccResult reference = null;

        for (SccAlgorithm alg : SccAlgorithms.all()) {
            if (alg.name().equals("NaiveScc") && g.nodeCount() > config.naiveMaxNodes()) {
                continue;
            }
            for (int i = 0; i < config.warmupRuns(); i++) {
                alg.run(g);
            }
            double sumMs = 0;
            double minMs = Double.MAX_VALUE;
            double maxMs = 0;
            double sumOps = 0;
            SccResult last = null;
            for (int i = 0; i < config.runs(); i++) {
                last = alg.run(g);
                double ms = last.elapsedMillis();
                sumMs += ms;
                minMs = Math.min(minMs, ms);
                maxMs = Math.max(maxMs, ms);
                sumOps += last.operations();
            }

            if (reference == null) {
                reference = last;
            } else if (!reference.samePartitionAs(last)) {
                throw new IllegalStateException("Algoritmii nu sunt de acord pe graful " + ng.name() + ": "
                        + reference.algorithm() + " a găsit " + reference.count() + " SCC, "
                        + last.algorithm() + " a găsit " + last.count() + " SCC.");
            }

            Row row = new Row(ng.name(), g.nodeCount(), g.edgeCount(), alg.name(), config.runs(),
                    sumMs / config.runs(), minMs, maxMs, sumOps / config.runs(), last.count(), last.largestSize());
            log.info("Benchmark {} (V={}, E={}) {}: {} ms, {} operații", ng.name(), g.nodeCount(), g.edgeCount(),
                    alg.name(), String.format("%.3f", row.avgMillis()), (long) row.avgOperations());
            rows.add(row);
        }
        return rows;
    }
}
