var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'cropWeight' : parseInt(metadata.values_cropWeight),
        'workerInCharge' : metadata.values_workerInCharge
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};