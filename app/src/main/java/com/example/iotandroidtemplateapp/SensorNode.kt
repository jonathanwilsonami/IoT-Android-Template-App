package com.example.iotandroidtemplateapp

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class SensorNode(
    var nodeName: String = "",
    var nodeType: String = "",
    var nodeImageUri: String? = "no img",
    var connectedNodes: ConnectedNodes? = null
) {
    var uuid: String = UUID.randomUUID().toString()
        private set

    fun updateUUID() {
        uuid = generateUUID(sanitizeInput(nodeType), sanitizeInput(nodeName))
    }

    private fun generateUUID(nodeType: String, nodeName: String): String {
        return "$nodeType-$nodeName-${UUID.randomUUID()}"
    }

    private fun sanitizeInput(input: String): String {
        return input.trim()
            .replace("[^a-zA-Z0-9- ]".toRegex(), "")  // Remove non-alphanumeric characters except hyphens and spaces
            .replace(" ", "-")  // Replace spaces with hyphens
    }
}


@Serializable
data class ConnectedNodes(
    var nodeMate: SensorNode? = null,
    var childrenNodes: MutableList<SensorNode>? = null
)
