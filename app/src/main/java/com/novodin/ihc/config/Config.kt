package com.novodin.ihc.config

// Configuration singleton with default values
object Config {
    var BackendIpAddress: String = "84.105.247.238"
    var BackendPort: String = "3001"
    var PassiveTimeoutShort: Long = (1000 * 20)     // 5 mins in ms
    var PassiveTimeoutMedium: Long = (1000 * 30)   // 10 mins in ms
    var PassiveTimeoutLong: Long = (1000 * 60)     // 30 mins in ms
    var PassiveRemoveFromCradleTimeout: Long = (1000 * 15)     // 30 seconds in ms
}