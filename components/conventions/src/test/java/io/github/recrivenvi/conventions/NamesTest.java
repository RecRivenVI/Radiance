package io.github.recrivenvi.conventions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NamesTest {
    @Test
    void derivesRunAndTaskNames() {
        assertEquals("client", Names.run("client"));
        assertEquals("clientMultiplayer", Names.run("client-multiplayer"));
        assertEquals("runClientMultiplayer", Names.task("run", Names.run("client-multiplayer")));
        assertEquals("prepareServer", Names.task("prepare", Names.run("server")));
        assertEquals("examplemod_probe", Names.probe("examplemod"));
        assertEquals(
                "validationSmokeLaunchClientMultiplayer",
                Names.validation("SmokeLaunch", "client-multiplayer"));
        assertEquals(
                "runValidationSmokeLaunchServer",
                Names.task("run", Names.validation("SmokeLaunch", "server")));
    }

    @Test
    void metadataEscapesStringContent() {
        assertEquals("Raven's \\\"Mod\\\" C:\\\\", Metadata.escape("Raven's \"Mod\" C:\\"));
    }
}
