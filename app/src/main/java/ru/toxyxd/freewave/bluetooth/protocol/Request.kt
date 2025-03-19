package ru.toxyxd.freewave.bluetooth.protocol


data class Request(
    val messageType: MessageType,
    val payload: ByteArray
) {
    constructor(messageType: MessageType, vararg payload: Int) : this(
        messageType,
        payload.map { it.toByte() }.toByteArray()
    )

    fun encode(sequenceNumber: Byte): ByteArray {
        return toMessage(sequenceNumber).encode()
    }

    private fun toMessage(sequenceNumber: Byte): Message {
        return Message(messageType, sequenceNumber, payload)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Request

        if (messageType != other.messageType) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageType.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}