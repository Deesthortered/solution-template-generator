function makeLightInData() {
    var date = new Date(parseInt(metadata.ts));
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

    var outsideValue = parseInt(metadata.values_light_out);

    var currentNeededLevel = (DAY_START_HOUR <= hour && hour < NIGHT_START_HOUR)
        ? dayLevel
        : nightLevel;

    return Math.max(0, currentNeededLevel - outsideValue);
}

metadata.values_light_in = makeLightInData();

return {msg: msg, metadata: metadata, msgType: msgType};