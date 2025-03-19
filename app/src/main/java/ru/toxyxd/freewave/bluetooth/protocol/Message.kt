package ru.toxyxd.freewave.bluetooth.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

const val MESSAGE_HEADER: Byte = 0x3e
const val MESSAGE_TRAILER: Byte = 0x3c
const val MESSAGE_ESCAPE: Byte = 0x3d
const val MESSAGE_ESCAPE_MASK: Byte = 0b11101111.toByte()

data class Message(
    val type: MessageType,
    val sequenceNumber: Byte,
    val payload: ByteArray
) {
    fun encode(): ByteArray {
        val buf = ByteBuffer.allocate(payload.size + 6).apply {
            order(ByteOrder.BIG_ENDIAN)
            put(type.code)
            put(sequenceNumber)
            putInt(payload.size)
            put(payload)
        }

        return encodeMessage(buf.array())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (type != other.type) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String {
        return if (payload.isNotEmpty()) {
            "Message{Cmd=$type, Seq=$sequenceNumber, PayloadType=${PayloadType.fromCode(payload[0])}, Payload=${payload.joinToString("") { "%02x".format(it) }}}"
        } else {
            "Message{Cmd=$type, Seq=$sequenceNumber}"
        }
    }

    companion object {
        fun fromBytes(rawBytes: ByteArray): Message? {
            if (rawBytes[0] != MESSAGE_HEADER) {
                throw IllegalArgumentException("Invalid header ${"%02x".format(rawBytes[0])}")
            }

            if (rawBytes[rawBytes.size - 1] != MESSAGE_TRAILER) {
                throw IllegalArgumentException("Invalid trailer ${"%02x".format(rawBytes[0])}")
            }

            val messageBytes = unescape(rawBytes)

            val messageChecksum = messageBytes[messageBytes.size - 2]
            val expectedChecksum = calcChecksum(messageBytes, 1, messageBytes.size - 2)
            if (messageChecksum != expectedChecksum) {
                return null
            }

            val payloadLength = ((messageBytes[3].toInt() and 0xFF) shl 24) or
                               ((messageBytes[4].toInt() and 0xFF) shl 16) or
                               ((messageBytes[5].toInt() and 0xFF) shl 8) or
                               (messageBytes[6].toInt() and 0xFF)

            if (payloadLength != messageBytes.size - 9) {
                return null
            }

            val rawMessageType = messageBytes[1]
            val messageType = MessageType.fromCode(rawMessageType)

            val sequenceNumber = messageBytes[2]
            val payload = ByteArray(payloadLength)
            System.arraycopy(messageBytes, 7, payload, 0, payloadLength)

            return Message(messageType, sequenceNumber, payload)
        }

        fun encodeMessage(message: ByteArray): ByteArray {
            val cmdStream = ByteArrayOutputStream(message.size + 2)

            cmdStream.write(MESSAGE_HEADER.toInt())

            val checksum = calcChecksum(message, 0, message.size)

            cmdStream.write(escape(message))
            cmdStream.write(escape(byteArrayOf(checksum)))
            cmdStream.write(MESSAGE_TRAILER.toInt())

            return cmdStream.toByteArray()
        }

        fun escape(bytes: ByteArray): ByteArray {
            val escapedStream = ByteArrayOutputStream(bytes.size)

            for (b in bytes) {
                when (b) {
                    MESSAGE_HEADER, MESSAGE_TRAILER, MESSAGE_ESCAPE -> {
                        escapedStream.write(MESSAGE_ESCAPE.toInt())
                        escapedStream.write((b and MESSAGE_ESCAPE_MASK).toInt())
                    }
                    else -> escapedStream.write(b.toInt())
                }
            }

            return escapedStream.toByteArray()
        }

        fun unescape(bytes: ByteArray): ByteArray {
            val unescapedStream = ByteArrayOutputStream(bytes.size)

            var i = 0
            while (i < bytes.size) {
                val b = bytes[i]
                if (b == MESSAGE_ESCAPE) {
                    if (++i >= bytes.size) {
                        throw IllegalArgumentException("Invalid escape character at end of array")
                    }
                    unescapedStream.write((bytes[i] or MESSAGE_ESCAPE_MASK.inv()).toInt())
                } else {
                    unescapedStream.write(b.toInt())
                }
                i++
            }

            return unescapedStream.toByteArray()
        }

        fun calcChecksum(message: ByteArray, start: Int, end: Int): Byte {
            var chk = 0
            for (i in start until end) {
                chk += message[i].toInt() and 0xFF
            }
            return chk.toByte()
        }

        fun hexToBytes(payloadHex: String): ByteArray {
            val parts = payloadHex.split(":")
            val stream = ByteArrayOutputStream()

            for (b in parts) {
                stream.write(((b[0].digitToInt(16) shl 4) + b[1].digitToInt(16)).toByte().toInt())
            }

            return stream.toByteArray()
        }
    }
}