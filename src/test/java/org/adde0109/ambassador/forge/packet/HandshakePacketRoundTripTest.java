package org.adde0109.ambassador.forge.packet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.adde0109.ambassador.forge.ForgeConstants;
import org.junit.jupiter.api.Test;

class HandshakePacketRoundTripTest {

  private static final Context SERVER_CONTEXT = Context.createContext(7, ForgeConstants.HANDSHAKE_CHANNEL);
  private static final Context.ClientContext CLIENT_CONTEXT = Context.fromContext(SERVER_CONTEXT, true);

  private static ByteBuf fml3ModList() {
    ByteBuf buf = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buf, ForgeConstants.MOD_LIST_PACKET_ID);
    ProtocolUtils.writeVarInt(buf, 2);
    ProtocolUtils.writeString(buf, "forge");
    ProtocolUtils.writeString(buf, "the_vault");
    ProtocolUtils.writeVarInt(buf, 1);
    ProtocolUtils.writeString(buf, "the_vault:main");
    ProtocolUtils.writeString(buf, "1");
    ProtocolUtils.writeVarInt(buf, 2);
    ProtocolUtils.writeString(buf, "minecraft:block");
    ProtocolUtils.writeString(buf, "minecraft:item");
    ProtocolUtils.writeVarInt(buf, 1);
    ProtocolUtils.writeString(buf, "minecraft:worldgen/biome");
    return buf;
  }

  private static byte[] bytesOf(ByteBuf buf) {
    return ByteBufUtil.getBytes(buf);
  }

  private static int skipPacketId(ByteBuf buf) {
    return ProtocolUtils.readVarInt(buf);
  }

  @Test
  void anFml3ModListIsReadAndWrittenBackUnchanged() {
    ByteBuf original = fml3ModList();
    byte[] originalBytes = bytesOf(original);
    assertEquals(ForgeConstants.MOD_LIST_PACKET_ID, skipPacketId(original));

    ModListPacket packet = ModListPacket.read(original, SERVER_CONTEXT, true);

    assertEquals(List.of("forge", "the_vault"), packet.getMods());
    assertEquals(List.of("minecraft:block", "minecraft:item"), packet.getRegistries());
    assertArrayEquals(originalBytes, bytesOf(packet.encode()));
  }

  @Test
  void aRegistryWithASnapshotIsReadAndWrittenBackUnchanged() {
    byte[] snapshot = "ids".getBytes(StandardCharsets.UTF_8);
    RegistryPacket packet = new RegistryPacket("minecraft:item", snapshot, SERVER_CONTEXT);
    ByteBuf encoded = packet.encode();
    byte[] encodedBytes = bytesOf(encoded);
    assertEquals(ForgeConstants.REGISTRY_PACKET_ID, skipPacketId(encoded));

    RegistryPacket read = RegistryPacket.read(encoded, SERVER_CONTEXT, true);

    assertEquals("minecraft:item", read.getRegistryName());
    assertArrayEquals(snapshot, read.getSnapshot());
    assertArrayEquals(encodedBytes, bytesOf(read.encode()));
  }

  @Test
  void aRegistryWithoutASnapshotKeepsNoSnapshot() {
    ByteBuf encoded = new RegistryPacket("forge:entity_data_serializers", null, SERVER_CONTEXT).encode();
    skipPacketId(encoded);

    assertNull(RegistryPacket.read(encoded, SERVER_CONTEXT, true).getSnapshot());
  }

  @Test
  void aModListReplyIsReadAndWrittenBackUnchanged() {
    ByteBuf original = Unpooled.buffer();
    ProtocolUtils.writeVarInt(original, ForgeConstants.MOD_LIST_REPLY_PACKET_ID);
    ProtocolUtils.writeVarInt(original, 1);
    ProtocolUtils.writeString(original, "the_vault");
    ProtocolUtils.writeVarInt(original, 0);
    ProtocolUtils.writeVarInt(original, 1);
    ProtocolUtils.writeString(original, "minecraft:item");
    ProtocolUtils.writeString(original, "1.0");
    byte[] originalBytes = bytesOf(original);
    skipPacketId(original);

    ModListReplyPacket packet = ModListReplyPacket.read(original, CLIENT_CONTEXT);

    assertEquals(List.of("the_vault"), packet.getMods());
    assertArrayEquals(originalBytes, bytesOf(packet.encode()));
  }

  @Test
  void configDataIsReadAndWrittenBackUnchanged() {
    ConfigDataPacket packet = new ConfigDataPacket("the_vault-common.toml",
        "a = 1".getBytes(StandardCharsets.UTF_8), SERVER_CONTEXT);
    ByteBuf encoded = packet.encode();
    byte[] encodedBytes = bytesOf(encoded);
    assertEquals(ForgeConstants.CONFIG_DATA_PACKET_ID, skipPacketId(encoded));

    assertArrayEquals(encodedBytes, bytesOf(ConfigDataPacket.read(encoded, SERVER_CONTEXT, true).encode()));
  }

  @Test
  void anAckIsOnlyItsPacketId() {
    ByteBuf encoded = new ACKPacket(CLIENT_CONTEXT).encode();

    assertEquals(ForgeConstants.ACK_PACKET_ID, ProtocolUtils.readVarInt(encoded));
    assertEquals(0, encoded.readableBytes());
  }
}
