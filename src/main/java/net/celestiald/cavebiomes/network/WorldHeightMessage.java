package net.celestiald.cavebiomes.network;

import io.netty.buffer.ByteBuf;
import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server-authoritative finite world range sent before chunk data begins streaming. */
public final class WorldHeightMessage implements IMessage {
    private int minimumY;
    private int maximumY;

    public WorldHeightMessage() {}

    public WorldHeightMessage(int minimumY, int maximumY) {
        this.minimumY = minimumY;
        this.maximumY = maximumY;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        minimumY = buffer.readInt();
        maximumY = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(minimumY);
        buffer.writeInt(maximumY);
    }

    public int getMinimumY() {
        return minimumY;
    }

    public int getMaximumY() {
        return maximumY;
    }

    public static final class Handler implements IMessageHandler<WorldHeightMessage, IMessage> {
        @Override
        public IMessage onMessage(final WorldHeightMessage message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(
                    () -> WorldHeightAPI.applyServerRange(
                            message.getMinimumY(), message.getMaximumY()));
            return null;
        }
    }
}
