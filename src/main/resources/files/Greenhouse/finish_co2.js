var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'concentration' : parseInt(metadata.values_concentration)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};