var makeEnergyMeterData = function () {
    return 0;
}

var makeWaterMeterData = function () {
    return 0;
}

var makeNecessaryData = function () {
    metadata.values_consumptionEnergy = makeEnergyMeterData();
    metadata.values_consumptionWater = makeWaterMeterData();
};

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};