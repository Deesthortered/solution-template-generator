var getDayofYear = function (date) {
    var start = new Date(date.getFullYear(), 0, 0);
    var diff = date - start;
    var oneDay = 1000 * 60 * 60 * 24;
    var day = Math.floor(diff / oneDay);
    return day;
};

var makeCo2ConsumptionData = function () {
    var nightConsumption = 50;
    var zeroConsumptionLightLevel = parseInt(metadata.ss_dayMinLight);

    var insideLight = metadata.values_light_in;
    var outsideLight = metadata.values_light_out;
    var light = outsideLight + insideLight;
    return Math.round((-1.0 * nightConsumption * light) / zeroConsumptionLightLevel) + nightConsumption;
};

var makeCo2ConcentrationData = function () {
    var MIN_WORD_CO2_CONCENTRATION = 400;
    var minLevel = metadata.ss_minCo2Concentration;
    var maxLevel = metadata.ss_maxCo2Concentration;
    var decreaseLevel = maxLevel - minLevel;

    var currentLevel = metadata.concentration;
    var co2Consumption = makeCo2ConsumptionData();
    currentLevel += co2Consumption;
    if (maxLevel <= currentLevel) {
        metadata.values_aeration = true;
        currentLevel -= decreaseLevel;
    }
    currentLevel = Math.max(MIN_WORD_CO2_CONCENTRATION, currentLevel);

    return currentLevel;
};

var makeNecessaryData = function () {
    var date = new Date();
    date.setMinutes(0, 0, 0);

    var ts = date.getTime();
    var concentration = makeCo2ConcentrationData(date);
    metadata.values_concentration = concentration;
};

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};