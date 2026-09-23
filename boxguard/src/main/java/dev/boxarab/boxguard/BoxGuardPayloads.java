package dev.boxarab.boxguard;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class BoxGuardPayloads {
    private BoxGuardPayloads() {}

    public static final Identifier SUSPECT_SYNC_ID = Identifier.fromNamespaceAndPath(BoxGuard.MOD_ID, "suspect_sync");
    public static final Identifier REQUEST_SUSPECTS_ID = Identifier.fromNamespaceAndPath(BoxGuard.MOD_ID, "request_suspects");

    public record SuspectEntry(String name, int score, String flags) {
        public static final StreamCodec<ByteBuf, SuspectEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), SuspectEntry::name,
                ByteBufCodecs.VAR_INT, SuspectEntry::score,
                ByteBufCodecs.stringUtf8(128), SuspectEntry::flags,
                SuspectEntry::new
        );
    }

    public record SuspectSyncPayload(List<SuspectEntry> entries) implements CustomPacketPayload {
        public static final Type<SuspectSyncPayload> TYPE = new Type<>(SUSPECT_SYNC_ID);
        public static final StreamCodec<ByteBuf, List<SuspectEntry>> LIST_CODEC = SuspectEntry.CODEC.apply(ByteBufCodecs.list(64));
        public static final StreamCodec<ByteBuf, SuspectSyncPayload> CODEC = LIST_CODEC.map(SuspectSyncPayload::new, SuspectSyncPayload::entries);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestSuspectsPayload() implements CustomPacketPayload {
        public static final Type<RequestSuspectsPayload> TYPE = new Type<>(REQUEST_SUSPECTS_ID);
        public static final StreamCodec<ByteBuf, RequestSuspectsPayload> CODEC = StreamCodec.unit(new RequestSuspectsPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
