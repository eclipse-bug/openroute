package com.eclipse.tarjanapa.model;

/**
 * Rută (GTFS routes.txt).
 *
 * @param type route_type GTFS: 0 tramvai, 3 autobuz, 11 troleibuz etc.; -1 dacă lipsește
 */
public record Route(String id, String shortName, String longName, int type) {

    /** Etichetă scurtă pentru afișare: numărul rutei, altfel id-ul. */
    public String label() {
        return shortName == null || shortName.isBlank() ? id : shortName;
    }

    /** Denumirea în română a tipului de vehicul. */
    public String typeName() {
        return switch (type) {
            case 0 -> "tramvai";
            case 1 -> "metrou";
            case 2 -> "tren";
            case 3 -> "autobuz";
            case 4 -> "feribot";
            case 11 -> "troleibuz";
            default -> "necunoscut";
        };
    }
}
