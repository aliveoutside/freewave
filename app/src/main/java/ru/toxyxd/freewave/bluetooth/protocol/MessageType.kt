package ru.toxyxd.freewave.bluetooth.protocol

enum class MessageType(val code: Byte) {
    ACK(0x01),
    COMMAND_1(0x0c),
    COMMAND_2(0x0e),
    UNKNOWN(0xff.toByte());

    companion object {
        fun fromCode(code: Byte): MessageType {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}
