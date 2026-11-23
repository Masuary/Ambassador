package org.adde0109.ambassador.forge.packet;

import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.adde0109.ambassador.forge.ForgeConstants;

public class ACKPacket implements IForgeLoginWrapperPacket<Context.ClientContext> {

    private final Context.ClientContext context;

    public ACKPacket(Context.ClientContext context) {
        this.context = context;
    }
    @Override
    public ByteBuf encode() {
        ByteBuf buf = Unpooled.buffer();

        ProtocolUtils.writeVarInt(buf, ForgeConstants.ACK_PACKET_ID);

        return buf;
    }

    @Override
    public Context.ClientContext getContext() {
        return context;
    }
}
