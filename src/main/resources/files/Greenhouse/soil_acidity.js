function getRandomFloat(min, max) {
    if (min === max) {
        return min;
    }
    return Math.random() * (max - min) + min;
}

function makeAcidityData() {
    var period = parseInt(metadata.ss_maxRipeningPeriodDays) * 24;
    var minLevel = parseFloat(metadata.ss_minPh);
    var maxLevel = parseFloat(metadata.ss_maxPh);

    var increaseLevel = (maxLevel - minLevel) / period;
    var irrigationIncreaseLevel = (maxLevel - minLevel) / period * 24 * 5;
    var acidificationDecreaseLevel = (maxLevel - minLevel);
    var currentLevel = parseFloat(metadata.acidity);

    var irrigation = metadata.values_irrigation != null ? Boolean(JSON.parse(metadata.values_irrigation)) : false;

    currentLevel -= increaseLevel;
    currentLevel -= (irrigation) ? irrigationIncreaseLevel : 0;

    if (currentLevel <= minLevel) {
        metadata.values_acidification = true;
        currentLevel += acidificationDecreaseLevel;
    }

    currentLevel += getRandomFloat(-0.03, 0.03);

    return currentLevel;
}

metadata.values_acidity = makeAcidityData();

return {msg: msg, metadata: metadata, msgType: msgType};