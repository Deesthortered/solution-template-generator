var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'consumptionEnergy' : parseInt(metadata.values_consumptionEnergy)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};