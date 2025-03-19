package ru.toxyxd.freewave.bluetooth.protocol.requests

import ru.toxyxd.freewave.bluetooth.protocol.PayloadType
import ru.toxyxd.freewave.bluetooth.protocol.Request

data class AmbientControl(
    val mode: Mode,
    val focusOnVoice: Boolean,
    val ambientSound: Int,
) : HeadphoneFeature, WriteableFeature<AmbientControl> {
    enum class Mode {
        OFF, NOISE_CANCELLING, WIND_NOISE_REDUCTION, AMBIENT_SOUND
    }

    override fun toSetRequest(): Request {
        val payload = ByteArray(8).apply {
            this[0] = PayloadType.AMBIENT_SOUND_CONTROL_SET.code
            this[1] = 0x02

            this[2] = if (mode == Mode.OFF) 0x00 else 0x11

            val supportsWindNoiseCancelling = true // This should be determined elsewhere

            if (supportsWindNoiseCancelling) {
                this[3] = 0x02

                this[4] = when (mode) {
                    Mode.NOISE_CANCELLING -> 0x02
                    Mode.WIND_NOISE_REDUCTION -> 0x01
                    Mode.OFF, Mode.AMBIENT_SOUND -> 0x00
                }
            } else {
                this[3] = 0x00

                this[4] = if (mode == Mode.NOISE_CANCELLING) 0x01 else 0x00
            }

            this[5] = 0x01
            this[6] = if (focusOnVoice) 0x01 else 0x00
            this[7] = when (mode) {
                Mode.OFF, Mode.AMBIENT_SOUND -> ambientSound.toByte()
                Mode.WIND_NOISE_REDUCTION, Mode.NOISE_CANCELLING -> 0x00
            }
        }

        return Request(
            PayloadType.AMBIENT_SOUND_CONTROL_SET.messageType,
            payload
        )
    }

    companion object : ReadableFeature<AmbientControl> {
        override fun toGetRequest(): Request = Request(
            PayloadType.AMBIENT_SOUND_CONTROL_GET.messageType,
            byteArrayOf(PayloadType.AMBIENT_SOUND_CONTROL_GET.code, 0x02)
        )

        override fun parseResponse(response: ByteArray): AmbientControl {
            if (response.size < 6 || response.size > 8) {
                throw IllegalArgumentException("Invalid response size: ${response.size}")
            }

            val mode: Mode
            var focusOnVoice = false
            var ambientSound = 0

            val isEnabled = response[2] != 0x00.toByte()
            val supportsWindReduction = response[3] == 0x02.toByte()

            // Parse mode
            if (!isEnabled) {
                mode = Mode.OFF
            } else {
                mode = if (supportsWindReduction) {
                    when (response[4].toInt() and 0xFF) {
                        0x00 -> Mode.AMBIENT_SOUND
                        0x01 -> Mode.WIND_NOISE_REDUCTION
                        0x02 -> Mode.NOISE_CANCELLING
                        else -> throw IllegalArgumentException("Invalid mode: ${response[4]}")
                    }
                } else {
                    if (response[4] == 0x00.toByte()) {
                        Mode.AMBIENT_SOUND
                    } else {
                        Mode.NOISE_CANCELLING
                    }
                }
            }
            if (response.size >= 7) {
                focusOnVoice = response[6] == 0x01.toByte()
            }

            if (response.size >= 8) {
                ambientSound = response[7].toInt() and 0xFF
            }

            return AmbientControl(mode, focusOnVoice, ambientSound)
        }
    }
}

