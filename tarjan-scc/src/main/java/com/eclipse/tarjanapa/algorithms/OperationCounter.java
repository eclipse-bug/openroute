package com.eclipse.tarjanapa.algorithms;

/**
 * Contor de operații elementare pentru analiza empirică a complexității.
 * <p>
 * Regula de numărare, aplicată identic în toți algoritmii SCC:
 * <ul>
 *   <li>{@link #visit()} — un nod este descoperit/vizitat;</li>
 *   <li>{@link #edge()} — un arc este examinat (inclusiv la construcția grafului transpus);</li>
 *   <li>{@link #push()} / {@link #pop()} — o operație pe structura auxiliară
 *       a algoritmului (stiva S la Tarjan, stiva de ordine la Kosaraju, coada BFS la NaiveScc).</li>
 * </ul>
 * Stiva de apeluri a DFS-ului (recursivă sau simulată explicit) NU se numără,
 * ca TarjanRecursive și TarjanIterative să aibă exact același număr de operații:
 * ele execută aceeași logică, diferă doar mecanismul de revenire din DFS.
 */
public final class OperationCounter {

    private long visits;
    private long edges;
    private long pushes;
    private long pops;

    public void visit() {
        visits++;
    }

    public void edge() {
        edges++;
    }

    public void push() {
        pushes++;
    }

    public void pop() {
        pops++;
    }

    public long visits() {
        return visits;
    }

    public long edges() {
        return edges;
    }

    public long pushes() {
        return pushes;
    }

    public long pops() {
        return pops;
    }

    /** Totalul operațiilor elementare. */
    public long total() {
        return visits + edges + pushes + pops;
    }
}
