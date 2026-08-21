package coakka.v2.android

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.Closeable
import java.io.EOFException
import java.io.FileDescriptor
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

internal class LaneCancellation private constructor(
    private val readEnd: ParcelFileDescriptor,
    private val writeEnd: ParcelFileDescriptor,
) : Closeable {
    private val cancelled = AtomicBoolean(false)

    val descriptor: FileDescriptor
        get() = readEnd.fileDescriptor

    fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            runCatching(writeEnd::close)
        }
    }

    override fun close() {
        cancel()
        runCatching(readEnd::close)
    }

    companion object {
        fun create(): LaneCancellation {
            val pipe = ParcelFileDescriptor.createPipe()
            return LaneCancellation(pipe[0], pipe[1])
        }
    }
}

internal class WriteFrameLane(
    private val pfd: ParcelFileDescriptor,
    private val cancellation: LaneCancellation,
) : Closeable {
    private val descriptor = pfd.fileDescriptor
    private val closed = AtomicBoolean(false)

    fun write(payload: ByteArray): Boolean {
        if (closed.get() || payload.size > MAX_FRAME_BYTES) {
            return false
        }
        return synchronized(this) {
            if (closed.get()) {
                return@synchronized false
            }
            try {
                val header = encodeFrameLength(payload.size)
                if (!writeFully(descriptor, header, closed, cancellation.descriptor)) {
                    return@synchronized false
                }
                payload.isEmpty() ||
                    writeFully(descriptor, payload, closed, cancellation.descriptor)
            } catch (failure: IOException) {
                close()
                throw failure
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching(pfd::close)
        }
    }
}

internal class ReadFrameLane(
    private val pfd: ParcelFileDescriptor,
    private val cancellation: LaneCancellation,
) : Closeable {
    private val descriptor = pfd.fileDescriptor
    private val closed = AtomicBoolean(false)

    @Synchronized
    fun read(): ByteArray? {
        if (closed.get()) {
            return null
        }
        try {
            val header = ByteArray(HEADER_BYTES)
            if (!readFully(
                    descriptor,
                    header,
                    allowCleanEof = true,
                    closed = closed,
                    cancellationFd = cancellation.descriptor,
                )
            ) {
                return null
            }
            val length = decodeFrameLength(header)
            require(length in 0..MAX_FRAME_BYTES.toLong()) {
                "invalid runtime frame length=$length"
            }
            val payload = ByteArray(length.toInt())
            if (payload.isNotEmpty()) {
                if (!readFully(
                        descriptor,
                        payload,
                        allowCleanEof = false,
                        closed = closed,
                        cancellationFd = cancellation.descriptor,
                    )
                ) {
                    return null
                }
            }
            return payload
        } catch (failure: IOException) {
            close()
            throw failure
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching(pfd::close)
        }
    }
}

internal class MonitorLane(
    private val pfd: ParcelFileDescriptor,
    private val cancellation: LaneCancellation,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val descriptor = pfd.fileDescriptor

    @Synchronized
    fun consume(): Long {
        if (closed.get()) {
            return 0L
        }
        val result = NativeRuntimeBridge.nativeConsumeMonitor(pfd.fd)
        if (result < 0L) {
            throw CoAkkaNativeException("monitor_consume", result.toInt())
        }
        return result
    }

    @Synchronized
    fun awaitAndConsume(): Long? {
        if (closed.get() ||
            !awaitReady(
                descriptor,
                OsConstants.POLLIN,
                cancellation.descriptor,
                closed,
            )
        ) {
            return null
        }
        return consume()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching(pfd::close)
        }
    }
}

internal fun encodeFrameLength(length: Int): ByteArray {
    require(length >= 0) { "frame length must not be negative" }
    return ByteBuffer.allocate(HEADER_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(length.toLong())
        .array()
}

private fun decodeFrameLength(header: ByteArray): Long =
    ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).long

private fun writeFully(
    fd: FileDescriptor,
    buffer: ByteArray,
    closed: AtomicBoolean,
    cancellationFd: FileDescriptor,
): Boolean {
    var offset = 0
    while (offset < buffer.size) {
        if (closed.get()) {
            return false
        }
        val count = try {
            Os.write(fd, buffer, offset, buffer.size - offset)
        } catch (failure: ErrnoException) {
            when (failure.errno) {
                OsConstants.EINTR -> continue
                OsConstants.EAGAIN -> {
                    if (!awaitReady(fd, OsConstants.POLLOUT, cancellationFd, closed)) {
                        return false
                    }
                    continue
                }
                OsConstants.EBADF, OsConstants.EPIPE -> {
                    if (closed.get()) {
                        return false
                    }
                    throw pipeIOException("write", failure)
                }
                else -> throw pipeIOException("write", failure)
            }
        } catch (failure: InterruptedIOException) {
            Thread.currentThread().interrupt()
            if (closed.get()) {
                return false
            }
            throw failure
        }
        if (count <= 0) {
            throw EOFException("runtime pipe closed while writing frame")
        }
        offset += count
    }
    return true
}

private fun readFully(
    fd: FileDescriptor,
    buffer: ByteArray,
    allowCleanEof: Boolean,
    closed: AtomicBoolean,
    cancellationFd: FileDescriptor,
): Boolean {
    var offset = 0
    var readAny = false
    while (offset < buffer.size) {
        if (closed.get()) {
            return false
        }
        val count = try {
            Os.read(fd, buffer, offset, buffer.size - offset)
        } catch (failure: ErrnoException) {
            when (failure.errno) {
                OsConstants.EINTR -> continue
                OsConstants.EAGAIN -> {
                    if (!awaitReady(fd, OsConstants.POLLIN, cancellationFd, closed)) {
                        return false
                    }
                    continue
                }
                OsConstants.EBADF -> {
                    if (closed.get()) {
                        return false
                    }
                    throw pipeIOException("read", failure)
                }
                else -> throw pipeIOException("read", failure)
            }
        } catch (failure: InterruptedIOException) {
            Thread.currentThread().interrupt()
            if (closed.get()) {
                return false
            }
            throw failure
        }
        if (count == 0) {
            if (allowCleanEof && !readAny) {
                return false
            }
            throw EOFException("runtime pipe closed inside frame")
        }
        offset += count
        readAny = true
    }
    return true
}

private fun awaitReady(
    fd: FileDescriptor,
    event: Int,
    cancellationFd: FileDescriptor,
    closed: AtomicBoolean,
): Boolean {
    val dataPoll = StructPollfd().apply {
        this.fd = fd
        events = event.toShort()
    }
    val cancellationPoll = StructPollfd().apply {
        this.fd = cancellationFd
        events = OsConstants.POLLIN.toShort()
    }
    val pollFds = arrayOf(dataPoll, cancellationPoll)
    while (!closed.get()) {
        dataPoll.revents = 0
        cancellationPoll.revents = 0
        try {
            Os.poll(pollFds, -1)
        } catch (failure: ErrnoException) {
            if (failure.errno == OsConstants.EINTR) {
                continue
            }
            if (closed.get() && failure.errno == OsConstants.EBADF) {
                return false
            }
            throw pipeIOException("poll", failure)
        }

        val cancellationEvents = cancellationPoll.revents.toInt()
        if (cancellationEvents and (OsConstants.POLLIN or POLL_TERMINAL_EVENTS) != 0) {
            return false
        }
        val dataEvents = dataPoll.revents.toInt()
        if (dataEvents and (event or POLL_TERMINAL_EVENTS) != 0) {
            return true
        }
    }
    return false
}

private fun pipeIOException(operation: String, failure: ErrnoException): IOException =
    IOException("runtime pipe $operation failed: ${Os.strerror(failure.errno)}", failure)

private const val HEADER_BYTES = 8
private const val MAX_FRAME_BYTES = 16 * 1024 * 1024
private val POLL_TERMINAL_EVENTS =
    OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL
