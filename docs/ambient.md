# Sony WH-1000XM4 Bluetooth Protocol: Ambient Sound Control

This document describes the "Ambient Sound Control" command within the Sony WH-1000XM4 Bluetooth protocol, used over Bluetooth Classic (RFCOMM). It enables control over noise cancellation modes and ambient sound levels. For general communication details, see [Bluetooth Communication](bluetooth_communication.md).

## Overview

The "Ambient Sound Control" feature manages noise cancellation and ambient sound settings. It supports SET commands to configure the state, GET commands to query it, and NOTIFY messages to report changes.

## Request Format

### SET Command
Configures the "Ambient Sound Control" state.

| Position | Description         | Size   | Values                                       |
|----------|---------------------|--------|----------------------------------------------|
| 0        | Payload Type        | 1 byte | `0x68` (SET)                                 |
| 1        | Version/Parameter   | 1 byte | `0x02`                                       |
| 2        | Mode Flag           | 1 byte | `0x00` (OFF), `0x11` (others)                |
| 3        | Wind Noise Support  | 1 byte | `0x02` (yes), `0x00` (no)                    |
| 4        | Submode             | 1 byte | `0x00` (Ambient), `0x01` (Wind), `0x02` (NC) |
| 5        | Unknown             | 1 byte | Typically `0x01`                             |
| 6        | Focus on Voice      | 1 byte | `0x00` (off), `0x01` (on)                    |
| 7        | Ambient Sound Level | 1 byte | `0x00`-`0x14` (0-20)                         |

#### Example: Set "Ambient Sound" with Level 14
[62, 0c, 01, 00, 00, 00, 08, 68, 02, 11, 02, 00, 01, 00, 0e, a5, 3c]
- `62` (`0x3E`) — header.
- `0c` — message type (COMMAND_1).
- `01` — sequence number.
- `00, 00, 00, 08` — payload length (8).
- `68, 02, 11, 02, 00, 01, 00, 0e` — payload.
- `a5` — checksum (example).
- `3c` (`0x3C`) — trailer.

### GET Command
Queries the current state.

| Position | Description            | Size   | Values                            |
|----------|------------------------|--------|-----------------------------------|
| 0        | Payload Type           | 1 byte | `0x66` (GET)                      |
| 1        | Version/Parameter      | 1 byte | `0x02`                            |

#### Example: Get Current State
[62, 0c, 01, 00, 00, 00, 02, 66, 02, 6a, 3c]
- `62` (`0x3E`) — header.
- `0c` — message type (COMMAND_1).
- `01` — sequence number.
- `00, 00, 00, 02` — payload length (2).
- `66, 02` — payload.
- `6a` — checksum (example).
- `3c` (`0x3C`) — trailer.

## Notification Format

Reports the current state in response to SET/GET or spontaneous changes.

| Position | Description         | Size   | Values                                       |
|----------|---------------------|--------|----------------------------------------------|
| 0        | Payload Type        | 1 byte | `0x69` (NOTIFY)                              |
| 1        | Version/Parameter   | 1 byte | `0x02`                                       |
| 2        | Mode Flag           | 1 byte | `0x00` (OFF), `0x01` (others)                |
| 3        | Wind Noise Support  | 1 byte | `0x02` (yes), `0x00` (no)                    |
| 4        | Submode             | 1 byte | `0x00` (Ambient), `0x01` (Wind), `0x02` (NC) |
| 5        | Unknown             | 1 byte | Typically `0x01`                             |
| 6        | Focus on Voice      | 1 byte | `0x00` (off), `0x01` (on)                    |
| 7        | Ambient Sound Level | 1 byte | `0x00`-`0x14` (0-20)                         |

### Example Notifications

#### "OFF" Mode
[62, 0c, 00, 00, 00, 00, 08, 69, 02, 00, 02, 00, 01, 00, 01, 83, 3c]
- `62` (`0x3E`) — header.
- `0c` — message type (COMMAND_1).
- `00` — sequence number.
- `00, 00, 00, 08` — payload length (8).
- `69, 02, 00, 02, 00, 01, 00, 01` — payload.
- `83` — checksum.
- `3c` (`0x3C`) — trailer.

#### "Ambient Sound" with Level 1
[62, 0c, 00, 00, 00, 00, 08, 69, 02, 01, 02, 00, 01, 00, 01, 83, 3c]
- `62` (`0x3E`) — header.
- `0c` — message type (COMMAND_1).
- `00` — sequence number.
- `00, 00, 00, 08` — payload length (8).
- `69, 02, 01, 02, 00, 01, 00, 01` — payload.
- `83` — checksum.
- `3c` (`0x3C`) — trailer.

## Modes and Submodes

- **OFF**: Noise cancellation and ambient sound disabled.
    - Mode Flag: `0x00`
    - Submode: `0x00`
- **NOISE_CANCELLING**: Full noise cancellation.
    - Mode Flag: `0x11` (SET), `0x01` (NOTIFY)
    - Submode: `0x02`
- **WIND_NOISE_REDUCTION**: Noise cancellation optimized for wind.
    - Mode Flag: `0x11` (SET), `0x01` (NOTIFY)
    - Submode: `0x01`
- **AMBIENT_SOUND**: Ambient sound mode with adjustable level (0-20).
    - Mode Flag: `0x11` (SET), `0x01` (NOTIFY)
    - Submode: `0x00`

## Protocol Details

- **Ambient Sound Level**: Applicable only in "AMBIENT_SOUND" mode (submode `0x00`). Ranges from 0 to 20 (`0x00`-`0x14`). Ignored in other modes.
- **Wind Noise Support**: Indicates support for wind noise reduction (`0x02` for yes, `0x00` for no). Must be verified per device model.
- **Mode Flag Discrepancy**: SET uses `0x11` for enabled modes, while NOTIFY uses `0x01`. Implementations must account for this.
- **Unknown Byte (Position 5)**: Typically `0x01`, but its purpose is unclear.

## Notes

- The headphones may reset the ambient sound level to 1 (`0x01`) when switching to "OFF".
- Notifications can occur due to manual interactions (e.g., button presses on the headphones).

## Limitations

- The role of position 5 remains unknown and requires further analysis.
- Behavior may vary with firmware updates or across Sony models.

---