package com.novodin.ihc.config

// Configuration singleton with default values
object Config {
//    var BackendIpAddress: String = "84.105.247.238"
    var BackendIpAddress: String = "192.168.0.189"
    var BackendPort: String = "3001"
    var PassiveTimeoutShort: Long = (1000 * 5)     // 5 mins in ms
    var PassiveTimeoutMedium: Long = (1000 * 40)   // 10 mins in ms
    var PassiveTimeoutLong: Long = (1000 * 20)     // 30 mins in ms
    var PassiveRemoveFromCradleTimeout: Long = (1000 * 30)     // 30 seconds in ms
}