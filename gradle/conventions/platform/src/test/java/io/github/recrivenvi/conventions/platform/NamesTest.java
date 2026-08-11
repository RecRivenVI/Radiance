package io.github.recrivenvi.conventions.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.recrivenvi.conventions.configuration.SideRequirement;
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
    void sidesExpandToLoaderMetadata() {
        SideRequirement required = SideRequirement.REQUIRED;
        SideRequirement unused = SideRequirement.UNUSED;
        SideRequirement optional = SideRequirement.OPTIONAL;
        assertEquals("client", Metadata.fabricEnvironment(unused));
        assertEquals("*", Metadata.fabricEnvironment(optional));
        assertEquals("*", Metadata.fabricEnvironment(required));
        assertEquals("MATCH_VERSION", Metadata.displayTest(required, required));
        assertEquals("IGNORE_ALL_VERSION", Metadata.displayTest(required, unused));
        assertEquals("IGNORE_ALL_VERSION", Metadata.displayTest(required, optional));
        assertEquals("IGNORE_SERVER_VERSION", Metadata.displayTest(unused, required));
        assertEquals("IGNORE_SERVER_VERSION", Metadata.displayTest(optional, required));
        assertEquals("IGNORE_SERVER_VERSION", Metadata.displayTest(optional, optional));
    }

    @Test
    void metadataEscapesStringContent() {
        assertEquals("Raven's \\\"Mod\\\" C:\\\\", Metadata.escape("Raven's \"Mod\" C:\\"));
    }
}
