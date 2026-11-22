package de.murmelsvisuals.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipPackSource implements PackSource {
    private final String id;
    private final ZipFile zip;
    private final String prefix;

    public ZipPackSource(Path file) throws IOException {
        this.id = file.getFileName().toString();
        this.zip = new ZipFile(file.toFile());
        this.prefix = detectPrefix(zip);
    }

    /** Manche ZIPs enthalten alles in einem Unterordner – das erkennen wir an der pack.mcmeta. */
    private static String detectPrefix(ZipFile zip) {
        Enumeration<? extends ZipEntry> en = zip.entries();
        String best = null;
        while (en.hasMoreElements()) {
            String n = en.nextElement().getName().replace('\\', '/');
            if (n.equals("pack.mcmeta")) return "";
            if (n.endsWith("/pack.mcmeta") && n.indexOf('/') == n.lastIndexOf('/')) {
                best = n.substring(0, n.lastIndexOf('/') + 1);
            }
        }
        return best == null ? "" : best;
    }

    @Override public String id() { return id; }

    @Override
    public List<String> files() {
        List<String> out = new ArrayList<>();
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            String n = e.getName().replace('\\', '/');
            if (!n.startsWith(prefix)) continue;
            String rel = n.substring(prefix.length());
            if (rel.startsWith("assets/")) out.add(rel);
        }
        return out;
    }

    @Override
    public InputStream open(String path) throws IOException {
        ZipEntry e = zip.getEntry(prefix + path);
        if (e == null) throw new IOException("Not in ZIP: " + path);
        return zip.getInputStream(e);
    }

    @Override public void close() throws IOException { zip.close(); }
}
