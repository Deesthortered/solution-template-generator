var temperature = function () {
    return {temperature: 1};
}

var msg = temperature();
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};