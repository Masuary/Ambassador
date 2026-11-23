package org.adde0109.ambassador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AmbassadorConfigTest {

  @TempDir
  Path configDirectory;

  private AmbassadorConfig readConfig(String fileContent) throws IOException {
    Path configPath = configDirectory.resolve("Ambassador.toml");
    Files.writeString(configPath, fileContent);
    return AmbassadorConfig.read(configPath);
  }

  @Test
  void serverRedirectTimeoutIsTakenFromTheFile() throws IOException {
    AmbassadorConfig config = readConfig("config-version = \"2.1\"\nserverRedirectTimeout = 45\n");

    assertEquals(45, config.getServerSwitchCancellationTime());
  }

  @Test
  void serverRedirectTimeoutMinusOneDisablesServerRedirectSupport() throws IOException {
    AmbassadorConfig config = readConfig("config-version = \"2.1\"\nserverRedirectTimeout = -1\n");

    assertEquals(-1, config.getServerSwitchCancellationTime());
  }

  @Test
  void kickResetKeepsAReconnectWindowWhenServerRedirectIsDisabled() throws IOException {
    assertEquals(30, readConfig("config-version = \"2.1\"\nserverRedirectTimeout = -1\n").getKickResetReconnectSeconds());
    assertEquals(30, readConfig("config-version = \"2.1\"\nserverRedirectTimeout = 0\n").getKickResetReconnectSeconds());
    assertEquals(45, readConfig("config-version = \"2.1\"\nserverRedirectTimeout = 45\n").getKickResetReconnectSeconds());
  }

  @Test
  void aMissingFileIsCreatedWithTheDefaults() throws IOException {
    Path configPath = configDirectory.resolve("Ambassador.toml");

    AmbassadorConfig config = AmbassadorConfig.read(configPath);

    assertTrue(Files.exists(configPath));
    assertEquals(30, config.getServerSwitchCancellationTime());
    assertFalse(config.isBypassRegistryCheck());
    assertFalse(config.isEnableKickReset());
    assertEquals("<red>Please reconnect.</red>", config.getKickReconnectMessageString());
  }

  @Test
  void theLiveSkyvaultSettingsAreRead() throws IOException {
    AmbassadorConfig config = readConfig("""
        config-version = "2.1"
        serverRedirectTimeout = 30
        silence-warnings = false
        bypass-registry-checks = true
        bypass-mod-checks = false
        debug-mode = false
        enable-kick-reset = false
        reconnect-message = "<gold>Please reconnect</gold>"
        """);

    assertTrue(config.isBypassRegistryCheck());
    assertFalse(config.isEnableKickReset());
    assertFalse(config.isDebugMode());
    assertEquals("<gold>Please reconnect</gold>", config.getKickReconnectMessageString());
  }

  @Test
  void aVersionTwoConfigIsUpgradedAndKeepsItsValues() throws IOException {
    AmbassadorConfig config = readConfig("""
        config-version = "2.0"
        serverRedirectTimeout = 12
        bypass-registry-checks = true
        reconnect-message = "<gold>Again</gold>"
        """);

    assertEquals(12, config.getServerSwitchCancellationTime());
    assertTrue(config.isBypassRegistryCheck());
    assertEquals("<gold>Again</gold>", config.getKickReconnectMessageString());
    String upgradedFile = Files.readString(configDirectory.resolve("Ambassador.toml"));
    assertTrue(upgradedFile.contains("config-version = \"2.1\""), upgradedFile);
  }
}
