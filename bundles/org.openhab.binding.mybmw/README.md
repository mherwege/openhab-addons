# MyBMW Binding

This binding gives read-only access to the data of BMW, MINI and Rolls-Royce vehicles through [BMW CarData](https://bmw-cardata.bmwgroup.com/customer/public/api-documentation), the data service BMW offers vehicle owners in the European Union.

CarData has two parts:

- a **stream**: the vehicle pushes changed values over MQTT, close to real time
- a **REST API**: current values on request, limited to 50 requests per account per day

The binding uses the stream for live data and spends the small REST budget on the vehicle list, vehicle properties and an occasional full refresh.

CarData cannot send commands to the vehicle: there is no remote locking, climate control or charge control.

## Prerequisites

### CarData client

1. Log in to the MyBMW (or MINI) portal, open your vehicle and go to **BMW CarData**.
1. Under _Technical access to BMW CarData_, create a **CarData client** and note its client ID.
1. Subscribe the client to both **CarData API** and **CarData Stream**.
1. Open **Configure data stream** and select the values you want to receive.
   Values that are not selected here never arrive over the stream.

You must be the primary user of the vehicle; secondary users cannot read data through CarData.

### Stream bridge

BMW allows only **one** stream connection per account at a time.
This version of the binding does not connect to BMW's stream itself, but receives it from a local MQTT broker that the [bmw-mqtt-bridge](https://github.com/dj0abr/bmw-mqtt-bridge) publishes to.
Set up the bridge with its own authorization, following its documentation, and point it at your broker.
The bridge's default settings are fine: the binding reads the unmodified messages on `<prefix>raw/<VIN>` and the bridge state on `<prefix>status`.
Split topics (`SPLIT_TOPICS`) are not needed.

Because the bridge holds the stream connection, other tools such as evcc can read the same data from the broker.
Do not run another tool that connects to the BMW stream directly with the same account: the connections push each other out.

The binding authorizes separately from the bridge, with the same client ID, for its REST requests.

## Supported Things

| Thing type | Description                                                         |
|------------|---------------------------------------------------------------------|
| `account`  | Bridge for a CarData account, identified by its client ID            |
| `vehicle`  | A vehicle mapped to the account, identified by its VIN               |

## Discovery

Once the account is authorized, the vehicles mapped to it are discovered automatically.
Only vehicles for which you are the primary user are offered.

## Thing Configuration

### `account` Bridge Configuration

| Name             | Type    | Description                                                                                         | Default | Required | Advanced |
|------------------|---------|-----------------------------------------------------------------------------------------------------|---------|----------|----------|
| `clientId`       | text    | CarData client ID from the customer portal                                                          | N/A     | yes      | no       |
| `brokerHost`     | text    | Host of the MQTT broker the bmw-mqtt-bridge publishes to                                            | N/A     | yes      | no       |
| `brokerPort`     | integer | Port of that broker                                                                                 | 1883    | no       | no       |
| `brokerSecure`   | boolean | Connect to the broker with TLS                                                                      | false   | no       | yes      |
| `brokerUsername` | text    | Broker username                                                                                     | N/A     | no       | yes      |
| `brokerPassword` | text    | Broker password                                                                                     | N/A     | no       | yes      |
| `topicPrefix`    | text    | The `LOCAL_PREFIX` configured in the bridge                                                         | bmw/    | no       | yes      |
| `refreshInterval`| integer | Hours between REST refreshes of all values, one request per vehicle; 0 disables the periodic refresh | 6       | no       | no       |
| `requestBudget`  | integer | Maximum REST requests the binding sends in 24 hours (BMW allows 50)                                 | 40      | no       | yes      |

### `vehicle` Thing Configuration

| Name  | Type | Description                          | Default | Required |
|-------|------|--------------------------------------|---------|----------|
| `vin` | text | Vehicle identification number        | N/A     | yes      |

## Authorization

When the account is first started, it shows the status `OFFLINE (AUTHORIZATION_PENDING)` with a link and a code.
Open the link, log in with your MyBMW account and confirm the code.
The account goes online within a few seconds.
If you do not confirm in time, the binding requests a new code.

The binding keeps the authorization alive by refreshing it every hour.
BMW's refresh token expires after two weeks without use, so after openHAB has been down for longer, the account asks for a new confirmation.

The tokens are kept by openHAB's OAuth service.
Changing the client ID or removing the account thing deletes them.

## REST Requests

BMW limits the REST API to 50 requests per account in 24 hours.
The binding counts its own requests over a rolling 24 hours, including across restarts, and stops at the configured `requestBudget`.
It uses requests for:

- the vehicle list, once after the account comes online
- the basic data of each vehicle (model, brand, propulsion type), once per vehicle; it is kept in the thing properties
- a container: the set of values the binding reads; it is created once and reused (BMW allows at most 10 containers per account)
- a refresh of all values, one request per vehicle: two minutes after start, every `refreshInterval` hours, and after the stream was interrupted for five minutes or more, as the stream does not replay what was missed

With the default settings and one vehicle, the binding uses about 4 requests a day, plus a few after each restart.

## Channels

Channels are grouped.
Which channels receive values depends on the vehicle and on the data selected in **Configure data stream**; channels for data the vehicle does not provide keep the state `NULL`.

### Group `status`

| Channel               | Type                      | Description                                        | Advanced |
|-----------------------|---------------------------|----------------------------------------------------|----------|
| `mileage`             | Number:Length             | Total distance traveled                            | no       |
| `lock`                | String                    | Overall lock state: `SECURED`, `LOCKED`, `SELECTIVELOCKED`, `UNLOCKED`, `UNKNOWN` | no |
| `moving`              | Switch                    | Vehicle is moving                                  | no       |
| `ignition`            | Switch                    | Ignition is on                                     | no       |
| `alarm-arm-state`     | String                    | Alarm arming state: `unarmed`, `doorsOnly`, `doorsTiltCabin` | no |
| `alarm-active`        | Switch                    | Alarm is going off                                 | no       |
| `deep-sleep`          | Switch                    | Vehicle is in deep sleep and does not send data    | yes      |
| `battery-12v-level`   | Number:Dimensionless      | 12V battery charge level                           | yes      |
| `battery-12v-voltage` | Number:ElectricPotential  | 12V battery voltage                                | yes      |
| `service-distance`    | Number:Length             | Distance to the next service                       | no       |
| `inspection-date`     | DateTime                  | Next legally required inspection                   | no       |
| `last-update`         | DateTime                  | Time of the most recent value                      | no       |

### Group `range`

| Channel          | Type                 | Description                                  |
|------------------|----------------------|----------------------------------------------|
| `fuel-level`     | Number:Dimensionless | Fuel tank level                              |
| `fuel-remaining` | Number:Volume        | Fuel left in the tank                        |
| `total-range`    | Number:Length        | Remaining range on fuel and battery combined |
| `electric-range` | Number:Length        | Remaining range on the high-voltage battery  |

### Group `charge`

| Channel            | Type                     | Description                                    | Advanced |
|--------------------|--------------------------|------------------------------------------------|----------|
| `soc`              | Number:Dimensionless     | High-voltage battery charge level              | no       |
| `target-soc`       | Number:Dimensionless     | Charge level the vehicle charges to            | no       |
| `status`           | String                   | Charging status, e.g. `NOCHARGING`, `CHARGINGACTIVE`, `CHARGINGENDED` | no |
| `plug`             | String                   | `CONNECTED` or `DISCONNECTED`                  | no       |
| `power`            | Number:Power             | Charging power                                 | no       |
| `remaining-time`   | Number:Time              | Time to full charge                            | no       |
| `energy-to-full`   | Number:Energy            | Energy needed to fully charge                  | no       |
| `ac-current`       | Number:ElectricCurrent   | AC charging current                            | yes      |
| `ac-voltage`       | Number:ElectricPotential | AC charging voltage                            | yes      |
| `phases`           | String                   | AC phases used: `1-PHASES`, `2-PHASES`, `3-PHASES` | yes  |
| `current-limit`    | Number:ElectricCurrent   | Selected AC charging current limit             | yes      |
| `battery-capacity` | Number:Energy            | Size of the high-voltage battery               | yes      |

### Group `location`

| Channel    | Type         | Description                             | Advanced |
|------------|--------------|-----------------------------------------|----------|
| `position` | Location     | Last reported position                  | no       |
| `heading`  | Number:Angle | Direction the vehicle is facing         | yes      |

### Group `doors`

`driver-front`, `passenger-front`, `driver-rear`, `passenger-rear`, `hood` and `trunk`, all of type `Contact`.

### Group `windows`

`driver-front`, `passenger-front`, `driver-rear`, `passenger-rear` and `sunroof`, all of type `Contact`.
A partly open window or sunroof is `OPEN`, like the doors, so windows and doors can share a group.

Each of them has an advanced `String` channel with the suffix `-state`, e.g. `driver-front-state`, with the state as reported: `CLOSED`, `INTERMEDIATE` (partly open) or `OPEN`.

### Group `tires`

`front-left`, `front-right`, `rear-left` and `rear-right` with the measured pressure, and the same names with `-target` (advanced) for the target pressure, all of type `Number:Pressure`.

### Group `climate`

| Channel          | Type        | Description                                                         |
|------------------|-------------|---------------------------------------------------------------------|
| `activity`       | String      | Preconditioning: `inactive`, `standby`, `heating`, `cooling`, `ventilation` |
| `remaining-time` | Number:Time | Remaining preconditioning time                                      |

## Full Example

### `demo.things` Example

```java
Bridge mybmw:account:home "CarData Account" [ clientId="12345678-90ab-cdef-1234-567890abcdef", brokerHost="192.168.1.10" ] {
    Thing vehicle i4 "BMW i4" [ vin="WBA00000000000001" ]
}
```

### `demo.items` Example

```java
Number:Dimensionless i4_Soc            "State of charge [%d %%]"      { channel="mybmw:vehicle:home:i4:charge#soc" }
String               i4_ChargingStatus "Charging [%s]"                { channel="mybmw:vehicle:home:i4:charge#status" }
Number:Power         i4_ChargingPower  "Charging power [%.1f kW]"     { channel="mybmw:vehicle:home:i4:charge#power" }
Number:Length        i4_Range          "Range [%d km]"                { channel="mybmw:vehicle:home:i4:range#electric-range" }
Number:Length        i4_Mileage        "Mileage [%d km]"              { channel="mybmw:vehicle:home:i4:status#mileage" }
String               i4_Lock           "Lock [%s]"                    { channel="mybmw:vehicle:home:i4:status#lock" }
Location             i4_Position       "Position"                     { channel="mybmw:vehicle:home:i4:location#position" }
DateTime             i4_LastUpdate     "Last update [%1$tF %1$tR]"    { channel="mybmw:vehicle:home:i4:status#last-update" }
```

## Troubleshooting

- **Authorization fails with `access_denied`:** BMW's device authorization is known to reject confirmations intermittently.
  The binding requests a new code after a minute; open the portal, choose _Authenticate device_ and type the code by hand.
- **Account online with "bmw-mqtt-bridge is not connected":** the bridge reports it lost its connection to BMW.
  Check the bridge log; its own authorization may need to be renewed.
- **Channels stay `NULL`:** check that the values are selected in **Configure data stream** in the portal, and that the vehicle provides them at all.
