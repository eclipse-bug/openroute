package com.eclipse.tarjanapa.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Graful condensat: fiecare SCC devine un singur nod, iar între componentele
 * A ≠ B există arc dacă în graful original există un arc u → v cu u ∈ A, v ∈ B.
 * Graful condensat este întotdeauna un DAG (un ciclu între componente le-ar
 * contopi într-o singură SCC).
 * <p>
 * Surse = componente cu grad de intrare 0 (nu se poate ajunge în ele din restul
 * rețelei); destinații = componente cu grad de ieșire 0 (din ele nu se poate pleca).
 * O componentă izolată este simultan sursă și destinație.
 * <p>
 * Construcție: O(V + E); arcele duplicate dintre aceleași componente se elimină.
 */
public final class Condensation {

    private final IntGraph dag;
    private final int[] inDegree;
    private final int[] outDegree;
    private final List<Integer> sources;
    private final List<Integer> sinks;

    private Condensation(IntGraph dag, int[] inDegree, int[] outDegree,
                         List<Integer> sources, List<Integer> sinks) {
        this.dag = dag;
        this.inDegree = inDegree;
        this.outDegree = outDegree;
        this.sources = sources;
        this.sinks = sinks;
    }

    public static Condensation of(IntGraph g, SccResult scc) {
        int k = scc.count();
        int[] comp = scc.componentOf();
        IntGraph.Builder builder = IntGraph.builder(k);

        // lastSeen[cv] == cu ⇔ arcul cu → cv a fost deja adăugat din componenta cu.
        // Nodurile se parcurg grupat pe componente, deci marcajul e suficient.
        int[] lastSeen = new int[k];
        Arrays.fill(lastSeen, -1);
        int[] inDeg = new int[k];
        int[] outDeg = new int[k];

        for (int cu = 0; cu < k; cu++) {
            for (int u : scc.components().get(cu)) {
                for (int v : g.neighbors(u)) {
                    int cv = comp[v];
                    if (cv != cu && lastSeen[cv] != cu) {
                        lastSeen[cv] = cu;
                        builder.addEdge(cu, cv);
                        outDeg[cu]++;
                        inDeg[cv]++;
                    }
                }
            }
        }

        List<Integer> sources = new ArrayList<>();
        List<Integer> sinks = new ArrayList<>();
        for (int c = 0; c < k; c++) {
            if (inDeg[c] == 0) {
                sources.add(c);
            }
            if (outDeg[c] == 0) {
                sinks.add(c);
            }
        }
        return new Condensation(builder.build(), inDeg, outDeg, List.copyOf(sources), List.copyOf(sinks));
    }

    /** DAG-ul componentelor (nodul c = componenta c din {@link SccResult}). */
    public IntGraph dag() {
        return dag;
    }

    public int componentCount() {
        return dag.nodeCount();
    }

    public int inDegree(int component) {
        return inDegree[component];
    }

    public int outDegree(int component) {
        return outDegree[component];
    }

    public List<Integer> sources() {
        return sources;
    }

    public List<Integer> sinks() {
        return sinks;
    }

    /**
     * Numărul minim de arce care trebuie adăugate pentru ca graful să devină
     * tare conex (Eswaran–Tarjan, 1976): 0 dacă există o singură SCC (sau
     * graful e gol), altfel max(nr. surse, nr. destinații) din graful condensat.
     */
    public int minEdgesToStronglyConnect() {
        if (componentCount() <= 1) {
            return 0;
        }
        return Math.max(sources.size(), sinks.size());
    }
}
