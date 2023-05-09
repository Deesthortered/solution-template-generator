function makeNecessaryData() {
    var temperature = msg.main.temp;
    var humidity = msg.main.humidity;

    metadata.values_temperature_out = temperature;
    metadata.values_humidity_out = humidity;
}

makeNecessaryData();
return {msg: msg, metadata: metadata, msgType: msgType};