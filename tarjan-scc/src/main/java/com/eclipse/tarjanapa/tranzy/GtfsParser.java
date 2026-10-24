package com.eclipse.tarjanapa.tranzy;

import com.eclipse.tarjanapa.model.Agency;
import com.eclipse.tarjanapa.model.Route;
import com.eclipse.tarjanapa.model.Stop;
import com.eclipse.tarjanapa.model.StopTime;
import com.eclipse.tarjanapa.model.Trip;
import com.eclipse.tarjanapa.model.Vehicle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * Transformă răspunsurile JSON Tranzy în obiectele modelului.
 * <p>
 * Parserul este tolerant: nu presupune o structură exactă. Se citesc doar
 * câmpurile GTFS standard (route_id, stop_id, ...), cu câteva denumiri
 * alternative, iar câmpurile necunoscute se ignoră. Id-urile pot veni ca număr
 * sau ca text. Înregistrările fără câmpurile obligatorii se sar.
 */
public final class GtfsParser {

    private GtfsParser() {
    }

    /**
     * Lista de înregistrări din răspuns: fie rădăcina e chiar un tablou, fie e
     * un obiect care conține un tablou (de ex. {"data": [...]}).
     */
    public static JsonNode records(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return JsonNodeFactory.instance.arrayNode();
        }
        if (root.isArray()) {
            return root;
        }
        if (root.isObject()) {
            for (String key : List.of("data", "results", "items", "records")) {
                if (root.path(key).isArray()) {
                    return root.get(key);
                }
            }
            Iterator<JsonNode> it = root.elements();
            while (it.hasNext()) {
                JsonNode child = it.next();
                if (child.isArray()) {
                    return child;
                }
            }
        }
        return JsonNodeFactory.instance.arrayNode();
    }

    public static List<Agency> agencies(JsonNode root) {
        return map(root, n -> {
            String id = text(n, "agency_id", "agencyId", "id");
            return id == null ? null : new Agency(id, text(n, "agency_name", "agencyName", "name"),
                    text(n, "agency_url", "url"), text(n, "agency_timezone", "timezone"));
        });
    }

    public static List<Route> routes(JsonNode root) {
        return map(root, n -> {
            String id = text(n, "route_id", "routeId", "id");
            return id == null ? null : new Route(id,
                    text(n, "route_short_name", "routeShortName", "short_name"),
                    text(n, "route_long_name", "routeLongName", "long_name"),
                    integer(n, -1, "route_type", "routeType", "type"));
        });
    }

    public static List<Trip> trips(JsonNode root) {
        return map(root, n -> {
            String id = text(n, "trip_id", "tripId", "id");
            String route = text(n, "route_id", "routeId");
            return id == null || route == null ? null : new Trip(id, route,
                    integer(n, -1, "direction_id", "directionId"),
                    text(n, "trip_headsign", "tripHeadsign", "headsign"));
        });
    }

    public static List<Stop> stops(JsonNode root) {
        return map(root, n -> {
            String id = text(n, "stop_id", "stopId", "id");
            Double lat = decimal(n, "stop_lat", "stopLat", "lat", "latitude");
            Double lon = decimal(n, "stop_lon", "stopLon", "lon", "longitude");
            if (id == null || lat == null || lon == null) {
                return null;
            }
            String name = text(n, "stop_name", "stopName", "name");
            return new Stop(id, name == null ? id : name, lat, lon);
        });
    }

    public static List<StopTime> stopTimes(JsonNode root) {
        return map(root, n -> {
            String trip = text(n, "trip_id", "tripId");
            String stop = text(n, "stop_id", "stopId");
            Integer seq = integerOrNull(n, "stop_sequence", "stopSequence", "sequence");
            return trip == null || stop == null || seq == null ? null : new StopTime(trip, stop, seq);
        });
    }

    public static List<Vehicle> vehicles(JsonNode root) {
        return map(root, n -> {
            String id = text(n, "id", "vehicle_id", "vehicleId");
            Double lat = decimal(n, "latitude", "lat");
            Double lon = decimal(n, "longitude", "lon");
            if (id == null) {
                return null;
            }
            return new Vehicle(id, text(n, "label"), lat == null ? Double.NaN : lat,
                    lon == null ? Double.NaN : lon, text(n, "route_id", "routeId"),
                    text(n, "trip_id", "tripId"), text(n, "timestamp"));
        });
    }

    // ------------------------------------------------------------ utilitare

    private static <T> List<T> map(JsonNode root, Function<JsonNode, T> mapper) {
        List<T> out = new ArrayList<>();
        for (JsonNode n : records(root)) {
            if (n.isObject()) {
                T value = mapper.apply(n);
                if (value != null) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    /** Primul câmp prezent și nenul dintre denumirile date, ca text. */
    static String text(JsonNode n, String... names) {
        for (String name : names) {
            JsonNode v = n.get(name);
            if (v != null && !v.isNull() && !v.isContainerNode()) {
                String s = v.asText().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    static Integer integerOrNull(JsonNode n, String... names) {
        String s = text(n, names);
        if (s == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int integer(JsonNode n, int fallback, String... names) {
        Integer v = integerOrNull(n, names);
        return v == null ? fallback : v;
    }

    static Double decimal(JsonNode n, String... names) {
        String s = text(n, names);
        if (s == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(s);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
