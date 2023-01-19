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
    let day = currentDate.getDay() % 7;
    let hours = currentDate.getHours();

    let minValue = 30_000;
    let amplitude = 5_000;
    let noiseWidth = 3000;
    let noiseAmplitude = (amplitude / noiseWidth) * 3;
    let phase = (3.14 * 0.3) / 14;
    let koeffDay = 3.14 / 14;
    let koeffHour = 3.14 / 6;

    let argumentDay = day * 2 - 7;
    let argumentHour = hours - 12;
    let noise = getRandomInt(-noiseAmplitude, noiseAmplitude) * noiseWidth;
    let value = minValue + noise + Math.round(amplitude * Math.sin(phase + koeffDay * argumentDay + koeffHour * argumentHour));

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