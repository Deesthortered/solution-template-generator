# Solution template generator 


### Sysadmin Info
* Login: `sysadmin@thingsboard.org`
* Password: `sysadmin`

### Tenant Info
* Login: `solution.template.generator@thingsboard.io`
* Password: `password`
* Title: Solution Template Generator Tenant

***

## Solutions:

* ### Basic Solution
  * Login: `basic@thingsboard.io`
  * Password: `password`

Data starts at 2023-01-01 00:00.000000

* ### Energy Metering Solution 
  * Login: `energymetering@thingsboard.io`
  * Password: `password`

There are the following item types: buildings, apartments, one energy meter, and one heat meter.
Buildings contain some apartments, apartments have one energy and one heat meter.

There are three buildings: Alpire, Feline, and Hogurity.
They have an `address` attribute and all of them are located in the USA in California or in New York.

Each building has apartments with different `floor`, `area`, and `localNumber`.
Apartments have a `state` attribute that determines apartment is rented or empty.
Rented apartments consume more energy and heating, and empty has only heating.
Sometimes we can observe anomalies in energy consumption because of accidents: stolen energy and forgotten home devices.
To simplify the solution, there are defined 3 levels of consumption: low, medium, and high.
Levels are not represented explicitly, it just makes a difference between apartments in their telemetry.

Each apartment has energy and meters with attributes `installDate` and `serialNumber`.
They provide telemetry only after `installDate` date.
Energy Meter provides `energyConsumption` telemetry and `energyConsAbsolute` that summing that data.
Heat Meter provides `heatConsumption` and `temperature` telemetries.
Energy Meter and Heat Meter do not make affect each other.


`Alpire` building located in the `USA, California, San Francisco, ...`.
It has 5 floors, each floor has 2 apartments.
The apartment on the 5th floor is free and do not have anomalies.
1st one on the 3rd floor is free, 2nd on the 1st floor too.
Others are occupied.
4th-floor apartments have a medium level of consumption.
The other 3 floors have the medium level.

`Feline` building located in the `USA, New York, New York City, Brooklyn, ...`.
It has 3 floors, each floor has 3 apartments.
1st floor has one empty apartment with anomalies and 2 other has a low level of consumption.
2nd floor is fully occupied and has a medium level.
3rd floor has 2 apartments with high levels and one empty apartment. 

`Hogurity` building located in the `USA, New York, New York City, Manhattan, ...`.
It has 4 floors, each floor has 1 apartment.
Each of them is occupied and has a high level of consumption.


* ### Water Metering Solution 