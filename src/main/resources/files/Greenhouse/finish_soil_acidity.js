var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'acidity' : parseInt(metadata.values_acidity)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};