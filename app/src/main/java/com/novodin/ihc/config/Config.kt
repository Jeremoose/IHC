package com.novodin.ihc.config

// Configuration singleton with default values
object Config {
    var BackendIpAddress: String = "34.68.16.30"
    var BackendPort: String = "3001"
    var PassiveTimeoutShort: Long = (1000 * 60 * 5)     // 5 mins in ms
    var PassiveTimeoutMedium: Long = (1000 * 60 * 10)   // 10 mins in ms
    var PassiveTimeoutLong: Long = (1000 * 60 * 30)     // 30 mins in ms
    var RemoveFromCradleTimeout: Long = (1000 * 30)     // 30 seconds in ms
}