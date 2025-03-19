package ru.toxyxd.freewave.bluetooth.protocol.requests

import ru.toxyxd.freewave.bluetooth.protocol.Request

interface HeadphoneFeature

interface ReadableFeature<T: HeadphoneFeature> : Getable, ResponseParser<T>
interface WriteableFeature<T: HeadphoneFeature> : Setable
interface ResponseParser<T: HeadphoneFeature> {
    fun parseResponse(response: ByteArray): T
}

interface Getable {
    fun toGetRequest(): Request
}

interface Setable {
    fun toSetRequest(): Request
}