# Sony WH-1000XM4 Bluetooth Communication

This document provides an overview of how the Sony WH-1000XM4 headphones communicate with a device over Bluetooth Classic (RFCOMM). It serves as a foundation for understanding specific command types detailed in separate documents.

## Overview

The Sony WH-1000XM4 headphones use Bluetooth Classic with an RFCOMM (Serial Port Profile) connection to exchange commands and notifications with a paired device. This enables control over features such as noise cancellation and ambient sound settings. Communication follows a request-response pattern, with the headphones also sending unsolicited notifications for state changes.

### Connection Details

- **UUID**: The headphones expose an RFCOMM service with the UUID `96CC203E-5068-46ad-B32D-E316F5E069BA`.
- **Transport**: Commands and notifications are transmitted over a Bluetooth socket using input and output streams.
- **Framing**: Messages are framed with a header byte (`0x3E`) and a trailer byte (`0x3C`).

### Message Structure

All messages (requests and notifications) are byte arrays with the following structure:

| Position  | Description     | Size    | Values                                              |
|-----------|-----------------|---------|-----------------------------------------------------|
| 0         | Header          | 1 byte  | `0x3E`                                              |
| 1         | Message Type    | 1 byte  | e.g., `0x0C` (COMMAND_1), `0x01` (ACK)              |
| 2         | Sequence Number | 1 byte  | `0x00`-`0xFF`                                       |
| 3-6       | Payload Length  | 4 bytes | Big-endian integer (e.g., `0x00, 0x00, 0x00, 0x08`) |
| 7-(7+n-1) | Payload         | n bytes | Command-specific data                               |
| 7+n       | Checksum        | 1 byte  | Calculated (sum of bytes)                           |
| 8+n       | Trailer         | 1 byte  | `0x3C`                                              |

- **Total Length**: `9 + payloadLength` bytes.
- **Checksum**: Calculated as the sum of all bytes from `Message Type` to `Payload` (inclusive), modulo 256.

#### Example Raw Message
[62, 0c, 00, 00, 00, 00, 08, 68, 02, 11, 02, 00, 01, 00, 0e, a5, 3c]
- `62` (`0x3E`) — header.
- `0c` — message type (COMMAND_1).
- `00` — sequence number.
- `00, 00, 00, 08` — payload length (8 bytes).
- `68, 02, 11, 02, 00, 01, 00, 0e` — payload.
- `a5` — checksum (example value).
- `3c` (`0x3C`) — trailer.

### Communication Flow

1. **Connection**: The device establishes an RFCOMM socket to the headphones using the specified UUID.
2. **Request**: The device sends a command (e.g., SET or GET) with a sequence number.
3. **Acknowledgment (ACK)**: The headphones respond with an ACK message, where the sequence number is XORed with `1`.
4. **Notification**: For SET commands or state changes, the headphones send a NOTIFY message with the updated state.
5. **Response**: For GET commands, the headphones send a NOTIFY message with the requested data.

### Example Message Sequence

#### Set Command
- **Request**: `[62, 0c, 01, 00, 00, 00, 08, 68, 02, 11, 02, 00, 01, 00, 0e, a5, 3c]`
    - `0c` — COMMAND_1.
    - `01` — sequence number.
    - `00, 00, 00, 08` — payload length (8).
    - `68, 02, 11, 02, 00, 01, 00, 0e` — payload (SET Ambient Sound, level 14).
- **ACK**: `[62, 01, 00, 00, 00, 00, 00, 01, 3c]`
    - `01` — ACK type.
    - `00` — sequence number XORed with 1.
- **Notification**: `[62, 0c, 00, 00, 00, 00, 08, 69, 02, 01, 02, 00, 01, 00, 01, 83, 3c]`
    - `0c` — COMMAND_1.
    - `00` — sequence number.
    - `69` — NOTIFY code.

### Command Types

Specific commands are documented in separate files:
- [Ambient Sound Control](ambient.md)
- *(Additional command types to be added as separate documents)*

## Notes

- The sequence number increments with each request and is used to correlate responses.
- Notifications may occur asynchronously due to physical interactions (e.g., button presses on the headphones).
- The protocol includes an escape mechanism for special bytes (`0x3E`, `0x3C`, `0x3D`), which must be handled during encoding/decoding.

---
