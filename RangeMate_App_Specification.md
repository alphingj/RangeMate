# RangeMate — Electric Scooter BMS Dashboard

## 1. Project Overview

**App name:** RangeMate  
**Platform:** Android (primary target)  
**Display orientation:** Landscape-first  
**Purpose:** Connect to a Bluetooth Battery Management System (BMS) used in an electric scooter and present important battery and riding information in a modern dashboard interface.

RangeMate is intended to work with a scooter BMS that is compatible with the **Xiaoxiang/JBD-style Bluetooth protocol**, including BMS units that can also communicate with apps such as Xiaoxiang or Overkill Solar.

> Important: Actual compatibility must be verified with the user's specific BMS. Different BMS hardware and firmware versions can use different Bluetooth services, UUIDs, packet formats, authentication requirements, or protocols.

---

# 2. Main User Flow

## Step 1 — Open RangeMate

When the user opens the app:

1. The app starts in landscape orientation.
2. A RangeMate splash/loading screen may appear briefly.
3. If no BMS is currently connected, the app shows the **Connect to Your Scooter** screen.

The app should request the required Android Bluetooth permissions when necessary.

---

## Step 2 — Connect to the Scooter BMS

The connection screen should:

- Scan for nearby Bluetooth Low Energy (BLE) devices.
- Display compatible or available Bluetooth devices.
- Allow the user to select the scooter BMS.
- Connect to the selected device.
- Discover Bluetooth services and characteristics.
- Subscribe to required notifications.
- Begin requesting or receiving live BMS data.

### Connection status

The UI should clearly show:

- Scanning
- Connecting
- Connected
- Disconnected
- Connection failed

The app should not assume a device is compatible only because its Bluetooth name contains a specific word. Compatibility should be determined by successful service discovery and protocol communication where possible.

---

# 3. Dashboard

After a successful connection, the user is taken to the main RangeMate dashboard.

The dashboard design should be inspired by a modern electric vehicle or scooter instrument cluster.

## Main information shown

### Speed

- Large and centered.
- Unit should be configurable: km/h or mph.
- Speed source may be:
  - Phone GPS, or
  - Another supported external data source in future versions.

The BMS normally does not necessarily provide vehicle speed, so speed should not be assumed to come from the BMS.

---

### Battery Percentage

Displays the battery State of Charge (SOC).

Example:

`Battery: 78%`

Preferred source:

- SOC value directly reported by the BMS.

Fallback calculations should only be used if the BMS does not provide SOC.

---

### Battery Voltage

Displays the live battery pack voltage.

Example:

`72.4 V`

---

### Battery Current

Displays live battery current.

Example:

`18.5 A`

Current direction should be handled correctly:

- Discharging
- Charging

The sign convention used by the connected BMS must be detected and documented during protocol integration.

---

### Power

Power should be calculated as:

`Power (W) = Voltage (V) × Current (A)`

Example:

`72 V × 20 A = 1440 W`

The dashboard should show the absolute or signed power according to the selected UI design.

---

### Estimated Distance to Empty

RangeMate estimates remaining distance using battery SOC and the configured full-range value.

Default formula:

`Estimated Remaining Range = (SOC / 100) × Configured Full Range`

Example:

- Battery SOC: 50%
- Configured full range: 70 km

Result:

`35 km remaining`

This estimate is intentionally simple in Version 1. It does not automatically account for:

- Rider weight
- Hills
- Wind
- Speed
- Battery temperature
- Battery age
- Riding style

A future version may add an adaptive range prediction based on historical energy consumption.

---

# 4. Animated Power Sweep Meter

One of the main visual features of RangeMate is the power meter.

## Design

The meter is inspired by the green section shown on the left side of the dashboard reference image.

As power increases:

- The illuminated sweep begins at the **left**.
- It moves progressively toward the **right**.
- The length or position of the illuminated section represents current power usage.

## Default Power Scale

For the user's current scooter configuration:

`Maximum power = 1600 W`

This value must be customizable.

## Default color zones

Example configuration:

- Green: low to normal power
- Yellow: medium/high power
- Red: high/maximum power

Suggested default zones for 1600 W:

- Green: 0–700 W
- Yellow: 701–1200 W
- Red: 1201–1600 W

These values should be configurable.

## Animation

The meter should:

- Update smoothly as new BMS data arrives.
- Avoid sudden visual jumps where possible.
- Use interpolation or animation between readings.
- Reset or reduce smoothly when power decreases.

The app should not fake power values. The sweep should be based on live measured voltage and current.

---

# 5. Power Graph

RangeMate should include a real-time power graph.

## Graph behavior

The graph displays power over time.

Example:

- Horizontal axis: time
- Vertical axis: watts

The graph can show recent values such as the last:

- 30 seconds
- 60 seconds
- 5 minutes

The initial version can use a 60-second rolling history.

## Toggle

The power graph must be toggleable.

The user can:

- Turn the graph ON.
- Turn the graph OFF.

When disabled:

- The graph should not take unnecessary dashboard space.
- The dashboard should rearrange cleanly.

---

# 6. Landscape Mode

RangeMate is designed primarily as a dashboard and should operate in landscape mode.

## Recommended layout

### Top area

- Current time
- Bluetooth/BMS connection status

### Center

- Large speed display

### Left or lower area

- Animated power sweep meter

### Right area

- Battery icon
- Battery percentage
- Estimated kilometres remaining

### Optional lower section

- Power graph when enabled

The layout should be responsive so it works across different Android screen sizes.

Examples:

- Standard Android phone
- Large Android phone
- Tablet
- Scooter-mounted display in future versions

---

# 7. Settings

RangeMate should contain a settings screen.

## Full Range

The user can configure the scooter's expected range when fully charged.

Default:

`70 km`

This is used for the basic distance-to-empty calculation.

---

## Maximum Power

The user can configure the maximum power value for the sweep meter.

Default:

`1600 W`

Examples:

- 1000 W
- 1600 W
- 2000 W
- Custom value

The power sweep must scale according to the selected maximum.

---

## Power Zones

The user should be able to configure:

- Green zone end
- Yellow zone end
- Red zone maximum

Example:

- Green: 0–700 W
- Yellow: 701–1200 W
- Red: 1201–1600 W

---

## Power Graph Toggle

Settings should include:

`Show Power Graph: ON/OFF`

---

## Speed Source

Potential options:

- GPS
- Disabled

Future options may include an external Bluetooth speed source.

---

# 8. Bluetooth and BMS Communication

## Primary connection method

RangeMate should use:

`Bluetooth Low Energy (BLE)`

## Target BMS family

The user's scooter BMS is known to work with:

- Xiaoxiang-compatible applications
- Overkill Solar-compatible applications

This suggests the BMS may use a known JBD/Xiaoxiang-style protocol, but this must be confirmed during real-device testing.

## Required development work

The implementation should:

1. Scan BLE devices.
2. Connect to the selected BMS.
3. Discover services.
4. Identify communication characteristics.
5. Send required protocol requests.
6. Receive notifications or responses.
7. Parse battery data.
8. Update the dashboard in real time.

## Data expected from BMS

Depending on the protocol and hardware, useful data may include:

- Pack voltage
- Pack current
- State of charge
- Battery temperature
- Individual cell voltages
- Charging status
- Discharging status
- Cycle count
- Remaining capacity
- Rated capacity
- Error or protection flags

Version 1 primarily needs:

- Voltage
- Current
- SOC

Additional values can be added later.

---

# 9. Important Compatibility and Testing Notes

A working Xiaoxiang or Overkill Solar connection does not automatically guarantee that every compatible-looking BMS uses exactly the same Bluetooth protocol.

Before claiming full compatibility, the RangeMate app must be tested with the actual scooter.

Testing should verify:

- Device discovery
- Successful connection
- Service discovery
- Correct protocol commands
- Correct voltage values
- Correct current values
- Correct SOC values
- Stable data updates
- Disconnect behavior
- Reconnection behavior

A debug screen is recommended for development.

---

# 10. Debug Mode

A developer/debug mode can help diagnose BMS connection problems.

When enabled, it can display:

- Bluetooth device name
- MAC/address where Android allows access
- Connected state
- Available BLE services
- Available characteristics
- Raw packet data
- Parsed packet data
- Connection errors
- Last successful update time

Debug mode should be disabled or hidden by default for normal users.

---

# 11. App Launch Requirements

The Android app must include a proper launcher activity so that it appears in the Android app drawer after installation.

The Android manifest should contain a launchable activity with:

- `android.intent.action.MAIN`
- `android.intent.category.LAUNCHER`

This should be verified before release.

The final release must be tested by:

1. Installing the APK.
2. Confirming the app icon appears in the launcher/app drawer.
3. Opening the app normally.
4. Verifying landscape orientation.
5. Verifying the Bluetooth connection flow.

---

# 12. Permissions

Depending on the Android version, RangeMate may require:

- Bluetooth scan permission
- Bluetooth connect permission
- Location permission on older Android versions for BLE scanning
- Fine location permission for GPS speed
- Foreground location permission where required

The app should request only the permissions needed for enabled features.

---

# 13. Data Update Flow

The real-time dashboard flow should work like this:

`BMS → Bluetooth → RangeMate BLE Service → Protocol Parser → App State → Dashboard`

Example:

1. BMS sends voltage.
2. RangeMate receives packet.
3. Packet parser extracts voltage.
4. BMS sends current.
5. RangeMate receives or extracts current.
6. App calculates power.
7. Dashboard updates:
   - Voltage
   - Current
   - Battery %
   - Power
   - Power sweep
   - Power graph
   - Estimated remaining range

---

# 14. Recommended Architecture

A clean architecture is recommended.

## BLE Layer

Responsible for:

- Scanning
- Connecting
- Disconnecting
- Reconnecting
- Reading characteristics
- Subscribing to notifications

## Protocol Layer

Responsible for:

- Creating protocol commands
- Parsing BMS responses
- Validating packets
- Handling checksums if required

## Data Layer

Responsible for:

- Storing current BMS state
- Keeping power history
- Saving user settings

## UI Layer

Responsible for:

- Connection screen
- Dashboard
- Settings
- Graph
- Animations

---

# 15. Suggested Dashboard Data Model

Example live state:

```text
BmsData
├── voltage: Double
├── current: Double
├── power: Double
├── soc: Int
├── connected: Boolean
├── timestamp: DateTime
└── optional values
    ├── temperature
    ├── cell voltages
    ├── cycle count
    └── protection status
```

Power should normally be derived:

```text
power = voltage × current
```

---

# 16. RangeMate Version 1 Feature Checklist

## Connection

- [ ] BLE scanner
- [ ] Device selection
- [ ] BMS connection
- [ ] Connection status
- [ ] Automatic reconnect

## Dashboard

- [ ] Landscape mode
- [ ] Large speed display
- [ ] Battery percentage
- [ ] Battery voltage
- [ ] Battery current
- [ ] Live power
- [ ] Estimated kilometres remaining
- [ ] Current time

## Power Meter

- [ ] Left-to-right sweep
- [ ] Green zone
- [ ] Yellow zone
- [ ] Red zone
- [ ] Smooth animation
- [ ] Custom maximum power

## Power Graph

- [ ] Real-time graph
- [ ] Rolling history
- [ ] ON/OFF toggle

## Settings

- [ ] Full-range setting
- [ ] Maximum power setting
- [ ] Power zone settings
- [ ] Graph toggle
- [ ] Speed source setting

---

# 17. Future Features

Possible future improvements:

## Adaptive Range Prediction

Instead of simply using SOC:

`Range = SOC × configured full range`

RangeMate could calculate energy consumption.

Example:

`Wh/km = Energy Used / Distance Travelled`

Then:

`Remaining Range = Remaining Battery Energy / Recent Wh per km`

This could produce a more accurate estimate.

---

## Ride History

Store:

- Distance
- Average power
- Maximum power
- Battery used
- Average speed
- Ride duration

---

## Battery Health

Potentially display:

- Full charge capacity
- Cycle count
- Cell voltage difference
- Battery temperature

---

## Alerts

Warnings for:

- Low battery
- High current
- High temperature
- BMS disconnect
- Cell imbalance

---

## Themes

Possible themes:

- Dark dashboard
- Light dashboard
- Custom accent color

---

# 18. Visual Design Direction

RangeMate should feel like a modern electric vehicle dashboard.

## Style

- Dark background
- High contrast text
- Minimal interface
- Large readable numbers
- Smooth animations
- Green accent color
- Yellow warning/high-power color
- Red maximum-power color

## Branding

App name:

`RangeMate`

Suggested logo concept:

A battery symbol combined with a clean, modern RangeMate wordmark.

---

# 19. Development Recommendation

The project can be built using:

## Option A — Native Android

- Kotlin
- Jetpack Compose
- Android Bluetooth LE APIs

Advantages:

- Best Android integration
- Strong BLE control
- Good performance

## Option B — Flutter

- Dart
- Flutter UI
- BLE package such as flutter_blue_plus

Advantages:

- Fast UI development
- Easier future iOS support

For an Android-only scooter dashboard, native Kotlin is recommended for maximum Bluetooth and system integration reliability.

---

# 20. Final Definition of RangeMate

RangeMate is a landscape-oriented Android dashboard application designed to connect to a compatible electric scooter BMS using Bluetooth Low Energy.

After connecting, it should provide a clean real-time dashboard showing:

- Speed
- Battery percentage
- Battery voltage
- Battery current
- Live power
- Estimated kilometres remaining
- Animated left-to-right power sweep
- Optional real-time power graph
- Configurable range and power settings

The app's most important requirement is reliable communication with the user's actual Xiaoxiang/Overkill-compatible scooter BMS.

## Development Priority Order

1. Verify real BMS protocol and connection.
2. Read correct voltage, current, and SOC.
3. Build a stable dashboard.
4. Add the animated power sweep.
5. Add power graph.
6. Add settings.
7. Test on the user's actual phone and scooter.
8. Package and verify the APK appears correctly in the Android app drawer.

---

# Important Final Note

This document describes the intended RangeMate application. A working APK must still be genuinely built, compiled, signed, and tested against the actual scooter BMS. Features should not be considered complete until they have been verified on the user's device.
