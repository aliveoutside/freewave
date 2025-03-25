package ru.toxyxd.freewave.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.toxyxd.freewave.bluetooth.protocol.MESSAGE_HEADER
import ru.toxyxd.freewave.bluetooth.protocol.MESSAGE_TRAILER
import ru.toxyxd.freewave.bluetooth.protocol.Message
import ru.toxyxd.freewave.bluetooth.protocol.MessageType
import ru.toxyxd.freewave.bluetooth.protocol.PayloadType
import ru.toxyxd.freewave.bluetooth.protocol.Request
import ru.toxyxd.freewave.bluetooth.protocol.requests.AmbientControl
import ru.toxyxd.freewave.bluetooth.protocol.requests.HeadphoneFeature
import ru.toxyxd.freewave.bluetooth.protocol.requests.ReadableFeature
import ru.toxyxd.freewave.bluetooth.protocol.requests.WriteableFeature
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException
import kotlin.experimental.xor

object BluetoothController {
    private val TAG = "BluetoothController"
    private val SONY_V1_UUID = UUID.fromString("96CC203E-5068-46ad-B32D-E316F5E069BA")
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var sequenceNumber: Byte = 0

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var listenerJob: Job? = null
    private val isListening = AtomicBoolean(false)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _eventsFlow = MutableSharedFlow<HeadphoneFeature>()
    val eventsFlow = _eventsFlow.asSharedFlow()

    val connection = object : BluetoothConnection {
        override suspend fun send(request: Request) = withContext(Dispatchers.IO) {
            val currentSeq = sequenceNumber
            Log.i(TAG, "send: sending request: $request with seq: $currentSeq")

            suspendCancellableCoroutine<Unit> { continuation ->
                try {
                    writeToSocket(request.encode(currentSeq))
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "connect: connecting to headphones")
            _connectionState.emit(ConnectionState.Connecting)

            socket = device.createRfcommSocketToServiceRecord(SONY_V1_UUID)
            socket?.connect()

            if (socket?.isConnected == true) {
                Log.v(TAG, "connect: socket connected")
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream

                sequenceNumber = 0

                coroutineScope.launch { startListenerThread() }

                _connectionState.emit(ConnectionState.Connected)
                Log.i(TAG, "connect: connected to headphones")
                return@withContext true
            } else {
                Log.e(TAG, "connect: socket not connected")
                _connectionState.emit(ConnectionState.Disconnected)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "connect: error connecting to headphones", e)
            _connectionState.emit(ConnectionState.Disconnected)
            return@withContext false
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect: disconnecting from headphones")
        stopListenerJob()

        try {
            socket?.close()
            inputStream?.close()
            outputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "disconnect: error closing socket", e)
        } finally {
            socket = null
            inputStream = null
            outputStream = null

            _connectionState.tryEmit(ConnectionState.Disconnected)
            Log.d(TAG, "disconnect: sockets closed")
        }
    }

    suspend inline fun <reified T : HeadphoneFeature> get(feature: ReadableFeature<T>) {
        val request = feature.toGetRequest()
        connection.send(request)
    }

    suspend inline fun <reified T> set(feature: T) where T : HeadphoneFeature, T : WriteableFeature<T> {
        val request = feature.toSetRequest()
        connection.send(request)
    }

    private suspend fun startListenerThread() = coroutineScope {
        isListening.set(true)
        listenerJob = launch {
            try {
                while (isListening.get() && socket?.isConnected == true) {
                    val message = readMessage()
                    Log.d(TAG, "listenerThread: received message: $message")

                    when (message.type) {
                        MessageType.ACK -> {
                            val originalSeq = message.sequenceNumber.xor(1)
                            Log.d(
                                TAG,
                                "listenerThread: received ack ${message.sequenceNumber} for $originalSeq"
                            )

                            sequenceNumber = message.sequenceNumber
                        }

                        else -> {
                            sendAck(message.sequenceNumber)
                            withContext(Dispatchers.Main) {
                                parseAndDispatchMessage(message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listenerThread: error reading message", e)
                    _connectionState.tryEmit(ConnectionState.Disconnected)
            } finally {
                isListening.set(false)
                disconnect()
            }
        }
    }

    private suspend fun parseAndDispatchMessage(message: Message) {
        val payloadType = PayloadType.fromCode(message.payload[0])
        val feature = when (payloadType) {
            PayloadType.AMBIENT_SOUND_CONTROL_RET,
            PayloadType.AMBIENT_SOUND_CONTROL_NOTIFY,
                -> AmbientControl.parseResponse(message.payload)

            else -> null
        }

        feature?.let {
            _eventsFlow.emit(it)
        }
    }

    private fun stopListenerJob() {
        isListening.set(false)
        listenerJob?.cancel("Listener job cancelled")
        listenerJob = null
    }

    private fun readMessage(): Message {
        val rawMessage = readFromSocket()
        val message = Message.fromBytes(rawMessage)
        return message ?: throw IllegalStateException("Invalid message format")
    }

    private fun readFromSocket(): ByteArray {
        Log.v(TAG, "readFromSocket: reading from socket")
        val msgStream = ByteArrayOutputStream()
        val incoming = ByteArray(1)

        do {
            val bytesRead = inputStream?.read(incoming) ?: -1
            if (bytesRead == -1) break

            if (incoming[0] == MESSAGE_HEADER) msgStream.reset()
            msgStream.write(incoming)
        } while (incoming[0] != MESSAGE_TRAILER)

        Log.v(
            TAG, "readFromSocket: raw message: ${msgStream.toByteArray().joinToString(",")}, msg: ${
                Message.fromBytes(msgStream.toByteArray())
            }"
        )

        return msgStream.toByteArray()
    }

    private fun writeToSocket(bytes: ByteArray) {
        try {
            outputStream?.write(bytes)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "writeToSocket: error writing to socket", e)
            _connectionState.tryEmit(ConnectionState.Disconnected)
            disconnect()
        }
    }

    private fun sendAck(sequenceNumber: Byte) {
        val ackSeq = sequenceNumber xor 1
        Log.d(
            TAG, "sendAck: sending ack ${sequenceNumber xor 1} for sequence number $sequenceNumber"
        )
        val ackMessage = Message(
            MessageType.ACK, ackSeq, byteArrayOf()
        )
        writeToSocket(ackMessage.encode())
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
    }
}

interface BluetoothConnection {
    suspend fun send(request: Request)
}