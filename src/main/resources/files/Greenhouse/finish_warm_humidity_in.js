var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'humidity_in' : parseInt(metadata.values_humidity_in),
        'temperature_in' : parseInt(metadata.values_temperature_in),
        'temp_heatingMode' : metadata.temp_heatingMode,
        'temp_coolingMode' : metadata.temp_coolingMode,
        'temp_humidificationMode' : metadata.temp_humidificationMode,
        'temp_dehumidificationMode' : metadata.temp_dehumidificationMode
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};