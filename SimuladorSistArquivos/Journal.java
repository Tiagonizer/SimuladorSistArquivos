import java.io.*;
import java.nio.file.*;
import java.util.*;

class Journal {
    private final Path journalPath;

    Journal(Path journalPath) throws IOException {
        this.journalPath = journalPath;
        if (!Files.exists(journalPath)) Files.createFile(journalPath);
    }

    static class Entry {
        final long id;
        final String opType;
        final Map<String, String> params;
        final long timestamp;
        final String status; // START or COMMIT

        Entry(long id, String opType, Map<String, String> params, long timestamp, String status) {
            this.id = id; this.opType = opType; this.params = new HashMap<>(params); this.timestamp = timestamp; this.status = status;
        }

        // Serialize entry as a single-line JSON-like format (simple)
        String serialize() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"id\":").append(id).append(",");
            sb.append("\"op\":\"").append(escape(opType)).append("\",");
            sb.append("\"ts\":").append(timestamp).append(",");
            sb.append("\"status\":\"").append(status).append("\",");
            sb.append("\"params\":{");
            boolean first = true;
            for (Map.Entry<String,String> e : params.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append("\"");
                first = false;
            }
            sb.append("}}");
            return sb.toString();
        }

        static Entry deserialize(String line) {
            try {
                Map<String,String> params = new HashMap<>();
                long id = 0;
                String op = "";
                long ts = 0;
                String status = "";
                String inner = line.trim();
                int iId = inner.indexOf("\"id\":");
                if (iId >= 0) {
                    int comma = inner.indexOf(',', iId);
                    String idStr = inner.substring(iId + 5, comma).trim();
                    id = Long.parseLong(idStr);
                }
                int iOp = inner.indexOf("\"op\":\"");
                if (iOp >= 0) {
                    int end = inner.indexOf('"', iOp + 6);
                    op = unescape(inner.substring(iOp + 6, end));
                }
                int iTs = inner.indexOf("\"ts\":");
                if (iTs >= 0) {
                    int comma = inner.indexOf(',', iTs);
                    String tsStr = inner.substring(iTs + 5, comma).trim();
                    ts = Long.parseLong(tsStr);
                }
                int iStatus = inner.indexOf("\"status\":\"");
                if (iStatus >= 0) {
                    int end = inner.indexOf('"', iStatus + 10);
                    status = inner.substring(iStatus + 10, end);
                }
                int iParams = inner.indexOf("\"params\":{");
                if (iParams >= 0) {
                    int start = iParams + 10;
                    String p = inner.substring(start + 1, inner.length() - 2).trim();
                    if (!p.isEmpty()) {
                        String[] pairs = p.split(",");
                        for (String pair : pairs) {
                            int colon = pair.indexOf(':');
                            if (colon < 0) continue;
                            String k = pair.substring(0, colon).trim();
                            if (k.startsWith("\"")) k = k.substring(1);
                            if (k.endsWith("\"")) k = k.substring(0, k.length()-1);
                            String v = pair.substring(colon + 1).trim();
                            if (v.startsWith("\"")) v = v.substring(1);
                            if (v.endsWith("\"")) v = v.substring(0, v.length()-1);
                            params.put(unescape(k), unescape(v));
                        }
                    }
                }
                return new Entry(id, op, params, ts, status);
            } catch (Exception e) {
                return null;
            }
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private static String unescape(String s) {
            if (s == null) return "";
            return s.replace("\\\"", "\"").replace("\\\\", "\\");
        }

        @Override public String toString() { return "Entry{id=" + id + ", op=" + opType + ", status=" + status + ", params=" + params + "}"; }
    }

    // Append an entry as new line
    synchronized void append(Entry entry) throws FSException {
        try (BufferedWriter w = Files.newBufferedWriter(journalPath, StandardOpenOption.APPEND)) {
            w.write(entry.serialize());
            w.write(System.lineSeparator());
            w.flush();
        } catch (IOException e) {
            throw new FSException("Falha ao escrever journal: " + e.getMessage());
        }
    }

    // Return list of START entries that don't have corresponding COMMIT
    synchronized List<Entry> uncommittedStarts() throws IOException {
        List<String> lines = Files.readAllLines(journalPath);
        Map<Long, Entry> starts = new LinkedHashMap<>();
        Set<Long> commits = new HashSet<>();
        for (String line : lines) {
            Journal.Entry e = Journal.Entry.deserialize(line);
            if (e == null) continue;
            if ("START".equals(e.status)) starts.put(e.id, e);
            else if ("COMMIT".equals(e.status)) commits.add(e.id);
        }
        List<Entry> result = new ArrayList<>();
        for (Map.Entry<Long, Entry> kv : starts.entrySet()) {
            if (!commits.contains(kv.getKey())) result.add(kv.getValue());
        }
        return result;
    }

    // Show journal to stdout
    synchronized void showJournal() throws FSException {
        try {
            List<String> lines = Files.readAllLines(journalPath);
            if (lines.isEmpty()) {
                System.out.println("(Journal vazio)");
                return;
            }
            for (String line : lines) System.out.println(line);
        } catch (IOException e) {
            throw new FSException("Falha ao ler journal: " + e.getMessage());
        }
    }

    synchronized void clear() throws FSException {
        try {
            Files.write(journalPath, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new FSException("Falha ao limpar journal: " + e.getMessage());
        }
    }

    // Compute current max id seen to initialize sequence
    synchronized long currentMaxId() {
        try {
            List<String> lines = Files.readAllLines(journalPath);
            long max = 0;
            for (String line : lines) {
                Entry e = Entry.deserialize(line);
                if (e != null && e.id > max) max = e.id;
            }
            return max;
        } catch (IOException e) {
            return 0;
        }
    }
}
