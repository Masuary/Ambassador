package org.adde0109.ambassador.forge;

import com.velocitypowered.proxy.connection.ConnectionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ForgeConstants {
  public static final String HANDLER = "Modern Forge handler";
  public static final String MARKER_ADDER = "FML2/3 Marker Adder";
  public static final String RESET_LISTENER = "ambassador-reset-listener";
  public static final String SERVER_SUCCESS_LISTENER = "ambassador-server-success-listener";
  public static final String PLUGIN_PACKET_QUEUE = "ambassador-plugin-generated-packet-queue";
  public static final String LOGIN_PACKET_QUEUE = "ambassador-login-packet-queue";
  public static final String FORGE_HANDSHAKE_DECODER = "ambassador-forge-decoder";
  public static final String FORGE_HANDSHAKE_HANDLER = "ambassador-forge-handler";
  public static final String COMMAND_ERROR_CATCHER = "ambassador-command-catcher";

  public static final String HANDSHAKE_CHANNEL = "fml:handshake";
  public static final String LOGIN_WRAPPER_CHANNEL = "fml:loginwrapper";

  public static final int MOD_LIST_PACKET_ID = 1;
  public static final int MOD_LIST_REPLY_PACKET_ID = 2;
  public static final int REGISTRY_PACKET_ID = 3;
  public static final int CONFIG_DATA_PACKET_ID = 4;
  public static final int MOD_DATA_PACKET_ID = 5;
  public static final int RESET_PACKET_ID = 98;
  public static final int ACK_PACKET_ID = 99;
  public static final int RESET_LOGIN_MESSAGE_ID = 98;

  public static final String FML2Marker = "\0FML2\0";
  public static final String FML3Marker = "\0FML3\0";
  public static final ConnectionType ForgeFML2 = new ForgeFMLConnectionType(2);
  public static final ConnectionType ForgeFML3 = new ForgeFMLConnectionType(3);

  public static final Component ALREADY_CONNECTED_MESSAGE =
          Component.translatable("velocity.error.already-connected-proxy", NamedTextColor.RED);
  public static final Component DUPLICATE_LOGIN_MESSAGE =
          Component.translatable("multiplayer.disconnect.duplicate_login");
}
