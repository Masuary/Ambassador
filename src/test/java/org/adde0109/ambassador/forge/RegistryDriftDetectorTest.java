package org.adde0109.ambassador.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.adde0109.ambassador.forge.packet.Context;
import org.adde0109.ambassador.forge.packet.RegistryPacket;
import org.junit.jupiter.api.Test;

class RegistryDriftDetectorTest {

  private static ForgeHandshake handshakeWith(String... registryNamesAndContents) {
    ForgeHandshake handshake = new ForgeHandshake();
    for (int index = 0; index < registryNamesAndContents.length; index += 2) {
      byte[] snapshot = registryNamesAndContents[index + 1] == null
          ? null
          : registryNamesAndContents[index + 1].getBytes(StandardCharsets.UTF_8);
      handshake.addRegistry(new RegistryPacket(registryNamesAndContents[index], snapshot,
          Context.createContext(index, ForgeConstants.HANDSHAKE_CHANNEL)));
    }
    return handshake;
  }

  @Test
  void identicalRegistriesDoNotDiffer() {
    ForgeHandshake source = handshakeWith("minecraft:block", "stone=1", "minecraft:item", "stick=2");
    ForgeHandshake target = handshakeWith("minecraft:item", "stick=2", "minecraft:block", "stone=1");

    assertEquals(List.of(), RegistryDriftDetector.differingRegistries(source, target));
  }

  @Test
  void aRegistryWithOtherIdsIsNamed() {
    ForgeHandshake source = handshakeWith("minecraft:block", "stone=1", "minecraft:item", "stick=2");
    ForgeHandshake target = handshakeWith("minecraft:block", "stone=1", "minecraft:item", "stick=3");

    assertEquals(List.of("minecraft:item"), RegistryDriftDetector.differingRegistries(source, target));
  }

  @Test
  void aRegistryOnlyOneSideHasIsNamedAndTheListIsSorted() {
    ForgeHandshake source = handshakeWith("the_vault:gear", "a", "minecraft:block", "stone=1");
    ForgeHandshake target = handshakeWith("minecraft:block", "stone=1", "minecraft:fluid", null);

    assertEquals(List.of("minecraft:fluid", "the_vault:gear"),
        RegistryDriftDetector.differingRegistries(source, target));
  }

  @Test
  void aDriftIsReportedOncePerServerPair() {
    RegistryDriftDetector detector = new RegistryDriftDetector();
    ForgeHandshake source = handshakeWith("minecraft:item", "stick=2");
    ForgeHandshake drifted = handshakeWith("minecraft:item", "stick=3");

    Optional<String> first = detector.reportOnce("MAIN", source, "OMNI", drifted);
    Optional<String> repeated = detector.reportOnce("MAIN", source, "OMNI", drifted);
    Optional<String> otherDirection = detector.reportOnce("OMNI", drifted, "MAIN", source);

    assertTrue(first.isPresent());
    assertTrue(first.get().contains("OMNI") && first.get().contains("MAIN")
        && first.get().contains("minecraft:item"), first.get());
    assertEquals(Optional.empty(), repeated);
    assertTrue(otherDirection.isPresent());
  }

  @Test
  void matchingRegistriesAreNeverReported() {
    RegistryDriftDetector detector = new RegistryDriftDetector();
    ForgeHandshake source = handshakeWith("minecraft:item", "stick=2");

    assertEquals(Optional.empty(), detector.reportOnce("MAIN", source, "OMNI", handshakeWith("minecraft:item", "stick=2")));
  }
}
