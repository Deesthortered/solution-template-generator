function getRandomInt(min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function makeAcidityData() {
    var period = parseInt(metadata.ss_maxRipeningPeriodDays) * 24;
    var minLevel = parseInt(metadata.ss_minPh);
    var maxLevel = parseInt(metadata.ss_maxPh);

    var increaseLevel = (maxLevel - minLevel) / period;
    var irrigationIncreaseLevel = (maxLevel - minLevel) / period * 24 * 5;
    var acidificationDecreaseLevel = (maxLevel - minLevel);
    var currentLevel = parseInt(metadata.acidity);

    var irrigation = metadata.values_irrigation != null ? metadata.values_irrigation : false;

    currentLevel -= increaseLevel;
    currentLevel -= (irrigation) ? irrigationIncreaseLevel : 0;

    if (currentLevel <= minLevel) {
        metadata.values_acidification = true;
        currentLevel += acidificationDecreaseLevel;
    }

    currentLevel += getRandomInt(-0.03, 0.03);

    return currentLevel;
}

metadata.values_acidity = makeAcidityData();

return {msg: msg, metadata: metadata, msgType: msgType};