package dev.fabricforge.bridge;

import net.fabricmc.loader.api.Version;

import java.util.Objects;

public final class VersionImpl implements Version {
    private final String raw;

    public VersionImpl(String raw) {
        this.raw = raw;
    }

    @Override
    public String getFriendlyString() {
        return raw;
    }

    @Override
    public int compareTo(Version o) {
        String[] a = raw.split("[.+\\-]");
        String[] b = o.getFriendlyString().split("[.+\\-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i] : "0";
            String y = i < b.length ? b[i] : "0";
            int c;
            if (x.matches("\\d+") && y.matches("\\d+")) {
                c = new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y));
            } else {
                c = x.compareTo(y);
            }
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Version v && v.getFriendlyString().equals(raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw);
    }

    @Override
    public String toString() {
        return raw;
    }
}
