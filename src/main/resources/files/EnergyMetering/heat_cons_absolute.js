var heatConsumption = parseInt(metadata.heatConsumption);
var newMsg = {
    heatConsAbsolute: heatConsumption + msg.heatConsAbsolute
}
return {
    msg: newMsg,
    metadata: metadata,
    msgType: msgType
};