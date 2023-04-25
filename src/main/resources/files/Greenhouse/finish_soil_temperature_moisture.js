var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'moisture' : parseInt(metadata.values_moisture),
        'temperature' : parseInt(metadata.values_temperature)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};