function makeCo2ConsumptionData() {
    var nightConsumption = 50;
    var zeroConsumptionLightLevel = parseInt(metadata.ss_dayMinLight);

    var insideLight = metadata.values_light_in;
    var outsideLight = metadata.values_light_out;
    var light = outsideLight + insideLight;
    return Math.round((-1.0 * nightConsumption * light) / zeroConsumptionLightLevel) + nightConsumption;
}

function makeCo2ConcentrationData() {
    var MIN_WORD_CO2_CONCENTRATION = 400;
    var minLevel = metadata.ss_minCo2Concentration;
    var maxLevel = metadata.ss_maxCo2Concentration;
    var decreaseLevel = maxLevel - minLevel;

    var currentLevel = parseInt(metadata.concentration);
    var co2Consumption = makeCo2ConsumptionData();
    currentLevel += co2Consumption;

    metadata.values_aeration = false;
    if (maxLevel <= currentLevel) {
        metadata.values_aeration = true;
        currentLevel -= decreaseLevel;
    }
    currentLevel = Math.max(MIN_WORD_CO2_CONCENTRATION, currentLevel);

    return currentLevel;
}

metadata.values_concentration = makeCo2ConcentrationData();

return {msg: msg, metadata: metadata, msgType: msgType};