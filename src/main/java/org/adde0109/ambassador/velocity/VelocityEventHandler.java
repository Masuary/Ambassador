package org.adde0109.ambassador.velocity;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.ModInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.StateRegistry;
import org.adde0109.ambassador.Ambassador;
import org.adde0109.ambassador.forge.ForgeConstants;
import org.adde0109.ambassador.forge.ForgeFMLConnectionType;
import org.adde0109.ambassador.forge.ForgePlayerData;
import org.adde0109.ambassador.forge.VelocityForgeClientConnectionPhase;
import org.adde0109.ambassador.forge.pipeline.ForgeLoginWrapperCodec;
import org.adde0109.ambassador.forge.pipeline.ForgeLoginWrapperHandler;

import java.util.Optional;

public class VelocityEventHandler {

  private final Ambassador ambassador;

  public VelocityEventHandler(Ambassador ambassador) {
    this.ambassador = ambassador;
  }

  @Subscribe(order = PostOrder.FIRST)
  public void onLoginEvent(LoginEvent event) {
    if (!event.getResult().isAllowed()) {
      return;
    }
    Player player = event.getPlayer();
    Optional<Player> handshakingSession = ForgePlayerData.findOtherActiveHolder(player);
    if (handshakingSession.isEmpty()) {
      return;
    }
    if (kickExistingPlayers()) {
      return;
    }
    ambassador.logger.info("Refusing a second login of {}: a session of this account is in its Forge handshake",
            player.getUsername());
    event.setResult(ResultedEvent.ComponentResult.denied(ForgeConstants.ALREADY_CONNECTED_MESSAGE));
  }

  @Subscribe(order = PostOrder.FIRST)
  public void onPostLoginEvent(PostLoginEvent event, Continuation continuation) {
    ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
    if (kickExistingPlayers() && player.isActive()) {
      ForgePlayerData.findOtherActiveHolder(player)
              .ifPresent(previousSession -> previousSession.disconnect(ForgeConstants.DUPLICATE_LOGIN_MESSAGE));
    }
    if (player.getPhase() instanceof VelocityForgeClientConnectionPhase) {
      if (!player.isActive()) {
        continuation.resume();
        return;
      }
      if (!claimHandshake(player)) {
        ambassador.logger.warn("Disconnecting {}: another session of this account is in its Forge handshake",
                player.getUsername());
        refuseDuringLogin(player);
        continuation.resume();
        return;
      }

      ((VelocityServer) Ambassador.getInstance().server).unregisterConnection(player);

      player.getConnection().eventLoop().submit(() -> {
        player.getConnection().setState(StateRegistry.LOGIN);

        player.getConnection().getChannel().pipeline().addBefore(
                Connections.HANDLER,
                ForgeConstants.FORGE_HANDSHAKE_DECODER, new ForgeLoginWrapperCodec(
                        player.getConnection().getType() == ForgeConstants.ForgeFML3));
        player.getConnection().getChannel().pipeline().addAfter(
                ForgeConstants.FORGE_HANDSHAKE_DECODER,
                ForgeConstants.FORGE_HANDSHAKE_HANDLER,
                new ForgeLoginWrapperHandler(player));
      });
    }
    continuation.resume();
  }

  @Subscribe(order = PostOrder.LAST)
  public void onPlayerChooseInitialServerEvent(
          PlayerChooseInitialServerEvent event, Continuation continuation) {
    ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
    if (!(player.getPhase() instanceof VelocityForgeClientConnectionPhase)) {
      continuation.resume();
      return;
    }
    RegisteredServer chosenServer =
            Ambassador.getTemporaryForced().remove(player.getUsername());
    if (chosenServer != null)
      event.setInitialServer(chosenServer);
    continuation.resume();
  }

  @Subscribe
  public void onPlayerChannelRegisterEvent(PlayerChannelRegisterEvent event) {
    ConnectedPlayer player = (ConnectedPlayer) event.getPlayer();
    if (!(player.getConnection().getType() instanceof ForgeFMLConnectionType) || !player.isActive()) {
      return;
    }
    player.setModInfo(new ModInfo("Channels", event.getChannels().stream().map((id) -> {
      return new ModInfo.Mod(id.getId(), "");
    }).toList()));

    VelocityForgeClientConnectionPhase clientPhase =
            (VelocityForgeClientConnectionPhase) player.getPhase();
    if (clientPhase.getResetType(player)
            == VelocityForgeClientConnectionPhase.ClientResetType.UNKNOWN) {
      clientPhase.updateResetType(player);
    }
  }

  @Subscribe
  public void onDisconnectEvent(DisconnectEvent event) {
    ForgePlayerData.remove(event.getPlayer());
  }

  private boolean claimHandshake(ConnectedPlayer player) {
    if (!kickExistingPlayers()) {
      return ForgePlayerData.claim(player);
    }
    ForgePlayerData.takeOver(player);
    return true;
  }

  private void refuseDuringLogin(ConnectedPlayer player) {
    player.getConnection().eventLoop().execute(() -> {
      ((VelocityServer) ambassador.server).unregisterConnection(player);
      player.getConnection().setState(StateRegistry.LOGIN);
      player.disconnect0(ForgeConstants.ALREADY_CONNECTED_MESSAGE, true);
    });
  }

  private boolean kickExistingPlayers() {
    VelocityConfiguration configuration = ((VelocityServer) ambassador.server).getConfiguration();
    return configuration.isOnlineMode() && configuration.isOnlineModeKickExistingPlayers();
  }
}
