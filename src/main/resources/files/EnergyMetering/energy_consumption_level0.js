let getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

let energyConsumption = function () {
    let currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    let ts = currentDate.getMilliseconds();
    let hours = currentDate.getHours();

    let minValue = 15;
    let amplitude = 10;
    let noiseWidth = 3;
    let noiseAmplitude = (amplitude / noiseWidth);
    let phase = (3.14 * 3) / 128;
    let koeff = 3.14 / 128;

    let argument = hours - 12;
    let noise = getRandomInt(-noiseAmplitude, noiseAmplitude) * noiseWidth;
    let value = minValue + noise + Math.round(amplitude * Math.sin(phase + koeff * argument));

    return {
        ts: ts,
        values: {
            energyConsumption: value
        }
    }
}

let consumption = energyConsumption();

var msg = consumption;
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};