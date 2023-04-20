var getDayofYear = function (date) {
    var start = new Date(date.getFullYear(), 0, 0);
    var diff = date - start;
    var oneDay = 1000 * 60 * 60 * 24;
    var day = Math.floor(diff / oneDay);
    return day;
};

var makeCo2ConsumptionData = function () {
    var nightConsumption = 50;
    var zeroConsumptionLightLevel = parseInt(metadata.ss_dayMinLight);

    var insideLight = metadata.values_light_in;
    var outsideLight = metadata.values_light_out;
    var light = outsideLight + insideLight;
    return Math.round((-1.0 * nightConsumption * light) / zeroConsumptionLightLevel) + nightConsumption;
};

var makeCo2ConcentrationData = function (date) {
    var hour = date.getHours();
    var day = getDayofYear(date);

    var co2Consumption = makeCo2ConsumptionData();

    return 0;
};

var makeNecessaryData = function () {
    var date = new Date();
    date.setMinutes(0, 0, 0);

    var ts = date.getTime();
    var concentration = makeCo2ConcentrationData(date);
    metadata.values_concentration = concentration;
};

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};