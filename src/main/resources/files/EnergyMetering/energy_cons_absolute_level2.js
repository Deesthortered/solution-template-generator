var energyConsAbsolute = function () {
    return {energyConsAbsolute: 1};
}

var msg = energyConsAbsolute();
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};