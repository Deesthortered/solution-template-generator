let getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

let heatConsumption = function () {
    let currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    let ts = currentDate.getMilliseconds();
    let day = currentDate.getDay();

    let valueWarmTime = 0;
    let valueColdTime = 6_000;
    let noiseAmplitude = 800;
    let noiseWidth = 400;

    let dayColdTimeEnd = 80;
    let dayWarmTimeStart = 120;
    let dayWarmTimeEnd = 230;
    let dayColdTimeStart = 290;

    let shiftedNoiseAmplitude = noiseAmplitude / noiseWidth;
    let value;
    let noise = getRandomInt(-shiftedNoiseAmplitude, shiftedNoiseAmplitude) * noiseWidth;
    if (day <= dayColdTimeEnd || day > dayColdTimeStart) {
        // Cold Time
        value = valueColdTime;
        value += noise;
    } else if (day <= dayWarmTimeStart) {
        // Cold To Warm
        let diff = dayWarmTimeStart - dayColdTimeEnd;
        let current = dayWarmTimeStart - day;
        value = valueWarmTime + Math.round((current * (valueColdTime - valueWarmTime)) / diff);
        value += noise;
    } else if (day <= dayWarmTimeEnd) {
        // Warm
        value = valueWarmTime;
    } else {
        // Warm To Cold
        let diff = dayColdTimeStart - dayWarmTimeEnd;
        let current = -(dayWarmTimeEnd - day);
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

let heatConsAbsolute = function (consumption) {
    let currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    let ts = currentDate.getMilliseconds();
    let value = 0;

    return {
        ts: ts,
        values: {
            heatConsAbsolute: value
        }
    }
}

let consumption = heatConsumption();
let consAbsolute = heatConsAbsolute(consumption);

var msg = consAbsolute;
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};