var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'nitrogen' : parseInt(metadata.values_nitrogen),
        'phosphorus' : parseInt(metadata.values_phosphorus),
        'potassium' : parseInt(metadata.values_potassium),
        'nitrogen_consumption' : parseInt(metadata.values_nitrogen_consumption),
        'phosphorus_consumption' : parseInt(metadata.values_phosphorus_consumption),
        'potassium_consumption' : parseInt(metadata.values_potassium_consumption)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};