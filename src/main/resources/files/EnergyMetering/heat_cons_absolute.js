var heatConsAbsolutePoint = JSON.parse(metadata.heatConsAbsolute);
var point_ts = heatConsAbsolutePoint.ts;
var point_value = heatConsAbsolutePoint.value;

var data_ts = msg.ts;
var data_value = msg.values.heatConsumption;

var newMsg = {
    "ts" : data_ts,
    "values" : {
        "heatConsAbsolute" : point_value + data_value
    }
}
return {
    msg: newMsg,
    metadata: metadata,
    msgType: msgType
};