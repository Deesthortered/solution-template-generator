var heatConsAbsolute = parseInt(metadata.heatConsAbsolute);
var newMsg = {
    energyConsAbsolute: heatConsAbsolute + msg.values.energyConsumption
}
return {
    msg: newMsg,
    metadata: metadata,
    msgType: msgType
};