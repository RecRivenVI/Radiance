package com.radiance.audit;

import java.util.Locale;

/** Observed frame-end state, separate from submitted GPU uniforms or saved player data. */
record SceneProfileState(
        String dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        double playerEyeY,
        double horizon,
        int minimumY,
        int maximumY,
        long gameTime,
        long dayTime,
        float rain,
        float thunder) {
    static final String HEADER =
            "dimension,camera_x,camera_y,camera_z,camera_yaw,camera_pitch,player_eye_y,horizon_y,world_min_y,world_max_y,game_time,day_time,rain,thunder";

    SceneProfileState {
        if (!dimension.matches("[a-z0-9_.:/-]+"))
            throw new IllegalArgumentException("Scene dimension must be a CSV-safe resource key");
    }

    static SceneProfileState absent() {
        return new SceneProfileState(
                "none",
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Float.NaN,
                Float.NaN,
                Double.NaN,
                Double.NaN,
                0,
                0,
                -1,
                -1,
                Float.NaN,
                Float.NaN);
    }

    String csv() {
        return String.format(
                Locale.ROOT,
                "%s,%.9f,%.9f,%.9f,%.5f,%.5f,%.9f,%.9f,%d,%d,%d,%d,%.5f,%.5f",
                dimension,
                x,
                y,
                z,
                yaw,
                pitch,
                playerEyeY,
                horizon,
                minimumY,
                maximumY,
                gameTime,
                dayTime,
                rain,
                thunder);
    }
}
