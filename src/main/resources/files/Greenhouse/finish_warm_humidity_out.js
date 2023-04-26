var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'humidity_out' : parseInt(metadata.values_humidity_out),
        'temperature_out' : parseInt(metadata.values_temperature_out)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};