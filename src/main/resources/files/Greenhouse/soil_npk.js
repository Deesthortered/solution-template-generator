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

function truncateDateToDayOfYear() {
    var date = new Date();
    var isLeap = new Date(date.getFullYear(), 1, 29).getDate() === 29;
    var dayOfYear = Math.floor((date - new Date(date.getFullYear(), 0, 0)) / 1000 / 60 / 60 / 24);
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - dayOfYear + (isLeap ? 1 : 0));
    return date;
}


var makeConsumptionData = function (prevValue, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues) {
    var startDate = truncateDateToDayOfYear();
    var iteratedDate = new Date(parseInt(metadata.ts));

    var daysBetween = getDaysBetweenDates(startDate, iteratedDate);

    var currentDayCycle = daysBetween % totalPeriodDays;
    if (currentDayCycle === 0) {
        prevValue = 0;
    }

    var periodDayPrev = 0;
    var periodValuePrev = 0;
    for (var i = 0; i < periodDays.length; i++) {
        var periodDay = periodDays[i];
        var periodValue = periodValues[i];
        if (currentDayCycle < periodDay) {
            var value = periodValuePrev + ((currentDayCycle - periodDayPrev) * (periodValue - periodValuePrev)) / (periodDay - periodDayPrev);
            value += getRandomInt(0, noiseAmplitude) * noiseCoefficient;
            value = Math.max(prevValue, value);
            return value;
        }
        periodDayPrev = periodDay;
        periodValuePrev = periodValue;
    }

    return 0;
}


var makeNitrogenConsumptionData = function () {
    var prevValue = metadata.values_nitrogen_consumption != null ? parseInt(metadata.values_nitrogen_consumption) : 0;
    var noiseAmplitude = 3;
    var noiseCoefficient = 1.0;
    var totalPeriodDays = (parseInt(metadata.ss_minRipeningPeriodDays) + parseInt(metadata.ss_maxRipeningPeriodDays)) / 2;
    var periodDays = JSON.parse(metadata.ss_growthPeriodsDayList != null ? metadata.ss_growthPeriodsDayList : '[]');
    var periodValues = JSON.parse(metadata.ss_growthPeriodsNitrogenConsumption != null ? metadata.ss_growthPeriodsNitrogenConsumption : '[]');

    var result = makeConsumptionData(prevValue, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    metadata.values_nitrogen_consumption = result;
    return result;
}
var makePhosphorusConsumptionData = function () {
    var prevValue = metadata.values_phosphorus_consumption != null ? parseInt(metadata.values_phosphorus_consumption) : 0;
    var noiseAmplitude = 1;
    var noiseCoefficient = 0.01;
    var totalPeriodDays = (parseInt(metadata.ss_minRipeningPeriodDays) + parseInt(metadata.ss_maxRipeningPeriodDays)) / 2;
    var periodDays = JSON.parse(metadata.ss_growthPeriodsDayList != null ? metadata.ss_growthPeriodsDayList : '[]');
    var periodValues = JSON.parse(metadata.ss_growthPeriodsPhosphorusConsumption != null ? metadata.ss_growthPeriodsPhosphorusConsumption : '[]');

    var result = makeConsumptionData(prevValue, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    metadata.values_phosphorus_consumption = result;
    return result;
}
var makePotassiumConsumptionData = function () {
    var prevValue = metadata.values_potassium_consumption != null ? parseInt(metadata.values_potassium_consumption) : 0;
    var noiseAmplitude = 3;
    var noiseCoefficient = 1.0;
    var totalPeriodDays = (parseInt(metadata.ss_minRipeningPeriodDays) + parseInt(metadata.ss_maxRipeningPeriodDays)) / 2;
    var periodDays = JSON.parse(metadata.ss_growthPeriodsDayList != null ? metadata.ss_growthPeriodsDayList : '[]');
    var periodValues = JSON.parse(metadata.ss_growthPeriodsPotassiumConsumption != null ? metadata.ss_growthPeriodsPotassiumConsumption : '[]');

    var result = makeConsumptionData(prevValue, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    metadata.values_potassium_consumption = result;
    return result;
}


var makeData = function (prevValue, minLevel, raiseValue, consumption) {
    var value = consumption;

    var delta = value - prevValue;
    if (delta < 0) {
        delta = value;
    }

    var currentLevel = prevValue;
    currentLevel -= delta;

    if (currentLevel <= minLevel) {
        currentLevel += raiseValue;
    }
    return currentLevel;
}

var makeNitrogenData = function () {
    var prevValue = parseInt(metadata.nitrogen);
    var minLevel = parseInt(metadata.ss_minNitrogenLevel);
    var raiseValue = parseInt(metadata.ss_maxNitrogenLevel) - parseInt(metadata.ss_minNitrogenLevel);
    var consumption = makeNitrogenConsumptionData();
    
    return makeData(prevValue, minLevel, raiseValue, consumption);
}
var makePhosphorusData = function () {
    var prevValue = parseInt(metadata.phosphorus);
    var minLevel = parseInt(metadata.ss_minPhosphorusLevel);
    var raiseValue = parseInt(metadata.ss_maxPhosphorusLevel) - parseInt(metadata.ss_minPhosphorusLevel);
    var consumption = makePhosphorusConsumptionData();

    return makeData(prevValue, minLevel, raiseValue, consumption);
}
var makePotassiumData = function () {
    var prevValue = parseInt(metadata.potassium);
    var minLevel = parseInt(metadata.ss_minPotassiumLevel);
    var raiseValue = parseInt(metadata.ss_maxPotassiumLevel) - parseInt(metadata.ss_minPotassiumLevel);
    var consumption = makePotassiumConsumptionData();

    return makeData(prevValue, minLevel, raiseValue, consumption);
}


var makeNecessaryData = function () {
    var iteratedDate = new Date(parseInt(metadata.ts));
    if (iteratedDate.getHours() > 0) {
        return;
    }

    metadata.values_nitrogen = makeNitrogenData();
    metadata.values_phosphorus = makePhosphorusData();
    metadata.values_potassium = makePotassiumData();
};

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};