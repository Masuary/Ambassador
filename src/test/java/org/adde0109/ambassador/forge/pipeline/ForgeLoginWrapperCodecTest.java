package org.adde0109.ambassador.forge.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.LoginPluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.LoginPluginResponsePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import org.adde0109.ambassador.forge.ForgeConstants;
import org.adde0109.ambassador.forge.packet.ACKPacket;
import org.adde0109.ambassador.forge.packet.Context;
import org.adde0109.ambassador.forge.packet.ModListPacket;
import org.adde0109.ambassador.forge.packet.ModListReplyPacket;
import org.adde0109.ambassador.forge.packet.RegistryPacket;
import org.junit.jupiter.api.Test;

class ForgeLoginWrapperCodecTest {

  private static ByteBuf wrapped(ByteBuf forgePacket) {
    ByteBuf wrapped = Unpooled.buffer();
    ProtocolUtils.writeString(wrapped, ForgeConstants.HANDSHAKE_CHANNEL);
    ProtocolUtils.writeVarInt(wrapped, forgePacket.readableBytes());
    wrapped.writeBytes(forgePacket);
    return wrapped;
  }

  private static ByteBuf modList() {
    ByteBuf buf = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buf, ForgeConstants.MOD_LIST_PACKET_ID);
    ProtocolUtils.writeVarInt(buf, 1);
    ProtocolUtils.writeString(buf, "the_vault");
    ProtocolUtils.writeVarInt(buf, 0);
    ProtocolUtils.writeVarInt(buf, 1);
    ProtocolUtils.writeString(buf, "minecraft:item");
    ProtocolUtils.writeVarInt(buf, 0);
    return buf;
  }

  private static ByteBuf modListReply() {
    ByteBuf buf = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buf, ForgeConstants.MOD_LIST_REPLY_PACKET_ID);
    ProtocolUtils.writeVarInt(buf, 1);
    ProtocolUtils.writeString(buf, "the_vault");
    ProtocolUtils.writeVarInt(buf, 0);
    ProtocolUtils.writeVarInt(buf, 0);
    return buf;
  }

  @Test
  void aServersModListArrivesAsAModListPacket() {
    EmbeddedChannel channel = new EmbeddedChannel(new ForgeLoginWrapperCodec(true));

    channel.writeInbound(new LoginPluginMessagePacket(3, ForgeConstants.LOGIN_WRAPPER_CHANNEL, wrapped(modList())));

    ModListPacket packet = assertInstanceOf(ModListPacket.class, channel.readInbound());
    assertEquals(3, packet.getContext().getResponseID());
    assertEquals(List.of("the_vault"), packet.getMods());
    assertEquals(List.of("minecraft:item"), packet.getRegistries());
  }

  @Test
  void aRegistryArrivesAsARegistryPacket() {
    ByteBuf registry = new RegistryPacket("minecraft:item", new byte[] {1, 2}, null).encode();
    EmbeddedChannel channel = new EmbeddedChannel(new ForgeLoginWrapperCodec(true));

    channel.writeInbound(new LoginPluginMessagePacket(4, ForgeConstants.LOGIN_WRAPPER_CHANNEL, wrapped(registry)));

    RegistryPacket packet = assertInstanceOf(RegistryPacket.class, channel.readInbound());
    assertEquals("minecraft:item", packet.getRegistryName());
  }

  @Test
  void anAckToAServerIsALoginPluginResponseWithTheServersMessageId() {
    EmbeddedChannel channel = new EmbeddedChannel(new ForgeLoginWrapperCodec(true));

    channel.writeOutbound(new ACKPacket(Context.createClientContext(9, true, ForgeConstants.HANDSHAKE_CHANNEL)));

    LoginPluginResponsePacket response = assertInstanceOf(LoginPluginResponsePacket.class, channel.readOutbound());
    assertEquals(9, response.getId());
    assertTrue(response.isSuccess());
    ByteBuf content = response.content();
    assertEquals(ForgeConstants.HANDSHAKE_CHANNEL, ProtocolUtils.readString(content));
    assertEquals(1, ProtocolUtils.readVarInt(content));
    assertEquals(ForgeConstants.ACK_PACKET_ID, ProtocolUtils.readVarInt(content));
  }

  @Test
  void theClientsReplyToAForwardedModListIsMatchedByItsMessageId() {
    EmbeddedChannel clientChannel = new EmbeddedChannel(new ForgeLoginWrapperCodec(true));
    EmbeddedChannel serverChannel = new EmbeddedChannel(new ForgeLoginWrapperCodec(true));
    serverChannel.writeInbound(new LoginPluginMessagePacket(5, ForgeConstants.LOGIN_WRAPPER_CHANNEL, wrapped(modList())));
    ModListPacket fromServer = serverChannel.readInbound();

    clientChannel.writeOutbound(fromServer);
    LoginPluginMessagePacket toClient = assertInstanceOf(LoginPluginMessagePacket.class, clientChannel.readOutbound());
    clientChannel.writeInbound(new LoginPluginResponsePacket(toClient.getId(), true, wrapped(modListReply())));

    ModListReplyPacket reply = assertInstanceOf(ModListReplyPacket.class, clientChannel.readInbound());
    assertEquals(5, reply.getContext().getResponseID());
    assertEquals(List.of("the_vault"), reply.getMods());
  }
}
