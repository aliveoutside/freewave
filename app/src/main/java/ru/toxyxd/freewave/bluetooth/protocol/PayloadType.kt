package ru.toxyxd.freewave.bluetooth.protocol

enum class PayloadType(val messageType: MessageType, val code: Byte) {
    INIT_REPLY(MessageType.COMMAND_1, 0x01),
    AMBIENT_SOUND_CONTROL_GET(MessageType.COMMAND_1, 0x66),
    AMBIENT_SOUND_CONTROL_RET(MessageType.COMMAND_1, 0x67),
    AMBIENT_SOUND_CONTROL_SET(MessageType.COMMAND_1, 0x68),
    AMBIENT_SOUND_CONTROL_NOTIFY(MessageType.COMMAND_1, 0x69),

    UNKNOWN(MessageType.UNKNOWN, 0x00);

    companion object {
        fun fromCode(code: Byte): PayloadType {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}