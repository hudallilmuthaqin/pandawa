package id.pbpandawa.report;

import java.util.List;

/** Minimal RFC 4180 CSV builder (UTF-8 with BOM so Excel opens it correctly). */
final class CsvWriter {

    private final StringBuilder sb = new StringBuilder("﻿");

    CsvWriter row(Object... values) {
        return row(List.of(values));
    }

    CsvWriter row(List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values.get(i)));
        }
        sb.append("\r\n");
        return this;
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        // Neutralise spreadsheet formula injection.
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
