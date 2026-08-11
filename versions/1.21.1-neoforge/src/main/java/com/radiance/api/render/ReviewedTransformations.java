package com.radiance.api.render;

import java.security.MessageDigest;
import java.util.*;

/** Exact reviewed additive transformations. Registration never grants blanket mod eligibility. */
public final class ReviewedTransformations {
    private record Evidence(String resource, String digest) {}

    private static final Map<String, Evidence> ENTRIES = new HashMap<>();
    private static final Map<String, Boolean> VERIFIED = new HashMap<>();

    private ReviewedTransformations() {}

    public static synchronized void register(String activity, String resource, String sha256) {
        if (activity.isBlank() || resource.isBlank() || !sha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Incomplete reviewed transformation");
        Evidence evidence = new Evidence(resource, sha256);
        Evidence previous = ENTRIES.putIfAbsent(activity, evidence);
        if (previous != null && !previous.equals(evidence))
            throw new IllegalStateException("Conflicting transformation: " + activity);
    }

    public static synchronized boolean matches(String activity, String digest) {
        Evidence evidence = ENTRIES.get(activity);
        return evidence != null && evidence.digest.equals(digest);
    }

    public static synchronized boolean accepts(String activity) {
        Evidence evidence = ENTRIES.get(activity);
        if (evidence == null) return false;
        return VERIFIED.computeIfAbsent(
                activity,
                key -> {
                    try (var input =
                            ReviewedTransformations.class
                                    .getClassLoader()
                                    .getResourceAsStream(evidence.resource)) {
                        String digest =
                                input == null
                                        ? "missing"
                                        : HexFormat.of()
                                                .formatHex(
                                                        MessageDigest.getInstance("SHA-256")
                                                                .digest(input.readAllBytes()));
                        boolean accepted = matches(key, digest);
                        com.mojang.logging.LogUtils.getLogger()
                                .info(
                                        "TAKEOVER_SOURCE registered-interface={} sha256={} verified={}",
                                        key,
                                        digest,
                                        accepted);
                        return accepted;
                    } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
                        return false;
                    }
                });
    }
}
