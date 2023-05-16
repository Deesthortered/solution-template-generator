function getBooleanByProbability(probability) {
    if (probability < 0 || 1 < probability) {
        throw new Error('Probability must be in range [0, 1], given = ${probability}');
    }
    return Math.random() < probability;
}

function getRandomInt(min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function makeTemperatureInData() {
    var DAY_START_HOUR = 8;
    var NIGHT_START_HOUR = 20;

    var defaultCoefficient = 0.2;
    var aerationCoefficient = 0.4;

    var heatingMode = metadata.temp_heatingMode != null ? Boolean(JSON.parse(metadata.temp_heatingMode)) : false;
    var coolingMode = metadata.temp_coolingMode != null ? Boolean(JSON.parse(metadata.temp_coolingMode)) : false;

    var heatingIncreaseValue = 8;
    var coolingDecreaseValue = 8;

    var dayLowLevel = parseInt(metadata.ss_dayMinTemperature);
    var dayHighLevel = parseInt(metadata.ss_dayMaxTemperature);
    var dayOkLevel = (dayLowLevel + dayHighLevel) / 2;
    var nightLowLevel = parseInt(metadata.ss_nightMinTemperature);
    var nightHighLevel = parseInt(metadata.ss_nightMaxTemperature);
    var nightOkLevel = (nightLowLevel + nightHighLevel) / 2;

    var hour = new Date(parseInt(metadata.ts)).getHours();
    var currentLevel = parseInt(metadata.temperature_in);

    var day = (DAY_START_HOUR <= hour && hour < NIGHT_START_HOUR);
    var aeration = metadata.values_aeration != null ? Boolean(JSON.parse(metadata.values_aeration)) : false;
    var lowLevel = (day) ? dayLowLevel : nightLowLevel;
    var highLevel = (day) ? dayHighLevel : nightHighLevel;
    var okLevel = (day) ? dayOkLevel : nightOkLevel;

    var outsideTemperature = parseInt(metadata.values_temperature_out);
    var diff = outsideTemperature - currentLevel;

    currentLevel += diff * defaultCoefficient;
    currentLevel += (aeration) ? diff * aerationCoefficient : 0;

    if (currentLevel <= okLevel) {
        coolingMode = false;
    }
    if (okLevel <= currentLevel) {
        heatingMode = false;
    }
    if (currentLevel < lowLevel) {
        heatingMode = true;
    }
    if (highLevel < currentLevel) {
        coolingMode = true;
    }

    if (aeration) {
        heatingMode = true;
    }

    metadata.values_heating = false;
    metadata.values_cooling = false;
    if (heatingMode) {
        metadata.values_heating = true;
        currentLevel += Math.min(heatingIncreaseValue, Math.abs(currentLevel - okLevel));
    }
    if (coolingMode) {
        metadata.values_cooling = true;
        currentLevel -= Math.min(coolingDecreaseValue, Math.abs(currentLevel - okLevel));
    }

    if (getBooleanByProbability(0.2)) {
        currentLevel += getRandomInt(-2, 2);
    }

    metadata.temp_heatingMode = heatingMode;
    metadata.temp_coolingMode = coolingMode;
    return currentLevel;
}

metadata.values_temperature_in = makeTemperatureInData();

return {msg: msg, metadata: metadata, msgType: msgType};