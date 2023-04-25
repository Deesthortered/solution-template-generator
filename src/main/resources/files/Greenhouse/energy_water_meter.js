var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}


var makeEnergyMeterData = function () {
    var light = parseInt(metadata.values_light_in);
    var aeration = metadata.values_aeration !== null ? metadata.values_aeration : false;
    var heating = metadata.values_heating !== null ? metadata.values_heating : false;
    var cooling = metadata.values_cooling !== null ? metadata.values_cooling : false;
    var humidification = metadata.values_humidification !== null ? metadata.values_humidification : false;
    var dehumidification = metadata.values_dehumidification !== null ? metadata.values_dehumidification : false;
    var irrigationCount = metadata.values_irrigation_count !== null ? parseInt(metadata.values_irrigation_count) : 0;

    var valueLight = 0;
    valueLight += light * 0.05;
    valueLight += getRandomInt(-2, 2);
    valueLight = Math.max(0, valueLight);

    var valueHeating = 0;
    valueHeating += (heating) ? 200 : 0;
    valueHeating += getRandomInt(-2, 2);
    valueHeating = Math.max(0, valueHeating);

    var valueCooling = 0;
    valueCooling += (cooling) ? 100 : 0;
    valueCooling += getRandomInt(-2, 2);
    valueCooling = Math.max(0, valueCooling);

    var valueAirControl = 0;
    valueAirControl += (aeration) ? 20 : 0;
    valueAirControl += (humidification) ? 20 : 0;
    valueAirControl += (dehumidification) ? 50 : 0;
    valueAirControl += getRandomInt(-2, 2);
    valueAirControl = Math.max(0, valueAirControl);

    var valueIrrigation = 0;
    valueIrrigation += irrigationCount * 100;
    valueIrrigation += getRandomInt(-2, 2);
    valueIrrigation = Math.max(0, valueIrrigation);


    metadata.values_energyConsumptionLight = valueLight;
    metadata.values_energyConsumptionHeating = valueHeating;
    metadata.values_energyConsumptionCooling = valueCooling;
    metadata.values_energyConsumptionAirControl = valueAirControl;
    metadata.values_energyConsumptionIrrigation = valueIrrigation;
}

var makeWaterMeterData = function () {
    var humidification = metadata.values_humidification !== null ? metadata.values_humidification : false;
    var irrigationCount = metadata.values_irrigation_count !== null ? parseInt(metadata.values_irrigation_count) : 0;

    var value = 0;
    value += (humidification) ? 0.5 : 0;
    value += irrigationCount * 2.5;

    value += getRandomInt(-0.5, 0.5);
    value = Math.max(0, value);

    return value;
}

var makeNecessaryData = function () {
    makeEnergyMeterData();
    metadata.values_consumptionWater = makeWaterMeterData();
};

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};