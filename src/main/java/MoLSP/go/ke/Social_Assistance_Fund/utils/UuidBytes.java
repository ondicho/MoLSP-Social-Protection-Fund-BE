package MoLSP.go.ke.Social_Assistance_Fund.utils;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UuidBytes {
    private UuidBytes() {
    }

    public static byte[] toBytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
    }

    public static UUID fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
