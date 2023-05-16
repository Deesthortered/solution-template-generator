var iteratedDate = new Date(parseInt(metadata.ts));
if (iteratedDate.getHours() > 0) {
    return {msg: {}, metadata: metadata, msgType: msgType};
}

var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'nitrogen' : parseFloat(metadata.values_nitrogen),
        'phosphorus' : parseFloat(metadata.values_phosphorus),
        'potassium' : parseFloat(metadata.values_potassium),
        'nitrogen_consumption' : parseFloat(metadata.values_nitrogen_consumption),
        'phosphorus_consumption' : parseFloat(metadata.values_phosphorus_consumption),
        'potassium_consumption' : parseFloat(metadata.values_potassium_consumption)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};