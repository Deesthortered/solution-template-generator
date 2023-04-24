var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function getDaysBetweenDates(date1, date2) {
    var diffInMs = Math.abs(date2.getTime() - date1.getTime());
    return diffInMs / (1000 * 60 * 60 * 24);
}


var makeNecessaryData = function () {
    var periodMin = parseInt(metadata.ss_minRipeningPeriodDays);
    var periodMax = parseInt(metadata.ss_maxRipeningPeriodDays);
    var averageCropWeight = parseInt(metadata.ss_averageCropWeight);
    var cropWeightNoiseAmplitude = averageCropWeight / 5;
    var workersInCharge = "WORKER_IN_CHARGE_PLACEHOLDER";

    var currentLevel = 0;
    var skip = true;

    var startDate = new Date();
    startDate.setHours(0, 0, 0, 0);
    var iteratedDate = new Date(parseInt(metadata.ts));

    var daysBetween = getDaysBetweenDates(startDate, iteratedDate);
    var daysPeriod = daysBetween % periodMax;

    if (periodMin < daysPeriod) {
        if (skip) {
            // skip = false;
            currentLevel = averageCropWeight;
            currentLevel += getRandomInt(-cropWeightNoiseAmplitude, cropWeightNoiseAmplitude);
        }
        var workerIndex = getRandomInt(0, workersInCharge.length);
        var worker = workersInCharge[workerIndex];

        var value = getRandomInt(0.5, 0.5 + currentLevel);
        value = Math.round(value * 100) / 100;

        if (currentLevel < value) {
            value = currentLevel;
        }
        currentLevel -= value;

        if (0 < value) {
            // value
            // worker
        }
    } else {
        skip = true;
    }


    metadata.values_cropWeight = null;
    metadata.values_workerInCharge = null;
};

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};