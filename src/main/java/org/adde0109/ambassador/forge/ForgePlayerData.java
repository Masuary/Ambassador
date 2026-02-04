package org.adde0109.ambassador.forge;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class ForgePlayerData {

    private static final ConcurrentHashMap<UUID, ForgePlayerData> PLAYER_DATA = new ConcurrentHashMap<>();

    private ForgeHandshake clientHandshake = new ForgeHandshake();
    private ForgeHandshake backendHandshake = new ForgeHandshake();
    private VelocityForgeClientConnectionPhase.ClientResetType resetType =
            VelocityForgeClientConnectionPhase.ClientResetType.UNKNOWN;
    private volatile CountDownLatch registryLatch;

    public static ForgePlayerData getOrCreate(UUID playerUUID) {
        return PLAYER_DATA.computeIfAbsent(playerUUID, k -> new ForgePlayerData());
    }

    public static ForgePlayerData get(UUID playerUUID) {
        return PLAYER_DATA.computeIfAbsent(playerUUID, k -> new ForgePlayerData());
    }

    public static void remove(UUID playerUUID) {
        PLAYER_DATA.remove(playerUUID);
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
}
