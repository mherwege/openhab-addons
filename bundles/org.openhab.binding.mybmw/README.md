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

Once a new account is authorized, the vehicles mapped to it are discovered automatically and labeled with their model.
There is no background discovery, as every fetch of the vehicle list costs a request: an account that already has vehicle things does not fetch it again at every start.
To add another vehicle later, scan for `MyBMW` things in the inbox.
Only vehicles for which you are the primary user are offered, and only those BMW reports can send data (see [vehicle properties](#vehicle-properties)).
A vehicle that is not offered can still be added manually.

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

### Vehicle Properties

Once the account is authorized, the binding fills these thing properties from the vehicle's basic data:

| Property            | Example                      |
|---------------------|------------------------------|
| `brand`             | BMW_I                        |
| `modelName`         | iX xDrive40                  |
| `series`            | I                            |
| `developmentSeries` | I20                          |
| `modelKey`          | 11CF                         |
| `bodyType`          | Sports Activity Vehicle      |
| `propulsionType`    | EL                           |
| `driveTrain`        | BEV                          |
| `chargingModes`     | AC_LOW                       |
| `color`             | BLUE RIDGE MOUNTAIN METALLIC |
| `constructionDate`  | 2022-10-21T00:00:00.000+0000 |
| `countryCode`       | BE                           |
| `hasNavigation`     | true                         |
| `hasSunroof`        | false                        |
| `headUnit`          | HU_MGU                       |
| `simStatus`         | ACTIVE                       |
| `telematicsCapable` | true                         |
| `mappingType`       | PRIMARY                      |

A vehicle that is not `telematicsCapable` cannot send data to CarData: it is not discovered, and when added manually its thing is offline with a configuration error.
A vehicle whose `simStatus` is not `ACTIVE` may not send data: its thing is online, with a warning in the status description.

The binding removes the channels that do not apply to the vehicle:

- for an electric vehicle (`driveTrain` `BEV`): the fuel channels, `total-range` and `remote-engine-running`
- for a vehicle without a high-voltage battery: the `charge` group, `electric-range` and `target-range`
- when `hasSunroof` is `false`: the `sunroof` channels
- when `hasNavigation` is `false`: the `location` channels, as the vehicle reports its location through the navigation system

A plug-in hybrid keeps all channels, as does a vehicle whose `driveTrain` and `propulsionType` the binding does not recognize.
To get removed channels back, recreate the thing.
`mappingType` must be `PRIMARY`: CarData only serves data to the primary user of a vehicle.

## Authorization

When the account is first started, it shows the status `OFFLINE (AUTHORIZATION_PENDING)`.
Open the authorization page at `http(s)://<your openHAB>:<port>/mybmw`, which the status links to.
It shows, for every account waiting for authorization, a button to the BMW portal, the same link as QR code to scan with your phone, and the code to confirm.
Log in with your MyBMW account and confirm the code.
The BMW portal opens in the tab of the page, so you can close it once you confirmed, and you are back where you opened the page.
The account goes online within a few seconds; going back to the page instead shows it as authorized there.
If you do not confirm in time, the binding requests a new code, and the page shows that one.

The binding keeps the authorization alive by refreshing it every hour.
BMW's refresh token expires after two weeks without use, so after openHAB has been down for longer, the account asks for a new confirmation.

The tokens are kept by openHAB's OAuth service.
Changing the client ID or removing the account thing deletes them.

## REST Requests

BMW limits the REST API to 50 requests per account in 24 hours.
The binding counts its own requests over a rolling 24 hours per client ID, including across restarts and when you recreate the account thing, and stops at the configured `requestBudget`.
It uses requests for:

- the vehicle list: when the account comes online while it has no vehicle things yet, and when you scan for vehicles in the inbox (at most once an hour; a scan within the hour reuses the last list)
- the basic data of each vehicle for which you are the primary user, once per vehicle, whether you add it or not; discovery uses it, and it is kept in the [thing properties](#vehicle-properties)
- a container: the set of values the binding reads; it is created once and reused (BMW allows at most 10 containers per account), and replaced when an update of the binding reads other values, which costs one request to list the containers, one to create the new one, and one per old container of the binding to delete it
- a refresh of all values, one request per vehicle: two minutes after start, every `refreshInterval` hours, after a stream interruption of five minutes or more (the stream does not replay what was missed), and for a vehicle thing that is added or changed after that first refresh

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
| `engine`              | Switch                    | Engine is on                                       | no       |
| `lights`              | Switch                    | Vehicle lights are on                              | no       |
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
| `total-range`    | Number:Length        | Remaining range on fuel and battery combined; BMW intends it for combustion and plug-in hybrid vehicles, use `electric-range` for an electric vehicle |
| `electric-range` | Number:Length        | Remaining range on the high-voltage battery  |
| `target-range`   | Number:Length        | Electric range once charged to `target-soc`  |
| `total-fuel`     | Number:Volume        | Fuel used over the vehicle's lifetime        |
| `total-fuel-distance` | Number:Length   | Distance driven on that fuel (advanced)      |

### Group `charge`

| Channel            | Type                     | Description                                    | Advanced |
|--------------------|--------------------------|------------------------------------------------|----------|
| `soc`              | Number:Dimensionless     | High-voltage battery charge level              | no       |
| `soc-displayed`    | Number:Dimensionless     | Charge level as shown in the vehicle           | no       |
| `target-soc`       | Number:Dimensionless     | Charge level the vehicle charges to            | no       |
| `status`           | String                   | Charging status, e.g. `NOCHARGING`, `CHARGINGACTIVE`, `CHARGINGENDED` | no |
| `plug`             | String                   | `CONNECTED` or `DISCONNECTED`                  | no       |
| `dc-plug`          | String                   | DC cable `CONNECTED` or `DISCONNECTED`         | no       |
| `power`            | Number:Power             | Charging power                                 | no       |
| `remaining-time`   | Number:Time              | Time to full charge                            | no       |
| `charging-time-remaining` | Number:Time       | Predicted remaining time of the charging session | no     |
| `total-charged-energy` | Number:Energy        | Energy charged over the vehicle's lifetime     | no       |
| `total-charged-distance` | Number:Length      | Distance driven on that energy                 | yes      |
| `energy-to-full`   | Number:Energy            | Energy needed to fully charge                  | no       |
| `ac-current`       | Number:ElectricCurrent   | AC charging current                            | yes      |
| `ac-voltage`       | Number:ElectricPotential | AC charging voltage                            | yes      |
| `phases`           | String                   | AC phases used: `1-PHASES`, `2-PHASES`, `3-PHASES` | yes  |
| `current-limit`    | Number:ElectricCurrent   | Selected AC charging current limit             | yes      |
| `current-limit-max` | Number:ElectricCurrent  | Highest AC charging current limit that can be selected | yes |
| `current-limit-min` | Number:ElectricCurrent  | Lowest AC charging current limit that can be selected | yes |
| `current-limit-active` | Switch               | Whether the AC charging current is limited     | yes      |
| `battery-capacity` | Number:Energy            | Size of the high-voltage battery               | yes      |
| `method`           | String                   | Charging method as reported, e.g. `NOCHARGING` | yes      |
| `mode`             | String                   | Charging mode as reported, e.g. `NORMAL_PROGNOSE_BASED` | yes |
| `connection-type`  | String                   | Charging connection as reported, e.g. `CONDUCTIVE` | yes  |
| `smart-charging`   | String                   | Smart charging preference as reported, e.g. `PRICE_OPTIMIZED` | yes |
| `flap-locked`      | Switch                   | Whether the charging flap is locked            | no       |
| `plug-event`       | Number                   | Number of the last plug-in; it goes up with every plug-in, so it can trigger a rule | yes |

### Group `location`

| Channel    | Type         | Description                             | Advanced |
|------------|--------------|-----------------------------------------|----------|
| `position` | Location     | Last reported position                  | no       |
| `heading`  | Number:Angle | Direction the vehicle is facing         | yes      |

### Group `doors`

`driver-front`, `passenger-front`, `driver-rear`, `passenger-rear`, `hood` and `trunk`, all of type `Contact`.
`trunk-locked` (`Switch`) tells whether the trunk is locked.
The four doors also have an advanced `Number:Dimensionless` channel with the suffix `-position`, e.g. `driver-front-position`, with how far the door is open in percent.

### Group `windows`

`driver-front`, `passenger-front`, `driver-rear`, `passenger-rear` and `sunroof`, all of type `Contact`.
A partly open window or sunroof is `OPEN`, like the doors, so windows and doors can share a group.

Each of them has an advanced `String` channel with the suffix `-state`, e.g. `driver-front-state`, with the state as reported: `CLOSED`, `INTERMEDIATE` (partly open) or `OPEN`.

### Group `tires`

`front-left`, `front-right`, `rear-left` and `rear-right` with the measured pressure, and the same names with `-target` (advanced) for the target pressure, all of type `Number:Pressure`.
The same names with `-temperature` (advanced, `Number:Temperature`) hold the tire temperatures.

### Group `climate`

| Channel          | Type        | Description                                                         |
|------------------|-------------|---------------------------------------------------------------------|
| `activity`       | String      | Preconditioning: `INACTIVE`, `STANDBY`, `HEATING`, `COOLING`, `VENTILATION` |
| `remaining-time` | Number:Time | Remaining preconditioning time                                      |
| `error`          | String      | Preconditioning error, `OK` when there is none (advanced)           |
| `remote-engine-running` | Switch | Whether the engine was started remotely for preconditioning (advanced) |

### Average Consumption

BMW reports average consumption in kWh/100 km and l/100 km, units openHAB cannot convert, e.g. to mi/kWh or mpg.
The binding therefore provides lifetime totals instead, which convert to any unit: divide `total-charged-energy` by `total-charged-distance`, or `total-fuel` by `total-fuel-distance`.
For example, in a DSL rule:

```java
val energy = (BMW_TotalChargedEnergy.state as QuantityType<Energy>).toUnit("kWh")
val distance = (BMW_TotalChargedDistance.state as QuantityType<Length>).toUnit("km")
BMW_AverageConsumption.postUpdate(energy.doubleValue / distance.doubleValue * 100) // kWh/100 km
```

### Group `trip`

Values of the last trip the vehicle recorded.

| Channel        | Type                 | Description                                         | Advanced |
|----------------|----------------------|-----------------------------------------------------|----------|
| `end-time`     | DateTime             | Time the last trip ended                            | no       |
| `end-mileage`  | Number:Length        | Total distance traveled at the end of the last trip | no       |
| `end-soc`      | Number:Dimensionless | High-voltage battery charge level at the end of the last trip | no |

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
- **Several openHAB installations on one CarData account:** run the same version of the binding on all of them.
  Each version deletes the containers of the other versions, so different versions keep replacing each other's container and spend requests on it.
