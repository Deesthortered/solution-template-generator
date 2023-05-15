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

function sign(x) {
    return typeof x === 'number' ? x ? x < 0 ? -1 : 1 : x === x ? 0 : NaN : NaN;
}

function makeHumidityInData() {

    var increaseLevel = 3;
    var aerationDecreaseValue = 30;
    var heatingIncreaseValue = 2;
    var coolingDecreaseValue = 5;
    var humidificationIncreaseValue = 10;
    var dehumidificationDecreaseValue = 10;

    var humidificationMode = metadata.ss_temp_humidificationMode != null ? Boolean(JSON.parse(metadata.ss_temp_humidificationMode)) : false;
    var dehumidificationMode = metadata.ss_temp_dehumidificationMode != null ? Boolean(JSON.parse(metadata.ss_temp_dehumidificationMode)) : false;
    var lowLevel = parseInt(metadata.ss_minAirHumidity);
    var highLevel = parseInt(metadata.ss_maxAirHumidity);
    var okLevel = (lowLevel + highLevel) / 2;

    var currentLevel = parseInt(metadata.humidity_in);

    var aeration = metadata.values_aeration != null ? Boolean(JSON.parse(metadata.values_aeration)) : false;
    var heating = metadata.values_heating != null ? Boolean(JSON.parse(metadata.values_heating)) : false;
    var cooling = metadata.values_cooling != null ? Boolean(JSON.parse(metadata.values_cooling)) : false;

    var outsideHumidity = parseInt(metadata.values_humidity_out);
    var diff = outsideHumidity - currentLevel;

    currentLevel += increaseLevel;
    currentLevel += (aeration) ? sign(diff) * Math.min(aerationDecreaseValue, Math.abs(diff)) : 0;
    currentLevel += (heating) ? heatingIncreaseValue : 0;
    currentLevel -= (cooling) ? coolingDecreaseValue : 0;

    if (currentLevel <= okLevel) {
        dehumidificationMode = false;
    }
    if (okLevel <= currentLevel) {
        humidificationMode = false;
    }
    if (currentLevel < lowLevel) {
        humidificationMode = true;
    }
    if (highLevel < currentLevel) {
        dehumidificationMode = true;
    }

    metadata.values_humidification = false;
    metadata.values_dehumidification = false;
    if (humidificationMode) {
        metadata.values_humidification = true;
        currentLevel += Math.min(humidificationIncreaseValue, Math.abs(currentLevel - okLevel)) + getRandomInt(-1, 1);
    }
    if (dehumidificationMode) {
        metadata.values_dehumidification = true;
        currentLevel -= Math.min(dehumidificationDecreaseValue, Math.abs(currentLevel - okLevel)) + getRandomInt(-1, 1);
    }

    if (getBooleanByProbability(0.3)) {
        currentLevel += getRandomInt(0, 1);
    }

    currentLevel = Math.min(currentLevel, 100);
    currentLevel = Math.max(currentLevel, 0);

    metadata.temp_humidificationMode = metadata.values_humidification;
    metadata.temp_dehumidificationMode = metadata.values_dehumidification;
    return currentLevel;
}

metadata.values_humidity_in = makeHumidityInData();

return {msg: msg, metadata: metadata, msgType: msgType};