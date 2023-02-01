var energyConsAbsolutePoint = JSON.parse(metadata.energyConsAbsolute);
var point_ts = energyConsAbsolutePoint.ts;
var point_value = energyConsAbsolutePoint.value;

var data_ts = msg.ts;
var data_value = msg.values.energyConsumption;

var newMsg = {
    "ts" : data_ts,
    "values" : {
        "energyConsAbsolute" : point_value + data_value
    }
}
return {
    msg: newMsg,
    metadata: metadata,
    msgType: msgType
};