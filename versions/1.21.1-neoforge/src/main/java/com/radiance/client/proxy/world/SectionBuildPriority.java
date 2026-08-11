package com.radiance.client.proxy.world;

/** Distance to the actual section bounds, in sixteen-block bands shared with native admission. */
record SectionBuildPriority(double distanceSquared) implements Comparable<SectionBuildPriority> {
    static SectionBuildPriority at(double x, double y, double z, double cx, double cy, double cz) {
        return at(x, y, z, 8, 8, 8, cx, cy, cz);
    }

    static SectionBuildPriority at(
            double x,
            double y,
            double z,
            double hx,
            double hy,
            double hz,
            double cx,
            double cy,
            double cz) {
        double dx = Math.max(0, Math.abs(x - cx) - hx) / 16;
        double dy = Math.max(0, Math.abs(y - cy) - hy) / 16;
        double dz = Math.max(0, Math.abs(z - cz) - hz) / 16;
        return new SectionBuildPriority(dx * dx + dy * dy + dz * dz);
    }

    double ring() {
        return Math.floor(Math.sqrt(distanceSquared));
    }

    @Override
    public int compareTo(SectionBuildPriority other) {
        return Double.compare(distanceSquared, other.distanceSquared);
    }
}
