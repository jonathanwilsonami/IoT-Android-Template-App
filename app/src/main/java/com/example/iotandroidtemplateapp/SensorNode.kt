package com.example.iotandroidtemplateapp

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class SensorNode(
    val uuid: String = UUID.randomUUID().toString(),
    var nodeName: String = "",
    var nodeType: String = ""
)
