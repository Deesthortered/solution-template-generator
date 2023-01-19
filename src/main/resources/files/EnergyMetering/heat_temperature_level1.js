var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

var temperature = function () {
    var currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    var ts = currentDate.getTime();
    var noiseAmplitude = 3;
    var value = 20 + getRandomInt(-noiseAmplitude, noiseAmplitude);

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