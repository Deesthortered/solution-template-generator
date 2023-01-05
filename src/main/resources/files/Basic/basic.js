var tsCycle = (Date.now() % 100) / 10;
var msg = { telem_value: Math.sin(tsCycle) };
var metadata = { data: 40 };
var msgType = "POST_TELEMETRY_REQUEST";

return { msg: msg, metadata: metadata, msgType: msgType };