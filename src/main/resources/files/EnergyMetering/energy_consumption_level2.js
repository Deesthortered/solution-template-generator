var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

var energyConsumption2 = function () {
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

var consumption = energyConsumption2();

var msg = consumption;
var metadata = {};
var msgType = "POST_TEgetTimeMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};