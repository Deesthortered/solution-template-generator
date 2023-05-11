var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'light_out' : parseInt(metadata.values_light_out)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};