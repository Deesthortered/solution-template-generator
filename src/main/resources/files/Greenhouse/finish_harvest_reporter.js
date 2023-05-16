var iteratedDate = new Date(parseInt(metadata.ts));
if (iteratedDate.getHours() > 0) {
    return {msg: {}, metadata: metadata, msgType: msgType};
}
var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'temp_harvest_currentLevel' : parseFloat(metadata.temp_harvest_currentLevel),
        'cropWeight' : parseFloat(metadata.values_cropWeight),
        'workerInCharge' : metadata.values_workerInCharge
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};