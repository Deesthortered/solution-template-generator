var makeNecessaryData = function () {
    var date = new Date();
    date.setMinutes(0, 0, 0);

    var ts = date.getTime();
    var temperature = msg.main.temp;
    var humidity = msg.main.humidity;

    metadata.values_temperature_out = temperature;
    metadata.values_humidity_out = humidity;
};

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};