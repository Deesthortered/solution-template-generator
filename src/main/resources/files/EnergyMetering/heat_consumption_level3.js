var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

var heatConsumption = function () {
    var currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    var ts = currentDate.getTime();
    var day = currentDate.getDay();

    var valueWarmTime = 0;
    var valueColdTime = 10000;
    var noiseAmplitude = 2000;
    var noiseWidth = 800;

    var dayColdTimeEnd = 80;
    var dayWarmTimeStart = 120;
    var dayWarmTimeEnd = 230;
    var dayColdTimeStart = 290;

    var shiftedNoiseAmplitude = noiseAmplitude / noiseWidth;
    var value;
    var noise = getRandomInt(-shiftedNoiseAmplitude, shiftedNoiseAmplitude) * noiseWidth;
    if (day <= dayColdTimeEnd || day > dayColdTimeStart) {
        // Cold Time
        value = valueColdTime;
        value += noise;
    } else if (day <= dayWarmTimeStart) {
        // Cold To Warm
        var diff = dayWarmTimeStart - dayColdTimeEnd;
        var current = dayWarmTimeStart - day;
        value = valueWarmTime + Math.round((current * (valueColdTime - valueWarmTime)) / diff);
        value += noise;
    } else if (day <= dayWarmTimeEnd) {
        // Warm
        value = valueWarmTime;
    } else {
        // Warm To Cold
        var diff = dayColdTimeStart - dayWarmTimeEnd;
        var current = -(dayWarmTimeEnd - day);
        value = valueWarmTime + Math.round((current * (valueColdTime - valueWarmTime)) / diff);
        value += noise;
    }

    value = Math.max(0, value);

    return {
        ts: ts,
        values: {
            heatConsumption: value
        }
    }
}

var consumption = heatConsumption();

var msg = consumption;
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};