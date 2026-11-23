package org.adde0109.ambassador.forge;

import com.velocitypowered.api.proxy.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class ForgePlayerData {

    private static final ConcurrentHashMap<UUID, ForgePlayerData> PLAYER_DATA = new ConcurrentHashMap<>();

    private final Player owner;
    private ForgeHandshake clientHandshake = new ForgeHandshake();
    private ForgeHandshake backendHandshake = new ForgeHandshake();
    private VelocityForgeClientConnectionPhase.ClientResetType resetType =
            VelocityForgeClientConnectionPhase.ClientResetType.UNKNOWN;
    private volatile CountDownLatch registryLatch;
    private String clientRegistrySourceServer;
    private int registriesUntilDriftCheck = -1;

    private ForgePlayerData(Player owner) {
        this.owner = owner;
    }

    public static boolean claim(Player player) {
        ForgePlayerData data = PLAYER_DATA.compute(player.getUniqueId(), (uuid, existing) ->
                existing == null || !existing.owner.isActive() ? new ForgePlayerData(player) : existing);
        return data.owner == player;
    }

    public static void takeOver(Player player) {
        PLAYER_DATA.put(player.getUniqueId(), new ForgePlayerData(player));
    }

    public static Optional<Player> findOtherActiveHolder(Player player) {
        ForgePlayerData data = PLAYER_DATA.get(player.getUniqueId());
        if (data == null || data.owner == player || !data.owner.isActive()) {
            return Optional.empty();
        }
        return Optional.of(data.owner);
    }

    public static ForgePlayerData get(Player player) {
        ForgePlayerData data = PLAYER_DATA.get(player.getUniqueId());
        if (data == null || data.owner != player) {
            throw new IllegalStateException("No Forge handshake data for this session of "
                    + player.getUsername() + " (" + player.getUniqueId() + ")");
        }
        return data;
    }

    public static void remove(Player player) {
        PLAYER_DATA.computeIfPresent(player.getUniqueId(), (uuid, existing) -> existing.owner == player ? null : existing);
    }

    public ForgeHandshake getClientHandshake() {
        return clientHandshake;
    }

    public void resetClientHandshake() {
        this.clientHandshake = new ForgeHandshake();
    }

    public ForgeHandshake getBackendHandshake() {
        return backendHandshake;
    }

    public void resetBackendHandshake() {
        this.backendHandshake = new ForgeHandshake();
    }

    public VelocityForgeClientConnectionPhase.ClientResetType getResetType() {
        return resetType;
    }

    public void setResetType(VelocityForgeClientConnectionPhase.ClientResetType resetType) {
        this.resetType = resetType;
    }

    public void createRegistryLatch(int count) {
        this.registryLatch = new CountDownLatch(count);
    }

    public CountDownLatch getRegistryLatch() {
        return registryLatch;
    }

    public void recordClientRegistrySource(String serverName) {
        this.clientRegistrySourceServer = serverName;
    }

    public String getClientRegistrySourceServer() {
        return Objects.requireNonNull(clientRegistrySourceServer,
                "the client's registries were never recorded from a backend");
    }

    public void expectRegistriesForDriftCheck(int registryCount) {
        this.registriesUntilDriftCheck = registryCount;
    }

    public boolean countRegistryForDriftCheck() {
        if (registriesUntilDriftCheck <= 0) {
            return false;
        }
        registriesUntilDriftCheck--;
        return registriesUntilDriftCheck == 0;
    }
}
