var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

var energyConsumption0 = function () {
    var currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    var ts = currentDate.getTime();
    var hours = currentDate.getHours();

    var minValue = 15;
    var amplitude = 10;
    var noiseWidth = 3;
    var noiseAmplitude = (amplitude / noiseWidth);
    var phase = (3.14 * 3) / 128;
    var koeff = 3.14 / 128;

    var argument = hours - 12;
    var noise = getRandomInt(-noiseAmplitude, noiseAmplitude) * noiseWidth;
    var value = minValue + noise + Math.round(amplitude * Math.sin(phase + koeff * argument));

    return {
        ts: ts,
        values: {
            energyConsumption: value
        }
    }
}

var consumption = energyConsumption0();

var msg = consumption;
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};