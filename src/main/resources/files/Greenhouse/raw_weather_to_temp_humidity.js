var makeNecessaryData = function () {
    var date = new Date();
    date.setMinutes(0, 0, 0);

    var ts = date.getTime();
    var temperature = msg.main.temp;
    var humidity = msg.main.humidity;

    return {
        'ts' : ts,
        'values' : {
            'temperature_out' : temperature,
            'humidity_out' : humidity
        }
    };
};

var newMsg = makeNecessaryData();

return {msg: newMsg, metadata: metadata, msgType: msgType};