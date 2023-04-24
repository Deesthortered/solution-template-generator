var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function getBooleanByProbability(probability) {
    if (probability < 0 || 1 < probability) {
        throw new Error('Probability must be in range [0, 1], given = ${probability}');
    }
    return Math.random() < probability;
}


var makeNitrogenConsumptionData = function () {
    return 0;
}
var makePhosphorusConsumptionData = function () {
    return 0;
}
var makePotassiumConsumptionData = function () {
    return 0;
}


var makeNitrogenData = function () {
    var consumption = makeNitrogenConsumptionData();
    return 0;
}
var makePhosphorusData = function () {
    var consumption = makePhosphorusConsumptionData();
    return 0;
}
var makePotassiumData = function () {
    var consumption = makePotassiumConsumptionData();
    return 0;
}


var makeNecessaryData = function () {

    metadata.values_nitrogen = makeNitrogenData();
    metadata.values_phosphorus = makePhosphorusData();
    metadata.values_potassium = makePotassiumData();
};

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};