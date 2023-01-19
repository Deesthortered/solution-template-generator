var heatConsumption = function () {
    return {heatConsumption: 1};
}

var msg = heatConsumption();
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};