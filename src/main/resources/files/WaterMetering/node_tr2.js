var newMsg = {
    "ts" : msg.ts,
    "values": {
        "provided" : msg.values.full_consumption
    }
}

return {
    msg: newMsg,
    metadata: metadata,
    msgType: msgType
};