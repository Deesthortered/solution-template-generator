var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'humidity_in' : parseInt(metadata.values_humidity_in),
        'temperature_in' : parseInt(metadata.values_temperature_in)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};