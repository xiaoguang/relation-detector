package com.relationdetector.cli;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.AdaptorApiVersion;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * Java SPI adaptor 发现与选择器。
 *
 * <p>CN: CLI 通过它加载内置 adaptor 和 plugin-dir 中的外部 adaptor jar，然后按
 * database.type / adaptorId 选择唯一 adaptor。
 *
 * <p>EN: Java SPI adaptor discovery and selection. CLI uses it to load built-in
 * adaptors plus external adaptor jars from plugin-dir, then selects one adaptor
 * by database.type and optional adaptorId.
 */
public final class AdaptorRegistry {
    private final List<DatabaseAdaptor> adaptors;

    private AdaptorRegistry(List<DatabaseAdaptor> adaptors) {
        this.adaptors = adaptors;
    }

    /**
     * 加载内置 SPI adaptor，并可选加载 plugin-dir jar。
     *
     * <p>EN: Loads built-in SPI adaptors and optionally adaptor jars from plugin-dir.
     */
    public static AdaptorRegistry load(Path pluginDir) throws Exception {
        List<DatabaseAdaptor> discovered = new ArrayList<>();
        ServiceLoader.load(DatabaseAdaptor.class).forEach(adaptor -> addValidated(discovered, adaptor));
        if (pluginDir != null && Files.isDirectory(pluginDir)) {
            List<URL> urls = new ArrayList<>();
            try (var stream = Files.list(pluginDir)) {
                stream.filter(path -> path.toString().endsWith(".jar"))
                        .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                        .forEach(path -> {
                            try {
                                urls.add(path.toUri().toURL());
                            } catch (Exception ex) {
                                throw new IllegalStateException(ex);
                            }
                        });
            }
            URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), AdaptorRegistry.class.getClassLoader());
            ServiceLoader.load(DatabaseAdaptor.class, loader).forEach(adaptor -> addValidated(discovered, adaptor));
        }
        discovered.sort(Comparator.comparing(DatabaseAdaptor::id));
        return new AdaptorRegistry(discovered);
    }

    private static void addValidated(List<DatabaseAdaptor> discovered, DatabaseAdaptor adaptor) {
        requireCurrentApi(adaptor);
        discovered.add(adaptor);
    }

    static void requireCurrentApi(DatabaseAdaptor adaptor) {
        int actual = adaptor.spiVersion();
        if (actual != AdaptorApiVersion.CURRENT) {
            throw new AdaptorException("adaptor SPI version mismatch: plugin=" + adaptor.id()
                    + ", actual=" + actual
                    + ", required=" + AdaptorApiVersion.CURRENT
                    + "; recompile the plugin against the current relation-detector contracts");
        }
    }

    /**
     * 根据数据库类型和可选 adaptorId 选择唯一 adaptor。
     *
     * <p>EN: Resolves a single adaptor by database type and optional adaptorId.
     */
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
