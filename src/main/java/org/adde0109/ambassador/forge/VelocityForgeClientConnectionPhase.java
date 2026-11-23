package org.adde0109.ambassador.forge;

import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.util.ModInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ClientConnectionPhase;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.LoginPluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import org.adde0109.ambassador.Ambassador;
import org.adde0109.ambassador.forge.packet.Context;
import org.adde0109.ambassador.forge.packet.GenericForgeLoginWrapperPacket;
import org.adde0109.ambassador.forge.packet.IForgeLoginWrapperPacket;
import org.adde0109.ambassador.forge.packet.ModListReplyPacket;
import org.adde0109.ambassador.velocity.client.FML2CRPMResetCompleteDecoder;
import org.adde0109.ambassador.velocity.client.OutboundSuccessHolder;
import org.adde0109.ambassador.velocity.client.ClientPacketQueue;

import java.nio.charset.StandardCharsets;

public enum VelocityForgeClientConnectionPhase implements ClientConnectionPhase {

  NOT_STARTED {
    @Override
    VelocityForgeClientConnectionPhase nextPhase() {
      return IN_PROGRESS;
    }

    @Override
    public void complete(ConnectedPlayer player) {
      ClientResetType.CRP.doReset(player);
    }
    },
  IN_PROGRESS {
  },
  WAITING_RESET() {
    @Override
    void onTransitionToNewPhase(ConnectedPlayer player) {
      ((VelocityServer) Ambassador.getInstance().server).unregisterConnection(player);
      player.getConnection().getChannel().pipeline().addAfter(Connections.MINECRAFT_ENCODER,
              ForgeConstants.LOGIN_PACKET_QUEUE, new ClientPacketQueue(StateRegistry.PLAY));
      if (player.getConnection().getChannel().pipeline().get(ForgeConstants.PLUGIN_PACKET_QUEUE) == null)
        player.getConnection().getChannel().pipeline().addAfter(Connections.MINECRAFT_ENCODER,
                ForgeConstants.PLUGIN_PACKET_QUEUE, new ClientPacketQueue(StateRegistry.LOGIN));
    }

    @Override
    public boolean handle(ConnectedPlayer player, IForgeLoginWrapperPacket<?> msg,
                          VelocityServerConnection server) {
      if (msg.getContext().getResponseID() == ForgeConstants.RESET_LOGIN_MESSAGE_ID) {
        player.getConnection().getChannel().pipeline().remove(ForgeConstants.RESET_LISTENER);
        player.setPhase(NOT_STARTED);

        player.getConnection().getChannel().pipeline().remove(ForgeConstants.LOGIN_PACKET_QUEUE);

        if (!(server.getConnection().getType() instanceof ForgeFMLConnectionType)) {
          complete(player, ((Context.ClientContext) msg.getContext()).success()
                  ? ClientResetType.CRP : null);
        }

        if (player.getConnectionInFlight() != null) {
          player.getConnectionInFlight().getConnection().getChannel().config().setAutoRead(true);
        }

        return true;
      } else {
        return false;
      }
    }
  },
  COMPLETE {
    @Override
    void onTransitionToNewPhase(ConnectedPlayer player) {
      MinecraftConnection connection = player.getConnection();
      ((OutboundSuccessHolder) connection.getChannel().pipeline()
              .get(ForgeConstants.SERVER_SUCCESS_LISTENER)).sendPacket();
      connection.setState(StateRegistry.PLAY);
      player.getConnection().getChannel().pipeline().remove(ForgeConstants.PLUGIN_PACKET_QUEUE);
      VelocityServer server = (VelocityServer) Ambassador.getInstance().server;
      boolean registered = server.getPlayer(player.getUniqueId()).orElse(null) == player
              || server.registerConnection(player);
      if (!registered) {
        Ambassador.getInstance().logger.warn(
                "Disconnecting {}: another session of this account joined during its Forge handshake",
                player.getUsername());
        player.disconnect(ForgeConstants.ALREADY_CONNECTED_MESSAGE);
      }
    }

    @Override
    public void resetConnectionPhase(ConnectedPlayer player) {
      ForgePlayerData data = ForgePlayerData.get(player);
      data.getResetType().doReset(player);
    }

    @Override
    public boolean consideredComplete() {
      return true;
    }

    @Override
    public void complete(ConnectedPlayer player) {
      if (Ambassador.getInstance().config.isDebugMode()) {
        player.sendMessage(Component.text("Not resetting"));
      }
    }
  };

  public boolean handle(ConnectedPlayer player, IForgeLoginWrapperPacket<?> msg,
                        VelocityServerConnection server) {
    ForgePlayerData data = ForgePlayerData.get(player);
    ForgeHandshake handshake = data.getClientHandshake();

    if (msg.getContext().getChannelName().equals("zeta:main")
            && msg instanceof GenericForgeLoginWrapperPacket<?> zetaFlagsPacket) {
      handshake.zetaFlagsPacket = zetaFlagsPacket;
    }

    if (msg instanceof ModListReplyPacket replyPacket) {
      ModInfo modInfo = new ModInfo("FML2", replyPacket.getMods().stream().map(
              (v) -> new ModInfo.Mod(v,"1")).toList());
      player.setModInfo(modInfo);
      handshake.setModListReplyPacket(replyPacket);
      if (!(server.getConnection().getType() instanceof ForgeFMLConnectionType)) {
        complete(player);
        player.getConnectionInFlight().getConnection().getChannel().config().setAutoRead(true);
        return true;
      }
      replyPacket.getChannels().put(
              MinecraftChannelIdentifier.from("ambassador:commands"),"1");
    }

    player.getConnectionInFlight().getConnection().write(msg);

    player.setPhase(nextPhase());

    return true;
  }

  public void complete(ConnectedPlayer player) {
    complete(player, null);
  }

  public void complete(ConnectedPlayer player, ClientResetType resetType) {
    player.setPhase(COMPLETE);
    COMPLETE.onTransitionToNewPhase(player);
    if (resetType != null) {
      ForgePlayerData data = ForgePlayerData.get(player);
      data.setResetType(resetType);
      if (Ambassador.getInstance().config.isDebugMode()) {
        player.sendMessage(Component.text("Reset type: " + resetType.toString()));
      }
    }

    if (Ambassador.getInstance().config.isDebugMode()) {
      player.sendMessage(Component.text("Forge handshake complete"));
    }
  }

  void onTransitionToNewPhase(ConnectedPlayer player) {
  }

  VelocityForgeClientConnectionPhase nextPhase() {
    return this;
  }

  @Override
  public boolean consideredComplete() {
    return false;
  }

  public ClientResetType getResetType(ConnectedPlayer player) {
    return ForgePlayerData.get(player).getResetType();
  }

  private ClientResetType detectResetType(ConnectedPlayer player) {
    if (Ambassador.getInstance().config.isDebugMode()) {
      player.sendMessage(Component.text("Scanning modlist for client reset mods"));
    }
    if (player.getModInfo().isPresent()) {
      if (player.getModInfo().get().getMods().stream()
              .anyMatch((mod -> mod.getId().equals("clientresetpacket")))) {
        return ClientResetType.CRP;
      } else if (Ambassador.getInstance().config.getServerSwitchCancellationTime() >= 0 &&
              player.getModInfo().get().getMods().stream()
                      .anyMatch((mod -> mod.getId().equals("serverredirect")
                              || mod.getId().equals("srvredirect:red")))
              && player.getVirtualHost().isPresent()) {
        return ClientResetType.SR;
      }
    }
    return ClientResetType.NONE;
  }

  public void updateResetType(ConnectedPlayer player) {
    ForgePlayerData data = ForgePlayerData.get(player);
    data.setResetType(detectResetType(player));
    if (Ambassador.getInstance().config.isDebugMode()) {
      player.sendMessage(Component.text("Reset type: " + data.getResetType().toString()));
    }
  }

  public enum ClientResetType {
    UNKNOWN,
    NONE,
    CRP {
      @Override
      void doReset(ConnectedPlayer player) {
        MinecraftConnection connection = player.getConnection();

        if (player.getConnectedServer() != null) {
          player.getConnectedServer().disconnect();
          player.setConnectedServer(null);
        }
        if (player.getConnectionInFlight() != null) {
          player.getConnectionInFlight().getConnection().getChannel().config()
                  .setAutoRead(false);
        }

        if (connection.getState() == StateRegistry.PLAY
                || connection.getState() == StateRegistry.CONFIG) {
          connection.write(new PluginMessagePacket(ForgeConstants.HANDSHAKE_CHANNEL,
                  Unpooled.wrappedBuffer(ForgeHandshakeUtils.generatePluginResetPacket())));
          connection.setState(StateRegistry.LOGIN);
        } else {
          connection.write(new LoginPluginMessagePacket(ForgeConstants.RESET_LOGIN_MESSAGE_ID, ForgeConstants.LOGIN_WRAPPER_CHANNEL,
                  Unpooled.wrappedBuffer(ForgeHandshakeUtils.generateResetPacket())));
        }

        connection.getChannel().pipeline().addBefore(Connections.MINECRAFT_DECODER,
                ForgeConstants.RESET_LISTENER, new FML2CRPMResetCompleteDecoder());

        player.setPhase(WAITING_RESET);
        WAITING_RESET.onTransitionToNewPhase(player);
      }
    },
    SR {
      @Override
      void doReset(ConnectedPlayer player) {
        ByteBuf buf = Unpooled.buffer();
        ProtocolUtils.writeVarInt(buf, 0);
        buf.writeBytes((player.getVirtualHost().get().getHostName() + ":"
                + player.getVirtualHost().get().getPort()).getBytes(StandardCharsets.UTF_8));
        player.getConnection().write(new PluginMessagePacket("srvredirect:red", buf));

        Ambassador.getInstance().reconnectSwitchPlayer(player);
      }
    };

    void doReset(ConnectedPlayer player) {
    }
  }
}
