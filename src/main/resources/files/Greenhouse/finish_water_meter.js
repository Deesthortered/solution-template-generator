var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'consumptionWater' : parseFloat(metadata.values_consumptionWater)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};
