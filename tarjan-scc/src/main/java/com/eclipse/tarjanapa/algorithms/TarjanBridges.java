package com.eclipse.tarjanapa.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Punți și puncte de articulație (Tarjan, 1973) pe versiunea NEORIENTATĂ a grafului.
 * <p>
 * Se ignoră sensul arcelor: u — v există dacă u → v sau v → u. În rețeaua de
 * transport, o punte este un segment fără alternativă (dacă dispare, rețeaua se
 * rupe în două), iar un punct de articulație este o stație fără ocolire.
 * <p>
 * DFS cu {@code disc[v]} (timpul descoperirii) și {@code low[v]} (cel mai mic
 * disc accesibil din subarborele lui v folosind cel mult o muchie de întoarcere):
 * <ul>
 *   <li>muchia de arbore (p, v) este punte ⇔ {@code low[v] > disc[p]};</li>
 *   <li>p ne-rădăcină este punct de articulație ⇔ are un copil v cu {@code low[v] >= disc[p]};</li>
 *   <li>rădăcina DFS este punct de articulație ⇔ are cel puțin 2 copii în arborele DFS.</li>
 * </ul>
 * Complexitate: O(V + E). DFS-ul este iterativ (fără StackOverflow).
 */
public final class TarjanBridges {

    /** O punte neorientată; întotdeauna {@code u < v}. */
    public record Bridge(int u, int v) {
    }

    /** Rezultatul: lista punților și lista punctelor de articulație. */
    public record Result(List<Bridge> bridges, List<Integer> articulationPoints) {
    }

    private TarjanBridges() {
    }

    public static Result compute(IntGraph directed) {
        int[][] und = undirectedSimple(directed);
        int n = und.length;

        int[] disc = new int[n];
        int[] low = new int[n];
        int[] parent = new int[n];
        int[] nextEdge = new int[n];
        int[] childCount = new int[n];
        boolean[] isArticulation = new boolean[n];
        int[] callStack = new int[n];
        Arrays.fill(disc, -1);
        Arrays.fill(parent, -1);

        List<Bridge> bridges = new ArrayList<>();
        int time = 0;

        for (int root = 0; root < n; root++) {
            if (disc[root] != -1) {
                continue;
            }
            disc[root] = low[root] = time++;
            int csp = 0;
            callStack[csp++] = root;

            while (csp > 0) {
                int v = callStack[csp - 1];
                if (nextEdge[v] < und[v].length) {
                    int w = und[v][nextEdge[v]++];
                    if (disc[w] == -1) {
                        // Muchie de arbore v — w.
                        parent[w] = v;
                        childCount[v]++;
                        disc[w] = low[w] = time++;
                        callStack[csp++] = w;
                    } else if (w != parent[v]) {
                        // Muchie de întoarcere (graful e simplu, deci părintele apare o singură dată).
                        low[v] = Math.min(low[v], disc[w]);
                    }
                    continue;
                }

                // Terminarea lui v: actualizăm părintele.
                csp--;
                int p = parent[v];
                if (p != -1) {
                    low[p] = Math.min(low[p], low[v]);
                    if (low[v] > disc[p]) {
                        bridges.add(new Bridge(Math.min(p, v), Math.max(p, v)));
                    }
                    if (parent[p] != -1 && low[v] >= disc[p]) {
                        isArticulation[p] = true;
                    }
                }
            }
            if (childCount[root] >= 2) {
                isArticulation[root] = true;
            }
        }

        List<Integer> articulation = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (isArticulation[v]) {
                articulation.add(v);
            }
        }
        return new Result(List.copyOf(bridges), List.copyOf(articulation));
    }

    /**
     * Versiunea neorientată și simplă a grafului: fără auto-bucle și fără muchii
     * duble (A → B și B → A devin o singură muchie A — B).
     */
    static int[][] undirectedSimple(IntGraph g) {
        int n = g.nodeCount();
        IntGraph.Builder b = IntGraph.builder(n);
        for (int u = 0; u < n; u++) {
            for (int v : g.neighbors(u)) {
                if (u != v) {
                    b.addEdge(u, v);
                    b.addEdge(v, u);
                }
            }
        }
        IntGraph both = b.build();
        int[][] result = new int[n][];
        for (int u = 0; u < n; u++) {
            result[u] = Arrays.stream(both.neighbors(u)).distinct().toArray();
        }
        return result;
    }
}
