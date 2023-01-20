var energyConsumption = parseInt(metadata.energyConsumption);
var newMsg = {
    energyConsAbsolute: energyConsumption + msg.energyConsAbsolute
}
return {
    msg: newMsg,
    metadata: metadata,
    msgType: msgType
};