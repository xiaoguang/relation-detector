package com.relationdetector.cli;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.Enums.DatabaseType;

/** Discovers built-in and plugin-dir adaptors via Java SPI. */
public final class AdaptorRegistry {
    private final List<DatabaseAdaptor> adaptors;

    private AdaptorRegistry(List<DatabaseAdaptor> adaptors) {
        this.adaptors = adaptors;
    }

    public static AdaptorRegistry load(Path pluginDir) throws Exception {
        List<DatabaseAdaptor> discovered = new ArrayList<>();
        ServiceLoader.load(DatabaseAdaptor.class).forEach(discovered::add);
        if (pluginDir != null && Files.isDirectory(pluginDir)) {
            List<URL> urls = new ArrayList<>();
            try (var stream = Files.list(pluginDir)) {
                stream.filter(path -> path.toString().endsWith(".jar"))
                        .forEach(path -> {
                            try {
                                urls.add(path.toUri().toURL());
                            } catch (Exception ex) {
                                throw new IllegalStateException(ex);
                            }
                        });
            }
            URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), AdaptorRegistry.class.getClassLoader());
            ServiceLoader.load(DatabaseAdaptor.class, loader).forEach(discovered::add);
        }
        return new AdaptorRegistry(discovered);
    }

    public DatabaseAdaptor resolve(DatabaseType databaseType, String adaptorId) {
        List<DatabaseAdaptor> matches = adaptors.stream()
                .filter(adaptor -> adaptor.supportedDatabaseTypes().contains(databaseType))
                .filter(adaptor -> adaptorId == null || adaptorId.isBlank() || adaptor.id().equals(adaptorId))
                .toList();
        if (matches.isEmpty()) {
            throw new AdaptorException("no adaptor found for database type " + databaseType);
        }
        if (matches.size() > 1) {
            throw new AdaptorException("multiple adaptors found for " + databaseType + "; set database.adaptorId");
        }
        return matches.get(0);
    }

    static final class AdaptorException extends RuntimeException {
        AdaptorException(String message) {
            super(message);
        }
    }
}
