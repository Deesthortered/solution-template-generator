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
    var day = currentDate.getDay() % 7;
    var hours = currentDate.getHours();

    var minValue = 30000;
    var amplitude = 5000;
    var noiseWidth = 3000;
    var noiseAmplitude = (amplitude / noiseWidth) * 3;
    var phase = (3.14 * 0.3) / 14;
    var koeffDay = 3.14 / 14;
    var koeffHour = 3.14 / 6;

    var argumentDay = day * 2 - 7;
    var argumentHour = hours - 12;
    var noise = getRandomInt(-noiseAmplitude, noiseAmplitude) * noiseWidth;
    var value = minValue + noise + Math.round(amplitude * Math.sin(phase + koeffDay * argumentDay + koeffHour * argumentHour));

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