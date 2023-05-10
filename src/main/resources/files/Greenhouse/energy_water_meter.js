function getRandomInt(min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}


function makeEnergyMeterData(irrigationCount) {
    var light = parseInt(metadata.values_light_in);
    var aeration = metadata.values_aeration != null ? Boolean(JSON.parse(metadata.values_aeration)) : false;
    var heating = metadata.values_heating != null ? Boolean(JSON.parse(metadata.values_heating)) : false;
    var cooling = metadata.values_cooling != null ? Boolean(JSON.parse(metadata.values_cooling)) : false;
    var humidification = metadata.values_humidification != null ? Boolean(JSON.parse(metadata.values_humidification)) : false;
    var dehumidification = metadata.values_dehumidification != null ? Boolean(JSON.parse(metadata.values_dehumidification)) : false;

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

function makeWaterMeterData(irrigationCount) {
    var humidification = metadata.values_humidification != null ? metadata.values_humidification : false;

    var value = 0;
    value += (humidification) ? 0.5 : 0;
    value += irrigationCount * 2.5;

    value += getRandomInt(-0.5, 0.5);
    value = Math.max(0, value);

    metadata.values_consumptionWater =  value;
}


function getIrrigations() {
    var irrigation = metadata.values_irrigation != null ? parseInt(metadata.values_irrigation) : 0;
    var ts = parseInt(metadata.ts);

    var temp_irrigation_count = metadata.ss_temp_irrigations != null ? parseInt(metadata.ss_temp_irrigations) : 0;
    var temp_ts = metadata.ss_temp_ts != null ? parseInt(metadata.ss_temp_ts) : 0;
    
    if (ts === temp_ts) {
        temp_irrigation_count += (irrigation) ? 1 : 0;
        metadata.ss_temp_irrigations = temp_irrigation_count;
    } else {
        temp_irrigation_count = 0;
        temp_irrigation_count += (irrigation) ? 1 : 0;
        metadata.ss_temp_irrigations = temp_irrigation_count;
        metadata.ss_temp_ts = ts;
    }

    return temp_irrigation_count;
}

function makeNecessaryData() {
    var irrigations = getIrrigations();
    makeEnergyMeterData(irrigations);
    makeWaterMeterData(irrigations);
}

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};