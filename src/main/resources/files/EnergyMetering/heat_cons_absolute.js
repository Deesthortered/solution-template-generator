var heatConsAbsolute = parseInt(metadata.heatConsAbsolute);
var newMsg = {
    heatConsAbsolute: heatConsAbsolute + msg.values.heatConsumption
}
return {
    msg: newMsg,
    metadata: metadata,
    msgType: msgType
};