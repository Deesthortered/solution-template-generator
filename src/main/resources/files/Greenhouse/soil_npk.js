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


function makeConsumptionData(prevValue, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues) {
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
            value += getRandomFloat(0, noiseAmplitude) * noiseCoefficient;
            value = Math.max(prevValue, value);
            return value;
        }
        periodDayPrev = periodDay;
        periodValuePrev = periodValue;
    }

    return 0;
}


function makeNitrogenConsumptionData() {
    var prevValue = metadata.values_nitrogen_consumption != null ? parseFloat(metadata.values_nitrogen_consumption) : 0;
    var noiseAmplitude = 3;
    var noiseCoefficient = 1.0;
    var totalPeriodDays = (parseInt(metadata.ss_minRipeningPeriodDays) + parseInt(metadata.ss_maxRipeningPeriodDays)) / 2;
    var periodDays = JSON.parse(metadata.ss_growthPeriodsDayList != null ? metadata.ss_growthPeriodsDayList : '[]');
    var periodValues = JSON.parse(metadata.ss_growthPeriodsNitrogenConsumption != null ? metadata.ss_growthPeriodsNitrogenConsumption : '[]');

    var result = makeConsumptionData(prevValue, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    metadata.values_nitrogen_consumption = result;
    return result;
}
function makePhosphorusConsumptionData() {
    var prevValue = metadata.values_phosphorus_consumption != null ? parseFloat(metadata.values_phosphorus_consumption) : 0;
    var noiseAmplitude = 1;
    var noiseCoefficient = 0.01;
    var totalPeriodDays = (parseInt(metadata.ss_minRipeningPeriodDays) + parseInt(metadata.ss_maxRipeningPeriodDays)) / 2;
    var periodDays = JSON.parse(metadata.ss_growthPeriodsDayList != null ? metadata.ss_growthPeriodsDayList : '[]');
    var periodValues = JSON.parse(metadata.ss_growthPeriodsPhosphorusConsumption != null ? metadata.ss_growthPeriodsPhosphorusConsumption : '[]');

    var result = makeConsumptionData(prevValue, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    metadata.values_phosphorus_consumption = result;
    return result;
}
function makePotassiumConsumptionData() {
    var prevValue = metadata.values_potassium_consumption != null ? parseFloat(metadata.values_potassium_consumption) : 0;
    var noiseAmplitude = 3;
    var noiseCoefficient = 1.0;
    var totalPeriodDays = (parseInt(metadata.ss_minRipeningPeriodDays) + parseInt(metadata.ss_maxRipeningPeriodDays)) / 2;
    var periodDays = JSON.parse(metadata.ss_growthPeriodsDayList != null ? metadata.ss_growthPeriodsDayList : '[]');
    var periodValues = JSON.parse(metadata.ss_growthPeriodsPotassiumConsumption != null ? metadata.ss_growthPeriodsPotassiumConsumption : '[]');

    var result = makeConsumptionData(prevValue, noiseAmplitude, noiseCoefficient, totalPeriodDays, periodDays, periodValues);
    metadata.values_potassium_consumption = result;
    return result;
}


function makeData(prevValue, minLevel, raiseValue, consumption) {
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

function makeNitrogenData() {
    var prevValue = parseFloat(metadata.nitrogen);
    var minLevel = parseFloat(metadata.ss_minNitrogenLevel);
    var raiseValue = parseFloat(metadata.ss_maxNitrogenLevel) - parseFloat(metadata.ss_minNitrogenLevel);
    var consumption = makeNitrogenConsumptionData();
    
    return makeData(prevValue, minLevel, raiseValue, consumption);
}
function makePhosphorusData() {
    var prevValue = parseFloat(metadata.phosphorus);
    var minLevel = parseFloat(metadata.ss_minPhosphorusLevel);
    var raiseValue = parseFloat(metadata.ss_maxPhosphorusLevel) - parseFloat(metadata.ss_minPhosphorusLevel);
    var consumption = makePhosphorusConsumptionData();

    return makeData(prevValue, minLevel, raiseValue, consumption);
}
function makePotassiumData() {
    var prevValue = parseFloat(metadata.potassium);
    var minLevel = parseFloat(metadata.ss_minPotassiumLevel);
    var raiseValue = parseFloat(metadata.ss_maxPotassiumLevel) - parseFloat(metadata.ss_minPotassiumLevel);
    var consumption = makePotassiumConsumptionData();

    return makeData(prevValue, minLevel, raiseValue, consumption);
}


function makeNecessaryData() {
    var iteratedDate = new Date(parseInt(metadata.ts));
    if (iteratedDate.getHours() > 0) {
        return;
    }

    metadata.values_nitrogen = makeNitrogenData();
    metadata.values_phosphorus = makePhosphorusData();
    metadata.values_potassium = makePotassiumData();
}

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};