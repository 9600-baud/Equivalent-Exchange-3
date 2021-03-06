package com.pahimar.ee3.network.message;

import com.google.gson.JsonParseException;
import com.pahimar.ee3.api.blacklist.BlacklistRegistryProxy;
import com.pahimar.ee3.blacklist.BlacklistRegistry;
import com.pahimar.ee3.exchange.WrappedStack;
import com.pahimar.ee3.util.CompressionUtils;
import com.pahimar.ee3.util.LogHelper;
import com.pahimar.ee3.util.SerializationHelper;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Set;

import static com.pahimar.ee3.api.blacklist.BlacklistRegistryProxy.Blacklist;

public class MessageSyncBlacklist implements IMessage {

    public Blacklist blacklist;
    public Set<WrappedStack> blacklistSet;

    public MessageSyncBlacklist() {
    }

    public MessageSyncBlacklist(Blacklist blacklist) {

        this.blacklist = blacklist;
        this.blacklistSet = BlacklistRegistryProxy.getBlacklist(blacklist);
    }

    @Override
    public void fromBytes(ByteBuf buf) {

        int blacklistOrdinal = buf.readInt();

        if (blacklistOrdinal == 0) {
            blacklist = Blacklist.KNOWLEDGE;
        }
        else if (blacklistOrdinal == 1) {
            blacklist = Blacklist.EXCHANGE;
        }
        else {
            blacklist = null;
        }

        if (blacklist != null) {

            int compressedJsonLength = buf.readInt();
            if (compressedJsonLength != 0) {

                byte[] compressedBlacklist = buf.readBytes(compressedJsonLength).array();

                if (compressedBlacklist != null) {

                    String jsonBlacklist = CompressionUtils.decompress(compressedBlacklist);

                    try {
                        blacklistSet = SerializationHelper.GSON.fromJson(jsonBlacklist, SerializationHelper.WRAPPED_STACK_SET_TYPE);
                    }
                    catch (JsonParseException e) {
                        LogHelper.warn("Failed to receive {} blacklist data from server", blacklist);
                        blacklistSet = null;
                    }
                }
                else {
                    blacklistSet = null;
                }
            }
        }
        else {
            blacklistSet = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {

        if (blacklist != null) {

            buf.writeInt(blacklist.ordinal());

            if (blacklistSet != null) {

                byte[] compressedBlacklist = CompressionUtils.compress(SerializationHelper.GSON.toJson(blacklistSet, SerializationHelper.WRAPPED_STACK_SET_TYPE));

                if (compressedBlacklist != null) {
                    buf.writeInt(compressedBlacklist.length);
                    buf.writeBytes(compressedBlacklist);
                }
                else {
                    buf.writeInt(0);
                }
            }
            else {
                buf.writeInt(0);
            }
        }
        else {
            buf.writeInt(-1);
        }
    }

    public static class MessageHandler implements IMessageHandler<MessageSyncBlacklist, IMessage> {

        @Override
        public IMessage onMessage(MessageSyncBlacklist message, MessageContext ctx) {

            if (message.blacklist != null && message.blacklistSet != null) {
                BlacklistRegistry.INSTANCE.load(message.blacklistSet, message.blacklist);
            }

            return null;
        }
    }
}
