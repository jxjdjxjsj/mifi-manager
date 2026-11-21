package com.admi.mifi.data.api

import com.admi.mifi.data.DeviceProtocol
import com.admi.mifi.data.DeviceSnapshot

interface DeviceApi {
    val protocol: DeviceProtocol

    suspend fun read(config: HostConfig, password: String): DeviceSnapshot

    suspend fun reboot(config: HostConfig, password: String): Boolean

    suspend fun shutdown(config: HostConfig, password: String): Boolean
}
