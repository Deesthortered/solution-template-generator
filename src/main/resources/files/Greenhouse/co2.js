var getDayofYear = function (date) {
    var start = new Date(date.getFullYear(), 0, 0);
    var diff = date - start;
    var oneDay = 1000 * 60 * 60 * 24;
    var day = Math.floor(diff / oneDay);
    return day;
};

var makeCo2ConcentrationData = function (date) {
    var hour = date.getHours();
    var day = getDayofYear(date);

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