var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'light_in' : parseInt(metadata.values_light_in)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};