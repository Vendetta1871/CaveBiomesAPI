package net.celestiald.cavebiomes.network;

import io.netty.buffer.ByteBuf;
import net.celestiald.cavebiomes.api.WorldHeightAPI;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server-authoritative finite world range validated during login. */
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
            final NetworkManager connection = context.getClientHandler().getNetworkManager();
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(
                    () -> validateRange(message, connection));
            return null;
        }

        private static void validateRange(WorldHeightMessage message, NetworkManager connection) {
            int minimumY = message.getMinimumY();
            int maximumY = message.getMaximumY();
            if (WorldHeightAPI.isActiveRange(minimumY, maximumY)) {
                return;
            }

            connection.closeChannel(new TextComponentString(
                    "Cave Biomes API world height mismatch: server "
                            + minimumY + ".." + maximumY + ", client "
                            + WorldHeightAPI.getMinY() + ".." + WorldHeightAPI.getMaxY()
                            + ". Match cavesbiomesapi.cfg on both sides and restart the client."));
        }
    }
}
