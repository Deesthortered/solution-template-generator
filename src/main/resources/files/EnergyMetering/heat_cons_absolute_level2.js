var heatConsAbsolute = function () {
    return {heatConsAbsolute: 1};
}

var msg = heatConsAbsolute();
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};