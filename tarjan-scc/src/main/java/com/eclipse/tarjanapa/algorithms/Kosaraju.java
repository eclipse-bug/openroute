package com.eclipse.tarjanapa.algorithms;

import java.util.Arrays;

/**
 * Algoritmul Kosaraju–Sharir, folosit pentru comparație cu Tarjan.
 * <ol>
 *   <li>DFS pe graful G; nodurile se pun pe stiva {@code order} în ordinea
 *       terminării (post-ordine).</li>
 *   <li>Se construiește graful transpus G<sup>T</sup>.</li>
 *   <li>Se scot nodurile de pe {@code order} (descrescător după timpul de
 *       terminare); din fiecare nod încă neatribuit se face DFS în G<sup>T</sup>:
 *       nodurile atinse formează exact o SCC.</li>
 * </ol>
 * Complexitate: O(V + E), dar cu două parcurgeri complete și memorie în plus
 * pentru graful transpus (O(V + E)) — față de Tarjan, care face o singură parcurgere.
 * <p>
 * Ambele DFS-uri sunt scrise iterativ (stivă explicită), ca algoritmul să ruleze
 * pe aceleași grafuri mari ca {@link TarjanIterative}.
 */
public final class Kosaraju implements SccAlgorithm {

    @Override
    public String name() {
        return "Kosaraju";
    }

    @Override
    public int label(IntGraph g, int[] componentOf, OperationCounter ops) {
        int n = g.nodeCount();

        // Pasul 1: DFS în G, ordinea de terminare pe stiva „order”.
        boolean[] visited = new boolean[n];
        int[] order = new int[n];
        int orderSize = 0;
        int[] callStack = new int[n];
        int[] nextEdge = new int[n];

        for (int root = 0; root < n; root++) {
            if (visited[root]) {
                continue;
            }
            visited[root] = true;
            ops.visit();
            int csp = 0;
            callStack[csp++] = root;
            while (csp > 0) {
                int v = callStack[csp - 1];
                int[] neighbors = g.neighbors(v);
                if (nextEdge[v] < neighbors.length) {
                    int w = neighbors[nextEdge[v]++];
                    ops.edge();
                    if (!visited[w]) {
                        visited[w] = true;
                        ops.visit();
                        callStack[csp++] = w;
                    }
                } else {
                    csp--;
                    order[orderSize++] = v;   // v s-a terminat
                    ops.push();
                }
            }
        }

        // Pasul 2: graful transpus (fiecare arc copiat se numără).
        IntGraph gt = g.transpose(ops);

        // Pasul 3: DFS în G^T în ordinea descrescătoare a timpului de terminare.
        Arrays.fill(componentOf, -1);
        int componentCount = 0;
        int[] dfsStack = new int[n];
        while (orderSize > 0) {
            int root = order[--orderSize];
            ops.pop();
            if (componentOf[root] != -1) {
                continue;
            }
            componentOf[root] = componentCount;
            ops.visit();
            int top = 0;
            dfsStack[top++] = root;
            while (top > 0) {
                int v = dfsStack[--top];
                for (int w : gt.neighbors(v)) {
                    ops.edge();
                    if (componentOf[w] == -1) {
                        componentOf[w] = componentCount;
                        ops.visit();
                        dfsStack[top++] = w;
                    }
                }
            }
            componentCount++;
        }
        return componentCount;
    }
}
