package org.adde0109.ambassador.forge;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.adde0109.ambassador.forge.packet.Context;
import org.adde0109.ambassador.forge.packet.GenericForgeLoginWrapperPacket;
import org.adde0109.ambassador.forge.packet.IForgeLoginWrapperPacket;

import java.nio.charset.StandardCharsets;

public class ForgeHandshakeUtils {

  public static void writeVarInt(ByteArrayDataOutput stream,int i) {
    while((i & -128) != 0) {
      stream.writeByte(i & 127 | 128);
      i >>>= 7;
    }

    stream.writeByte(i);
  }

  public static void writeUtf(ByteArrayDataOutput stream,String p_211400_1_) {
    byte[] abyte = p_211400_1_.getBytes(StandardCharsets.UTF_8);
      writeVarInt(stream,abyte.length);
      stream.write(abyte);
  }

  public static byte[] generateResetPacket() {
    ByteArrayDataOutput dataAndPacketIdStream = ByteStreams.newDataOutput();
    writeVarInt(dataAndPacketIdStream,98);

    ByteArrayDataOutput stream = ByteStreams.newDataOutput();
    byte[] dataAndPacketId = dataAndPacketIdStream.toByteArray();
    writeUtf(stream,"fml:handshake");
    writeVarInt(stream,dataAndPacketId.length);
    stream.write(dataAndPacketId);
    return stream.toByteArray();
  }
  public static byte[] generatePluginResetPacket() {
    ByteArrayDataOutput dataAndPacketIdStream = ByteStreams.newDataOutput();
    writeVarInt(dataAndPacketIdStream,98);
    return dataAndPacketIdStream.toByteArray();
  }

  public static class ThirdPartyRegistryUtils {

    static enum ThirdPartyChannel {
      SILENTGEAR_NETWORK {
        @Override
        public IForgeLoginWrapperPacket<Context.ClientContext> generateResponsePacket(Context.ClientContext context, ForgeHandshake completed) {
          return new ACKPacket(context, 3);
        }
      },
      ZETA_MAIN {
        @Override
        public IForgeLoginWrapperPacket<Context.ClientContext> generateResponsePacket(Context.ClientContext context, ForgeHandshake completed) {
          return new GenericForgeLoginWrapperPacket<Context.ClientContext>(completed.zetaFlagsPacket.getContent(), context);
        }
      };
      abstract public IForgeLoginWrapperPacket<Context.ClientContext> generateResponsePacket(Context.ClientContext context, ForgeHandshake completed);
    }

    static boolean isThirdPartyPacket(GenericForgeLoginWrapperPacket<?> packet) {
      try {
        Enum.valueOf(ThirdPartyChannel.class,
                packet.getContext().getChannelName().replace(':', '_').toUpperCase());
        return true;
      } catch (IllegalArgumentException e) {
        return false;
      }
    }

    static ThirdPartyChannel getThirdPartyChannel(GenericForgeLoginWrapperPacket<?> packet) {
      return Enum.valueOf(ThirdPartyChannel.class,
              packet.getContext().getChannelName().replace(':', '_').toUpperCase());
    }

    static class ACKPacket implements IForgeLoginWrapperPacket<Context.ClientContext> {

      private final Context.ClientContext context;
      private final int packetID;
      ACKPacket(Context.ClientContext context, int packetID) {
        this.context = context;
        this.packetID = packetID;
      }

      public ByteBuf encode() {
        ByteBuf buf = Unpooled.buffer();

        ProtocolUtils.writeVarInt(buf, packetID);

        return buf;
      }

      @Override
      public Context.ClientContext getContext() {
        return context;
      }
    }
  }
}
