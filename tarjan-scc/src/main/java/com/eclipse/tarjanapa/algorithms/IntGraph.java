package com.eclipse.tarjanapa.algorithms;

import java.util.Arrays;

/**
 * Graf orientat reprezentat prin liste de adiacență cu indici întregi.
 * <p>
 * Nodurile sunt numerele 0..n-1, iar {@code adj[u]} conține vecinii lui u
 * (arcele u → v). Reprezentarea e compactă (doar tablouri de int), ceea ce
 * contează la grafurile de benchmark cu 100 000 de noduri.
 * <p>
 * Clasa este imutabilă: algoritmii doar citesc din ea.
 */
public final class IntGraph {

    private final int n;
    private final int[][] adj;
    private final int edgeCount;

    private IntGraph(int[][] adj) {
        this.n = adj.length;
        this.adj = adj;
        int m = 0;
        for (int[] list : adj) {
            m += list.length;
        }
        this.edgeCount = m;
    }

    /** Numărul de noduri (V). */
    public int nodeCount() {
        return n;
    }

    /** Numărul de arce (E). */
    public int edgeCount() {
        return edgeCount;
    }

    /** Vecinii lui u. Tabloul NU trebuie modificat de apelant. */
    public int[] neighbors(int u) {
        return adj[u];
    }

    /** Graful transpus (toate arcele inversate) — folosit de Kosaraju și NaiveScc. */
    public IntGraph transpose() {
        return transpose(null);
    }

    /**
     * Graful transpus; dacă {@code ops} nu e null, fiecare arc copiat este
     * numărat ca o examinare de arc (costul construcției face parte din algoritm).
     */
    public IntGraph transpose(OperationCounter ops) {
        int[] inDegree = new int[n];
        for (int u = 0; u < n; u++) {
            for (int v : adj[u]) {
                inDegree[v]++;
            }
        }
        int[][] rev = new int[n][];
        for (int v = 0; v < n; v++) {
            rev[v] = new int[inDegree[v]];
        }
        int[] fill = new int[n];
        for (int u = 0; u < n; u++) {
            for (int v : adj[u]) {
                rev[v][fill[v]++] = u;
                if (ops != null) {
                    ops.edge();
                }
            }
        }
        return new IntGraph(rev);
    }

    /** Creează un graf direct din liste de adiacență (sunt copiate defensiv). */
    public static IntGraph of(int[][] adjacency) {
        int[][] copy = new int[adjacency.length][];
        for (int i = 0; i < adjacency.length; i++) {
            copy[i] = Arrays.copyOf(adjacency[i], adjacency[i].length);
        }
        return new IntGraph(copy);
    }

    public static Builder builder(int n) {
        return new Builder(n);
    }

    /** Constructor incremental: se adaugă arce, apoi {@link #build()}. */
    public static final class Builder {
        private final int[][] lists;
        private final int[] sizes;

        private Builder(int n) {
            if (n < 0) {
                throw new IllegalArgumentException("Număr negativ de noduri: " + n);
            }
            this.lists = new int[n][];
            this.sizes = new int[n];
            for (int i = 0; i < n; i++) {
                lists[i] = new int[2];
            }
        }

        /** Adaugă arcul u → v (se permit auto-bucle și arce multiple). */
        public Builder addEdge(int u, int v) {
            if (u < 0 || u >= lists.length || v < 0 || v >= lists.length) {
                throw new IndexOutOfBoundsException("Arc invalid " + u + " → " + v);
            }
            if (sizes[u] == lists[u].length) {
                lists[u] = Arrays.copyOf(lists[u], lists[u].length * 2);
            }
            lists[u][sizes[u]++] = v;
            return this;
        }

        public IntGraph build() {
            int[][] adj = new int[lists.length][];
            for (int i = 0; i < lists.length; i++) {
                adj[i] = Arrays.copyOf(lists[i], sizes[i]);
            }
            return new IntGraph(adj);
        }
    }
}
