package coakka.v2.android

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimePipeTransportTest {
    @Test
    fun frameLengthUsesUnsignedLittleEndianWireOrder() {
        assertArrayEquals(
            byteArrayOf(0x04, 0x03, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00),
            encodeFrameLength(0x01020304),
        )
    }

    @Test
    fun frameLengthMatchesRuntimeEightByteHeader() {
        val header = encodeFrameLength(65537)

        val decoded = ByteBuffer.wrap(header)
            .order(ByteOrder.LITTLE_ENDIAN)
            .long
        assertArrayEquals(
            byteArrayOf(0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00),
            header,
        )
        assertEquals(65537L, decoded)
    }
}
