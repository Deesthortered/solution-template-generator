var tsCycle = (Date.now() % 100) / 10;
var msg = { generated_telemetry: Math.sin(tsCycle) * 100 };
var metadata = { data: 40 };
var msgType = "POST_TELEMETRY_REQUEST";

return { msg: msg, metadata: metadata, msgType: msgType };