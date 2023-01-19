var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

var energyConsumption = function () {
    var currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    var ts = currentDate.getTime();
    var hours = currentDate.getHours();

    var minValue = 15000;
    var amplitude = 2000;
    var noiseWidth = 500;
    var noiseAmplitude = (amplitude / noiseWidth) * 3;
    var phase = (3.14 * 3) / 12;
    var koeff = 3.14 / 12;

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

var energyConsAbsolute = function (consumption) {
    var currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    var ts = currentDate.getTime();
    var value = 0;

    return {
        ts: ts,
        values: {
            energyConsAbsolute: value
        }
    }
}

var consumption = energyConsumption();
var consAbsolute = energyConsAbsolute(consumption);

var msg = consAbsolute;
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};