package com.eclipse.tarjanapa.algorithms;

import java.util.Arrays;

/**
 * Algoritm naiv pentru SCC, folosit ca referință în benchmark.
 * <p>
 * Pentru fiecare nod v încă neatribuit unei componente:
 * <ol>
 *   <li>BFS în G din v → mulțimea nodurilor accesibile din v (înainte);</li>
 *   <li>BFS în G<sup>T</sup> din v → mulțimea nodurilor din care v e accesibil (înapoi);</li>
 *   <li>SCC(v) = intersecția celor două mulțimi.</li>
 * </ol>
 * Corectitudine: u și v sunt în aceeași SCC ⇔ u e accesibil din v și v din u.
 * <p>
 * Complexitate: O(V · (V + E)) în cazul cel mai defavorabil — de exemplu un
 * DAG, unde fiecare nod e propria componentă și se fac V perechi de BFS-uri.
 * Omiterea nodurilor deja atribuite nu schimbă ordinul de mărime, dar evită
 * munca redundantă în interiorul componentelor mari.
 */
public final class NaiveScc implements SccAlgorithm {

    @Override
    public String name() {
        return "NaiveScc";
    }

    @Override
    public int label(IntGraph g, int[] componentOf, OperationCounter ops) {
        int n = g.nodeCount();
        IntGraph gt = g.transpose(ops);
        Arrays.fill(componentOf, -1);

        // Marcaje cu „ștampilă”: forwardMark[u] == stamp ⇔ u atins în BFS-ul curent.
        // Evită reinițializarea tablourilor (O(V)) la fiecare nod.
        int[] forwardMark = new int[n];
        int[] backwardMark = new int[n];
        int[] queue = new int[n];
        int stamp = 0;
        int componentCount = 0;

        for (int v = 0; v < n; v++) {
            if (componentOf[v] != -1) {
                continue;
            }
            stamp++;
            bfs(g, v, forwardMark, stamp, queue, ops);
            bfs(gt, v, backwardMark, stamp, queue, ops);

            // Intersecția: noduri atinse în ambele direcții.
            for (int u = 0; u < n; u++) {
                if (forwardMark[u] == stamp && backwardMark[u] == stamp) {
                    componentOf[u] = componentCount;
                }
            }
            componentCount++;
        }
        return componentCount;
    }

    private static void bfs(IntGraph g, int source, int[] mark, int stamp, int[] queue,
                            OperationCounter ops) {
        int head = 0;
        int tail = 0;
        mark[source] = stamp;
        ops.visit();
        queue[tail++] = source;
        ops.push();
        while (head < tail) {
            int u = queue[head++];
            ops.pop();
            for (int w : g.neighbors(u)) {
                ops.edge();
                if (mark[w] != stamp) {
                    mark[w] = stamp;
                    ops.visit();
                    queue[tail++] = w;
                    ops.push();
                }
            }
        }
    }
}
