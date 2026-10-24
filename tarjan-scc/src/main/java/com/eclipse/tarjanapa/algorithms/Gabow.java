package com.eclipse.tarjanapa.algorithms;

import java.util.Arrays;

/**
 * Algoritmul lui Gabow (2000), varianta „path-based” pentru componente tare conexe.
 * <p>
 * Ca și Tarjan, face un singur DFS, dar în loc de {@code lowlink} folosește
 * două stive:
 * <ul>
 *   <li><b>S</b> — nodurile vizitate și încă neatribuite unei componente
 *       (aceeași rol ca stiva lui Tarjan);</li>
 *   <li><b>P</b> — „granițele”: rădăcinile candidate ale componentelor de pe
 *       drumul DFS curent, în ordinea crescătoare a numărului de preordine.</li>
 * </ul>
 * Pași pentru nodul v:
 * <ol>
 *   <li>la descoperire: {@code pre[v] = C++}, v se pune pe S și pe P;</li>
 *   <li>pentru fiecare arc v → w:
 *     <ul>
 *       <li>w nevizitat → DFS din w;</li>
 *       <li>w vizitat, dar încă fără componentă (deci e pe S) → s-a găsit un
 *           ciclu: se scot din P toate nodurile cu {@code pre > pre[w]}
 *           (ele se contopesc în componenta lui w);</li>
 *     </ul></li>
 *   <li>la terminarea lui v: dacă v este în vârful lui P, v este rădăcina unei
 *       componente — se scoate din P, iar de pe S se scot nodurile până la v inclusiv.</li>
 * </ol>
 * Complexitate: O(V + E) timp; fiecare nod intră și iese cel mult o dată din
 * fiecare stivă. Față de Tarjan nu actualizează {@code lowlink} la fiecare arc,
 * dar întreține o a doua stivă.
 * <p>
 * DFS-ul este scris iterativ (stivă de apeluri explicită), ca la {@link TarjanIterative}.
 */
public final class Gabow implements SccAlgorithm {

    @Override
    public String name() {
        return "Gabow";
    }

    @Override
    public int label(IntGraph g, int[] componentOf, OperationCounter ops) {
        int n = g.nodeCount();
        int[] pre = new int[n];          // numărul de preordine; -1 = nevizitat
        int[] stackS = new int[n];
        int sTop = 0;
        int[] stackP = new int[n];
        int pTop = 0;
        int[] callStack = new int[n];
        int[] nextEdge = new int[n];
        Arrays.fill(pre, -1);
        Arrays.fill(componentOf, -1);

        int preCounter = 0;
        int componentCount = 0;

        for (int root = 0; root < n; root++) {
            if (pre[root] != -1) {
                continue;
            }
            // „Apelul” DFS(root).
            pre[root] = preCounter++;
            ops.visit();
            stackS[sTop++] = root;
            ops.push();
            stackP[pTop++] = root;
            ops.push();
            int csp = 0;
            callStack[csp++] = root;

            while (csp > 0) {
                int v = callStack[csp - 1];
                int[] neighbors = g.neighbors(v);

                if (nextEdge[v] < neighbors.length) {
                    int w = neighbors[nextEdge[v]++];
                    ops.edge();
                    if (pre[w] == -1) {
                        // Arc de arbore: „apelăm” DFS(w).
                        pre[w] = preCounter++;
                        ops.visit();
                        stackS[sTop++] = w;
                        ops.push();
                        stackP[pTop++] = w;
                        ops.push();
                        callStack[csp++] = w;
                    } else if (componentOf[w] == -1) {
                        // w e pe S: ciclu — contopim granițele de deasupra lui w.
                        while (pre[stackP[pTop - 1]] > pre[w]) {
                            pTop--;
                            ops.pop();
                        }
                    }
                    continue;
                }

                // Toate arcele lui v au fost examinate: „return” din v.
                csp--;
                if (stackP[pTop - 1] == v) {
                    pTop--;
                    ops.pop();
                    int w;
                    do {
                        w = stackS[--sTop];
                        ops.pop();
                        componentOf[w] = componentCount;
                    } while (w != v);
                    componentCount++;
                }
            }
        }
        return componentCount;
    }
}
