package de.murmelsvisuals.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class DirPackSource implements PackSource {
    private final Path root;

    public DirPackSource(Path root) {
        this.root = root;
    }

    @Override public String id() { return root.getFileName().toString(); }

    @Override
    public List<String> files() throws IOException {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return List.of();
        try (Stream<Path> s = Files.walk(assets)) {
            return s.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .toList();
        }
    }

    @Override
    public InputStream open(String path) throws IOException {
        return Files.newInputStream(root.resolve(path));
    }

    @Override public void close() {}
}
