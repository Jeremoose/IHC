package com.novodin.ihc.config

// Configuration singleton with default values
object Config {
    //    var BackendIpAddress: String = "84.105.247.238"
    var BackendIpAddress: String = "192.168.0.189"
    var BackendPort: String = "3001"

    // max values for passive timeouts is 1193 hours (max value of Uint in milliseconds)
    var PassiveTimeoutShort: UInt = (1000 * 10).toUInt()    // 5 mins in ms
    var PassiveTimeoutMedium: UInt = (1000 * 15).toUInt()  // 10 mins in ms
    var PassiveTimeoutLong: UInt = (1000 * 20).toUInt()     // 30 mins in ms
    var PassiveRemoveFromCradleTimeout: UInt = (1000 * 30).toUInt()     // 30 seconds in ms
}