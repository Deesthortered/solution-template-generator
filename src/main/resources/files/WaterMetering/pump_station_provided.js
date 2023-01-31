var currentDate = new Date();
currentDate.setMinutes(0, 0, 0);

var ts = currentDate.getTime();
var value = parseInt(metadata.region_full_consumption);


var provided = {
    ts: ts,
    values: {
        provided: value
    }
};

return {
    msg: provided,
    metadata: metadata,
    msgType: msgType
};