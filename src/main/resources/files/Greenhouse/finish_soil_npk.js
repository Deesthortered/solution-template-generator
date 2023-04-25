var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'nitrogen' : parseInt(metadata.values_nitrogen),
        'phosphorus' : parseInt(metadata.values_phosphorus),
        'potassium' : parseInt(metadata.values_potassium)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};