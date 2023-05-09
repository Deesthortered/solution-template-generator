function getRandomInt(min, max) {
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

function truncateDateToDayOfYear() {
    var date = new Date(parseInt(metadata.ts));
    var isLeap = new Date(date.getFullYear(), 1, 29).getDate() === 29;
    var dayOfYear = Math.floor((date - new Date(date.getFullYear(), 0, 0)) / 1000 / 60 / 60 / 24);
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - dayOfYear + (isLeap ? 1 : 0));
    return date;
}


function makeMoistureConsumptionData() {
    var minLevel = parseInt(metadata.ss_minSoilMoisture);
    var maxLevel = parseInt(metadata.ss_maxSoilMoisture);
    var minRipeningPeriodDays = parseInt(metadata.ss_minRipeningPeriodDays);
    var maxRipeningPeriodDays = parseInt(metadata.ss_maxRipeningPeriodDays);
    var period = 24 * (minRipeningPeriodDays + maxRipeningPeriodDays) / 2;

    var startDate = truncateDateToDayOfYear();
    var iteratedDate = new Date(parseInt(metadata.ts));

    var hoursBetween = getHoursBetweenDates(startDate, iteratedDate);
    var hourCycle = (hoursBetween % period) + 1;

    var step = hourCycle / (period / 3) + 1;

    var consumption = Math.pow(0.8, step) * (minLevel + maxLevel) / period;
    consumption += getRandomInt(0, 1);

    return consumption;
}

function makeMoistureData() {
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


function makeTemperatureData() {
    var increaseLevel = 0.5;
    var decreaseIrrigationLevel = 5;

    var irrigation = metadata.values_irrigation != null ? metadata.values_irrigation : false;
    var insideTemperature = parseInt(metadata.values_temperature_in);

    var currentLevel = parseInt(metadata.temperature);
    var diff = insideTemperature - currentLevel;

    currentLevel += diff * increaseLevel;
    currentLevel -= (irrigation) ? decreaseIrrigationLevel : 0;

    return currentLevel;
}


function makeNecessaryData() {
    var moisture = makeMoistureData();
    var temperature = makeTemperatureData();

    metadata.values_moisture = moisture;
    metadata.values_temperature = temperature;
}

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};