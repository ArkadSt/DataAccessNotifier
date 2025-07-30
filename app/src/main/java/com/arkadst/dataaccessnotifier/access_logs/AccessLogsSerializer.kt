package com.arkadst.dataaccessnotifier.access_logs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.arkadst.dataaccessnotifier.AccessLogsProto
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object AccessLogsSerializer : Serializer<AccessLogsProto> {
    override val defaultValue: AccessLogsProto = AccessLogsProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AccessLogsProto {
        try {
            return AccessLogsProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: AccessLogsProto, output: OutputStream) = t.writeTo(output)
}