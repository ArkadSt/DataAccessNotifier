package com.arkadst.dataaccessnotifier.user_info

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.arkadst.dataaccessnotifier.UserInfoProto
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object UserInfoSerializer : Serializer<UserInfoProto> {
    override val defaultValue: UserInfoProto = UserInfoProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserInfoProto {
        try {
            return UserInfoProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: UserInfoProto, output: OutputStream) = t.writeTo(output)
}
