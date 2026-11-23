package org.adde0109.ambassador.forge;

import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.BackendConnectionPhase;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.AvailableCommandsPacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import net.kyori.adventure.text.Component;
import org.adde0109.ambassador.Ambassador;
import org.adde0109.ambassador.forge.packet.*;
import org.adde0109.ambassador.forge.pipeline.CommandDecoderErrorCatcher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public enum VelocityForgeBackendConnectionPhase implements BackendConnectionPhase {
  NOT_STARTED {
    @Override
    VelocityForgeBackendConnectionPhase nextPhase() {
      return IN_PROGRESS;
    }

    @Override
    public boolean consideredComplete() {
      return true;
    }
  },
  IN_PROGRESS {
    @Override
    public void onLoginSuccess(VelocityServerConnection serverCon, ConnectedPlayer player) {
      serverCon.setConnectionPhase(VelocityForgeBackendConnectionPhase.COMPLETE);

      serverCon.getConnection().getChannel().pipeline().addBefore(
              Connections.MINECRAFT_DECODER,
              ForgeConstants.COMMAND_ERROR_CATCHER,
              new CommandDecoderErrorCatcher(
                      serverCon.getConnection().getProtocolVersion(), player));
    }

    @Override
    void onTransitionToNewPhase(VelocityServerConnection connection) {
      MinecraftConnection mc = connection.getConnection();
      if (mc != null) {
        mc.setType(connection.getPlayer().getConnection().getType());
      }
    }
  },

  COMPLETE {
    @Override
    public boolean consideredComplete() {
      return true;
    }
  };

  VelocityForgeBackendConnectionPhase() {
  }

  public void handle(VelocityServerConnection server, ConnectedPlayer player,
                     IForgeLoginWrapperPacket<?> message) {
    VelocityForgeBackendConnectionPhase newPhase = getNewPhase(server, message);

    server.setConnectionPhase(newPhase);

    if (!player.isActive()) {
      return;
    }

    VelocityForgeClientConnectionPhase clientPhase =
            (VelocityForgeClientConnectionPhase) player.getPhase();
    ForgePlayerData playerData = ForgePlayerData.get(player);

    if (!clientPhase.consideredComplete()) {
      forwardInitialHandshakePacket(player, playerData, message);
    } else {
      handleServerSwitchPacket(server, player, clientPhase, playerData, message);
    }
  }

  private static void forwardInitialHandshakePacket(ConnectedPlayer player, ForgePlayerData playerData,
                                                    IForgeLoginWrapperPacket<?> message) {
    if (message instanceof ModListPacket) {
      playerData.resetClientHandshake();
    }
    if (message instanceof RegistryPacket registryPacket) {
      playerData.getClientHandshake().addRegistry(registryPacket);
    }
    player.getConnection().write(message);
  }

  private static void handleServerSwitchPacket(VelocityServerConnection server, ConnectedPlayer player,
                                               VelocityForgeClientConnectionPhase clientPhase,
                                               ForgePlayerData playerData, IForgeLoginWrapperPacket<?> message) {
    VelocityForgeClientConnectionPhase.ClientResetType resetType =
            clientPhase.getResetType(player);
    if (resetType == VelocityForgeClientConnectionPhase.ClientResetType.CRP ||
            resetType == VelocityForgeClientConnectionPhase.ClientResetType.SR) {
      clientPhase.resetConnectionPhase(player);
      player.getConnection().write(message);
      return;
    }

    ForgeHandshake clientHandshake = playerData.getClientHandshake();
    if (clientHandshake.getModListReplyPacket() == null) {
      refuseSwitchWithoutClientHandshake(server, player);
      return;
    }

    if (message instanceof ModListPacket modListPacket) {
      answerSwitchModList(server, player, playerData, clientHandshake, modListPacket);
    } else if (message instanceof RegistryPacket registryPacket) {
      acknowledge(server, message);
      playerData.getBackendHandshake().addRegistry(registryPacket);
      CountDownLatch latch = playerData.getRegistryLatch();
      if (latch != null) {
        latch.countDown();
      }
    } else if (message instanceof ConfigDataPacket) {
      acknowledge(server, message);
    } else if (message instanceof GenericForgeLoginWrapperPacket<?> packet
            && ForgeHandshakeUtils.ThirdPartyRegistryUtils.isThirdPartyPacket(packet)) {
      answerThirdPartyPacket(server, clientHandshake, packet);
    }
  }

  private static void refuseSwitchWithoutClientHandshake(VelocityServerConnection server, ConnectedPlayer player) {
    if (Ambassador.getInstance().config.isEnableKickReset()) {
      Ambassador.getInstance().reconnectSwitchPlayer(player);
    } else {
      Ambassador.getInstance().logger.error(
              "Unable for {} to switch servers. Vanilla({}) -> Forge({}) switch " +
                      "without client side mod or kick-reset enabled is not yet supported!",
              player.getGameProfile().getName(),
              player.getConnectedServer().getServerInfo().getName(),
              server.getServerInfo().getName());
      server.disconnect();
    }
  }

  private static void answerSwitchModList(VelocityServerConnection server, ConnectedPlayer player,
                                          ForgePlayerData playerData, ForgeHandshake clientHandshake,
                                          ModListPacket modListPacket) {
    playerData.resetBackendHandshake();

    if (Ambassador.getInstance().config.isBypassRegistryCheck()) {
      if (Ambassador.getInstance().config.isDebugMode()) {
        player.sendMessage(Component.text(
                "Bypass enabled - skipping registry wait for " +
                        server.getServer().getServerInfo().getName()));
      }
      server.ensureConnected().write(clientHandshake.getModListReplyPacket());
    } else {
      answerModListOnceRegistriesMatch(server, player, playerData, clientHandshake, modListPacket);
    }
  }

  private static void answerModListOnceRegistriesMatch(VelocityServerConnection server, ConnectedPlayer player,
                                                       ForgePlayerData playerData, ForgeHandshake clientHandshake,
                                                       ModListPacket modListPacket) {
    playerData.createRegistryLatch(modListPacket.getRegistries().size());

    if (Ambassador.getInstance().config.isDebugMode())
      player.sendMessage(Component.text(
              "Expecting " + modListPacket.getRegistries().size() +
                      " packets from server " +
                      server.getServer().getServerInfo().getName()));

    long time = System.currentTimeMillis();
    CountDownLatch latch = playerData.getRegistryLatch();

    CompletableFuture.runAsync(() -> {
      try {
        latch.await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }).thenAcceptAsync((v) -> {
      if (Ambassador.getInstance().config.isDebugMode()) {
        player.sendMessage(Component.text(
                "Handshake took: " + (System.currentTimeMillis() - time) + " ms"));
        player.sendMessage(Component.text(
                "Avg packet time " +
                        (System.currentTimeMillis() - time) /
                                modListPacket.getRegistries().size() + " ms"));
      }

      ForgeHandshake backendHandshake = playerData.getBackendHandshake();
      if (clientHandshake.isCompatible(backendHandshake)) {
        server.ensureConnected().write(clientHandshake.getModListReplyPacket());
      } else if (Ambassador.getInstance().config.isEnableKickReset()) {
        Ambassador.getInstance().reconnectSwitchPlayer(player);
      } else {
        Ambassador.getInstance().logger.error(
                "Unable to switch due to the registries of " +
                        server.getServer().getServerInfo().getName() +
                        " being different from the registries of " +
                        player.getConnectedServer().getServer()
                                .getServerInfo().getName());
        server.disconnect();
      }
    }, server.ensureConnected().eventLoop());
  }

  private static void acknowledge(VelocityServerConnection server, IForgeLoginWrapperPacket<?> message) {
    server.getConnection().write(
            new ACKPacket(Context.fromContext(message.getContext(), true)));
  }

  private static void answerThirdPartyPacket(VelocityServerConnection server, ForgeHandshake clientHandshake,
                                             GenericForgeLoginWrapperPacket<?> packet) {
    server.getConnection().write(
            ForgeHandshakeUtils.ThirdPartyRegistryUtils
                    .getThirdPartyChannel(packet)
                    .generateResponsePacket(
                            Context.ClientContext.fromContext(
                                    packet.getContext(), true),
                            clientHandshake));
  }

  public void onLoginSuccess(VelocityServerConnection serverCon, ConnectedPlayer player) {
  }

  void onTransitionToNewPhase(VelocityServerConnection connection) {
  }

  VelocityForgeBackendConnectionPhase nextPhase() {
    return this;
  }

  private VelocityForgeBackendConnectionPhase getNewPhase(
          VelocityServerConnection serverConnection,
          IForgeLoginWrapperPacket<?> packet) {
    VelocityForgeBackendConnectionPhase phaseToTransitionTo = nextPhase();
    if (phaseToTransitionTo != this) {
      phaseToTransitionTo.onTransitionToNewPhase(serverConnection);
    }
    return phaseToTransitionTo;
  }

  @Override
  public boolean handle(VelocityServerConnection server, ConnectedPlayer player,
                        PluginMessagePacket message) {
    if (message.getChannel().equals("ambassador:commands")) {
      AvailableCommandsPacket packet = new AvailableCommandsPacket();
      packet.decode(message.content(), ProtocolUtils.Direction.CLIENTBOUND,
              server.getConnection().getProtocolVersion());
      server.getConnection().getActiveSessionHandler().handle(packet);
      return true;
    }
    return false;
  }

  public boolean consideredComplete() {
    return false;
  }
}
