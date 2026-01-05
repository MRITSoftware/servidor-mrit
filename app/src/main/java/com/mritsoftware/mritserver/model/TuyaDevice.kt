package com.mritsoftware.mritserver.model

import java.io.Serializable

data class TuyaDevice(
    val id: String, // tuya_device_id (gwId)
    var name: String,
    val type: DeviceType,
    var localKey: String? = null, // local_key necessária para comandos
    var lanIp: String? = null, // IP local (opcional, pode ser "auto")
    var protocolVersion: String? = null, // protocol_version do dispositivo
    var isOnline: Boolean = false,
    var isOn: Boolean = false,
    var brightness: Int = 100,
    var temperature: Int = 25
) : Serializable {
    enum class DeviceType {
        LIGHT,
        SWITCH,
        SENSOR,
        THERMOSTAT,
        OTHER
    }
}

