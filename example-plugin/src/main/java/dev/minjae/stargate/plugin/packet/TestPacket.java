package dev.minjae.stargate.plugin.packet;

import alemiz.stargate.protocol.StarGatePacket;
import alemiz.stargate.protocol.types.PacketHelper;
import io.netty.buffer.ByteBuf;

public class TestPacket extends StarGatePacket {

    public static final byte PACKET_ID = 48;

    private String someField;
    private boolean someField2;

    @Override
    public void encodePayload(ByteBuf buf) {
        PacketHelper.writeString(buf, someField);
        buf.writeBoolean(someField2);
    }

    @Override
    public void decodePayload(ByteBuf buf) {
        someField = PacketHelper.readString(buf);
        someField2 = buf.readBoolean();
    }

    @Override
    public byte getPacketId() {
        return PACKET_ID;
    }
}
