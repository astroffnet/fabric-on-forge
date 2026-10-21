package dev.fabricforge.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Downloads (once, cached) the two mapping sources and joins them. Needs internet on first launch only. */
public final class MappingProvider {
    static final String INTERMEDIARY_URL =
        "https://maven.fabricmc.net/net/fabricmc/intermediary/1.20.1/intermediary-1.20.1-v2.jar";
    static final String MCP_URL =
        "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412.zip";

    private MappingProvider() {}

    public static MappingData load(Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);
        Path tiny = cacheDir.resolve("intermediary-1.20.1.tiny");
        Path tsrg = cacheDir.resolve("joined-1.20.1.tsrg");
        if (!Files.exists(tiny)) {
            downloadEntry(INTERMEDIARY_URL, "mappings/mappings.tiny", tiny);
        }
        if (!Files.exists(tsrg)) {
            downloadEntry(MCP_URL, "config/joined.tsrg", tsrg);
        }
        try (BufferedReader a = Files.newBufferedReader(tiny, StandardCharsets.UTF_8);
             BufferedReader b = Files.newBufferedReader(tsrg, StandardCharsets.UTF_8)) {
            return MappingData.build(a, b);
        }
    }

    private static void downloadEntry(String url, String entry, Path dest) throws IOException {
        Path tmp = Files.createTempFile("fabric-on-forge", ".zip");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "fabric-on-forge/0.1");
            try (InputStream in = c.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                ZipEntry e = zip.getEntry(entry);
                if (e == null) {
                    throw new IOException(entry + " not found in " + url);
                }
                Path part = dest.resolveSibling(dest.getFileName() + ".part");
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
