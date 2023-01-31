var currentDate = new Date();
currentDate.setMinutes(0, 0, 0);

var ts = currentDate.getTime();

var sum = 0;
for (const [key, value] of Object.entries(metadata)) {
    sum += parseInt(value);
}

var full_consumption = {
    ts: ts,
    values: {
        full_consumption: sum
    }
};

return {
    msg: full_consumption,
    metadata: metadata,
    msgType: msgType
};