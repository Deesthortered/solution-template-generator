# Solution template generator 
***

### System Information

* Sysadmin Info
  * Login: `sysadmin@thingsboard.org`
  * Password: `sysadmin`

* Tenant Info
  * Login: `solution.template.generator@thingsboard.io`
  * Password: `password`
  * Title: Solution Template Generator Tenant

***


## Basic Solution
  * Login: `basic@thingsboard.io`
  * Password: `password`

Data starts at `<start year>-01-01`

***

## Energy Metering Solution 
  * Login: `energymetering@thingsboard.io`
  * Password: `password`

  ### Summary
There are the following item types: `buildings`, `apartments`, `energy meter`, and `heat meter`.
Buildings contain some apartments, apartments have one energy and one heat meter.

There are three buildings: Alpire, Feline, and Hogurity.
They have an `address` attribute and all of them are located in the USA in California or in New York.

Each building has apartments with different `floor`, `area`, and `localNumber`.
Apartments have a `state` attribute that determines apartment is rented or empty.
Rented apartments consume energy and heating, and empty has no energy consumption and no heating.
Sometimes we can observe anomalies in energy consumption because of accidents.
Anomaly can be next: increased consumption, decreased consumption, gaps in measurements, zero values.
To simplify the solution, there are defined 3 levels of consumption: low, medium, and high.
Levels are not represented explicitly, it just makes a difference between apartments in their telemetry.

Each apartment has energy and meters with attributes `installDate` and `serialNumber` - random 5-digit numbers.
They provide telemetry only after `installDate` date.
Energy Meter provides `energyConsumption` telemetry and `energyConsAbsolute` that summing that data.
Heat Meter provides `temperature`, `heatConsumption` and `heatConsAbsolute` telemetries.
Energy Meter and Heat Meter do not make affect each other.


`Alpire` building located in the `USA, California, San Francisco, ...`.
It has 5 floors, each floor has 2 apartments.
Apartment 2 on the 1st floor is free and has anomaly (increased consumption, July 10-20).
Apartment 1 on the 3rd floor is free and does not have anomalies.
Apartments on the 5th floor is free and do not have anomalies.
4th-floor apartments are occupied and have a high level of consumption. 
The other 3 floors apartments are occupied and have the medium level of consumption.

`Feline` building located in the `USA, New York, New York City, Brooklyn, ...`.
It has 3 floors, each floor has 3 apartments.
1st floor has 1st empty apartment with anomalies (increased consumption, September 1-10).
1st floor 2 other has a low level of consumption.
2nd floor is fully occupied and has a medium level.
3rd floor has 2 apartments with high levels and one empty apartment. 
3rd floor 1st apartment with anomaly (decreased consumption, October 20-25).
3rd floor 2nd apartment with anomaly (zero values, September 15-20).

`Hogurity` building located in the `USA, New York, New York City, Manhattan, ...`.
It has 4 floors, each floor has 1 apartment.
Each of them is occupied and has a high level of consumption.
Apartment 1 on 4th floor with high level of consumption has anomaly (zero values, August 1-5)
Building has data gap anomaly on December 1-3.

### Details
Telemetry starts is on `<start year>-01-01` and will be consumed `each hour` from all meters.
It can not have gaps except anomaly cases.

All telemetry will be produced by meters since the beginning of the year, except in some cases:
1. Alpire, 5th floor - since February 2022
2. Feline, full building - since May 2022
3. Hogurity, 3-4 floors since March 2022 

Anomalies:
1. Alpire, 1st floor, 2nd apartment - increased consumption, July 10-20
2. Feline, 1st floor, 1st apartment - increased consumption, September 1-10
3. Feline, 3rd floor, 1st apartment - decreased consumption, October 20-25
4. Feline, 3rd floor, 2nd apartment - zero values, September 15-20
5. Hogurity, 4th floor, 1st apartment - zero values, August 1-5
6. Hogurity, all building - data gap, December 1-3

***

## Water Metering Solution 
  * Login: `watermetering@thingsboard.io`
  * Password: `password`

### Summary
There are the following item types: `WM City`, `WM Region`, `WM Consumer`, and `WM Pump station`.
Cities (WM City) contain some regions (WM Region) and pump station (WM Pump station) for each region - 
all of them is under city in the hierarchy. Regions have some consumers (WM Consumer).
Business case is - consumers has the consumption of water that provided by pump station in particular region.
City can be considered as bundle of regions. 
Consumers have types - Householders (HSH), Government (GOV) and Industrial (IND). 

There are two cities: London and Edinburgh.
London has Dulwich and Wimbledon regions with 6 and 2 consumers (4 HSH, 1 GOV, 1 IND and Wimbledon).
Edinburgh has Leith and Stockbridge regions by 3 consumers (by each type).

Any consumption telemetry uses `Litres` as measurement unit.
There is `consumption` telemetry produced by consumers and measurements depend on type of consumers.
Also, telemetry has one more pattern that is summed with daily and make random zigzag values.
Consumer types:
* Householder - usual living building, has little consumption, bigger in the morning and evening, daily interval of values is [0, 100] and zigzag range is [0, 100].
* Government - government building (not enterprises), has little consumption, bigger in the middle of day, daily interval of values is [0, 50] and zigzag range is [-30, 100].
* Industrial - industrial building (like factories, plants, workshops), has big consumption, less at night, daily interval of values is [10, 600] and zigzag range is [-100, 100].
Any consumption has noises with amplitude `60 litres`.

Each region has `full_consumption` telemetry that summing all related consumer's `consumption` telemetries.
Pump Station (only one for region) has telemetry `provided` that must be equal to `full_consumption` of corresponding region.

The data has anomalies that violate mentioned rules, and it can be found by existing inaccuracies.
Anomalies:
1. London, Wimbledon, Gov1        - January (5-10), zero consumption
2. London, Dulwich, Hsh2          - March (15-22), zero consumption
3. London, Dulwich, Pump Station  - May (1-21) 3am-8am every day, Theft (200 litres every hour)
4. Edinburgh, Leith, Region       - 10 February - 10 April, leakage (100 litres every hour)
5. Edinburgh, Leith, Region       - June (5-25), leakage (100 litres every hour)
6. Edinburgh, Stockbridge, Region - January (5-16), leakage (100 litres every hour)
7. Edinburgh, Stockbridge, Region - March (1-14), leakage (100 litres every hour)

### Details
Telemetry starts is on `<start year>-01-01` and will be consumed `each hour` from all meters.
It can not have gaps except anomaly cases.
All telemetry will be produced by meters since the beginning of the year with no exception.
All anomaly will be only at first year of the telemetry.


***