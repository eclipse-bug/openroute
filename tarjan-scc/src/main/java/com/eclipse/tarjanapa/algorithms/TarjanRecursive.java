package com.eclipse.tarjanapa.algorithms;

import java.util.Arrays;

/**
 * Algoritmul lui Tarjan (1972), varianta recursivă clasică.
 * <p>
 * Ideea: un singur DFS. Fiecare nod primește un {@code index} (ordinea
 * descoperirii) și un {@code lowlink} = cel mai mic index accesibil din
 * subarborele DFS al nodului folosind cel mult un arc de întoarcere către un
 * nod aflat încă pe stiva S. Un nod v este rădăcina unei SCC exact când
 * {@code lowlink[v] == index[v]}; atunci componenta se scoate de pe stivă
 * până la v inclusiv.
 * <p>
 * Complexitate: O(V + E) timp — fiecare nod e vizitat o dată, fiecare arc
 * examinat o dată, fiecare nod intră și iese o dată de pe stivă; O(V) memorie.
 * <p>
 * Limitare: adâncimea recursivității poate ajunge la V. Pe un lanț de
 * 1 000 000 de noduri apare {@link StackOverflowError} cu stiva implicită a
 * JVM-ului — de aceea există {@link TarjanIterative}.
 * <p>
 * Observație: componentele sunt emise în ordine topologică inversă a
 * grafului condensat (prima componentă găsită nu are arce spre alte componente noi).
 */
public final class TarjanRecursive implements SccAlgorithm {

    @Override
    public String name() {
        return "TarjanRecursive";
    }

    @Override
    public int label(IntGraph g, int[] componentOf, OperationCounter ops) {
        return new State(g, componentOf, ops).runAll();
    }

    /** Starea unei rulări (separată, ca instanța algoritmului să fie reutilizabilă și thread-safe). */
    private static final class State {
        private final IntGraph g;
        private final int[] componentOf;
        private final OperationCounter ops;

        private final int[] index;     // ordinea descoperirii; -1 = nevizitat
        private final int[] lowlink;
        private final boolean[] onStack;
        private final int[] stack;     // stiva S a lui Tarjan
        private int sp;                // vârful stivei S
        private int nextIndex;
        private int componentCount;

        State(IntGraph g, int[] componentOf, OperationCounter ops) {
            int n = g.nodeCount();
            this.g = g;
            this.componentOf = componentOf;
            this.ops = ops;
            this.index = new int[n];
            this.lowlink = new int[n];
            this.onStack = new boolean[n];
            this.stack = new int[n];
            Arrays.fill(index, -1);
        }

        int runAll() {
            for (int v = 0; v < g.nodeCount(); v++) {
                if (index[v] == -1) {
                    strongConnect(v);
                }
            }
            return componentCount;
        }

        private void strongConnect(int v) {
            // 1. Descoperirea lui v.
            index[v] = nextIndex;
            lowlink[v] = nextIndex;
            nextIndex++;
            ops.visit();
            stack[sp++] = v;
            onStack[v] = true;
            ops.push();

            // 2. Examinarea arcelor v → w.
            for (int w : g.neighbors(v)) {
                ops.edge();
                if (index[w] == -1) {
                    // Arc de arbore: coborâm, apoi propagăm lowlink-ul copilului.
                    strongConnect(w);
                    lowlink[v] = Math.min(lowlink[v], lowlink[w]);
                } else if (onStack[w]) {
                    // Arc de întoarcere/transversal în SCC-ul curent.
                    lowlink[v] = Math.min(lowlink[v], index[w]);
                }
                // Altfel w aparține unei SCC deja finalizate: arcul se ignoră.
            }

            // 3. v este rădăcină de SCC: scoatem componenta de pe stivă.
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
        }
    }
}
