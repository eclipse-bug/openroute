package com.eclipse.tarjanapa.algorithms;

import java.util.Arrays;

/**
 * Algoritmul lui Tarjan, varianta iterativă.
 * <p>
 * Logica este identică cu {@link TarjanRecursive}; recursivitatea este
 * înlocuită de o stivă explicită de apeluri:
 * <ul>
 *   <li>{@code callStack} — nodurile aflate „în execuție” în DFS (echivalentul cadrelor de apel);</li>
 *   <li>{@code nextEdge[v]} — poziția următorului arc de examinat din lista lui v
 *       (echivalentul variabilei de buclă locale din varianta recursivă).</li>
 * </ul>
 * Când toate arcele lui v au fost examinate, v se scoate din {@code callStack}
 * („return”), iar părintele preia {@code lowlink[v]} — exact ce face linia de
 * după apelul recursiv.
 * <p>
 * Complexitate: aceeași, O(V + E) timp și O(V) memorie, dar memoria stivei de
 * apeluri e pe heap, deci nu există {@link StackOverflowError} la grafuri adânci.
 */
public final class TarjanIterative implements SccAlgorithm {

    @Override
    public String name() {
        return "TarjanIterative";
    }

    @Override
    public int label(IntGraph g, int[] componentOf, OperationCounter ops) {
        int n = g.nodeCount();
        int[] index = new int[n];
        int[] lowlink = new int[n];
        boolean[] onStack = new boolean[n];
        int[] stack = new int[n];       // stiva S a lui Tarjan
        int sp = 0;
        int[] callStack = new int[n];   // stiva de apeluri simulată
        int csp = 0;
        int[] nextEdge = new int[n];
        Arrays.fill(index, -1);

        int nextIndex = 0;
        int componentCount = 0;

        for (int root = 0; root < n; root++) {
            if (index[root] != -1) {
                continue;
            }
            // „Apelul” strongConnect(root).
            index[root] = lowlink[root] = nextIndex++;
            ops.visit();
            stack[sp++] = root;
            onStack[root] = true;
            ops.push();
            callStack[csp++] = root;

            while (csp > 0) {
                int v = callStack[csp - 1];
                int[] neighbors = g.neighbors(v);

                if (nextEdge[v] < neighbors.length) {
                    int w = neighbors[nextEdge[v]++];
                    ops.edge();
                    if (index[w] == -1) {
                        // Arc de arbore: „apelăm” strongConnect(w).
                        index[w] = lowlink[w] = nextIndex++;
                        ops.visit();
                        stack[sp++] = w;
                        onStack[w] = true;
                        ops.push();
                        callStack[csp++] = w;
                    } else if (onStack[w]) {
                        lowlink[v] = Math.min(lowlink[v], index[w]);
                    }
                    continue;
                }

                // Toate arcele lui v au fost examinate: „return” din v.
                csp--;
                if (lowlink[v] == index[v]) {
                    int w;
                    do {
                        w = stack[--sp];
                        onStack[w] = false;
                        ops.pop();
                        componentOf[w] = componentCount;
                    } while (w != v);
                    componentCount++;
                }
                if (csp > 0) {
                    // Părintele preia lowlink-ul copilului (linia de după apelul recursiv).
                    int parent = callStack[csp - 1];
                    lowlink[parent] = Math.min(lowlink[parent], lowlink[v]);
                }
            }
        }
        return componentCount;
    }
}
