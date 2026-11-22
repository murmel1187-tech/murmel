package de.murmelsvisuals.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny tolerant JSON reader (objects -> Map, arrays -> List, numbers -> Double). Pack authors often leave trailing commas. */
final class Json {
    private final String s;
    private int i = 0;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        try {
            Json j = new Json(text);
            j.ws();
            return j.value();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    static double[] nums(Object o, int n) {
        List<Object> l = arr(o);
        if (l == null || l.size() < n) return null;
        double[] r = new double[n];
        for (int k = 0; k < n; k++) {
            if (!(l.get(k) instanceof Double)) return null;
            r[k] = (Double) l.get(k);
        }
        return r;
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\uFEFF') i++;
            else break;
        }
    }

    private Object value() {
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw new IllegalStateException("bad json");
        return Double.valueOf(s.substring(st, i));
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        while (s.charAt(i) != '}') {
            String k = string(); ws();
            if (s.charAt(i) != ':') throw new IllegalStateException("bad json");
            i++; ws();
            m.put(k, value()); ws();
            if (s.charAt(i) == ',') { i++; ws(); }
        }
        i++;
        return m;
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        while (s.charAt(i) != ']') {
            l.add(value()); ws();
            if (s.charAt(i) == ',') { i++; ws(); }
        }
        i++;
        return l;
    }

    private String string() {
        if (s.charAt(i) != '"') throw new IllegalStateException("bad json");
        StringBuilder b = new StringBuilder();
        i++;
        while (s.charAt(i) != '"') {
            char c = s.charAt(i++);
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else {
                b.append(c);
            }
        }
        i++;
        return b.toString();
    }
}
