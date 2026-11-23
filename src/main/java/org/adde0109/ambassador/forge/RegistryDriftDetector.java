package org.adde0109.ambassador.forge;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class RegistryDriftDetector {

  private final Set<String> reportedServerPairs = ConcurrentHashMap.newKeySet();

  public static List<String> differingRegistries(ForgeHandshake source, ForgeHandshake target) {
    Map<String, Long> sourceRegistries = source.getRegistries();
    Map<String, Long> targetRegistries = target.getRegistries();
    Set<String> registryNames = new TreeSet<>(sourceRegistries.keySet());
    registryNames.addAll(targetRegistries.keySet());
    return registryNames.stream()
            .filter(name -> !Objects.equals(sourceRegistries.get(name), targetRegistries.get(name)))
            .toList();
  }

  public Optional<String> reportOnce(String sourceServer, ForgeHandshake source,
                                     String targetServer, ForgeHandshake target) {
    List<String> differing = differingRegistries(source, target);
    if (differing.isEmpty() || !reportedServerPairs.add(sourceServer + " -> " + targetServer)) {
      return Optional.empty();
    }
    return Optional.of("Registry drift: " + targetServer + " differs from " + sourceServer + " in "
            + differing.size() + " registries " + differing + ". bypass-registry-checks lets Forge clients"
            + " switch anyway, so they can see wrong blocks or items there until the registries match again."
            + " Reported once per server pair until the proxy restarts.");
  }
}
