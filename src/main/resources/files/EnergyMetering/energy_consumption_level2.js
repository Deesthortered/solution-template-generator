var energyConsumption = function () {
    return {energyConsumption: 1};
}

var msg = energyConsumption();
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};