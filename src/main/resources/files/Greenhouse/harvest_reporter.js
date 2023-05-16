function getRandomInt(min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function getRandomFloat(min, max) {
    if (min === max) {
        return min;
    }
    return Math.random() * (max - min) + min;
}


function getDaysBetweenDates(date1, date2) {
    var diffInMs = Math.abs(date2.getTime() - date1.getTime());
    return diffInMs / (1000 * 60 * 60 * 24);
}

function truncateDateToDayOfYear() {
    var date = new Date(parseInt(metadata.ts));
    var isLeap = new Date(date.getFullYear(), 1, 29).getDate() === 29;
    var dayOfYear = Math.floor((date - new Date(date.getFullYear(), 0, 0)) / 1000 / 60 / 60 / 24);
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - dayOfYear + (isLeap ? 1 : 0));
    return date;
}


function makeNecessaryData() {
    var startDate = truncateDateToDayOfYear();
    var iteratedDate = new Date(parseInt(metadata.ts));
    if (iteratedDate.getHours() > 0) {
        return;
    }

    var periodMin = parseInt(metadata.ss_minRipeningPeriodDays);
    var periodMax = parseInt(metadata.ss_maxRipeningPeriodDays);
    var averageCropWeight = parseFloat(metadata.ss_averageCropWeight);
    var cropWeightNoiseAmplitude = averageCropWeight / 5;
    var workersInCharge = "WORKER_IN_CHARGE_PLACEHOLDER";

    var currentLevel = metadata.values_harverstReporter_currentLevel !== null
        ? parseFloat(metadata.values_harverstReporter_currentLevel)
        : 0;

    var daysBetween = getDaysBetweenDates(startDate, iteratedDate);
    var daysPeriod = daysBetween % periodMax;

    if (periodMin < daysPeriod) {
        if (periodMin === daysPeriod) {
            currentLevel = averageCropWeight;
            currentLevel += getRandomFloat(-cropWeightNoiseAmplitude, cropWeightNoiseAmplitude);
        }
        var workerIndex = getRandomInt(0, workersInCharge.length);
        var worker = workersInCharge[workerIndex];

        var value = getRandomFloat(0.5, 0.5 + currentLevel);
        value = Math.round(value * 100) / 100;

        if (currentLevel < value) {
            value = currentLevel;
        }
        currentLevel -= value;
        metadata.values_harverstReporter_currentLevel = currentLevel;

        if (0 < value) {
            metadata.values_cropWeight = value;
            metadata.values_workerInCharge = worker;
        } else {
            metadata.values_cropWeight = 0;
            metadata.values_workerInCharge = "None";
        }
    } else {
        metadata.values_cropWeight = 0;
        metadata.values_workerInCharge = "None";
    }
}

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};