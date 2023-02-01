var prev_point = msg;
var old_data = JSON.parse(metadata.full_consumption);

var point_ts = prev_point.ts;
var point_value = prev_point.values.consumption;

var old_ts = old_data.ts;
var old_value = old_data.value;

var newMsg = {
    "ts" : Math.max(point_ts, old_ts),
    "values": {
        "full_consumption" : old_value + point_value
    }
}

return {
    msg: newMsg,
    metadata: metadata,
    msgType: msgType
};