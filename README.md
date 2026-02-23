# MeshCoreKmp

[![build-and-test](https://github.com/Wavesonics/MeshCoreKmp/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/Wavesonics/MeshCoreKmp/actions/workflows/build-and-test.yml)

Kotlin Multiplatform Library

### Run Sample App

 - Android: `open project in Android Studio and run the sample app`
 - iOS: `open 'sample/iosApp/iosApp.xcodeproj' in Xcode and run the sample app`

## API Usage

### Setup

Create a `BlueFalconBleAdapter` with a platform-specific `BlueFalcon` instance, then pass it to `DeviceScanner`:

```kotlin
// Android
val blueFalcon = BlueFalcon(context = application)
val scanner = DeviceScanner(BlueFalconBleAdapter(blueFalcon))

// iOS
val blueFalcon = BlueFalcon(context = UIApplication.sharedApplication)
val scanner = DeviceScanner(BlueFalconBleAdapter(blueFalcon))
```                                                                                                                                                                                                                                                                                                                         

### Scanning for Devices

```kotlin                                                                                                                                                                                                                                                                                                                   
// Start scanning for MeshCore companion devices                                                                                                                                                                                                                                                                            
scanner.startScan(scope = coroutineScope)                                                                                                                                                                                                                                                                                   
                                                                                                                                                                                                                                                                                                                            
// Observe discovered devices via StateFlow                                                                                                                                                                                                                                                                                 
scanner.discoveredDevices.collect { devices ->                                                                                                                                                                                                                                                                              
    devices.forEach { device ->                                                                                                                                                                                                                                                                                             
        println("Found: ${device.name} (${device.identifier}) rssi=${device.rssi}")                                                                                                                                                                                                                                         
    }                                                                                                                                                                                                                                                                                                                       
}                                                                                                                                                                                                                                                                                                                           
                                                                                                                                                                                                                                                                                                                            
// Optionally filter by name prefix                                                                                                                                                                                                                                                                                         
scanner.startScan(                                                                                                                                                                                                                                                                                                          
    filter = ScanFilter(namePrefix = "MeshCore"),                                                                                                                                                                                                                                                                           
    scope = coroutineScope,                                                                                                                                                                                                                                                                                                 
)                                                                                                                                                                                                                                                                                                                           
                                                                                                                                                                                                                                                                                                                            
// Stop scanning when done                                                                                                                                                                                                                                                                                                  
scanner.stopScan()                                                                                                                                                                                                                                                                                                          
```                                                                                                                                                                                                                                                                                                                         

### Connecting to a Device

```kotlin                                                                                                                                                                                                                                                                                                                   
val connection = scanner.connect(                                                                                                                                                                                                                                                                                           
    device = selectedDevice,                                                                                                                                                                                                                                                                                                
    scope = coroutineScope,                                                                                                                                                                                                                                                                                                 
    config = ConnectionConfig(                                                                                                                                                                                                                                                                                              
        appName = "MyApp",                                                                                                                                                                                                                                                                                                  
        autoSyncTime = true,                                                                                                                                                                                                                                                                                                
        autoFetchChannels = true,                                                                                                                                                                                                                                                                                           
        autoPollMessages = true,                                                                                                                                                                                                                                                                                            
    ),                                                                                                                                                                                                                                                                                                                      
)                                                                                                                                                                                                                                                                                                                           
                                                                                                                                                                                                                                                                                                                            
// Observe connection state                                                                                                                                                                                                                                                                                                 
connection.connectionState.collect { state ->                                                                                                                                                                                                                                                                               
    when (state) {                                                                                                                                                                                                                                                                                                          
        is ConnectionState.Connected -> println("Connected!")                                                                                                                                                                                                                                                               
        is ConnectionState.Connecting -> println("Connecting...")                                                                                                                                                                                                                                                           
        is ConnectionState.Disconnected -> println("Disconnected")                                                                                                                                                                                                                                                          
        is ConnectionState.Error -> println("Error: ${state.message}")                                                                                                                                                                                                                                                      
    }                                                                                                                                                                                                                                                                                                                       
}                                                                                                                                                                                                                                                                                                                           
```                                                                                                                                                                                                                                                                                                                         

### Sending Messages

```kotlin                                                                                                                                                                                                                                                                                                                   
// Send a message on a channel                                                                                                                                                                                                                                                                                              
val confirmation = connection.sendChannelMessage(                                                                                                                                                                                                                                                                           
    channelIndex = 0,                                                                                                                                                                                                                                                                                                       
    text = "Hello mesh network!",                                                                                                                                                                                                                                                                                           
)                                                                                                                                                                                                                                                                                                                           
println("Sent! Ack expected: ${confirmation.expectedAck}")                                                                                                                                                                                                                                                                  
                                                                                                                                                                                                                                                                                                                            
// Wait for the acknowledgment                                                                                                                                                                                                                                                                                              
connection.acks.first { it == confirmation.expectedAck }                                                                                                                                                                                                                                                                    
println("Message acknowledged!")                                                                                                                                                                                                                                                                                            
```                                                                                                                                                                                                                                                                                                                         

### Receiving Messages

```kotlin                                                                                                                                                                                                                                                                                                                   
// Incoming messages are automatically polled and emitted                                                                                                                                                                                                                                                                   
connection.incomingMessages.collect { message ->                                                                                                                                                                                                                                                                            
    when (message) {                                                                                                                                                                                                                                                                                                        
        is ReceivedMessage.ChannelMessage -> {                                                                                                                                                                                                                                                                              
            println("[Channel ${message.channelIndex}] ${message.text}")                                                                                                                                                                                                                                                    
        }                                                                                                                                                                                                                                                                                                                   
        is ReceivedMessage.ContactMessage -> {                                                                                                                                                                                                                                                                              
            println("[DM from ${message.publicKeyPrefix}] ${message.text}")                                                                                                                                                                                                                                                 
        }                                                                                                                                                                                                                                                                                                                   
    }                                                                                                                                                                                                                                                                                                                       
}                                                                                                                                                                                                                                                                                                                           
```                                                                                                                                                                                                                                                                                                                         

### Raw Binary Data

Send and receive arbitrary binary payloads over the mesh, useful for custom application protocols.

```kotlin
// Send raw data (broadcast/flood) with an optional path
connection.sendRawData(payload = byteArrayOf(0x01, 0x02, 0x03))

// Listen for incoming raw data from the mesh
connection.incomingRawData.collect { rawData ->
    println("Raw data received (SNR: ${rawData.snr}, RSSI: ${rawData.rssi})")
    println("Payload: ${rawData.payload.size} bytes")
}
```

### Binary Requests

Send binary data to a specific contact by public key, with correlated responses.

```kotlin
// Send a binary request to a specific contact
val confirmation = connection.sendBinaryRequest(
    publicKey = contactPublicKey, // 32-byte public key
    requestData = myRequestPayload,
)
println("Binary request sent, tag: ${confirmation.expectedAck}")

// Listen for binary responses (correlated by tag)
connection.incomingBinaryResponses.collect { response ->
    println("Binary response for tag=${response.tag}: ${response.responseData.size} bytes")
}
```

### Device Info and Stats

```kotlin                                                                                                                                                                                                                                                                                                                   
// Device info is fetched automatically on connect                                                                                                                                                                                                                                                                          
val info = connection.deviceInfo.value                                                                                                                                                                                                                                                                                      
println("Firmware: ${info?.firmwareBuild} | Model: ${info?.model}")                                                                                                                                                                                                                                                         
                                                                                                                                                                                                                                                                                                                            
// Battery                                                                                                                                                                                                                                                                                                                  
val battery = connection.getBattery()                                                                                                                                                                                                                                                                                       
println("Battery: ${battery.levelPercent}%")                                                                                                                                                                                                                                                                                
                                                                                                                                                                                                                                                                                                                            
// Radio stats                                                                                                                                                                                                                                                                                                              
val radio = connection.getRadioStats()                                                                                                                                                                                                                                                                                      
println("RSSI: ${radio.lastRssiDbm} dBm, SNR: ${radio.lastSnrDb} dB")                                                                                                                                                                                                                                                       
```                                                                                                                                                                                                                                                                                                                         

### Channel Management

```kotlin                                                                                                                                                                                                                                                                                                                   
// Channels are auto-fetched on connect, observe via StateFlow                                                                                                                                                                                                                                                              
val channels = connection.channels.value                                                                                                                                                                                                                                                                                    
                                                                                                                                                                                                                                                                                                                            
// Or fetch manually                                                                                                                                                                                                                                                                                                        
val allChannels = connection.getAllChannels()                                                                                                                                                                                                                                                                               
allChannels.forEach { ch ->                                                                                                                                                                                                                                                                                                 
    println("Channel ${ch.index}: ${ch.name} (empty=${ch.isEmpty})")                                                                                                                                                                                                                                                        
}                                                                                                                                                                                                                                                                                                                           
```                                                                                                                                                                                                                                                                                                                         

### Disconnecting

```kotlin                                                                                                                                                                                                                                                                                                                   
connection.disconnect()                                                                                                                                                                                                                                                                                                     
```                                                                                                                                                                                                                                                                                                                         
 