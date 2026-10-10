package io.github.recrivenvi.conventions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceModsTest {
    @TempDir Path temp;
    private Path instance;
    private Path mods;
    private Path inputs;

    @BeforeEach
    void directories() throws IOException {
        instance = Files.createDirectories(temp.resolve("instance"));
        mods = instance.resolve("mods");
        inputs = Files.createDirectories(temp.resolve("inputs"));
    }

    private Path input(String directory, String name, String content) throws IOException {
        Path file = Files.createDirectories(inputs.resolve(directory)).resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static Map<String, Path> map(Object... pairs) {
        Map<String, Path> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], (Path) pairs[i + 1]);
        return map;
    }

    private List<String> sync(Map<String, Path> map) throws IOException {
        return InstanceMods.sync(instance, map);
    }

    private String message(Map<String, Path> map) {
        return assertThrows(GradleException.class, () -> sync(map)).getMessage();
    }

    private List<String> manifest() throws IOException {
        return Files.readAllLines(instance.resolve(InstanceMods.MANIFEST));
    }

    @Test
    void copiesInputsAndRecordsThem() throws IOException {
        Path skins = input("a", "skins-1.0.jar", "skins");
        Path maps = input("b", "maps 2.jar", "maps");
        assertEquals(List.of(), sync(map("skins", skins, "maps", maps)));
        assertEquals("skins", Files.readString(mods.resolve("skins-1.0.jar")));
        assertEquals("maps", Files.readString(mods.resolve("maps 2.jar")));
        assertEquals(
                List.of(
                        InstanceMods.sha256(maps) + "  maps 2.jar",
                        InstanceMods.sha256(skins) + "  skins-1.0.jar"),
                manifest());
        try (var entries = Files.list(mods)) {
            assertEquals(2, entries.count(), "没有残留临时文件");
        }
    }

    private List<String> files() throws IOException {
        try (var entries = Files.list(mods)) {
            return entries.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    // 在区分大小写的文件系统上（例如持续集成的 Linux），旧副本与新文件是两个文件。
    @Test
    void caseOnlyRenameReplacesTheCopy() throws IOException {
        sync(map("skins", input("a", "Skins.jar", "skins")));
        Path renamed = input("b", "skins.jar", "skins 2");
        assertEquals(List.of(), sync(map("skins", renamed)));
        assertEquals(List.of("skins.jar"), files());
        assertEquals("skins 2", Files.readString(mods.resolve("skins.jar")));
        assertEquals(List.of(InstanceMods.sha256(renamed) + "  skins.jar"), manifest());
    }

    @Test
    void caseOnlyRenameWithTheSameContentUsesTheNewName() throws IOException {
        sync(map("skins", input("a", "Skins.jar", "skins")));
        sync(map("skins", input("b", "skins.jar", "skins")));
        assertEquals(List.of("skins.jar"), files());
    }

    @Test
    void caseOnlyRenameKeepsAModifiedCopy() throws IOException {
        sync(map("skins", input("a", "Skins.jar", "skins")));
        Files.writeString(mods.resolve("Skins.jar"), "edited");
        assertTrue(message(map("skins", input("b", "skins.jar", "skins 2"))).contains("在放入后被改动过"));
        assertEquals(1, files().size());
        assertEquals("edited", Files.readString(mods.resolve(files().getFirst())));
    }

    @Test
    void caseOnlyRenameKeepsADirectoryInThePlaceOfTheOldCopy() throws IOException {
        sync(map("skins", input("a", "Skins.jar", "skins")));
        Files.delete(mods.resolve("Skins.jar"));
        Files.createDirectory(mods.resolve("Skins.jar"));
        assertTrue(message(map("skins", input("b", "skins.jar", "skins 2"))).contains("不是文件"));
        assertTrue(Files.isDirectory(mods.resolve("Skins.jar")));
    }

    @Test
    void repeatedSyncChangesNothing() throws IOException {
        Path skins = input("a", "skins.jar", "skins");
        sync(map("skins", skins));
        List<String> before = manifest();
        sync(map("skins", skins));
        assertEquals(before, manifest());
        assertEquals("skins", Files.readString(mods.resolve("skins.jar")));
    }

    @Test
    void noInputsCreateNothing() throws IOException {
        assertEquals(List.of(), sync(map()));
        assertFalse(Files.exists(mods));
        assertFalse(Files.exists(instance.resolve(InstanceMods.MANIFEST)));
    }

    @Test
    void removedInputsLoseTheirCopyButNotOtherFiles() throws IOException {
        Path skins = input("a", "skins.jar", "skins");
        Files.createDirectories(mods);
        Files.writeString(mods.resolve("own.jar"), "own");
        sync(map("skins", skins));
        sync(map());
        assertFalse(Files.exists(mods.resolve("skins.jar")));
        assertEquals("own", Files.readString(mods.resolve("own.jar")));
        assertFalse(Files.exists(instance.resolve(InstanceMods.MANIFEST)));
    }

    @Test
    void modifiedCopiesAreKeptWhenRemoved() throws IOException {
        Path skins = input("a", "skins.jar", "skins");
        sync(map("skins", skins));
        Files.writeString(mods.resolve("skins.jar"), "patched by the user");
        assertEquals(List.of("skins.jar"), sync(map()));
        assertEquals("patched by the user", Files.readString(mods.resolve("skins.jar")));
        assertEquals(List.of(), sync(map()), "不再管理的文件只提示一次");
    }

    @Test
    void newVersionOfAnInputReplacesTheUnchangedCopy() throws IOException {
        Path skins = input("a", "skins.jar", "skins 1");
        sync(map("skins", skins));
        Files.writeString(skins, "skins 2");
        sync(map("skins", skins));
        assertEquals("skins 2", Files.readString(mods.resolve("skins.jar")));
        assertEquals(List.of(InstanceMods.sha256(skins) + "  skins.jar"), manifest());
    }

    @Test
    void renamedInputFileReplacesTheOldCopy() throws IOException {
        sync(map("skins", input("a", "skins-1.jar", "skins 1")));
        sync(map("skins", input("a", "skins-2.jar", "skins 2")));
        assertFalse(Files.exists(mods.resolve("skins-1.jar")));
        assertEquals("skins 2", Files.readString(mods.resolve("skins-2.jar")));
    }

    @Test
    void deletedCopiesAreRestored() throws IOException {
        Path skins = input("a", "skins.jar", "skins");
        sync(map("skins", skins));
        Files.delete(mods.resolve("skins.jar"));
        sync(map("skins", skins));
        assertEquals("skins", Files.readString(mods.resolve("skins.jar")));
    }

    @Test
    void sameFileNameFromTwoInputsIsRejectedBeforeCopying() throws IOException {
        Path first = input("a", "mod.jar", "first");
        Path second = input("b", "mod.jar", "second");
        String message = message(map("first", first, "second", second));
        assertTrue(message.contains("first 与 second 的文件名都是 mod.jar"), message);
        assertFalse(Files.exists(mods));
    }

    @Test
    void fileNamesThatDifferOnlyInCaseAreRejected() throws IOException {
        Path first = input("a", "Mod.jar", "first");
        Path second = input("b", "mod.jar", "second");
        assertTrue(message(map("first", first, "second", second)).contains("文件名都是 mod.jar"));
    }

    @Test
    void userFilesWithTheSameNameAreNeverOverwritten() throws IOException {
        Files.createDirectories(mods);
        Files.writeString(mods.resolve("skins.jar"), "own build");
        Path skins = input("a", "skins.jar", "skins");
        String message = message(map("skins", skins));
        assertTrue(message.contains("mods/skins.jar 已存在，不是由实例设置 mods 放入的"), message);
        assertEquals("own build", Files.readString(mods.resolve("skins.jar")));
        assertFalse(Files.exists(instance.resolve(InstanceMods.MANIFEST)));
    }

    @Test
    void modifiedCopiesAreNeverOverwritten() throws IOException {
        Path skins = input("a", "skins.jar", "skins 1");
        sync(map("skins", skins));
        Files.writeString(mods.resolve("skins.jar"), "patched by the user");
        Files.writeString(skins, "skins 2");
        assertTrue(message(map("skins", skins)).contains("mods/skins.jar 在放入后被改动过"));
        assertEquals("patched by the user", Files.readString(mods.resolve("skins.jar")));
    }

    @Test
    void identicalUserFilesAreAdopted() throws IOException {
        Files.createDirectories(mods);
        Files.writeString(mods.resolve("skins.jar"), "skins");
        Path skins = input("a", "skins.jar", "skins");
        sync(map("skins", skins));
        assertEquals(List.of(InstanceMods.sha256(skins) + "  skins.jar"), manifest());
    }

    @Test
    void directoriesWithTheSameNameAreRejected() throws IOException {
        Files.createDirectories(mods.resolve("skins.jar"));
        assertTrue(
                message(map("skins", input("a", "skins.jar", "skins")))
                        .contains("mods/skins.jar 不是文件"));
    }

    @Test
    void inputsInsideTheModsFolderAreRejected() throws IOException {
        Files.createDirectories(mods);
        Path inside = Files.writeString(mods.resolve("skins.jar"), "skins");
        assertTrue(message(map("skins", inside)).contains("skins 的文件位于实例的 mods 文件夹中"));
        assertEquals("skins", Files.readString(inside));
    }

    @Test
    void foreignManifestLinesAreIgnored() throws IOException {
        Files.createDirectories(mods);
        Files.writeString(mods.resolve("own.jar"), "own");
        Files.writeString(
                instance.resolve(InstanceMods.MANIFEST), "garbage\nnot-a-hash  own.jar\n\n");
        sync(map());
        assertEquals("own", Files.readString(mods.resolve("own.jar")));
    }

    @Test
    void conflictsLeaveEverythingUntouched() throws IOException {
        Path skins = input("a", "skins.jar", "skins");
        sync(map("skins", skins));
        List<String> before = manifest();
        Files.writeString(mods.resolve("maps.jar"), "own");
        Path maps = input("b", "maps.jar", "maps");
        message(map("skins", skins, "maps", maps));
        assertEquals(before, manifest());
        assertEquals("own", Files.readString(mods.resolve("maps.jar")));
        assertEquals("skins", Files.readString(mods.resolve("skins.jar")));
    }
}
