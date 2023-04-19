var makeNecessaryData = function () {
    var date = new Date();
    date.setMinutes(0, 0, 0);
    var ts = date.getTime();
    var hour = date.getHours();

    var DAY_START_HOUR = 8;
    var NIGHT_START_HOUR = 20;

    var dayMinLevel = JSON.parse(metadata.ss_dayMinLight);
    var dayMaxLevel = JSON.parse(metadata.ss_dayMaxLight);
    var nightMinLevel = JSON.parse(metadata.ss_nightMinLight);
    var nightMaxLevel = JSON.parse(metadata.ss_nightMaxLight);
    var dayLevel = (dayMinLevel + dayMaxLevel) / 2;
    var nightLevel = (nightMinLevel + nightMaxLevel) / 2;

    var outsideValue = msg.light_out;

    var currentNeededLevel = (DAY_START_HOUR <= hour && hour < NIGHT_START_HOUR)
        ? dayLevel
        : nightLevel;

    var diff = Math.max(0, currentNeededLevel - outsideValue);


    return {
        'ts' : ts,
        'values' : {
            'light_in' : diff
        }
    };
};

var newMsg = makeNecessaryData();
return {msg: newMsg, metadata: metadata, msgType: msgType};