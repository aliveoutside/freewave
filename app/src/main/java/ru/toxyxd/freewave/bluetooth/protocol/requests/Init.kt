package ru.toxyxd.freewave.bluetooth.protocol.requests

import ru.toxyxd.freewave.bluetooth.protocol.MessageType
import ru.toxyxd.freewave.bluetooth.protocol.Request

class Init : HeadphoneFeature, WriteableFeature<Init> {
    override fun toSetRequest(): Request {
        return Request(MessageType.COMMAND_1, byteArrayOf(0x00, 0x00))
    }

    companion object : ReadableFeature<Init> {
        override fun toGetRequest(): Request {
            return Request(MessageType.COMMAND_1, byteArrayOf(0x00, 0x00))
        }

        override fun parseResponse(response: ByteArray): Init {
            return Init()
        }
    }
}