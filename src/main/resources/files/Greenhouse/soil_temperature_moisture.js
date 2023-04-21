var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function getHoursBetweenDates(date1, date2) {
    var diffInMs = Math.abs(date2.getTime() - date1.getTime());
    return diffInMs / (1000 * 60 * 60);
}


var makeMoistureConsumptionData = function () {
    var minLevel = parseInt(metadata.ss_minSoilMoisture);
    var maxLevel = parseInt(metadata.ss_maxSoilMoisture);
    var minRipeningPeriodDays = parseInt(metadata.ss_minRipeningPeriodDays);
    var maxRipeningPeriodDays = parseInt(metadata.ss_maxRipeningPeriodDays);
    var period = 24 * (minRipeningPeriodDays + maxRipeningPeriodDays) / 2;

    var startDate = new Date();
    startDate.setHours(0, 0, 0, 0);
    var iteratedDate = new Date(parseInt(metadata.ts));

    var hoursBetween = getHoursBetweenDates(startDate, iteratedDate);
    var hourCycle = (hoursBetween % period) + 1;

    var step = hourCycle / (period / 3) + 1;

    var consumption = Math.pow(0.8, step) * (minLevel + maxLevel) / period;
    consumption += getRandomInt(0, 1);

    return consumption;
}

var makeMoistureData = function () {
    var minLevel = parseInt(metadata.ss_minSoilMoisture);
    var maxLevel = parseInt(metadata.ss_maxSoilMoisture);

    var waterConsumption = makeMoistureConsumptionData();

    var currentLevel = parseInt(metadata.moisture);
    currentLevel -= waterConsumption;
    if (currentLevel <= minLevel) {
        currentLevel += (maxLevel - minLevel);
        metadata.values_irrigation = true;
    }

    currentLevel = Math.min(currentLevel, 100);
    currentLevel = Math.max(currentLevel, 0);

    return currentLevel;
}


var makeTemperatureData = function () {
    var increaseLevel = 0.5;
    var decreaseIrrigationLevel = 5;

    var irrigation = metadata.values_irrigation !== null ? metadata.values_irrigation : false;
    var insideTemperature = parseInt(metadata.values_temperature_in);

    var diff = insideTemperature - currentLevel;

    var currentLevel = parseInt(metadata.values_temperature);
    currentLevel += diff * increaseLevel;
    currentLevel -= (irrigation) ? decreaseIrrigationLevel : 0;

    return currentLevel;
}


var makeNecessaryData = function () {
    var moisture = makeMoistureData();
    var temperature = makeTemperatureData();

    metadata.values_moisture = moisture;
    metadata.values_temperature = temperature;
};

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};