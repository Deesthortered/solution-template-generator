let getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

let temperature = function () {
    let currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    let ts = currentDate.getMilliseconds();
    let noiseAmplitude = 3;
    let value = 20 + getRandomInt(-noiseAmplitude, noiseAmplitude);

    return {
        ts: ts,
        values: {
            temperature: value
        }
    }
}

var msg = temperature();
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};